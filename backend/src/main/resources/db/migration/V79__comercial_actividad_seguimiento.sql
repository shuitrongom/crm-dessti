-- ============================================================================
-- V79__comercial_actividad_seguimiento.sql
--
-- Submodulo `actividades y seguimiento comercial` del modulo comercial-crm.
-- Incorpora la pieza operativa que faltaba en el CRM: el registro y la gestion
-- de las interacciones con el Cliente (llamadas, correos, reuniones, tareas y
-- notas), su calendario de seguimiento (fecha programada y vencimiento), su
-- responsable y su estado (pendiente/completada/cancelada), asi como el
-- historial cronologico (timeline) consultable por Cliente y por Oportunidad.
--
-- Replica EXACTAMENTE el patron reutilizable establecido en
-- V11__comercial_cliente_contacto.sql, V12__comercial_producto_listas_precios.sql
-- y V13__comercial_oportunidad.sql para toda tabla tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. VINCULO FLEXIBLE (Cliente obligatorio, Oportunidad opcional): toda
--      actividad pertenece a un Cliente (cliente_id NOT NULL, FK a cliente) y
--      OPCIONALMENTE a una Oportunidad de ese Cliente (oportunidad_id nullable,
--      FK a oportunidad). Asi el timeline se puede reconstruir tanto a nivel
--      Cliente (ficha 360) como a nivel Oportunidad (pipeline), sin duplicar
--      filas. Se prefiere este modelo de doble columna con FK real sobre un
--      vinculo polimorfico (tipo+id sin FK) para preservar integridad
--      referencial y aprovechar la RLS por tenant en ambas relaciones.
--   2. TIPO: VARCHAR(20) con CHECK IN ('llamada','correo','reunion','tarea',
--      'nota'). Etiquetas ASCII minusculas ('reunion' sin acento) por
--      estabilidad de codificacion, coherente con V5 (roles) y V13 (etapas). La
--      semantica de cada tipo vive en el dominio (TipoActividad).
--   3. ESTADO: VARCHAR(20) con CHECK IN ('pendiente','completada','cancelada').
--      La maquina de estados pura vive en el dominio (EstadoActividad):
--         pendiente -> completada | cancelada ; completada/cancelada finales.
--      Una NOTA nace 'completada' (es un registro de algo ya ocurrido), mientras
--      que llamada/correo/reunion/tarea nacen 'pendiente' (seguimiento futuro).
--      La BD no distingue el estado inicial por tipo (lo fija el dominio); solo
--      acota el conjunto de valores validos.
--   4. FECHAS: fecha_programada TIMESTAMPTZ NOT NULL (cuando ocurre/ocurrio la
--      interaccion o vence la tarea, en UTC) y vencimiento TIMESTAMPTZ nullable
--      (fecha limite opcional para tareas). completada_en TIMESTAMPTZ nullable
--      se sella al pasar a 'completada'. Todas en UTC (convencion del proyecto).
--   5. RESPONSABLE: responsable_usuario_id UUID nullable con FK -> usuario (el
--      Usuario a cargo del seguimiento). Sin ON DELETE CASCADE: los Usuarios se
--      desactivan, no se borran, de modo que no se rompen las Actividades.
--   6. CONTENIDO: asunto VARCHAR(200) NOT NULL (titulo breve) y descripcion
--      VARCHAR(4000) nullable (detalle libre / notas fechadas). La validacion
--      fina (asunto 1..200) la aplica el dominio (422); la BD refuerza longitud.
--   7. PERMISOS: se siembra el recurso 'actividad' con las operaciones
--      {crear,leer,listar,actualizar,eliminar} (no existia en V5) y se asigna a
--      los roles que operan el CRM: ventas (gestion completa) y admin_empresa;
--      gerente y supervisor reciben solo lectura/listado, coherente con su
--      alcance de solo lectura transversal (Req 27.8, 27.9). El guardado REST
--      con @PreAuthorize usa estos permisos.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- actividad_comercial
--   Interaccion o tarea de seguimiento comercial asociada a un Cliente y,
--   opcionalmente, a una Oportunidad. tenant-scoped (Req 23). Alimenta el
--   timeline de la ficha 360 del Cliente y del pipeline, y el tablero de tareas
--   pendientes por responsable.
-- ----------------------------------------------------------------------------
CREATE TABLE actividad_comercial (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID          NOT NULL,
    cliente_id              UUID          NOT NULL,
    oportunidad_id          UUID,
    tipo                    VARCHAR(20)   NOT NULL,
    estado                  VARCHAR(20)   NOT NULL DEFAULT 'pendiente',
    asunto                  VARCHAR(200)  NOT NULL,
    descripcion             VARCHAR(4000),
    fecha_programada        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    vencimiento             TIMESTAMPTZ,
    completada_en           TIMESTAMPTZ,
    responsable_usuario_id  UUID,
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    CONSTRAINT pk_actividad_comercial PRIMARY KEY (id),
    CONSTRAINT fk_actividad_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_actividad_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_actividad_oportunidad FOREIGN KEY (oportunidad_id) REFERENCES oportunidad (id),
    CONSTRAINT fk_actividad_responsable FOREIGN KEY (responsable_usuario_id) REFERENCES usuario (id),
    -- Tipos permitidos de interaccion (Decision 2). Etiquetas ASCII minusculas.
    CONSTRAINT ck_actividad_tipo CHECK (
        tipo IN ('llamada', 'correo', 'reunion', 'tarea', 'nota')),
    -- Estados permitidos (Decision 3). La maquina de estados vive en el dominio.
    CONSTRAINT ck_actividad_estado CHECK (
        estado IN ('pendiente', 'completada', 'cancelada')),
    -- Coherencia del sello de completado: solo las actividades 'completada'
    -- tienen completada_en; las demas lo mantienen nulo.
    CONSTRAINT ck_actividad_completada_en CHECK (
        (estado = 'completada' AND completada_en IS NOT NULL)
        OR (estado <> 'completada' AND completada_en IS NULL)),
    -- Si hay vencimiento, no puede ser anterior a la fecha programada.
    CONSTRAINT ck_actividad_vencimiento CHECK (
        vencimiento IS NULL OR vencimiento >= fecha_programada)
);

CREATE INDEX ix_actividad_tenant_id ON actividad_comercial (tenant_id);

-- Timeline por Cliente (ficha 360): actividades del Cliente ordenadas por fecha
-- programada descendente, acotadas al tenant.
CREATE INDEX ix_actividad_tenant_cliente_fecha
    ON actividad_comercial (tenant_id, cliente_id, fecha_programada DESC);

-- Timeline por Oportunidad (pipeline): actividades de la Oportunidad ordenadas
-- por fecha programada descendente, acotadas al tenant.
CREATE INDEX ix_actividad_tenant_oportunidad_fecha
    ON actividad_comercial (tenant_id, oportunidad_id, fecha_programada DESC);

-- Tablero de tareas pendientes por responsable: actividades pendientes de un
-- Usuario ordenadas por vencimiento, acotadas al tenant.
CREATE INDEX ix_actividad_tenant_responsable_estado
    ON actividad_comercial (tenant_id, responsable_usuario_id, estado);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V11/V12/V13/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE actividad_comercial ENABLE ROW LEVEL SECURITY;
ALTER TABLE actividad_comercial FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON actividad_comercial
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Permisos atomicos del recurso 'actividad' (Req 3.1). No existian en V5. Se
-- agregan al catalogo global de permisos y se enlazan a los roles que operan el
-- CRM. El guardado REST con @PreAuthorize los usa (recurso 'actividad').
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES
    ('actividad', 'crear'),
    ('actividad', 'leer'),
    ('actividad', 'listar'),
    ('actividad', 'actualizar'),
    ('actividad', 'eliminar')
ON CONFLICT (recurso, operacion) DO NOTHING;

-- ventas (Req 27.2) y admin_empresa (Req 27.10): gestion completa del CRM,
-- incluidas las actividades de seguimiento.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT rol_id, p.id
FROM permiso p
CROSS JOIN (VALUES
        ('a0000000-0000-0000-0000-000000000005'::uuid),  -- ventas
        ('a0000000-0000-0000-0000-000000000002'::uuid)   -- admin_empresa
    ) AS r(rol_id)
WHERE p.recurso = 'actividad'
  AND p.operacion IN ('crear', 'leer', 'listar', 'actualizar', 'eliminar')
ON CONFLICT DO NOTHING;

-- gerente (Req 27.8) y supervisor (Req 27.9): solo lectura/listado del historial
-- de actividades, coherente con su alcance de solo lectura transversal.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT rol_id, p.id
FROM permiso p
CROSS JOIN (VALUES
        ('a0000000-0000-0000-0000-000000000003'::uuid),  -- gerente
        ('a0000000-0000-0000-0000-000000000004'::uuid)   -- supervisor
    ) AS r(rol_id)
WHERE p.recurso = 'actividad'
  AND p.operacion IN ('leer', 'listar')
ON CONFLICT DO NOTHING;
