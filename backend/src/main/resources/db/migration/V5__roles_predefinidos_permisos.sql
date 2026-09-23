-- ============================================================================
-- V5__roles_predefinidos_permisos.sql
--
-- Semilla del catalogo de Permisos atomicos y de los Roles predefinidos del
-- Sistema (Tarea 10.2). Cubre:
--   - Req 3 (RBAC deny-by-default con permisos atomicos): catalogo de permisos
--     (recurso, operacion) y roles predefinidos de nivel empresa.
--   - Req 27 (modelo de roles y responsabilidades): rol de plataforma
--     `super_admin` (tenant_id NULL, ambito administracion de Empresas/Planes/
--     Suscripciones y NO datos de negocio) y roles de nivel empresa
--     `admin_empresa`, `gerente`, `supervisor`, `ventas`, `diseno`,
--     `produccion`, `almacen`, `instalacion`, `mantenimiento`, `contabilidad`,
--     `rh` y `marketing`.
--   - Req 28 (roles personalizables): los permisos atomicos que un
--     Rol_Personalizado de una Empresa puede combinar (NUNCA los permisos de
--     nivel plataforma reservados a `super_admin`).
--
-- ----------------------------------------------------------------------------
-- CONVENCIONES Y DECISIONES
-- ----------------------------------------------------------------------------
--   * Los roles predefinidos se insertan con `predefinido = TRUE` y
--     `tenant_id = NULL` (roles de Sistema comunes a todas las Empresas). El
--     indice parcial `uq_rol_nombre_predefinido` (V1) garantiza su unicidad
--     global por nombre.
--   * PERMISO DE PLATAFORMA vs DE EMPRESA (Req 27.7, 28.5): los recursos de
--     nivel plataforma son `empresa`, `plan`, `suscripcion` y `offboarding`.
--     Solo el rol `super_admin` recibe sus permisos. Ningun rol de empresa
--     predefinido los recibe, y la capa de aplicacion impide que un
--     Rol_Personalizado los incluya (ver ClasificadorRecursosPlataforma).
--   * UUIDs deterministas: se usan UUIDs fijos (v4 arbitrarios pero estables)
--     para los roles predefinidos, de modo que las asignaciones rol_permiso
--     sean reproducibles. Los permisos usan gen_random_uuid() por DEFAULT y se
--     enlazan por subconsulta (recurso, operacion), que es su clave natural
--     unica (uq_permiso_recurso_operacion, V1).
--   * Idempotencia razonable: la migracion Flyway se aplica una sola vez; aun
--     asi se emplea ON CONFLICT DO NOTHING en el catalogo de permisos y en los
--     roles para tolerar re-siembras manuales sin romper.
--   * `nombre` de rol sin caracteres acentuados (diseno/produccion/almacen/
--     instalacion) para mantener estabilidad de codificacion en la BD; la capa
--     de presentacion aplica la etiqueta visible.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) CATALOGO DE PERMISOS ATOMICOS (recurso, operacion)  -- Req 3.1, 28.1
-- ----------------------------------------------------------------------------
-- Se define un conjunto minimo pero representativo de permisos por recurso,
-- suficiente para modelar los alcances del Req 27. Nuevos recursos de modulos
-- futuros agregaran sus permisos en migraciones posteriores.

INSERT INTO permiso (recurso, operacion)
VALUES
    -- === Recursos de NIVEL PLATAFORMA (reservados a super_admin) ===========
    ('empresa',      'crear'),
    ('empresa',      'leer'),
    ('empresa',      'listar'),
    ('empresa',      'actualizar'),
    ('empresa',      'cambiar_estado'),
    ('plan',         'crear'),
    ('plan',         'leer'),
    ('plan',         'listar'),
    ('plan',         'actualizar'),
    ('suscripcion',  'crear'),
    ('suscripcion',  'leer'),
    ('suscripcion',  'listar'),
    ('suscripcion',  'actualizar'),
    ('suscripcion',  'cambiar_estado'),
    ('offboarding',  'exportar'),
    ('offboarding',  'cambiar_estado'),

    -- === Recursos de NIVEL EMPRESA =========================================
    -- Seguridad / administracion de la Empresa (admin_empresa)
    ('usuario',      'crear'),
    ('usuario',      'leer'),
    ('usuario',      'listar'),
    ('usuario',      'actualizar'),
    ('usuario',      'cambiar_estado'),
    ('rol',          'crear'),
    ('rol',          'leer'),
    ('rol',          'listar'),
    ('rol',          'actualizar'),
    ('rol',          'eliminar'),
    ('sesion',       'listar'),
    ('sesion',       'cambiar_estado'),
    ('branding',     'actualizar'),

    -- Comercial (ventas)
    ('cliente',      'crear'),
    ('cliente',      'leer'),
    ('cliente',      'listar'),
    ('cliente',      'actualizar'),
    ('contacto',     'crear'),
    ('contacto',     'leer'),
    ('contacto',     'listar'),
    ('contacto',     'actualizar'),
    ('oportunidad',  'crear'),
    ('oportunidad',  'leer'),
    ('oportunidad',  'listar'),
    ('oportunidad',  'actualizar'),
    ('oportunidad',  'cambiar_estado'),
    ('cotizacion',   'crear'),
    ('cotizacion',   'leer'),
    ('cotizacion',   'listar'),
    ('cotizacion',   'actualizar'),
    ('cotizacion',   'cambiar_estado'),
    ('proyecto',     'crear'),
    ('proyecto',     'leer'),
    ('proyecto',     'listar'),
    ('proyecto',     'actualizar'),
    ('bandeja',      'leer'),
    ('bandeja',      'actualizar'),
    ('conversacion', 'leer'),
    ('conversacion', 'actualizar'),

    -- Diseno
    ('prueba_diseno', 'crear'),
    ('prueba_diseno', 'leer'),
    ('prueba_diseno', 'listar'),
    ('prueba_diseno', 'cambiar_estado'),

    -- Produccion
    ('orden_fabricacion', 'crear'),
    ('orden_fabricacion', 'leer'),
    ('orden_fabricacion', 'listar'),
    ('orden_fabricacion', 'cambiar_estado'),

    -- Almacen / compras
    ('material',            'crear'),
    ('material',            'leer'),
    ('material',            'listar'),
    ('material',            'actualizar'),
    ('movimiento_inventario', 'crear'),
    ('movimiento_inventario', 'leer'),
    ('movimiento_inventario', 'listar'),
    ('proveedor',           'crear'),
    ('proveedor',           'leer'),
    ('proveedor',           'listar'),
    ('proveedor',           'actualizar'),
    ('requisicion_compra',  'crear'),
    ('requisicion_compra',  'leer'),
    ('requisicion_compra',  'listar'),
    ('requisicion_compra',  'cambiar_estado'),
    ('orden_compra',        'crear'),
    ('orden_compra',        'leer'),
    ('orden_compra',        'listar'),
    ('orden_compra',        'cambiar_estado'),
    ('recepcion_mercancia', 'crear'),
    ('recepcion_mercancia', 'leer'),
    ('recepcion_mercancia', 'listar'),
    ('factura_proveedor',   'crear'),
    ('factura_proveedor',   'leer'),
    ('factura_proveedor',   'listar'),
    ('producto',            'crear'),
    ('producto',            'leer'),
    ('producto',            'listar'),
    ('producto',            'actualizar'),
    ('lista_precios',       'crear'),
    ('lista_precios',       'leer'),
    ('lista_precios',       'listar'),
    ('lista_precios',       'actualizar'),
    ('almacen',             'crear'),
    ('almacen',             'leer'),
    ('almacen',             'listar'),
    ('lote',                'crear'),
    ('lote',                'leer'),
    ('lote',                'listar'),
    ('kardex',              'leer'),

    -- Instalacion
    ('levantamiento_sitio',       'crear'),
    ('levantamiento_sitio',       'leer'),
    ('levantamiento_sitio',       'listar'),
    ('levantamiento_sitio',       'cambiar_estado'),
    ('permiso_instalacion',       'crear'),
    ('permiso_instalacion',       'leer'),
    ('permiso_instalacion',       'listar'),
    ('permiso_instalacion',       'cambiar_estado'),
    ('orden_trabajo_instalacion', 'crear'),
    ('orden_trabajo_instalacion', 'leer'),
    ('orden_trabajo_instalacion', 'listar'),
    ('orden_trabajo_instalacion', 'cambiar_estado'),
    ('cuadrilla',                 'crear'),
    ('cuadrilla',                 'leer'),
    ('cuadrilla',                 'listar'),
    ('cuadrilla',                 'actualizar'),

    -- Mantenimiento
    ('contrato_mantenimiento', 'crear'),
    ('contrato_mantenimiento', 'leer'),
    ('contrato_mantenimiento', 'listar'),
    ('contrato_mantenimiento', 'actualizar'),
    ('ticket_servicio',        'crear'),
    ('ticket_servicio',        'leer'),
    ('ticket_servicio',        'listar'),
    ('ticket_servicio',        'cambiar_estado'),

    -- Contabilidad / finanzas
    ('factura',            'crear'),
    ('factura',            'leer'),
    ('factura',            'listar'),
    ('factura',            'cambiar_estado'),
    ('complemento_pago',   'crear'),
    ('complemento_pago',   'leer'),
    ('nota_credito',       'crear'),
    ('nota_credito',       'leer'),
    ('pago_cliente',       'crear'),
    ('pago_cliente',       'leer'),
    ('pago_cliente',       'listar'),
    ('cuenta_contable',    'crear'),
    ('cuenta_contable',    'leer'),
    ('cuenta_contable',    'listar'),
    ('poliza_contable',    'crear'),
    ('poliza_contable',    'leer'),
    ('poliza_contable',    'listar'),
    ('cuenta_por_pagar',   'leer'),
    ('cuenta_por_pagar',   'listar'),
    ('programacion_pago',  'crear'),
    ('programacion_pago',  'leer'),
    ('cuenta_bancaria',    'crear'),
    ('cuenta_bancaria',    'leer'),
    ('cuenta_bancaria',    'listar'),
    ('conciliacion_bancaria', 'crear'),
    ('conciliacion_bancaria', 'leer'),
    ('activo_fijo',        'crear'),
    ('activo_fijo',        'leer'),
    ('activo_fijo',        'listar'),
    ('depreciacion',       'crear'),
    ('depreciacion',       'leer'),
    ('estado_financiero',  'leer'),
    ('estado_financiero',  'exportar'),
    ('reporte_financiero', 'leer'),
    ('reporte_financiero', 'exportar'),

    -- RH / nomina
    ('empleado',            'crear'),
    ('empleado',            'leer'),
    ('empleado',            'listar'),
    ('empleado',            'actualizar'),
    ('contrato_laboral',    'crear'),
    ('contrato_laboral',    'leer'),
    ('contrato_laboral',    'listar'),
    ('incidencia',          'crear'),
    ('incidencia',          'leer'),
    ('incidencia',          'listar'),
    ('nomina',              'crear'),
    ('nomina',              'leer'),
    ('nomina',              'cambiar_estado'),
    ('puesto',              'crear'),
    ('puesto',              'leer'),
    ('puesto',              'listar'),
    ('organigrama',         'leer'),
    ('evaluacion_desempeno', 'crear'),
    ('evaluacion_desempeno', 'leer'),

    -- Marketing / redes sociales
    ('cuenta_canal_social',  'crear'),
    ('cuenta_canal_social',  'leer'),
    ('cuenta_canal_social',  'listar'),
    ('publicacion_social',   'crear'),
    ('publicacion_social',   'leer'),
    ('publicacion_social',   'listar'),
    ('publicacion_social',   'cambiar_estado'),
    ('campana_publicitaria', 'crear'),
    ('campana_publicitaria', 'leer'),
    ('campana_publicitaria', 'listar'),
    ('analitica_social',     'leer'),

    -- Planeacion estrategica y presupuestos (gerente / admin_empresa)
    ('planeacion_estrategica', 'crear'),
    ('planeacion_estrategica', 'leer'),
    ('planeacion_estrategica', 'actualizar'),
    ('objetivo_estrategico',   'crear'),
    ('objetivo_estrategico',   'leer'),
    ('objetivo_estrategico',   'listar'),
    ('objetivo_estrategico',   'actualizar'),
    ('presupuesto',            'crear'),
    ('presupuesto',            'leer'),
    ('presupuesto',            'listar'),
    ('presupuesto',            'actualizar'),

    -- Transversal de lectura (gerente/supervisor): tableros y reportes
    ('reporte',  'leer'),
    ('reporte',  'exportar'),
    ('tablero',  'leer')
ON CONFLICT (recurso, operacion) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2) ROLES PREDEFINIDOS  -- Req 27.1
-- ----------------------------------------------------------------------------
-- UUIDs fijos para poder enlazar rol_permiso de forma reproducible.

INSERT INTO rol (id, tenant_id, nombre, predefinido) VALUES
    ('a0000000-0000-0000-0000-000000000001', NULL, 'super_admin',    TRUE),
    ('a0000000-0000-0000-0000-000000000002', NULL, 'admin_empresa',  TRUE),
    ('a0000000-0000-0000-0000-000000000003', NULL, 'gerente',        TRUE),
    ('a0000000-0000-0000-0000-000000000004', NULL, 'supervisor',     TRUE),
    ('a0000000-0000-0000-0000-000000000005', NULL, 'ventas',         TRUE),
    ('a0000000-0000-0000-0000-000000000006', NULL, 'diseno',         TRUE),
    ('a0000000-0000-0000-0000-000000000007', NULL, 'produccion',     TRUE),
    ('a0000000-0000-0000-0000-000000000008', NULL, 'almacen',        TRUE),
    ('a0000000-0000-0000-0000-000000000009', NULL, 'instalacion',    TRUE),
    ('a0000000-0000-0000-0000-00000000000a', NULL, 'mantenimiento',  TRUE),
    ('a0000000-0000-0000-0000-00000000000b', NULL, 'contabilidad',   TRUE),
    ('a0000000-0000-0000-0000-00000000000c', NULL, 'rh',             TRUE),
    ('a0000000-0000-0000-0000-00000000000d', NULL, 'marketing',      TRUE)
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3) ASIGNACIONES rol_permiso
-- ----------------------------------------------------------------------------
-- Se usa un patron uniforme: por cada rol se selecciona el subconjunto de
-- permisos (por recurso) y se enlaza. La subconsulta hace el JOIN por la clave
-- natural (recurso, operacion) para no depender de los UUID de permiso.

-- 3.1) super_admin -> SOLO recursos de nivel plataforma (Req 27.7, 24.3).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000001', p.id
FROM permiso p
WHERE p.recurso IN ('empresa', 'plan', 'suscripcion', 'offboarding')
ON CONFLICT DO NOTHING;

-- 3.2) admin_empresa -> gestion de Usuarios/Roles/Sesiones/branding de SU
--      Empresa (Req 27.10, 3.4) + planeacion estrategica y presupuestos
--      (Req 27.14). NO recibe permisos de plataforma.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000002', p.id
FROM permiso p
WHERE p.recurso IN (
        'usuario', 'rol', 'sesion', 'branding',
        'planeacion_estrategica', 'objetivo_estrategico', 'presupuesto',
        'reporte', 'tablero')
ON CONFLICT DO NOTHING;

-- 3.3) gerente -> lectura transversal de todos los modulos, reportes y
--      tableros, y aprobaciones (cambiar_estado de cotizacion), sin gestionar
--      Usuarios ni Roles (Req 27.8, 27.14).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000003', p.id
FROM permiso p
WHERE (
        p.operacion IN ('leer', 'listar', 'exportar')
        AND p.recurso NOT IN ('empresa', 'plan', 'suscripcion', 'offboarding',
                              'usuario', 'rol', 'sesion')
      )
   OR (p.recurso = 'cotizacion' AND p.operacion = 'cambiar_estado')
   OR (p.recurso IN ('planeacion_estrategica', 'objetivo_estrategico', 'presupuesto'))
ON CONFLICT DO NOTHING;

-- 3.4) supervisor -> solo lectura acotada (mas limitado que gerente): lectura
--      de reportes y tableros y consulta general, sin aprobaciones globales
--      (Req 27.9).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000004', p.id
FROM permiso p
WHERE p.operacion IN ('leer', 'listar')
  AND p.recurso IN ('reporte', 'tablero', 'cliente', 'cotizacion', 'oportunidad',
                    'orden_fabricacion', 'orden_trabajo_instalacion',
                    'ticket_servicio', 'material', 'movimiento_inventario')
ON CONFLICT DO NOTHING;

-- 3.5) ventas -> Clientes, Contactos, Oportunidades, Cotizaciones, Proyectos y
--      atencion de Bandeja/Conversacion (Req 27.2).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000005', p.id
FROM permiso p
WHERE p.recurso IN ('cliente', 'contacto', 'oportunidad', 'cotizacion',
                    'proyecto', 'bandeja', 'conversacion',
                    'producto', 'lista_precios')
ON CONFLICT DO NOTHING;

-- 3.6) diseno -> Prueba_Diseno (Req 27.3).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000006', p.id
FROM permiso p
WHERE p.recurso = 'prueba_diseno'
ON CONFLICT DO NOTHING;

-- 3.7) produccion -> Orden_Fabricacion (Req 27.4).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000007', p.id
FROM permiso p
WHERE p.recurso = 'orden_fabricacion'
ON CONFLICT DO NOTHING;

-- 3.8) almacen -> inventario, compras, catalogo y Kardex (Req 27.5).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000008', p.id
FROM permiso p
WHERE p.recurso IN ('material', 'movimiento_inventario', 'proveedor',
                    'requisicion_compra', 'orden_compra', 'recepcion_mercancia',
                    'factura_proveedor', 'producto', 'lista_precios', 'almacen',
                    'lote', 'kardex')
ON CONFLICT DO NOTHING;

-- 3.9) instalacion -> Levantamiento, Permiso_Instalacion, OTI y Cuadrilla
--      (Req 27.6).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000009', p.id
FROM permiso p
WHERE p.recurso IN ('levantamiento_sitio', 'permiso_instalacion',
                    'orden_trabajo_instalacion', 'cuadrilla')
ON CONFLICT DO NOTHING;

-- 3.10) mantenimiento -> Contrato_Mantenimiento y Ticket_Servicio (Req 27.7 -> 27.7 area).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000a', p.id
FROM permiso p
WHERE p.recurso IN ('contrato_mantenimiento', 'ticket_servicio')
ON CONFLICT DO NOTHING;

-- 3.11) contabilidad -> facturacion, pagos, contabilidad, tesoreria, activos y
--       reportes financieros (Req 27.11).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000b', p.id
FROM permiso p
WHERE p.recurso IN ('factura', 'complemento_pago', 'nota_credito',
                    'pago_cliente', 'cuenta_contable', 'poliza_contable',
                    'cuenta_por_pagar', 'programacion_pago', 'cuenta_bancaria',
                    'conciliacion_bancaria', 'activo_fijo', 'depreciacion',
                    'estado_financiero', 'reporte_financiero')
ON CONFLICT DO NOTHING;

-- 3.12) rh -> Empleado, Contrato_Laboral, Incidencia, Nomina, Puesto,
--       organigrama y Evaluacion_Desempeno (Req 27.12).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000c', p.id
FROM permiso p
WHERE p.recurso IN ('empleado', 'contrato_laboral', 'incidencia', 'nomina',
                    'puesto', 'organigrama', 'evaluacion_desempeno')
ON CONFLICT DO NOTHING;

-- 3.13) marketing -> Cuenta_Canal_Social, Publicacion_Social, Campana,
--       Bandeja/Conversacion y analitica social (Req 27.15).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000d', p.id
FROM permiso p
WHERE p.recurso IN ('cuenta_canal_social', 'publicacion_social',
                    'campana_publicitaria', 'bandeja', 'conversacion',
                    'analitica_social')
ON CONFLICT DO NOTHING;
