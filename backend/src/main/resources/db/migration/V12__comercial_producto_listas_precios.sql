-- ============================================================================
-- V12__comercial_producto_listas_precios.sql
--
-- Submodulo `catalogo de productos y listas de precios` del modulo
-- comercial-crm (Tarea 16.1, Req 59, 23). Replica EXACTAMENTE el patron
-- reutilizable establecido en V11__comercial_cliente_contacto.sql para toda
-- tabla tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql.
--
-- Requisitos cubiertos:
--   - Req 59.1/59.2 (alta de Producto con datos obligatorios: nombre 1..200,
--     unidad y descripcion): columnas NOT NULL; la validacion fina la aplica el
--     dominio (422). Identificador unico por Empresa via PK + tenant_id.
--   - Req 59.3/59.10 (precios entre 0.01 y 999,999,999.99): CHECK en
--     precio_producto que refleja EXACTAMENTE el rango del dominio; un precio
--     fuera de rango se rechaza (422 en la capa de aplicacion, defensa en BD).
--   - Req 59.5 (informacion comercial de apoyo: cliente meta, alianzas y
--     competencia): columnas TEXT nullable, datos descriptivos.
--   - Req 59.6 (borrado logico de Producto): columna activo BOOLEAN.
--   - Req 59.7 (listado paginado y filtro por nombre): indice de apoyo al filtro
--     por nombre sin distinguir mayusculas.
--   - Req 59.9 (seleccion por prioridad/segmento): lista_precios.prioridad y
--     lista_precios.segmento; la vigencia en lista_precios acota las listas
--     aplicables por fecha.
--   - Req 23 (multi-tenant): tenant_id + RLS por tabla.
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien puede proveer su propio UUID (lo hace la fabrica de
--      dominio, coherente con Cliente en V11).
--   2. timestamptz para las marcas temporales (UTC); DATE para la vigencia de la
--      lista (periodo por dia natural), coherente con Req 59.3.
--   3. MONEDA: NUMERIC(18,2) para el precio (convencion del proyecto,
--      BigDecimal escala 2 en Java). El CHECK acota el precio al rango
--      0.01..999,999,999.99 (Req 59.3, 59.10) y refleja la validacion del
--      dominio (PrecioProducto), de modo que ambas coinciden.
--   4. VIGENCIA DE PRECIOS: la vigencia se modela en `lista_precios`
--      (vigencia_inicio/vigencia_fin), de forma que un precio (precio_producto)
--      hereda la vigencia de su lista (design.md: "Lista_Precios/Precio_Producto
--      con vigencia y prioridad"). CHECK vigencia_fin >= vigencia_inicio.
--   5. UN PRECIO POR PRODUCTO Y LISTA: indice unico (tenant_id, lista_precios_id,
--      producto_id) para que un Producto tenga a lo sumo un precio en una lista
--      dada (design.md: UNIQUE (tenant_id, lista_id, producto_id)).
--   6. PRIORIDAD: entero; a MAYOR valor, MAYOR prioridad de aplicacion (Req 59.9).
--      La lista especifica de un segmento se prefiere sobre la general: esa
--      preferencia la resuelve la capa de aplicacion (ServicioSeleccionPrecio).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- producto
--   Bien o servicio del catalogo (tipo de anuncio luminoso). tenant-scoped
--   (Req 23). nombre/unidad/descripcion obligatorios (Req 59.1/59.2). Informacion
--   comercial de apoyo (cliente_meta, alianzas, competencia) como datos
--   descriptivos opcionales (Req 59.5). Borrado logico via activo (Req 59.6).
-- ----------------------------------------------------------------------------
CREATE TABLE producto (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    nombre        VARCHAR(200) NOT NULL,
    unidad        VARCHAR(50)  NOT NULL,
    descripcion   VARCHAR(2000) NOT NULL,
    cliente_meta  TEXT,
    alianzas      TEXT,
    competencia   TEXT,
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    CONSTRAINT pk_producto PRIMARY KEY (id),
    CONSTRAINT fk_producto_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id)
);

CREATE INDEX ix_producto_tenant_id ON producto (tenant_id);

-- Apoyo al filtro por nombre sin distinguir mayusculas (Req 59.7). Indice
-- funcional sobre LOWER(nombre) acotado al tenant.
CREATE INDEX ix_producto_tenant_nombre_lower ON producto (tenant_id, LOWER(nombre));

-- ----------------------------------------------------------------------------
-- lista_precios
--   Conjunto de precios vigentes de los Productos, aplicable por periodo o por
--   segmento de Cliente (Req 59.3, 59.9). tenant-scoped (Req 23). La prioridad
--   (mayor = se aplica antes) y el segmento (null = general) rigen la seleccion.
-- ----------------------------------------------------------------------------
CREATE TABLE lista_precios (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    nombre           VARCHAR(200) NOT NULL,
    prioridad        INTEGER      NOT NULL DEFAULT 0,
    segmento         VARCHAR(100),
    vigencia_inicio  DATE         NOT NULL,
    vigencia_fin     DATE,
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    CONSTRAINT pk_lista_precios PRIMARY KEY (id),
    CONSTRAINT fk_lista_precios_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- La vigencia abierta (vigencia_fin NULL) es valida; si hay fin, no puede
    -- ser anterior al inicio (Req 59.3).
    CONSTRAINT ck_lista_precios_vigencia CHECK (vigencia_fin IS NULL OR vigencia_fin >= vigencia_inicio)
);

CREATE INDEX ix_lista_precios_tenant_id ON lista_precios (tenant_id);

-- Apoyo a la seleccion por vigencia/prioridad (Req 59.9): filtra listas
-- vigentes y ordena por prioridad descendente.
CREATE INDEX ix_lista_precios_tenant_vigencia_prioridad
    ON lista_precios (tenant_id, vigencia_inicio, vigencia_fin, prioridad DESC);

-- ----------------------------------------------------------------------------
-- precio_producto
--   Precio de un Producto dentro de una Lista_Precios (Req 59.3). tenant-scoped
--   (Req 23). El precio se acota al rango 0.01..999,999,999.99 con un CHECK que
--   refleja la validacion del dominio (Req 59.3, 59.10). Un Producto tiene a lo
--   sumo un precio por lista (UNIQUE).
-- ----------------------------------------------------------------------------
CREATE TABLE precio_producto (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID          NOT NULL,
    lista_precios_id  UUID          NOT NULL,
    producto_id       UUID          NOT NULL,
    precio            NUMERIC(18, 2) NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    CONSTRAINT pk_precio_producto PRIMARY KEY (id),
    CONSTRAINT fk_precio_producto_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_precio_producto_lista FOREIGN KEY (lista_precios_id) REFERENCES lista_precios (id),
    CONSTRAINT fk_precio_producto_producto FOREIGN KEY (producto_id) REFERENCES producto (id),
    -- Rango de precios (Req 59.3, 59.10): refleja EXACTAMENTE la validacion del
    -- dominio (PrecioProducto). Property 30: un precio fuera de rango se rechaza.
    CONSTRAINT ck_precio_producto_rango CHECK (precio >= 0.01 AND precio <= 999999999.99)
);

CREATE INDEX ix_precio_producto_tenant_id ON precio_producto (tenant_id);

CREATE INDEX ix_precio_producto_tenant_producto ON precio_producto (tenant_id, producto_id);

-- Un Producto tiene a lo sumo un precio por Lista_Precios (Req 59.3).
CREATE UNIQUE INDEX uq_precio_producto_lista_producto
    ON precio_producto (tenant_id, lista_precios_id, producto_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V11/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE producto ENABLE ROW LEVEL SECURITY;
ALTER TABLE producto FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON producto
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE lista_precios ENABLE ROW LEVEL SECURITY;
ALTER TABLE lista_precios FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON lista_precios
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE precio_producto ENABLE ROW LEVEL SECURITY;
ALTER TABLE precio_producto FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON precio_producto
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Permiso atomico faltante de borrado logico del Producto (Req 59.6, 3.1). V5
-- sembro producto:{crear,leer,listar,actualizar} y lista_precios:{crear,leer,
-- listar,actualizar} y los asigno a los roles `ventas` (UUID ...005) y
-- `almacen` (UUID ...008). Falta la operacion 'eliminar' del Producto, necesaria
-- para la baja logica (Req 59.6). Se agrega al catalogo y se enlaza a ambos
-- roles, coherente con Req 27.2/27.5 (area comercial o de almacen mantiene el
-- catalogo). El guardado REST con @PreAuthorize corresponde a esta misma tarea.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES ('producto', 'eliminar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT rol_id, p.id
FROM permiso p
CROSS JOIN (VALUES
        ('a0000000-0000-0000-0000-000000000005'::uuid),  -- ventas (Req 27.2)
        ('a0000000-0000-0000-0000-000000000008'::uuid)   -- almacen (Req 27.5)
    ) AS r(rol_id)
WHERE p.recurso = 'producto' AND p.operacion = 'eliminar'
ON CONFLICT DO NOTHING;
