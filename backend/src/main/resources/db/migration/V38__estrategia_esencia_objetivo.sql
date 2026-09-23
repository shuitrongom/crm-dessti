-- ============================================================================
-- V38__estrategia_esencia_objetivo.sql
--
-- Modulo `estrategia` (planeacion estrategica) del CRM (Tarea 37.1, Req 58, 12, 23).
-- Introduce la Esencia_Empresa (mision/vision/valores), el Objetivo_Estrategico
-- con resultados clave ponderados (OKR-like) y el historial de avance, replicando
-- EXACTAMENTE el patron reutilizable establecido en V11..V17 para toda tabla
-- tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V16/V17.
--
-- NUMERO DE MIGRACION: V38 (pre-asignado). NO usar V37 (lo ocupa otro modulo en
-- paralelo).
--
-- Requisitos cubiertos:
--   - Req 58.1 (mision/vision/valores de la Empresa): tabla esencia_empresa con
--     una fila por tenant (UNIQUE (tenant_id)); los tres campos son NULLABLE para
--     que la Empresa los complete progresivamente (upsert desde la aplicacion).
--   - Req 58.2 (Objetivo_Estrategico con nombre, responsable, periodo y meta;
--     avance inicial 0): objetivo_estrategico con esos campos NOT NULL y
--     avance NUMERIC(5,2) NOT NULL DEFAULT 0.
--   - Req 58.3 (rechazo si falta un campo obligatorio nombrando el faltante): las
--     columnas NOT NULL y CHECK de presencia lo refuerzan; el nombramiento del
--     campo faltante lo produce el dominio (422).
--   - Req 58.4 (historial de avance conservado): tabla historial_avance_objetivo
--     APPEND-ONLY; la aplicacion solo inserta filas, nunca las modifica ni elimina.
--   - Req 58.5/58.6 (listado paginado, filtro por periodo y por responsable):
--     indices de apoyo (tenant_id, periodo_inicio) y (tenant_id, responsable).
--   - Req 58.7 (auditoria al crear/modificar objetivo o esencia): la registra la
--     aplicacion.
--   - Req 58.8 (resultados clave ponderados; avance = porcentaje ponderado de
--     cumplimiento, agregacion de solo lectura): tabla resultado_clave con
--     valor_objetivo, valor_actual y peso; el avance del objetivo lo calcula la
--     funcion pura CalculoAvanceObjetivo (Property 29) y se persiste como cache
--     de solo lectura en objetivo_estrategico.avance.
--   - Req 58.9 (avance entre 0% y 100%, NUNCA excede 100%): CHECK
--     (avance >= 0 AND avance <= 100) en objetivo_estrategico y en el historial;
--     el clamp lo garantiza ademas el dominio (Property 29).
--   - Req 58.10 (estado derivado en_riesgo/en_curso/cumplido): NO se persiste; es
--     una derivacion de solo lectura (DerivacionEstadoObjetivo) a partir del avance
--     y del periodo, calculada en tiempo de consulta.
--   - Req 23 (multi-tenant): tenant_id + RLS en las cuatro tablas.
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. AVANCE PONDERADO ACOTADO 0..100 (Property 29, Req 58.8/58.9): el avance del
--      objetivo es el porcentaje ponderado de cumplimiento de sus resultados clave
--      (cumplimiento_i = clamp(valor_actual/valor_objetivo, 0, 1); avance =
--      round(sum(peso_i*cumplimiento_i)/sum(peso_i)*100, 2)) y NUNCA excede 100%.
--      Se persiste en objetivo_estrategico.avance como cache de solo lectura
--      (recalculada por la aplicacion al cambiar los resultados clave); el CHECK
--      [0,100] lo refuerza a nivel de BD. Sin resultados clave, el avance lo
--      gobierna la actualizacion manual (Req 58.4), tambien acotada a [0,100].
--   2. HISTORIAL APPEND-ONLY (Req 58.4): cada actualizacion del avance inserta una
--      fila en historial_avance_objetivo; no se exponen UPDATE/DELETE sobre filas
--      historicas. Se guarda objetivo_estrategico_id (no navegacion) porque el
--      historial solo se agrega.
--   3. ESTADO DERIVADO NO PERSISTIDO (Req 58.10): en_riesgo/en_curso/cumplido se
--      derivan del avance y del periodo en cada consulta; no hay columna de estado
--      ni maquina de estados.
--   4. ESENCIA UNICA POR TENANT (Req 58.1): UNIQUE (tenant_id) impone una sola
--      Esencia_Empresa por Empresa; la aplicacion hace upsert (crear o actualizar).
--   5. FK resultado_clave/historial -> objetivo_estrategico ON DELETE CASCADE: los
--      resultados clave y el historial son partes del agregado del objetivo; si el
--      objetivo se elimina, sus hijos se eliminan con el.
--   6. PERMISOS: V5 ya sembro planeacion_estrategica:{crear,leer,actualizar} y
--      objetivo_estrategico:{crear,leer,listar,actualizar} y los asigno a los roles
--      admin_empresa (a0000000-...-000000000002) y gerente
--      (a0000000-...-000000000003). Por tanto V38 NO necesita sembrar permisos
--      adicionales.
--   7. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V17.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- esencia_empresa
--   Mision, vision y valores de la Empresa (Req 58.1). Una fila por tenant
--   (UNIQUE (tenant_id)); los tres campos son NULLABLE (se completan
--   progresivamente). tenant-scoped (Req 23).
-- ----------------------------------------------------------------------------
CREATE TABLE esencia_empresa (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    mision          TEXT,
    vision          TEXT,
    valores         TEXT,
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_esencia_empresa PRIMARY KEY (id),
    CONSTRAINT fk_esencia_empresa_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Una sola esencia por Empresa (Req 58.1, DECISION 4).
    CONSTRAINT uq_esencia_empresa_tenant UNIQUE (tenant_id)
);

CREATE INDEX ix_esencia_empresa_tenant_id ON esencia_empresa (tenant_id);

-- ----------------------------------------------------------------------------
-- objetivo_estrategico
--   Objetivo con nombre, responsable, periodo y meta medible (Req 58.2). El avance
--   NUMERIC(5,2) es el porcentaje ponderado de cumplimiento de sus resultados clave
--   (cache de solo lectura, Property 29) o el valor manual sin resultados clave;
--   acotado a [0,100] (Req 58.9). tenant-scoped (Req 23).
-- ----------------------------------------------------------------------------
CREATE TABLE objetivo_estrategico (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    nombre          VARCHAR(200)   NOT NULL,
    responsable     VARCHAR(200)   NOT NULL,
    periodo_inicio  DATE           NOT NULL,
    periodo_fin     DATE           NOT NULL,
    meta            VARCHAR(500)   NOT NULL,
    -- Avance ponderado acotado a [0,100] (Req 58.8/58.9, DECISION 1); inicial 0.
    avance          NUMERIC(5,2)   NOT NULL DEFAULT 0,
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_objetivo_estrategico PRIMARY KEY (id),
    CONSTRAINT fk_objetivo_estrategico_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Presencia efectiva de nombre/responsable/meta (Req 58.2/58.3): no en blanco.
    CONSTRAINT ck_objetivo_estrategico_nombre CHECK (length(btrim(nombre)) >= 1),
    CONSTRAINT ck_objetivo_estrategico_responsable CHECK (length(btrim(responsable)) >= 1),
    CONSTRAINT ck_objetivo_estrategico_meta CHECK (length(btrim(meta)) >= 1),
    -- El fin del periodo no puede ser anterior a su inicio (Req 58.2).
    CONSTRAINT ck_objetivo_estrategico_periodo CHECK (periodo_fin >= periodo_inicio),
    -- Avance entre 0% y 100%, NUNCA excede 100% (Req 58.9, DECISION 1).
    CONSTRAINT ck_objetivo_estrategico_avance CHECK (avance >= 0 AND avance <= 100)
);

CREATE INDEX ix_objetivo_estrategico_tenant_id ON objetivo_estrategico (tenant_id);

-- Apoyo a los filtros del listado (Req 58.5/58.6): por periodo y por responsable,
-- siempre acotados al tenant.
CREATE INDEX ix_objetivo_estrategico_tenant_periodo
    ON objetivo_estrategico (tenant_id, periodo_inicio);
CREATE INDEX ix_objetivo_estrategico_tenant_responsable
    ON objetivo_estrategico (tenant_id, responsable);

-- ----------------------------------------------------------------------------
-- resultado_clave
--   Metrica medible ponderada asociada a un objetivo (Req 58.8): valor_objetivo
--   (meta), valor_actual y peso relativo (0,100]. El avance del objetivo se calcula
--   como el porcentaje ponderado de cumplimiento (Property 29). tenant-scoped (Req 23).
-- ----------------------------------------------------------------------------
CREATE TABLE resultado_clave (
    id                       UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                UUID           NOT NULL,
    objetivo_estrategico_id  UUID           NOT NULL,
    descripcion              VARCHAR(300)   NOT NULL,
    valor_objetivo           NUMERIC(18,4)  NOT NULL,
    valor_actual             NUMERIC(18,4)  NOT NULL DEFAULT 0,
    peso                     NUMERIC(5,2)   NOT NULL,
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version                  BIGINT         NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    CONSTRAINT pk_resultado_clave PRIMARY KEY (id),
    CONSTRAINT fk_resultado_clave_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Parte del agregado del objetivo: se elimina en cascada con el (DECISION 5).
    CONSTRAINT fk_resultado_clave_objetivo FOREIGN KEY (objetivo_estrategico_id)
        REFERENCES objetivo_estrategico (id) ON DELETE CASCADE,
    -- Peso relativo estrictamente positivo y a lo sumo 100 (Req 58.8).
    CONSTRAINT ck_resultado_clave_peso CHECK (peso > 0 AND peso <= 100)
);

CREATE INDEX ix_resultado_clave_tenant_id ON resultado_clave (tenant_id);

-- Apoyo a la carga de los resultados clave de un objetivo (Req 58.8), acotado al tenant.
CREATE INDEX ix_resultado_clave_tenant_objetivo
    ON resultado_clave (tenant_id, objetivo_estrategico_id);

-- ----------------------------------------------------------------------------
-- historial_avance_objetivo
--   Registro APPEND-ONLY de cada avance de un objetivo (Req 58.4, DECISION 2). Cada
--   actualizacion del avance -manual o derivada del recalculo por resultados clave-
--   inserta una fila; nunca se modifican ni eliminan filas historicas.
--   tenant-scoped (Req 23).
-- ----------------------------------------------------------------------------
CREATE TABLE historial_avance_objetivo (
    id                       UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                UUID           NOT NULL,
    objetivo_estrategico_id  UUID           NOT NULL,
    avance                   NUMERIC(5,2)   NOT NULL,
    registrado_en            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    actor                    VARCHAR(255),
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version                  BIGINT         NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    CONSTRAINT pk_historial_avance_objetivo PRIMARY KEY (id),
    CONSTRAINT fk_historial_avance_objetivo_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Parte del agregado del objetivo: se elimina en cascada con el (DECISION 5).
    CONSTRAINT fk_historial_avance_objetivo_objetivo FOREIGN KEY (objetivo_estrategico_id)
        REFERENCES objetivo_estrategico (id) ON DELETE CASCADE,
    -- Avance registrado entre 0% y 100% (Req 58.4/58.9).
    CONSTRAINT ck_historial_avance_objetivo_avance CHECK (avance >= 0 AND avance <= 100)
);

CREATE INDEX ix_historial_avance_objetivo_tenant_id ON historial_avance_objetivo (tenant_id);

-- Apoyo a la consulta del historial de un objetivo (Req 58.4), acotado al tenant.
CREATE INDEX ix_historial_avance_objetivo_tenant_objetivo
    ON historial_avance_objetivo (tenant_id, objetivo_estrategico_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V16/V17/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE esencia_empresa ENABLE ROW LEVEL SECURITY;
ALTER TABLE esencia_empresa FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON esencia_empresa
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE objetivo_estrategico ENABLE ROW LEVEL SECURITY;
ALTER TABLE objetivo_estrategico FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON objetivo_estrategico
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE resultado_clave ENABLE ROW LEVEL SECURITY;
ALTER TABLE resultado_clave FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON resultado_clave
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE historial_avance_objetivo ENABLE ROW LEVEL SECURITY;
ALTER TABLE historial_avance_objetivo FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON historial_avance_objetivo
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
