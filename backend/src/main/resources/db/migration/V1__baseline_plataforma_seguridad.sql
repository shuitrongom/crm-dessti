-- ============================================================================
-- V1__baseline_plataforma_seguridad.sql
--
-- Migracion inicial (baseline) del CRM de Anuncios Luminosos.
-- Alcance: SOLO esquema base de Plataforma y Seguridad (design.md ->
-- Data Models -> "Plataforma y Seguridad"). NO incluye tablas de modulos de
-- negocio (clientes, cotizaciones, etc.), que se agregan en migraciones
-- posteriores.
--
-- Requisitos cubiertos:
--   - Req 23 (multi-tenant): columna tenant_id en tablas tenant-scoped e
--     indices por tenant_id; unicidad de negocio POR TENANT donde aplica.
--   - Req 49 (concurrencia optimista): columna version BIGINT en toda entidad
--     de negocio modificable.
--
-- DECISIONES DE DISENO documentadas en esta baseline:
--   1. Generacion de UUID: se habilita la extension "pgcrypto" y se usa
--      gen_random_uuid() como DEFAULT de las PK. Asi, las filas insertadas
--      directamente en BD (semillas, pruebas manuales) obtienen un UUID, y la
--      aplicacion tambien puede proveer su propio UUID (generado por la app)
--      sin conflicto. pgcrypto viene incluido en PostgreSQL contrib (>= 13) y
--      no requiere componentes externos, a diferencia de uuid-ossp.
--   2. Unicidad de login (usuario.identificador_acceso): se define GLOBAL
--      (no por tenant). El Servicio_Autenticacion resuelve al usuario por su
--      identificador ANTES de conocer el tenant (el tenant_id se deriva del
--      usuario/JWT, Req 23.4); ademas el super_admin tiene tenant_id NULL. Un
--      identificador global unico evita ambiguedad en el login. Las demas
--      unicidades de negocio (p. ej. nombre de rol personalizado) se definen
--      POR TENANT.
--   3. Marcas temporales en UTC: se usa timestamptz; la aplicacion opera en
--      UTC (Req de auditoria). created_by/updated_by se guardan como texto
--      (identificador del actor), sin FK dura para no acoplar el historico.
--
-- NOTA sobre RLS: la Row-Level Security (Req 23, segunda capa de defensa) NO
-- se habilita en esta migracion. Se activa en una migracion posterior
-- (tarea 4.2), junto con las politicas tenant_isolation y el uso de
-- app.current_tenant por transaccion. El rol de BD de la aplicacion NO debe
-- ser superusuario ni tener BYPASSRLS (ver db/roles/app_role.sql).
-- ============================================================================

-- Extension para generacion de UUID en el lado de la base de datos.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------------------------------------------------------
-- empresa (Tenant)
--   Unidad de aislamiento multi-empresa. Es la propia entidad tenant, por lo
--   que su PK (id) ES el tenant_id; no lleva columna tenant_id adicional.
-- ----------------------------------------------------------------------------
CREATE TABLE empresa (
    id                       UUID        NOT NULL DEFAULT gen_random_uuid(),
    nombre                   VARCHAR(200) NOT NULL,
    rfc                      VARCHAR(13) NOT NULL,
    estado                   VARCHAR(20) NOT NULL DEFAULT 'activa',
    branding_nombre_visible  VARCHAR(200),
    branding_logo            TEXT,
    fecha_cancelacion        TIMESTAMPTZ,
    fin_periodo_gracia       TIMESTAMPTZ,
    version                  BIGINT      NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    CONSTRAINT pk_empresa PRIMARY KEY (id),
    CONSTRAINT ck_empresa_estado CHECK (estado IN ('activa', 'suspendida', 'cancelada'))
);

-- ----------------------------------------------------------------------------
-- plan
--   Definicion de suscripcion a nivel plataforma (no tenant-scoped): los Planes
--   son catalogos de plataforma administrados por el super_admin. modulos_
--   habilitados se modela como jsonb (design.md).
-- ----------------------------------------------------------------------------
CREATE TABLE plan (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),
    nombre               VARCHAR(120) NOT NULL,
    max_usuarios         INTEGER     NOT NULL,
    modulos_habilitados  JSONB       NOT NULL DEFAULT '[]'::jsonb,
    version              BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),
    CONSTRAINT pk_plan PRIMARY KEY (id),
    CONSTRAINT uq_plan_nombre UNIQUE (nombre),
    CONSTRAINT ck_plan_max_usuarios CHECK (max_usuarios >= 0)
);

-- ----------------------------------------------------------------------------
-- suscripcion
--   Relacion entre una Empresa y un Plan, con estado y vigencia. tenant_id
--   referencia a la empresa (Req 23).
-- ----------------------------------------------------------------------------
CREATE TABLE suscripcion (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID        NOT NULL,
    plan_id           UUID        NOT NULL,
    estado            VARCHAR(20) NOT NULL DEFAULT 'activa',
    vigencia_inicio   DATE        NOT NULL,
    vigencia_fin      DATE,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    CONSTRAINT pk_suscripcion PRIMARY KEY (id),
    CONSTRAINT fk_suscripcion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_suscripcion_plan    FOREIGN KEY (plan_id)   REFERENCES plan (id),
    CONSTRAINT ck_suscripcion_estado  CHECK (estado IN ('activa', 'suspendida', 'cancelada')),
    CONSTRAINT ck_suscripcion_vigencia CHECK (vigencia_fin IS NULL OR vigencia_fin >= vigencia_inicio)
);

CREATE INDEX ix_suscripcion_tenant_id ON suscripcion (tenant_id);
CREATE INDEX ix_suscripcion_plan_id   ON suscripcion (plan_id);

-- ----------------------------------------------------------------------------
-- usuario
--   Cuenta de acceso. tenant_id es NULL para el super_admin (nivel plataforma)
--   y NOT NULL para usuarios de una Empresa. identificador_acceso es UNICO
--   GLOBAL (ver DECISION 2 arriba).
-- ----------------------------------------------------------------------------
CREATE TABLE usuario (
    id                    UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID,
    identificador_acceso  VARCHAR(255) NOT NULL,
    hash_password         VARCHAR(255) NOT NULL,
    activo                BOOLEAN     NOT NULL DEFAULT TRUE,
    intentos_fallidos     INTEGER     NOT NULL DEFAULT 0,
    bloqueado_hasta       TIMESTAMPTZ,
    version               BIGINT      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT fk_usuario_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT uq_usuario_identificador_acceso UNIQUE (identificador_acceso),
    CONSTRAINT ck_usuario_intentos_fallidos CHECK (intentos_fallidos >= 0)
);

CREATE INDEX ix_usuario_tenant_id ON usuario (tenant_id);

-- ----------------------------------------------------------------------------
-- rol
--   Conjunto nombrado de permisos. tenant_id NULL cuando es un rol predefinido
--   de sistema (predefinido = TRUE); NOT NULL cuando es un Rol_Personalizado de
--   una Empresa. Unicidad del nombre POR TENANT para los personalizados y de
--   forma global para los predefinidos (ver indices parciales).
-- ----------------------------------------------------------------------------
CREATE TABLE rol (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID,
    nombre       VARCHAR(120) NOT NULL,
    predefinido  BOOLEAN     NOT NULL DEFAULT FALSE,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255),
    CONSTRAINT pk_rol PRIMARY KEY (id),
    CONSTRAINT fk_rol_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id)
);

CREATE INDEX ix_rol_tenant_id ON rol (tenant_id);

-- Unicidad del nombre de rol POR TENANT (roles personalizados de una Empresa).
CREATE UNIQUE INDEX uq_rol_nombre_por_tenant
    ON rol (tenant_id, nombre)
    WHERE tenant_id IS NOT NULL;

-- Unicidad global del nombre para los roles predefinidos de sistema
-- (tenant_id IS NULL): evita duplicar 'super_admin', 'ventas', etc.
CREATE UNIQUE INDEX uq_rol_nombre_predefinido
    ON rol (nombre)
    WHERE tenant_id IS NULL;

-- ----------------------------------------------------------------------------
-- permiso
--   Autorizacion atomica (recurso, operacion). Catalogo de plataforma comun a
--   todas las Empresas (no tenant-scoped): los permisos atomicos son fijos del
--   Sistema y los roles los combinan.
-- ----------------------------------------------------------------------------
CREATE TABLE permiso (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    recurso     VARCHAR(100) NOT NULL,
    operacion   VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_permiso PRIMARY KEY (id),
    CONSTRAINT uq_permiso_recurso_operacion UNIQUE (recurso, operacion)
);

-- ----------------------------------------------------------------------------
-- rol_permiso (N:M entre rol y permiso)
-- ----------------------------------------------------------------------------
CREATE TABLE rol_permiso (
    rol_id      UUID        NOT NULL,
    permiso_id  UUID        NOT NULL,
    CONSTRAINT pk_rol_permiso PRIMARY KEY (rol_id, permiso_id),
    CONSTRAINT fk_rol_permiso_rol     FOREIGN KEY (rol_id)     REFERENCES rol (id)     ON DELETE CASCADE,
    CONSTRAINT fk_rol_permiso_permiso FOREIGN KEY (permiso_id) REFERENCES permiso (id) ON DELETE CASCADE
);

CREATE INDEX ix_rol_permiso_permiso_id ON rol_permiso (permiso_id);

-- ----------------------------------------------------------------------------
-- usuario_rol (N:M entre usuario y rol)
-- ----------------------------------------------------------------------------
CREATE TABLE usuario_rol (
    usuario_id  UUID        NOT NULL,
    rol_id      UUID        NOT NULL,
    CONSTRAINT pk_usuario_rol PRIMARY KEY (usuario_id, rol_id),
    CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_usuario_rol_rol     FOREIGN KEY (rol_id)     REFERENCES rol (id)     ON DELETE CASCADE
);

CREATE INDEX ix_usuario_rol_rol_id ON usuario_rol (rol_id);
