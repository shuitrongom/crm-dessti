-- ============================================================================
-- V80__comercial_cotizacion_impuestos_descuentos.sql
--
-- Incorpora al agregado Cotizacion el desglose fiscal mexicano (CFDI): IVA por
-- partida (16% / 8% frontera / 0% / exento), descuentos (por partida y global) y
-- retenciones opcionales (ISR e IVA). Hasta ahora la Cotizacion calculaba
-- total = subtotal (suma de partidas) sin impuestos ni descuentos; esta
-- migracion agrega las columnas necesarias para el calculo CFDI completo.
--
-- Patron NO destructivo (replica V60): solo se agregan columnas nullable o con
-- DEFAULT que preservan las filas existentes. Los defaults se eligen para que
-- las Cotizaciones historicas conserven su total actual:
--   * descuentos y retenciones por defecto 0 (no alteran el total),
--   * iva por defecto 0 en cotizacion (el total historico total==subtotal se
--     mantiene: subtotal + 0 (iva) - 0 (desc) - 0 (ret) = subtotal),
--   * en las partidas, tasa_iva por defecto 0.16 e iva 0: la tasa queda lista
--     para futuros recalculos, pero el iva persistido de las filas historicas
--     permanece 0 hasta que la partida se recalcule (no se reescriben filas).
--
-- Aritmetica: NUMERIC(18,2) para importes monetarios (BigDecimal escala 2
-- HALF_UP en Java), coherente con V14. La tasa de IVA se guarda como etiqueta
-- ASCII ('16','8','0','exento') en VARCHAR, coherente con el patron de
-- EstadoCotizacion/EstadoActividad (converter que persiste valorBd()), en lugar
-- de un NUMERIC, para exponer explicitamente el caso 'exento' (distinto de 0%).
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. IVA POR PARTIDA (no global): cada partida lleva su tasa, de modo que una
--      Cotizacion puede mezclar productos gravados al 16% con otros exentos o a
--      tasa 0. El IVA de la Cotizacion es la suma del IVA de cada partida sobre
--      su base neta (subtotal de partida menos su descuento). Asi se respetan
--      tasas mixtas, como exige el CFDI.
--   2. DESCUENTO POR PARTIDA Y GLOBAL: 'descuento' en la partida es un MONTO en
--      la moneda de la Cotizacion que se resta del importe bruto de esa partida
--      ANTES de calcular su IVA. 'descuento_global' en la Cotizacion es un MONTO
--      que se resta del subtotal (suma de bases de partida) para obtener la base
--      gravable. La UI puede capturar porcentajes y convertirlos a monto; la BD
--      y el dominio operan con montos, evitando ambiguedad de redondeo.
--   3. RETENCIONES OPCIONALES (ISR/IVA): montos que se restan del total. Por
--      defecto 0 (la mayoria de las ventas de anuncios luminosos no retienen);
--      quedan disponibles para los casos de servicios que si retienen.
--   4. COLUMNAS DERIVADAS PERSISTIDAS: se persisten 'iva', 'retencion_isr',
--      'retencion_iva' y 'descuento_global' en la Cotizacion, y 'descuento',
--      'tasa_iva', 'iva', 'importe_base' en la partida, para que el PDF, los DTOs
--      y los reportes lean el desglose sin recomputarlo. El dominio los recalcula
--      de forma deterministica al crear/editar (fuente de verdad), de modo que
--      permanecen consistentes con subtotal/total.
--   5. SEMANTICA DE subtotal/total (V14) BAJO EL NUEVO MODELO:
--        subtotal = suma de las bases de partida (cantidad*precio - descuento
--                   de partida), es decir la base ANTES del descuento global.
--        total    = subtotal - descuento_global + iva - retencion_isr - retencion_iva.
--      Se conservan ambas columnas de V14 (no se renombran) para no romper
--      consultas/DTL existentes; su significado se enriquece de forma compatible.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- partida_cotizacion: descuento por partida, tasa de IVA e importes derivados.
-- ----------------------------------------------------------------------------
ALTER TABLE partida_cotizacion
    ADD COLUMN IF NOT EXISTS descuento    NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tasa_iva     VARCHAR(10)    NOT NULL DEFAULT '16',
    ADD COLUMN IF NOT EXISTS importe_base NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS iva          NUMERIC(18, 2) NOT NULL DEFAULT 0;

-- El descuento de la partida no puede ser negativo (un descuento captura una
-- reduccion; el dominio ademas garantiza que no exceda el importe bruto).
ALTER TABLE partida_cotizacion
    ADD CONSTRAINT ck_partida_descuento_no_negativo CHECK (descuento >= 0);

-- Tasas de IVA permitidas (Decision 1). Etiquetas ASCII, coherentes con el
-- converter del dominio (TasaIva.valorBd()).
ALTER TABLE partida_cotizacion
    ADD CONSTRAINT ck_partida_tasa_iva CHECK (tasa_iva IN ('16', '8', '0', 'exento'));

-- Importes derivados no negativos.
ALTER TABLE partida_cotizacion
    ADD CONSTRAINT ck_partida_importe_base_no_negativo CHECK (importe_base >= 0);
ALTER TABLE partida_cotizacion
    ADD CONSTRAINT ck_partida_iva_no_negativo CHECK (iva >= 0);

-- Para las partidas historicas (creadas antes de V80), 'importe_base' arranca en
-- 0 por el DEFAULT; se rellena con el subtotal existente para que el desglose sea
-- coherente de inmediato sin recalcular impuestos (iva permanece 0: no se cobra
-- IVA retroactivo sobre cotizaciones ya emitidas).
UPDATE partida_cotizacion SET importe_base = subtotal WHERE importe_base = 0;

-- ----------------------------------------------------------------------------
-- cotizacion: descuento global, IVA consolidado y retenciones.
-- ----------------------------------------------------------------------------
ALTER TABLE cotizacion
    ADD COLUMN IF NOT EXISTS descuento_global NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS iva              NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS retencion_isr    NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS retencion_iva    NUMERIC(18, 2) NOT NULL DEFAULT 0;

ALTER TABLE cotizacion
    ADD CONSTRAINT ck_cotizacion_descuento_global_no_negativo CHECK (descuento_global >= 0);
ALTER TABLE cotizacion
    ADD CONSTRAINT ck_cotizacion_iva_no_negativo CHECK (iva >= 0);
ALTER TABLE cotizacion
    ADD CONSTRAINT ck_cotizacion_retencion_isr_no_negativo CHECK (retencion_isr >= 0);
ALTER TABLE cotizacion
    ADD CONSTRAINT ck_cotizacion_retencion_iva_no_negativo CHECK (retencion_iva >= 0);
