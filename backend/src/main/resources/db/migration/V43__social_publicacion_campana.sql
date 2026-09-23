-- ============================================================================
-- V43__social_publicacion_campana.sql
--
-- Modulo `social` - Publicacion_Social y Campana_Publicitaria (Tarea 41, Req 65,
-- 11, 23, 49). EXTIENDE el modulo social de V41 (mensajeria omnicanal) con la
-- programacion de publicaciones organicas y las campañas de marketing hacia los
-- canales de Meta, reutilizando cuenta_canal_social como origen. Establece tres
-- tablas tenant-scoped:
--   * publicacion_social   : una Publicacion_Social programada hacia un
--                            Canal_Social, con maquina de estados
--                            borrador -> programada -> {publicada|fallida}
--                            (Req 65.1-65.5).
--   * intento_publicacion  : registro por intento de la politica de reintentos de
--                            la publicacion (Req 65.6). Trazabilidad de cada envio
--                            al adaptador (exito/fallo).
--   * campana_publicitaria : una Campana_Publicitaria con presupuesto acotado
--                            [0.01, 999,999,999.99] y periodo valido
--                            (fecha_fin >= fecha_inicio) (Req 65.7, 65.8). Su
--                            estado operativo es de SOLO LECTURA desde la Marketing
--                            API de Meta (Req 65.9), NO autoritativo en BD.
--
-- Replica EXACTAMENTE el patron reutilizable de V11..V42 para toda tabla
-- tenant-scoped:
--   * tenant_id UUID NOT NULL (Req 23.1) + FK -> empresa,
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2/V17/V41 (ENABLE + FORCE +
--     tenant_isolation con app.current_tenant).
--
-- Requisitos cubiertos:
--   - Req 65.1/65.2 (crear Publicacion_Social, estado inicial 'borrador',
--     contenido y fecha programada validos): tabla publicacion_social con estado
--     por defecto 'borrador', contenido no vacio y fecha_programada NOT NULL. La
--     regla "fecha no anterior al momento actual" la aplica el dominio con el Clock
--     del servicio (Req 65.2), no la BD.
--   - Req 65.3/65.4 (maquina de estados): CHECK sobre estado
--     ('borrador','programada','publicada','fallida'); las transiciones validas y
--     el rechazo (409) los aplica la maquina de estados PURA del dominio
--     (EstadoPublicacion), coherente con conversacion/nomina de V41/anteriores.
--   - Req 65.5/65.6 (publicar via adaptador con reintentos y registrar cada
--     intento): la publicacion la ejecuta la aplicacion via PublicacionSocialPort;
--     externo_id/publicada_en/motivo_fallo en publicacion_social e intento_publicacion
--     por cada intento. La politica de reintentos es configurable en el adaptador
--     (crm.social.*), no en BD.
--   - Req 65.7/65.8 (Campana con presupuesto y periodo validos; Property 39):
--     campana_publicitaria con CHECK de rango de presupuesto NUMERIC(18,2)
--     [0.01, 999999999.99] y CHECK fecha_fin >= fecha_inicio. La validacion PURA la
--     realiza el dominio (ValidacionCampana) y la BD la refuerza como red de
--     seguridad.
--   - Req 65.9 (estado de Campana de SOLO LECTURA desde Meta): NO se persiste un
--     estado autoritativo; estado_externo VARCHAR(20) NULL guarda a lo sumo la
--     ultima instantanea de diagnostico consultada via el adaptador.
--   - Req 65.10 (listado paginado 20/100, filtro por Canal_Social y estado):
--     indices (tenant_id, canal) y (tenant_id, estado) de apoyo al listado.
--   - Req 65.11 (auditoria): la registra la aplicacion via AuditoriaPort (no BD).
--   - Req 23 (multi-tenant): tenant_id + RLS. Req 49 (concurrencia): version.
--   - Req 11 (secretos): el token de Meta se resuelve por credenciales_ref de la
--     cuenta_canal_social; NUNCA se almacena el secreto aqui.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. MAQUINA DE ESTADOS EN EL DOMINIO (Req 65.3, 65.4): el CHECK de estado solo
--      acota el dominio de valores; la ACEPTACION/RECHAZO de transiciones y la
--      inmutabilidad de los estados finales (publicada/fallida) las gobierna la
--      maquina de estados PURA (EstadoPublicacion + MaquinaEstados), como en
--      conversacion (V41) y nomina.
--   2. CANAL REUTILIZA EL DOMINIO DE V41 (whatsapp/messenger/instagram): el Req
--      65.1 menciona Facebook/Instagram; para no divergir del dominio de canal ya
--      establecido en cuenta_canal_social/conversacion (V41), el CHECK de
--      publicacion_social.canal reutiliza el mismo conjunto. En la practica la
--      Publicacion_Social aplica a 'messenger' (paginas de Facebook) e 'instagram';
--      la restriccion de subconjunto se documenta pero no se fuerza en BD.
--   3. REINTENTOS CON TRAZA POR INTENTO (Req 65.6): intento_publicacion registra
--      numero_intento (>=1), exito y mensaje_error por cada envio al adaptador, con
--      ON DELETE CASCADE respecto a publicacion_social. La politica (numero de
--      intentos, espera) es configurable en el adaptador (crm.social.*), no en BD.
--   4. PRESUPUESTO NUMERIC(18,2) CON RANGO (Req 65.7, 65.8; Property 39): identico
--      rango y escala que el precio unitario de cotizacion (0.01..999999999.99),
--      validado PRIMERO en el dominio (ValidacionCampana) y reforzado por CHECK.
--   5. PERIODO fecha_fin >= fecha_inicio (Req 65.8): CHECK a nivel de fila; el
--      dominio lo valida antes de persistir (Property 39).
--   6. ESTADO DE CAMPANA DE SOLO LECTURA (Req 65.9): la autoridad es la Marketing
--      API de Meta consultada via el adaptador. No hay columna de estado
--      autoritativo; estado_externo es una instantanea NULLABLE, no fuente de
--      verdad.
--   7. PERMISOS (Req 3): V5 YA sembro publicacion_social:{crear,leer,listar,
--      cambiar_estado} y campana_publicitaria:{crear,leer,listar}, asignados al rol
--      `marketing` (a0000000-0000-0000-0000-00000000000d). Por tanto este bloque NO
--      re-siembra permisos ni asignaciones (se verifico en V5).
--   8. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente con
--      V11..V42.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- publicacion_social
--   Publicacion_Social programada hacia un Canal_Social (Req 65.1-65.5).
--   tenant-scoped (Req 23). Maquina de estados en el dominio (DECISION 1).
-- ----------------------------------------------------------------------------
CREATE TABLE publicacion_social (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID          NOT NULL,
    cuenta_canal_social_id  UUID          NOT NULL,
    canal                   VARCHAR(12)   NOT NULL,
    contenido               TEXT          NOT NULL,
    fecha_programada        TIMESTAMPTZ   NOT NULL,
    estado                  VARCHAR(12)   NOT NULL DEFAULT 'borrador',
    externo_id              VARCHAR(120),
    publicada_en            TIMESTAMPTZ,
    motivo_fallo            VARCHAR(500),
    -- Columnas heredadas de TenantScopedEntity.
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    CONSTRAINT pk_publicacion_social PRIMARY KEY (id),
    CONSTRAINT fk_publicacion_social_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_publicacion_social_cuenta_canal FOREIGN KEY (cuenta_canal_social_id)
        REFERENCES cuenta_canal_social (id),
    -- Canal reutiliza el dominio de V41 (DECISION 2).
    CONSTRAINT ck_publicacion_social_canal CHECK (canal IN ('whatsapp', 'messenger', 'instagram')),
    CONSTRAINT ck_publicacion_social_contenido CHECK (length(btrim(contenido)) >= 1),
    -- Dominio de estados (DECISION 1); transiciones las gobierna el dominio.
    CONSTRAINT ck_publicacion_social_estado CHECK (
        estado IN ('borrador', 'programada', 'publicada', 'fallida'))
);

CREATE INDEX ix_publicacion_social_tenant_id ON publicacion_social (tenant_id);
CREATE INDEX ix_publicacion_social_tenant_canal ON publicacion_social (tenant_id, canal);
CREATE INDEX ix_publicacion_social_tenant_estado ON publicacion_social (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- intento_publicacion
--   Registro por intento de la politica de reintentos de la publicacion
--   (Req 65.6). tenant-scoped (Req 23). ON DELETE CASCADE respecto a la
--   Publicacion_Social (DECISION 3).
-- ----------------------------------------------------------------------------
CREATE TABLE intento_publicacion (
    id                     UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id              UUID          NOT NULL,
    publicacion_social_id  UUID          NOT NULL,
    numero_intento         INTEGER       NOT NULL,
    exito                  BOOLEAN       NOT NULL,
    mensaje_error          VARCHAR(500),
    intentado_en           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Columnas heredadas de TenantScopedEntity.
    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by             VARCHAR(255),
    updated_by             VARCHAR(255),
    CONSTRAINT pk_intento_publicacion PRIMARY KEY (id),
    CONSTRAINT fk_intento_publicacion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Al borrar la Publicacion_Social se elimina su historial de intentos (cascada).
    CONSTRAINT fk_intento_publicacion_publicacion FOREIGN KEY (publicacion_social_id)
        REFERENCES publicacion_social (id) ON DELETE CASCADE,
    CONSTRAINT ck_intento_publicacion_numero CHECK (numero_intento >= 1)
);

CREATE INDEX ix_intento_publicacion_tenant_id ON intento_publicacion (tenant_id);
CREATE INDEX ix_intento_publicacion_tenant_publicacion
    ON intento_publicacion (tenant_id, publicacion_social_id);

-- ----------------------------------------------------------------------------
-- campana_publicitaria
--   Campana_Publicitaria de marketing con presupuesto y periodo (Req 65.7-65.9).
--   tenant-scoped (Req 23). Presupuesto acotado (DECISION 4), periodo coherente
--   (DECISION 5) y estado de SOLO LECTURA desde Meta (DECISION 6).
-- ----------------------------------------------------------------------------
CREATE TABLE campana_publicitaria (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID           NOT NULL,
    cuenta_canal_social_id  UUID,
    canal                   VARCHAR(12),
    nombre                  VARCHAR(200)   NOT NULL,
    presupuesto             NUMERIC(18, 2) NOT NULL,
    fecha_inicio            DATE           NOT NULL,
    fecha_fin               DATE           NOT NULL,
    externo_id              VARCHAR(120),
    -- Instantanea de estado externo (Req 65.9); NO autoritativa (DECISION 6).
    estado_externo          VARCHAR(20),
    -- Columnas heredadas de TenantScopedEntity.
    version                 BIGINT         NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    CONSTRAINT pk_campana_publicitaria PRIMARY KEY (id),
    CONSTRAINT fk_campana_publicitaria_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_campana_publicitaria_cuenta_canal FOREIGN KEY (cuenta_canal_social_id)
        REFERENCES cuenta_canal_social (id),
    -- Canal opcional; cuando se indica, reutiliza el dominio de V41 (DECISION 2).
    CONSTRAINT ck_campana_publicitaria_canal CHECK (
        canal IS NULL OR canal IN ('whatsapp', 'messenger', 'instagram')),
    CONSTRAINT ck_campana_publicitaria_nombre CHECK (length(btrim(nombre)) >= 1),
    -- Presupuesto en [0.01, 999,999,999.99] (DECISION 4, Property 39).
    CONSTRAINT ck_campana_publicitaria_presupuesto CHECK (
        presupuesto >= 0.01 AND presupuesto <= 999999999.99),
    -- Periodo coherente: fecha_fin no anterior a fecha_inicio (DECISION 5, Property 39).
    CONSTRAINT ck_campana_publicitaria_periodo CHECK (fecha_fin >= fecha_inicio)
);

CREATE INDEX ix_campana_publicitaria_tenant_id ON campana_publicitaria (tenant_id);
CREATE INDEX ix_campana_publicitaria_tenant_canal ON campana_publicitaria (tenant_id, canal);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V41/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE publicacion_social ENABLE ROW LEVEL SECURITY;
ALTER TABLE publicacion_social FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON publicacion_social
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE intento_publicacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE intento_publicacion FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON intento_publicacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE campana_publicitaria ENABLE ROW LEVEL SECURITY;
ALTER TABLE campana_publicitaria FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON campana_publicitaria
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- PERMISOS (Req 3) -- DECISION 7
--   V5 YA sembro y asigno al rol `marketing`
--   (a0000000-0000-0000-0000-00000000000d):
--     - publicacion_social:{crear,leer,listar,cambiar_estado}
--     - campana_publicitaria:{crear,leer,listar}
--   Por tanto este bloque NO re-siembra permisos ni asignaciones.
-- ----------------------------------------------------------------------------
