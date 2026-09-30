-- ============================================================================
-- V88__inventario_avanzado_alerta_stock.sql
--
-- Mejora enterprise del modulo Inventario Avanzado (Req 60): ALERTAS DE STOCK
-- CONSULTABLES. Hasta ahora la deteccion de condiciones de stock (minimo/maximo/
-- reabastecimiento) existia en el servicio (evaluarNotificaciones), pero la unica
-- "entrega" era un placeholder que escribia en el log (no consultable por el
-- usuario). Se introduce una BITACORA PERSISTENTE de alertas de inventario para
-- que la UI pueda listarlas, revisarlas y darles seguimiento.
--
--   tabla alerta_inventario:
--     * tipo         -> minimo | maximo | reabastecimiento
--     * almacen_id, material_id, nombre_material (snapshot para el mensaje)
--     * cantidad     -> saldo en el momento de la deteccion
--     * umbral       -> punto de reorden (min/reab) o stock maximo (max)
--     * atendida     -> baja logica de seguimiento (una alerta resuelta se marca)
--     * detectada_en -> instante de la deteccion
--
-- Se anade el permiso atomico alerta_inventario:listar, asignado al rol `almacen`
-- (UUID ...008, Req 27.5). tenant-scoped con RLS identica al patron de V26.
-- Patron de siembra de permisos idempotente (ON CONFLICT DO NOTHING).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- alerta_inventario
--   Bitacora persistente de las condiciones de stock detectadas (Req 60).
--   tenant-scoped (Req 23). Append-only en la practica: la deteccion inserta;
--   el seguimiento solo alterna `atendida` (no se borran para conservar historial).
-- ----------------------------------------------------------------------------
CREATE TABLE alerta_inventario (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    -- Tipo de la alerta (minimo | maximo | reabastecimiento).
    tipo            VARCHAR(20)    NOT NULL,
    almacen_id      UUID           NOT NULL,
    material_id     UUID           NOT NULL,
    -- Snapshot del nombre del Material al momento de la deteccion (para el mensaje).
    nombre_material VARCHAR(200),
    -- Saldo en el momento de la deteccion; no negativo.
    cantidad        NUMERIC(18,3)  NOT NULL,
    -- Umbral cruzado (punto de reorden o stock maximo); no negativo.
    umbral          NUMERIC(18,3)  NOT NULL,
    -- Seguimiento: una alerta atendida/resuelta se marca (no se borra).
    atendida        BOOLEAN        NOT NULL DEFAULT FALSE,
    -- Instante de la deteccion.
    detectada_en    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- Columnas heredadas de TenantScopedEntity (Req 49) y auditoria.
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_alerta_inventario PRIMARY KEY (id),
    CONSTRAINT fk_alerta_inventario_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_alerta_inventario_almacen FOREIGN KEY (almacen_id) REFERENCES almacen (id),
    CONSTRAINT fk_alerta_inventario_material FOREIGN KEY (material_id) REFERENCES material (id),
    -- Tipos permitidos.
    CONSTRAINT ck_alerta_inventario_tipo CHECK (tipo IN ('minimo', 'maximo', 'reabastecimiento')),
    -- No negatividad (defensa en profundidad).
    CONSTRAINT ck_alerta_inventario_cantidad_no_negativa CHECK (cantidad >= 0),
    CONSTRAINT ck_alerta_inventario_umbral_no_negativo CHECK (umbral >= 0)
);

CREATE INDEX ix_alerta_inventario_tenant_id ON alerta_inventario (tenant_id);

-- Apoyo al listado de alertas recientes y pendientes del tenant.
CREATE INDEX ix_alerta_inventario_tenant_detectada
    ON alerta_inventario (tenant_id, detectada_en DESC);
CREATE INDEX ix_alerta_inventario_tenant_atendida
    ON alerta_inventario (tenant_id, atendida);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V26.
-- ----------------------------------------------------------------------------
ALTER TABLE alerta_inventario ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerta_inventario FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON alerta_inventario
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Permisos atomicos: consultar y dar seguimiento a las alertas de inventario.
-- Se asignan al rol `almacen` (UUID ...008, Req 27.5) que ya opera el modulo.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES ('alerta_inventario', 'listar'),
       ('alerta_inventario', 'actualizar')
ON CONFLICT DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000008', p.id
FROM permiso p
WHERE p.recurso = 'alerta_inventario' AND p.operacion IN ('listar', 'actualizar')
ON CONFLICT DO NOTHING;
