-- ============================================================================
-- V44__reportesbi_tablero_personalizado.sql
--
-- Modulo `reportes-bi` (Tarea 44, Req 22, 48, 12, 23, 49). Persiste los tableros
-- analiticos PERSONALIZADOS que un Usuario define y guarda dentro de su Empresa,
-- combinando metricas de distintas areas (Req 48.3). El resto del modulo (Tablero
-- de indicadores por area del Req 22 y analisis consolidado del Req 48.1) es de
-- SOLO LECTURA y se compone en memoria a partir de puertos de indicadores
-- desacoplados; por eso esta migracion solo crea las tablas del Req 48.3.
--
-- Replica EXACTAMENTE el patron reutilizable de V11..V42 para toda tabla
-- tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V16/V18.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. SOLO SE PERSISTE LO PERSONALIZADO (Req 48.3): los indicadores del Tablero
--      (Req 22) y del consolidado (Req 48.1) son agregaciones de solo lectura
--      sobre los datos de otros modulos (Req 22.2, 48.2); NO se copian ni se
--      materializan aqui. Este modulo AGREGA via puertos de indicadores
--      (com.dessti.crm.reportesbi.application.indicadores) con adaptadores por
--      defecto en cero; los adaptadores concretos de cada area leen sus propios
--      repositorios. Por tanto la unica informacion propia y mutable del modulo
--      es la definicion de los tableros personalizados y sus widgets.
--   2. tablero_personalizado + widget_tablero (1..N): un tablero personalizado
--      agrupa varios widgets; cada widget referencia una metrica de un area. La
--      relacion se modela con FK widget -> tablero ON DELETE CASCADE, de modo que
--      al eliminar un tablero se eliminan sus widgets (composicion). Es la unica
--      cascada del modulo y es segura porque el widget no tiene vida propia fuera
--      de su tablero.
--   3. AISLAMIENTO POR TENANT (Req 48.5): ambas tablas son tenant-scoped con RLS
--      ENABLE+FORCE y politica tenant_isolation; un tablero personalizado solo es
--      visible/gestionable dentro de la Empresa que lo creo.
--   4. propietario_usuario_id NULLABLE: se guarda de forma orientativa el Usuario
--      que creo el tablero; el control de acceso efectivo es por permiso RBAC
--      (inteligencia_negocio) + aislamiento por tenant, no por propietario, para
--      no bloquear la colaboracion dentro de la Empresa. No hay FK a usuario para
--      no acoplar el modulo al esquema de identidad; el created_by textual ya deja
--      rastro del actor.
--   5. configuracion_json TEXT NULL: parametros de presentacion del widget
--      (tipo de grafico, orden de series, formato) como JSON opaco para el
--      backend; el modulo no lo interpreta (solo lectura de indicadores). Se usa
--      TEXT (no jsonb) por coherencia con el resto del esquema y porque no se
--      consulta por contenido.
--   6. area VARCHAR(40) / metrica VARCHAR(80): etiquetas ASCII estables que
--      corresponden a AreaIndicador.etiqueta() y a la clave de ValorIndicador.
--      No se imponen como FK/catalogo en BD para permitir que nuevas areas y
--      metricas se agreguen en codigo sin migracion; la validacion de longitud
--      minima la aplican los CHECK y el dominio.
--   7. PERMISO ANALITICO (Req 48.6): V5 sembro tablero:{leer} y
--      reporte:{leer,exportar} (transversales de lectura para gerente/
--      admin_empresa/supervisor). NO existe un recurso para la Inteligencia de
--      Negocio (analisis consolidado + tableros personalizados). Se crea el
--      recurso `inteligencia_negocio` con las operaciones {leer, gestionar,
--      exportar} y se asigna a los roles gerente (...003) y admin_empresa (...002),
--      coherente con el patron de V18. El Tablero del Req 22 reutiliza el permiso
--      existente tablero:leer (y reporte:exportar para su exportacion); ver el
--      controlador ReportesBiController.
--   8. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V42.
--
-- Requisitos cubiertos:
--   - Req 48.3 (definir y guardar tableros analiticos personalizados combinando
--     metricas de distintas areas dentro de su Empresa): tablas
--     tablero_personalizado y widget_tablero, tenant-scoped.
--   - Req 48.5 (aislamiento por tenant): tenant_id + RLS en ambas tablas.
--   - Req 48.6 / 22.5 (403 sin permiso): recurso inteligencia_negocio sembrado y
--     asignado; el Tablero reutiliza tablero:leer.
--   - Req 49 (concurrencia optimista): columna version.
--   - Req 22.2 / 48.2 (solo lectura de indicadores): NO se materializan datos de
--     otras areas (DECISION 1).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- tablero_personalizado
--   Tablero analitico definido y guardado por un Usuario dentro de su Empresa
--   (Req 48.3). tenant-scoped (Req 23). Agrupa 1..N widget_tablero.
-- ----------------------------------------------------------------------------
CREATE TABLE tablero_personalizado (
    id                     UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id              UUID          NOT NULL,
    nombre                 VARCHAR(200)  NOT NULL,
    descripcion            VARCHAR(500),
    -- Usuario que creo el tablero, orientativo (DECISION 4); sin FK a usuario.
    propietario_usuario_id UUID,
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by             VARCHAR(255),
    updated_by             VARCHAR(255),
    CONSTRAINT pk_tablero_personalizado PRIMARY KEY (id),
    CONSTRAINT fk_tablero_personalizado_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Nombre entre 1 y 200 caracteres; VARCHAR(200) acota el maximo y este CHECK
    -- impone el minimo tras recortar espacios.
    CONSTRAINT ck_tablero_personalizado_nombre_no_vacio CHECK (length(btrim(nombre)) >= 1)
);

CREATE INDEX ix_tablero_personalizado_tenant_id ON tablero_personalizado (tenant_id);

-- Apoyo al listado/busqueda por nombre dentro del tenant (Req 48.3), acotado al tenant.
CREATE INDEX ix_tablero_personalizado_tenant_nombre
    ON tablero_personalizado (tenant_id, nombre);

-- ----------------------------------------------------------------------------
-- widget_tablero
--   Widget de un tablero personalizado: una metrica de un area (Req 48.3).
--   tenant-scoped (Req 23). Se elimina en cascada con su tablero (DECISION 2).
-- ----------------------------------------------------------------------------
CREATE TABLE widget_tablero (
    id                       UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                UUID          NOT NULL,
    tablero_personalizado_id UUID          NOT NULL,
    -- Etiqueta ASCII del area (AreaIndicador.etiqueta()); 1..40 (DECISION 6).
    area                     VARCHAR(40)   NOT NULL,
    -- Clave ASCII de la metrica (ValorIndicador.clave()); 1..80 (DECISION 6).
    metrica                  VARCHAR(80)   NOT NULL,
    -- Orden de presentacion del widget dentro del tablero.
    orden                    INTEGER       NOT NULL DEFAULT 0,
    -- Parametros de presentacion como JSON opaco (DECISION 5); opcional.
    configuracion_json       TEXT,
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version                  BIGINT        NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    CONSTRAINT pk_widget_tablero PRIMARY KEY (id),
    CONSTRAINT fk_widget_tablero_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_widget_tablero_tablero FOREIGN KEY (tablero_personalizado_id)
        REFERENCES tablero_personalizado (id) ON DELETE CASCADE,
    CONSTRAINT ck_widget_tablero_area_no_vacio CHECK (length(btrim(area)) >= 1),
    CONSTRAINT ck_widget_tablero_metrica_no_vacio CHECK (length(btrim(metrica)) >= 1),
    CONSTRAINT ck_widget_tablero_orden_no_negativo CHECK (orden >= 0)
);

CREATE INDEX ix_widget_tablero_tenant_id ON widget_tablero (tenant_id);

-- Apoyo a la carga de los widgets de un tablero (Req 48.3), acotado al tenant.
CREATE INDEX ix_widget_tablero_tenant_tablero
    ON widget_tablero (tenant_id, tablero_personalizado_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23, 48.5) -- patron replicado de V18/V16/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE tablero_personalizado ENABLE ROW LEVEL SECURITY;
ALTER TABLE tablero_personalizado FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tablero_personalizado
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE widget_tablero ENABLE ROW LEVEL SECURITY;
ALTER TABLE widget_tablero FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON widget_tablero
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Permiso analitico de Inteligencia de Negocio (Req 48.6). V5 no sembro ningun
-- recurso para el analisis consolidado ni para los tableros personalizados; solo
-- existen tablero:{leer} y reporte:{leer,exportar} (transversales de lectura).
-- Se crea el recurso `inteligencia_negocio` con:
--   * leer      -> consultar el consolidado y los tableros personalizados,
--   * gestionar -> crear/actualizar/eliminar tableros personalizados (Req 48.3),
--   * exportar  -> exportar el consolidado (Req 48.4).
-- Se asigna a gerente (...003) y admin_empresa (...002), coherente con V18 y con
-- la lectura transversal que V5 ya concedio a esos roles. Idempotente.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES
    ('inteligencia_negocio', 'leer'),
    ('inteligencia_negocio', 'gestionar'),
    ('inteligencia_negocio', 'exportar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.rol_id, p.id
FROM permiso p
CROSS JOIN (VALUES
        ('a0000000-0000-0000-0000-000000000003'::uuid),  -- gerente (Req 27.8)
        ('a0000000-0000-0000-0000-000000000002'::uuid)   -- admin_empresa (Req 27.10)
    ) AS r(rol_id)
WHERE p.recurso = 'inteligencia_negocio'
  AND p.operacion IN ('leer', 'gestionar', 'exportar')
ON CONFLICT DO NOTHING;
