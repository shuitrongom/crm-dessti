-- ============================================================================
-- V50__catalogo_giros.sql
--
-- Catalogo de Giros de la PLATAFORMA MULTIGIRO (Tarea 2.1, Req 1.2, 8.4, 11.1).
-- Introduce el eje "Giro" como vertical de negocio al que pertenece una Empresa
-- (p. ej. `anuncios-luminosos`, `manufactura`). El Giro es un ATRIBUTO de la
-- Empresa (tenant), NUNCA un nuevo eje de aislamiento (Req 8.1): el aislamiento
-- sigue rigiendose por `tenant_id` + RLS de las tablas de negocio.
--
-- Esta migracion cubre exclusivamente el andamiaje de datos del Catalogo_Giros:
--   1) crea la tabla `giro` (dato de PLATAFORMA, sin RLS),
--   2) siembra el Giro `anuncios-luminosos` como activo (Req 11.1),
--   3) siembra el recurso RBAC `giro` (crear/listar/activar/desactivar) y lo
--      asigna al rol predefinido `super_admin` (Req 1.1/1.2).
--
-- El enlace de la Empresa a su Giro (columna `empresa.giro_id`, backfill y
-- NOT NULL) se realiza en la migracion posterior `V51` (design.md, "Data
-- Models"); aqui NO se toca la tabla `empresa`.
--
-- ----------------------------------------------------------------------------
-- CONVENCIONES Y DECISIONES
-- ----------------------------------------------------------------------------
--   1. DATO DE PLATAFORMA, SIN RLS (Req 8.4). `giro` es un catalogo de
--      plataforma administrado por el Super_Administrador, coherente con el
--      tratamiento de las tablas `empresa` y `plan` (V1), que TAMPOCO llevan
--      Row-Level Security por `tenant_id`. Un Giro no pertenece a ninguna
--      Empresa: es compartido por todas. Por tanto NO se habilita RLS ni se
--      define politica `tenant_isolation`; el control de acceso lo aporta el
--      RBAC de plataforma (`@autorizador.tiene('giro', ...)`, recurso reservado
--      a `super_admin`). Habilitar RLS aqui romperia el arranque/siembra y las
--      consultas de plataforma que operan sin `app.current_tenant` fijado
--      (mismo motivo por el que `empresa` no la lleva; ver V2/V48).
--
--   2. COLUMNAS DE AUDITORIA / VERSION. Se replica EXACTAMENTE el patron de las
--      tablas de plataforma de V1 (`empresa`, `plan`):
--        * `version`    BIGINT      NOT NULL DEFAULT 0  (concurrencia optimista),
--        * `created_at` TIMESTAMPTZ NOT NULL DEFAULT now()  (UTC),
--        * `updated_at` TIMESTAMPTZ NOT NULL DEFAULT now()  (UTC),
--        * `created_by` VARCHAR(255) NULL,
--        * `updated_by` VARCHAR(255) NULL.
--
--   3. CLAVE CANONICA NORMALIZADA (Req 1.2). `clave` es UNICA y se persiste
--      normalizada a minusculas en formato kebab (p. ej. `anuncios-luminosos`).
--      La normalizacion/validacion de forma la aplica el dominio `Giro`
--      (Tarea 2.2); en BD se garantiza la unicidad (uq_giro_clave) y, como
--      refuerzo declarativo, un CHECK de minusculas y de no-vacio.
--
--   4. UUID. `id` usa gen_random_uuid() por DEFAULT (pgcrypto habilitado en V1),
--      coherente con V1/V11..V47; la fabrica de dominio tambien puede aportar su
--      propio UUID. Para el Giro sembrado se usa un UUID FIJO/DETERMINISTA (como
--      los roles predefinidos de V5) para que futuras migraciones (V51 backfill)
--      y pruebas puedan enlazarlo de forma reproducible; aun asi el enlace real
--      se hace por su clave natural `clave` = 'anuncios-luminosos'.
--
--   5. IDEMPOTENCIA. La migracion Flyway se aplica una sola vez; aun asi la
--      siembra usa ON CONFLICT (...) DO NOTHING (por la clave natural de cada
--      tabla) para tolerar re-siembras manuales sin romper, igual que V5/V44.
--
-- Requisitos cubiertos:
--   - Req 1.2  : persistir el Giro con identificador unico y clave canonica
--                normalizada (unicidad + CHECK de minusculas).
--   - Req 8.4  : `giro` es dato de plataforma, SIN politicas RLS por tenant_id,
--                coherente con `empresa`.
--   - Req 11.1 : sembrar el Giro `anuncios-luminosos` como Giro activo.
--   - Req 1.1  : recurso RBAC `giro` (crear/listar/activar/desactivar) para el
--                Super_Administrador (`super_admin`).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) TABLA giro
--   Catalogo de Giros de plataforma (Req 1.2). Dato de plataforma, SIN RLS
--   (Req 8.4, DECISION 1). No lleva `tenant_id`: no pertenece a una Empresa.
-- ----------------------------------------------------------------------------
CREATE TABLE giro (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    -- Clave canonica normalizada a minusculas, formato kebab (Req 1.2). Unica.
    clave          VARCHAR(60)  NOT NULL,
    nombre_visible VARCHAR(150) NOT NULL,
    -- Descripcion opcional del vertical de negocio.
    descripcion    TEXT,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Columnas de plataforma (patron de empresa/plan en V1): concurrencia
    -- optimista y auditoria en UTC.
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    CONSTRAINT pk_giro PRIMARY KEY (id),
    -- Clave canonica unica en toda la plataforma (Req 1.2, 1.3).
    CONSTRAINT uq_giro_clave UNIQUE (clave),
    -- Refuerzo declarativo de la normalizacion: clave en minusculas y no vacia,
    -- y nombre visible no vacio (la normalizacion completa la aplica el dominio).
    CONSTRAINT ck_giro_clave_normalizada CHECK (clave = lower(clave) AND length(btrim(clave)) >= 1),
    CONSTRAINT ck_giro_nombre_visible_no_vacio CHECK (length(btrim(nombre_visible)) >= 1)
);

-- Apoyo a los listados filtrados por estado activo/inactivo (Req 1.6).
CREATE INDEX ix_giro_activo ON giro (activo);

-- NOTA (Req 8.4): intencionalmente NO se ejecuta
--   ALTER TABLE giro ENABLE/FORCE ROW LEVEL SECURITY;
-- ni se crea politica `tenant_isolation`. `giro` es dato de plataforma,
-- coherente con `empresa` y `plan` (V1), que tampoco llevan RLS.

-- ----------------------------------------------------------------------------
-- 2) SIEMBRA DEL GIRO `anuncios-luminosos` (Req 11.1)
--   Primer Giro de la plataforma, activo. UUID fijo/determinista (DECISION 4)
--   para enlace reproducible; el enlace real se hace por la clave natural.
--   Idempotente por ON CONFLICT (clave) (DECISION 5).
-- ----------------------------------------------------------------------------
INSERT INTO giro (id, clave, nombre_visible, descripcion, activo) VALUES
    ('c1a00000-0000-0000-0000-000000000001',
     'anuncios-luminosos',
     'Anuncios Luminosos',
     'Vertical de anuncios luminosos: diseno, fabricacion, levantamiento de sitio, permisos e instalacion de senalizacion luminosa. Primer giro enchufable de la plataforma multigiro.',
     TRUE)
ON CONFLICT (clave) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3) RECURSO RBAC `giro` Y ASIGNACION A super_admin (Req 1.1, 1.2)
--   El recurso `giro` es de NIVEL PLATAFORMA (administracion del Catalogo_Giros
--   por el Super_Administrador), coherente con `empresa`/`plan`/`suscripcion`
--   de V5. Solo el rol predefinido `super_admin` recibe sus permisos.
--   Operaciones: crear, listar, activar, desactivar (ServicioGiros, Tarea 2.5).
--   Idempotente (patron de V5 y V44).
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES
    ('giro', 'crear'),
    ('giro', 'listar'),
    ('giro', 'activar'),
    ('giro', 'desactivar')
ON CONFLICT (recurso, operacion) DO NOTHING;

-- Asignacion al rol de plataforma super_admin (id fijo de V5), por subconsulta
-- sobre la clave natural (recurso, operacion) para no depender del UUID de
-- permiso, replicando el patron de V5 seccion 3.1.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000001', p.id
FROM permiso p
WHERE p.recurso = 'giro'
  AND p.operacion IN ('crear', 'listar', 'activar', 'desactivar')
ON CONFLICT DO NOTHING;

COMMENT ON TABLE giro IS
    'Catalogo de Giros (verticales de negocio) de la plataforma multigiro. Dato de plataforma sin RLS (Req 8.4), coherente con empresa/plan. El Giro es atributo de la Empresa, no eje de aislamiento (Req 8.1).';
