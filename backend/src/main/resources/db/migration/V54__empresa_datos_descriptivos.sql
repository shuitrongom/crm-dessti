-- ============================================================================
-- V54__empresa_datos_descriptivos.sql
--
-- DATOS DESCRIPTIVOS / DE CONTACTO OPCIONALES de la Empresa (Tenant) (Req 24).
--
-- La migracion V1 creo la tabla `empresa` (unidad tenant; su PK ES el
-- tenant_id) con la identidad basica (nombre, rfc, estado, branding, giro via
-- V51). Esta migracion agrega un conjunto de columnas puramente DESCRIPTIVAS y
-- de CONTACTO de la Empresa (nombre comercial, correo, telefono, sitio web,
-- direccion desglosada y notas libres) para que el super_admin pueda registrar
-- y consultar una ficha mas rica de cada Empresa a nivel de PLATAFORMA.
--
-- Todas las columnas son OPCIONALES (NULLABLE) por dos motivos:
--   1) Son datos descriptivos, no obligatorios para operar el tenant.
--   2) Ya existen filas en `empresa`: agregarlas como NOT NULL romperia la
--      migracion. Se agregan NULLABLE de forma NO destructiva (no altera ni
--      borra datos existentes; solo suma columnas vacias).
--
-- ALCANCE / SEGURIDAD:
--   * `empresa` es dato de PLATAFORMA y NO lleva politicas RLS (decision
--     documentada en V1/V2/V48/V53); aqui NO se toca RLS ni ninguna logica
--     tenant-scoped: estas columnas jamas participan en el aislamiento por
--     tenant (Req 8.1).
--   * `branding_logo` ya existe desde V1 (logo como URL o data-URI); NO se
--     agrega aqui.
--
-- Requisitos cubiertos:
--   - Req 24 : ficha de plataforma de la Empresa mas completa para el super_admin.
--   - Migracion versionada y NO destructiva (solo agrega columnas nullable).
-- ============================================================================

-- IF NOT EXISTS en cada ADD COLUMN para tolerar re-aplicaciones manuales sin
-- dejar estado parcial (Flyway envuelve la migracion en una sola transaccion).
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS nombre_comercial VARCHAR(200);
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS email_contacto   VARCHAR(255);
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS telefono         VARCHAR(40);
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS sitio_web        VARCHAR(255);
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS direccion_calle  VARCHAR(255);
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS direccion_ciudad VARCHAR(120);
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS direccion_estado VARCHAR(120);
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS direccion_cp     VARCHAR(12);
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS direccion_pais   VARCHAR(80);
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS notas            TEXT;

COMMENT ON COLUMN empresa.nombre_comercial IS
    'Nombre comercial (marca) de la Empresa; dato descriptivo opcional de plataforma (Req 24).';
COMMENT ON COLUMN empresa.email_contacto IS
    'Correo de contacto de la Empresa; opcional. Se almacena en minusculas (normalizado por la entidad).';
COMMENT ON COLUMN empresa.telefono IS
    'Telefono de contacto de la Empresa; dato descriptivo opcional de plataforma.';
COMMENT ON COLUMN empresa.sitio_web IS
    'Sitio web de la Empresa; dato descriptivo opcional de plataforma.';
COMMENT ON COLUMN empresa.direccion_calle IS
    'Calle y numero de la direccion de la Empresa; opcional.';
COMMENT ON COLUMN empresa.direccion_ciudad IS
    'Ciudad de la direccion de la Empresa; opcional.';
COMMENT ON COLUMN empresa.direccion_estado IS
    'Estado/provincia de la direccion de la Empresa; opcional.';
COMMENT ON COLUMN empresa.direccion_cp IS
    'Codigo postal de la direccion de la Empresa; opcional.';
COMMENT ON COLUMN empresa.direccion_pais IS
    'Pais de la direccion de la Empresa; opcional.';
COMMENT ON COLUMN empresa.notas IS
    'Notas libres del super_admin sobre la Empresa; dato descriptivo opcional de plataforma.';
