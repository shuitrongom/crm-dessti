-- ============================================================================
-- V6__sesiones_refresco.sql
--
-- Almacen de Sesiones (Token_Refresco) y denylist de refresco revocados
-- (Tarea 11.2). Crea la tabla `sesion_refresco` conforme al diseno
-- (design.md -> Data Models -> "Plataforma y Seguridad" -> Token_Refresco
-- (Sesion)) y a la Security -> "Gestion de Sesion (Req 68)".
--
-- Requisito cubierto: Req 68 (gestion y revocacion de sesiones):
--   * Registro de cada Token_Refresco emitido con su jti, usuario, tenant e
--     instantes de emision/expiracion (Req 68.3).
--   * Revocacion en logout, por administrador, por desactivacion de cuenta y
--     por cambio de contrasena, mediante la bandera `revocado` (Req 68.1,
--     68.2, 68.4).
--   * Rechazo de todo Token_Refresco revocado conforme al Req 1.9.
--   * Consulta paginada de sesiones activas (Req 68.5).
--
-- ----------------------------------------------------------------------------
-- MODELO ELEGIDO: fila por sesion con bandera `revocado`
-- ----------------------------------------------------------------------------
-- El diseno admite "un registro de refresco revocados (denylist por jti) o su
-- equivalente". Se opta por una FILA POR SESION con `revocado BOOLEAN`, no por
-- una denylist separada, porque con UNA sola tabla se satisface tanto:
--   (a) el RECHAZO de un refresco revocado (consulta por jti -> revocado), como
--   (b) el LISTADO de sesiones ACTIVAS de una cuenta (Req 68.5),
-- que una denylist pura (solo jti revocados) no permitiria (no conoce las
-- sesiones activas). NUNCA se almacena el valor del token: solo su jti y
-- metadatos (Req 10.10, 11.3).
--
-- ----------------------------------------------------------------------------
-- AMBITO MULTI-TENANT Y RLS (decision documentada)
-- ----------------------------------------------------------------------------
-- `sesion_refresco` sigue el mismo criterio que la tabla `usuario` respecto al
-- flujo de autenticacion, pero con una diferencia importante frente a la RLS:
--   * tenant_id es NULLABLE (NULL = super_admin), igual que en `usuario`.
--   * Las operaciones sobre sesiones (registrar en login, consultar la denylist
--     por jti en refresh, revocar en logout) ocurren en el FLUJO DE
--     AUTENTICACION, que NO fija `app.current_tenant` (el tenant se deriva del
--     usuario/JWT DESPUES de autenticar). Por ello, a diferencia de `usuario`
--     (que se resuelve por identificador_acceso unico global en contexto de
--     plataforma), aplicar la politica RLS `tenant_isolation` aqui IMPEDIRIA la
--     consulta por jti de una sesion tenant-scoped durante refresh/logout
--     (fail-safe que romperia el rechazo del Req 68.3).
--   * En consecuencia, NO se habilita RLS sobre `sesion_refresco`. El
--     aislamiento se garantiza en la CAPA DE AUTORIZACION: la consulta de
--     sesiones activas y la revocacion por administrador exigen los Permisos
--     ('sesion','listar') / ('sesion','cambiar_estado') y acotan por
--     usuario_id derivado del contexto autenticado (nunca de la peticion,
--     Req 23.4). El jti (UUID aleatorio, no adivinable) actua ademas como
--     capacidad no enumerable para el rechazo del refresco revocado.
-- ============================================================================

CREATE TABLE sesion_refresco (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    jti                VARCHAR(64)  NOT NULL,       -- claim jti (UUID) del Token_Refresco
    usuario_id         UUID         NOT NULL,
    tenant_id          UUID,                        -- NULL = super_admin (plataforma)
    emitido_en         TIMESTAMPTZ  NOT NULL,
    expira_en          TIMESTAMPTZ  NOT NULL,
    revocado           BOOLEAN      NOT NULL DEFAULT FALSE,
    revocado_en        TIMESTAMPTZ,
    motivo_revocacion  VARCHAR(20),                 -- LOGOUT | ADMINISTRADOR | DESACTIVACION | CAMBIO_PASSWORD
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_sesion_refresco PRIMARY KEY (id),
    CONSTRAINT uq_sesion_refresco_jti UNIQUE (jti),
    CONSTRAINT fk_sesion_refresco_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_sesion_refresco_empresa FOREIGN KEY (tenant_id)  REFERENCES empresa (id),
    CONSTRAINT ck_sesion_refresco_motivo
        CHECK (motivo_revocacion IS NULL
               OR motivo_revocacion IN ('LOGOUT', 'ADMINISTRADOR', 'DESACTIVACION', 'CAMBIO_PASSWORD')),
    -- Coherencia: una sesion revocada lleva instante y motivo; una activa no.
    CONSTRAINT ck_sesion_refresco_revocacion
        CHECK ((revocado = TRUE  AND revocado_en IS NOT NULL AND motivo_revocacion IS NOT NULL)
            OR (revocado = FALSE AND revocado_en IS NULL     AND motivo_revocacion IS NULL)),
    CONSTRAINT ck_sesion_refresco_vigencia CHECK (expira_en > emitido_en)
);

-- Indice por usuario para la revocacion en bloque (Req 68.2, 68.4) y el listado
-- de sesiones activas (Req 68.5). Filtra por sesiones no revocadas, que son las
-- consultadas al listar y revocar.
CREATE INDEX ix_sesion_refresco_usuario_activas
    ON sesion_refresco (usuario_id, emitido_en DESC)
    WHERE revocado = FALSE;

-- (El indice unico uq_sesion_refresco_jti cubre la consulta por jti para el
--  rechazo del refresco revocado, Req 1.9/68.3.)

COMMENT ON TABLE sesion_refresco IS
    'Sesiones (Token_Refresco) del Sistema: registro + denylist por jti para la '
    'revocacion de sesiones (Req 68). Fila por sesion con bandera revocado; '
    'nunca almacena el valor del token. Sin RLS: las operaciones ocurren en el '
    'flujo de autenticacion (sin app.current_tenant); el aislamiento se aplica '
    'en la capa de autorizacion (permisos de sesion) y por jti no enumerable.';
