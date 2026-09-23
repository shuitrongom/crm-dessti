-- ============================================================================
-- V55__plan_giro_moneda_precios.sql
--
-- REDISENO DEL MODELO DE PLAN (plataforma-multigiro).
--
-- Hasta V54 un Plan era un catalogo de plataforma que solo definia limites
-- (max_usuarios) y un ARRAY de claves de modulo habilitadas
-- (modulos_habilitados JSONB). Esta migracion enriquece el Plan para que ademas:
--
--   1) Pertenezca a UN Giro (vertical de negocio): columna `giro_id` con FK a
--      `giro(id)`. Un Plan se disena para un Giro concreto; sus modulos de
--      vertical deben pertenecer a ese Giro o al Nucleo Comun.
--   2) Se cotice en UNA moneda: columna `moneda_codigo` con FK a `moneda(codigo)`.
--   3) Almacene un PRECIO POR MODULO: columna `precios_modulos` JSONB, un objeto
--      JSON que mapea clave-de-modulo -> precio (numero). El TOTAL del Plan es la
--      suma de los precios de sus modulos (lo calcula el dominio).
--
-- COHERENCIA DE CLAVES:
--   `modulos_habilitados` (array) sigue siendo la lista AUTORITATIVA de claves de
--   modulo habilitadas del Plan y se mantiene SINCRONIZADA con las claves de
--   `precios_modulos`: el conjunto de claves de `precios_modulos` es EXACTAMENTE
--   el conjunto de elementos de `modulos_habilitados`. La entidad Plan deriva
--   `modulos_habilitados` de las claves del mapa de precios para evitar
--   divergencias. `modulos_habilitados` se conserva para no romper el gating de
--   modulos (ServicioEmpresas / ModulosPlanValidacion / facturacion de renta)
--   que ya lo consume.
--
-- NO DESTRUCTIVO / COMPATIBILIDAD:
--   * `giro_id` y `moneda_codigo` se agregan NULLABLE para preservar las filas
--     existentes de `plan` (Planes "legado" quedan con giro/moneda sin definir).
--   * `precios_modulos` es NOT NULL con DEFAULT '{}'::jsonb: los Planes legado
--     quedan sin precios, y el dominio trata un precio ausente como 0 y un total
--     vacio como 0.00. El super_admin debera reeditar esos Planes para fijar su
--     Giro, su moneda y los precios por modulo.
--   * `plan` es dato de PLATAFORMA y NO lleva politicas RLS (decision
--     documentada en V1/V2): aqui NO se toca RLS ni logica tenant-scoped.
--
-- Migracion versionada y NO destructiva (solo agrega columnas y restricciones FK).
-- ============================================================================

-- IF NOT EXISTS para tolerar re-aplicaciones manuales sin dejar estado parcial
-- (Flyway envuelve la migracion en una sola transaccion).
ALTER TABLE plan ADD COLUMN IF NOT EXISTS giro_id         UUID;
ALTER TABLE plan ADD COLUMN IF NOT EXISTS moneda_codigo   VARCHAR(3);
ALTER TABLE plan ADD COLUMN IF NOT EXISTS precios_modulos JSONB NOT NULL DEFAULT '{}'::jsonb;

-- Clave foranea al catalogo de Giros de plataforma (giro.id, V50). ON DELETE no
-- se especifica (RESTRICT por defecto): no se permite borrar un Giro referenciado
-- por un Plan.
ALTER TABLE plan
    ADD CONSTRAINT fk_plan_giro
    FOREIGN KEY (giro_id) REFERENCES giro (id);

-- Clave foranea al catalogo de Monedas de plataforma (moneda.codigo, V22).
ALTER TABLE plan
    ADD CONSTRAINT fk_plan_moneda
    FOREIGN KEY (moneda_codigo) REFERENCES moneda (codigo);

COMMENT ON COLUMN plan.giro_id IS
    'Giro (vertical) al que pertenece el Plan (FK giro.id, V50). NULLABLE: los Planes legado quedan sin Giro; los Planes nuevos/actualizados lo exigen (Req plataforma-multigiro).';
COMMENT ON COLUMN plan.moneda_codigo IS
    'Moneda de cotizacion del Plan (FK moneda.codigo, V22). NULLABLE por compatibilidad con Planes legado; obligatoria para Planes nuevos/actualizados.';
COMMENT ON COLUMN plan.precios_modulos IS
    'Precio por modulo del Plan como objeto JSON {clave_modulo: precio}. El total del Plan es la suma de estos precios. Sus claves coinciden EXACTAMENTE con modulos_habilitados (lista autoritativa). Ausencia de precio = 0; objeto vacio = total 0.00.';
