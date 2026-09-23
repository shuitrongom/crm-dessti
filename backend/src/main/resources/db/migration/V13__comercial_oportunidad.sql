-- ============================================================================
-- V13__comercial_oportunidad.sql
--
-- Submodulo `oportunidades y pipeline de ventas` del modulo comercial-crm
-- (Tarea 17.1, Req 14, 23). Replica EXACTAMENTE el patron reutilizable
-- establecido en V11__comercial_cliente_contacto.sql y
-- V12__comercial_producto_listas_precios.sql para toda tabla tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql.
--
-- Requisitos cubiertos:
--   - Req 14.1 (alta con datos obligatorios y etapa inicial 'nuevo'): columnas
--     NOT NULL (cliente_id, titulo, valor_estimado, etapa) y etapa con DEFAULT
--     'nuevo'. La validacion fina de titulo (1..200) y valor la aplica el
--     dominio (422); la BD refuerza el rango del valor con un CHECK.
--   - Req 14.1 (valor estimado entre 0.01 y 999,999,999.99): valor_estimado
--     NUMERIC(18,2) con CHECK que refleja EXACTAMENTE el rango del dominio
--     (OportunidadValidaciones), coherente con precio_producto de V12.
--   - Req 14.2 (asignacion de responsable): responsable_usuario_id UUID nullable
--     con FK -> usuario (el Usuario de ventas responsable).
--   - Req 14.3/14.4 (etapas del pipeline y transiciones): etapa VARCHAR(20) con
--     CHECK IN ('nuevo','calificado','propuesta','negociacion','ganado',
--     'perdido'). Las etiquetas se almacenan en ASCII minusculas (sin acento en
--     'negociacion') por estabilidad de codificacion, coherente con V5 para los
--     nombres de rol; la maquina de estados pura vive en el dominio
--     (EtapaOportunidad).
--   - Req 14.5 (conversion 'ganado' -> Cotizacion): cotizacion_id UUID nullable,
--     que se rellena al convertir. Ver DECISION 3 sobre la ausencia de FK.
--   - Req 14.7/14.8 (listado paginado y filtros por cliente/etapa/responsable):
--     indices de apoyo a los filtros.
--   - Req 23 (multi-tenant): tenant_id + RLS por tabla.
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (lo hace la fabrica de dominio,
--      coherente con Cliente/Producto en V11/V12).
--   2. MONEDA: NUMERIC(18,2) para valor_estimado (convencion del proyecto,
--      BigDecimal escala 2 en Java). El CHECK acota al rango 0.01..999,999,999.99
--      (Req 14.1) y refleja la validacion del dominio, de modo que ambas
--      coinciden (como en precio_producto de V12).
--   3. cotizacion_id SIN FK: la tabla `cotizacion` la crea la tarea 17.2. Para
--      no acoplar V13 a una tabla inexistente ni requerir una migracion de
--      cambio en 17.2, se agrega ya la columna nullable `cotizacion_id` UUID
--      SIN restriccion de clave foranea. La tarea 17.2 podra anadir la FK (y, si
--      lo desea, la FK inversa cotizacion.oportunidad_id) en su propia migracion.
--      Asi la conversion (marcarConvertida) escribe esta columna sin cambios de
--      esquema en 17.1.
--   4. PERMISOS: V5 ya sembro oportunidad:{crear,leer,listar,actualizar,
--      cambiar_estado} y los asigno al rol `ventas` (UUID ...005, Req 27.2). Por
--      tanto V13 NO necesita sembrar permisos adicionales (a diferencia de V12,
--      que agrego 'producto:eliminar'). El guardado REST con @PreAuthorize usa
--      esos permisos existentes.
--   5. PERDIDA DE DATOS AL BORRAR CLIENTE/USUARIO: las FK a cliente y usuario no
--      declaran ON DELETE CASCADE; el borrado de Clientes es logico (activo=false)
--      y los Usuarios se desactivan, de modo que no se rompen las Oportunidades.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- oportunidad
--   Prospecto/lead de venta gestionado en el pipeline comercial antes de
--   convertirse en Cotizacion (Req 14). tenant-scoped (Req 23). Etapa inicial
--   'nuevo' (Req 14.1); etapas finales 'ganado'/'perdido' (Req 14.3/14.4). El
--   responsable (Usuario de ventas) es opcional hasta que se asigna (Req 14.2).
-- ----------------------------------------------------------------------------
CREATE TABLE oportunidad (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID          NOT NULL,
    cliente_id              UUID          NOT NULL,
    titulo                  VARCHAR(200)  NOT NULL,
    valor_estimado          NUMERIC(18, 2) NOT NULL,
    etapa                   VARCHAR(20)   NOT NULL DEFAULT 'nuevo',
    responsable_usuario_id  UUID,
    cotizacion_id           UUID,
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    CONSTRAINT pk_oportunidad PRIMARY KEY (id),
    CONSTRAINT fk_oportunidad_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_oportunidad_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_oportunidad_responsable FOREIGN KEY (responsable_usuario_id) REFERENCES usuario (id),
    -- Rango del valor estimado (Req 14.1): refleja EXACTAMENTE la validacion del
    -- dominio (OportunidadValidaciones). Un valor fuera de rango se rechaza.
    CONSTRAINT ck_oportunidad_valor_rango CHECK (valor_estimado >= 0.01 AND valor_estimado <= 999999999.99),
    -- Etapas permitidas del pipeline (Req 14.3). Etiquetas ASCII minusculas;
    -- 'negociacion' sin acento por estabilidad de codificacion.
    CONSTRAINT ck_oportunidad_etapa CHECK (
        etapa IN ('nuevo', 'calificado', 'propuesta', 'negociacion', 'ganado', 'perdido'))
);

CREATE INDEX ix_oportunidad_tenant_id ON oportunidad (tenant_id);

-- Apoyo a los filtros del listado (Req 14.7, 14.8): por Cliente, por etapa y por
-- Usuario responsable, siempre acotados al tenant.
CREATE INDEX ix_oportunidad_tenant_cliente ON oportunidad (tenant_id, cliente_id);
CREATE INDEX ix_oportunidad_tenant_etapa ON oportunidad (tenant_id, etapa);
CREATE INDEX ix_oportunidad_tenant_responsable ON oportunidad (tenant_id, responsable_usuario_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V11/V12/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE oportunidad ENABLE ROW LEVEL SECURITY;
ALTER TABLE oportunidad FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON oportunidad
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
