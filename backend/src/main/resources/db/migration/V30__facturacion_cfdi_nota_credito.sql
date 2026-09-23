-- ============================================================================
-- V30__facturacion_cfdi_nota_credito.sql
--
-- Modulo `facturacion-cfdi` (Tareas 28.2, 28.3, 28.4; Req 34, 35, 37, 12, 23, 49).
-- Establece las dos tablas del bloque 28: la raiz `factura` (CFDI 4.0 de ingreso,
-- con datos fiscales del receptor, importes, timbrado y ciclo de vida) y
-- `nota_credito` (CFDI de egreso que referencia una Factura timbrada). Replica
-- EXACTAMENTE el patron reutilizable establecido en V11..V28 para toda tabla
-- tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V14.
--
-- Requisitos cubiertos:
--   - Req 34.1 (emision desde Cotizacion aprobada u Orden_Fabricacion, con datos
--     fiscales del receptor y estado inicial 'borrador'): factura.cotizacion_id y
--     orden_fabricacion_id (FK nullable; CHECK de origen exactamente uno),
--     cliente_id NOT NULL (para el filtro del Req 34.4), receptor_rfc/nombre/cp/
--     regimen_fiscal y uso_cfdi NOT NULL; estado DEFAULT 'borrador'.
--   - Req 34.2 (IVA 16%, retenciones y total half-up): subtotal/iva/retenciones/
--     total NUMERIC(18,2) con CHECK de no negatividad; el calculo half-up lo
--     realiza el dominio (CalculoFiscalCfdi; Property 4).
--   - Req 34.4 (filtros por Cliente/estado): indices de apoyo (tenant_id,
--     cliente_id) y (tenant_id, estado).
--   - Req 35.1 (timbrado -> Folio_Fiscal + estado 'timbrada'): folio_fiscal UUID
--     NULL, sello_sat TEXT NULL, fecha_timbrado TIMESTAMPTZ NULL. UNIQUE parcial
--     (tenant_id, folio_fiscal) WHERE folio_fiscal IS NOT NULL: un Folio_Fiscal es
--     unico por tenant (el borrador sin folio no colisiona).
--   - Req 35.3/35.6 (inmutabilidad tras timbrado; historico): la inmutabilidad de
--     los datos fiscales tras 'timbrada' la impone el dominio (Property 21); la BD
--     conserva folio_fiscal/sello_sat aun tras la cancelacion (historico).
--   - Req 35.7 (maquina de estados): estado con CHECK IN ('borrador','timbrada',
--     'cancelacion_en_proceso','cancelada'); la maquina pura vive en el dominio
--     (EstadoFactura). motivo_cancelacion VARCHAR(4) NULL (clave del SAT, Req 35.4).
--   - Req 37.1/37.2 (nota de credito referencia Factura timbrada; acotada por el
--     saldo): nota_credito.factura_id NOT NULL FK -> factura; monto NUMERIC(18,2)
--     CHECK > 0. El tope monto <= total - Σ(notas previas no canceladas) lo aplica
--     el dominio/aplicacion (NotaCreditoValidaciones; Property 14).
--   - Req 37.3 (historico inmutable del CFDI de egreso): folio_fiscal/sello_sat se
--     conservan; la inmutabilidad tras 'timbrada' la impone el dominio.
--   - Req 23 (multi-tenant): tenant_id + RLS por tabla (ambas).
--   - Req 49 (concurrencia optimista): columna version en ambas tablas.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. IVA 16% Y TOTAL HALF-UP (Req 34.2): iva = round(subtotal*0.16,2),
--      retenciones = round(subtotal*tasaRetencion,2) cuando aplica, y
--      total = round(subtotal + iva - retenciones, 2), todo half-up escala 2. El
--      calculo es una funcion pura del dominio (CalculoFiscalCfdi); la BD solo
--      almacena los importes ya calculados con CHECK de no negatividad.
--   2. ORIGEN EXACTAMENTE UNO (Req 34.1): una Factura se emite desde una Cotizacion
--      aprobada O desde una Orden_Fabricacion. Se modela con dos FK nullable y un
--      CHECK que exige que EXACTAMENTE una este presente
--      (ck_factura_origen_exclusivo), evitando facturas sin origen o con origen
--      ambiguo. La aplicacion tambien lo valida (422).
--   3. FOLIO_FISCAL UNICO PARCIAL (Req 35.1): un Folio_Fiscal (UUID del SAT) es
--      unico dentro del tenant, pero los borradores aun no lo tienen. Se usa un
--      indice UNICO PARCIAL sobre (tenant_id, folio_fiscal) WHERE folio_fiscal IS
--      NOT NULL, de modo que multiples borradores (folio NULL) coexisten sin
--      colisionar y, una vez timbrada, el folio no se repite.
--   4. INMUTABILIDAD (Req 35.3, 35.6, 37.3): se garantiza en el DOMINIO
--      (los mutadores de datos fiscales lanzan 422 si el estado != 'borrador', y la
--      maquina de estados solo admite las transiciones definidas). La BD conserva
--      folio_fiscal/sello_sat/fecha_timbrado como historico aun tras la
--      cancelacion; no se agrega trigger de inmutabilidad para no divergir del
--      patron del resto de migraciones (la fuente de verdad es el dominio;
--      Property 21 lo verifica).
--   5. NOTA DE CREDITO ACOTADA POR EL SALDO (Req 37.2): en este bloque el tope es
--      total - Σ(notas de credito previas no canceladas de la Factura). La
--      Cuenta_Por_Cobrar completa (con pagos) es del bloque 29 (modulo
--      contabilidad-finanzas); alli se refinara el saldo restando tambien los
--      pagos. Aqui NO se crean tablas de CxC (corresponden al bloque 29).
--   6. PERMISOS: V5 ya sembro factura:{crear,leer,listar,cambiar_estado} y
--      nota_credito:{crear,leer}, asignados al rol `contabilidad`
--      (a0000000-0000-0000-0000-00000000000b). Esta migracion agrega
--      nota_credito:{listar,cambiar_estado} (listado y timbrado) al MISMO rol,
--      siguiendo el patron de V9 (INSERT del permiso con ON CONFLICT DO NOTHING y
--      enlace rol_permiso por subconsulta). Los permisos de factura NO se
--      re-siembran.
--   7. sello_sat TEXT: el sello digital del SAT es una cadena Base64 larga; se
--      modela TEXT (sin limite fijo) para no truncarlo. folio_fiscal UUID coincide
--      con el tipo del Folio_Fiscal del SAT.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- factura
--   CFDI 4.0 de ingreso emitido a un Cliente (Req 34, 35). tenant-scoped (Req 23).
--   Estado inicial 'borrador' (Req 34.1); estado final 'cancelada' (Req 35.7).
-- ----------------------------------------------------------------------------
CREATE TABLE factura (
    id                       UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                UUID           NOT NULL,
    cotizacion_id            UUID,
    orden_fabricacion_id     UUID,
    cliente_id               UUID           NOT NULL,
    receptor_rfc             VARCHAR(13)    NOT NULL,
    receptor_nombre          VARCHAR(300)   NOT NULL,
    receptor_cp              VARCHAR(5)     NOT NULL,
    receptor_regimen_fiscal  VARCHAR(5)     NOT NULL,
    uso_cfdi                 VARCHAR(4)     NOT NULL,
    subtotal                 NUMERIC(18, 2) NOT NULL,
    iva                      NUMERIC(18, 2) NOT NULL,
    retenciones              NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total                    NUMERIC(18, 2) NOT NULL,
    estado                   VARCHAR(24)    NOT NULL DEFAULT 'borrador',
    folio_fiscal             UUID,
    sello_sat                TEXT,
    fecha_timbrado           TIMESTAMPTZ,
    motivo_cancelacion       VARCHAR(4),
    version                  BIGINT         NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    CONSTRAINT pk_factura PRIMARY KEY (id),
    CONSTRAINT fk_factura_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_factura_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_factura_cotizacion FOREIGN KEY (cotizacion_id) REFERENCES cotizacion (id),
    CONSTRAINT fk_factura_orden_fabricacion FOREIGN KEY (orden_fabricacion_id) REFERENCES orden_fabricacion (id),
    -- Origen exactamente uno: Cotizacion aprobada O Orden_Fabricacion (Req 34.1).
    CONSTRAINT ck_factura_origen_exclusivo CHECK (
        (cotizacion_id IS NOT NULL AND orden_fabricacion_id IS NULL)
        OR (cotizacion_id IS NULL AND orden_fabricacion_id IS NOT NULL)),
    -- Estados permitidos (Req 35.7). Etiquetas ASCII minusculas.
    CONSTRAINT ck_factura_estado CHECK (
        estado IN ('borrador', 'timbrada', 'cancelacion_en_proceso', 'cancelada')),
    -- Importes no negativos y acotados a la escala monetaria (Req 34.2).
    CONSTRAINT ck_factura_subtotal_no_negativo CHECK (subtotal >= 0),
    CONSTRAINT ck_factura_iva_no_negativo CHECK (iva >= 0),
    CONSTRAINT ck_factura_retenciones_no_negativo CHECK (retenciones >= 0),
    CONSTRAINT ck_factura_total_no_negativo CHECK (total >= 0)
);

CREATE INDEX ix_factura_tenant_id ON factura (tenant_id);

-- Apoyo a los filtros del listado (Req 34.4): por Cliente y por estado, siempre
-- acotados al tenant.
CREATE INDEX ix_factura_tenant_cliente ON factura (tenant_id, cliente_id);
CREATE INDEX ix_factura_tenant_estado ON factura (tenant_id, estado);

-- Folio_Fiscal unico por tenant, solo cuando existe (Req 35.1). Los borradores
-- (folio NULL) no participan del indice unico parcial y no colisionan entre si.
CREATE UNIQUE INDEX uq_factura_tenant_folio_fiscal
    ON factura (tenant_id, folio_fiscal)
    WHERE folio_fiscal IS NOT NULL;

-- ----------------------------------------------------------------------------
-- nota_credito
--   CFDI de egreso que referencia una Factura timbrada (Req 37). tenant-scoped
--   (Req 23). monto > 0 y acotado por el saldo de la Factura (Req 37.2, dominio).
-- ----------------------------------------------------------------------------
CREATE TABLE nota_credito (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    factura_id      UUID           NOT NULL,
    cliente_id      UUID           NOT NULL,
    monto           NUMERIC(18, 2) NOT NULL,
    estado          VARCHAR(24)    NOT NULL DEFAULT 'borrador',
    folio_fiscal    UUID,
    sello_sat       TEXT,
    fecha_timbrado  TIMESTAMPTZ,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_nota_credito PRIMARY KEY (id),
    CONSTRAINT fk_nota_credito_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_nota_credito_factura FOREIGN KEY (factura_id) REFERENCES factura (id),
    CONSTRAINT fk_nota_credito_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    -- Estados permitidos. Etiquetas ASCII minusculas.
    CONSTRAINT ck_nota_credito_estado CHECK (
        estado IN ('borrador', 'timbrada', 'cancelada')),
    -- Monto estrictamente positivo (Req 37.2).
    CONSTRAINT ck_nota_credito_monto_positivo CHECK (monto > 0)
);

CREATE INDEX ix_nota_credito_tenant_id ON nota_credito (tenant_id);

-- Apoyo a la suma de notas previas por Factura (calculo del saldo, Req 37.2) y al
-- filtro del listado por Factura, acotado al tenant.
CREATE INDEX ix_nota_credito_tenant_factura ON nota_credito (tenant_id, factura_id);

-- Folio_Fiscal unico por tenant, solo cuando existe (Req 37.1, 37.3).
CREATE UNIQUE INDEX uq_nota_credito_tenant_folio_fiscal
    ON nota_credito (tenant_id, folio_fiscal)
    WHERE folio_fiscal IS NOT NULL;

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V14/V17/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE factura ENABLE ROW LEVEL SECURITY;
ALTER TABLE factura FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON factura
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE nota_credito ENABLE ROW LEVEL SECURITY;
ALTER TABLE nota_credito FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON nota_credito
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- PERMISOS ADICIONALES DE NOTA DE CREDITO (Req 3, 27.11) -- DECISION 6
--   V5 sembro nota_credito:{crear,leer} y los asigno al rol `contabilidad`. El
--   listado (GET) y el timbrado (POST .../timbrado) exigen nota_credito:listar y
--   nota_credito:cambiar_estado, que V5 no incluia. Se agregan aqui y se enlazan
--   al MISMO rol predefinido `contabilidad` (UUID fijo de V5), con el estilo de
--   V9 (ON CONFLICT DO NOTHING por la clave natural (recurso, operacion)).
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('nota_credito', 'listar'),
    ('nota_credito', 'cambiar_estado')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000b', p.id
FROM permiso p
WHERE p.recurso = 'nota_credito' AND p.operacion IN ('listar', 'cambiar_estado')
ON CONFLICT DO NOTHING;
