-- ============================================================================
-- V72__contabilidad_cierre_periodo.sql
--
-- Bloque enterprise "Cierre de periodo contable / Candado contable".
--
-- Agrega el control de cierre mensual al modulo `contabilidad`, protegiendo la
-- integridad fiscal: una vez CERRADO un periodo (anio, mes) —por ejemplo tras
-- enviar la Contabilidad Electronica del Anexo 24 al SAT— ninguna Poliza_Contable
-- nueva ni reverso puede afectar ese periodo. La reapertura queda disponible pero
-- AUDITADA y con motivo obligatorio (revision/correccion autorizada).
--
-- Introduce:
--   1) Tabla `periodo_contable` (tenant-scoped, RLS): un registro por (tenant,
--      anio, mes) con su estado (abierto/cerrado) y los metadatos de cierre y
--      reapertura. AUSENCIA de fila = periodo ABIERTO por defecto (no se exige
--      crear filas por adelantado): solo se materializa al cerrar o reabrir.
--
--   2) Permisos nuevos (patron idempotente ON CONFLICT DO NOTHING) enlazados al
--      rol predefinido `contabilidad` (a0000000-0000-0000-0000-00000000000b, V5):
--        * periodo_contable:cerrar   (cerrar un periodo mensual)
--        * periodo_contable:reabrir  (reapertura auditada con motivo)
--        * periodo_contable:leer     (consulta/listado del estado de periodos)
--        * periodo_contable:listar   (listado por anio; reservado)
--
-- DECISIONES DE DISENO
--   - Periodo MENSUAL (anio + mes). El cierre de ejercicio anual (poliza de
--     cierre, saldado de resultados) es un bloque posterior, fuera de este.
--   - Estado por defecto ABIERTO sin fila: minimiza escrituras y evita sembrar
--     12 filas por tenant/anio. La aplicacion trata la ausencia como abierto.
--   - El candado (rechazo de polizas/reversos en periodo cerrado) se valida en la
--     capa de aplicacion (ServicioContabilidad), UNICO punto de entrada de
--     polizas, por lo que cubre captura manual y eventos automaticos por igual.
--   - Numero de migracion V72: siguiente libre tras V71.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- periodo_contable
--   Estado de cierre de un periodo mensual de la Empresa. tenant-scoped (Req 23).
--   Unico por (tenant_id, anio, mes). estado acotado por CHECK. Los metadatos de
--   cierre/reapertura documentan quien, cuando y (en la reapertura) por que.
-- ----------------------------------------------------------------------------
CREATE TABLE periodo_contable (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    anio                SMALLINT     NOT NULL,
    mes                 SMALLINT     NOT NULL,
    estado              VARCHAR(8)   NOT NULL,
    fecha_cierre        TIMESTAMPTZ,
    cerrado_por         VARCHAR(255),
    fecha_reapertura    TIMESTAMPTZ,
    reabierto_por       VARCHAR(255),
    motivo_reapertura   VARCHAR(500),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    CONSTRAINT pk_periodo_contable PRIMARY KEY (id),
    CONSTRAINT fk_periodo_contable_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Anio en rango razonable (evita datos absurdos).
    CONSTRAINT ck_periodo_contable_anio CHECK (anio BETWEEN 2000 AND 2100),
    -- Mes 1..12.
    CONSTRAINT ck_periodo_contable_mes CHECK (mes BETWEEN 1 AND 12),
    -- Estado acotado (abierto, cerrado).
    CONSTRAINT ck_periodo_contable_estado CHECK (estado IN ('abierto', 'cerrado'))
);

CREATE INDEX ix_periodo_contable_tenant_id ON periodo_contable (tenant_id);

-- Un unico registro de periodo por (tenant, anio, mes).
CREATE UNIQUE INDEX uq_periodo_contable_tenant_anio_mes
    ON periodo_contable (tenant_id, anio, mes);

-- Apoyo al listado por anio, acotado al tenant.
CREATE INDEX ix_periodo_contable_tenant_anio ON periodo_contable (tenant_id, anio);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V33/V31/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE periodo_contable ENABLE ROW LEVEL SECURITY;
ALTER TABLE periodo_contable FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON periodo_contable
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- PERMISOS COMPLEMENTARIOS (Req 3, 27.11)
--   Se siembran (ON CONFLICT DO NOTHING por la clave natural recurso, operacion)
--   y se enlazan al MISMO rol predefinido `contabilidad`
--   (a0000000-0000-0000-0000-00000000000b), con el estilo de V71/V33.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('periodo_contable', 'cerrar'),
    ('periodo_contable', 'reabrir'),
    ('periodo_contable', 'leer'),
    ('periodo_contable', 'listar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000b', p.id
FROM permiso p
WHERE p.recurso = 'periodo_contable'
  AND p.operacion IN ('cerrar', 'reabrir', 'leer', 'listar')
ON CONFLICT DO NOTHING;
