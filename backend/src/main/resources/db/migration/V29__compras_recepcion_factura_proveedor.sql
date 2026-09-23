-- ============================================================================
-- V29__compras_recepcion_factura_proveedor.sql
--
-- Modulo `compras-abastecimiento` (Bloque 27; tareas 27.1, 27.2;
-- Req 32, 33, 18, 12, 23, 49). Crea las tres tablas de la recepcion de
-- mercancia y de las facturas de proveedor con conciliacion de tres vias:
--   * recepcion_mercancia : cabecera de una recepcion contra una Orden_Compra.
--   * partida_recepcion   : renglon (partida de OC + Material + cantidad recibida).
--   * factura_proveedor   : Factura_Proveedor con estado y maquina de estados.
--
-- Replica EXACTAMENTE el patron reutilizable establecido en V28
-- (compras: documento padre + lineas, dinero NUMERIC(18,2), RLS) y V18
-- (inventario: movimientos y existencias) para toda tabla tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron ENABLE+FORCE de V17/V18/V28.
--
-- Requisitos cubiertos:
--   - Req 32 (Recepcion_Mercancia e integracion con inventario):
--       * Alta contra una Orden_Compra en estado 'abierta'/'recibida_parcial'
--         (Req 32.1); rechazo si la OC no admite recepciones (Req 32.2).
--       * Rechazo del exceso: la cantidad recibida ACUMULADA por partida no puede
--         superar la cantidad ordenada (Req 32.3) -> lo aplica el dominio/servicio.
--       * Generacion de un Movimiento_Inventario 'entrada' por Material recibido y
--         actualizacion de existencias (Req 32.4), en integracion con V18.
--       * Derivacion del estado de la OC: 'recibida_total' cuando todas las
--         partidas quedan completas (Req 32.5); 'recibida_parcial' con recepcion
--         parcial (Req 32.6) -> lo aplica el dominio/servicio.
--       * Listado paginado (20/100) filtrable por Orden_Compra (Req 32.7);
--         auditoria del alta (Req 32.8).
--   - Req 33 (Factura_Proveedor y Conciliacion_Tres_Vias):
--       * Alta con estado inicial 'registrada' asociada a una Orden_Compra
--         existente, con monto y folio del Proveedor (Req 33.1, 33.2).
--       * Conciliacion de tres vias: cantidad facturada <= recibida y precio
--         dentro de una tolerancia CONFIGURABLE (Req 33.3) -> dominio/servicio.
--       * Marca 'discrepancia' (no autoriza pago, Req 33.4) o 'conciliada'
--         (habilita pago, Req 33.5).
--       * Maquina de estados: 'registrada'->'conciliada', 'conciliada'->'pagada',
--         'registrada'->'discrepancia' (Req 33.6); guarda de autorizacion de pago
--         solo desde 'conciliada' (Req 33.7).
--       * Listado paginado (20/100) filtrable por Proveedor, Orden_Compra y estado
--         (Req 33.8); auditoria del cambio de estado (Req 33.9).
--   - Req 23 (multi-tenant): tenant_id + RLS en las tres tablas.
--   - Req 49 (concurrencia optimista): columna version en las tres tablas.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabricas de dominio), coherente
--      con V11..V28.
--   2. CANTIDAD RECIBIDA ACUMULADA <= ORDENADA POR PARTIDA (Req 32.3): NO se
--      impone por constraint de BD (requeriria una suma sobre partida_recepcion
--      contra partida_orden_compra); se valida en el dominio de forma PURA
--      (RecepcionMercancia / regla "no exceder lo ordenado", Property 10) y en el
--      servicio (ServicioRecepciones) sumando lo previamente recibido por partida.
--      El acumulado previo se obtiene con una consulta de agregacion sobre
--      partida_recepcion (ver ix (tenant_id, partida_orden_compra_id)).
--   3. ESTADO DE LA OC DERIVADO (Req 32.5, 32.6): no se persiste en estas tablas;
--      el servicio deriva el nuevo estado (funcion PURA, Property 11) a partir del
--      acumulado recibido por partida y actualiza orden_compra.estado con su propia
--      maquina de estados (V28), auditando el cambio.
--   4. cantidad_recibida COMO NUMERIC(18,3): consistente con material.existencias
--      (V18) y con partida_orden_compra.cantidad (V28), que admiten unidades
--      fraccionarias. CHECK (cantidad_recibida > 0): una recepcion registra solo
--      renglones con cantidad estrictamente positiva; el limite superior (no
--      exceder lo ordenado acumulado) lo aplica el dominio (Req 32.3).
--   5. GENERACION DE INVENTARIO (Req 32.4): la recepcion NO escribe en
--      movimiento_inventario directamente; invoca el puerto de aplicacion del
--      inventario (RecepcionMaterialPort -> ServicioInventario) que registra un
--      Movimiento_Inventario 'entrada' por Material y actualiza existencias, de
--      forma transaccional y auditada (mismo mecanismo que el consumo del Req 18.4,
--      pero con TipoMovimientoInventario.ENTRADA). Preserva la arquitectura
--      hexagonal: compras depende del contrato del inventario, no de su esquema.
--   6. MONTO Y TOLERANCIA (Req 33.3): factura_proveedor.monto NUMERIC(18,2)
--      (moneda unica, escala 2) con CHECK (monto >= 0). La TOLERANCIA de precio de
--      la conciliacion de tres vias NO se persiste: es CONFIGURABLE por propiedad
--      externa `crm.compras.conciliacion.tolerancia-precio` (fraccion decimal;
--      por defecto 0.02 = 2%), enlazada via @ConfigurationProperties
--      (ConciliacionProperties). Asi el umbral se ajusta sin migracion y las
--      pruebas de propiedad pueden inyectar un valor.
--   7. PROVEEDOR DENORMALIZADO EN LA FACTURA (Req 33.8): factura_proveedor guarda
--      proveedor_id (FK -> proveedor) ademas de orden_compra_id (FK ->
--      orden_compra) para soportar el filtro del listado por Proveedor de forma
--      directa (ix (tenant_id, proveedor_id)) sin unir con orden_compra. El
--      servicio lo deriva del Proveedor de la Orden_Compra al registrar.
--   8. ESTADOS DE LA FACTURA (Req 33.6): VARCHAR(14) con CHECK IN ('registrada',
--      'conciliada','discrepancia','pagada'). Transiciones permitidas por la
--      maquina de estados del dominio (EstadoFacturaProveedor):
--        registrada  -> conciliada | discrepancia
--        conciliada  -> pagada
--        (pagada, discrepancia: finales, sin salida)
--      El spec (Req 33.6) lista solo esas tres transiciones; por tanto
--      DISCREPANCIA es terminal (no vuelve a conciliada). Una re-conciliacion tras
--      corregir la discrepancia requeriria RE-REGISTRAR la factura (nuevo
--      registro); se documenta como trabajo futuro y no se habilita aqui para no
--      introducir transiciones fuera del Req 33.6.
--   9. ON DELETE CASCADE de las lineas hacia su cabecera (partida_recepcion ->
--      recepcion_mercancia), igual que las lineas de V28. Las FK a
--      partida_orden_compra / material / orden_compra / proveedor NO declaran
--      cascada (son catalogos/documentos con baja logica o inmutables).
--  10. PERMISOS: V5 ya sembro recepcion_mercancia:{crear,leer,listar} y
--      factura_proveedor:{crear,leer,listar} y los asigno al rol `almacen`
--      (UUID ...008, Req 27.5). SIN EMBARGO, V5 NO sembro
--      factura_proveedor:'cambiar_estado', necesario para la maquina de estados
--      (conciliar / autorizar pago, Req 33.6, 33.7). Por ello V29 SIEMBRA ese
--      unico permiso (patron INSERT ... ON CONFLICT de V5) y lo asigna al rol
--      `almacen`, ya que la asignacion masiva de V5 (por recurso) ya se ejecuto y
--      no recogeria el permiso nuevo. No se siembra ningun otro permiso.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- recepcion_mercancia
--   Cabecera de una recepcion registrada contra una Orden_Compra (Req 32.1).
--   tenant-scoped (Req 23). La precondicion de estado de la OC ('abierta'/
--   'recibida_parcial', Req 32.2) la aplica la aplicacion (ServicioRecepciones).
-- ----------------------------------------------------------------------------
CREATE TABLE recepcion_mercancia (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    orden_compra_id  UUID         NOT NULL,
    recibida_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    CONSTRAINT pk_recepcion_mercancia PRIMARY KEY (id),
    CONSTRAINT fk_recepcion_mercancia_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_recepcion_mercancia_orden_compra FOREIGN KEY (orden_compra_id)
        REFERENCES orden_compra (id)
);

CREATE INDEX ix_recepcion_mercancia_tenant_id ON recepcion_mercancia (tenant_id);

-- Apoyo al filtro del listado por Orden_Compra (Req 32.7) y al calculo del
-- acumulado recibido por Orden, acotado al tenant.
CREATE INDEX ix_recepcion_mercancia_tenant_orden
    ON recepcion_mercancia (tenant_id, orden_compra_id);

-- ----------------------------------------------------------------------------
-- partida_recepcion
--   Renglon de una recepcion: partida de OC + Material + cantidad recibida
--   (Req 32.1, 32.3). tenant-scoped (Req 23). cantidad_recibida > 0; el limite
--   superior (acumulado <= ordenado, Req 32.3) lo aplica el dominio/servicio.
-- ----------------------------------------------------------------------------
CREATE TABLE partida_recepcion (
    id                       UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                UUID          NOT NULL,
    recepcion_mercancia_id   UUID          NOT NULL,
    partida_orden_compra_id  UUID          NOT NULL,
    material_id              UUID          NOT NULL,
    cantidad_recibida        NUMERIC(18,3) NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    CONSTRAINT pk_partida_recepcion PRIMARY KEY (id),
    CONSTRAINT fk_partida_recepcion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_partida_recepcion_padre FOREIGN KEY (recepcion_mercancia_id)
        REFERENCES recepcion_mercancia (id) ON DELETE CASCADE,
    CONSTRAINT fk_partida_recepcion_partida_oc FOREIGN KEY (partida_orden_compra_id)
        REFERENCES partida_orden_compra (id),
    CONSTRAINT fk_partida_recepcion_material FOREIGN KEY (material_id) REFERENCES material (id),
    -- La cantidad recibida por renglon es estrictamente positiva (Req 32.1). El
    -- tope acumulado por partida (Req 32.3) lo garantiza el dominio (Property 10).
    CONSTRAINT ck_partida_recepcion_cantidad_positiva CHECK (cantidad_recibida > 0)
);

CREATE INDEX ix_partida_recepcion_tenant_id ON partida_recepcion (tenant_id);

-- Apoyo a la recuperacion de las partidas de una recepcion, acotada al tenant.
CREATE INDEX ix_partida_recepcion_tenant_padre
    ON partida_recepcion (tenant_id, recepcion_mercancia_id);

-- Apoyo al calculo del acumulado recibido POR PARTIDA de la Orden_Compra
-- (Req 32.3, 32.5), acotado al tenant.
CREATE INDEX ix_partida_recepcion_tenant_partida_oc
    ON partida_recepcion (tenant_id, partida_orden_compra_id);

-- ----------------------------------------------------------------------------
-- factura_proveedor
--   Factura_Proveedor asociada a una Orden_Compra (Req 33.1). tenant-scoped
--   (Req 23). Estado inicial 'registrada' (Req 33.1); finales 'pagada'/
--   'discrepancia' (Req 33.6). proveedor_id denormalizado para el filtro del
--   listado por Proveedor (Req 33.8).
-- ----------------------------------------------------------------------------
CREATE TABLE factura_proveedor (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID           NOT NULL,
    orden_compra_id  UUID           NOT NULL,
    proveedor_id     UUID           NOT NULL,
    folio_proveedor  VARCHAR(100)   NOT NULL,
    monto            NUMERIC(18,2)  NOT NULL,
    estado           VARCHAR(14)    NOT NULL DEFAULT 'registrada',
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    CONSTRAINT pk_factura_proveedor PRIMARY KEY (id),
    CONSTRAINT fk_factura_proveedor_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_factura_proveedor_orden_compra FOREIGN KEY (orden_compra_id)
        REFERENCES orden_compra (id),
    CONSTRAINT fk_factura_proveedor_proveedor FOREIGN KEY (proveedor_id)
        REFERENCES proveedor (id),
    -- Folio del Proveedor no vacio (Req 33.2); VARCHAR(100) acota el maximo y este
    -- CHECK impone el minimo tras recortar espacios.
    CONSTRAINT ck_factura_proveedor_folio_no_vacio CHECK (length(btrim(folio_proveedor)) >= 1),
    -- Monto no negativo (Req 33.2), escala 2 (moneda unica).
    CONSTRAINT ck_factura_proveedor_monto_no_negativo CHECK (monto >= 0),
    -- Estados permitidos (Req 33.6). Etiquetas ASCII minusculas.
    CONSTRAINT ck_factura_proveedor_estado CHECK (
        estado IN ('registrada', 'conciliada', 'discrepancia', 'pagada'))
);

CREATE INDEX ix_factura_proveedor_tenant_id ON factura_proveedor (tenant_id);

-- Apoyo a los filtros del listado por Orden_Compra, por Proveedor y por estado
-- (Req 33.8), acotados al tenant.
CREATE INDEX ix_factura_proveedor_tenant_orden     ON factura_proveedor (tenant_id, orden_compra_id);
CREATE INDEX ix_factura_proveedor_tenant_proveedor ON factura_proveedor (tenant_id, proveedor_id);
CREATE INDEX ix_factura_proveedor_tenant_estado    ON factura_proveedor (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V18/V28.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE recepcion_mercancia ENABLE ROW LEVEL SECURITY;
ALTER TABLE recepcion_mercancia FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON recepcion_mercancia
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE partida_recepcion ENABLE ROW LEVEL SECURITY;
ALTER TABLE partida_recepcion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON partida_recepcion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE factura_proveedor ENABLE ROW LEVEL SECURITY;
ALTER TABLE factura_proveedor FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON factura_proveedor
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- PERMISOS (DECISION 10): V5 sembro recepcion_mercancia:{crear,leer,listar} y
-- factura_proveedor:{crear,leer,listar} (rol `almacen`), pero NO
-- factura_proveedor:'cambiar_estado'. Se siembra aqui ese unico permiso y se
-- asigna al rol `almacen`, ya que la asignacion masiva por recurso de V5 ya se
-- ejecuto y no recogeria el permiso nuevo.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES ('factura_proveedor', 'cambiar_estado')
ON CONFLICT DO NOTHING;

-- Asignacion al rol `almacen` (UUID ...008, Req 27.5) del nuevo permiso.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000008', p.id
FROM permiso p
WHERE p.recurso = 'factura_proveedor' AND p.operacion = 'cambiar_estado'
ON CONFLICT DO NOTHING;
