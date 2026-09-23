-- ============================================================================
-- V33__contabilidad_cuentas_polizas_cxp.sql
--
-- Submodulo `contabilidad-finanzas` / Catalogo de cuentas, Polizas contables y
-- Cuentas por Pagar (Tareas 30.1, 30.2; Req 38, 42, 12, 23, 49).
--
-- Crea CINCO tablas tenant-scoped, replicando EXACTAMENTE el patron reutilizable
-- de V11..V31 para toda tabla tenant-scoped:
--   * tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando V14/V17/V31 (ENABLE + FORCE + politica USING/WITH CHECK).
--
-- Requisitos cubiertos:
--   - Req 38.1 (catalogo de Cuenta_Contable por tenant): tabla cuenta_contable con
--     codigo UNICO por tenant, nombre, tipo, naturaleza y bandera activa.
--   - Req 38.2 (generar Poliza_Contable ante eventos relevantes): tabla
--     poliza_contable con origen/origen_id que documentan el evento contable
--     (factura_timbrada, pago_cliente, nota_credito, factura_proveedor,
--     movimiento_inventario, reverso). La generacion la ejecuta la aplicacion
--     (PolizaContablePort) al invocarse desde los productores de eventos.
--   - Req 38.3, 38.4 (poliza balanceada; rechazo por desbalance informando la
--     diferencia): total_cargos = total_abonos garantizado por CHECK
--     ck_poliza_balanceada; la validacion PURA (con el mensaje de diferencia) vive
--     en el dominio (PolizaContable, Property 16). La BD es la segunda capa.
--   - Req 38.5 (polizas inmutables; solo reverso/correccion): las columnas
--     financieras son inmutables en el dominio (sin setters); una correccion se
--     modela como una poliza de reverso que referencia poliza_revertida_id.
--   - Req 38.6 (listado paginado 20/100 filtrable por rango de fechas y por
--     Cuenta_Contable): indice (tenant_id, fecha) en poliza_contable y
--     (tenant_id, cuenta_contable_id) en movimiento_poliza (para el filtro por
--     cuenta via join).
--   - Req 38.7 (auditoria al crear Cuenta_Contable o Poliza_Contable): la ejecuta
--     la aplicacion via AuditoriaPort (no BD).
--   - Req 42.1 (CxP al conciliar Factura_Proveedor por su saldo): tabla
--     cuenta_por_pagar (una por Factura_Proveedor via UNIQUE (tenant_id,
--     factura_proveedor_id)); la crea la aplicacion al conciliar (cableado en
--     ServicioFacturasProveedor -> CuentaPorPagarPort). IDEMPOTENTE.
--   - Req 42.2 (Programacion_Pago: fecha + monto por CxP): tabla programacion_pago.
--   - Req 42.3 (aplicar pago disminuye saldo y al llegar a 0 marca la
--     Factura_Proveedor 'pagada'): la disminucion PURA la aplica el dominio
--     (CuentaPorPagar.aplicarPago); el marcado de la Factura_Proveedor lo delega la
--     aplicacion al modulo compras via FacturaProveedorPagablePort (Req 33).
--   - Req 42.4 (rechazo por exceso conservando la CxP; informa el excedente): regla
--     PURA del dominio (Property 15), no BD; saldo NUMERIC(18,2) con CHECK >= 0.
--   - Req 42.5 (aging por Proveedor): CxP guarda proveedor_id y fecha_registro/
--     fecha_vencimiento; el calculo por rangos lo hace la aplicacion con el Clock.
--   - Req 42.6 (listado paginado 20/100 filtrable por Proveedor y estado): indices
--     (tenant_id, proveedor_id) y (tenant_id, estado) en cuenta_por_pagar.
--   - Req 42.7 (auditoria al crear/modificar/aplicar CxP o Programacion_Pago): la
--     ejecuta la aplicacion via AuditoriaPort (no BD).
--   - Req 23 (multi-tenant): tenant_id + RLS por tabla (las cinco).
--   - Req 49 (concurrencia optimista): columna version en las cinco tablas.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. POLIZA BALANCEADA POR CHECK + VALIDACION DE DOMINIO (Req 38.3, 38.4;
--      Property 16): poliza_contable persiste total_cargos y total_abonos, ambos
--      NUMERIC(18,2) NOT NULL >= 0, con CHECK ck_poliza_balanceada
--      (total_cargos = total_abonos). La regla que RECHAZA una poliza no balanceada
--      informando la diferencia es una funcion PURA del dominio
--      (PolizaContable.crear); la BD solo garantiza la invariante como segunda capa.
--   2. RENGLON CARGO XOR ABONO (movimiento_poliza): cada renglon es un cargo o un
--      abono, nunca ambos ni ninguno: CHECK ck_mov_cargo_xor_abono
--      ((cargo>0 AND abono=0) OR (abono>0 AND cargo=0)). cargo y abono NUMERIC(18,2)
--      NOT NULL DEFAULT 0, ambos >= 0.
--   3. INMUTABILIDAD VIA REVERSO (Req 38.5): las polizas no se actualizan ni se
--      borran; una correccion se registra como una nueva poliza de reverso (origen
--      'reverso') que referencia poliza_revertida_id (FK auto-referencial). El
--      dominio no expone setters de datos financieros. La FK de movimiento_poliza a
--      poliza_contable es ON DELETE CASCADE solo por consistencia estructural; la
--      aplicacion nunca borra polizas.
--   4. CxP SALDO >= 0 Y APLICACION ACOTADA (Req 42.3, 42.4; Property 15):
--      cuenta_por_pagar.saldo NUMERIC(18,2) con CHECK (saldo >= 0) y
--      total >= 0. La regla de aplicacion de pago acotada por el saldo (rechazo por
--      exceso SIN mutar, informando el excedente) es una funcion PURA del dominio
--      (CuentaPorPagar.aplicarPago). El estado se DERIVA del saldo: saldo==total ->
--      'pendiente'; 0<saldo<total -> 'parcial'; saldo==0 -> 'pagada'.
--   5. ESTADO DE LA CxP (maquina de estados, dominio): estado con CHECK IN
--      ('pendiente','parcial','pagada','cancelada'); las transiciones validas viven
--      en el dominio (EstadoCuentaPorPagar). 'pagada' y 'cancelada' son finales.
--   6. UNA CxP POR FACTURA_PROVEEDOR (Req 42.1): indice UNICO (tenant_id,
--      factura_proveedor_id). El registro al conciliar es IDEMPOTENTE en la
--      aplicacion; el UNIQUE es la segunda capa de defensa ante concurrencia.
--   7. PROGRAMACION_PAGO (Req 42.2): fecha_programada DATE + monto NUMERIC(18,2) > 0
--      por cuenta_por_pagar_id; bandera aplicada BOOLEAN. La FK a cuenta_por_pagar
--      NO cascada (la CxP es historico contable).
--   8. PERMISOS (Req 3, 27.11): V5 ya sembro cuenta_contable:{crear,leer,listar},
--      poliza_contable:{crear,leer,listar}, cuenta_por_pagar:{leer,listar} y
--      programacion_pago:{crear,leer}, todos asignados al rol `contabilidad`
--      (a0000000-0000-0000-0000-00000000000b) por el bloque de asignacion por
--      recurso de V5. Esta migracion COMPLETA lo que falta para el bloque 30:
--        * programacion_pago:listar  (el listado GET del Req 42.6 lo exige y V5 no
--          lo sembro),
--        * cuenta_por_pagar:aplicar_pago  (endpoint de aplicacion de pago del
--          Req 42.3; operacion distinta de leer/listar).
--      Se siembran con ON CONFLICT DO NOTHING (clave natural recurso, operacion) y
--      se ENLAZAN explicitamente al rol `contabilidad`, porque la asignacion por
--      recurso de V5 ya se ejecuto y no recogeria operaciones nuevas.
--   9. NUMERO DE MIGRACION V33: pre-asignado a este bloque. V34 lo usa otro modulo
--      en paralelo; aqui NO se usa V34.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- cuenta_contable
--   Cuenta del catalogo contable de la Empresa (Req 38.1). tenant-scoped (Req 23).
--   codigo UNICO por tenant. tipo y naturaleza acotados por CHECK.
-- ----------------------------------------------------------------------------
CREATE TABLE cuenta_contable (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    codigo      VARCHAR(30)  NOT NULL,
    nombre      VARCHAR(200) NOT NULL,
    tipo        VARCHAR(12)  NOT NULL,
    naturaleza  VARCHAR(8)   NOT NULL,
    activa      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_cuenta_contable PRIMARY KEY (id),
    CONSTRAINT fk_cuenta_contable_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Nombre no vacio (Req 38.1).
    CONSTRAINT ck_cuenta_contable_nombre CHECK (length(btrim(nombre)) >= 1),
    -- Tipo contable acotado (activo, pasivo, capital, ingreso, gasto).
    CONSTRAINT ck_cuenta_contable_tipo CHECK (
        tipo IN ('activo', 'pasivo', 'capital', 'ingreso', 'gasto')),
    -- Naturaleza del saldo (deudora o acreedora).
    CONSTRAINT ck_cuenta_contable_naturaleza CHECK (
        naturaleza IN ('deudora', 'acreedora'))
);

CREATE INDEX ix_cuenta_contable_tenant_id ON cuenta_contable (tenant_id);

-- codigo unico por tenant (Req 38.1).
CREATE UNIQUE INDEX uq_cuenta_contable_tenant_codigo
    ON cuenta_contable (tenant_id, codigo);

-- Apoyo al listado/filtro por tipo, acotado al tenant.
CREATE INDEX ix_cuenta_contable_tenant_tipo ON cuenta_contable (tenant_id, tipo);

-- ----------------------------------------------------------------------------
-- poliza_contable
--   Asiento contable balanceado (conjunto de cargos y abonos) que registra el
--   efecto contable de una operacion (Req 38.2, 38.3). tenant-scoped (Req 23).
--   total_cargos = total_abonos (CHECK). origen/origen_id documentan el evento;
--   poliza_revertida_id referencia la poliza revertida en un reverso (Req 38.5).
-- ----------------------------------------------------------------------------
CREATE TABLE poliza_contable (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID           NOT NULL,
    fecha                DATE           NOT NULL,
    tipo                 VARCHAR(12)    NOT NULL,
    concepto             VARCHAR(300)   NOT NULL,
    origen               VARCHAR(40),
    origen_id            UUID,
    poliza_revertida_id  UUID,
    total_cargos         NUMERIC(18, 2) NOT NULL,
    total_abonos         NUMERIC(18, 2) NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),
    CONSTRAINT pk_poliza_contable PRIMARY KEY (id),
    CONSTRAINT fk_poliza_contable_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Reverso: referencia auto-referencial a la poliza revertida (Req 38.5).
    CONSTRAINT fk_poliza_contable_revertida FOREIGN KEY (poliza_revertida_id)
        REFERENCES poliza_contable (id),
    -- Tipo de poliza acotado (ingreso, egreso, diario).
    CONSTRAINT ck_poliza_contable_tipo CHECK (tipo IN ('ingreso', 'egreso', 'diario')),
    -- Concepto no vacio.
    CONSTRAINT ck_poliza_contable_concepto CHECK (length(btrim(concepto)) >= 1),
    -- Totales no negativos.
    CONSTRAINT ck_poliza_cargos_no_negativo CHECK (total_cargos >= 0),
    CONSTRAINT ck_poliza_abonos_no_negativo CHECK (total_abonos >= 0),
    -- Poliza balanceada: suma de cargos = suma de abonos (Req 38.3, Property 16).
    CONSTRAINT ck_poliza_balanceada CHECK (total_cargos = total_abonos)
);

CREATE INDEX ix_poliza_contable_tenant_id ON poliza_contable (tenant_id);

-- Apoyo al filtro por rango de fechas del listado (Req 38.6), acotado al tenant.
CREATE INDEX ix_poliza_contable_tenant_fecha ON poliza_contable (tenant_id, fecha);

-- ----------------------------------------------------------------------------
-- movimiento_poliza
--   Renglon de una Poliza_Contable: un cargo O un abono a una Cuenta_Contable
--   (Req 38.2, 38.3). tenant-scoped (Req 23). cargo XOR abono (CHECK).
-- ----------------------------------------------------------------------------
CREATE TABLE movimiento_poliza (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID           NOT NULL,
    poliza_contable_id   UUID           NOT NULL,
    cuenta_contable_id   UUID           NOT NULL,
    cargo                NUMERIC(18, 2) NOT NULL DEFAULT 0,
    abono                NUMERIC(18, 2) NOT NULL DEFAULT 0,
    version              BIGINT         NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),
    CONSTRAINT pk_movimiento_poliza PRIMARY KEY (id),
    CONSTRAINT fk_movimiento_poliza_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_movimiento_poliza_poliza FOREIGN KEY (poliza_contable_id)
        REFERENCES poliza_contable (id) ON DELETE CASCADE,
    CONSTRAINT fk_movimiento_poliza_cuenta FOREIGN KEY (cuenta_contable_id)
        REFERENCES cuenta_contable (id),
    -- Cargo y abono no negativos.
    CONSTRAINT ck_movimiento_cargo_no_negativo CHECK (cargo >= 0),
    CONSTRAINT ck_movimiento_abono_no_negativo CHECK (abono >= 0),
    -- Cada renglon es un cargo O un abono, nunca ambos ni ninguno (decision 2).
    CONSTRAINT ck_mov_cargo_xor_abono CHECK (
        (cargo > 0 AND abono = 0) OR (abono > 0 AND cargo = 0))
);

CREATE INDEX ix_movimiento_poliza_tenant_id ON movimiento_poliza (tenant_id);

-- Apoyo al desglose por poliza y al filtro por Cuenta_Contable (Req 38.6),
-- ambos acotados al tenant.
CREATE INDEX ix_movimiento_poliza_tenant_poliza ON movimiento_poliza (tenant_id, poliza_contable_id);
CREATE INDEX ix_movimiento_poliza_tenant_cuenta ON movimiento_poliza (tenant_id, cuenta_contable_id);

-- ----------------------------------------------------------------------------
-- cuenta_por_pagar
--   Saldo pendiente de pago a un Proveedor derivado de una Factura_Proveedor
--   conciliada (Req 42.1). tenant-scoped (Req 23). Una CxP por Factura_Proveedor
--   (UNIQUE). Estado inicial 'pendiente'; finales 'pagada' y 'cancelada'.
-- ----------------------------------------------------------------------------
CREATE TABLE cuenta_por_pagar (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL,
    factura_proveedor_id  UUID           NOT NULL,
    proveedor_id          UUID           NOT NULL,
    total                 NUMERIC(18, 2) NOT NULL,
    saldo                 NUMERIC(18, 2) NOT NULL,
    estado                VARCHAR(12)    NOT NULL DEFAULT 'pendiente',
    fecha_registro        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    fecha_vencimiento     DATE,
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    CONSTRAINT pk_cuenta_por_pagar PRIMARY KEY (id),
    CONSTRAINT fk_cxp_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_cxp_factura_proveedor FOREIGN KEY (factura_proveedor_id)
        REFERENCES factura_proveedor (id),
    CONSTRAINT fk_cxp_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedor (id),
    -- Estados permitidos (maquina de estados en el dominio). Etiquetas ASCII minusculas.
    CONSTRAINT ck_cxp_estado CHECK (
        estado IN ('pendiente', 'parcial', 'pagada', 'cancelada')),
    -- Total y saldo no negativos (Req 42.3, 42.4; Property 15).
    CONSTRAINT ck_cxp_total_no_negativo CHECK (total >= 0),
    CONSTRAINT ck_cxp_saldo_no_negativo CHECK (saldo >= 0)
);

CREATE INDEX ix_cuenta_por_pagar_tenant_id ON cuenta_por_pagar (tenant_id);

-- Una Cuenta_Por_Pagar por Factura_Proveedor dentro del tenant (Req 42.1, decision 6).
CREATE UNIQUE INDEX uq_cuenta_por_pagar_tenant_factura
    ON cuenta_por_pagar (tenant_id, factura_proveedor_id);

-- Apoyo a los filtros del listado y al aging por Proveedor (Req 42.5, 42.6) y por
-- estado, siempre acotados al tenant.
CREATE INDEX ix_cuenta_por_pagar_tenant_proveedor ON cuenta_por_pagar (tenant_id, proveedor_id);
CREATE INDEX ix_cuenta_por_pagar_tenant_estado ON cuenta_por_pagar (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- programacion_pago
--   Calendario de pago (fecha programada + monto) por Cuenta_Por_Pagar (Req 42.2).
--   tenant-scoped (Req 23). monto > 0. aplicada indica si ya se ejecuto el pago.
-- ----------------------------------------------------------------------------
CREATE TABLE programacion_pago (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID           NOT NULL,
    cuenta_por_pagar_id  UUID           NOT NULL,
    fecha_programada     DATE           NOT NULL,
    monto                NUMERIC(18, 2) NOT NULL,
    aplicada             BOOLEAN        NOT NULL DEFAULT FALSE,
    version              BIGINT         NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),
    CONSTRAINT pk_programacion_pago PRIMARY KEY (id),
    CONSTRAINT fk_programacion_pago_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_programacion_pago_cxp FOREIGN KEY (cuenta_por_pagar_id)
        REFERENCES cuenta_por_pagar (id),
    -- Monto estrictamente positivo (Req 42.2).
    CONSTRAINT ck_programacion_pago_monto_positivo CHECK (monto > 0)
);

CREATE INDEX ix_programacion_pago_tenant_id ON programacion_pago (tenant_id);

-- Apoyo al desglose y al listado por Cuenta_Por_Pagar (Req 42.6), acotado al tenant.
CREATE INDEX ix_programacion_pago_tenant_cxp ON programacion_pago (tenant_id, cuenta_por_pagar_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V31/V14/V17/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE cuenta_contable ENABLE ROW LEVEL SECURITY;
ALTER TABLE cuenta_contable FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON cuenta_contable
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE poliza_contable ENABLE ROW LEVEL SECURITY;
ALTER TABLE poliza_contable FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON poliza_contable
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE movimiento_poliza ENABLE ROW LEVEL SECURITY;
ALTER TABLE movimiento_poliza FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON movimiento_poliza
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE cuenta_por_pagar ENABLE ROW LEVEL SECURITY;
ALTER TABLE cuenta_por_pagar FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON cuenta_por_pagar
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE programacion_pago ENABLE ROW LEVEL SECURITY;
ALTER TABLE programacion_pago FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON programacion_pago
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- PERMISOS COMPLEMENTARIOS (Req 3, 27.11) -- DECISION 8
--   V5 sembro cuenta_contable:{crear,leer,listar}, poliza_contable:{crear,leer,
--   listar}, cuenta_por_pagar:{leer,listar} y programacion_pago:{crear,leer},
--   todos asignados al rol `contabilidad` (a0000000-0000-0000-0000-00000000000b).
--   Este bloque necesita ADEMAS:
--     * programacion_pago:listar  (listado GET del Req 42.6),
--     * cuenta_por_pagar:aplicar_pago  (endpoint de aplicacion de pago del Req 42.3).
--   Se siembran (ON CONFLICT DO NOTHING por la clave natural recurso, operacion) y
--   se enlazan al MISMO rol predefinido `contabilidad`, con el estilo de V31/V30.
--   El resto de permisos de contabilidad NO se re-siembran.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('programacion_pago', 'listar'),
    ('cuenta_por_pagar',  'aplicar_pago')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000b', p.id
FROM permiso p
WHERE (p.recurso = 'programacion_pago' AND p.operacion = 'listar')
   OR (p.recurso = 'cuenta_por_pagar'  AND p.operacion = 'aplicar_pago')
ON CONFLICT DO NOTHING;
