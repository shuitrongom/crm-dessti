-- ============================================================================
-- V27__mantenimiento_contrato_ticket.sql
--
-- Modulo `mantenimiento` (Tarea 25.1, Req 20, 23, 49). Establece las dos raices
-- de agregado del modulo: `contrato_mantenimiento` (contrato de SLA asociado a un
-- Cliente) y `ticket_servicio` (incidencia/orden de servicio con maquina de
-- estados y evaluacion de cumplimiento del SLA). Replica EXACTAMENTE el patron
-- reutilizable establecido en V11..V26 para toda tabla tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V17/V25.
--
-- Requisitos cubiertos (Req 20):
--   - Req 20.1 (registrar Contrato_Mantenimiento asociado a un Cliente existente
--     con datos obligatorios: tipo 'preventivo'/'correctivo', tiempo de respuesta
--     del SLA en horas y tiempo de resolucion del SLA en horas -> id unico):
--     tabla `contrato_mantenimiento` con cliente_id NOT NULL (FK a cliente), tipo
--     VARCHAR(12) NOT NULL CHECK IN ('preventivo','correctivo'), sla_respuesta_horas
--     y sla_resolucion_horas INTEGER NOT NULL CHECK (> 0); id UUID PK.
--   - Req 20.2 (generar Ticket_Servicio manual o automatico por mantenimiento
--     preventivo, estado inicial 'abierto', id unico): tabla `ticket_servicio` con
--     origen VARCHAR(12) NOT NULL CHECK IN ('manual','preventivo'), estado
--     VARCHAR(12) NOT NULL DEFAULT 'abierto'; id UUID PK.
--   - Req 20.3 (asignar el Ticket a un tecnico o a una Cuadrilla): asignado_tipo
--     VARCHAR(10) NULL CHECK IN ('tecnico','cuadrilla') y asignado_id UUID NULL.
--   - Req 20.4/20.5 (maquina de estados con transiciones definidas; transicion no
--     incluida se rechaza conservando el estado): estado con CHECK IN
--     ('abierto','asignado','en_proceso','resuelto','cerrado'); la maquina pura
--     vive en el dominio (EstadoTicketServicio).
--   - Req 20.6 (al pasar a 'resuelto' registrar cumplimiento/incumplimiento del SLA
--     comparando el tiempo transcurrido desde la apertura con los tiempos del
--     Contrato_Mantenimiento asociado): abierto_en TIMESTAMPTZ NOT NULL, resuelto_en
--     TIMESTAMPTZ NULL y las banderas sla_respuesta_cumplido/sla_resolucion_cumplido
--     BOOLEAN NULL (nulas hasta la resolucion; nulas siempre si no hay contrato).
--   - Req 20.7 (listado paginado 20/100, filtros por estado, por Cliente y por
--     vencimiento de SLA): cliente_id DENORMALIZADO en el ticket (DECISION 1) e
--     indices de apoyo por (tenant_id, estado) y (tenant_id, cliente_id).
--   - Req 20.8 (auditoria al crear/cambiar estado del Ticket: actor, estado
--     anterior, estado nuevo, marca UTC): la registra la aplicacion via AuditoriaPort.
--   - Req 23 (multi-tenant): tenant_id + RLS en ambas tablas.
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. cliente_id DENORMALIZADO EN `ticket_servicio` (Req 20.7): el filtro del
--      listado por Cliente debe ser un predicado directo sobre la propia tabla,
--      sin depender de que el ticket tenga contrato. Un Ticket_Servicio manual
--      puede no tener Contrato_Mantenimiento (contrato_mantenimiento_id NULL, ver
--      DECISION 2), por lo que el Cliente no siempre puede derivarse del contrato:
--      se almacena directamente en el ticket (FK a cliente, sin cascada). Coherente
--      con la denormalizacion de cliente_id en orden_fabricacion (V17).
--   2. contrato_mantenimiento_id NULLABLE (Req 20.2): un Ticket_Servicio puede
--      generarse de forma MANUAL sin un Contrato_Mantenimiento asociado (origen
--      'manual'). La evaluacion del SLA (Req 20.6) SOLO aplica cuando el ticket
--      tiene contrato; sin contrato, las banderas sla_*_cumplido permanecen NULL.
--      FK a contrato_mantenimiento sin cascada (registro operativo).
--   3. asignado_id COMO REFERENCIA DEBIL (UUID SIN FK) (Req 20.3): la asignacion
--      puede apuntar a un tecnico (usuario) o a una Cuadrilla; el tipo de destino
--      lo discrimina asignado_tipo ('tecnico'/'cuadrilla'). Como el destino es
--      polimorfico (dos tablas potenciales) y la tabla `cuadrilla` no forma parte
--      del alcance de este bloque, asignado_id NO lleva FK: es una referencia debil
--      documentada, coherente con la referencia debil sitio_id de V19/V20/V24. La
--      coherencia asignado_tipo/asignado_id (ambos presentes o ambos ausentes) la
--      garantiza el CHECK ck_ticket_servicio_asignacion.
--   4. ESTADOS EN ASCII MINUSCULAS: etiquetas 'abierto','asignado','en_proceso',
--      'resuelto','cerrado' (Req 20.4). 'en_proceso' sin acento por estabilidad de
--      codificacion, coherente con V17. El enum de dominio mapea a estas etiquetas.
--   5. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V26.
--   6. PERMISOS: V5 YA sembro contrato_mantenimiento:{crear,leer,listar,actualizar}
--      y ticket_servicio:{crear,leer,listar,cambiar_estado} y los asigno al rol
--      `mantenimiento` (UUID a0000000-...-00000000000a, Req 27.7). Por tanto V27
--      NO siembra permisos adicionales.
--   7. FKs sin cascada: tanto el contrato como el ticket son registros operativos
--      que no deben borrarse en cascada con su Cliente ni con su contrato.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- contrato_mantenimiento
--   Contrato de SLA asociado a un Cliente existente (Req 20.1). tenant-scoped
--   (Req 23). Define el tipo ('preventivo'/'correctivo') y los tiempos del SLA
--   (respuesta y resolucion, en horas, ambos > 0). Es la referencia contra la que
--   se evalua el cumplimiento del SLA de sus Tickets_Servicio (Req 20.6).
-- ----------------------------------------------------------------------------
CREATE TABLE contrato_mantenimiento (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL,
    -- Cliente al que pertenece el contrato (Req 20.1). FK a cliente (V11); sin
    -- cascada (registro operativo, DECISION 7).
    cliente_id            UUID           NOT NULL,
    -- Tipo de contrato (Req 20.1): 'preventivo' o 'correctivo'.
    tipo                  VARCHAR(12)    NOT NULL,
    -- Tiempo de respuesta del SLA en horas (Req 20.1), estrictamente positivo.
    sla_respuesta_horas   INTEGER        NOT NULL,
    -- Tiempo de resolucion del SLA en horas (Req 20.1), estrictamente positivo.
    sla_resolucion_horas  INTEGER        NOT NULL,
    -- Baja logica del contrato (no destruye el historico de tickets).
    activo                BOOLEAN        NOT NULL DEFAULT TRUE,
    -- Columnas heredadas de TenantScopedEntity: concurrencia optimista (Req 49) y
    -- auditoria (Req 20.8).
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    CONSTRAINT pk_contrato_mantenimiento PRIMARY KEY (id),
    CONSTRAINT fk_contrato_mantenimiento_empresa
        FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Cliente existente (Req 20.1). Sin cascada (registro operativo).
    CONSTRAINT fk_contrato_mantenimiento_cliente
        FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    -- Tipo permitido (Req 20.1). Etiquetas ASCII minusculas.
    CONSTRAINT ck_contrato_mantenimiento_tipo
        CHECK (tipo IN ('preventivo', 'correctivo')),
    -- Tiempos del SLA estrictamente positivos (Req 20.1).
    CONSTRAINT ck_contrato_mantenimiento_sla_respuesta
        CHECK (sla_respuesta_horas > 0),
    CONSTRAINT ck_contrato_mantenimiento_sla_resolucion
        CHECK (sla_resolucion_horas > 0)
);

CREATE INDEX ix_contrato_mantenimiento_tenant_id
    ON contrato_mantenimiento (tenant_id);

-- Apoyo a la consulta de contratos por Cliente, acotada al tenant (Req 20.1, 20.7).
CREATE INDEX ix_contrato_mantenimiento_tenant_cliente
    ON contrato_mantenimiento (tenant_id, cliente_id);

-- ----------------------------------------------------------------------------
-- ticket_servicio
--   Incidencia/orden de servicio (Req 20.2). tenant-scoped (Req 23). Puede
--   generarse de forma manual (sin contrato) o automatica por mantenimiento
--   preventivo (con contrato). Estado inicial 'abierto' (Req 20.2); maquina de
--   estados abierto -> asignado -> en_proceso -> resuelto -> cerrado (Req 20.4).
--   Al pasar a 'resuelto' se evalua el cumplimiento del SLA contra su contrato,
--   si lo tiene (Req 20.6). cliente_id DENORMALIZADO (DECISION 1) para el filtro
--   del Req 20.7; asignado_id como referencia debil (DECISION 3).
-- ----------------------------------------------------------------------------
CREATE TABLE ticket_servicio (
    id                        UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                 UUID           NOT NULL,
    -- Contrato de SLA asociado; NULLABLE (DECISION 2): un ticket manual puede no
    -- tener contrato. La evaluacion del SLA (Req 20.6) solo aplica si existe.
    contrato_mantenimiento_id UUID,
    -- Cliente del ticket, denormalizado para el filtro del listado (Req 20.7,
    -- DECISION 1). FK a cliente; sin cascada.
    cliente_id                UUID           NOT NULL,
    -- Origen del ticket (Req 20.2): 'manual' o 'preventivo'.
    origen                    VARCHAR(12)    NOT NULL,
    -- Estado del ciclo de vida (Req 20.4). Inicial 'abierto' (Req 20.2).
    estado                    VARCHAR(12)    NOT NULL DEFAULT 'abierto',
    -- Asignacion (Req 20.3): tipo de destino y referencia debil al destino
    -- (DECISION 3). Ambos NULL mientras el ticket no este asignado.
    asignado_tipo             VARCHAR(10),
    asignado_id               UUID,
    -- Marca UTC de apertura del ticket (Req 20.6): base del calculo del SLA.
    abierto_en                TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- Marca UTC de resolucion del ticket (Req 20.6); NULL hasta que se resuelve.
    resuelto_en               TIMESTAMPTZ,
    -- Cumplimiento del SLA (Req 20.6). NULL hasta la resolucion; NULL siempre si el
    -- ticket no tiene Contrato_Mantenimiento asociado.
    sla_respuesta_cumplido    BOOLEAN,
    sla_resolucion_cumplido   BOOLEAN,
    -- Columnas heredadas de TenantScopedEntity (Req 49, 20.8).
    version                   BIGINT         NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by                VARCHAR(255),
    updated_by                VARCHAR(255),
    CONSTRAINT pk_ticket_servicio PRIMARY KEY (id),
    CONSTRAINT fk_ticket_servicio_empresa
        FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Contrato asociado (Req 20.2). Sin cascada; NULLABLE (DECISION 2).
    CONSTRAINT fk_ticket_servicio_contrato
        FOREIGN KEY (contrato_mantenimiento_id) REFERENCES contrato_mantenimiento (id),
    -- Cliente denormalizado (Req 20.7, DECISION 1). Sin cascada.
    CONSTRAINT fk_ticket_servicio_cliente
        FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    -- Origen permitido (Req 20.2).
    CONSTRAINT ck_ticket_servicio_origen
        CHECK (origen IN ('manual', 'preventivo')),
    -- Estados permitidos (Req 20.4). Etiquetas ASCII minusculas; 'en_proceso' sin
    -- acento por estabilidad de codificacion (DECISION 4).
    CONSTRAINT ck_ticket_servicio_estado
        CHECK (estado IN ('abierto', 'asignado', 'en_proceso', 'resuelto', 'cerrado')),
    -- Tipo de asignacion permitido (Req 20.3).
    CONSTRAINT ck_ticket_servicio_asignado_tipo
        CHECK (asignado_tipo IS NULL OR asignado_tipo IN ('tecnico', 'cuadrilla')),
    -- Coherencia de la asignacion (DECISION 3): tipo y destino van juntos.
    CONSTRAINT ck_ticket_servicio_asignacion
        CHECK ((asignado_tipo IS NULL AND asignado_id IS NULL)
            OR (asignado_tipo IS NOT NULL AND asignado_id IS NOT NULL))
);

CREATE INDEX ix_ticket_servicio_tenant_id
    ON ticket_servicio (tenant_id);

-- Apoyo a los filtros del listado (Req 20.7): por estado y por Cliente, siempre
-- acotados al tenant.
CREATE INDEX ix_ticket_servicio_tenant_estado
    ON ticket_servicio (tenant_id, estado);
CREATE INDEX ix_ticket_servicio_tenant_cliente
    ON ticket_servicio (tenant_id, cliente_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V25/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE contrato_mantenimiento ENABLE ROW LEVEL SECURITY;
ALTER TABLE contrato_mantenimiento FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON contrato_mantenimiento
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE ticket_servicio ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_servicio FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON ticket_servicio
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
