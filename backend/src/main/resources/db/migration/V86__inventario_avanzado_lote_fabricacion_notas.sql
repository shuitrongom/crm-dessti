-- ============================================================================
-- V86__inventario_avanzado_lote_fabricacion_notas.sql
--
-- Mejora enterprise del modulo Inventario Avanzado (Req 60): enriquecimiento de
-- los datos del Lote. Hasta ahora un Lote solo guardaba su codigo (identidad de
-- negocio, inmutable) y una fecha de caducidad opcional (V26). Para una gestion
-- de lotes mas completa y trazable se anaden DOS columnas OPCIONALES y editables:
--   * fecha_fabricacion DATE          -> cuando se fabrico/recibio el Lote.
--   * notas             VARCHAR(500)  -> observaciones libres (proveedor, remision...).
--
-- DISENO:
--   - Ambas columnas son NULLABLE: los Lotes existentes quedan validos sin
--     necesidad de backfill (compatibilidad hacia atras).
--   - El codigo y el material del Lote SIGUEN siendo inmutables (identidad); solo
--     estos campos descriptivos y la caducidad son editables desde el servicio.
--   - fecha_fabricacion, si se informa, debe ser <= fecha_caducidad cuando ambas
--     existan: esa regla de negocio se valida en el dominio (Lote), no por CHECK,
--     para dar un mensaje 422 claro y homogeneo con el resto del modulo.
--   - notas se acota a 500 caracteres (coherente con movimiento_almacen.motivo de
--     V26) y no puede quedar como cadena de solo espacios (el dominio la normaliza
--     a NULL); aqui solo se fija el tope de longitud.
--
-- tenant-scoped y RLS: la tabla `lote` ya tiene su politica de aislamiento por
-- tenant desde V26; anadir columnas no la altera. Idempotencia: se usa
-- ADD COLUMN IF NOT EXISTS para poder re-ejecutar sin error.
-- ============================================================================

ALTER TABLE lote
    ADD COLUMN IF NOT EXISTS fecha_fabricacion DATE;

ALTER TABLE lote
    ADD COLUMN IF NOT EXISTS notas VARCHAR(500);

-- Tope de longitud de las notas (defensa en profundidad; el dominio tambien lo
-- valida). Se agrega como CHECK NOT VALID + VALIDATE para no bloquear la tabla en
-- caliente y aceptar filas preexistentes (todas NULL en este punto).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_lote_notas_longitud'
    ) THEN
        ALTER TABLE lote
            ADD CONSTRAINT ck_lote_notas_longitud
            CHECK (notas IS NULL OR length(notas) <= 500) NOT VALID;
        ALTER TABLE lote VALIDATE CONSTRAINT ck_lote_notas_longitud;
    END IF;
END $$;
