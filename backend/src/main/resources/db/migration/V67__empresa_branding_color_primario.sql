-- ============================================================================
-- V67__empresa_branding_color_primario.sql
--
-- Persistencia backend del color primario de marca por empresa (spec
-- tematizacion-empresa-enterprise, tarea 4.1). Agrega a la tabla `empresa` la
-- columna `branding_color_primario` que guarda el Color_Primario_Marca elegido
-- por el administrador de cada empresa desde /empresa/administracion/branding.
-- A partir de este unico color el frontend deriva en runtime la paleta completa
-- (Servicio_Tematizacion) garantizando contraste WCAG 2.1 AA.
--
-- La columna es VARCHAR(7) NULLABLE, sin DEFAULT (por lo tanto su valor por
-- defecto es NULL): una empresa que aun no fija color se comporta igual que
-- antes de esta funcionalidad, usando el Tema_Corporativo por defecto.
--
-- Se anade ademas un CHECK que valida el formato hexadecimal `#RRGGBB` (o NULL),
-- defensa en profundidad frente al `@Pattern` del request y a la revalidacion
-- del ServicioBranding.
--
-- Requisitos cubiertos: 6.1 (columna en `empresa`, default nulo, via V67),
--                        9.1 (sin color fijado => apariencia por defecto).
--
-- ----------------------------------------------------------------------------
-- NOMBRES REALES VERIFICADOS (leidos de las migraciones del proyecto):
--   * Tabla:  empresa (V1). Su PK `id` (UUID) ES el tenant_id; la tabla NO
--             tiene columna `tenant_id` propia ni RLS por tenant (se carga por
--             id). Por eso esta migracion NO agrega ni altera ninguna RLS.
--   * Columnas de branding ya existentes (V1):
--             branding_nombre_visible VARCHAR(200), branding_logo TEXT.
--   * Patron de columnas nuevas en `empresa` (V54): ADD COLUMN IF NOT EXISTS +
--             COMMENT ON COLUMN (idempotente ante re-aplicaciones).
--   * Ultima migracion previa: V66; V67 esta libre.
-- ============================================================================

-- 1) Columna nueva: color primario de marca (hex #RRGGBB), NULLABLE, sin DEFAULT.
ALTER TABLE empresa
    ADD COLUMN IF NOT EXISTS branding_color_primario VARCHAR(7);

-- 2) CHECK de formato hexadecimal `#RRGGBB` (o NULL). PostgreSQL no soporta
--    `ADD CONSTRAINT IF NOT EXISTS`, asi que se crea de forma idempotente:
--    solo se agrega si aun no existe una constraint con ese nombre.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_empresa_branding_color_primario'
          AND conrelid = 'empresa'::regclass
    ) THEN
        ALTER TABLE empresa
            ADD CONSTRAINT ck_empresa_branding_color_primario
            CHECK (
                branding_color_primario IS NULL
                OR branding_color_primario ~ '^#[0-9a-fA-F]{6}$'
            );
    END IF;
END $$;

-- 3) Documentacion de la columna.
COMMENT ON COLUMN empresa.branding_color_primario IS
    'Color primario de marca de la empresa en formato hexadecimal #RRGGBB. '
    'A partir de el se deriva la paleta de tematizacion (contraste WCAG 2.1 AA). '
    'NULL = la empresa usa el Tema_Corporativo por defecto (sin color de marca).';
