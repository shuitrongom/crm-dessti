-- ============================================================================
-- V65__dependencia_operacion_inventario_avanzado.sql
-- ============================================================================
--
-- Backfill idempotente de la dependencia de modulos:
--     'inventario-avanzado'  REQUIERE  'operacion'  (dependencia unidireccional)
--
-- CONTEXTO Y PROPOSITO
--   El modulo 'inventario-avanzado' opera SOBRE los Materiales del modulo
--   'operacion' (inventario base). Historicamente la plataforma permitia que un
--   Plan, un Paquete de Suscripcion o una Suscripcion (override por Empresa)
--   tuvieran 'inventario-avanzado' SIN 'operacion'. Ese estado deja el modulo
--   avanzado inutilizable: no hay Materiales que operar y el endpoint
--   POST /api/v1/materiales (que exige el modulo 'operacion') responde 403.
--
--   A partir de ahora la dependencia se normaliza al persistir (Plan,
--   PaqueteSuscripcion y override de Suscripcion). Esta migracion corrige el
--   ESTADO ROTO YA EXISTENTE en la base de datos, incluida la empresa "tester"
--   (id cf1acdb6-75e6-40f4-a28d-d364142fbfbe), cuyo override
--   ["comercial","inventario-avanzado","estrategia","redes-sociales"] queda
--   completado con "operacion".
--
-- ESTRATEGIA (JSONB, idempotente)
--   Para cada tabla cuyo array `modulos_habilitados` contenga
--   'inventario-avanzado' y NO contenga 'operacion':
--     * `plan` y `paquete_suscripcion`: se agrega 'operacion' al array
--       `modulos_habilitados` y la clave 'operacion' a `precios_modulos` con
--       precio 0.00 (dependencia sin costo adicional), manteniendo el invariante
--       "claves de precios_modulos == elementos de modulos_habilitados".
--     * `suscripcion` (override): solo se agrega 'operacion' al array; la
--       suscripcion no tiene precios por modulo.
--
--   El predicado
--       WHERE modulos_habilitados @> '["inventario-avanzado"]'::jsonb
--         AND NOT (modulos_habilitados @> '["operacion"]'::jsonb)
--   hace la operacion IDEMPOTENTE: reaplicarla no encuentra filas (no duplica
--   'operacion'). El operador `||` agrega sin alterar el resto del array y
--   `jsonb_set(..., true)` solo crea la clave si falta, preservando los demas
--   modulos y datos (Req 10.1-10.5).
--
--   Nombres de columna verificados contra migraciones existentes:
--     * plan.modulos_habilitados (jsonb, V1) / plan.precios_modulos (jsonb, V55)
--     * paquete_suscripcion.modulos_habilitados y .precios_modulos (jsonb, V64)
--     * suscripcion.modulos_habilitados (jsonb, override NULLABLE, V21;
--       NULL = hereda del instrumento y NO se toca)
-- ----------------------------------------------------------------------------

-- Planes: array modulos_habilitados + objeto precios_modulos.
UPDATE plan
SET modulos_habilitados = modulos_habilitados || '["operacion"]'::jsonb,
    precios_modulos      = jsonb_set(precios_modulos, '{operacion}', '0.00'::jsonb, true)
WHERE modulos_habilitados @> '["inventario-avanzado"]'::jsonb
  AND NOT (modulos_habilitados @> '["operacion"]'::jsonb);

-- Paquetes de suscripcion: mismo patron que `plan`.
UPDATE paquete_suscripcion
SET modulos_habilitados = modulos_habilitados || '["operacion"]'::jsonb,
    precios_modulos      = jsonb_set(precios_modulos, '{operacion}', '0.00'::jsonb, true)
WHERE modulos_habilitados @> '["inventario-avanzado"]'::jsonb
  AND NOT (modulos_habilitados @> '["operacion"]'::jsonb);

-- Suscripciones con override (modulos_habilitados NO nulo): solo el array,
-- sin precios. Incluye el caso de la empresa "tester".
UPDATE suscripcion
SET modulos_habilitados = modulos_habilitados || '["operacion"]'::jsonb
WHERE modulos_habilitados IS NOT NULL
  AND modulos_habilitados @> '["inventario-avanzado"]'::jsonb
  AND NOT (modulos_habilitados @> '["operacion"]'::jsonb);
