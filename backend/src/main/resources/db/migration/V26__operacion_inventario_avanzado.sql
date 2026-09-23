-- ============================================================================
-- V26__operacion_inventario_avanzado.sql
--
-- Submodulo `inventario avanzado por Almacen` del modulo operacion-produccion
-- (Tarea 23.1 y 23.2, Req 60, 18, 23, 49). Amplia el inventario BASE del Req 18
-- (tablas `material` y `movimiento_inventario` de V18) con la gestion por Almacen:
-- Almacenes, existencias por Almacen, Kardex por Almacen (read-model), lotes,
-- capas de costo PEPS y la configuracion de inventario por Material (stock maximo,
-- punto de reorden, metodo de costeo). Replica EXACTAMENTE el patron reutilizable
-- establecido en V11..V25 para toda tabla tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V18/V25.
--
-- ----------------------------------------------------------------------------
-- ALCANCE DE LA MIGRACION (una sola migracion para 23.1 y 23.2)
-- ----------------------------------------------------------------------------
-- Esta migracion crea TODO el esquema avanzado de una vez para que la tarea 23.2
-- (motor de costeo promedio/PEPS, uso efectivo de lotes y transferencias entre
-- Almacenes) NO requiera migraciones adicionales: solo agregara logica de
-- aplicacion/dominio sobre estas mismas tablas. La tarea 23.1 usa efectivamente:
-- almacen, config_inventario_material, existencia_almacen (lectura/listado),
-- movimiento_almacen (lectura del Kardex) y lote (alta basica). Las tablas
-- capa_costo y las columnas de costeo/transferencia de movimiento_almacen quedan
-- creadas y listas para 23.2.
--
-- Requisitos cubiertos (Req 60):
--   - Req 60 (Almacenes, existencias por Almacen, Kardex, costeo promedio/PEPS,
--     lotes, transferencias, configuracion de inventario por Material y alertas de
--     minimo/maximo/reabastecimiento). La tarea 23.1 materializa Almacenes, la
--     configuracion por Material, el listado de existencias, el Kardex de solo
--     lectura y las notificaciones de min/max/reabastecimiento; la tarea 23.2
--     agrega el motor de costeo, los lotes en movimientos y las transferencias.
--   - Req 23 (multi-tenant): tenant_id + RLS en TODAS las tablas.
--   - Req 49 (concurrencia optimista): columna version en TODAS las tablas.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. ESCALAS DECIMALES. Las CANTIDADES se modelan como NUMERIC(18,3) (mapeadas a
--      BigDecimal escala 3), coherente con el inventario base del Req 18 (V18) que
--      admite unidades fraccionarias. Los COSTOS se modelan como NUMERIC(18,4)
--      (mapeados a BigDecimal escala 4) para dar precision al costeo promedio/PEPS
--      del Req 60. Todo redondeo en el dominio es HALF_UP. La no negatividad se
--      impone tanto en el dominio como con CHECK en la BD (defensa en profundidad).
--   2. CONFIGURACION 1:1 EN TABLA SEPARADA (config_inventario_material). La
--      configuracion avanzada por Material (metodo de costeo, stock maximo, punto de
--      reorden derivado de consumo/tiempo de entrega/stock de seguridad, control de
--      lote) se separa en una tabla 1:1 con `material` para NO MODIFICAR la tabla
--      `material` del Req 18 (V18): asi el inventario base permanece intacto y el
--      avanzado se activa por Material bajo demanda. UNIQUE(tenant_id, material_id)
--      garantiza la relacion 1:1 dentro del tenant.
--   3. existencia_almacen ES EL SALDO VIVO POR (Almacen, Material). Cantidad y costo
--      promedio por Almacen; el motor de costeo de 23.2 mantendra estos valores. La
--      tarea 23.1 solo los lee/lista. UNIQUE(tenant_id, almacen_id, material_id).
--   4. movimiento_almacen ES EL KARDEX APPEND-ONLY POR ALMACEN (Req 60). Cada fila
--      guarda el movimiento (tipo, cantidad, costo unitario/total) y el snapshot del
--      saldo por Almacen tras aplicarlo (saldo_cantidad/saldo_costo_total), base del
--      Kardex cronologico. La tarea 23.1 solo lo consulta; la 23.2 lo escribe. Es
--      distinto del `movimiento_inventario` del Req 18 (historial por Material sin
--      costo ni Almacen); ambos coexisten.
--   5. capa_costo MODELA LAS CAPAS PEPS (Req 60). Cada capa registra la cantidad
--      restante y el costo unitario de una entrada, ordenadas por `secuencia` para
--      consumirlas en orden. La crea/consume el motor PEPS de 23.2; aqui solo se
--      define el esquema.
--   6. lote MODELA LOTES/CADUCIDAD (Req 60). Alta basica en 23.1 (codigo unico por
--      Material dentro del tenant, caducidad opcional); su uso en movimientos y capas
--      llega en 23.2. UNIQUE(tenant_id, material_id, codigo).
--   7. FKs SIN CASCADA. Los movimientos y capas son registros contables; las FKs a
--      almacen/material/lote no borran en cascada, coherente con V18/V25.
--   8. tipo VARCHAR(20) EN almacen ('sucursal'|'bodega') y VARCHAR(10) EN
--      config_inventario_material.metodo_costeo ('promedio'|'peps') con CHECK,
--      etiquetas ASCII minusculas, coherentes con la convencion de V17/V18.
--   9. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V25.
--  10. PERMISOS. V5 ya sembro y asigno al rol `almacen` (UUID ...008, Req 27.5):
--      almacen:{crear,leer,listar}, lote:{crear,leer,listar}, kardex:leer,
--      movimiento_inventario:{crear,leer,listar} y material:{crear,leer,listar,
--      actualizar}. NO se re-siembran. FALTA almacen:actualizar (edicion de Almacen,
--      Req 60), ausente en V5; se agrega al catalogo y se enlaza al rol `almacen`,
--      siguiendo el patron de siembra de V18 (recurso material:eliminar). La edicion
--      de la configuracion de inventario por Material viaja sobre material:actualizar
--      (ya existente en V5), pues es un atributo avanzado del Material.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- almacen
--   Almacen fisico o logico del tenant (Req 60): sucursal o bodega. tenant-scoped
--   (Req 23). Baja logica con `activo` (coherente con la baja logica del Material).
-- ----------------------------------------------------------------------------
CREATE TABLE almacen (
    id          UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID           NOT NULL,
    -- Nombre del Almacen (Req 60), no vacio tras recortar espacios.
    nombre      VARCHAR(200)   NOT NULL,
    -- Tipo del Almacen (DECISION 8): sucursal o bodega.
    tipo        VARCHAR(20)    NOT NULL DEFAULT 'bodega',
    -- Baja logica del Almacen.
    activo      BOOLEAN        NOT NULL DEFAULT TRUE,
    -- Columnas heredadas de TenantScopedEntity (Req 49) y auditoria.
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_almacen PRIMARY KEY (id),
    CONSTRAINT fk_almacen_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Nombre no vacio tras recortar espacios (Req 60).
    CONSTRAINT ck_almacen_nombre_no_vacio CHECK (length(btrim(nombre)) >= 1),
    -- Tipos permitidos (DECISION 8).
    CONSTRAINT ck_almacen_tipo CHECK (tipo IN ('sucursal', 'bodega'))
);

CREATE INDEX ix_almacen_tenant_id ON almacen (tenant_id);

-- Apoyo al listado paginado con filtro por nombre (Req 60), acotado al tenant.
CREATE INDEX ix_almacen_tenant_nombre ON almacen (tenant_id, nombre);

-- ----------------------------------------------------------------------------
-- config_inventario_material
--   Configuracion avanzada de inventario POR Material (Req 60), en tabla 1:1 con
--   `material` para NO modificar la tabla del Req 18 (DECISION 2). Guarda el metodo
--   de costeo (promedio/peps), el stock maximo, el control de lote y los parametros
--   del punto de reorden (consumo promedio, tiempo de entrega y stock de seguridad).
--   tenant-scoped (Req 23). El punto de reorden se DERIVA en el dominio:
--   consumo_promedio * tiempo_entrega_dias + stock_seguridad.
-- ----------------------------------------------------------------------------
CREATE TABLE config_inventario_material (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL,
    -- Material al que aplica la configuracion (Req 60). FK a material (V18); 1:1.
    material_id         UUID           NOT NULL,
    -- Metodo de costeo (DECISION 8): promedio ponderado o PEPS (FIFO).
    metodo_costeo       VARCHAR(10)    NOT NULL DEFAULT 'promedio',
    -- Stock maximo opcional (Req 60): NULL = sin tope; si se fija, >= 0.
    stock_maximo        NUMERIC(18,3),
    -- Control de lotes activado para este Material (Req 60). Uso efectivo en 23.2.
    control_lote        BOOLEAN        NOT NULL DEFAULT FALSE,
    -- Parametros del punto de reorden (Req 60), todos >= 0.
    consumo_promedio    NUMERIC(18,3)  NOT NULL DEFAULT 0,
    tiempo_entrega_dias INTEGER        NOT NULL DEFAULT 0,
    stock_seguridad     NUMERIC(18,3)  NOT NULL DEFAULT 0,
    -- Columnas heredadas de TenantScopedEntity (Req 49) y auditoria.
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    CONSTRAINT pk_config_inventario_material PRIMARY KEY (id),
    CONSTRAINT fk_config_inventario_material_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_config_inventario_material_material FOREIGN KEY (material_id) REFERENCES material (id),
    -- Metodos permitidos (DECISION 8).
    CONSTRAINT ck_config_inventario_material_metodo CHECK (metodo_costeo IN ('promedio', 'peps')),
    -- Stock maximo nulo o no negativo (Req 60).
    CONSTRAINT ck_config_inventario_material_stock_maximo
        CHECK (stock_maximo IS NULL OR stock_maximo >= 0),
    -- Parametros del punto de reorden no negativos (Req 60).
    CONSTRAINT ck_config_inventario_material_consumo_no_negativo CHECK (consumo_promedio >= 0),
    CONSTRAINT ck_config_inventario_material_tiempo_no_negativo CHECK (tiempo_entrega_dias >= 0),
    CONSTRAINT ck_config_inventario_material_seguridad_no_negativo CHECK (stock_seguridad >= 0),
    -- Relacion 1:1 con Material dentro del tenant (DECISION 2).
    CONSTRAINT uq_config_inventario_material_material UNIQUE (tenant_id, material_id)
);

CREATE INDEX ix_config_inventario_material_tenant_id ON config_inventario_material (tenant_id);

-- Apoyo a la busqueda de la configuracion por Material, acotado al tenant.
CREATE INDEX ix_config_inventario_material_tenant_material
    ON config_inventario_material (tenant_id, material_id);

-- ----------------------------------------------------------------------------
-- existencia_almacen
--   Saldo vivo de existencias de un Material EN un Almacen (Req 60, DECISION 3):
--   cantidad y costo promedio por Almacen. tenant-scoped (Req 23). La tarea 23.1
--   lo lee/lista; el motor de costeo de 23.2 lo mantiene. UNIQUE por (Almacen,
--   Material) dentro del tenant.
-- ----------------------------------------------------------------------------
CREATE TABLE existencia_almacen (
    id             UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID           NOT NULL,
    almacen_id     UUID           NOT NULL,
    material_id    UUID           NOT NULL,
    -- Cantidad en existencia en el Almacen (Req 60); nunca negativa.
    cantidad       NUMERIC(18,3)  NOT NULL DEFAULT 0,
    -- Costo promedio ponderado por unidad en el Almacen (Req 60); nunca negativo.
    costo_promedio NUMERIC(18,4)  NOT NULL DEFAULT 0,
    -- Columnas heredadas de TenantScopedEntity (Req 49) y auditoria.
    version        BIGINT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    CONSTRAINT pk_existencia_almacen PRIMARY KEY (id),
    CONSTRAINT fk_existencia_almacen_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_existencia_almacen_almacen FOREIGN KEY (almacen_id) REFERENCES almacen (id),
    CONSTRAINT fk_existencia_almacen_material FOREIGN KEY (material_id) REFERENCES material (id),
    -- No negatividad (defensa en profundidad, DECISION 1).
    CONSTRAINT ck_existencia_almacen_cantidad_no_negativa CHECK (cantidad >= 0),
    CONSTRAINT ck_existencia_almacen_costo_no_negativo CHECK (costo_promedio >= 0),
    -- Un unico saldo por (Almacen, Material) dentro del tenant (DECISION 3).
    CONSTRAINT uq_existencia_almacen_almacen_material UNIQUE (tenant_id, almacen_id, material_id)
);

CREATE INDEX ix_existencia_almacen_tenant_id ON existencia_almacen (tenant_id);

-- Apoyo al listado de existencias por Almacen y por Material (Req 60), al tenant.
CREATE INDEX ix_existencia_almacen_tenant_almacen ON existencia_almacen (tenant_id, almacen_id);
CREATE INDEX ix_existencia_almacen_tenant_material ON existencia_almacen (tenant_id, material_id);

-- ----------------------------------------------------------------------------
-- lote
--   Lote de un Material con caducidad opcional (Req 60, DECISION 6). tenant-scoped
--   (Req 23). Alta basica en 23.1; su uso en movimientos y capas de costo llega en
--   23.2. Codigo unico por Material dentro del tenant.
-- ----------------------------------------------------------------------------
CREATE TABLE lote (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    material_id     UUID           NOT NULL,
    -- Codigo del lote (Req 60), no vacio tras recortar espacios.
    codigo          VARCHAR(100)   NOT NULL,
    -- Fecha de caducidad opcional (Req 60).
    fecha_caducidad DATE,
    -- Columnas heredadas de TenantScopedEntity (Req 49) y auditoria.
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_lote PRIMARY KEY (id),
    CONSTRAINT fk_lote_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_lote_material FOREIGN KEY (material_id) REFERENCES material (id),
    -- Codigo no vacio tras recortar espacios (Req 60).
    CONSTRAINT ck_lote_codigo_no_vacio CHECK (length(btrim(codigo)) >= 1),
    -- Codigo unico por Material dentro del tenant (DECISION 6).
    CONSTRAINT uq_lote_material_codigo UNIQUE (tenant_id, material_id, codigo)
);

CREATE INDEX ix_lote_tenant_id ON lote (tenant_id);

-- Apoyo a la carga de los lotes de un Material (Req 60), acotado al tenant.
CREATE INDEX ix_lote_tenant_material ON lote (tenant_id, material_id);

-- ----------------------------------------------------------------------------
-- capa_costo
--   Capa de costo PEPS (FIFO) de un Material en un Almacen (Req 60, DECISION 5).
--   Cada capa registra la cantidad restante y el costo unitario de una entrada,
--   ordenadas por `secuencia` para consumirlas en orden. tenant-scoped (Req 23).
--   La crea/consume el motor PEPS de la tarea 23.2; aqui solo se define el esquema.
-- ----------------------------------------------------------------------------
CREATE TABLE capa_costo (
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID           NOT NULL,
    almacen_id        UUID           NOT NULL,
    material_id       UUID           NOT NULL,
    -- Lote de origen de la capa cuando el Material controla lotes (Req 60); opcional.
    lote_id           UUID,
    -- Cantidad aun disponible en la capa (Req 60); nunca negativa.
    cantidad_restante NUMERIC(18,3)  NOT NULL,
    -- Costo unitario de la capa (Req 60); nunca negativo.
    costo_unitario    NUMERIC(18,4)  NOT NULL,
    -- Orden de consumo PEPS: menor secuencia se consume primero (DECISION 5).
    secuencia         BIGINT         NOT NULL,
    -- Instante de creacion de la capa (para desempate/auditoria del costeo).
    creada_en         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- Columnas heredadas de TenantScopedEntity (Req 49) y auditoria.
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    CONSTRAINT pk_capa_costo PRIMARY KEY (id),
    CONSTRAINT fk_capa_costo_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_capa_costo_almacen FOREIGN KEY (almacen_id) REFERENCES almacen (id),
    CONSTRAINT fk_capa_costo_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_capa_costo_lote FOREIGN KEY (lote_id) REFERENCES lote (id),
    -- No negatividad (defensa en profundidad, DECISION 1).
    CONSTRAINT ck_capa_costo_cantidad_no_negativa CHECK (cantidad_restante >= 0),
    CONSTRAINT ck_capa_costo_costo_no_negativo CHECK (costo_unitario >= 0)
);

CREATE INDEX ix_capa_costo_tenant_id ON capa_costo (tenant_id);

-- Apoyo al consumo PEPS ordenado por (Almacen, Material, secuencia), al tenant.
CREATE INDEX ix_capa_costo_tenant_almacen_material_secuencia
    ON capa_costo (tenant_id, almacen_id, material_id, secuencia);

-- ----------------------------------------------------------------------------
-- movimiento_almacen
--   Kardex APPEND-ONLY POR ALMACEN (Req 60, DECISION 4). Cada fila registra un
--   movimiento (entrada/salida/transferencia/ajuste) con su costo unitario y total,
--   y el snapshot del saldo del (Almacen, Material) tras aplicarlo. tenant-scoped
--   (Req 23). La tarea 23.1 solo lo CONSULTA (Kardex cronologico); la 23.2 lo
--   ESCRIBE con el motor de costeo. Coexiste con `movimiento_inventario` (V18).
-- ----------------------------------------------------------------------------
CREATE TABLE movimiento_almacen (
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID           NOT NULL,
    almacen_id        UUID           NOT NULL,
    material_id       UUID           NOT NULL,
    -- Lote afectado cuando el Material controla lotes (Req 60); opcional.
    lote_id           UUID,
    -- Tipo del movimiento (Req 60): incluye las dos patas de una transferencia.
    tipo              VARCHAR(22)    NOT NULL,
    -- Cantidad del movimiento (siempre positiva; el sentido lo da el tipo).
    cantidad          NUMERIC(18,3)  NOT NULL,
    -- Costo unitario y total del movimiento (Req 60); no negativos.
    costo_unitario    NUMERIC(18,4)  NOT NULL,
    costo_total       NUMERIC(18,4)  NOT NULL,
    -- Snapshot del saldo del (Almacen, Material) tras aplicar el movimiento (Kardex).
    saldo_cantidad    NUMERIC(18,3)  NOT NULL,
    saldo_costo_total NUMERIC(18,4)  NOT NULL,
    -- Identificador que agrupa las dos patas de una transferencia (Req 60, 23.2).
    transferencia_id  UUID,
    -- Motivo/nota opcional del movimiento.
    motivo            VARCHAR(500),
    -- Columnas heredadas de TenantScopedEntity (Req 49); el Kardex no se modifica.
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    CONSTRAINT pk_movimiento_almacen PRIMARY KEY (id),
    CONSTRAINT fk_movimiento_almacen_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_movimiento_almacen_almacen FOREIGN KEY (almacen_id) REFERENCES almacen (id),
    CONSTRAINT fk_movimiento_almacen_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_movimiento_almacen_lote FOREIGN KEY (lote_id) REFERENCES lote (id),
    -- Tipos permitidos (Req 60). Etiquetas ASCII minusculas (DECISION 8).
    CONSTRAINT ck_movimiento_almacen_tipo CHECK (tipo IN (
        'entrada', 'salida', 'transferencia_salida', 'transferencia_entrada', 'ajuste')),
    -- Cantidad estrictamente positiva; el sentido lo aporta el tipo.
    CONSTRAINT ck_movimiento_almacen_cantidad_positiva CHECK (cantidad > 0),
    -- No negatividad de costos y saldos (defensa en profundidad, DECISION 1).
    CONSTRAINT ck_movimiento_almacen_costo_unitario_no_negativo CHECK (costo_unitario >= 0),
    CONSTRAINT ck_movimiento_almacen_costo_total_no_negativo CHECK (costo_total >= 0),
    CONSTRAINT ck_movimiento_almacen_saldo_cantidad_no_negativo CHECK (saldo_cantidad >= 0),
    CONSTRAINT ck_movimiento_almacen_saldo_costo_no_negativo CHECK (saldo_costo_total >= 0)
);

CREATE INDEX ix_movimiento_almacen_tenant_id ON movimiento_almacen (tenant_id);

-- Apoyo al Kardex cronologico por (Almacen, Material) (Req 60), acotado al tenant.
CREATE INDEX ix_movimiento_almacen_kardex
    ON movimiento_almacen (tenant_id, almacen_id, material_id, created_at);

-- Apoyo a consultas por Almacen y por Material, acotado al tenant.
CREATE INDEX ix_movimiento_almacen_tenant_almacen ON movimiento_almacen (tenant_id, almacen_id);
CREATE INDEX ix_movimiento_almacen_tenant_material ON movimiento_almacen (tenant_id, material_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V18/V25/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE almacen ENABLE ROW LEVEL SECURITY;
ALTER TABLE almacen FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON almacen
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE config_inventario_material ENABLE ROW LEVEL SECURITY;
ALTER TABLE config_inventario_material FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON config_inventario_material
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE existencia_almacen ENABLE ROW LEVEL SECURITY;
ALTER TABLE existencia_almacen FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON existencia_almacen
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE lote ENABLE ROW LEVEL SECURITY;
ALTER TABLE lote FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON lote
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE capa_costo ENABLE ROW LEVEL SECURITY;
ALTER TABLE capa_costo FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON capa_costo
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE movimiento_almacen ENABLE ROW LEVEL SECURITY;
ALTER TABLE movimiento_almacen FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON movimiento_almacen
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Permiso atomico faltante para la EDICION de Almacen (Req 60). V5 sembro
-- almacen:{crear,leer,listar} y los asigno al rol `almacen` (UUID ...008, Req 27.5),
-- pero NO almacen:actualizar, necesario para renombrar/reclasificar un Almacen
-- (DECISION 10). Se agrega al catalogo y se enlaza al rol `almacen`, siguiendo el
-- patron de siembra de V18 (material:eliminar). La edicion de la configuracion de
-- inventario por Material viaja sobre material:actualizar (ya existente en V5).
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES ('almacen', 'actualizar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT rol_id, p.id
FROM permiso p
CROSS JOIN (VALUES
        ('a0000000-0000-0000-0000-000000000008'::uuid)   -- almacen (Req 27.5)
    ) AS r(rol_id)
WHERE p.recurso = 'almacen' AND p.operacion = 'actualizar'
ON CONFLICT DO NOTHING;
