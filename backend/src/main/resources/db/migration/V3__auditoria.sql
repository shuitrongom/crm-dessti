-- ============================================================================
-- V3__auditoria.sql
--
-- Servicio de auditoria inmutable encadenado por hash (Tarea 7.1).
-- Crea la tabla `registro_auditoria` conforme al diseno (design.md ->
-- Security -> "Servicio de Auditoria (Req 10)" y Data Models -> "Auditoria").
--
-- Requisito cubierto: Req 10 (registro de auditoria inmutable, encadenado por
-- hash, con valor anterior/nuevo sin secretos, trace_id, UTC, consulta
-- paginada/filtrable y exportacion). NO cubre las alertas configurables
-- (Alerta_Auditoria) ni la verificacion de integridad de la cadena bajo
-- demanda: eso corresponde a la Tarea 7.2.
--
-- ----------------------------------------------------------------------------
-- ORDEN ESTABLE DE LA CADENA: id BIGSERIAL
-- ----------------------------------------------------------------------------
-- A diferencia de las demas entidades (PK UUID), el registro de auditoria usa
-- una PK secuencial (BIGSERIAL) porque la cadena de hash requiere un ORDEN
-- TOTAL y estable de insercion para encadenar cada entrada con la anterior. Un
-- id monotono creciente proporciona ese orden de forma natural y barata, y
-- facilita la verificacion de la cadena (Tarea 7.2) recorriendo por id.
--
-- ----------------------------------------------------------------------------
-- ENCADENAMIENTO POR HASH (Req 10.4, 10.7)  -- documenta la ESTRATEGIA
-- ----------------------------------------------------------------------------
-- Se adopta una CADENA GLOBAL (una sola bitacora encadenada para toda la
-- plataforma, ordenada por id), NO una cadena por tenant. Justificacion:
--   * No repudio de TODA la bitacora: cualquier alteracion o borrado de
--     cualquier registro (de negocio o de plataforma) rompe la cadena y es
--     detectable, incluidas las acciones del ambito de plataforma
--     (tenant_id NULL) que no pertenecen a ningun tenant.
--   * Simplicidad y robustez: un unico "hash_previo" (el del ultimo registro
--     por id) evita mantener N punteros de cabeza por tenant y elimina huecos
--     de encadenamiento cuando un tenant tiene pocos o ningun evento.
-- El calculo del hash y la seleccion del hash_previo se realizan en la capa de
-- aplicacion (ServicioAuditoria), serializando la escritura para garantizar la
-- consistencia de la cadena frente a concurrencia (ver JavaDoc del servicio).
--
-- hash_actual = SHA-256( contenido_canonico_del_registro || hash_previo )
-- El primer registro de la cadena usa un hash_previo semilla (cadena de ceros).
--
-- ----------------------------------------------------------------------------
-- AMBITO MULTI-TENANT Y RLS (decision documentada)
-- ----------------------------------------------------------------------------
-- La tabla es tenant-aware pero NO estrictamente tenant-scoped: tenant_id es
-- NULL para los eventos del ambito de PLATAFORMA (super_admin, Req 24.3) y
-- NOT NULL para los eventos de una Empresa. Por ello NO se habilita RLS aqui
-- (a diferencia de V2): una politica  tenant_id = app.current_tenant  impediria
-- registrar y consultar la auditoria de plataforma (tenant_id NULL), que es un
-- ambito legitimo. La auditoria debe poder registrar eventos de AMBOS ambitos.
-- El aislamiento de lectura por tenant se aplica en la capa de aplicacion /
-- autorizacion (RBAC, tarea 10) y en las consultas del ServicioAuditoria, que
-- filtran por el tenant del contexto cuando corresponde. Si en el futuro se
-- desea reforzar con RLS, debe usarse una politica que contemple explicitamente
-- el ambito de plataforma (p. ej. permitir tenant_id NULL a un rol de
-- plataforma), sin romper el registro de eventos de plataforma.
--
-- ----------------------------------------------------------------------------
-- INMUTABILIDAD (Req 10.4)  -- defensa en profundidad
-- ----------------------------------------------------------------------------
-- La bitacora es APPEND-ONLY: no se permite UPDATE ni DELETE.
--   1) A nivel de PERMISOS de BD: el rol de la aplicacion NO debe tener
--      privilegios UPDATE/DELETE sobre esta tabla (solo INSERT/SELECT). Esto se
--      configura al aprovisionar el rol (ver db/roles/app_role.sql). Se
--      documenta aqui como requisito operativo.
--   2) A nivel de BASE DE DATOS (esta migracion): un trigger BEFORE UPDATE OR
--      DELETE lanza una excepcion, de modo que ni siquiera el dueno de la tabla
--      pueda alterar o borrar registros por error. Es la ultima linea de
--      defensa, independiente de la configuracion de permisos.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- registro_auditoria
-- ----------------------------------------------------------------------------
CREATE TABLE registro_auditoria (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY,  -- orden total estable de la cadena
    tenant_id        UUID,                       -- NULL = ambito de plataforma (Req 24.3)
    actor            VARCHAR(255) NOT NULL,      -- quien ejecuta la accion (usuario/sistema)
    accion           VARCHAR(100) NOT NULL,      -- verbo/tipo de accion (login, crear, timbrar, ...)
    recurso          VARCHAR(150) NOT NULL,      -- tipo de recurso afectado (cliente, factura, ...)
    detalle          TEXT,                       -- descripcion adicional legible (sin secretos)
    valor_anterior   JSONB,                      -- estado previo (sin secretos, Req 10.10)
    valor_nuevo      JSONB,                      -- estado nuevo (sin secretos, Req 10.10)
    trace_id         VARCHAR(64),                -- correlacion extremo a extremo (Req 10.12)
    timestamp_utc    TIMESTAMPTZ  NOT NULL DEFAULT now(),  -- marca temporal en UTC
    hash_previo      CHAR(64)     NOT NULL,      -- hash del registro anterior (hex SHA-256)
    hash_actual      CHAR(64)     NOT NULL,      -- hash de este registro (hex SHA-256)
    CONSTRAINT pk_registro_auditoria PRIMARY KEY (id),
    CONSTRAINT uq_registro_auditoria_hash_actual UNIQUE (hash_actual)
);

-- Indices para consulta filtrable (Req 10.5):
--   * por actor
--   * por tipo de recurso (recurso)
--   * por rango de fechas (timestamp_utc)
--   * por tenant_id (aislamiento de lectura por empresa)
CREATE INDEX ix_registro_auditoria_actor         ON registro_auditoria (actor);
CREATE INDEX ix_registro_auditoria_recurso       ON registro_auditoria (recurso);
CREATE INDEX ix_registro_auditoria_timestamp_utc ON registro_auditoria (timestamp_utc);
CREATE INDEX ix_registro_auditoria_tenant_id     ON registro_auditoria (tenant_id);
CREATE INDEX ix_registro_auditoria_trace_id      ON registro_auditoria (trace_id);

-- ----------------------------------------------------------------------------
-- Trigger de INMUTABILIDAD (append-only)  -- Req 10.4, defensa en profundidad
-- ----------------------------------------------------------------------------
-- Impide cualquier UPDATE o DELETE sobre la bitacora, incluso para el dueno de
-- la tabla. La unica operacion permitida es INSERT (append). Complementa la
-- restriccion de permisos del rol de aplicacion (INSERT/SELECT unicamente).
CREATE OR REPLACE FUNCTION fn_registro_auditoria_inmutable()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'registro_auditoria es append-only: no se permite % (Req 10.4)', TG_OP
        USING ERRCODE = 'raise_exception';
END;
$$;

CREATE TRIGGER trg_registro_auditoria_inmutable
    BEFORE UPDATE OR DELETE ON registro_auditoria
    FOR EACH ROW
    EXECUTE FUNCTION fn_registro_auditoria_inmutable();

COMMENT ON TABLE registro_auditoria IS
    'Bitacora de auditoria inmutable, append-only, encadenada por hash SHA-256 (Req 10). '
    'tenant_id NULL = ambito de plataforma. No se permite UPDATE/DELETE (trigger + permisos).';
