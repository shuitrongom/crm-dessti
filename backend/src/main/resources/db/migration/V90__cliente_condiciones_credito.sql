-- ============================================================================
-- V90__cliente_condiciones_credito.sql
--
-- CONDICIONES DE CREDITO del Cliente (comercial-crm, Req 5) y permiso de cambio
-- de estado de la Cuenta_Por_Cobrar (contabilidad-finanzas, Req 36).
--
-- Contexto: la V11 creo `cliente`; la V59 le sumo datos basicos. Esta migracion
-- agrega las condiciones de credito necesarias para derivar la fecha_vencimiento
-- de la CxC al timbrar (regla fiscal MX CFDI 4.0 PUE/PPD):
--     fecha_vencimiento = fecha_emision + dias_credito
--   * dias_credito = 0  -> CONTADO (PUE): vence el mismo dia de emision.
--   * dias_credito > 0  -> CREDITO (PPD): vence a emision + dias.
--
-- ALCANCE / SEGURIDAD:
--   * NO se toca la Row-Level Security de `cliente` (Capa 2, V11) ni sus indices
--     (incluido uq_cliente_rfc_activo_por_tenant). Solo SUMA columnas.
--   * dias_credito es NOT NULL DEFAULT 0: las filas existentes quedan en contado,
--     que es el comportamiento fiscal seguro por defecto (no se asume 30).
--   * limite_credito es NULLABLE (opcional): NULL = sin limite definido. Esta
--     fase solo lo captura; su aplicacion al timbrar/pagar es una fase posterior.
--
-- Requisitos cubiertos:
--   - Req 5   : ficha de Cliente con condiciones de credito.
--   - Req 36  : base del aging real (fecha_vencimiento poblada al timbrar).
--   - Req 3   : permiso atomico cuenta_por_cobrar:cambiar_estado (rol contabilidad).
--   - Migracion versionada y NO destructiva (solo agrega columnas nullable/default).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- cliente: condiciones de credito
-- ----------------------------------------------------------------------------
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS dias_credito   INTEGER        NOT NULL DEFAULT 0;
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS limite_credito NUMERIC(18, 2);

-- CHECK de rango de dias_credito (0..365) y de no-negatividad de limite_credito.
-- Se envuelven en IF NOT EXISTS (pg_constraint) para tolerar re-aplicaciones.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_cliente_dias_credito'
    ) THEN
        ALTER TABLE cliente
            ADD CONSTRAINT ck_cliente_dias_credito
            CHECK (dias_credito >= 0 AND dias_credito <= 365);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_cliente_limite_credito'
    ) THEN
        ALTER TABLE cliente
            ADD CONSTRAINT ck_cliente_limite_credito
            CHECK (limite_credito IS NULL OR limite_credito >= 0);
    END IF;
END $$;

COMMENT ON COLUMN cliente.dias_credito IS
    'Dias de credito del Cliente (0..365). 0 = CONTADO (PUE): la CxC vence el mismo '
    'dia de emision. >0 = CREDITO (PPD): vence a emision + dias. NOT NULL DEFAULT 0.';
COMMENT ON COLUMN cliente.limite_credito IS
    'Limite de credito del Cliente en MXN (NUMERIC(18,2)); NULL = sin limite definido. '
    'Fase 1 solo lo captura; su aplicacion al timbrar/pagar es posterior.';

-- ----------------------------------------------------------------------------
-- PERMISO cuenta_por_cobrar:cambiar_estado (Req 3, 27.11)
--   La cancelacion en cascada de la CxC al cancelar su Factura es un cambio de
--   estado de CxC. V31 sembro cuenta_por_cobrar:{leer,listar}; aqui se agrega la
--   operacion de cambio de estado y se enlaza al MISMO rol predefinido
--   `contabilidad` (a0000000-0000-0000-0000-00000000000b), imitando el patron de
--   V33 (cuenta_por_pagar:aplicar_pago): ON CONFLICT DO NOTHING por la clave
--   natural (recurso, operacion) y enlace rol_permiso por subconsulta.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('cuenta_por_cobrar', 'cambiar_estado')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000b', p.id
FROM permiso p
WHERE p.recurso = 'cuenta_por_cobrar' AND p.operacion = 'cambiar_estado'
ON CONFLICT DO NOTHING;
