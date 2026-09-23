-- ============================================================================
-- V16__comercial_prueba_diseno.sql
--
-- Submodulo `pruebas de diseno` (aprobacion de arte) del modulo comercial-crm
-- (Tarea 18.1, Req 15, 12, 23). Replica EXACTAMENTE el patron reutilizable
-- establecido en V11..V15 para toda tabla tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V14.
--
-- Requisitos cubiertos:
--   - Req 15.1 (generacion con version 1 y estado 'pendiente' a partir de una
--     Cotizacion existente): cotizacion_id NOT NULL con FK -> cotizacion;
--     numero_version INTEGER NOT NULL (la primera es 1); estado VARCHAR(20)
--     NOT NULL DEFAULT 'pendiente'. La regla la aplica el dominio.
--   - Req 15.2 (aprobacion con actor y UTC): aprobada_por VARCHAR(255) nullable
--     y decidida_en TIMESTAMPTZ nullable, que la aplicacion rellena con el actor
--     y el instante UTC (Clock) de la decision.
--   - Req 15.3 (rechazo genera una NUEVA Prueba_Diseno con version + 1 y estado
--     'pendiente'): la nueva fila la inserta el dominio/aplicacion; la unicidad
--     (tenant_id, cotizacion_id, numero_version) garantiza el versionado
--     monotono (Property 8) a nivel de BD.
--   - Req 15.4 (historial inmutable): no se exponen operaciones de UPDATE que
--     muten filas historicas ni DELETE; la aplicacion solo permite la transicion
--     'pendiente' -> {'aprobada','rechazada'} sobre la version pendiente vigente.
--   - Req 15.6 (listado paginado por Cotizacion): indice de apoyo.
--   - Req 15.7 (auditoria del cambio de estado con actor/version/estados/UTC):
--     la registra la aplicacion.
--   - Req 23 (multi-tenant): tenant_id + RLS.
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. numero_version vs version (CRITICO): la columna heredada `version` BIGINT
--      es el CONTADOR DE CONCURRENCIA OPTIMISTA (@Version de Hibernate, Req 49) y
--      NO debe confundirse con la version de negocio de la Prueba_Diseno. La
--      version de negocio (1, 2, 3, ...) se modela en una columna SEPARADA
--      `numero_version` INTEGER NOT NULL. Reusar `version` para el numero de
--      version romperia el bloqueo optimista y el versionado monotono. Ambas
--      columnas coexisten con proposito distinto.
--   2. UNIQUE (tenant_id, cotizacion_id, numero_version): garantiza que dentro de
--      un tenant, cada Cotizacion tiene numeros de version unicos y estrictamente
--      crecientes (Property 8: el rechazo genera max(numero_version)+1). Impide
--      duplicar una version por concurrencia.
--   3. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V15.
--   4. PERMISOS: V5 ya sembro prueba_diseno:{crear,leer,listar,cambiar_estado} y
--      los asigno al rol `diseno` (UUID a0000000-...-000000000006, Req 27.3). Por
--      tanto V16 NO necesita sembrar permisos adicionales.
--   5. FK cotizacion_id -> cotizacion sin cascada: el historial de Prueba_Diseno
--      es inmutable (Req 15.4); no se elimina en cascada.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- prueba_diseno
--   Prueba de diseno (arte) versionada, vinculada a una Cotizacion (Req 15).
--   tenant-scoped (Req 23). Estado inicial 'pendiente' (Req 15.1); estados
--   finales 'aprobada'/'rechazada' (Req 15.2/15.3). El rechazo genera una nueva
--   fila con numero_version + 1 (Req 15.3, Property 8).
-- ----------------------------------------------------------------------------
CREATE TABLE prueba_diseno (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    cotizacion_id   UUID           NOT NULL,
    -- Version de NEGOCIO (1, 2, 3, ...). SEPARADA de la columna `version`
    -- (concurrencia optimista). Ver DECISION 1.
    numero_version  INTEGER        NOT NULL,
    estado          VARCHAR(20)    NOT NULL DEFAULT 'pendiente',
    -- Actor que decidio (aprobo o rechazo) y momento UTC de la decision
    -- (Req 15.2/15.7); nulos mientras la prueba esta 'pendiente'.
    aprobada_por    VARCHAR(255),
    rechazada_por   VARCHAR(255),
    decidida_en     TIMESTAMPTZ,
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_prueba_diseno PRIMARY KEY (id),
    CONSTRAINT fk_prueba_diseno_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_prueba_diseno_cotizacion FOREIGN KEY (cotizacion_id) REFERENCES cotizacion (id),
    -- Estados permitidos (Req 15). Etiquetas ASCII minusculas, coherente con V14.
    CONSTRAINT ck_prueba_diseno_estado CHECK (
        estado IN ('pendiente', 'aprobada', 'rechazada')),
    -- La version de negocio arranca en 1 y es estrictamente positiva (Req 15.1).
    CONSTRAINT ck_prueba_diseno_numero_version CHECK (numero_version >= 1),
    -- Versionado monotono (Property 8, Req 15.3): dentro del tenant, cada
    -- Cotizacion tiene numeros de version unicos.
    CONSTRAINT uq_prueba_diseno_version UNIQUE (tenant_id, cotizacion_id, numero_version)
);

CREATE INDEX ix_prueba_diseno_tenant_id ON prueba_diseno (tenant_id);

-- Apoyo al listado paginado por Cotizacion (Req 15.6) y al calculo de
-- max(numero_version) para el rechazo (Req 15.3), siempre acotado al tenant.
CREATE INDEX ix_prueba_diseno_tenant_cotizacion ON prueba_diseno (tenant_id, cotizacion_id);

-- Apoyo a la consulta de existencia de una Prueba_Diseno 'aprobada' por
-- Cotizacion, precondicion de la Orden_Fabricacion (Req 15.5, bloque 19).
CREATE INDEX ix_prueba_diseno_tenant_cotizacion_estado
    ON prueba_diseno (tenant_id, cotizacion_id, estado);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V14/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE prueba_diseno ENABLE ROW LEVEL SECURITY;
ALTER TABLE prueba_diseno FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON prueba_diseno
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
