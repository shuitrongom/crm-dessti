-- ============================================================================
-- V28__compras_proveedor_requisicion_orden.sql
--
-- Modulo `compras-abastecimiento` (Bloque 26; tareas 26.1, 26.2, 26.3;
-- Req 29, 30, 31, 12, 23, 49). Crea las cinco tablas del aprovisionamiento:
--   * proveedor              (Req 29): origen de abastecimiento con RFC unico.
--   * requisicion_compra     (Req 30): solicitud interna de Materiales.
--   * partida_requisicion    (Req 30): renglon (Material + cantidad).
--   * orden_compra           (Req 31): documento de compra con total.
--   * partida_orden_compra   (Req 31): renglon (Material + cantidad + precio).
--
-- Replica EXACTAMENTE el patron reutilizable establecido en
-- V11__comercial_cliente_contacto.sql (proveedor <-> cliente: `activo`, RFC unico
-- por tenant entre activos) y V14__comercial_cotizacion.sql (documento padre +
-- lineas con dinero NUMERIC(18,2), maquina de estados, RLS) para toda tabla
-- tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql (ENABLE+FORCE).
--
-- Requisitos cubiertos:
--   - Req 29 (Proveedor): datos obligatorios (nombre 1..200, RFC, >=1 contacto),
--     RFC unico POR TENANT entre ACTIVOS (indice unico parcial), borrado logico
--     (activo), listado paginado filtrable por nombre/RFC, auditoria.
--   - Req 30 (Requisicion_Compra): alta con >=1 Material (cantidad 1..999999),
--     estado inicial 'borrador'; maquina de estados con finales aprobada/rechazada/
--     cancelada; generacion de Orden_Compra desde 'aprobada'; listado por estado.
--   - Req 31 (Orden_Compra): alta con Proveedor y 1..500 partidas (cantidad
--     1..999999, precio 0.01..999999999.99); subtotal=cantidad*precio half-up 2
--     decimales, total=suma de subtotales; estado inicial 'abierta'; maquina de
--     estados con finales cerrada/cancelada; listado por Proveedor y estado.
--   - Req 23 (multi-tenant): tenant_id + RLS en las cinco tablas.
--   - Req 49 (concurrencia optimista): columna version en las cinco tablas.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabricas de dominio), coherente
--      con V11..V26.
--   2. RFC UNICO POR TENANT ENTRE ACTIVOS (Req 29.2, 23.6): igual que `cliente`
--      en V11, se usa un INDICE UNICO PARCIAL sobre (tenant_id, rfc) WHERE
--      activo = TRUE, de modo que el RFC de un Proveedor dado de baja logica se
--      puede reutilizar. La aplicacion (ServicioProveedores) comprueba
--      existsByRfcAndActivoTrue antes de insertar y traduce la violacion del
--      indice ante carreras concurrentes a HTTP 409. rfc VARCHAR(20) (el dominio
--      acota a 12..13; VARCHAR(20) deja holgura, coherente con la columna del
--      Proveedor descrita en el modelo de datos).
--   3. AL MENOS UN DATO DE CONTACTO (Req 29.1): CHECK (email IS NOT NULL OR
--      telefono IS NOT NULL) como segunda capa; el formato de email/telefono lo
--      valida el dominio (ProveedorValidaciones), no por CHECK, para no duplicar
--      la logica de formato (igual criterio que `cliente` en V11).
--   4. BORRADO LOGICO DEL PROVEEDOR (Req 29.5): columna activo BOOLEAN NOT NULL
--      DEFAULT TRUE; la baja conserva el historico (no se elimina la fila).
--   5. CANTIDAD COMO NUMERIC(18,3): aunque los Req 30.1 y 31.2 acotan la cantidad
--      al rango ENTERO 1..999999, se modela NUMERIC(18,3) por CONSISTENCIA con la
--      unidad de medida del inventario de Materiales (material.existencias/
--      stock_minimo son NUMERIC(18,3) en V18, y admiten unidades fraccionarias).
--      El rango entero se valida en el dominio (validarCantidad) y se refuerza con
--      CHECK (cantidad >= 1 AND cantidad <= 999999). Las entidades JPA exponen la
--      cantidad como int, coherente con el rango entero.
--   6. TOTALES HALF-UP (Req 31.3): subtotal/total NUMERIC(18,2); el redondeo
--      half-up (subtotal = round(cantidad*precio,2), total = round(Σ subtotales,2))
--      lo realiza el dominio (OrdenCompraValidaciones), identico a la Cotizacion
--      de V14. precio_unitario NUMERIC(18,2) con CHECK 0.01..999999999.99.
--   7. PARTIDAS 1..500 (Req 31.1): la cota superior de partidas la valida el
--      dominio/aplicacion (OrdenCompra.crear -> 422); no se impone por constraint
--      de BD (una tabla de lineas no acota su cardinalidad por fila). El minimo
--      (>=1) tambien lo garantiza el dominio.
--   8. GENERACION DE ORDEN DESDE REQUISICION (Req 30.3): requisicion_compra tiene
--      orden_compra_id UUID NULL (FK -> orden_compra) que se rellena al generar la
--      Orden; y orden_compra tiene requisicion_compra_id UUID NULL (FK ->
--      requisicion_compra) para el enlace inverso. Ambas FK son nullable porque el
--      alta de cada agregado es independiente (una Orden puede crearse directa sin
--      requisicion). La precondicion "requisicion aprobada" la aplica la
--      aplicacion (ServicioRequisiciones -> 422 si no aprobada).
--   9. ON DELETE CASCADE de las lineas hacia su padre (partida_requisicion ->
--      requisicion_compra, partida_orden_compra -> orden_compra): al eliminar el
--      padre se eliminan sus lineas, igual que partida_cotizacion en V14. Las FK a
--      material/proveedor NO declaran cascada (el Proveedor se da de baja LOGICA).
--  10. PERMISOS: V5 ya sembro proveedor:{crear,leer,listar,actualizar},
--      requisicion_compra:{crear,leer,listar,cambiar_estado} y
--      orden_compra:{crear,leer,listar,cambiar_estado} y los asigno al rol
--      `almacen` (UUID ...008, Req 27.5). Por tanto V28 NO siembra permisos.
--      La baja logica del Proveedor (Req 29.5) NO tiene un permiso 'eliminar'
--      propio (a diferencia del Cliente, cuyo 'eliminar' se agrego en V11): se
--      protege con proveedor:actualizar en el controlador, por ser una
--      modificacion del recurso, evitando introducir un permiso nuevo.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- proveedor
--   Origen de abastecimiento de Materiales (Req 29). tenant-scoped (Req 23).
--   RFC unico POR TENANT entre activos (indice unico parcial). Borrado logico.
-- ----------------------------------------------------------------------------
CREATE TABLE proveedor (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    nombre      VARCHAR(200) NOT NULL,
    rfc         VARCHAR(20)  NOT NULL,
    email       VARCHAR(320),
    telefono    VARCHAR(20),
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_proveedor PRIMARY KEY (id),
    CONSTRAINT fk_proveedor_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Nombre/razon social entre 1 y 200 caracteres (Req 29.1); VARCHAR(200) acota
    -- el maximo y este CHECK impone el minimo tras recortar espacios.
    CONSTRAINT ck_proveedor_nombre_no_vacio CHECK (length(btrim(nombre)) >= 1),
    -- Al menos un dato de contacto (Req 29.1) -> segunda capa; el formato lo valida
    -- el dominio (ProveedorValidaciones).
    CONSTRAINT ck_proveedor_contacto CHECK (email IS NOT NULL OR telefono IS NOT NULL)
);

CREATE INDEX ix_proveedor_tenant_id ON proveedor (tenant_id);

-- Apoyo a los filtros del listado por nombre y por RFC (Req 29.6), acotados al
-- tenant.
CREATE INDEX ix_proveedor_tenant_nombre ON proveedor (tenant_id, nombre);
CREATE INDEX ix_proveedor_tenant_rfc    ON proveedor (tenant_id, rfc);

-- Unicidad del RFC POR TENANT unicamente entre Proveedores ACTIVOS (Req 29.2,
-- 23.6): permite reutilizar el RFC de un Proveedor dado de baja logica.
CREATE UNIQUE INDEX uq_proveedor_rfc_activo_por_tenant
    ON proveedor (tenant_id, rfc)
    WHERE activo = TRUE;

COMMENT ON INDEX uq_proveedor_rfc_activo_por_tenant IS
    'Unicidad del RFC del Proveedor POR TENANT entre activos (Req 29.2, 23.6). El '
    'RFC se almacena normalizado a mayusculas por la entidad; la aplicacion '
    'comprueba existsByRfcAndActivoTrue y traduce la violacion a HTTP 409 ante '
    'carreras concurrentes. Un Proveedor inactivo (baja logica) libera su RFC.';

-- ----------------------------------------------------------------------------
-- requisicion_compra
--   Solicitud interna de Materiales (Req 30). tenant-scoped (Req 23). Estado
--   inicial 'borrador' (Req 30.1); finales 'aprobada'/'rechazada'/'cancelada'
--   (Req 30.2). orden_compra_id se rellena al generar la Orden (Req 30.3).
-- ----------------------------------------------------------------------------
CREATE TABLE requisicion_compra (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    estado            VARCHAR(12)  NOT NULL DEFAULT 'borrador',
    -- Orden_Compra generada desde esta requisicion (Req 30.3); NULL hasta generar.
    orden_compra_id   UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    CONSTRAINT pk_requisicion_compra PRIMARY KEY (id),
    CONSTRAINT fk_requisicion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Estados permitidos (Req 30.2). Etiquetas ASCII minusculas.
    CONSTRAINT ck_requisicion_estado CHECK (
        estado IN ('borrador', 'enviada', 'aprobada', 'rechazada', 'cancelada'))
    -- La FK orden_compra_id -> orden_compra se agrega despues de crear la tabla
    -- orden_compra (dependencia circular resuelta con ALTER TABLE al final).
);

CREATE INDEX ix_requisicion_compra_tenant_id ON requisicion_compra (tenant_id);

-- Apoyo al filtro del listado por estado (Req 30.5), acotado al tenant.
CREATE INDEX ix_requisicion_compra_tenant_estado ON requisicion_compra (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- partida_requisicion
--   Renglon de una Requisicion_Compra: Material + cantidad (Req 30.1).
--   tenant-scoped (Req 23). cantidad 1..999999 (validado en dominio + CHECK).
-- ----------------------------------------------------------------------------
CREATE TABLE partida_requisicion (
    id                     UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id              UUID          NOT NULL,
    requisicion_compra_id  UUID          NOT NULL,
    material_id            UUID          NOT NULL,
    cantidad               NUMERIC(18,3) NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by             VARCHAR(255),
    updated_by             VARCHAR(255),
    CONSTRAINT pk_partida_requisicion PRIMARY KEY (id),
    CONSTRAINT fk_partida_requisicion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_partida_requisicion_padre FOREIGN KEY (requisicion_compra_id)
        REFERENCES requisicion_compra (id) ON DELETE CASCADE,
    CONSTRAINT fk_partida_requisicion_material FOREIGN KEY (material_id) REFERENCES material (id),
    -- Rango de cantidad (Req 30.1): refleja la validacion del dominio. Aunque el
    -- rango es entero, el tipo es NUMERIC(18,3) por consistencia con material.
    CONSTRAINT ck_partida_requisicion_cantidad CHECK (cantidad >= 1 AND cantidad <= 999999)
);

CREATE INDEX ix_partida_requisicion_tenant_id ON partida_requisicion (tenant_id);

-- Apoyo a la recuperacion de las partidas de una requisicion, acotada al tenant.
CREATE INDEX ix_partida_requisicion_tenant_padre
    ON partida_requisicion (tenant_id, requisicion_compra_id);

-- ----------------------------------------------------------------------------
-- orden_compra
--   Documento de compra con partidas y total (Req 31). tenant-scoped (Req 23).
--   Estado inicial 'abierta' (Req 31.4); finales 'cerrada'/'cancelada' (Req 31.6).
--   total = round(Σ subtotales, 2) half-up (Req 31.3). Puede generarse desde una
--   Requisicion_Compra aprobada (Req 30.3, requisicion_compra_id).
-- ----------------------------------------------------------------------------
CREATE TABLE orden_compra (
    id                     UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id              UUID           NOT NULL,
    proveedor_id           UUID           NOT NULL,
    requisicion_compra_id  UUID,
    estado                 VARCHAR(16)    NOT NULL DEFAULT 'abierta',
    total                  NUMERIC(18, 2) NOT NULL DEFAULT 0,
    version                BIGINT         NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by             VARCHAR(255),
    updated_by             VARCHAR(255),
    CONSTRAINT pk_orden_compra PRIMARY KEY (id),
    CONSTRAINT fk_orden_compra_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_orden_compra_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedor (id),
    CONSTRAINT fk_orden_compra_requisicion FOREIGN KEY (requisicion_compra_id)
        REFERENCES requisicion_compra (id),
    -- Estados permitidos (Req 31.6). Etiquetas ASCII minusculas.
    CONSTRAINT ck_orden_compra_estado CHECK (
        estado IN ('abierta', 'recibida_parcial', 'recibida_total', 'cerrada', 'cancelada')),
    -- Total no negativo y acotado a la escala monetaria (Req 31.3).
    CONSTRAINT ck_orden_compra_total_no_negativo CHECK (total >= 0)
);

CREATE INDEX ix_orden_compra_tenant_id ON orden_compra (tenant_id);

-- Apoyo a los filtros del listado por Proveedor y por estado (Req 31.7), acotados
-- al tenant.
CREATE INDEX ix_orden_compra_tenant_proveedor ON orden_compra (tenant_id, proveedor_id);
CREATE INDEX ix_orden_compra_tenant_estado    ON orden_compra (tenant_id, estado);

-- FK diferida de requisicion_compra -> orden_compra (dependencia circular, Req 30.3).
ALTER TABLE requisicion_compra
    ADD CONSTRAINT fk_requisicion_orden_compra
    FOREIGN KEY (orden_compra_id) REFERENCES orden_compra (id);

-- ----------------------------------------------------------------------------
-- partida_orden_compra
--   Renglon de una Orden_Compra: Material + cantidad + precio + subtotal
--   (Req 31.2, 31.3). tenant-scoped (Req 23). cantidad 1..999999; precio_unitario
--   0.01..999999999.99; subtotal = round(cantidad*precio, 2) half-up (dominio).
-- ----------------------------------------------------------------------------
CREATE TABLE partida_orden_compra (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID           NOT NULL,
    orden_compra_id  UUID           NOT NULL,
    material_id      UUID           NOT NULL,
    cantidad         NUMERIC(18, 3) NOT NULL,
    precio_unitario  NUMERIC(18, 2) NOT NULL,
    subtotal         NUMERIC(18, 2) NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    CONSTRAINT pk_partida_orden_compra PRIMARY KEY (id),
    CONSTRAINT fk_partida_orden_compra_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_partida_orden_compra_padre FOREIGN KEY (orden_compra_id)
        REFERENCES orden_compra (id) ON DELETE CASCADE,
    CONSTRAINT fk_partida_orden_compra_material FOREIGN KEY (material_id) REFERENCES material (id),
    -- Rango de cantidad (Req 31.2): entero, tipo NUMERIC(18,3) por consistencia.
    CONSTRAINT ck_partida_orden_compra_cantidad CHECK (cantidad >= 1 AND cantidad <= 999999),
    -- Rango de precio unitario (Req 31.2): identico a partida_cotizacion de V14.
    CONSTRAINT ck_partida_orden_compra_precio CHECK (
        precio_unitario >= 0.01 AND precio_unitario <= 999999999.99),
    -- Subtotal no negativo; el dominio lo calcula como cantidad*precio half-up.
    CONSTRAINT ck_partida_orden_compra_subtotal_no_negativo CHECK (subtotal >= 0)
);

CREATE INDEX ix_partida_orden_compra_tenant_id ON partida_orden_compra (tenant_id);

-- Apoyo a la recuperacion de las partidas de una Orden (recalculo/proyeccion),
-- acotada al tenant.
CREATE INDEX ix_partida_orden_compra_tenant_padre
    ON partida_orden_compra (tenant_id, orden_compra_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V11/V14/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE proveedor ENABLE ROW LEVEL SECURITY;
ALTER TABLE proveedor FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON proveedor
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE requisicion_compra ENABLE ROW LEVEL SECURITY;
ALTER TABLE requisicion_compra FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON requisicion_compra
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE partida_requisicion ENABLE ROW LEVEL SECURITY;
ALTER TABLE partida_requisicion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON partida_requisicion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE orden_compra ENABLE ROW LEVEL SECURITY;
ALTER TABLE orden_compra FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON orden_compra
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE partida_orden_compra ENABLE ROW LEVEL SECURITY;
ALTER TABLE partida_orden_compra FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON partida_orden_compra
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- PERMISOS: no se siembran (DECISION 10). V5 ya sembro proveedor:{crear,leer,
-- listar,actualizar}, requisicion_compra:{crear,leer,listar,cambiar_estado} y
-- orden_compra:{crear,leer,listar,cambiar_estado} y los asigno al rol `almacen`.
-- La baja logica del Proveedor se protege con proveedor:actualizar en el
-- controlador; no se agrega un permiso 'eliminar' nuevo.
-- ----------------------------------------------------------------------------
