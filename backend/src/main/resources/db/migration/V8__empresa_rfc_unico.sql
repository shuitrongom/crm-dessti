-- ============================================================================
-- V8__empresa_rfc_unico.sql
--
-- Unicidad del identificador fiscal (RFC) de la Empresa a NIVEL PLATAFORMA
-- (Tarea 14.1, Req 24.2). La migracion V1 creo la tabla `empresa` con la columna
-- `rfc VARCHAR(13) NOT NULL` pero SIN restriccion de unicidad. Al ser la Empresa
-- la propia unidad tenant (su PK es el tenant_id), su RFC identifica a la
-- Empresa dentro de la plataforma y no debe duplicarse entre Empresas: dos
-- tenants con el mismo RFC serian ambiguos para la administracion de plataforma.
--
-- Requisito cubierto: Req 24.2 (alta de Empresa con datos validos y consistentes
--   a nivel plataforma). Nota: la unicidad de RFC de negocio POR TENANT (por
--   ejemplo, el RFC de un Cliente dentro de una Empresa, Req 23.6) es un asunto
--   distinto y se aborda en los modulos de negocio; esta restriccion aplica
--   unicamente a la tabla de plataforma `empresa`.
--
-- ----------------------------------------------------------------------------
-- DECISION: indice unico global sobre empresa.rfc
-- ----------------------------------------------------------------------------
--   * La capa de aplicacion (ServicioEmpresas) ya comprueba `existsByRfc` antes
--     de insertar y normaliza el RFC a MAYUSCULAS en la entidad, por lo que el
--     indice se define sobre el valor almacenado (ya normalizado). El indice
--     actua como segunda linea de defensa ante carreras concurrentes, que el
--     servicio traduce a HTTP 409 (ConflictoUnicidadException).
--   * `empresa` NO tiene politicas RLS (decision documentada en V1/V2: el
--     super_admin debe poder listar/crear Empresas), por lo que un indice unico
--     ordinario es suficiente y no interactua con RLS.
--   * Idempotencia: se usa IF NOT EXISTS para tolerar re-aplicaciones manuales.
-- ============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_empresa_rfc
    ON empresa (rfc);

COMMENT ON INDEX uq_empresa_rfc IS
    'Unicidad del identificador fiscal (RFC) de la Empresa a nivel plataforma '
    '(Req 24.2). El RFC se almacena normalizado a mayusculas por la entidad '
    'Empresa; la aplicacion comprueba existsByRfc y traduce la violacion de este '
    'indice a HTTP 409 ante carreras concurrentes.';
