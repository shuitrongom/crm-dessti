-- ============================================================================
-- V20__operacion_permiso_instalacion.sql
--
-- Submodulo `permisos y zonificacion` del modulo operacion-produccion
-- (Tarea 21.2, Req 17, 12, 23, 49). Establece la raiz `permiso_instalacion`
-- (autorizaciones -municipal o de arrendador- requeridas para instalar un
-- anuncio en un Sitio) con su maquina de estados
-- `solicitado -> {aprobado | rechazado}`. Replica EXACTAMENTE el patron
-- reutilizable establecido en V11..V19 para toda tabla tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V17/V18/V19.
--
-- Requisitos cubiertos:
--   - Req 17.1 (alta con datos obligatorios -tipo 'municipal' o 'arrendador' y
--     fecha de vencimiento- vinculada a un Sitio existente, estado inicial
--     'solicitado'): columnas tipo VARCHAR(20) NOT NULL con CHECK IN
--     ('municipal','arrendador'), fecha_vencimiento DATE NOT NULL, estado
--     VARCHAR(20) NOT NULL DEFAULT 'solicitado'. El id UUID PK se genera con
--     gen_random_uuid() / la fabrica de dominio.
--   - Req 17.2/17.3 (maquina de estados: solicitado -> aprobado | rechazado;
--     'aprobado' y 'rechazado' finales; transiciones invalidas rechazadas): el
--     CHECK acota los valores admitidos; la maquina de estados pura vive en el
--     dominio (EstadoPermisoInstalacion) y una transicion invalida se traduce a
--     409 (TransicionInvalidaException).
--   - Req 17.4 (guarda de programacion: instalar exige Permiso_Instalacion
--     'aprobado'): la expone el puerto PermisoAprobadoPort que el bloque 22
--     (instalacion, tarea 22.1) consumira; se apoya en una consulta por sitio_id
--     + estado. No requiere columnas adicionales aqui.
--   - Req 17.5 (notificacion de vencimiento proximo <= 30 dias de un permiso
--     'aprobado'): se apoya en una consulta por estado + fecha_vencimiento en un
--     rango relativo a la fecha actual (Clock inyectado). No requiere columnas
--     adicionales; el indice (tenant_id, estado) da soporte al filtro.
--   - Req 17.6 (listado paginado 20/100 con filtro por Sitio, tipo y estado):
--     indices de apoyo por (tenant_id, sitio_id), (tenant_id, tipo) y
--     (tenant_id, estado); la acotacion de pagina la aplica la capa web.
--   - Req 17.7 (auditoria de alta y de cambio de estado): la registra la
--     aplicacion via AuditoriaPort.
--   - Req 23 (multi-tenant): tenant_id + RLS.
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. sitio_id SIN FK (referencia debil): la entidad `Sitio` (Req 21) todavia no
--      tiene tabla; se crea en el bloque 22.2 (tarea 22.2, Proyecto/Sitio). Para
--      no introducir una dependencia de orden de migraciones ni una FK a una
--      tabla inexistente, sitio_id se modela como UUID NULL SIN restriccion de
--      clave foranea (referencia logica), replicando la DECISION 1 de V19. Cuando
--      exista la tabla `sitio`, una migracion posterior (bloque 22.2) podra anadir
--      la FK. El aislamiento por tenant lo garantizan igualmente el filtro global
--      de Hibernate y la RLS. Nota (Req 17.1): la existencia del Sitio la validara
--      la capa de aplicacion cuando el bloque 22.2 exponga el repositorio de Sitio.
--   2. tipo en ASCII minusculas: 'municipal' / 'arrendador' (CHECK), coherente con
--      las etiquetas ASCII de V14/V16/V17/V18/V19.
--   3. estado en ASCII minusculas: 'solicitado' / 'aprobado' / 'rechazado'
--      (CHECK). La maquina de estados pura vive en el dominio
--      (EstadoPermisoInstalacion); 'aprobado' y 'rechazado' son finales.
--   4. decidido_por VARCHAR(255) NULL y decidido_en TIMESTAMPTZ NULL: actor e
--      instante UTC de la decision (aprobacion/rechazo, Req 17.2); NULL mientras el
--      permiso sigue 'solicitado'. La aplicacion los fija con el Clock inyectado.
--   5. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V19.
--   6. PERMISOS: V5 YA sembro permiso_instalacion:{crear,leer,listar,
--      cambiar_estado} y los asigno al rol `instalacion`
--      (UUID a0000000-0000-0000-0000-000000000009, Req 27.6). Por tanto V20 NO
--      necesita sembrar permisos adicionales (a diferencia de V18, que agrego
--      material:eliminar). La aprobacion/rechazo reutilizan el permiso
--      permiso_instalacion:cambiar_estado (son cambios de estado del agregado).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- permiso_instalacion
--   Raiz del agregado del Permiso_Instalacion (Req 17). tenant-scoped (Req 23).
--   Estado inicial 'solicitado' (Req 17.1); estados finales 'aprobado' y
--   'rechazado' (Req 17.2). Vinculo opcional/debil a Sitio (Req 17.1, DECISION 1).
-- ----------------------------------------------------------------------------
CREATE TABLE permiso_instalacion (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL,
    -- Sitio al que se vincula el permiso (Req 17.1). Sin FK (DECISION 1): la
    -- tabla `sitio` aun no existe (bloque 22.2). Referencia logica NULLABLE.
    sitio_id            UUID,
    -- Datos obligatorios del permiso (Req 17.1).
    tipo                VARCHAR(20)    NOT NULL,
    fecha_vencimiento   DATE           NOT NULL,
    -- Estado (Req 17.1/17.2). Inicial 'solicitado'; 'aprobado'/'rechazado' finales.
    estado              VARCHAR(20)    NOT NULL DEFAULT 'solicitado',
    -- Actor e instante UTC de la decision (aprobacion/rechazo, Req 17.2); NULL
    -- mientras el permiso sigue 'solicitado' (DECISION 4).
    decidido_por        VARCHAR(255),
    decidido_en         TIMESTAMPTZ,
    -- Columnas heredadas de TenantScopedEntity: concurrencia optimista (Req 49).
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    CONSTRAINT pk_permiso_instalacion PRIMARY KEY (id),
    CONSTRAINT fk_permiso_instalacion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Tipos permitidos (Req 17.1). Etiquetas ASCII minusculas (DECISION 2).
    CONSTRAINT ck_permiso_instalacion_tipo CHECK (tipo IN ('municipal', 'arrendador')),
    -- Estados permitidos (Req 17.1, 17.2). Etiquetas ASCII minusculas (DECISION 3).
    CONSTRAINT ck_permiso_instalacion_estado
        CHECK (estado IN ('solicitado', 'aprobado', 'rechazado'))
);

CREATE INDEX ix_permiso_instalacion_tenant_id ON permiso_instalacion (tenant_id);

-- Apoyo a la guarda del Req 17.4 (buscar el Permiso 'aprobado' de un Sitio) y al
-- filtro por Sitio del listado (Req 17.6), siempre acotados al tenant.
CREATE INDEX ix_permiso_instalacion_tenant_sitio ON permiso_instalacion (tenant_id, sitio_id);

-- Apoyo a los filtros del listado por tipo y por estado (Req 17.6). El indice por
-- (tenant_id, estado) tambien da soporte a la consulta de vencimientos proximos
-- de permisos 'aprobado' (Req 17.5).
CREATE INDEX ix_permiso_instalacion_tenant_tipo ON permiso_instalacion (tenant_id, tipo);
CREATE INDEX ix_permiso_instalacion_tenant_estado ON permiso_instalacion (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V18/V19/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE permiso_instalacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE permiso_instalacion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON permiso_instalacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
