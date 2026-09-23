-- ============================================================================
-- V61__producto_foto.sql
--
-- FOTO/IMAGEN opcional del Producto del catalogo comercial (comercial-crm,
-- Req 59). Permite identificar visualmente un Producto (por ejemplo, al
-- mostrarlo en una Cotizacion).
--
-- La migracion V12 creo la tabla `producto` (tenant-scoped, con RLS) con la
-- identidad basica: nombre, unidad, descripcion, informacion comercial de
-- apoyo (cliente_meta, alianzas, competencia), activo, version y marcas de
-- auditoria. Esta migracion agrega UNA columna DESCRIPTIVA y OPCIONAL: la foto
-- del Producto, almacenada como una URL o un `data URI` en linea (base64).
--
-- La columna es OPCIONAL (NULLABLE) por dos motivos:
--   1) Es un dato complementario para la identificacion visual, no obligatorio
--      para operar el Producto (las reglas obligatorias siguen siendo nombre,
--      unidad y descripcion, Req 59.1/59.2).
--   2) Ya existen filas en `producto`: agregarla como NOT NULL romperia la
--      migracion. Se agrega NULLABLE de forma NO destructiva (solo suma una
--      columna vacia; las filas existentes conservan NULL).
--
-- CONVENCION (misma que el logotipo de branding de la Empresa, V1/V54):
--   * Tipo TEXT: admite tanto una referencia (URL) como un pequeno `data URI`
--     en linea (base64). No se sube ni almacena un binario/archivo aparte.
--   * NO se fuerza un formato de imagen a nivel de base: la validacion es de
--     PRESENCIA/LONGITUD y se APLICA EN LA CAPA DE APLICACION (dominio), que
--     acota el tamano a ~1 MiB (1.048.576 caracteres) y rechaza un exceso con
--     HTTP 422, replicando `Empresa.actualizarBranding`.
--
-- ALCANCE / SEGURIDAD:
--   * NO se toca la Row-Level Security de `producto` (Capa 2, V12): esta
--     columna jamas participa en el aislamiento por tenant (Req 23). El
--     `tenant_id` y sus politicas quedan intactos.
--   * NO se tocan las columnas ni los indices existentes: esta migracion solo
--     SUMA una columna.
--
-- Requisitos cubiertos:
--   - Req 59  : ficha de Producto mas completa (identificacion visual).
--   - Migracion versionada y NO destructiva (solo agrega columna nullable).
-- ============================================================================

-- IF NOT EXISTS para tolerar re-aplicaciones manuales sin dejar estado parcial
-- (Flyway envuelve la migracion en una sola transaccion).
ALTER TABLE producto ADD COLUMN IF NOT EXISTS foto TEXT;

COMMENT ON COLUMN producto.foto IS
    'Foto/imagen opcional del Producto para identificacion visual (p. ej. en Cotizaciones), como URL o data URI en base64. Nullable; el tamano (~1 MiB) se acota en la capa de aplicacion (dominio), misma convencion que el logotipo de branding de la Empresa.';
