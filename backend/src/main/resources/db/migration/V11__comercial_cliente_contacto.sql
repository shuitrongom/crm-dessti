-- ============================================================================
-- V11__comercial_cliente_contacto.sql
--
-- Primer modulo de negocio (comercial-crm): tablas `cliente` y `contacto`
-- (Tareas 15.1 y 4.3, Req 5, 23). Establece el PATRON REUTILIZABLE que los
-- modulos de negocio posteriores (16+) deben replicar para toda tabla
-- tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql.
--
-- Requisitos cubiertos:
--   - Req 5   (gestion de Clientes y Contactos): datos obligatorios, borrado
--             logico (activo), unicidad de RFC por tenant en activos (5.3),
--             asociacion de Contacto solo a Cliente activo (5.5/5.6).
--   - Req 23  (multi-tenant): tenant_id + RLS por tabla; unicidad de negocio
--             POR TENANT (23.6, RFC unico dentro de la Empresa, no global).
--   - Req 4.3 (unicidad de negocio por tenant y acceso cruzado): indice unico
--             parcial (tenant_id, rfc) entre activos; el acceso a un recurso de
--             otro tenant devuelve 404 (lo aplica la capa de aplicacion apoyada
--             en el filtro de Hibernate + RLS) y se registra en auditoria.
--   - Req 49  (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1), igual
--      que la baseline; la aplicacion tambien puede proveer su propio UUID.
--   2. timestamptz para las marcas temporales (UTC), coherente con V1.
--   3. UNICIDAD DE RFC POR TENANT ENTRE ACTIVOS (Req 5.3, 23.6, 4.3): se usa un
--      INDICE UNICO PARCIAL sobre (tenant_id, rfc) WHERE activo = TRUE, de modo
--      que el RFC de un Cliente dado de baja logica (activo = FALSE) puede
--      reutilizarse por un nuevo Cliente activo. La capa de aplicacion
--      (ServicioClientes) comprueba existsByRfcAndActivoTrue antes de insertar y
--      traduce la violacion del indice ante carreras concurrentes a HTTP 409.
--   4. BORRADO LOGICO (Req 5.9): columna activo BOOLEAN NOT NULL DEFAULT TRUE;
--      la baja conserva el historico (no se elimina la fila).
--   5. CONTACTO -> CLIENTE: FK cliente_id NOT NULL. La regla "solo a Cliente
--      activo" (Req 5.6) se aplica en la capa de aplicacion (una FK no puede
--      exigir que el Cliente este activo). Ambas tablas comparten tenant.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- cliente
--   Destinatario comercial (persona fisica o moral). tenant-scoped (Req 23).
--   Debe existir al menos un dato de contacto (email valido o telefono); esa
--   regla se valida en la capa de aplicacion (Req 5.1), no por CHECK, para no
--   duplicar la logica de formato (RFC/email/telefono).
-- ----------------------------------------------------------------------------
CREATE TABLE cliente (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    nombre      VARCHAR(200) NOT NULL,
    rfc         VARCHAR(13)  NOT NULL,
    email       VARCHAR(320),
    telefono    VARCHAR(20),
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_cliente PRIMARY KEY (id),
    CONSTRAINT fk_cliente_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id)
);

CREATE INDEX ix_cliente_tenant_id ON cliente (tenant_id);

-- Unicidad del RFC POR TENANT unicamente entre Clientes ACTIVOS (Req 5.3, 23.6,
-- 4.3): permite reutilizar el RFC de un Cliente dado de baja logica.
CREATE UNIQUE INDEX uq_cliente_rfc_activo_por_tenant
    ON cliente (tenant_id, rfc)
    WHERE activo = TRUE;

COMMENT ON INDEX uq_cliente_rfc_activo_por_tenant IS
    'Unicidad del RFC del Cliente POR TENANT entre activos (Req 5.3, 23.6). El '
    'RFC se almacena normalizado a mayusculas por la entidad; la aplicacion '
    'comprueba existsByRfcAndActivoTrue y traduce la violacion a HTTP 409 ante '
    'carreras concurrentes. Un Cliente inactivo (baja logica) libera su RFC.';

-- ----------------------------------------------------------------------------
-- contacto
--   Persona asociada a un Cliente con datos de contacto (Req 5.5). tenant-scoped
--   (Req 23). La regla "solo a Cliente activo" (Req 5.6) se valida en la
--   aplicacion. Incluye borrado logico y version por consistencia con el patron.
-- ----------------------------------------------------------------------------
CREATE TABLE contacto (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    cliente_id  UUID         NOT NULL,
    nombre      VARCHAR(200) NOT NULL,
    email       VARCHAR(320),
    telefono    VARCHAR(20),
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_contacto PRIMARY KEY (id),
    CONSTRAINT fk_contacto_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_contacto_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id)
);

CREATE INDEX ix_contacto_tenant_cliente ON contacto (tenant_id, cliente_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V2__rls_multi_tenant.sql.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE cliente FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON cliente
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE contacto ENABLE ROW LEVEL SECURITY;
ALTER TABLE contacto FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON contacto
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Permisos atomicos faltantes de borrado logico (Req 5.9, 3.1). V5 sembro
-- cliente:{crear,leer,listar,actualizar} y contacto:{crear,leer,listar,
-- actualizar} pero NO la operacion 'eliminar', necesaria para la baja logica
-- del Cliente/Contacto. Se agregan al catalogo y se enlazan al rol predefinido
-- `ventas` (UUID fijo de V5), coherente con Req 27.2 (ventas gestiona Clientes).
-- El guardado REST con @PreAuthorize corresponde a la tarea 15.2.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES ('cliente', 'eliminar'),
       ('contacto', 'eliminar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000005', p.id
FROM permiso p
WHERE (p.recurso = 'cliente'  AND p.operacion = 'eliminar')
   OR (p.recurso = 'contacto' AND p.operacion = 'eliminar')
ON CONFLICT DO NOTHING;
