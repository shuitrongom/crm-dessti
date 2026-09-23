-- ============================================================================
-- V19__operacion_levantamiento_sitio.sql
--
-- Submodulo `levantamiento en sitio` del modulo operacion-produccion
-- (Tarea 21.1, Req 16, 12, 23, 49). Establece la raiz `levantamiento_sitio`
-- (condiciones fisicas/electricas de un Sitio antes de fabricar/instalar) y su
-- tabla hija `levantamiento_foto` (fotografias adjuntas, Req 16.3). Replica
-- EXACTAMENTE el patron reutilizable establecido en V11..V18 para toda tabla
-- tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V17/V18.
--
-- Requisitos cubiertos:
--   - Req 16.1 (alta con datos obligatorios -mediciones, tipo de superficie o
--     estructura y condiciones electricas- y estado inicial 'en_proceso'):
--     columnas mediciones TEXT NOT NULL, tipo_superficie VARCHAR(200) NOT NULL,
--     condiciones_electricas TEXT NOT NULL (con CHECK de longitud >= 1 tras
--     recortar espacios); estado VARCHAR(20) NOT NULL DEFAULT 'en_proceso'. El
--     id UUID PK se genera con gen_random_uuid() / la fabrica de dominio.
--   - Req 16.2 (vinculo OPCIONAL a Sitio, Cotizacion u Orden_Fabricacion
--     existentes): sitio_id, cotizacion_id, orden_fabricacion_id UUID NULL. Ver
--     DECISION 1 sobre sitio_id (aun no existe tabla `sitio`, bloque 22.2). Las
--     FK a cotizacion (V14) y orden_fabricacion (V17) son NULL y sin cascada.
--   - Req 16.3 (adjuntar fotografias conservadas vinculadas al Levantamiento):
--     tabla hija `levantamiento_foto` (id, tenant_id, levantamiento_id FK,
--     referencia TEXT, audit) con RLS. Ver DECISION 2.
--   - Req 16.4 (marcar 'completado' con actor y marca temporal UTC): columnas
--     completado_por VARCHAR(255) NULL y completado_en TIMESTAMPTZ NULL, que la
--     aplicacion fija al completar usando el Clock inyectado.
--   - Req 16.5 (guarda de programacion: la instalacion exige un Levantamiento
--     'completado'): la expone el puerto LevantamientoCompletadoPort que el
--     bloque 22 (instalacion) consumira; se apoya en una consulta por sitio_id +
--     estado. No requiere columnas adicionales aqui.
--   - Req 16.6 (listado paginado 20/100): indices de apoyo por (tenant_id, ...);
--     la acotacion de pagina la aplica la capa web.
--   - Req 16.7 (auditoria de alta y de cambio de estado): la registra la
--     aplicacion via AuditoriaPort.
--   - Req 23 (multi-tenant): tenant_id + RLS en ambas tablas.
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. sitio_id SIN FK (referencia debil): la entidad `Sitio` (Req 21) todavia no
--      tiene tabla; se crea en el bloque 22.2 (tarea 22.2, Proyecto/Sitio). Para
--      no introducir una dependencia de orden de migraciones ni una FK a una
--      tabla inexistente, sitio_id se modela como UUID NULL SIN restriccion de
--      clave foranea (referencia logica). Cuando exista la tabla `sitio`, una
--      migracion posterior podra anadir la FK. El aislamiento por tenant lo
--      garantizan igualmente el filtro global y la RLS.
--   2. FOTOGRAFIAS EN TABLA HIJA (Req 16.3): se prefiere una tabla hija
--      `levantamiento_foto` (una fila por foto) frente a una lista embebida
--      (jsonb/text), por limpieza relacional, para poder auditar/consultar cada
--      referencia por separado y para heredar tenant_id + RLS de forma uniforme.
--      Guarda una `referencia` (URL o clave de objeto en el almacen), no el
--      binario. FK levantamiento_id -> levantamiento_sitio ON DELETE CASCADE: las
--      fotos carecen de sentido sin su Levantamiento (relacion de composicion).
--   3. estado en ASCII minusculas: 'en_proceso' / 'completado' (CHECK), coherente
--      con las etiquetas ASCII de V14/V16/V17. La maquina de estados pura vive en
--      el dominio (EstadoLevantamiento); 'completado' es final.
--   4. mediciones y condiciones_electricas como TEXT (descripcion libre, sin
--      limite estricto); tipo_superficie como VARCHAR(200) (etiqueta acotada).
--   5. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V18.
--   6. PERMISOS: V5 YA sembro levantamiento_sitio:{crear,leer,listar,
--      cambiar_estado} y los asigno al rol `instalacion`
--      (UUID a0000000-...-000000000009, Req 27.6). Por tanto V19 NO necesita
--      sembrar permisos adicionales (a diferencia de V18, que agrego material:
--      eliminar). La operacion 'completar' reutiliza el permiso
--      levantamiento_sitio:cambiar_estado (es un cambio de estado del agregado).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- levantamiento_sitio
--   Raiz del agregado del Levantamiento_Sitio (Req 16). tenant-scoped (Req 23).
--   Estado inicial 'en_proceso' (Req 16.1); estado final 'completado' (Req 16.4).
--   Vinculos opcionales a Sitio/Cotizacion/Orden_Fabricacion (Req 16.2).
-- ----------------------------------------------------------------------------
CREATE TABLE levantamiento_sitio (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID           NOT NULL,
    -- Vinculos OPCIONALES (Req 16.2). sitio_id sin FK (DECISION 1); cotizacion_id
    -- y orden_fabricacion_id con FK NULL a V14/V17.
    sitio_id                UUID,
    cotizacion_id           UUID,
    orden_fabricacion_id    UUID,
    -- Datos obligatorios del levantamiento (Req 16.1).
    mediciones              TEXT           NOT NULL,
    tipo_superficie         VARCHAR(200)   NOT NULL,
    condiciones_electricas  TEXT           NOT NULL,
    -- Estado (Req 16.1/16.4). Inicial 'en_proceso'; 'completado' es final.
    estado                  VARCHAR(20)    NOT NULL DEFAULT 'en_proceso',
    -- Actor y marca temporal UTC de la finalizacion (Req 16.4); NULL mientras
    -- el levantamiento sigue 'en_proceso'.
    completado_por          VARCHAR(255),
    completado_en           TIMESTAMPTZ,
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version                 BIGINT         NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    CONSTRAINT pk_levantamiento_sitio PRIMARY KEY (id),
    CONSTRAINT fk_levantamiento_sitio_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Vinculos opcionales con integridad referencial cuando se proporcionan
    -- (Req 16.2). Sin cascada: el Levantamiento es un registro operativo que no
    -- debe borrarse en cascada con la Cotizacion ni con la Orden_Fabricacion.
    CONSTRAINT fk_levantamiento_sitio_cotizacion FOREIGN KEY (cotizacion_id)
        REFERENCES cotizacion (id),
    CONSTRAINT fk_levantamiento_sitio_of FOREIGN KEY (orden_fabricacion_id)
        REFERENCES orden_fabricacion (id),
    -- Datos obligatorios no vacios tras recortar espacios (Req 16.1).
    CONSTRAINT ck_levantamiento_sitio_mediciones_no_vacio
        CHECK (length(btrim(mediciones)) >= 1),
    CONSTRAINT ck_levantamiento_sitio_superficie_no_vacio
        CHECK (length(btrim(tipo_superficie)) >= 1),
    CONSTRAINT ck_levantamiento_sitio_electricas_no_vacio
        CHECK (length(btrim(condiciones_electricas)) >= 1),
    -- Estados permitidos (Req 16.1, 16.4). Etiquetas ASCII minusculas (DECISION 3).
    CONSTRAINT ck_levantamiento_sitio_estado CHECK (estado IN ('en_proceso', 'completado'))
);

CREATE INDEX ix_levantamiento_sitio_tenant_id ON levantamiento_sitio (tenant_id);

-- Apoyo a la guarda del Req 16.5 (buscar el Levantamiento 'completado' de un
-- Sitio) y al listado por estado/Sitio (Req 16.6), siempre acotados al tenant.
CREATE INDEX ix_levantamiento_sitio_tenant_sitio ON levantamiento_sitio (tenant_id, sitio_id);
CREATE INDEX ix_levantamiento_sitio_tenant_estado ON levantamiento_sitio (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- levantamiento_foto
--   Fotografia adjunta a un Levantamiento_Sitio (Req 16.3). tenant-scoped
--   (Req 23). Guarda una referencia (URL/clave de objeto), no el binario
--   (DECISION 2). Relacion de composicion con su Levantamiento (ON DELETE CASCADE).
-- ----------------------------------------------------------------------------
CREATE TABLE levantamiento_foto (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL,
    levantamiento_id    UUID           NOT NULL,
    -- Referencia a la fotografia (URL o clave del objeto en el almacen).
    referencia          TEXT           NOT NULL,
    -- Columna heredada de TenantScopedEntity (Req 49).
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    CONSTRAINT pk_levantamiento_foto PRIMARY KEY (id),
    CONSTRAINT fk_levantamiento_foto_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_levantamiento_foto_levantamiento FOREIGN KEY (levantamiento_id)
        REFERENCES levantamiento_sitio (id) ON DELETE CASCADE,
    -- Referencia no vacia tras recortar espacios (Req 16.3).
    CONSTRAINT ck_levantamiento_foto_referencia_no_vacio CHECK (length(btrim(referencia)) >= 1)
);

CREATE INDEX ix_levantamiento_foto_tenant_id ON levantamiento_foto (tenant_id);

-- Apoyo a la consulta de las fotos de un Levantamiento (Req 16.3), acotado al tenant.
CREATE INDEX ix_levantamiento_foto_tenant_levantamiento
    ON levantamiento_foto (tenant_id, levantamiento_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V18/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE levantamiento_sitio ENABLE ROW LEVEL SECURITY;
ALTER TABLE levantamiento_sitio FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON levantamiento_sitio
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE levantamiento_foto ENABLE ROW LEVEL SECURITY;
ALTER TABLE levantamiento_foto FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON levantamiento_foto
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
