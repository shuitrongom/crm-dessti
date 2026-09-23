-- ============================================================================
-- V17__operacion_orden_fabricacion.sql
--
-- Submodulo `ordenes de fabricacion` del modulo operacion-produccion
-- (Tarea 19.1, Req 7, 15.5, 12, 23). Es la PRIMERA tabla del modulo
-- operacion-produccion; establece la raiz `orden_fabricacion` replicando
-- EXACTAMENTE el patron reutilizable establecido en V11..V16 para toda tabla
-- tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V14/V16.
--
-- Requisitos cubiertos:
--   - Req 7.1 (generacion desde Cotizacion 'aprobada' con id unico): cotizacion_id
--     UUID NOT NULL con FK -> cotizacion; id UUID PK. La precondicion de estado
--     'aprobada' la aplica el dominio/aplicacion.
--   - Req 7.3 (una Cotizacion no puede tener dos Orden_Fabricacion): unicidad
--     UNIQUE (tenant_id, cotizacion_id). Es la segunda capa de defensa: la
--     aplicacion pre-verifica con existsByCotizacionId (409) y captura la
--     violacion de este indice como respaldo ante concurrencia.
--   - Req 7.4 (estado inicial 'pendiente'): estado VARCHAR(20) NOT NULL DEFAULT
--     'pendiente'.
--   - Req 7.5/7.6 (maquina de estados con finales): estado con CHECK IN
--     ('pendiente','en_produccion','terminada','cancelada'); la maquina pura vive
--     en el dominio (EstadoOrdenFabricacion), estados finales 'terminada'/'cancelada'.
--   - Req 7.9 (filtro por estado y por Cliente): cliente_id denormalizado desde la
--     Cotizacion e indices de apoyo por (tenant_id, estado) y (tenant_id, cliente_id).
--   - Req 7.10 (auditoria del cambio de estado): la registra la aplicacion.
--   - Req 15.5 (precondicion de Prueba_Diseno aprobada): la aplica el dominio/
--     aplicacion consumiendo PruebaDisenoAprobadaPort; no requiere columnas aqui.
--   - Req 23 (multi-tenant): tenant_id + RLS.
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. cliente_id DENORMALIZADO (Req 7.9): se almacena el cliente_id de la
--      Cotizacion en la Orden_Fabricacion al momento de generarla, de modo que el
--      filtro del listado por Cliente sea un simple predicado sobre la propia
--      tabla, sin JOIN a cotizacion. Es un dato derivado e inmutable de la
--      Cotizacion de origen (que a su vez no cambia de Cliente). Se declara FK a
--      cliente para integridad referencial.
--   2. UNIQUE (tenant_id, cotizacion_id): impone "una Orden_Fabricacion por
--      Cotizacion" (Req 7.3) a nivel de BD. Junto a la pre-verificacion de la
--      aplicacion conforma una doble defensa robusta ante concurrencia. Un
--      intento de generar una segunda OF viola este indice; la aplicacion lo
--      traduce a 409 (ConflictoUnicidadException).
--   3. ESTADO 'en_produccion' EN ASCII (SIN ACENTO): el requisito en prosa usa
--      "en_produccion" (con acento). Para estabilidad de codificacion en la BD
--      —coherente con la convencion de V5 para nombres de rol (diseno/produccion/
--      etc.) y con las etiquetas ASCII de V14/V16— se persiste 'en_produccion'
--      (sin acento). La capa de presentacion aplica la etiqueta visible con
--      acento si procede. El enum de dominio mapea a esta misma etiqueta ASCII.
--   4. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V16.
--   5. PERMISOS: V5 ya sembro orden_fabricacion:{crear,leer,listar,cambiar_estado}
--      y los asigno al rol `produccion` (UUID a0000000-...-000000000007, Req 27.4).
--      Por tanto V17 NO necesita sembrar permisos adicionales.
--   6. FK cotizacion_id -> cotizacion sin cascada: la Orden_Fabricacion es un
--      documento de produccion que no debe eliminarse en cascada con la Cotizacion.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- orden_fabricacion
--   Orden de fabricacion generada a partir de una Cotizacion 'aprobada' (Req 7).
--   tenant-scoped (Req 23). Estado inicial 'pendiente' (Req 7.4); estados finales
--   'terminada'/'cancelada' (Req 7.5/7.6). Vinculada 1:1 a una Cotizacion (Req 7.3).
-- ----------------------------------------------------------------------------
CREATE TABLE orden_fabricacion (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    cotizacion_id   UUID           NOT NULL,
    -- Cliente denormalizado desde la Cotizacion para el filtro del listado
    -- (Req 7.9). Inmutable; se fija al generar la OF. Ver DECISION 1.
    cliente_id      UUID           NOT NULL,
    estado          VARCHAR(20)    NOT NULL DEFAULT 'pendiente',
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_orden_fabricacion PRIMARY KEY (id),
    CONSTRAINT fk_orden_fabricacion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_orden_fabricacion_cotizacion FOREIGN KEY (cotizacion_id) REFERENCES cotizacion (id),
    CONSTRAINT fk_orden_fabricacion_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    -- Estados permitidos (Req 7.5). Etiquetas ASCII minusculas; 'en_produccion'
    -- SIN acento por estabilidad de codificacion (ver DECISION 3), coherente con
    -- V14/V16.
    CONSTRAINT ck_orden_fabricacion_estado CHECK (
        estado IN ('pendiente', 'en_produccion', 'terminada', 'cancelada')),
    -- Una Orden_Fabricacion por Cotizacion dentro del tenant (Req 7.3, DECISION 2).
    CONSTRAINT uq_orden_fabricacion_cotizacion UNIQUE (tenant_id, cotizacion_id)
);

CREATE INDEX ix_orden_fabricacion_tenant_id ON orden_fabricacion (tenant_id);

-- Apoyo a los filtros del listado (Req 7.9): por estado y por Cliente, siempre
-- acotados al tenant.
CREATE INDEX ix_orden_fabricacion_tenant_estado ON orden_fabricacion (tenant_id, estado);
CREATE INDEX ix_orden_fabricacion_tenant_cliente ON orden_fabricacion (tenant_id, cliente_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V14/V16/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE orden_fabricacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE orden_fabricacion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON orden_fabricacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
