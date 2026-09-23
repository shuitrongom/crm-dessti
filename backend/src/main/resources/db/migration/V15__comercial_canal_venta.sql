-- ============================================================================
-- V15__comercial_canal_venta.sql
--
-- Submodulo `canal de venta y clasificacion comercial` del modulo comercial-crm
-- (Tarea 17.3, Req 63, 23). Introduce el catalogo tenant-scoped `canal_venta`
-- (por ejemplo: directo, referido, en linea) y agrega la clasificacion OPCIONAL
-- por canal de venta a las Oportunidades (V13) y a las Cotizaciones (V14),
-- para su analisis (Req 63.1) y su posterior segmentacion en reportes
-- comerciales (Req 63.2). La auditoria de la asignacion/modificacion del canal
-- (Req 63.3) la realiza la capa de aplicacion.
--
-- Replica EXACTAMENTE el patron reutilizable establecido en
-- V11__comercial_cliente_contacto.sql, V12__comercial_producto_listas_precios.sql,
-- V13__comercial_oportunidad.sql y V14__comercial_cotizacion.sql para toda tabla
-- tenant-scoped:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql.
--
-- Requisitos cubiertos:
--   - Req 63.1 (clasificar Oportunidades y Cotizaciones por canal de venta):
--     catalogo `canal_venta` + columna NULLABLE `canal_venta_id` en `oportunidad`
--     y `cotizacion`, con FK -> canal_venta. Es NULLABLE porque la clasificacion
--     es OPCIONAL: una Oportunidad/Cotizacion puede no tener canal asignado.
--   - Req 63.2 (segmentar reportes comerciales por canal): se persiste y se
--     indexa `canal_venta_id` en ambas tablas para que los reportes de los
--     bloques 44/48 (Req 22, 48) puedan segmentar/agrupar por canal. El reporte
--     en si NO se construye aqui: 17.3 solo garantiza que el dato de
--     clasificacion queda almacenado y consultable.
--   - Req 63.3 (auditoria de asignacion/modificacion del canal): la registra la
--     aplicacion (ServicioOportunidades/ServicioCotizaciones), no la BD.
--   - Req 23 (multi-tenant): tenant_id + RLS por tabla; unicidad de negocio POR
--     TENANT (23.6, nombre del canal unico dentro de la Empresa entre activos).
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con Cliente/Producto/Oportunidad/Cotizacion en V11..V14.
--   2. BORRADO LOGICO (coherente con cliente/producto): columna activo BOOLEAN
--      NOT NULL DEFAULT TRUE; la baja conserva el historico y libera el nombre
--      frente al indice unico parcial.
--   3. UNICIDAD DEL NOMBRE POR TENANT ENTRE ACTIVOS (Req 23.6): INDICE UNICO
--      PARCIAL sobre (tenant_id, LOWER(nombre)) WHERE activo = TRUE, replicando
--      el patron de uq_cliente_rfc_activo_por_tenant (V11) pero sin distinguir
--      mayusculas. La capa de aplicacion comprueba la existencia previa y
--      traduce la violacion del indice a HTTP 409 ante carreras concurrentes.
--   4. FK NULLABLE canal_venta_id EN oportunidad Y cotizacion (Req 63.1): un
--      ALTER TABLE ADD COLUMN de una columna NULLABLE NO altera ni reevalua las
--      politicas RLS ya existentes de esas tablas (V13/V14): la politica
--      tenant_isolation sigue aplicando sobre la fila completa. La FK garantiza
--      integridad referencial dentro del tenant (ambas tablas comparten tenant).
--      No se declara ON DELETE CASCADE: el borrado del canal es LOGICO
--      (activo=false), de modo que no rompe las Oportunidades/Cotizaciones que
--      lo referencian; a lo sumo quedan clasificadas por un canal inactivo, lo
--      que preserva el historico para los reportes (Req 63.2).
--   5. PERMISOS: V5 NO sembro permisos para el recurso `canal_venta`. Se agregan
--      canal_venta:{crear,leer,listar,actualizar,eliminar} al catalogo y se
--      enlazan a los roles predefinidos `ventas` (...005), `gerente` (...003) y
--      `marketing` (...00d), coherente con Req 27.2/27.8/27.15 (el area comercial
--      define y consume los canales; gerencia y marketing los consultan para el
--      analisis por canal). El guardado REST usa estos permisos con @PreAuthorize.
--      La ASIGNACION del canal a una Oportunidad/Cotizacion NO usa un permiso
--      propio: reutiliza el permiso de actualizacion del recurso afectado
--      ('oportunidad':'actualizar' / 'cotizacion':'actualizar'), pues es una
--      modificacion de esos recursos (documentado en los controladores).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- canal_venta
--   Catalogo de canales de venta de la Empresa (por ejemplo: directo, referido,
--   en linea) para clasificar Oportunidades y Cotizaciones (Req 63.1).
--   tenant-scoped (Req 23). Incluye borrado logico (activo) y version por
--   consistencia con el patron del modulo.
-- ----------------------------------------------------------------------------
CREATE TABLE canal_venta (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(500),
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_canal_venta PRIMARY KEY (id),
    CONSTRAINT fk_canal_venta_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id)
);

CREATE INDEX ix_canal_venta_tenant_id ON canal_venta (tenant_id);

-- Unicidad del nombre del canal POR TENANT unicamente entre canales ACTIVOS,
-- sin distinguir mayusculas (Req 23.6): permite reutilizar el nombre de un canal
-- dado de baja logica. Replica el patron de uq_cliente_rfc_activo_por_tenant (V11).
CREATE UNIQUE INDEX uq_canal_venta_nombre_activo_por_tenant
    ON canal_venta (tenant_id, LOWER(nombre))
    WHERE activo = TRUE;

COMMENT ON INDEX uq_canal_venta_nombre_activo_por_tenant IS
    'Unicidad del nombre del Canal_Venta POR TENANT entre activos, sin distinguir '
    'mayusculas (Req 23.6, 63.1). La aplicacion comprueba la existencia previa y '
    'traduce la violacion a HTTP 409 ante carreras concurrentes. Un canal inactivo '
    '(baja logica) libera su nombre.';

-- ----------------------------------------------------------------------------
-- Row-Level Security del catalogo (Capa 2, Req 23) -- patron replicado de
-- V11/V12/V13/V14/V2. ENABLE activa las politicas para roles normales; FORCE las
-- aplica tambien al dueno de la tabla (rol migrador). Sin app.current_tenant
-- fijada, ninguna fila tenant-scoped es visible ni modificable (deny-by-default).
-- ----------------------------------------------------------------------------
ALTER TABLE canal_venta ENABLE ROW LEVEL SECURITY;
ALTER TABLE canal_venta FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON canal_venta
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Clasificacion OPCIONAL por canal de venta en oportunidad (V13) y cotizacion
-- (V14) (Req 63.1). ADD COLUMN de una columna NULLABLE es una operacion aditiva
-- que NO perturba las politicas RLS ni los indices existentes de esas tablas.
-- La FK -> canal_venta es NULLABLE porque la clasificacion es opcional.
-- ----------------------------------------------------------------------------
ALTER TABLE oportunidad
    ADD COLUMN canal_venta_id UUID;

COMMENT ON COLUMN oportunidad.canal_venta_id IS
    'Canal de venta al que se clasifica la Oportunidad (Req 63.1). NULLABLE: la '
    'clasificacion es OPCIONAL. Se persiste e indexa para la segmentacion de '
    'reportes comerciales por canal (Req 63.2, bloques 44/48). La asignacion o '
    'modificacion se audita en la aplicacion (Req 63.3).';

ALTER TABLE oportunidad
    ADD CONSTRAINT fk_oportunidad_canal_venta
        FOREIGN KEY (canal_venta_id) REFERENCES canal_venta (id);

-- Indice de apoyo a la segmentacion/filtro por canal, acotado al tenant (Req 63.2).
CREATE INDEX ix_oportunidad_tenant_canal ON oportunidad (tenant_id, canal_venta_id);

ALTER TABLE cotizacion
    ADD COLUMN canal_venta_id UUID;

COMMENT ON COLUMN cotizacion.canal_venta_id IS
    'Canal de venta al que se clasifica la Cotizacion (Req 63.1). NULLABLE: la '
    'clasificacion es OPCIONAL. Se persiste e indexa para la segmentacion de '
    'reportes comerciales por canal (Req 63.2, bloques 44/48). La asignacion o '
    'modificacion se audita en la aplicacion (Req 63.3).';

ALTER TABLE cotizacion
    ADD CONSTRAINT fk_cotizacion_canal_venta
        FOREIGN KEY (canal_venta_id) REFERENCES canal_venta (id);

-- Indice de apoyo a la segmentacion/filtro por canal, acotado al tenant (Req 63.2).
CREATE INDEX ix_cotizacion_tenant_canal ON cotizacion (tenant_id, canal_venta_id);

-- ----------------------------------------------------------------------------
-- Permisos atomicos del recurso `canal_venta` (Req 3.1, 63.1). V5 no los sembro.
-- Se agregan al catalogo y se enlazan a los roles predefinidos `ventas` (...005),
-- `gerente` (...003) y `marketing` (...00d), coherente con Req 27.2/27.8/27.15.
-- El guardado REST con @PreAuthorize (CanalVentaController) usa estos permisos.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES ('canal_venta', 'crear'),
       ('canal_venta', 'leer'),
       ('canal_venta', 'listar'),
       ('canal_venta', 'actualizar'),
       ('canal_venta', 'eliminar')
ON CONFLICT (recurso, operacion) DO NOTHING;

-- ventas -> gestion completa del catalogo de canales (Req 27.2).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000005', p.id
FROM permiso p
WHERE p.recurso = 'canal_venta'
ON CONFLICT DO NOTHING;

-- gerente -> lectura del catalogo para el analisis por canal (Req 27.8).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000003', p.id
FROM permiso p
WHERE p.recurso = 'canal_venta' AND p.operacion IN ('leer', 'listar')
ON CONFLICT DO NOTHING;

-- marketing -> lectura del catalogo para la segmentacion social por canal
-- (Req 27.15, integracion con Req 66.2).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000d', p.id
FROM permiso p
WHERE p.recurso = 'canal_venta' AND p.operacion IN ('leer', 'listar')
ON CONFLICT DO NOTHING;
