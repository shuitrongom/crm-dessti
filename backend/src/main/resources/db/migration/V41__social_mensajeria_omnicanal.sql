-- ============================================================================
-- V41__social_mensajeria_omnicanal.sql
--
-- Modulo `social` - Mensajeria omnicanal y Bandeja_Unificada (Tarea 40, Req 64,
-- 11, 9, 23, 49). Cubre los tres canales de Meta (WhatsApp, Facebook Messenger e
-- Instagram) desde una unica bandeja ligada al CRM. Establece cinco tablas
-- tenant-scoped:
--   * cuenta_canal_social : conexion de la Empresa a un Canal_Social (numero de
--                           WhatsApp Business, pagina de Facebook o perfil de
--                           Instagram). Guarda una REFERENCIA a las credenciales
--                           (credenciales_ref), NUNCA el secreto (Req 11).
--   * conversacion        : hilo unico por remitente en una cuenta de canal,
--                           ligado a Cliente/Contacto; su ultimo_entrante_utc
--                           gobierna la Ventana_Servicio (Req 64.6/64.7).
--   * mensaje_social      : mensaje individual entrante/saliente de una
--                           Conversacion, con tipo (texto/plantilla/interactivo),
--                           marca de marketing y estado_entrega (Req 64.11).
--   * plantilla_mensaje   : Plantilla_Mensaje aprobada por el proveedor, requerida
--                           para comunicar FUERA de la Ventana_Servicio (Req 64.7).
--   * consentimiento_canal: registro de Opt_In/Opt_Out por canal y sujeto, con
--                           actor y marca temporal UTC (Req 64.8/64.9).
--
-- Replica EXACTAMENTE el patron reutilizable de V11..V40 para toda tabla
-- tenant-scoped:
--   * tenant_id UUID NOT NULL (Req 23.1) + FK -> empresa,
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V17/V37.
--
-- Requisitos cubiertos:
--   - Req 64.1/64.2 (Cuenta_Canal_Social por canal): tabla cuenta_canal_social
--     con canal acotado, identificador_externo y credenciales_ref (referencia,
--     nunca el secreto, Req 11). UNIQUE (tenant_id, canal, identificador_externo).
--   - Req 64.3/64.4 (recepcion de entrantes por webhook): la persistencia del
--     Mensaje_Social entrante y su Conversacion la realiza la aplicacion tras
--     validar la firma del webhook (HMAC, X-Hub-Signature-256). TLS termina en
--     IIS (Req 9); la aplicacion valida la firma del evento.
--   - Req 64.5/64.14 (Bandeja_Unificada, hilo unico por Cliente/Contacto con
--     filtros): indices (tenant_id, cliente_id), (tenant_id, estado),
--     (tenant_id, canal) de apoyo al listado paginado.
--   - Req 64.6/64.7 (Ventana_Servicio, Property 37): la guarda es una funcion PURA
--     del dominio sobre conversacion.ultimo_entrante_utc; fuera de la ventana de
--     24h se exige Plantilla_Mensaje aprobada.
--   - Req 64.8/64.9 (Opt_In marketing, Property 38): consentimiento_canal registra
--     opt_in/opt_out con canal, actor y marca UTC; la guarda de marketing es PURA.
--   - Req 64.10 (handover/asignacion): conversacion.estado (abierta/asignada/
--     cerrada) y asignado_a.
--   - Req 64.11 (mensajes interactivos): mensaje_social.tipo admite 'interactivo'.
--   - Req 64.13 (reintentos): politica de reintentos configurable en el adaptador
--     (crm.social.*), no en BD.
--   - Req 64.15 (auditoria): la registra la aplicacion via AuditoriaPort (no BD).
--   - Req 23 (multi-tenant): tenant_id + RLS. Req 49 (concurrencia): version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. CREDENCIALES_REF, NUNCA EL SECRETO (Req 11): cuenta_canal_social guarda
--      solo una REFERENCIA (nombre/clave logica) al almacen de secretos donde
--      residen los tokens de acceso de Meta. El valor del secreto jamas se
--      almacena en BD ni se escribe en logs; lo resuelve el adaptador desde el
--      entorno/vault (crm.social.*).
--   2. VENTANA_SERVICIO POR ultimo_entrante_utc (DECISION central, Property 37):
--      la ventana de 24h se mide desde conversacion.ultimo_entrante_utc (el ultimo
--      Mensaje_Social ENTRANTE). La columna es NULL mientras no haya ningun
--      entrante (conversacion iniciada por la Empresa), en cuyo caso se esta FUERA
--      de la ventana y se exige Plantilla_Mensaje. La guarda vive en el dominio
--      como funcion pura; la BD solo persiste el instante.
--   3. HILO UNICO POR REMITENTE: UNIQUE (tenant_id, cuenta_canal_social_id,
--      remitente_externo) garantiza una unica Conversacion por remitente dentro de
--      una cuenta de canal; la Bandeja_Unificada consolida los tres canales por
--      Cliente/Contacto a nivel de consulta (Req 64.5).
--   4. ESTADO conversacion abierta/asignada/cerrada (maquina de estados minima,
--      Req 64.10): etiquetas ASCII en minusculas coherentes con V17/V37; el
--      dominio aplica la maquina de estados pura. asignado_a referencia al Usuario
--      responsable del handover.
--   5. es_marketing + estado_entrega: mensaje_social.es_marketing distingue los
--      envios de marketing (sujetos a la guarda de Opt_In, Req 64.8);
--      estado_entrega (enviado/entregado/leido/fallido) es NULL en entrantes y se
--      actualiza en salientes conforme al mapeo del adaptador (Req 64.11).
--   6. CONSENTIMIENTO por (canal, sujeto_externo) y opcionalmente cliente_id: el
--      sujeto se identifica por el remitente del canal (sujeto_externo) y, cuando
--      se resuelve, por cliente_id. El ultimo registro por (tenant, canal, sujeto)
--      determina la vigencia (opt_in vigente <=> ultimo estado = 'opt_in').
--   7. PERMISOS (Req 3): V5 ya sembro cuenta_canal_social:{crear,leer,listar},
--      bandeja:{leer,actualizar} y conversacion:{leer,actualizar}, asignados a
--      los roles `marketing` (a0000000-0000-0000-0000-00000000000d) y `ventas`
--      (a0000000-0000-0000-0000-000000000005). Este bloque necesita ADEMAS:
--        - conversacion:enviar        (envio de Mensaje_Social, Req 64.6/64.7),
--        - consentimiento:registrar   (opt-in/opt-out, Req 64.9),
--        - plantilla_mensaje:{crear,leer,listar}  (Plantilla_Mensaje, Req 64.7).
--      Se siembran (ON CONFLICT DO NOTHING) y se enlazan a marketing y ventas,
--      con el estilo de V37. El resto de permisos NO se re-siembran.
--   8. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V40.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- cuenta_canal_social
--   Conexion de la Empresa a un Canal_Social (Req 64.1, 64.2). tenant-scoped
--   (Req 23). credenciales_ref es una REFERENCIA al secreto, NUNCA el valor
--   (DECISION 1, Req 11).
-- ----------------------------------------------------------------------------
CREATE TABLE cuenta_canal_social (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID          NOT NULL,
    canal                 VARCHAR(12)   NOT NULL,
    identificador_externo VARCHAR(120)  NOT NULL,
    nombre                VARCHAR(200)  NOT NULL,
    -- Referencia (clave logica) al almacen de secretos; NUNCA el secreto (Req 11).
    credenciales_ref      VARCHAR(200)  NOT NULL,
    activa                BOOLEAN       NOT NULL DEFAULT TRUE,
    -- Columnas heredadas de TenantScopedEntity.
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    CONSTRAINT pk_cuenta_canal_social PRIMARY KEY (id),
    CONSTRAINT fk_cuenta_canal_social_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Canal acotado a los tres canales de Meta (DECISION 4).
    CONSTRAINT ck_cuenta_canal_social_canal CHECK (
        canal IN ('whatsapp', 'messenger', 'instagram')),
    -- Identificadores/nombre/referencia no vacios (Req 64.1).
    CONSTRAINT ck_cuenta_canal_social_identificador CHECK (length(btrim(identificador_externo)) >= 1),
    CONSTRAINT ck_cuenta_canal_social_nombre CHECK (length(btrim(nombre)) >= 1),
    CONSTRAINT ck_cuenta_canal_social_cred_ref CHECK (length(btrim(credenciales_ref)) >= 1),
    -- Una unica cuenta por canal e identificador externo dentro del tenant.
    CONSTRAINT uq_cuenta_canal_social_canal_identificador UNIQUE (tenant_id, canal, identificador_externo)
);

CREATE INDEX ix_cuenta_canal_social_tenant_id ON cuenta_canal_social (tenant_id);
CREATE INDEX ix_cuenta_canal_social_tenant_canal ON cuenta_canal_social (tenant_id, canal);

-- ----------------------------------------------------------------------------
-- conversacion
--   Hilo unico por remitente en una cuenta de canal (Req 64.5). tenant-scoped
--   (Req 23). ultimo_entrante_utc gobierna la Ventana_Servicio (DECISION 2). Su
--   estado (abierta/asignada/cerrada) modela el handover (DECISION 4, Req 64.10).
-- ----------------------------------------------------------------------------
CREATE TABLE conversacion (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID          NOT NULL,
    cuenta_canal_social_id  UUID          NOT NULL,
    canal                   VARCHAR(12)   NOT NULL,
    remitente_externo       VARCHAR(120)  NOT NULL,
    cliente_id              UUID,
    contacto_id             UUID,
    estado                  VARCHAR(10)   NOT NULL DEFAULT 'abierta',
    asignado_a              UUID,
    -- Marca temporal del ultimo Mensaje_Social ENTRANTE; NULL si aun no hay
    -- entrantes (fuera de la Ventana_Servicio, DECISION 2).
    ultimo_entrante_utc     TIMESTAMPTZ,
    -- Columnas heredadas de TenantScopedEntity.
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    CONSTRAINT pk_conversacion PRIMARY KEY (id),
    CONSTRAINT fk_conversacion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_conversacion_cuenta_canal FOREIGN KEY (cuenta_canal_social_id)
        REFERENCES cuenta_canal_social (id),
    -- Enlace opcional a Cliente existente; sin cascada (historico del CRM).
    CONSTRAINT fk_conversacion_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    -- Canal acotado (coherente con la cuenta de canal).
    CONSTRAINT ck_conversacion_canal CHECK (canal IN ('whatsapp', 'messenger', 'instagram')),
    CONSTRAINT ck_conversacion_remitente CHECK (length(btrim(remitente_externo)) >= 1),
    -- Estado acotado (DECISION 4, Req 64.10).
    CONSTRAINT ck_conversacion_estado CHECK (estado IN ('abierta', 'asignada', 'cerrada')),
    -- Un unico hilo por remitente dentro de una cuenta de canal (DECISION 3).
    CONSTRAINT uq_conversacion_cuenta_remitente UNIQUE (tenant_id, cuenta_canal_social_id, remitente_externo)
);

CREATE INDEX ix_conversacion_tenant_id ON conversacion (tenant_id);
CREATE INDEX ix_conversacion_tenant_cliente ON conversacion (tenant_id, cliente_id);
CREATE INDEX ix_conversacion_tenant_estado ON conversacion (tenant_id, estado);
CREATE INDEX ix_conversacion_tenant_canal ON conversacion (tenant_id, canal);

-- ----------------------------------------------------------------------------
-- mensaje_social
--   Mensaje individual entrante/saliente de una Conversacion (Req 64.4, 64.11).
--   tenant-scoped (Req 23). estado_entrega es NULL en entrantes y se actualiza en
--   salientes conforme al mapeo del adaptador (DECISION 5).
-- ----------------------------------------------------------------------------
CREATE TABLE mensaje_social (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    conversacion_id UUID          NOT NULL,
    direccion       VARCHAR(8)    NOT NULL,
    tipo            VARCHAR(12)   NOT NULL,
    contenido       TEXT          NOT NULL,
    es_marketing    BOOLEAN       NOT NULL DEFAULT FALSE,
    estado_entrega  VARCHAR(12),
    externo_id      VARCHAR(120),
    enviado_en      TIMESTAMPTZ,
    recibido_en     TIMESTAMPTZ,
    -- Columnas heredadas de TenantScopedEntity.
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_mensaje_social PRIMARY KEY (id),
    CONSTRAINT fk_mensaje_social_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Al borrar la Conversacion se elimina su historial de mensajes (cascada).
    CONSTRAINT fk_mensaje_social_conversacion FOREIGN KEY (conversacion_id)
        REFERENCES conversacion (id) ON DELETE CASCADE,
    CONSTRAINT ck_mensaje_social_direccion CHECK (direccion IN ('entrante', 'saliente')),
    CONSTRAINT ck_mensaje_social_tipo CHECK (tipo IN ('texto', 'plantilla', 'interactivo')),
    CONSTRAINT ck_mensaje_social_contenido CHECK (length(btrim(contenido)) >= 1),
    -- estado_entrega acotado; NULL admitido (entrantes) (DECISION 5).
    CONSTRAINT ck_mensaje_social_estado_entrega CHECK (
        estado_entrega IS NULL
        OR estado_entrega IN ('enviado', 'entregado', 'leido', 'fallido'))
);

CREATE INDEX ix_mensaje_social_tenant_id ON mensaje_social (tenant_id);
CREATE INDEX ix_mensaje_social_tenant_conversacion ON mensaje_social (tenant_id, conversacion_id);

-- ----------------------------------------------------------------------------
-- plantilla_mensaje
--   Plantilla_Mensaje aprobada por el proveedor del Canal_Social, requerida para
--   comunicar FUERA de la Ventana_Servicio (Req 64.7). tenant-scoped (Req 23).
-- ----------------------------------------------------------------------------
CREATE TABLE plantilla_mensaje (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID          NOT NULL,
    canal       VARCHAR(12)   NOT NULL,
    nombre      VARCHAR(120)  NOT NULL,
    contenido   TEXT          NOT NULL,
    aprobada    BOOLEAN       NOT NULL DEFAULT FALSE,
    -- Columnas heredadas de TenantScopedEntity.
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_plantilla_mensaje PRIMARY KEY (id),
    CONSTRAINT fk_plantilla_mensaje_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT ck_plantilla_mensaje_canal CHECK (canal IN ('whatsapp', 'messenger', 'instagram')),
    CONSTRAINT ck_plantilla_mensaje_nombre CHECK (length(btrim(nombre)) >= 1),
    CONSTRAINT ck_plantilla_mensaje_contenido CHECK (length(btrim(contenido)) >= 1),
    -- Un nombre de plantilla por canal dentro del tenant.
    CONSTRAINT uq_plantilla_mensaje_canal_nombre UNIQUE (tenant_id, canal, nombre)
);

CREATE INDEX ix_plantilla_mensaje_tenant_id ON plantilla_mensaje (tenant_id);
CREATE INDEX ix_plantilla_mensaje_tenant_canal ON plantilla_mensaje (tenant_id, canal);

-- ----------------------------------------------------------------------------
-- consentimiento_canal (Opt_In / Opt_Out)
--   Registro del consentimiento del Cliente/Contacto por canal (Req 64.8, 64.9).
--   tenant-scoped (Req 23). El ultimo registro por (tenant, canal, sujeto)
--   determina la vigencia (DECISION 6).
-- ----------------------------------------------------------------------------
CREATE TABLE consentimiento_canal (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    canal          VARCHAR(12)   NOT NULL,
    sujeto_externo VARCHAR(120)  NOT NULL,
    cliente_id     UUID,
    estado         VARCHAR(8)    NOT NULL,
    registrado_en  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actor          VARCHAR(255),
    -- Columnas heredadas de TenantScopedEntity.
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    CONSTRAINT pk_consentimiento_canal PRIMARY KEY (id),
    CONSTRAINT fk_consentimiento_canal_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_consentimiento_canal_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT ck_consentimiento_canal_canal CHECK (canal IN ('whatsapp', 'messenger', 'instagram')),
    CONSTRAINT ck_consentimiento_canal_sujeto CHECK (length(btrim(sujeto_externo)) >= 1),
    -- Estado acotado a opt_in/opt_out (DECISION 6, Req 64.9).
    CONSTRAINT ck_consentimiento_canal_estado CHECK (estado IN ('opt_in', 'opt_out'))
);

CREATE INDEX ix_consentimiento_canal_tenant_id ON consentimiento_canal (tenant_id);
CREATE INDEX ix_consentimiento_canal_tenant_canal_sujeto
    ON consentimiento_canal (tenant_id, canal, sujeto_externo);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V37/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE cuenta_canal_social ENABLE ROW LEVEL SECURITY;
ALTER TABLE cuenta_canal_social FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON cuenta_canal_social
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE conversacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversacion FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON conversacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE mensaje_social ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensaje_social FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON mensaje_social
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE plantilla_mensaje ENABLE ROW LEVEL SECURITY;
ALTER TABLE plantilla_mensaje FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON plantilla_mensaje
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE consentimiento_canal ENABLE ROW LEVEL SECURITY;
ALTER TABLE consentimiento_canal FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON consentimiento_canal
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- PERMISOS COMPLEMENTARIOS (Req 3) -- DECISION 7
--   V5 ya sembro cuenta_canal_social:{crear,leer,listar}, bandeja:{leer,
--   actualizar} y conversacion:{leer,actualizar}, asignados a `marketing`
--   (a0000000-0000-0000-0000-00000000000d) y `ventas`
--   (a0000000-0000-0000-0000-000000000005). Este bloque necesita ADEMAS:
--     - conversacion:enviar        (envio de Mensaje_Social, Req 64.6/64.7),
--     - consentimiento:registrar   (opt-in/opt-out, Req 64.9),
--     - plantilla_mensaje:{crear,leer,listar}  (Plantilla_Mensaje, Req 64.7).
--   Se siembran (ON CONFLICT DO NOTHING por la clave natural recurso, operacion)
--   y se enlazan a AMBOS roles, con el estilo de V37/V33. El resto NO se re-siembra.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('conversacion',      'enviar'),
    ('consentimiento',    'registrar'),
    ('plantilla_mensaje', 'crear'),
    ('plantilla_mensaje', 'leer'),
    ('plantilla_mensaje', 'listar')
ON CONFLICT (recurso, operacion) DO NOTHING;

-- marketing (a0000000-0000-0000-0000-00000000000d)
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000d', p.id
FROM permiso p
WHERE (p.recurso = 'conversacion'      AND p.operacion = 'enviar')
   OR (p.recurso = 'consentimiento'    AND p.operacion = 'registrar')
   OR (p.recurso = 'plantilla_mensaje' AND p.operacion IN ('crear', 'leer', 'listar'))
ON CONFLICT DO NOTHING;

-- ventas (a0000000-0000-0000-0000-000000000005)
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000005', p.id
FROM permiso p
WHERE (p.recurso = 'conversacion'      AND p.operacion = 'enviar')
   OR (p.recurso = 'consentimiento'    AND p.operacion = 'registrar')
   OR (p.recurso = 'plantilla_mensaje' AND p.operacion IN ('crear', 'leer', 'listar'))
ON CONFLICT DO NOTHING;
