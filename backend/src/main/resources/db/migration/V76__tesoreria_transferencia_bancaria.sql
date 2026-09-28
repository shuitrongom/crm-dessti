-- ============================================================================
-- V76__tesoreria_transferencia_bancaria.sql
--
-- Mejora enterprise del modulo `tesoreria`: TRANSFERENCIAS ENTRE CUENTAS
-- BANCARIAS de la misma Empresa (traspaso interno de fondos). Completa la suite
-- de tesoreria (cuentas, estados de cuenta, movimientos, conciliaciones) con el
-- registro del movimiento de dinero entre dos Cuentas_Bancarias propias.
--
-- Crea UNA tabla tenant-scoped replicando EXACTAMENTE el patron de V35:
--   * tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23).
--
-- DECISIONES DE DISENO
--   1. CUENTAS DISTINTAS: CHECK ck_transferencia_cuentas_distintas garantiza que
--      cuenta_origen_id <> cuenta_destino_id (la invariante tambien la valida el
--      dominio de forma pura). FKs a cuenta_bancaria por origen y destino.
--   2. MONTO POSITIVO: NUMERIC(18,2) con CHECK monto > 0 (moneda unica, escala 2).
--   3. INMUTABLE: la transferencia es un hecho historico; no hay update de sus
--      campos financieros (solo version/auditoria heredadas).
--   4. PERMISOS (Req 3, 27.11): permiso nuevo transferencia_bancaria:{crear,leer,
--      listar}, sembrado ON CONFLICT DO NOTHING y asignado al rol `contabilidad`
--      (a0000000-0000-0000-0000-00000000000b), coherente con el resto de tesoreria.
--   5. NUMERO DE MIGRACION V76: siguiente disponible (la mas alta previa es V75).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- transferencia_bancaria
--   Traspaso de fondos entre dos Cuentas_Bancarias de la Empresa. tenant-scoped.
-- ----------------------------------------------------------------------------
CREATE TABLE transferencia_bancaria (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL,
    cuenta_origen_id    UUID           NOT NULL,
    cuenta_destino_id   UUID           NOT NULL,
    monto               NUMERIC(18, 2) NOT NULL,
    fecha               DATE           NOT NULL,
    concepto            VARCHAR(300),
    fecha_registro      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    CONSTRAINT pk_transferencia_bancaria PRIMARY KEY (id),
    CONSTRAINT fk_transferencia_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_transferencia_origen FOREIGN KEY (cuenta_origen_id)
        REFERENCES cuenta_bancaria (id),
    CONSTRAINT fk_transferencia_destino FOREIGN KEY (cuenta_destino_id)
        REFERENCES cuenta_bancaria (id),
    -- Cuentas distintas (decision 1) y monto positivo (decision 2).
    CONSTRAINT ck_transferencia_cuentas_distintas CHECK (cuenta_origen_id <> cuenta_destino_id),
    CONSTRAINT ck_transferencia_monto CHECK (monto > 0)
);

CREATE INDEX ix_transferencia_bancaria_tenant_id ON transferencia_bancaria (tenant_id);

-- Apoyo al listado y filtros por cuenta (origen/destino), acotado al tenant.
CREATE INDEX ix_transferencia_bancaria_tenant_origen
    ON transferencia_bancaria (tenant_id, cuenta_origen_id);
CREATE INDEX ix_transferencia_bancaria_tenant_destino
    ON transferencia_bancaria (tenant_id, cuenta_destino_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V35.
-- ----------------------------------------------------------------------------
ALTER TABLE transferencia_bancaria ENABLE ROW LEVEL SECURITY;
ALTER TABLE transferencia_bancaria FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON transferencia_bancaria
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- PERMISOS (Req 3, 27.11)
--   Permiso nuevo transferencia_bancaria:{crear,leer,listar}, asignado al rol
--   predefinido `contabilidad` (a0000000-0000-0000-0000-00000000000b). Patron
--   idempotente identico a V35 (INSERT ON CONFLICT + enlace selectivo).
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('transferencia_bancaria', 'crear'),
    ('transferencia_bancaria', 'leer'),
    ('transferencia_bancaria', 'listar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000b', p.id
FROM permiso p
WHERE p.recurso = 'transferencia_bancaria' AND p.operacion IN ('crear', 'leer', 'listar')
ON CONFLICT DO NOTHING;
