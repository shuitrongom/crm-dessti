-- ============================================================================
-- V89__compras_proveedor_datos_fiscales_contacto.sql
--
-- Mejora enterprise del modulo Compras (Req 29): enriquecimiento del catalogo de
-- Proveedores con datos fiscales, de contacto y comerciales, para dejar el
-- registro del Proveedor lo mas completo posible y alimentar las Ordenes de
-- Compra (condiciones de pago) y la facturacion (domicilio/regimen fiscal).
--
-- Hasta ahora el Proveedor solo guardaba nombre/razon social, RFC, email y
-- telefono (V28). Se anaden columnas OPCIONALES (nullable) para no invalidar los
-- Proveedores existentes ni requerir backfill:
--
--   persona_contacto  VARCHAR(200)  -> nombre de la persona de contacto.
--   regimen_fiscal    VARCHAR(10)   -> clave del regimen fiscal (catalogo SAT).
--   dias_credito      INTEGER       -> condiciones de pago (dias de credito), >= 0.
--   domicilio_calle   VARCHAR(300)  -> calle y numero del domicilio fiscal.
--   domicilio_ciudad  VARCHAR(150)  -> ciudad/municipio.
--   domicilio_estado  VARCHAR(150)  -> estado/entidad federativa.
--   codigo_postal     VARCHAR(5)    -> CP (5 digitos en Mexico).
--
-- REGLAS:
--   - Todas las columnas son NULLABLE: el formato y las cotas finas las valida el
--     dominio (ProveedorValidaciones) para dar mensajes 422 claros y homogeneos.
--   - dias_credito, si se informa, no puede ser negativo (CHECK defensivo).
--   - codigo_postal, si se informa, debe ser 5 digitos (CHECK defensivo).
--
-- tenant-scoped y RLS: la tabla `proveedor` ya tiene su aislamiento por tenant
-- desde V28; anadir columnas no lo altera. Idempotente (ADD COLUMN IF NOT EXISTS).
-- ============================================================================

ALTER TABLE proveedor ADD COLUMN IF NOT EXISTS persona_contacto VARCHAR(200);
ALTER TABLE proveedor ADD COLUMN IF NOT EXISTS regimen_fiscal    VARCHAR(10);
ALTER TABLE proveedor ADD COLUMN IF NOT EXISTS dias_credito      INTEGER;
ALTER TABLE proveedor ADD COLUMN IF NOT EXISTS domicilio_calle   VARCHAR(300);
ALTER TABLE proveedor ADD COLUMN IF NOT EXISTS domicilio_ciudad  VARCHAR(150);
ALTER TABLE proveedor ADD COLUMN IF NOT EXISTS domicilio_estado  VARCHAR(150);
ALTER TABLE proveedor ADD COLUMN IF NOT EXISTS codigo_postal     VARCHAR(5);

-- CHECK defensivos (el dominio tambien valida). NOT VALID + VALIDATE para no
-- bloquear la tabla en caliente y aceptar filas preexistentes (todas NULL aqui).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_proveedor_dias_credito_no_negativo') THEN
        ALTER TABLE proveedor
            ADD CONSTRAINT ck_proveedor_dias_credito_no_negativo
            CHECK (dias_credito IS NULL OR dias_credito >= 0) NOT VALID;
        ALTER TABLE proveedor VALIDATE CONSTRAINT ck_proveedor_dias_credito_no_negativo;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_proveedor_codigo_postal_formato') THEN
        ALTER TABLE proveedor
            ADD CONSTRAINT ck_proveedor_codigo_postal_formato
            CHECK (codigo_postal IS NULL OR codigo_postal ~ '^[0-9]{5}$') NOT VALID;
        ALTER TABLE proveedor VALIDATE CONSTRAINT ck_proveedor_codigo_postal_formato;
    END IF;
END $$;
