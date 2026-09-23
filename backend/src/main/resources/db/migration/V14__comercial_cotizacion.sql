-- ============================================================================
-- V14__comercial_cotizacion.sql
--
-- Submodulo `cotizaciones` del modulo comercial-crm (Tarea 17.2, Req 6, 12, 23).
-- Replica EXACTAMENTE el patron reutilizable establecido en
-- V11__comercial_cliente_contacto.sql, V12__comercial_producto_listas_precios.sql
-- y V13__comercial_oportunidad.sql para toda tabla tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql.
--
-- Requisitos cubiertos:
--   - Req 6.1 (alta con Cliente existente y estado inicial 'borrador'):
--     cliente_id NOT NULL con FK -> cliente; estado VARCHAR(20) NOT NULL DEFAULT
--     'borrador'. La regla de 1..500 Partida_Cotizacion la aplica el dominio
--     (422) en el camino de alta manual; ver DECISION 2 sobre la conversion.
--   - Req 6.3/6.4 (rango de cantidad y precio de la partida): en
--     partida_cotizacion, cantidad INTEGER con CHECK 1..999999 y precio_unitario
--     NUMERIC(18,2) con CHECK 0.01..999999999.99, que reflejan EXACTAMENTE la
--     validacion del dominio (CotizacionValidaciones), coherente con
--     precio_producto de V12 y valor_estimado de V13.
--   - Req 6.5 (total = suma de subtotales, half-up escala 2): subtotal/total
--     NUMERIC(18,2); el calculo half-up lo realiza el dominio (Property 2).
--   - Req 6.6/6.7 (maquina de estados): estado con CHECK IN ('borrador',
--     'enviada','aprobada','rechazada'); la maquina de estados pura vive en el
--     dominio (EstadoCotizacion), etapas finales 'aprobada'/'rechazada'.
--   - Req 6.9 (filtros por Cliente/estado): indices de apoyo.
--   - Req 6.10 (auditoria del cambio de estado): la registra la aplicacion.
--   - Req 23 (multi-tenant): tenant_id + RLS por tabla (ambas).
--   - Req 49 (concurrencia optimista): columna version en ambas tablas.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con Cliente/Producto/Oportunidad en V11/V12/V13.
--   2. cantidad como INTEGER: el Req 6.3 acota la cantidad al rango entero
--      1..999,999 ("cantidad entre 1 y 999,999"), lo que sugiere unidades
--      enteras; se modela INTEGER con CHECK 1..999999. El precio unitario y los
--      subtotales/totales son NUMERIC(18,2) (BigDecimal escala 2 half-up en Java).
--   3. oportunidad_id con FK nullable -> oportunidad: enlaza la Cotizacion de
--      vuelta a la Oportunidad de origen cuando se crea por conversion (Req 14.5).
--      Es nullable porque una Cotizacion puede crearse manualmente sin
--      Oportunidad. La columna inversa oportunidad.cotizacion_id (V13, sin FK) se
--      rellena desde el dominio de Oportunidad al convertir; aqui NO se agrega esa
--      FK inversa para no acoplar V14 al ciclo de vida de V13.
--   4. CONVERSION Y REGLA DE >=1 PARTIDA (Req 6.1/6.2 vs Req 14.5): la creacion
--      MANUAL de una Cotizacion exige >=1 y <=500 Partida_Cotizacion (Req 6.1/6.2,
--      validado por el dominio). La creacion por CONVERSION desde una Oportunidad
--      ganada (Req 14.5) crea un CASCARON en estado 'borrador' SIN partidas, que
--      se agregan despues via el endpoint de partidas. Ademas, el paso
--      'borrador'->'enviada' exige >=1 partida (guarda del dominio), de modo que
--      ninguna Cotizacion vacia puede enviarse/aprobarse. Asi se concilian ambos
--      requisitos de forma explicita y consistente.
--   5. PERMISOS: V5 ya sembro cotizacion:{crear,leer,listar,actualizar,
--      cambiar_estado} y los asigno al rol `ventas` (UUID ...005, Req 27.2) y el
--      'cambiar_estado' tambien a `gerente`. Por tanto V14 NO necesita sembrar
--      permisos adicionales.
--   6. ON DELETE CASCADE de partida_cotizacion -> cotizacion: al eliminar una
--      Cotizacion se eliminan sus partidas. El borrado de Clientes es logico, de
--      modo que las FK a cliente/oportunidad no declaran cascada.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- cotizacion
--   Documento comercial con partidas y totales (Req 6). tenant-scoped (Req 23).
--   Estado inicial 'borrador' (Req 6.1); estados finales 'aprobada'/'rechazada'
--   (Req 6.6). subtotal/total derivados de las partidas (Req 6.5).
-- ----------------------------------------------------------------------------
CREATE TABLE cotizacion (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    cliente_id      UUID           NOT NULL,
    oportunidad_id  UUID,
    estado          VARCHAR(20)    NOT NULL DEFAULT 'borrador',
    subtotal        NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total           NUMERIC(18, 2) NOT NULL DEFAULT 0,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_cotizacion PRIMARY KEY (id),
    CONSTRAINT fk_cotizacion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_cotizacion_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_cotizacion_oportunidad FOREIGN KEY (oportunidad_id) REFERENCES oportunidad (id),
    -- Estados permitidos (Req 6.6). Etiquetas ASCII minusculas, coherente con V13.
    CONSTRAINT ck_cotizacion_estado CHECK (
        estado IN ('borrador', 'enviada', 'aprobada', 'rechazada')),
    -- Totales no negativos y acotados a la escala monetaria (Req 6.5).
    CONSTRAINT ck_cotizacion_subtotal_no_negativo CHECK (subtotal >= 0),
    CONSTRAINT ck_cotizacion_total_no_negativo CHECK (total >= 0)
);

CREATE INDEX ix_cotizacion_tenant_id ON cotizacion (tenant_id);

-- Apoyo a los filtros del listado (Req 6.9): por Cliente y por estado, siempre
-- acotados al tenant.
CREATE INDEX ix_cotizacion_tenant_cliente ON cotizacion (tenant_id, cliente_id);
CREATE INDEX ix_cotizacion_tenant_estado ON cotizacion (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- partida_cotizacion
--   Renglon de una Cotizacion con cantidad, precio unitario y subtotal (Req 6.3).
--   tenant-scoped (Req 23). Puede referir un Producto para sugerir su precio
--   (Req 59.4) o ser de texto libre. cantidad entera 1..999,999 (Req 6.3/6.4);
--   precio_unitario 0.01..999,999,999.99 (Req 6.3/6.4).
-- ----------------------------------------------------------------------------
CREATE TABLE partida_cotizacion (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID           NOT NULL,
    cotizacion_id    UUID           NOT NULL,
    producto_id      UUID,
    descripcion      VARCHAR(500)   NOT NULL,
    cantidad         INTEGER        NOT NULL,
    precio_unitario  NUMERIC(18, 2) NOT NULL,
    subtotal         NUMERIC(18, 2) NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    CONSTRAINT pk_partida_cotizacion PRIMARY KEY (id),
    CONSTRAINT fk_partida_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_partida_cotizacion FOREIGN KEY (cotizacion_id) REFERENCES cotizacion (id) ON DELETE CASCADE,
    CONSTRAINT fk_partida_producto FOREIGN KEY (producto_id) REFERENCES producto (id),
    -- Rango de cantidad (Req 6.3/6.4): refleja EXACTAMENTE la validacion del
    -- dominio (CotizacionValidaciones). Una cantidad fuera de rango se rechaza.
    CONSTRAINT ck_partida_cantidad_rango CHECK (cantidad >= 1 AND cantidad <= 999999),
    -- Rango de precio unitario (Req 6.3/6.4): identico a precio_producto de V12.
    CONSTRAINT ck_partida_precio_rango CHECK (precio_unitario >= 0.01 AND precio_unitario <= 999999999.99),
    -- Subtotal no negativo; el dominio lo calcula como cantidad*precio half-up.
    CONSTRAINT ck_partida_subtotal_no_negativo CHECK (subtotal >= 0)
);

CREATE INDEX ix_partida_cotizacion_tenant_id ON partida_cotizacion (tenant_id);

-- Apoyo a la recuperacion de las partidas de una Cotizacion (recalculo de
-- totales y proyeccion a DTO), acotada al tenant.
CREATE INDEX ix_partida_cotizacion_tenant_cotizacion ON partida_cotizacion (tenant_id, cotizacion_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V11/V12/V13/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE cotizacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE cotizacion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON cotizacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE partida_cotizacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE partida_cotizacion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON partida_cotizacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
