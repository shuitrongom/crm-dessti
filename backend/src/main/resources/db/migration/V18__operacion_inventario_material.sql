-- ============================================================================
-- V18__operacion_inventario_material.sql
--
-- Submodulo `inventario de materiales` del modulo operacion-produccion
-- (Tarea 20.1, Req 18, 12, 23, 49). Establece las dos tablas base del inventario
-- de Materiales del Req 18: la raiz `material` (existencias por Material) y el
-- historial append-only `movimiento_inventario`. Replica EXACTAMENTE el patron
-- reutilizable establecido en V11..V17 para toda tabla tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V17.
--
-- Requisitos cubiertos:
--   - Req 18.1 (alta de Material con nombre 1..200, unidad de medida y stock
--     minimo >= 0; existencias iniciales 0): material.nombre VARCHAR(200) NOT NULL
--     con CHECK de longitud >= 1, unidad_medida VARCHAR(50) NOT NULL, stock_minimo
--     NUMERIC(18,3) NOT NULL CHECK >= 0, existencias NUMERIC(18,3) NOT NULL
--     DEFAULT 0.
--   - Req 18.2 (Movimiento_Inventario entrada/salida/ajuste actualiza existencias):
--     movimiento_inventario.tipo VARCHAR(10) CHECK IN ('entrada','salida','ajuste')
--     + cantidad; el motor de aplicacion vive en el dominio (Material.aplicarMovimiento).
--   - Req 18.3 (rechazar salida que dejaria existencias < 0; conservar existencias):
--     el dominio rechaza con ReglaNegocioException (422) y, como SEGUNDA CAPA de
--     defensa (Property 9), la BD impone CHECK (existencias >= 0) sobre `material`.
--   - Req 18.4 (consumo por Orden_Fabricacion): movimiento_inventario.orden_fabricacion_id
--     UUID NULL con FK -> orden_fabricacion; cada consumo registra un movimiento
--     'salida' referenciando la OF.
--   - Req 18.5 (notificar stock bajo): condicion derivada (existencias < stock_minimo)
--     evaluada por el dominio/aplicacion; la emite el NotificadorStockPort. No requiere
--     columnas aqui.
--   - Req 18.6 (listado paginado 20/100, filtro por nombre y por stock bajo):
--     indices de apoyo por (tenant_id, nombre) y por (tenant_id, material_id) en el
--     historial. El filtro por stock bajo es el predicado existencias < stock_minimo.
--   - Req 18.7 (auditoria de alta y de movimientos): la registra la aplicacion.
--   - Req 23 (multi-tenant): tenant_id + RLS en ambas tablas.
--   - Req 49 (concurrencia optimista): columna version (relevante en `material`;
--     una salida concurrente sobre existencias obsoletas produce
--     OptimisticLockingFailure -> 409, comportamiento aceptable documentado).
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. TIPO NUMERIC(18,3) PARA CANTIDADES DE MATERIAL: el inventario de Materiales
--      admite unidades fraccionarias (metros, litros, kilogramos), por lo que
--      stock_minimo, existencias, cantidad y existencias_resultantes se modelan
--      como NUMERIC(18,3), mapeado a BigDecimal (escala 3) en el dominio. El
--      inventario AVANZADO por Almacen del Req 60 (bloque 23) usa NUMERIC(18,4)
--      para costeo; aqui el Req 18 solo administra cantidades, sin costo, y 3
--      decimales bastan. La no negatividad se impone con signo por el dominio.
--   2. DEFENSA EN PROFUNDIDAD DE NO NEGATIVIDAD (Property 9): la regla "las
--      existencias nunca son negativas" (Req 18.3) se aplica en DOS capas:
--        (a) DOMINIO: Material.aplicarMovimiento rechaza una 'salida' que dejaria
--            existencias < 0 con ReglaNegocioException (422), conservando las
--            existencias sin cambios (fuente de verdad del mensaje "existencias
--            insuficientes").
--        (b) BD: CHECK (existencias >= 0) sobre `material` como red de seguridad
--            ante cualquier ruta que intente persistir un saldo negativo.
--   3. movimiento_inventario ES APPEND-ONLY (historial/Kardex base, Req 18.7):
--      registra cada movimiento con la cantidad y el snapshot existencias_resultantes
--      tras aplicarlo. La aplicacion nunca actualiza ni borra movimientos; el saldo
--      vivo se mantiene en material.existencias (inventario perpetuo del Req 18.2).
--      La columna version se incluye por herencia de TenantScopedEntity aunque el
--      historial no se modifique.
--   4. FK material_id -> material SIN cascada: el historial de movimientos es un
--      registro contable que no debe borrarse en cascada con el Material; ademas el
--      Material se da de baja de forma LOGICA (activo = FALSE), no fisica.
--   5. FK orden_fabricacion_id -> orden_fabricacion (V17) NULL: solo se fija cuando
--      el movimiento proviene del consumo de una Orden_Fabricacion (Req 18.4).
--   6. BAJA LOGICA DEL MATERIAL: columna activo BOOLEAN NOT NULL DEFAULT TRUE; el
--      alta y las consultas trabajan sobre materiales activos. Requiere el permiso
--      atomico material:eliminar, ausente en V5; se siembra abajo (patron de V12).
--   7. PERMISOS: V5 ya sembro material:{crear,leer,listar,actualizar} y
--      movimiento_inventario:{crear,leer,listar} y los asigno al rol `almacen`
--      (UUID a0000000-...-000000000008, Req 27.5). Falta material:eliminar para la
--      baja logica (Req 18, 3.1); se agrega al catalogo y se enlaza a `almacen`,
--      coherente con V12.
--   8. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V17.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- material
--   Raiz del inventario de Materiales (Req 18). tenant-scoped (Req 23). Mantiene
--   el saldo vivo `existencias` (inventario perpetuo, Req 18.2) que NUNCA puede ser
--   negativo (Req 18.3, Property 9): CHECK (existencias >= 0) como segunda capa.
-- ----------------------------------------------------------------------------
CREATE TABLE material (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    nombre          VARCHAR(200)   NOT NULL,
    unidad_medida   VARCHAR(50)    NOT NULL,
    -- Stock minimo para la deteccion de stock bajo (Req 18.5); >= 0 (Req 18.1).
    stock_minimo    NUMERIC(18,3)  NOT NULL DEFAULT 0,
    -- Saldo vivo de existencias (Req 18.2). Inicial 0 (Req 18.1). Nunca negativo
    -- (Req 18.3, Property 9) -> ver DECISION 2.
    existencias     NUMERIC(18,3)  NOT NULL DEFAULT 0,
    -- Baja logica (DECISION 6).
    activo          BOOLEAN        NOT NULL DEFAULT TRUE,
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_material PRIMARY KEY (id),
    CONSTRAINT fk_material_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Nombre entre 1 y 200 caracteres (Req 18.1); VARCHAR(200) acota el maximo y
    -- este CHECK impone el minimo tras recortar espacios.
    CONSTRAINT ck_material_nombre_no_vacio CHECK (length(btrim(nombre)) >= 1),
    -- Stock minimo mayor o igual a 0 (Req 18.1).
    CONSTRAINT ck_material_stock_minimo_no_negativo CHECK (stock_minimo >= 0),
    -- No negatividad de existencias (Req 18.3, Property 9) -> segunda capa de defensa.
    CONSTRAINT ck_material_existencias_no_negativo CHECK (existencias >= 0)
);

CREATE INDEX ix_material_tenant_id ON material (tenant_id);

-- Apoyo al filtro del listado por nombre (Req 18.6), acotado al tenant.
CREATE INDEX ix_material_tenant_nombre ON material (tenant_id, nombre);

-- ----------------------------------------------------------------------------
-- movimiento_inventario
--   Historial APPEND-ONLY (DECISION 3) de los movimientos de inventario del Req 18
--   (entrada/salida/ajuste). Cada fila guarda la cantidad del movimiento y el
--   snapshot existencias_resultantes tras aplicarlo (base del Kardex del Req 60).
--   tenant-scoped (Req 23).
-- ----------------------------------------------------------------------------
CREATE TABLE movimiento_inventario (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID           NOT NULL,
    material_id             UUID           NOT NULL,
    tipo                    VARCHAR(10)    NOT NULL,
    -- Cantidad del movimiento (siempre positiva; el signo lo determina el tipo).
    cantidad                NUMERIC(18,3)  NOT NULL,
    -- Snapshot de las existencias del Material tras aplicar este movimiento (Req 18.2).
    existencias_resultantes NUMERIC(18,3)  NOT NULL,
    -- Orden_Fabricacion de origen cuando el movimiento proviene de un consumo
    -- (Req 18.4); NULL en movimientos manuales. Ver DECISION 5.
    orden_fabricacion_id    UUID,
    -- Motivo/nota opcional del movimiento (p. ej. razon del ajuste).
    motivo                  VARCHAR(500),
    -- Columna heredada de TenantScopedEntity (Req 49); el historial no se modifica.
    version                 BIGINT         NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    CONSTRAINT pk_movimiento_inventario PRIMARY KEY (id),
    CONSTRAINT fk_movimiento_inventario_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_movimiento_inventario_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_movimiento_inventario_of FOREIGN KEY (orden_fabricacion_id)
        REFERENCES orden_fabricacion (id),
    -- Tipos permitidos (Req 18.2). Etiquetas ASCII minusculas, coherentes con V17.
    CONSTRAINT ck_movimiento_inventario_tipo CHECK (tipo IN ('entrada', 'salida', 'ajuste')),
    -- Cantidad estrictamente positiva; el sentido (suma/resta) lo aporta el tipo.
    CONSTRAINT ck_movimiento_inventario_cantidad_positiva CHECK (cantidad > 0),
    -- Snapshot no negativo, coherente con la no negatividad de existencias (Property 9).
    CONSTRAINT ck_movimiento_inventario_saldo_no_negativo CHECK (existencias_resultantes >= 0)
);

CREATE INDEX ix_movimiento_inventario_tenant_id ON movimiento_inventario (tenant_id);

-- Apoyo al listado del historial por Material (Req 18.6), acotado al tenant.
CREATE INDEX ix_movimiento_inventario_tenant_material
    ON movimiento_inventario (tenant_id, material_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V14/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE material ENABLE ROW LEVEL SECURITY;
ALTER TABLE material FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON material
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE movimiento_inventario ENABLE ROW LEVEL SECURITY;
ALTER TABLE movimiento_inventario FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON movimiento_inventario
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Permiso atomico faltante de baja logica del Material (Req 18, 3.1). V5 sembro
-- material:{crear,leer,listar,actualizar} y movimiento_inventario:{crear,leer,
-- listar} y los asigno al rol `almacen` (UUID ...008, Req 27.5). Falta la
-- operacion 'eliminar' del Material, necesaria para la baja logica (DECISION 6).
-- Se agrega al catalogo y se enlaza al rol `almacen`, coherente con V12.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES ('material', 'eliminar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT rol_id, p.id
FROM permiso p
CROSS JOIN (VALUES
        ('a0000000-0000-0000-0000-000000000008'::uuid)   -- almacen (Req 27.5)
    ) AS r(rol_id)
WHERE p.recurso = 'material' AND p.operacion = 'eliminar'
ON CONFLICT DO NOTHING;
