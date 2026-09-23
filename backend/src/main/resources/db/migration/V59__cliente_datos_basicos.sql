-- ============================================================================
-- V59__cliente_datos_basicos.sql
--
-- DATOS BASICOS DE NEGOCIO adicionales del Cliente (comercial-crm, Req 5).
--
-- La migracion V11 creo la tabla `cliente` (tenant-scoped, con RLS) con la
-- identidad basica: nombre, rfc, email, telefono, activo, version y marcas de
-- auditoria. Esta migracion agrega un conjunto de columnas DESCRIPTIVAS y de
-- CONTACTO/DIRECCION del Cliente (nombre comercial, tipo de persona, telefono
-- adicional, direccion desglosada y notas libres) para enriquecer la ficha del
-- Cliente sin alterar las reglas obligatorias ya existentes.
--
-- Todas las columnas son OPCIONALES (NULLABLE) por dos motivos:
--   1) Son datos complementarios, no obligatorios para operar el Cliente (las
--      reglas obligatorias siguen siendo nombre, rfc y al menos un contacto).
--   2) Ya existen filas en `cliente`: agregarlas como NOT NULL romperia la
--      migracion. Se agregan NULLABLE de forma NO destructiva (solo suman
--      columnas vacias; las filas existentes conservan NULL).
--
-- ALCANCE / SEGURIDAD:
--   * NO se toca la Row-Level Security de `cliente` (Capa 2, V11): estas
--     columnas jamas participan en el aislamiento por tenant (Req 23). El
--     `tenant_id` y sus politicas quedan intactos.
--   * NO se tocan las columnas ni los indices existentes (incluido el indice
--     unico parcial uq_cliente_rfc_activo_por_tenant): esta migracion solo
--     SUMA columnas.
--
-- Requisitos cubiertos:
--   - Req 5   : ficha de Cliente mas completa (datos basicos de negocio).
--   - Migracion versionada y NO destructiva (solo agrega columnas nullable).
-- ============================================================================

-- IF NOT EXISTS en cada ADD COLUMN para tolerar re-aplicaciones manuales sin
-- dejar estado parcial (Flyway envuelve la migracion en una sola transaccion).
-- Tamanos alineados con las cotas del dominio (DatosContacto/Cliente).
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS nombre_comercial    VARCHAR(200);
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS tipo_persona        VARCHAR(20);
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS telefono_adicional  VARCHAR(20);
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS direccion_calle     VARCHAR(200);
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS direccion_ciudad    VARCHAR(120);
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS direccion_estado    VARCHAR(120);
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS direccion_cp        VARCHAR(10);
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS direccion_pais      VARCHAR(80);
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS notas               VARCHAR(1000);

-- CHECK LENIENTE de `tipo_persona`: solo admite los valores de negocio
-- ('fisica' | 'moral') o NULL (columna opcional). El dominio ya normaliza y
-- valida el valor (respuesta 422); esta restriccion es una defensa de datos a
-- nivel de base sin volver obligatoria la columna. IS NULL se evalua como
-- verdadero para permitir la ausencia de dato.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_cliente_tipo_persona'
    ) THEN
        ALTER TABLE cliente
            ADD CONSTRAINT ck_cliente_tipo_persona
            CHECK (tipo_persona IS NULL OR tipo_persona IN ('fisica', 'moral'));
    END IF;
END $$;

COMMENT ON COLUMN cliente.nombre_comercial IS
    'Nombre comercial (marca) del Cliente; dato descriptivo opcional (Req 5).';
COMMENT ON COLUMN cliente.tipo_persona IS
    'Tipo de persona del Cliente: ''fisica'' o ''moral''; opcional (validado en el dominio y por ck_cliente_tipo_persona).';
COMMENT ON COLUMN cliente.telefono_adicional IS
    'Telefono secundario del Cliente (10..15 digitos, misma regla que telefono); opcional.';
COMMENT ON COLUMN cliente.direccion_calle IS
    'Calle y numero de la direccion del Cliente; opcional.';
COMMENT ON COLUMN cliente.direccion_ciudad IS
    'Ciudad de la direccion del Cliente; opcional.';
COMMENT ON COLUMN cliente.direccion_estado IS
    'Estado/provincia de la direccion del Cliente; opcional.';
COMMENT ON COLUMN cliente.direccion_cp IS
    'Codigo postal de la direccion del Cliente; opcional.';
COMMENT ON COLUMN cliente.direccion_pais IS
    'Pais de la direccion del Cliente; opcional.';
COMMENT ON COLUMN cliente.notas IS
    'Notas libres sobre el Cliente; dato descriptivo opcional (max. 1000).';
