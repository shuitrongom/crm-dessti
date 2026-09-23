-- ============================================================================
-- V25__operacion_proyecto_sitio.sql
--
-- Submodulo `proyecto` del modulo operacion-produccion (Tarea 22.2, Req 21, 23,
-- 49). Establece la raiz `proyecto` (agrupador comercial-operativo asociado a un
-- Cliente) y su tabla hija `sitio` (ubicacion fisica concreta en la que se
-- ejecutan las fases de instalacion). Replica EXACTAMENTE el patron reutilizable
-- establecido en V11..V24 para toda tabla tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V17/V19/V24.
--
-- Requisitos cubiertos:
--   - Req 21.1 (crear Proyecto asociado a un Cliente existente, nombre 1..200):
--     tabla `proyecto` con cliente_id UUID NOT NULL (FK a cliente, V11), nombre
--     VARCHAR(200) NOT NULL con CHECK de longitud 1..200 tras recortar espacios;
--     id UUID PK generado con gen_random_uuid() / la fabrica de dominio.
--   - Req 21.2 (agregar un Sitio a un Proyecto existente): tabla `sitio` con
--     proyecto_id UUID NOT NULL (FK a proyecto). Ver DECISION 2 (sin cascada).
--   - Req 21.3 (avance individual de cada Sitio en las fases Levantamiento_Sitio,
--     Permiso_Instalacion, Orden_Fabricacion y Orden_Trabajo_Instalacion): el
--     avance NO se materializa como columnas de estado en `sitio`; se DERIVA en
--     tiempo de consulta a partir de los agregados de esas fases (que ya
--     referencian sitio_id). Ver DECISION 3.
--   - Req 21.4 (consultar un Proyecto -> estado consolidado derivado del avance de
--     sus Sitios): la derivacion es una funcion pura del dominio
--     (DerivacionEstadoProyecto); no requiere columnas adicionales aqui.
--   - Req 21.5 (listado paginado 20/100, filtro por Cliente): indice de apoyo
--     (tenant_id, cliente_id); la acotacion de pagina la aplica la capa web.
--   - Req 21.6 (auditoria de creacion/modificacion de Proyecto y Sitio): la
--     registra la aplicacion via AuditoriaPort (actor, accion, recurso, timestamp).
--   - Req 23 (multi-tenant): tenant_id + RLS en ambas tablas.
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. TABLA `sitio` CREADA AQUI POR PRIMERA VEZ. Los submodulos previos ya
--      referencian un Sitio mediante una columna sitio_id como REFERENCIA DEBIL
--      (UUID SIN FK): levantamiento_sitio.sitio_id (V19, NULL),
--      permiso_instalacion.sitio_id (V20) y orden_trabajo_instalacion.sitio_id
--      (V24, NOT NULL). Esa decision de referencia debil fue deliberada (V19,
--      DECISION 1) para no acoplar el orden de migraciones a una tabla `sitio`
--      inexistente. Ahora que `sitio` existe, NO se anaden FKs retroactivas a
--      aquellas tablas: es explicitamente FUERA DE ALCANCE de la tarea 22.2 (evita
--      riesgo de orden de migraciones y acoplamiento retroactivo). El aislamiento
--      entre tenants de esos modulos lo garantizan igualmente el filtro global por
--      tenant_id y la RLS; la integridad logica sitio_id -> sitio queda como
--      referencia debil documentada, susceptible de reforzarse en una migracion
--      posterior si el negocio lo requiere.
--   2. sitio.proyecto_id CON FK PERO SIN CASCADA (ON DELETE por defecto,
--      RESTRICT/NO ACTION): un Sitio es un REGISTRO OPERATIVO (acumula fases de
--      instalacion) y no debe borrarse en cascada al eliminar su Proyecto; borrar
--      un Proyecto con Sitios debe bloquearse a nivel de BD, obligando a una baja
--      controlada. Coherente con la ausencia de cascada en los vinculos operativos
--      de V19 (Levantamiento) y V17 (Orden_Fabricacion).
--   3. AVANCE POR FASE NO MATERIALIZADO EN `sitio` (Req 21.3, 21.4). El avance de
--      cada Sitio en las 4 fases vive en OTROS modulos (levantamiento_sitio,
--      permiso_instalacion, orden_trabajo_instalacion). Para respetar los limites
--      hexagonales, el submodulo proyecto NO duplica ese estado: lo consulta en
--      tiempo de lectura a traves de puertos de solo-lectura (AvanceSitioPort, que
--      delega en LevantamientoCompletadoPort, PermisoAprobadoPort e
--      InstalacionCompletadaPort). El estado consolidado del Proyecto es una
--      funcion PURA de esos avances (DerivacionEstadoProyecto en el dominio). Por
--      ello estas tablas no llevan columnas de estado de fase.
--   4. direccion del Sitio OPCIONAL: VARCHAR(500) NULL (direccion fisica libre).
--   5. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V24.
--   6. PERMISOS: V5 YA sembro proyecto:{crear,leer,listar,actualizar} y los asigno
--      a los roles ventas/supervisor/gerente. Por tanto V25 NO siembra permisos.
--      NO existe recurso `sitio` en V5: las operaciones sobre Sitio viajan sobre
--      los permisos de `proyecto` (agregar Sitio = proyecto:actualizar; consultar
--      Proyecto con sus Sitios = proyecto:leer), pues el Sitio se gestiona como
--      parte del agregado Proyecto.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- proyecto
--   Raiz del agregado Proyecto (Req 21). tenant-scoped (Req 23). Asociado a un
--   Cliente existente (Req 21.1); agrupa uno o varios Sitios (Req 21.2).
-- ----------------------------------------------------------------------------
CREATE TABLE proyecto (
    id          UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID           NOT NULL,
    -- Cliente al que pertenece el Proyecto (Req 21.1). FK a cliente (V11); sin
    -- cascada: un Proyecto es un registro operativo que no se borra en cascada.
    cliente_id  UUID           NOT NULL,
    -- Nombre del Proyecto (Req 21.1), 1..200 tras recortar espacios.
    nombre      VARCHAR(200)   NOT NULL,
    -- Columnas heredadas de TenantScopedEntity: concurrencia optimista (Req 49) y
    -- auditoria (Req 21.6).
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_proyecto PRIMARY KEY (id),
    CONSTRAINT fk_proyecto_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Cliente existente (Req 21.1). Sin cascada (registro operativo).
    CONSTRAINT fk_proyecto_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    -- Nombre no vacio y acotado a 200 tras recortar espacios (Req 21.1).
    CONSTRAINT ck_proyecto_nombre_longitud
        CHECK (length(btrim(nombre)) BETWEEN 1 AND 200)
);

CREATE INDEX ix_proyecto_tenant_id ON proyecto (tenant_id);

-- Apoyo al listado paginado con filtro por Cliente (Req 21.5), acotado al tenant.
CREATE INDEX ix_proyecto_tenant_cliente ON proyecto (tenant_id, cliente_id);

-- ----------------------------------------------------------------------------
-- sitio
--   Ubicacion fisica concreta de un Proyecto (Req 21.2). tenant-scoped (Req 23).
--   Acumula el avance de las 4 fases de instalacion, que NO se materializa aqui
--   sino que se DERIVA de otros modulos (DECISION 3). Registro operativo: la FK a
--   proyecto NO tiene cascada (DECISION 2). Es la tabla que las columnas sitio_id
--   de V19/V20/V24 referencian logicamente como referencia debil (DECISION 1); no
--   se anaden FKs retroactivas a aquellas tablas (fuera de alcance de 22.2).
-- ----------------------------------------------------------------------------
CREATE TABLE sitio (
    id          UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID           NOT NULL,
    -- Proyecto al que pertenece el Sitio (Req 21.2). FK a proyecto; sin cascada
    -- (DECISION 2): el Sitio es un registro operativo.
    proyecto_id UUID           NOT NULL,
    -- Nombre del Sitio (Req 21.2), 1..200 tras recortar espacios.
    nombre      VARCHAR(200)   NOT NULL,
    -- Direccion fisica del Sitio; opcional (DECISION 4).
    direccion   VARCHAR(500),
    -- Columnas heredadas de TenantScopedEntity (Req 49, 21.6).
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_sitio PRIMARY KEY (id),
    CONSTRAINT fk_sitio_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Proyecto existente (Req 21.2). Sin cascada (DECISION 2): borrar un Proyecto
    -- con Sitios queda bloqueado a nivel de BD.
    CONSTRAINT fk_sitio_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto (id),
    -- Nombre no vacio y acotado a 200 tras recortar espacios (Req 21.2).
    CONSTRAINT ck_sitio_nombre_longitud
        CHECK (length(btrim(nombre)) BETWEEN 1 AND 200)
);

CREATE INDEX ix_sitio_tenant_id ON sitio (tenant_id);

-- Apoyo a la carga de los Sitios de un Proyecto (Req 21.2, 21.4), acotado al tenant.
CREATE INDEX ix_sitio_tenant_proyecto ON sitio (tenant_id, proyecto_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V19/V24/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE proyecto ENABLE ROW LEVEL SECURITY;
ALTER TABLE proyecto FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON proyecto
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE sitio ENABLE ROW LEVEL SECURITY;
ALTER TABLE sitio FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON sitio
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);