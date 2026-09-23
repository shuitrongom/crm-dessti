-- ============================================================================
-- V42__notificaciones_notificacion.sql
--
-- Modulo transversal `notificaciones` (Tarea 43.1, Req 46, 12, 23, 49). Establece
-- las dos tablas del modulo de Notificaciones: la raiz `notificacion` (una
-- Notificacion generada ante un evento relevante y entregada por correo o por un
-- Canal_Social) y el historial append-only `intento_envio_notificacion` (el
-- resultado de CADA intento de envio conforme a la politica de reintentos). Replica
-- EXACTAMENTE el patron reutilizable establecido en V11..V40 para toda tabla
-- tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V17.
--
-- Requisitos cubiertos:
--   - Req 46.1 (generar una Notificacion ante evento relevante -> correo o WhatsApp):
--     notificacion.evento_origen VARCHAR(40) NOT NULL (catalogo del enum
--     TipoEventoNotificacion: prueba_diseno_enviada, permiso_por_vencer,
--     ticket_sla_por_incumplir, factura_timbrada, nomina_timbrada, ...); canal
--     VARCHAR(12) NOT NULL con CHECK IN ('correo','whatsapp','messenger','instagram');
--     destinatario VARCHAR(320) NOT NULL.
--   - Req 46.2 (integracion desacoplada correo/WhatsApp via puerto/adaptador;
--     credenciales via gestion de secretos Req 11): la frontera vive en la
--     aplicacion (NotificadorCorreoPort/NotificadorWhatsappPort/NotificadorSocialPort);
--     no requiere columnas aqui.
--   - Req 46.3 (reintentar segun politica configurable y registrar cada intento):
--     tabla intento_envio_notificacion con numero_intento >= 1, exito, mensaje_error
--     e intentado_en; la politica (max-intentos/backoff) es configurable
--     (crm.notificaciones.reintentos.*).
--   - Req 46.4 (contenido minimo, sin datos sensibles): asunto VARCHAR(200) NULL,
--     contenido VARCHAR(2000) NOT NULL; la minimizacion es responsabilidad del
--     llamador. mensaje_error VARCHAR(500) sin secretos.
--   - Req 46.5 (auditoria del envio con actor/evento/destinatario/canal/resultado y
--     marca UTC): la registra la aplicacion (Servicio_Auditoria).
--   - Req 46.6 (Canal_Social reutilizando la integracion, respetando
--     Ventana_Servicio y Plantilla_Mensaje): canal social en el CHECK; el enrutado
--     lo realiza la aplicacion via NotificadorSocialPort (puerto propio del modulo,
--     independiente del modulo social). No requiere columnas aqui.
--   - Req 46.7 (sin Opt_In vigente -> abstenerse y registrar el motivo de omision):
--     estado 'omitida' + motivo_omision VARCHAR(300).
--   - Req 23 (multi-tenant): tenant_id + RLS en ambas tablas.
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. PUERTOS DESACOPLADOS POR CANAL (Req 46.2): la entrega concreta (correo,
--      WhatsApp, Canal_Social) vive tras puertos de salida en la aplicacion, con
--      adaptadores por defecto de registro en log (@ConditionalOnMissingBean) para
--      arrancar y probar sin proveedores reales. Las credenciales se resuelven
--      EXCLUSIVAMENTE desde la gestion de secretos (Req 11) y nunca se persisten
--      aqui. La BD solo guarda el resultado (estado + historial de intentos).
--   2. REINTENTOS CONFIGURABLES CON REGISTRO POR INTENTO (Req 46.3):
--      intento_envio_notificacion es APPEND-ONLY: una fila por intento con su
--      resultado. Es el registro contable del Req 46.3. FK a notificacion ON DELETE
--      CASCADE: los intentos son subordinados de su Notificacion.
--   3. GUARDA DE OPT_IN PARA MARKETING SOCIAL (Req 46.7): cuando la Notificacion es
--      de marketing y el canal es social y NO hay Opt_In vigente, la aplicacion la
--      marca 'omitida' y registra motivo_omision, SIN enviar. El adaptador de
--      consentimiento por defecto aplica una politica segura (asume ausencia de
--      Opt_In) hasta que exista una fuente real.
--   4. CONTENIDO MINIMO (Req 46.4): asunto <= 200 y contenido <= 2000 acotan el
--      tamano; la minimizacion semantica la garantiza el llamador. mensaje_error se
--      limita a 500 y no debe contener secretos.
--   5. ESTADO 'omitida' (Req 46.7): se agrega al CHECK del estado junto a
--      'pendiente','enviada','fallida' para representar la abstencion por falta de
--      Opt_In. Etiquetas ASCII minusculas, coherentes con V17/V18.
--   6. evento_origen y canal COMO ETIQUETAS ASCII: el enum de dominio persiste su
--      etiqueta ASCII (sin acentos) por estabilidad de codificacion, coherente con
--      la convencion de V17/V18.
--   7. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente con
--      V11..V40.
--   8. PERMISOS: V5 NO sembro ningun recurso 'notificacion'. Como las Notificaciones
--      son mayormente generadas por eventos (no por endpoints de escritura), solo se
--      exponen operaciones de CONSULTA. Se siembran notificacion:{leer,listar} y se
--      asignan a admin_empresa (a0000000-...-002) y gerente (a0000000-...-003),
--      coherente con el patron de V18. No se re-siembra ningun permiso existente.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- notificacion
--   Raiz del modulo de Notificaciones (Req 46). tenant-scoped (Req 23). Se genera
--   ante un evento relevante (Req 46.1) en estado 'pendiente'; su estado final es
--   'enviada'/'fallida' segun el resultado del envio con reintentos (Req 46.3) u
--   'omitida' cuando falta Opt_In para marketing en Canal_Social (Req 46.7).
-- ----------------------------------------------------------------------------
CREATE TABLE notificacion (
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID           NOT NULL,
    -- Evento de negocio que la origina (Req 46.1). Etiqueta ASCII del enum
    -- TipoEventoNotificacion (DECISION 6).
    evento_origen     VARCHAR(40)    NOT NULL,
    -- Canal de entrega (Req 46.1, 46.6). Etiquetas ASCII minusculas (DECISION 6).
    canal             VARCHAR(12)    NOT NULL,
    -- Correo/telefono/identificador social del destinatario (Req 46.1).
    destinatario      VARCHAR(320)   NOT NULL,
    -- Asunto (correo); opcional. Contenido minimo sin datos sensibles (Req 46.4).
    asunto            VARCHAR(200),
    contenido         VARCHAR(2000)  NOT NULL,
    -- Notificacion de marketing (relevante para la guarda de Opt_In, Req 46.7).
    es_marketing      BOOLEAN        NOT NULL DEFAULT FALSE,
    -- Recurso de negocio referenciado por el evento (opcional).
    referencia_tipo   VARCHAR(40),
    referencia_id     UUID,
    -- Estado del ciclo de vida (Req 46.1, 46.3, 46.7). Ver DECISION 5.
    estado            VARCHAR(12)    NOT NULL DEFAULT 'pendiente',
    -- Motivo de la omision cuando estado = 'omitida' (Req 46.7).
    motivo_omision    VARCHAR(300),
    -- Marcas de negocio del envio (Req 46.1, 46.3).
    creada_en         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    enviada_en        TIMESTAMPTZ,
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    CONSTRAINT pk_notificacion PRIMARY KEY (id),
    CONSTRAINT fk_notificacion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Canales permitidos (Req 46.1, 46.6). Etiquetas ASCII minusculas.
    CONSTRAINT ck_notificacion_canal CHECK (
        canal IN ('correo', 'whatsapp', 'messenger', 'instagram')),
    -- Estados permitidos (Req 46.1, 46.3, 46.7). Ver DECISION 5.
    CONSTRAINT ck_notificacion_estado CHECK (
        estado IN ('pendiente', 'enviada', 'fallida', 'omitida')),
    -- Contenido no vacio (Req 46.4): al menos 1 caracter tras recortar espacios.
    CONSTRAINT ck_notificacion_contenido_no_vacio CHECK (length(btrim(contenido)) >= 1),
    -- Destinatario no vacio (Req 46.1).
    CONSTRAINT ck_notificacion_destinatario_no_vacio CHECK (length(btrim(destinatario)) >= 1)
);

CREATE INDEX ix_notificacion_tenant_id ON notificacion (tenant_id);

-- Apoyo a los listados/consultas del modulo, siempre acotados al tenant.
CREATE INDEX ix_notificacion_tenant_estado ON notificacion (tenant_id, estado);
CREATE INDEX ix_notificacion_tenant_evento ON notificacion (tenant_id, evento_origen);

-- ----------------------------------------------------------------------------
-- intento_envio_notificacion
--   Historial APPEND-ONLY (DECISION 2) del resultado de CADA intento de envio de
--   una Notificacion conforme a la politica de reintentos configurable (Req 46.3).
--   tenant-scoped (Req 23).
-- ----------------------------------------------------------------------------
CREATE TABLE intento_envio_notificacion (
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID           NOT NULL,
    notificacion_id   UUID           NOT NULL,
    -- Numero de intento 1-indexado y creciente (Req 46.3).
    numero_intento    INTEGER        NOT NULL,
    -- Resultado del intento (Req 46.3).
    exito             BOOLEAN        NOT NULL,
    -- Mensaje de error del proveedor cuando el intento fallo; sin secretos (Req 46.4).
    mensaje_error     VARCHAR(500),
    intentado_en      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- Columna heredada de TenantScopedEntity (Req 49); el historial no se modifica.
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    CONSTRAINT pk_intento_envio_notificacion PRIMARY KEY (id),
    CONSTRAINT fk_intento_envio_notificacion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Los intentos son subordinados de su Notificacion (DECISION 2).
    CONSTRAINT fk_intento_envio_notificacion_notificacion FOREIGN KEY (notificacion_id)
        REFERENCES notificacion (id) ON DELETE CASCADE,
    -- Numero de intento >= 1 (Req 46.3).
    CONSTRAINT ck_intento_envio_notificacion_numero CHECK (numero_intento >= 1)
);

CREATE INDEX ix_intento_envio_notificacion_tenant_id ON intento_envio_notificacion (tenant_id);

-- Apoyo a la recuperacion del historial de intentos por Notificacion (Req 46.3),
-- acotado al tenant.
CREATE INDEX ix_intento_envio_notificacion_tenant_notificacion
    ON intento_envio_notificacion (tenant_id, notificacion_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V18/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE notificacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE notificacion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON notificacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE intento_envio_notificacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE intento_envio_notificacion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON intento_envio_notificacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Permisos atomicos de CONSULTA de Notificaciones (Req 46, 3.1). V5 no sembro
-- ningun recurso 'notificacion'. Como las Notificaciones se generan por eventos
-- (no por endpoints de escritura), solo se exponen operaciones de lectura:
-- notificacion:{leer,listar}. Se agregan al catalogo y se asignan a admin_empresa
-- (...002) y gerente (...003), coherente con el patron de V18 (DECISION 8).
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES
    ('notificacion', 'leer'),
    ('notificacion', 'listar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT rol_id, p.id
FROM permiso p
CROSS JOIN (VALUES
        ('a0000000-0000-0000-0000-000000000002'::uuid),  -- admin_empresa (Req 27.10)
        ('a0000000-0000-0000-0000-000000000003'::uuid)   -- gerente (Req 27.8)
    ) AS r(rol_id)
WHERE p.recurso = 'notificacion' AND p.operacion IN ('leer', 'listar')
ON CONFLICT DO NOTHING;
