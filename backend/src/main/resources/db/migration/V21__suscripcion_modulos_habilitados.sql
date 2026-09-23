-- ============================================================================
-- V21__suscripcion_modulos_habilitados.sql
--
-- Override por Empresa del subconjunto de modulos habilitados (Req 25.4).
--
-- Contexto: cada Empresa (tenant) se vincula a un Plan a traves de una
-- Suscripcion. El Plan define el catalogo de modulos contratables en su columna
-- `plan.modulos_habilitados` (V1). Hasta ahora una Empresa heredaba SIEMPRE
-- TODOS los modulos de su Plan. Esta migracion permite que el super_admin
-- seleccione, para UNA Empresa concreta, un SUBCONJUNTO especifico de los
-- modulos del Plan (p. ej. el Plan permite 8 modulos pero esta Empresa solo
-- recibe 3), y que lo edite despues.
--
-- El override se guarda en la propia Suscripcion (la relacion Empresa<->Plan),
-- como un array JSONB de nombres de modulo normalizados (minusculas, sin
-- espacios), coherente con `plan.modulos_habilitados`.
--
-- ----------------------------------------------------------------------------
-- SEMANTICA NULL vs valor (IMPORTANTE)
-- ----------------------------------------------------------------------------
--   * `modulos_habilitados` NULL  => la Empresa HEREDA TODOS los modulos del
--       Plan (comportamiento historico preservado; es el DEFAULT).
--   * `modulos_habilitados` con un array (incluido `[]`) => la Empresa recibe
--       EXACTAMENTE ese subconjunto de modulos. Un array vacio `[]` significa
--       CERO modulos habilitados para esa Empresa (todos denegados -> 403).
--   * La capa de aplicacion garantiza que el subconjunto elegido este SIEMPRE
--       contenido en `plan.modulos_habilitados` (si no, HTTP 422).
--
-- Por eso la columna es NULLABLE y SIN DEFAULT: null y `[]` tienen significados
-- distintos y no deben confundirse.
--
-- ----------------------------------------------------------------------------
-- RLS
-- ----------------------------------------------------------------------------
--   La tabla `suscripcion` es tenant-scoped y tiene RLS habilitada desde V2
--   (policy `tenant_isolation`). Un `ADD COLUMN ... JSONB` nullable NO altera
--   las politicas RLS existentes ni la visibilidad de filas: la nueva columna
--   simplemente pasa a formar parte de las filas ya gobernadas por la policy.
--
--   Idempotencia: se usa IF NOT EXISTS para tolerar re-aplicaciones manuales.
-- ============================================================================

ALTER TABLE suscripcion
    ADD COLUMN IF NOT EXISTS modulos_habilitados JSONB;

COMMENT ON COLUMN suscripcion.modulos_habilitados IS
    'Subconjunto de modulos habilitados para ESTA Empresa (Req 25.4). NULL = '
    'hereda todos los modulos del Plan (comportamiento por defecto); un array '
    'JSONB (incluido []) = subconjunto especifico de esa Empresa, que debe ser '
    'subconjunto de plan.modulos_habilitados (validado por la aplicacion, 422). '
    '[] significa cero modulos habilitados para la Empresa.';
