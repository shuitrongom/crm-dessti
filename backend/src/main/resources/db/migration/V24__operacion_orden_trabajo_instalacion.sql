-- ============================================================================
-- V24__operacion_orden_trabajo_instalacion.sql
--
-- Submodulo `instalacion` del modulo operacion-produccion (Tarea 22.1, Req 19,
-- 12, 23, 49). Establece la raiz `orden_trabajo_instalacion` (Orden de Trabajo de
-- Instalacion, OTI) y sus dos tablas hijas: `pendiente_instalacion`
-- (Lista_Pendientes, Req 19.4/19.6) y `evidencia_instalacion` (evidencia
-- fotografica, Req 19.4). Replica EXACTAMENTE el patron reutilizable establecido
-- en V11..V23 para toda tabla tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V17/V19.
--
-- Requisitos cubiertos:
--   - Req 19.1 (crear OTI a partir de una Orden_Fabricacion 'terminada', asignando
--     Cuadrilla y fecha programada; id unico; estado inicial 'programada'):
--     orden_fabricacion_id UUID NOT NULL con FK -> orden_fabricacion; cuadrilla_id
--     UUID NOT NULL (referencia debil, ver DECISION 2); fecha_programada DATE NOT
--     NULL; estado VARCHAR(20) NOT NULL DEFAULT 'programada'; id UUID PK.
--   - Req 19.2 (rechazar creacion si la OF no esta 'terminada'): la guarda la
--     aplica la aplicacion via OrdenFabricacionTerminadaPort; no requiere columnas.
--   - Req 19.3 (rechazar programacion si el Sitio no tiene Levantamiento_Sitio
--     'completado' o Permiso_Instalacion requerido 'aprobado'): la guarda la aplica
--     la aplicacion via LevantamientoCompletadoPort y PermisoAprobadoPort. sitio_id
--     UUID NOT NULL (referencia debil, ver DECISION 1).
--   - Req 19.4 (registrar avance: evidencia fotografica + Lista_Pendientes
--     asociadas a la OTI): tablas hijas pendiente_instalacion y
--     evidencia_instalacion (ver DECISION 3).
--   - Req 19.5 (maquina de estados con finales 'completada'/'cancelada'): estado
--     con CHECK IN ('programada','en_curso','completada','cancelada'); la maquina
--     pura vive en el dominio (EstadoOrdenTrabajoInstalacion).
--   - Req 19.6 (no completar mientras existan pendientes sin resolver): columna
--     resuelto BOOLEAN en pendiente_instalacion; un pendiente "sin resolver" es
--     resuelto = false. La guarda la aplica la aplicacion.
--   - Req 19.7 (listado paginado 20/100 con filtros por estado, Cuadrilla y
--     Cliente): cliente_id denormalizado desde la OF e indices de apoyo por
--     (tenant_id, estado), (tenant_id, cuadrilla_id) y (tenant_id, cliente_id).
--   - Req 19.8 (auditoria de creacion y de cambio de estado): la registra la
--     aplicacion via AuditoriaPort.
--   - Req 23 (multi-tenant): tenant_id + RLS en las tres tablas.
--   - Req 49 (concurrencia optimista): columna version en las tres tablas.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. sitio_id NOT NULL SIN FK (referencia debil): una OTI siempre se realiza en
--      un Sitio (Req 19.3), por lo que sitio_id es NOT NULL. Sin embargo, la tabla
--      `sitio` (Req 21) todavia no existe: se crea en la tarea 22.2. Para no
--      introducir una dependencia de orden de migraciones ni una FK a una tabla
--      inexistente, sitio_id se modela SIN restriccion de clave foranea (referencia
--      logica), exactamente como levantamiento_sitio.sitio_id y
--      permiso_instalacion.sitio_id. Cuando exista la tabla `sitio`, una migracion
--      posterior (22.2) podra anadir la FK. El aislamiento por tenant lo garantizan
--      igualmente el filtro global y la RLS.
--   2. cuadrilla_id NOT NULL SIN FK (referencia debil): la OTI siempre asigna una
--      Cuadrilla (Req 19.1), pero la tabla `cuadrilla` aun no existe. Por el mismo
--      motivo que sitio_id, cuadrilla_id es NOT NULL sin FK. Una migracion posterior
--      podra anadir la FK cuando exista `cuadrilla`.
--   3. LISTA_PENDIENTES Y EVIDENCIA EN TABLAS HIJAS (Req 19.4): se prefieren tablas
--      hijas (una fila por elemento) frente a listas embebidas, por limpieza
--      relacional, para poder auditar/consultar cada elemento por separado y para
--      heredar tenant_id + RLS de forma uniforme, replicando el patron de
--      levantamiento_foto (V19). Ambas FK -> orden_trabajo_instalacion ON DELETE
--      CASCADE: pendientes y evidencias carecen de sentido sin su OTI (composicion).
--      evidencia_instalacion guarda una `url` (URL o clave de objeto en el almacen),
--      no el binario.
--   4. cliente_id DENORMALIZADO (Req 19.7): se almacena el cliente_id de la
--      Orden_Fabricacion en la OTI al programarla, de modo que el filtro del listado
--      por Cliente sea un simple predicado sobre la propia tabla, sin JOIN. Es un
--      dato derivado e inmutable de la OF de origen. Se declara FK a cliente (V11)
--      para integridad referencial, coherente con orden_fabricacion.cliente_id.
--   5. estado en ASCII minusculas: 'programada'/'en_curso'/'completada'/'cancelada'
--      (CHECK), coherente con las etiquetas ASCII de V14/V16/V17/V19. La maquina de
--      estados pura vive en el dominio (EstadoOrdenTrabajoInstalacion); finales
--      'completada'/'cancelada'.
--   6. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente con
--      V11..V23.
--   7. PERMISOS: V5 YA sembro orden_trabajo_instalacion:{crear,leer,listar,
--      cambiar_estado} y los asigno a los roles `instalacion`/`supervisor`. Por
--      tanto V24 NO necesita sembrar permisos adicionales. El registro de avance
--      (Req 19.4) reutiliza el permiso orden_trabajo_instalacion:cambiar_estado
--      (muta el agregado OTI e incide en la guarda de cierre del Req 19.6); no
--      existe una operacion 'actualizar' para este recurso en V5.
--   8. FK orden_fabricacion_id -> orden_fabricacion sin cascada: la OTI es un
--      documento de instalacion que no debe eliminarse en cascada con la OF.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- orden_trabajo_instalacion
--   Raiz del agregado de la Orden de Trabajo de Instalacion (Req 19).
--   tenant-scoped (Req 23). Estado inicial 'programada' (Req 19.1); estados finales
--   'completada'/'cancelada' (Req 19.5). Vinculada a una Orden_Fabricacion
--   terminada, a un Sitio y a una Cuadrilla (Req 19.1, 19.3).
-- ----------------------------------------------------------------------------
CREATE TABLE orden_trabajo_instalacion (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID           NOT NULL,
    orden_fabricacion_id    UUID           NOT NULL,
    -- Sitio de la instalacion (Req 19.3). NOT NULL sin FK: la tabla `sitio` aun no
    -- existe (tarea 22.2). Referencia debil (ver DECISION 1).
    sitio_id                UUID           NOT NULL,
    -- Cuadrilla asignada (Req 19.1, 19.7). NOT NULL sin FK: la tabla `cuadrilla`
    -- aun no existe. Referencia debil (ver DECISION 2).
    cuadrilla_id            UUID           NOT NULL,
    -- Cliente denormalizado desde la Orden_Fabricacion para el filtro del listado
    -- (Req 19.7). Inmutable; se fija al programar la OTI. Ver DECISION 4.
    cliente_id              UUID           NOT NULL,
    fecha_programada        DATE           NOT NULL,
    estado                  VARCHAR(20)    NOT NULL DEFAULT 'programada',
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version                 BIGINT         NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    CONSTRAINT pk_orden_trabajo_instalacion PRIMARY KEY (id),
    CONSTRAINT fk_oti_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_oti_orden_fabricacion FOREIGN KEY (orden_fabricacion_id)
        REFERENCES orden_fabricacion (id),
    CONSTRAINT fk_oti_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    -- Estados permitidos (Req 19.5). Etiquetas ASCII minusculas (DECISION 5).
    CONSTRAINT ck_oti_estado CHECK (
        estado IN ('programada', 'en_curso', 'completada', 'cancelada'))
);

CREATE INDEX ix_oti_tenant_id ON orden_trabajo_instalacion (tenant_id);

-- Apoyo a los filtros del listado (Req 19.7): por estado, por Cuadrilla y por
-- Cliente, siempre acotados al tenant.
CREATE INDEX ix_oti_tenant_estado ON orden_trabajo_instalacion (tenant_id, estado);
CREATE INDEX ix_oti_tenant_cuadrilla ON orden_trabajo_instalacion (tenant_id, cuadrilla_id);
CREATE INDEX ix_oti_tenant_cliente ON orden_trabajo_instalacion (tenant_id, cliente_id);

-- ----------------------------------------------------------------------------
-- pendiente_instalacion
--   Entrada de la Lista_Pendientes de una OTI (Req 19.4, 19.6). tenant-scoped
--   (Req 23). Un pendiente "sin resolver" es resuelto = false; la guarda de cierre
--   del Req 19.6 impide completar la OTI mientras exista alguno sin resolver.
--   Relacion de composicion con su OTI (ON DELETE CASCADE, DECISION 3).
-- ----------------------------------------------------------------------------
CREATE TABLE pendiente_instalacion (
    id                              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                       UUID           NOT NULL,
    orden_trabajo_instalacion_id    UUID           NOT NULL,
    descripcion                     VARCHAR(500)   NOT NULL,
    resuelto                        BOOLEAN        NOT NULL DEFAULT false,
    -- Columna heredada de TenantScopedEntity (Req 49).
    version                         BIGINT         NOT NULL DEFAULT 0,
    created_at                      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by                      VARCHAR(255),
    updated_by                      VARCHAR(255),
    CONSTRAINT pk_pendiente_instalacion PRIMARY KEY (id),
    CONSTRAINT fk_pendiente_instalacion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_pendiente_instalacion_oti FOREIGN KEY (orden_trabajo_instalacion_id)
        REFERENCES orden_trabajo_instalacion (id) ON DELETE CASCADE,
    -- Descripcion no vacia tras recortar espacios (Req 19.4).
    CONSTRAINT ck_pendiente_instalacion_descripcion_no_vacio
        CHECK (length(btrim(descripcion)) >= 1)
);

CREATE INDEX ix_pendiente_instalacion_tenant_id ON pendiente_instalacion (tenant_id);

-- Apoyo a la consulta de los pendientes de una OTI y a la guarda de cierre del
-- Req 19.6 (existencia de pendientes sin resolver), acotado al tenant.
CREATE INDEX ix_pendiente_instalacion_tenant_oti
    ON pendiente_instalacion (tenant_id, orden_trabajo_instalacion_id);

-- ----------------------------------------------------------------------------
-- evidencia_instalacion
--   Evidencia fotografica adjunta a una OTI (Req 19.4). tenant-scoped (Req 23).
--   Guarda una referencia (URL/clave de objeto), no el binario (DECISION 3).
--   Relacion de composicion con su OTI (ON DELETE CASCADE).
-- ----------------------------------------------------------------------------
CREATE TABLE evidencia_instalacion (
    id                              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                       UUID           NOT NULL,
    orden_trabajo_instalacion_id    UUID           NOT NULL,
    -- Referencia a la fotografia (URL o clave del objeto en el almacen).
    url                             VARCHAR(1000)  NOT NULL,
    -- Columna heredada de TenantScopedEntity (Req 49).
    version                         BIGINT         NOT NULL DEFAULT 0,
    created_at                      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by                      VARCHAR(255),
    updated_by                      VARCHAR(255),
    CONSTRAINT pk_evidencia_instalacion PRIMARY KEY (id),
    CONSTRAINT fk_evidencia_instalacion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_evidencia_instalacion_oti FOREIGN KEY (orden_trabajo_instalacion_id)
        REFERENCES orden_trabajo_instalacion (id) ON DELETE CASCADE,
    -- Referencia no vacia tras recortar espacios (Req 19.4).
    CONSTRAINT ck_evidencia_instalacion_url_no_vacio CHECK (length(btrim(url)) >= 1)
);

CREATE INDEX ix_evidencia_instalacion_tenant_id ON evidencia_instalacion (tenant_id);

-- Apoyo a la consulta de las evidencias de una OTI (Req 19.4), acotado al tenant.
CREATE INDEX ix_evidencia_instalacion_tenant_oti
    ON evidencia_instalacion (tenant_id, orden_trabajo_instalacion_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V19/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE orden_trabajo_instalacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE orden_trabajo_instalacion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON orden_trabajo_instalacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE pendiente_instalacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE pendiente_instalacion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON pendiente_instalacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE evidencia_instalacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE evidencia_instalacion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON evidencia_instalacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
