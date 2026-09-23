-- ============================================================================
-- V4__alertas_auditoria.sql
--
-- Alertas configurables de auditoria (Alerta_Auditoria) - Tarea 7.2.
-- Crea la tabla `alerta_auditoria` conforme al diseno (design.md ->
-- Data Models -> "Auditoria" y Security -> "Alertas configurables (Req 10.11)").
--
-- Requisito cubierto: Req 10.11 (reglas configurables que, ante patrones
-- sensibles dentro de su ventana y umbral, emiten una Notificacion al
-- destinatario configurado). NO cubre la entrega real de la Notificacion, que
-- depende del modulo de notificaciones (Tarea 43); aqui solo se persiste la
-- configuracion de la regla y el servicio la evalua tras registrar eventos.
--
-- ----------------------------------------------------------------------------
-- AMBITO MULTI-TENANT Y RLS (decision documentada)
-- ----------------------------------------------------------------------------
-- A diferencia de `registro_auditoria` (append-only, cadena global, sin RLS),
-- `alerta_auditoria` es CONFIGURACION mutable y se modela como TENANT-SCOPED:
--   * Las alertas de una Empresa (tenant_id NOT NULL) las administra su
--     Administrador y solo deben ser visibles/editables dentro de su tenant.
--     Por eso se habilita RLS con la misma politica `tenant_isolation` que el
--     resto de tablas de negocio (patron reutilizable de V2), aplicada SOLO a
--     las filas con tenant_id NOT NULL.
--   * CASO PLATAFORMA (tenant_id NULL): se admiten alertas de ambito de
--     plataforma (p. ej. patron `acceso_otra_empresa` observado a nivel
--     plataforma por el super_admin). Como la politica RLS compara
--     tenant_id = app.current_tenant, una fila con tenant_id NULL NUNCA es
--     visible bajo un tenant de negocio (fail-safe, coherente con V2/usuario/rol):
--     el super_admin administra las alertas de plataforma en su propio ambito
--     (sin tenant fijado) a traves de un caso de uso de plataforma. Esta es la
--     misma estrategia documentada para `usuario`/`rol` en V2.
--
-- Se usa ENABLE + FORCE ROW LEVEL SECURITY (igual que V2) para que la politica
-- aplique tambien al rol dueno/migrador.
-- ============================================================================

CREATE TABLE alerta_auditoria (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID,                        -- NULL = alerta de ambito de plataforma
    patron         VARCHAR(30)  NOT NULL,       -- accesos_denegados | exportacion_masiva | acceso_otra_empresa
    umbral         INTEGER      NOT NULL,       -- numero de eventos que dispara la alerta
    ventana        INTERVAL     NOT NULL,       -- ventana temporal de conteo (p. ej. '15 minutes')
    destinatarios  TEXT         NOT NULL,       -- destinatarios de la Notificacion (lista separada por comas)
    activa         BOOLEAN      NOT NULL DEFAULT TRUE,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    CONSTRAINT pk_alerta_auditoria PRIMARY KEY (id),
    CONSTRAINT ck_alerta_auditoria_patron
        CHECK (patron IN ('accesos_denegados', 'exportacion_masiva', 'acceso_otra_empresa')),
    CONSTRAINT ck_alerta_auditoria_umbral CHECK (umbral >= 1)
);

-- Indice para la busqueda de reglas activas por tenant y patron durante la
-- evaluacion tras registrar un evento.
CREATE INDEX ix_alerta_auditoria_tenant_patron
    ON alerta_auditoria (tenant_id, patron)
    WHERE activa = TRUE;

-- ----------------------------------------------------------------------------
-- RLS (segunda capa) - patron reutilizable de V2 para tablas tenant-scoped.
-- Las filas con tenant_id NULL (ambito plataforma) no son visibles bajo un
-- tenant de negocio (fail-safe); se administran en el ambito de plataforma.
-- ----------------------------------------------------------------------------
ALTER TABLE alerta_auditoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerta_auditoria FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON alerta_auditoria
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

COMMENT ON TABLE alerta_auditoria IS
    'Reglas configurables de alerta de auditoria (Req 10.11): patron sensible, '
    'umbral y ventana; al superarse dentro de la ventana se emite una Notificacion. '
    'tenant_id NULL = ambito de plataforma. Tenant-scoped con RLS (patron V2).';
