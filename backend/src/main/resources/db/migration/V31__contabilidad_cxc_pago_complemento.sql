-- ============================================================================
-- V31__contabilidad_cxc_pago_complemento.sql
--
-- Submodulo `contabilidad-finanzas` / Cuentas_Por_Cobrar (Tareas 29.1, 29.2;
-- Req 36, 37, 12, 23, 49). CIERRA la integracion con CxC que el bloque 28
-- (facturacion-cfdi, V30) dejo apuntada: al timbrar una Factura se registra su
-- Cuenta_Por_Cobrar, los Pagos de Cliente se aplican disminuyendo saldos, y las
-- parcialidades/diferidos generan un Complemento_Pago (CFDI tipo pago).
--
-- Crea TRES tablas tenant-scoped, replicando EXACTAMENTE el patron reutilizable de
-- V11..V30 para toda tabla tenant-scoped:
--   * tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando V14/V17/V30 (ENABLE + FORCE + politica USING/WITH CHECK).
--
-- Requisitos cubiertos:
--   - Req 36.1 (CxC al timbrar por el saldo = total de la Factura): tabla
--     cuenta_por_cobrar (una por Factura via UNIQUE (tenant_id, factura_id)), con
--     total/saldo NUMERIC(18,2) NOT NULL y estado inicial 'pendiente'.
--   - Req 36.2 (Pago_Cliente aplicado a una o varias Facturas, disminuyendo el
--     saldo): tablas pago_cliente y aplicacion_pago (link pago->CxC/factura con
--     monto_aplicado). La disminucion del saldo la ejecuta el dominio (CuentaPorCobrar).
--   - Req 36.3 (aplicacion acotada por el saldo; rechazo por exceso conservando
--     saldos, informando excedente): regla PURA del dominio (Property 13), no BD.
--   - Req 36.4 (parcialidad/diferido -> Complemento_Pago timbrado via PAC, Req 35):
--     pago_cliente.es_parcialidad + complemento_folio_fiscal/sello/fecha_timbrado.
--   - Req 36.5 (aging por rango de dias de vencimiento por Cliente): CxC guarda
--     fecha_emision y fecha_vencimiento; el calculo por rangos lo hace la aplicacion
--     con el Clock inyectado.
--   - Req 36.6 (listado paginado 20/100 filtrable por Cliente y estado): indices de
--     apoyo (tenant_id, cliente_id) y (tenant_id, estado) en cuenta_por_cobrar y
--     (tenant_id, cliente_id) en pago_cliente.
--   - Req 37.1 (Nota de Credito disminuye la CxC): la disminucion la aplica el
--     dominio CxC via el puerto CuentaPorCobrarPort (cableado en ServicioNotasCredito).
--   - Req 23 (multi-tenant): tenant_id + RLS por tabla (las tres).
--   - Req 49 (concurrencia optimista): columna version en las tres tablas.
--   - Req 36.7 (auditoria al registrar/aplicar Pago_Cliente): la ejecuta la
--     aplicacion via AuditoriaPort (no BD).
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. UNA CxC POR FACTURA (Req 36.1): indice UNICO (tenant_id, factura_id) sobre
--      cuenta_por_cobrar. El registro al timbrar es IDEMPOTENTE en la aplicacion
--      (si ya existe una CxC para la Factura, no se crea otra), y el UNIQUE es la
--      segunda capa de defensa ante concurrencia.
--   2. SALDO NUNCA NEGATIVO (Req 36.2, 36.3; Property 13): saldo NUMERIC(18,2) con
--      CHECK (saldo >= 0) y CHECK (total >= 0). La regla de aplicacion de pago
--      acotada por el saldo (rechazo por exceso SIN mutar) es una funcion PURA del
--      dominio (CuentaPorCobrar.aplicarPago); la BD solo garantiza la invariante de
--      no negatividad. El estado se DERIVA del saldo: saldo==total -> 'pendiente';
--      0<saldo<total -> 'parcial'; saldo==0 -> 'pagada'.
--   3. ESTADO DE LA CxC (maquina de estados, dominio): estado con CHECK IN
--      ('pendiente','parcial','pagada','cancelada'); las transiciones validas viven
--      en el dominio (EstadoCuentaPorCobrar). 'pagada' y 'cancelada' son finales.
--   4. APLICACION DE PAGO (aplicacion_pago): un Pago_Cliente puede aplicarse a
--      varias Facturas; cada renglon (aplicacion_pago) enlaza pago_cliente_id ->
--      cuenta_por_cobrar_id -> factura_id con monto_aplicado > 0. La FK a
--      pago_cliente es ON DELETE CASCADE (los renglones no sobreviven a su pago);
--      la FK a cuenta_por_cobrar NO cascada (la CxC es historico contable).
--   5. COMPLEMENTO_PAGO PARA PARCIALIDADES (Req 36.4): es_parcialidad BOOLEAN; al
--      registrarse un pago parcial/diferido, la aplicacion genera y timbra el CFDI
--      tipo pago via el PAC (reutilizando SolicitudTimbrado/ResultadoTimbrado del
--      bloque 35) y guarda complemento_folio_fiscal/sello/fecha_timbrado. Si el PAC
--      rechaza, la aplicacion revierte la transaccion (422). complemento_sello TEXT
--      (cadena Base64 larga), coherente con sello_sat de V30.
--   6. AGING (Req 36.5): fecha_vencimiento DATE NULL; cuando es NULL, la aplicacion
--      usa fecha_emision como referencia. Los rangos (0-30, 31-60, 61-90, >90) se
--      calculan en la aplicacion con el Clock inyectado (deterministico en pruebas).
--   7. PERMISOS (Req 3, 27.11): V5 ya sembro pago_cliente:{crear,leer,listar} y
--      complemento_pago:{crear,leer}, asignados al rol `contabilidad`
--      (a0000000-0000-0000-0000-00000000000b); NO se re-siembran. V5 NO incluyo el
--      recurso cuenta_por_cobrar; esta migracion siembra
--      cuenta_por_cobrar:{leer,listar} y los enlaza al MISMO rol `contabilidad`, con
--      el estilo de V30/V9 (INSERT ... ON CONFLICT DO NOTHING + enlace rol_permiso
--      por subconsulta). El listado y el aging de CxC usan estos permisos; los
--      endpoints de Pago_Cliente usan los de V5.
--   8. NUMERO DE MIGRACION V31: pre-asignado a este bloque. V32 lo usa otro modulo
--      en paralelo; aqui NO se usa V32.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- cuenta_por_cobrar
--   Saldo pendiente de cobro de una Factura timbrada (Req 36.1). tenant-scoped
--   (Req 23). Una CxC por Factura (UNIQUE). Estado inicial 'pendiente'; finales
--   'pagada' y 'cancelada'.
-- ----------------------------------------------------------------------------
CREATE TABLE cuenta_por_cobrar (
    id                 UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          UUID           NOT NULL,
    factura_id         UUID           NOT NULL,
    cliente_id         UUID           NOT NULL,
    total              NUMERIC(18, 2) NOT NULL,
    saldo              NUMERIC(18, 2) NOT NULL,
    estado             VARCHAR(12)    NOT NULL DEFAULT 'pendiente',
    fecha_emision      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    fecha_vencimiento  DATE,
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    CONSTRAINT pk_cuenta_por_cobrar PRIMARY KEY (id),
    CONSTRAINT fk_cxc_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_cxc_factura FOREIGN KEY (factura_id) REFERENCES factura (id),
    CONSTRAINT fk_cxc_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    -- Estados permitidos (maquina de estados en el dominio). Etiquetas ASCII minusculas.
    CONSTRAINT ck_cxc_estado CHECK (
        estado IN ('pendiente', 'parcial', 'pagada', 'cancelada')),
    -- Total y saldo no negativos (Req 36.2, 36.3; Property 13).
    CONSTRAINT ck_cxc_total_no_negativo CHECK (total >= 0),
    CONSTRAINT ck_cxc_saldo_no_negativo CHECK (saldo >= 0)
);

CREATE INDEX ix_cuenta_por_cobrar_tenant_id ON cuenta_por_cobrar (tenant_id);

-- Una Cuenta_Por_Cobrar por Factura dentro del tenant (Req 36.1, decision 1).
CREATE UNIQUE INDEX uq_cuenta_por_cobrar_tenant_factura
    ON cuenta_por_cobrar (tenant_id, factura_id);

-- Apoyo a los filtros del listado y al aging (Req 36.5, 36.6): por Cliente y por
-- estado, siempre acotados al tenant.
CREATE INDEX ix_cuenta_por_cobrar_tenant_cliente ON cuenta_por_cobrar (tenant_id, cliente_id);
CREATE INDEX ix_cuenta_por_cobrar_tenant_estado ON cuenta_por_cobrar (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- pago_cliente
--   Pago recibido de un Cliente, aplicable a una o varias Facturas (Req 36.2).
--   Si es parcialidad/diferido, porta los datos del Complemento_Pago (Req 36.4).
--   tenant-scoped (Req 23). monto > 0.
-- ----------------------------------------------------------------------------
CREATE TABLE pago_cliente (
    id                          UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                   UUID           NOT NULL,
    cliente_id                  UUID           NOT NULL,
    monto                       NUMERIC(18, 2) NOT NULL,
    fecha_pago                  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    forma_pago                  VARCHAR(20),
    es_parcialidad              BOOLEAN        NOT NULL DEFAULT FALSE,
    complemento_folio_fiscal    UUID,
    complemento_sello           TEXT,
    complemento_fecha_timbrado  TIMESTAMPTZ,
    version                     BIGINT         NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by                  VARCHAR(255),
    updated_by                  VARCHAR(255),
    CONSTRAINT pk_pago_cliente PRIMARY KEY (id),
    CONSTRAINT fk_pago_cliente_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_pago_cliente_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    -- Monto estrictamente positivo (Req 36.2).
    CONSTRAINT ck_pago_cliente_monto_positivo CHECK (monto > 0)
);

CREATE INDEX ix_pago_cliente_tenant_id ON pago_cliente (tenant_id);

-- Apoyo al filtro del listado por Cliente (Req 36.6), acotado al tenant.
CREATE INDEX ix_pago_cliente_tenant_cliente ON pago_cliente (tenant_id, cliente_id);

-- ----------------------------------------------------------------------------
-- aplicacion_pago
--   Enlaza un Pago_Cliente con la CxC (y su Factura) a la que se aplico, con el
--   monto aplicado (Req 36.2). Un pago puede tener varias aplicaciones (una por
--   Factura). tenant-scoped (Req 23). monto_aplicado > 0.
-- ----------------------------------------------------------------------------
CREATE TABLE aplicacion_pago (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL,
    pago_cliente_id       UUID           NOT NULL,
    cuenta_por_cobrar_id  UUID           NOT NULL,
    factura_id            UUID           NOT NULL,
    monto_aplicado        NUMERIC(18, 2) NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    CONSTRAINT pk_aplicacion_pago PRIMARY KEY (id),
    CONSTRAINT fk_aplicacion_pago_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_aplicacion_pago_pago FOREIGN KEY (pago_cliente_id)
        REFERENCES pago_cliente (id) ON DELETE CASCADE,
    CONSTRAINT fk_aplicacion_pago_cxc FOREIGN KEY (cuenta_por_cobrar_id)
        REFERENCES cuenta_por_cobrar (id),
    CONSTRAINT fk_aplicacion_pago_factura FOREIGN KEY (factura_id) REFERENCES factura (id),
    -- Monto aplicado estrictamente positivo (Req 36.2).
    CONSTRAINT ck_aplicacion_pago_monto_positivo CHECK (monto_aplicado > 0)
);

CREATE INDEX ix_aplicacion_pago_tenant_id ON aplicacion_pago (tenant_id);

-- Apoyo al desglose por Pago_Cliente y por CxC, acotado al tenant (Req 36.2).
CREATE INDEX ix_aplicacion_pago_tenant_pago ON aplicacion_pago (tenant_id, pago_cliente_id);
CREATE INDEX ix_aplicacion_pago_tenant_cxc ON aplicacion_pago (tenant_id, cuenta_por_cobrar_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V30/V14/V17/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE cuenta_por_cobrar ENABLE ROW LEVEL SECURITY;
ALTER TABLE cuenta_por_cobrar FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON cuenta_por_cobrar
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE pago_cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE pago_cliente FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON pago_cliente
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE aplicacion_pago ENABLE ROW LEVEL SECURITY;
ALTER TABLE aplicacion_pago FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON aplicacion_pago
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- PERMISOS DE CUENTA_POR_COBRAR (Req 3, 27.11) -- DECISION 7
--   V5 sembro pago_cliente:{crear,leer,listar} y complemento_pago:{crear,leer}
--   (rol `contabilidad`), pero NO el recurso cuenta_por_cobrar. El listado (GET) y
--   el aging (GET) de CxC exigen cuenta_por_cobrar:{listar,leer}. Se siembran aqui
--   y se enlazan al MISMO rol predefinido `contabilidad` (UUID fijo de V5), con el
--   estilo de V30/V9 (ON CONFLICT DO NOTHING por la clave natural (recurso, operacion)).
--   Los permisos de pago_cliente NO se re-siembran.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('cuenta_por_cobrar', 'leer'),
    ('cuenta_por_cobrar', 'listar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000b', p.id
FROM permiso p
WHERE p.recurso = 'cuenta_por_cobrar' AND p.operacion IN ('leer', 'listar')
ON CONFLICT DO NOTHING;
