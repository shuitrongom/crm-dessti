-- ============================================================================
-- V47__calidad_iso9001_2026.sql
--
-- Modulo `calidad` - Cumplimiento y calidad ISO 9001:2026 (Tarea 54, Req 70, 3,
-- 10, 12, 23, 49). Habilita y evidencia el Sistema de Gestion de Calidad (SGC)
-- de la Empresa conforme a ISO 9001:2026, sin introducir logica fiscal ni
-- financiera; se apoya en los servicios transversales existentes (auditoria,
-- notificaciones, tablero, redes sociales). Establece siete tablas
-- tenant-scoped:
--   * queja_cliente          : reclamacion del Cliente con su origen (incluido el
--                              canal de la Bandeja_Unificada del Req 64),
--                              descripcion, Cliente asociado y marca UTC; entrada
--                              potencial -no obligatoria- a una Accion_Correctiva
--                              (clausula 10.2, Req 70.1).
--   * no_conformidad         : No_Conformidad con origen, descripcion, proceso
--                              afectado y marca UTC (clausula 10.2, Req 70.2).
--   * accion_correctiva      : Accion_Correctiva con responsable, causa raiz,
--                              acciones planificadas, evidencia de cierre y
--                              verificacion de eficacia; maquina de estados con
--                              estado final `cerrada` que EXIGE eficacia
--                              verificada (Property 43, clausula 10.2, Req 70.2).
--   * riesgo                 : Riesgo con probabilidad, impacto, nivel derivado y
--                              acciones (clausula 6.1.2, Req 70.3).
--   * oportunidad_calidad    : Oportunidad de calidad con beneficio esperado y
--                              acciones (clausula 6.1.3, Req 70.3). Se nombra
--                              oportunidad_calidad para NO confundir con la
--                              Oportunidad comercial del pipeline (Req 14).
--   * cambio_sgc             : Cambio del SGC que EXIGE proposito, consecuencias
--                              potenciales, recursos necesarios y responsable
--                              antes de aprobar; maquina de estados
--                              propuesto->aprobado->implementado, con `rechazado`
--                              como final alterno (clausula 6.3, Req 70.4).
--   * contexto_organizacion  : cuestion interna/externa pertinente al SGC, con el
--                              indicador de pertinencia del cambio climatico y su
--                              justificacion (conservada aun cuando la conclusion
--                              sea "no pertinente"), parte interesada y expectativa
--                              (clausulas 4.1 y 4.2, Req 70.5).
--
-- Replica EXACTAMENTE el patron reutilizable de V11..V46 para toda tabla
-- tenant-scoped:
--   * tenant_id UUID NOT NULL (Req 23.1) + FK -> empresa,
--   * version BIGINT NOT NULL DEFAULT 0 para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at (TIMESTAMPTZ NOT NULL DEFAULT
--     now()) y created_by/updated_by (VARCHAR(255)) en UTC,
--   * indice por tenant_id (y por (tenant_id, estado) donde apoya el listado),
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando EXACTAMENTE el patron de V41/V37/V17/V2.
--
-- Requisitos cubiertos:
--   - Req 70.1 (Queja_Cliente y vinculo opcional a Accion_Correctiva): tabla
--     queja_cliente con origen acotado (incluido 'social'), canal_social_id
--     opcional, cliente_id (FK) y accion_correctiva_id opcional (vinculo NO
--     obligatorio); estado registrada/vinculada/atendida.
--   - Req 70.2 (No_Conformidad y Accion_Correctiva con eficacia verificada):
--     tablas no_conformidad y accion_correctiva; el CHECK de estado acota la
--     maquina; la guarda de cierre (eficacia verificada) vive en el dominio
--     (funcion pura, Property 43). Como refuerzo declarativo se anade un CHECK
--     de coherencia estado='cerrada' => eficacia_verificada = TRUE.
--   - Req 70.3 (Riesgo y Oportunidad_Calidad separados): tablas riesgo y
--     oportunidad_calidad como agregados distintos con acciones propias
--     (clausulas 6.1.2 y 6.1.3).
--   - Req 70.4 (Cambio_SGC): tabla cambio_sgc; el CHECK de estado acota la
--     maquina; la exigencia de proposito/consecuencias/recursos/responsable
--     antes de aprobar la aplica el dominio; la aprobacion queda en auditoria.
--   - Req 70.5 (Contexto_Organizacion y cambio climatico): tabla
--     contexto_organizacion con clima_pertinente (bool) y justificacion; la
--     justificacion NO se descarta cuando clima_pertinente = FALSE.
--   - Req 70.6/70.9 (evidencia y auditoria): NO se agrega almacenamiento nuevo;
--     se reutiliza el Servicio_Auditoria (Req 10). Cada operacion la audita la
--     aplicacion via AuditoriaPort (no la BD).
--   - Req 70.7/70.8 (indicadores de cultura de calidad y percepcion del cliente):
--     agregaciones de SOLO LECTURA en la aplicacion (ServicioIndicadoresCalidad);
--     no requieren esquema nuevo.
--   - Req 70.10 (trazabilidad de clausulas): catalogo fijo en codigo
--     (ServicioTrazabilidadIso); no requiere esquema.
--   - Req 3 (RBAC deny-by-default): permisos atomicos por recurso y rol de
--     calidad (DECISION 7). Req 23 (multi-tenant): tenant_id + RLS. Req 49
--     (concurrencia): version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. VINCULO OPCIONAL QUEJA -> ACCION_CORRECTIVA (Req 70.1, clausula 10.2): la
--      Queja_Cliente puede ser entrada de una Accion_Correctiva, pero NO se
--      obliga. accion_correctiva_id es NULLable; el estado 'vinculada' lo fija la
--      operacion de vinculo. Para evitar una dependencia ciclica de tablas (la
--      Accion_Correctiva se crea antes o despues que la Queja) la FK
--      accion_correctiva_id se declara sin cascada y admite NULL.
--   2. ORIGEN SOCIAL SIN CICLO DE MODULOS (Req 70.1, 70.8): la creacion de una
--      Queja_Cliente desde una Conversacion social NO acopla `calidad` con
--      `social`. queja_cliente guarda cliente_id (FK, ya resuelto por social) y
--      un canal_social_id OPCIONAL (referencia debil, SIN FK a tablas de social)
--      mas origen='social'. El caso de uso de creacion recibe estos datos ya
--      resueltos; `calidad` no importa internals de `social`.
--   3. NIVEL DERIVADO DEL RIESGO (Req 70.3, clausula 6.1.2): nivel_derivado se
--      persiste como columna acotada (bajo/medio/alto/critico) para consulta e
--      indexacion, pero su valor lo DERIVA la funcion pura del dominio a partir
--      de (probabilidad x impacto). Es de solo lectura para el cliente del API.
--   4. GUARDA DE CIERRE DE ACCION_CORRECTIVA (Property 43, Req 70.2): la maquina
--      de estados (abierta->en_analisis->en_ejecucion->verificacion->cerrada)
--      vive en el dominio; 'cerrada' es final y solo se alcanza si
--      eficacia_verificada = TRUE. El CHECK ck_accion_correctiva_cierre_eficacia
--      es un refuerzo declarativo de esa invariante en la BD.
--   5. MAQUINA DE CAMBIO_SGC (Req 70.4, clausula 6.3): propuesto->aprobado->
--      implementado, con 'rechazado' como final alterno desde 'propuesto'. La
--      exigencia de los campos antes de aprobar la impone el dominio;
--      aprobado_por/aprobado_en quedan NULL hasta la aprobacion.
--   6. CONTEXTO Y CAMBIO CLIMATICO (Req 70.5, clausulas 4.1/4.2): clima_pertinente
--      es un booleano y justificacion es OBLIGATORIA en ambos sentidos (la
--      determinacion de "no pertinente" tambien debe justificarse y conservarse).
--   7. PERMISOS Y ROL DE CALIDAD (Req 3): se agrega el rol predefinido `calidad`
--      (tenant_id NULL, predefinido TRUE) con UUID fijo
--      a0000000-0000-0000-0000-00000000000f, continuando la familia de V5/V45
--      (el ultimo fue ...00000e = cliente_portal). Se siembran los permisos
--      atomicos recurso:{crear,leer,listar,cambiar_estado} de los siete recursos
--      de calidad (ON CONFLICT DO NOTHING) y se enlazan:
--        - a `calidad`       : TODOS (crear/leer/listar/cambiar_estado),
--        - a `gerente`       : SOLO leer/listar (lectura transversal, Req 27.8),
--        - a `admin_empresa` : SOLO leer/listar.
--      Estos recursos son de NIVEL EMPRESA (no de plataforma): NO se agregan al
--      ClasificadorRecursosPlataforma.
--   8. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V46.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- no_conformidad
--   No_Conformidad detectada en el SGC (Req 70.2, clausula 10.2). tenant-scoped
--   (Req 23). Se crea antes que su Accion_Correctiva (que la referencia).
-- ----------------------------------------------------------------------------
CREATE TABLE no_conformidad (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL,
    origen           VARCHAR(20)   NOT NULL,
    descripcion      TEXT          NOT NULL,
    proceso_afectado VARCHAR(200)  NOT NULL,
    detectada_en     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    estado           VARCHAR(16)   NOT NULL DEFAULT 'abierta',
    -- Columnas heredadas de TenantScopedEntity.
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    CONSTRAINT pk_no_conformidad PRIMARY KEY (id),
    CONSTRAINT fk_no_conformidad_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Origen acotado (Req 70.2). Etiquetas ASCII minusculas.
    CONSTRAINT ck_no_conformidad_origen CHECK (
        origen IN ('queja', 'auditoria_interna', 'proceso', 'proveedor', 'otro')),
    CONSTRAINT ck_no_conformidad_descripcion CHECK (length(btrim(descripcion)) >= 1),
    CONSTRAINT ck_no_conformidad_proceso CHECK (length(btrim(proceso_afectado)) >= 1),
    -- Estado acotado (Req 70.2). Etiquetas ASCII minusculas.
    CONSTRAINT ck_no_conformidad_estado CHECK (
        estado IN ('abierta', 'en_tratamiento', 'cerrada'))
);

CREATE INDEX ix_no_conformidad_tenant_id ON no_conformidad (tenant_id);
CREATE INDEX ix_no_conformidad_tenant_estado ON no_conformidad (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- accion_correctiva
--   Accion_Correctiva del SGC (Req 70.2, clausula 10.2). tenant-scoped (Req 23).
--   Maquina de estados abierta->en_analisis->en_ejecucion->verificacion->cerrada;
--   'cerrada' es final y EXIGE eficacia_verificada = TRUE (Property 43, DECISION 4).
-- ----------------------------------------------------------------------------
CREATE TABLE accion_correctiva (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID          NOT NULL,
    no_conformidad_id     UUID,
    responsable_id        UUID          NOT NULL,
    causa_raiz            TEXT          NOT NULL,
    acciones_planificadas TEXT          NOT NULL,
    evidencia_cierre      TEXT,
    eficacia_verificada   BOOLEAN       NOT NULL DEFAULT FALSE,
    estado                VARCHAR(16)   NOT NULL DEFAULT 'abierta',
    cerrada_en            TIMESTAMPTZ,
    -- Columnas heredadas de TenantScopedEntity.
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    CONSTRAINT pk_accion_correctiva PRIMARY KEY (id),
    CONSTRAINT fk_accion_correctiva_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Vinculo opcional a la No_Conformidad de origen; sin cascada (DECISION 1).
    CONSTRAINT fk_accion_correctiva_no_conformidad FOREIGN KEY (no_conformidad_id)
        REFERENCES no_conformidad (id),
    -- Responsable (Usuario) de la Accion_Correctiva (Req 70.2).
    CONSTRAINT fk_accion_correctiva_responsable FOREIGN KEY (responsable_id)
        REFERENCES usuario (id),
    CONSTRAINT ck_accion_correctiva_causa CHECK (length(btrim(causa_raiz)) >= 1),
    CONSTRAINT ck_accion_correctiva_acciones CHECK (length(btrim(acciones_planificadas)) >= 1),
    -- Estado acotado a la maquina de estados (Req 70.2, DECISION 4).
    CONSTRAINT ck_accion_correctiva_estado CHECK (
        estado IN ('abierta', 'en_analisis', 'en_ejecucion', 'verificacion', 'cerrada')),
    -- Refuerzo declarativo de la guarda de cierre (Property 43, Req 70.2):
    -- una Accion_Correctiva 'cerrada' exige eficacia verificada Y su marca de cierre.
    CONSTRAINT ck_accion_correctiva_cierre_eficacia CHECK (
        estado <> 'cerrada'
        OR (eficacia_verificada = TRUE AND cerrada_en IS NOT NULL))
);

CREATE INDEX ix_accion_correctiva_tenant_id ON accion_correctiva (tenant_id);
CREATE INDEX ix_accion_correctiva_tenant_estado ON accion_correctiva (tenant_id, estado);
CREATE INDEX ix_accion_correctiva_tenant_no_conformidad
    ON accion_correctiva (tenant_id, no_conformidad_id);

-- ----------------------------------------------------------------------------
-- queja_cliente
--   Reclamacion del Cliente (Req 70.1, clausula 10.2). tenant-scoped (Req 23).
--   Entrada potencial -no obligatoria- a una Accion_Correctiva (DECISION 1). El
--   origen 'social' con canal_social_id (referencia debil, SIN FK, DECISION 2)
--   cubre la creacion desde una Conversacion de la Bandeja_Unificada (Req 64).
-- ----------------------------------------------------------------------------
CREATE TABLE queja_cliente (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID          NOT NULL,
    cliente_id           UUID          NOT NULL,
    origen               VARCHAR(12)   NOT NULL,
    -- Referencia debil al canal social de origen (Conversacion/Cuenta), sin FK a
    -- tablas de `social` para NO acoplar modulos (DECISION 2). NULL salvo social.
    canal_social_id      UUID,
    descripcion          TEXT          NOT NULL,
    estado               VARCHAR(12)   NOT NULL DEFAULT 'registrada',
    -- Vinculo OPCIONAL a la Accion_Correctiva de la que es entrada (DECISION 1).
    accion_correctiva_id UUID,
    registrada_en        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Columnas heredadas de TenantScopedEntity.
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),
    CONSTRAINT pk_queja_cliente PRIMARY KEY (id),
    CONSTRAINT fk_queja_cliente_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Cliente existente asociado a la queja (Req 70.1). Sin cascada (historico).
    CONSTRAINT fk_queja_cliente_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    -- Vinculo opcional a la Accion_Correctiva; sin cascada (DECISION 1).
    CONSTRAINT fk_queja_cliente_accion_correctiva FOREIGN KEY (accion_correctiva_id)
        REFERENCES accion_correctiva (id),
    -- Origen acotado, incluido el canal social del Req 64 (Req 70.1, 70.8).
    CONSTRAINT ck_queja_cliente_origen CHECK (
        origen IN ('portal', 'social', 'correo', 'telefono', 'otro')),
    CONSTRAINT ck_queja_cliente_descripcion CHECK (length(btrim(descripcion)) >= 1),
    -- Estado acotado (Req 70.1). 'vinculada' <=> hay accion_correctiva_id.
    CONSTRAINT ck_queja_cliente_estado CHECK (
        estado IN ('registrada', 'vinculada', 'atendida')),
    -- Coherencia: si esta 'vinculada' debe existir la Accion_Correctiva vinculada.
    CONSTRAINT ck_queja_cliente_vinculada CHECK (
        estado <> 'vinculada' OR accion_correctiva_id IS NOT NULL)
);

CREATE INDEX ix_queja_cliente_tenant_id ON queja_cliente (tenant_id);
CREATE INDEX ix_queja_cliente_tenant_cliente ON queja_cliente (tenant_id, cliente_id);
CREATE INDEX ix_queja_cliente_tenant_estado ON queja_cliente (tenant_id, estado);
CREATE INDEX ix_queja_cliente_tenant_origen ON queja_cliente (tenant_id, origen);

-- ----------------------------------------------------------------------------
-- riesgo
--   Riesgo del SGC (Req 70.3, clausula 6.1.2). tenant-scoped (Req 23).
--   nivel_derivado lo calcula la funcion pura del dominio (probabilidad x
--   impacto) y se persiste para consulta/indexacion (DECISION 3).
-- ----------------------------------------------------------------------------
CREATE TABLE riesgo (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    descripcion    TEXT          NOT NULL,
    probabilidad   VARCHAR(8)    NOT NULL,
    impacto        VARCHAR(8)    NOT NULL,
    nivel_derivado VARCHAR(8)    NOT NULL,
    acciones       TEXT,
    estado         VARCHAR(16)   NOT NULL DEFAULT 'identificado',
    -- Columnas heredadas de TenantScopedEntity.
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    CONSTRAINT pk_riesgo PRIMARY KEY (id),
    CONSTRAINT fk_riesgo_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT ck_riesgo_descripcion CHECK (length(btrim(descripcion)) >= 1),
    -- Probabilidad e impacto acotados (Req 70.3, clausula 6.1.2).
    CONSTRAINT ck_riesgo_probabilidad CHECK (probabilidad IN ('baja', 'media', 'alta')),
    CONSTRAINT ck_riesgo_impacto CHECK (impacto IN ('bajo', 'medio', 'alto')),
    -- Nivel derivado acotado (DECISION 3): resultado de la matriz prob x impacto.
    CONSTRAINT ck_riesgo_nivel CHECK (nivel_derivado IN ('bajo', 'medio', 'alto', 'critico')),
    -- Estado acotado (Req 70.3).
    CONSTRAINT ck_riesgo_estado CHECK (
        estado IN ('identificado', 'en_tratamiento', 'mitigado', 'aceptado'))
);

CREATE INDEX ix_riesgo_tenant_id ON riesgo (tenant_id);
CREATE INDEX ix_riesgo_tenant_estado ON riesgo (tenant_id, estado);
CREATE INDEX ix_riesgo_tenant_nivel ON riesgo (tenant_id, nivel_derivado);

-- ----------------------------------------------------------------------------
-- oportunidad_calidad
--   Oportunidad de calidad del SGC (Req 70.3, clausula 6.1.3). tenant-scoped
--   (Req 23). Agregado DISTINTO del Riesgo y de la Oportunidad comercial (Req 14).
-- ----------------------------------------------------------------------------
CREATE TABLE oportunidad_calidad (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID          NOT NULL,
    descripcion       TEXT          NOT NULL,
    beneficio_esperado TEXT         NOT NULL,
    acciones          TEXT,
    estado            VARCHAR(16)   NOT NULL DEFAULT 'identificada',
    -- Columnas heredadas de TenantScopedEntity.
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    CONSTRAINT pk_oportunidad_calidad PRIMARY KEY (id),
    CONSTRAINT fk_oportunidad_calidad_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT ck_oportunidad_calidad_descripcion CHECK (length(btrim(descripcion)) >= 1),
    CONSTRAINT ck_oportunidad_calidad_beneficio CHECK (length(btrim(beneficio_esperado)) >= 1),
    -- Estado acotado (Req 70.3).
    CONSTRAINT ck_oportunidad_calidad_estado CHECK (
        estado IN ('identificada', 'en_evaluacion', 'en_ejecucion', 'realizada', 'descartada'))
);

CREATE INDEX ix_oportunidad_calidad_tenant_id ON oportunidad_calidad (tenant_id);
CREATE INDEX ix_oportunidad_calidad_tenant_estado ON oportunidad_calidad (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- cambio_sgc
--   Gestion del cambio del SGC (Req 70.4, clausula 6.3). tenant-scoped (Req 23).
--   Maquina propuesto->aprobado->implementado, con 'rechazado' final alterno
--   (DECISION 5). La exigencia de los campos antes de aprobar la impone el
--   dominio; aprobado_por/aprobado_en quedan NULL hasta la aprobacion.
-- ----------------------------------------------------------------------------
CREATE TABLE cambio_sgc (
    id                       UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                UUID          NOT NULL,
    titulo                   VARCHAR(200)  NOT NULL,
    proposito                TEXT          NOT NULL,
    consecuencias_potenciales TEXT         NOT NULL,
    recursos_necesarios      TEXT          NOT NULL,
    responsable_id           UUID          NOT NULL,
    estado                   VARCHAR(16)   NOT NULL DEFAULT 'propuesto',
    aprobado_por             VARCHAR(255),
    aprobado_en              TIMESTAMPTZ,
    -- Columnas heredadas de TenantScopedEntity.
    version                  BIGINT        NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    CONSTRAINT pk_cambio_sgc PRIMARY KEY (id),
    CONSTRAINT fk_cambio_sgc_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Responsable (Usuario) del cambio (Req 70.4).
    CONSTRAINT fk_cambio_sgc_responsable FOREIGN KEY (responsable_id) REFERENCES usuario (id),
    CONSTRAINT ck_cambio_sgc_titulo CHECK (length(btrim(titulo)) >= 1),
    CONSTRAINT ck_cambio_sgc_proposito CHECK (length(btrim(proposito)) >= 1),
    CONSTRAINT ck_cambio_sgc_consecuencias CHECK (length(btrim(consecuencias_potenciales)) >= 1),
    CONSTRAINT ck_cambio_sgc_recursos CHECK (length(btrim(recursos_necesarios)) >= 1),
    -- Estado acotado a la maquina de estados (Req 70.4, DECISION 5).
    CONSTRAINT ck_cambio_sgc_estado CHECK (
        estado IN ('propuesto', 'aprobado', 'implementado', 'rechazado')),
    -- Coherencia de la aprobacion: 'aprobado'/'implementado' exigen actor y marca UTC.
    CONSTRAINT ck_cambio_sgc_aprobacion CHECK (
        estado NOT IN ('aprobado', 'implementado')
        OR (aprobado_por IS NOT NULL AND aprobado_en IS NOT NULL))
);

CREATE INDEX ix_cambio_sgc_tenant_id ON cambio_sgc (tenant_id);
CREATE INDEX ix_cambio_sgc_tenant_estado ON cambio_sgc (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- contexto_organizacion
--   Contexto de la organizacion y cambio climatico (Req 70.5, clausulas 4.1/4.2).
--   tenant-scoped (Req 23). justificacion es OBLIGATORIA aun cuando
--   clima_pertinente = FALSE (DECISION 6).
-- ----------------------------------------------------------------------------
CREATE TABLE contexto_organizacion (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL,
    cuestion         TEXT          NOT NULL,
    tipo             VARCHAR(8)    NOT NULL,
    clima_pertinente BOOLEAN       NOT NULL DEFAULT FALSE,
    justificacion    TEXT          NOT NULL,
    parte_interesada VARCHAR(200),
    expectativa      TEXT,
    -- Columnas heredadas de TenantScopedEntity.
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    CONSTRAINT pk_contexto_organizacion PRIMARY KEY (id),
    CONSTRAINT fk_contexto_organizacion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT ck_contexto_organizacion_cuestion CHECK (length(btrim(cuestion)) >= 1),
    -- Tipo acotado (Req 70.5, clausula 4.1).
    CONSTRAINT ck_contexto_organizacion_tipo CHECK (tipo IN ('interna', 'externa')),
    -- La justificacion se conserva siempre (DECISION 6, clausula 4.2).
    CONSTRAINT ck_contexto_organizacion_justificacion CHECK (length(btrim(justificacion)) >= 1)
);

CREATE INDEX ix_contexto_organizacion_tenant_id ON contexto_organizacion (tenant_id);
CREATE INDEX ix_contexto_organizacion_tenant_tipo ON contexto_organizacion (tenant_id, tipo);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V41/V37/V17/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE no_conformidad ENABLE ROW LEVEL SECURITY;
ALTER TABLE no_conformidad FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON no_conformidad
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE accion_correctiva ENABLE ROW LEVEL SECURITY;
ALTER TABLE accion_correctiva FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON accion_correctiva
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE queja_cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE queja_cliente FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON queja_cliente
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE riesgo ENABLE ROW LEVEL SECURITY;
ALTER TABLE riesgo FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON riesgo
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE oportunidad_calidad ENABLE ROW LEVEL SECURITY;
ALTER TABLE oportunidad_calidad FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON oportunidad_calidad
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE cambio_sgc ENABLE ROW LEVEL SECURITY;
ALTER TABLE cambio_sgc FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON cambio_sgc
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE contexto_organizacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE contexto_organizacion FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contexto_organizacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- ROL PREDEFINIDO `calidad` (Req 3) -- DECISION 7
--   Continua la familia de UUIDs de V5/V45. El ultimo fue ...00000e
--   (cliente_portal), por lo que el siguiente libre es ...00000f. tenant_id NULL,
--   predefinido TRUE. La unicidad global la garantiza el indice parcial
--   uq_rol_nombre_predefinido (V1). Idempotente (ON CONFLICT DO NOTHING).
-- ----------------------------------------------------------------------------
INSERT INTO rol (id, tenant_id, nombre, predefinido) VALUES
    ('a0000000-0000-0000-0000-00000000000f', NULL, 'calidad', TRUE)
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- PERMISOS ATOMICOS DE CALIDAD (Req 3, Req 70.9) -- DECISION 7
--   recurso:{crear,leer,listar,cambiar_estado} para los siete recursos del
--   modulo. Etiquetas ASCII coherentes con V5/V37/V41. Idempotente por la clave
--   natural (recurso, operacion).
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('queja_cliente',         'crear'),
    ('queja_cliente',         'leer'),
    ('queja_cliente',         'listar'),
    ('queja_cliente',         'cambiar_estado'),
    ('no_conformidad',        'crear'),
    ('no_conformidad',        'leer'),
    ('no_conformidad',        'listar'),
    ('no_conformidad',        'cambiar_estado'),
    ('accion_correctiva',     'crear'),
    ('accion_correctiva',     'leer'),
    ('accion_correctiva',     'listar'),
    ('accion_correctiva',     'cambiar_estado'),
    ('riesgo',                'crear'),
    ('riesgo',                'leer'),
    ('riesgo',                'listar'),
    ('riesgo',                'cambiar_estado'),
    ('oportunidad_calidad',   'crear'),
    ('oportunidad_calidad',   'leer'),
    ('oportunidad_calidad',   'listar'),
    ('oportunidad_calidad',   'cambiar_estado'),
    ('cambio_sgc',            'crear'),
    ('cambio_sgc',            'leer'),
    ('cambio_sgc',            'listar'),
    ('cambio_sgc',            'cambiar_estado'),
    ('contexto_organizacion', 'crear'),
    ('contexto_organizacion', 'leer'),
    ('contexto_organizacion', 'listar'),
    ('contexto_organizacion', 'cambiar_estado'),
    -- Recurso agregador de solo lectura para indicadores y trazabilidad ISO
    -- (Req 70.6, 70.7, 70.8, 70.10). Solo operacion 'leer'.
    ('calidad',               'leer')
ON CONFLICT (recurso, operacion) DO NOTHING;

-- calidad (a0000000-0000-0000-0000-00000000000f) -> TODOS los permisos de calidad.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000f', p.id
FROM permiso p
WHERE p.recurso IN (
        'queja_cliente', 'no_conformidad', 'accion_correctiva', 'riesgo',
        'oportunidad_calidad', 'cambio_sgc', 'contexto_organizacion', 'calidad')
ON CONFLICT DO NOTHING;

-- gerente (a0000000-0000-0000-0000-000000000003) -> SOLO leer/listar (Req 27.8).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000003', p.id
FROM permiso p
WHERE p.recurso IN (
        'queja_cliente', 'no_conformidad', 'accion_correctiva', 'riesgo',
        'oportunidad_calidad', 'cambio_sgc', 'contexto_organizacion', 'calidad')
  AND p.operacion IN ('leer', 'listar')
ON CONFLICT DO NOTHING;

-- admin_empresa (a0000000-0000-0000-0000-000000000002) -> SOLO leer/listar.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000002', p.id
FROM permiso p
WHERE p.recurso IN (
        'queja_cliente', 'no_conformidad', 'accion_correctiva', 'riesgo',
        'oportunidad_calidad', 'cambio_sgc', 'contexto_organizacion', 'calidad')
  AND p.operacion IN ('leer', 'listar')
ON CONFLICT DO NOTHING;
