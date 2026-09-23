-- ============================================================================
-- V66__operacion_produccion_generalizacion.sql
--
-- Generalizacion del nucleo del modulo `operacion` (spec operacion-produccion-
-- enterprise, BLOQUE B/F, tarea 5.1). Agrupa tres bloques:
--
--   1) Generalizacion de `orden_fabricacion` (Req 1): se permite una Orden de
--      Fabricacion de ORIGEN GENERICO (sin Cotizacion). `cotizacion_id` pasa a
--      NULLABLE y el indice de unicidad "una Cotizacion -> a lo sumo una OF"
--      (V17: uq_orden_fabricacion_cotizacion) se vuelve PARCIAL, de modo que
--      solo aplica a filas con `cotizacion_id` NO nulo. Asi conviven multiples
--      OF genericas (todas con `cotizacion_id` NULL) sin romper la regla del
--      flujo de anuncios.
--
--   2) Tabla `partida_orden_fabricacion` (Req 5): Partidas/BOM de la OF (Material
--      + cantidad positiva). Tenant-scoped con RLS (Capa 2) y `version`
--      (concurrencia optimista), replicando EXACTAMENTE el patron reutilizable
--      de V2/V17. FK a `orden_fabricacion` con ON DELETE CASCADE (las partidas
--      son hijas de la OF) y FK a `material` (integridad referencial).
--
--   3) Seed idempotente de permisos (Req 2, 3; Decision acordada 8): se asignan
--      los permisos `orden_fabricacion:{crear,leer,listar,cambiar_estado}` y
--      `proyecto:{crear,leer,listar,actualizar}` (YA existentes en `permiso`,
--      sembrados en V5) al rol transversal `gerente`, para que giros no-anuncios
--      no queden bloqueados al generalizar la clasificacion por giro (que la
--      hace el codigo al retirar estos recursos de AnunciosVertical.recursos()).
--      NO se re-crean permisos ni se altera ningun dato del giro anuncios (los
--      roles `produccion`/`ventas` ya tenian estos permisos desde V5).
--
-- Requisitos cubiertos: 1.3, 2.1, 3.1, 5.4.
--
-- ----------------------------------------------------------------------------
-- NOMBRES REALES VERIFICADOS (leidos de las migraciones del proyecto):
--   * Tabla:  orden_fabricacion (V17); columnas: tenant_id, cotizacion_id,
--             cliente_id, estado, version, created_at/updated_at/created_by/
--             updated_by.
--   * Indice unico existente (V17): uq_orden_fabricacion_cotizacion
--             UNIQUE (tenant_id, cotizacion_id)  -> se convierte en parcial.
--   * Tabla material (V18) referenciada por la FK de partidas.
--   * Patron RLS (V2/V17): ENABLE + FORCE ROW LEVEL SECURITY +
--             CREATE POLICY tenant_isolation
--                 USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
--                 WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
--   * Tabla puente rol_permiso: columnas (rol_id, permiso_id).
--   * Catalogo permiso: clave natural (recurso, operacion) -> enlace por
--             subconsulta, sin depender del UUID del permiso.
--   * Rol destino: 'gerente' con UUID fijo a0000000-0000-0000-0000-000000000003
--             (V5), transversal a giros.
--   * UUID por defecto: gen_random_uuid() (pgcrypto habilitado en V1), coherente
--             con V17/V18.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) GENERALIZACION DE orden_fabricacion (Req 1.3)
-- ----------------------------------------------------------------------------
-- `cotizacion_id` pasa a NULLABLE: una OF de origen generico no tiene Cotizacion.
ALTER TABLE orden_fabricacion ALTER COLUMN cotizacion_id DROP NOT NULL;

-- La unicidad "una Cotizacion -> a lo sumo una OF" (Req 1.3) se vuelve PARCIAL:
-- solo aplica a filas con cotizacion_id NO nulo, permitiendo multiples OF
-- genericas (cotizacion_id NULL) dentro del mismo tenant. El nombre del indice
-- se conserva (uq_orden_fabricacion_cotizacion, V17). Antes existia como
-- restriccion UNIQUE inline; se elimina esa restriccion y se recrea como indice
-- unico parcial.
ALTER TABLE orden_fabricacion DROP CONSTRAINT IF EXISTS uq_orden_fabricacion_cotizacion;
DROP INDEX IF EXISTS uq_orden_fabricacion_cotizacion;
CREATE UNIQUE INDEX uq_orden_fabricacion_cotizacion
    ON orden_fabricacion (tenant_id, cotizacion_id)
    WHERE cotizacion_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 2) TABLA partida_orden_fabricacion (Req 5.4)
--   Partidas/BOM de la Orden de Fabricacion (Material + cantidad positiva).
--   Tenant-scoped (Req 23) con RLS (Capa 2) y version (concurrencia optimista).
--   Patron reutilizable replicado de V17/V2 para toda tabla tenant-scoped.
-- ----------------------------------------------------------------------------
CREATE TABLE partida_orden_fabricacion (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID          NOT NULL,
    orden_fabricacion_id  UUID          NOT NULL,
    material_id           UUID          NOT NULL,
    cantidad              NUMERIC(18,4) NOT NULL,
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by            VARCHAR(120),
    updated_by            VARCHAR(120),
    CONSTRAINT pk_partida_orden_fabricacion PRIMARY KEY (id),
    CONSTRAINT fk_partida_of_empresa
        FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Las partidas son hijas de la OF: se eliminan en cascada con ella.
    CONSTRAINT fk_partida_of_orden
        FOREIGN KEY (orden_fabricacion_id) REFERENCES orden_fabricacion (id) ON DELETE CASCADE,
    CONSTRAINT fk_partida_of_material
        FOREIGN KEY (material_id) REFERENCES material (id),
    -- Cantidad estrictamente positiva (Req 5.1/5.2): cantidad <= 0 se rechaza.
    CONSTRAINT ck_partida_of_cantidad CHECK (cantidad > 0)
);

-- Apoyo a la consulta de partidas por OF, siempre acotada al tenant.
CREATE INDEX ix_partida_of_orden ON partida_orden_fabricacion (tenant_id, orden_fabricacion_id);

-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V2/V17.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
ALTER TABLE partida_orden_fabricacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE partida_orden_fabricacion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON partida_orden_fabricacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- 3) SEED IDEMPOTENTE DE PERMISOS (Req 2.1, 3.1; Decision acordada 8)
--   Los permisos orden_fabricacion:{crear,leer,listar,cambiar_estado} y
--   proyecto:{crear,leer,listar,actualizar} YA existen (V5); aqui SOLO se
--   asignan al rol transversal `gerente` para no bloquear a giros no-anuncios.
--   Enlace por subconsulta sobre la clave natural (recurso) del catalogo permiso
--   y ON CONFLICT DO NOTHING para idempotencia (patron de V5/V10). No se re-crean
--   permisos ni se altera ningun dato del giro anuncios.
-- ----------------------------------------------------------------------------
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000003', p.id
FROM permiso p
WHERE p.recurso IN ('orden_fabricacion', 'proyecto')
ON CONFLICT DO NOTHING;
