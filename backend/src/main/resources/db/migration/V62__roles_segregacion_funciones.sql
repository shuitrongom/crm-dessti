-- ============================================================================
-- V62__roles_segregacion_funciones.sql
--
-- Segregacion de funciones (SoD) y roles de aprobacion de nivel EMPRESA. Amplia
-- el modelo RBAC de V5/V45/V47 para separar QUIEN opera de QUIEN aprueba:
--   * Los roles OPERATIVOS realizan el trabajo (crear/leer/actualizar) pero
--     dejan de poder AUTO-aprobar: se les retiran los permisos de aprobacion
--     ('cambiar_estado') y de aplicacion de pago ('aplicar_pago') que antes
--     concentraban (Req 3, RBAC minimo privilegio; Req 27 responsabilidades).
--   * Se agregan roles GERENTE/APROBADOR dedicados que concentran esos permisos
--     de aprobacion, ademas de un rol transversal `director` de lectura y
--     aprobaciones estrategicas.
--
-- Cada rol nuevo queda condicionado (gating) al/los modulo(s) contratado(s) por
-- la Empresa a traves del catalogo de dominio RolModuloCatalogo (fuente unica de
-- verdad de la relacion rol->modulo). El rol `director` es TRANSVERSAL (sin
-- modulo). El rol de plataforma `super_admin` NUNCA se ofrece.
--
-- ----------------------------------------------------------------------------
-- CONVENCIONES Y DECISIONES
-- ----------------------------------------------------------------------------
--   * UUIDs deterministas continuando la familia de V5/V45/V47. El ultimo
--     sembrado fue ...00000f (`calidad`, V47), por lo que la familia libre
--     arranca en ...000010. Se asignan de forma secuencial:
--       ...0010 director            (TRANSVERSAL, sin modulo)
--       ...0011 gerente_comercial   (modulo: comercial)
--       ...0012 gerente_compras     (modulos: compras / inventario-avanzado / operacion)
--       ...0013 contador_general    (modulos: contabilidad / facturacion)
--       ...0014 tesorero            (modulo: tesoreria)
--       ...0015 gerente_rh          (modulo: rh-nomina)
--       ...0016 gerente_operaciones (modulos: operacion / mantenimiento)
--       ...0017 gerente_calidad     (modulo: operacion -- ver DECISION 3)
--   * Roles predefinidos: tenant_id NULL, predefinido TRUE. La unicidad global
--     por nombre la garantiza el indice parcial uq_rol_nombre_predefinido (V1).
--   * Idempotencia: los INSERT usan ON CONFLICT DO NOTHING; los enlaces
--     rol_permiso se hacen por la clave natural (recurso, operacion) via
--     subconsulta (nunca por UUID de permiso). Los DELETE de PART 1 son
--     idempotentes por naturaleza (si el permiso ya no esta enlazado, no borran).
--   * NO se inventan operaciones nuevas: solo se enlazan pares (recurso,
--     operacion) YA existentes en el catalogo (V5/V30/V33/V37/V44/V47).
--   * `poliza_contable` NO tiene 'cambiar_estado' (solo crear/leer/listar): la
--     "aprobacion/registro" de polizas del contador_general se modela como
--     poliza_contable:{crear,leer,listar} (DECISION 4).
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. SEGREGACION DE APROBACION (SoD, Req 3/27): un rol operativo que CREA un
--      documento no debe poder APROBARLO (cambiar su estado) ni APLICAR su pago.
--      Se retira 'cambiar_estado'/'aplicar_pago' de los roles operativos y se
--      reasigna a un rol gerente/aprobador dedicado. Se CONSERVAN en el rol
--      operativo las operaciones crear/leer/listar/actualizar necesarias para
--      operar.
--   2. GERENTE GENERAL INTACTO (Req 27.8): el rol `gerente` (...0003) se deja
--      COMO ESTA. Ya ostenta lectura transversal + cotizacion:cambiar_estado y
--      actua como director-lite general. Los nuevos roles gerente_* dan
--      granularidad fina a organizaciones grandes sin quitarle nada a `gerente`.
--   3. MODULO DE CALIDAD: el catalogo de modulos contratables (V22
--      catalogo_modulo) NO define una clave `calidad`; los recursos de calidad
--      (V47) se consideran parte del vertical de OPERACION. Por ello
--      `gerente_calidad` y el ya existente `calidad` (V47) se condicionan al
--      modulo 'operacion'. (Si en el futuro se agrega una clave de modulo
--      'calidad' al catalogo, bastara con ampliar RolModuloCatalogo.)
--   4. APROBACION DE POLIZAS (Req 27.11): poliza_contable no expone
--      'cambiar_estado' en el catalogo; se modela el alcance del contador_general
--      con poliza_contable:{crear,leer,listar}.
--   5. PUBLICACION SOCIAL (Req 27.15): marketing redacta pero no publica; la
--      aprobacion de publicacion (publicacion_social:cambiar_estado) se asigna al
--      rol operativo de aprobacion transversal de operaciones
--      (gerente_operaciones) por ahora, evitando crear un rol dedicado adicional.
-- ============================================================================


-- ############################################################################
-- PART 1 -- REFINAR ROLES OPERATIVOS (retirar permisos de aprobacion / pago)
-- ############################################################################
-- Cada DELETE retira SOLO el/los par(es) (recurso, operacion) de aprobacion,
-- dejando intactas las operaciones operativas (crear/leer/listar/actualizar).

-- 1.1) ventas (...0005): crea/edita cotizaciones y oportunidades pero NO las
--      aprueba. Se retira cotizacion:cambiar_estado y oportunidad:cambiar_estado.
DELETE FROM rol_permiso
WHERE rol_id = 'a0000000-0000-0000-0000-000000000005'
  AND permiso_id IN (
        SELECT id FROM permiso
        WHERE (recurso = 'cotizacion'  AND operacion = 'cambiar_estado')
           OR (recurso = 'oportunidad' AND operacion = 'cambiar_estado'));

-- 1.2) almacen (...0008): crea requisiciones y ordenes de compra pero NO las
--      aprueba (aprobacion -> gerente_compras). Se CONSERVA recepcion_mercancia
--      (recepcion es operativa) y movimiento_inventario.
DELETE FROM rol_permiso
WHERE rol_id = 'a0000000-0000-0000-0000-000000000008'
  AND permiso_id IN (
        SELECT id FROM permiso
        WHERE (recurso = 'requisicion_compra' AND operacion = 'cambiar_estado')
           OR (recurso = 'orden_compra'       AND operacion = 'cambiar_estado'));

-- 1.3) contabilidad (...000b): registra facturas y CxP pero NO aprueba ni aplica
--      pagos (aprobacion -> contador_general; pago -> tesorero). Se retiran
--      factura:cambiar_estado, factura_proveedor:cambiar_estado,
--      nota_credito:cambiar_estado y cuenta_por_pagar:aplicar_pago. Se CONSERVAN
--      crear/leer/listar de factura, poliza, nota_credito, etc.
DELETE FROM rol_permiso
WHERE rol_id = 'a0000000-0000-0000-0000-00000000000b'
  AND permiso_id IN (
        SELECT id FROM permiso
        WHERE (recurso = 'factura'           AND operacion = 'cambiar_estado')
           OR (recurso = 'factura_proveedor' AND operacion = 'cambiar_estado')
           OR (recurso = 'nota_credito'      AND operacion = 'cambiar_estado')
           OR (recurso = 'cuenta_por_pagar'  AND operacion = 'aplicar_pago'));

-- 1.4) rh (...000c): prepara la nomina pero NO la aprueba (aprobacion ->
--      gerente_rh). Se retira nomina:cambiar_estado.
DELETE FROM rol_permiso
WHERE rol_id = 'a0000000-0000-0000-0000-00000000000c'
  AND permiso_id IN (
        SELECT id FROM permiso
        WHERE recurso = 'nomina' AND operacion = 'cambiar_estado');

-- 1.5) diseno (...0006): entrega la prueba de diseno pero NO la aprueba
--      (aprobacion -> gerente_operaciones). Se conserva crear/leer/listar.
DELETE FROM rol_permiso
WHERE rol_id = 'a0000000-0000-0000-0000-000000000006'
  AND permiso_id IN (
        SELECT id FROM permiso
        WHERE recurso = 'prueba_diseno' AND operacion = 'cambiar_estado');

-- 1.6) produccion (...0007): opera ordenes de fabricacion pero NO las aprueba
--      (aprobacion -> gerente_operaciones). Se conserva crear/leer/listar.
DELETE FROM rol_permiso
WHERE rol_id = 'a0000000-0000-0000-0000-000000000007'
  AND permiso_id IN (
        SELECT id FROM permiso
        WHERE recurso = 'orden_fabricacion' AND operacion = 'cambiar_estado');

-- 1.7) instalacion (...0009): opera levantamientos, permisos y OTI pero NO los
--      aprueba (aprobacion -> gerente_operaciones). Se conserva crear/leer/listar
--      y cuadrilla.
DELETE FROM rol_permiso
WHERE rol_id = 'a0000000-0000-0000-0000-000000000009'
  AND permiso_id IN (
        SELECT id FROM permiso
        WHERE (recurso = 'levantamiento_sitio'       AND operacion = 'cambiar_estado')
           OR (recurso = 'permiso_instalacion'       AND operacion = 'cambiar_estado')
           OR (recurso = 'orden_trabajo_instalacion' AND operacion = 'cambiar_estado'));

-- 1.8) mantenimiento (...000a): opera tickets de servicio pero NO los aprueba
--      (aprobacion -> gerente_operaciones). Se conserva crear/leer/listar y
--      contrato_mantenimiento.
DELETE FROM rol_permiso
WHERE rol_id = 'a0000000-0000-0000-0000-00000000000a'
  AND permiso_id IN (
        SELECT id FROM permiso
        WHERE recurso = 'ticket_servicio' AND operacion = 'cambiar_estado');

-- 1.9) marketing (...000d): redacta publicaciones pero NO las publica/aprueba
--      (aprobacion -> gerente_operaciones, DECISION 5). Se conserva
--      crear/leer/listar.
DELETE FROM rol_permiso
WHERE rol_id = 'a0000000-0000-0000-0000-00000000000d'
  AND permiso_id IN (
        SELECT id FROM permiso
        WHERE recurso = 'publicacion_social' AND operacion = 'cambiar_estado');

-- 1.10) calidad (...000f): opera el SGC pero NO aprueba (aprobacion ->
--       gerente_calidad). Se retira 'cambiar_estado' de los recursos del SGC. Se
--       conserva crear/leer/listar de todos ellos y calidad:leer.
DELETE FROM rol_permiso
WHERE rol_id = 'a0000000-0000-0000-0000-00000000000f'
  AND permiso_id IN (
        SELECT id FROM permiso
        WHERE operacion = 'cambiar_estado'
          AND recurso IN ('no_conformidad', 'accion_correctiva', 'oportunidad_calidad',
                          'cambio_sgc', 'riesgo'));


-- ############################################################################
-- PART 2 -- NUEVOS ROLES APROBADORES / GERENTES (siembra + rol_permiso)
-- ############################################################################

-- 2.0) Semilla de los roles (tenant_id NULL, predefinido TRUE). UUIDs desde
--      ...0010. Idempotente.
INSERT INTO rol (id, tenant_id, nombre, predefinido) VALUES
    ('a0000000-0000-0000-0000-000000000010', NULL, 'director',            TRUE),
    ('a0000000-0000-0000-0000-000000000011', NULL, 'gerente_comercial',   TRUE),
    ('a0000000-0000-0000-0000-000000000012', NULL, 'gerente_compras',     TRUE),
    ('a0000000-0000-0000-0000-000000000013', NULL, 'contador_general',    TRUE),
    ('a0000000-0000-0000-0000-000000000014', NULL, 'tesorero',            TRUE),
    ('a0000000-0000-0000-0000-000000000015', NULL, 'gerente_rh',          TRUE),
    ('a0000000-0000-0000-0000-000000000016', NULL, 'gerente_operaciones', TRUE),
    ('a0000000-0000-0000-0000-000000000017', NULL, 'gerente_calidad',     TRUE)
ON CONFLICT DO NOTHING;

-- 2.1) director (...0010) -- TRANSVERSAL (sin modulo). Lectura transversal de
--      todos los modulos de negocio (leer/listar/exportar) MAS aprobaciones
--      estrategicas: presupuesto, objetivo_estrategico, planeacion_estrategica,
--      inteligencia de negocio y exportacion financiera/de reportes. NO gestiona
--      Usuarios/Roles/Sesiones ni recursos de plataforma (empresa/plan/
--      suscripcion/offboarding). Perfil "CEO/CFO: lectura + estrategia".
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000010', p.id
FROM permiso p
WHERE (
        p.operacion IN ('leer', 'listar', 'exportar')
        AND p.recurso NOT IN ('empresa', 'plan', 'suscripcion', 'offboarding',
                              'usuario', 'rol', 'sesion')
      )
   OR (p.recurso IN ('planeacion_estrategica', 'objetivo_estrategico', 'presupuesto'))
   OR (p.recurso = 'inteligencia_negocio' AND p.operacion IN ('exportar', 'gestionar'))
ON CONFLICT DO NOTHING;

-- 2.2) gerente_comercial (...0011) -- modulo: comercial. Aprueba cotizaciones y
--      oportunidades (cambiar_estado) y lee lo necesario para revisarlas.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000011', p.id
FROM permiso p
WHERE (p.recurso = 'cotizacion'  AND p.operacion = 'cambiar_estado')
   OR (p.recurso = 'oportunidad' AND p.operacion = 'cambiar_estado')
   OR (p.recurso IN ('cliente', 'contacto', 'oportunidad', 'cotizacion',
                     'proyecto', 'producto', 'lista_precios')
       AND p.operacion IN ('leer', 'listar'))
ON CONFLICT DO NOTHING;

-- 2.3) gerente_compras (...0012) -- modulos: compras / inventario-avanzado /
--      operacion. Aprueba requisiciones y ordenes de compra (cambiar_estado) y
--      lee el ciclo de compras.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000012', p.id
FROM permiso p
WHERE (p.recurso = 'requisicion_compra' AND p.operacion = 'cambiar_estado')
   OR (p.recurso = 'orden_compra'       AND p.operacion = 'cambiar_estado')
   OR (p.recurso IN ('proveedor', 'requisicion_compra', 'orden_compra',
                     'recepcion_mercancia', 'factura_proveedor', 'material')
       AND p.operacion IN ('leer', 'listar'))
ON CONFLICT DO NOTHING;

-- 2.4) contador_general (...0013) -- modulos: contabilidad / facturacion.
--      Aprueba facturas (propias y de proveedor), gestiona notas de credito y
--      registra polizas (poliza no tiene cambiar_estado, DECISION 4). Lee
--      catalogo de cuentas y exporta estados/reportes financieros.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000013', p.id
FROM permiso p
WHERE (p.recurso = 'factura'           AND p.operacion = 'cambiar_estado')
   OR (p.recurso = 'factura_proveedor' AND p.operacion = 'cambiar_estado')
   OR (p.recurso = 'nota_credito'      AND p.operacion IN ('crear', 'leer'))
   OR (p.recurso = 'poliza_contable'   AND p.operacion IN ('crear', 'leer', 'listar'))
   OR (p.recurso = 'cuenta_contable'   AND p.operacion IN ('leer', 'listar'))
   OR (p.recurso = 'estado_financiero'  AND p.operacion IN ('leer', 'exportar'))
   OR (p.recurso = 'reporte_financiero' AND p.operacion IN ('leer', 'exportar'))
ON CONFLICT DO NOTHING;

-- 2.5) tesorero (...0014) -- modulo: tesoreria. Autoriza/aplica pagos
--      (cuenta_por_pagar:aplicar_pago), gestiona programacion de pagos,
--      conciliacion y cuentas bancarias. SoD frente a contador_general: quien
--      aprueba la factura NO es quien aplica el pago.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000014', p.id
FROM permiso p
WHERE (p.recurso = 'cuenta_por_pagar'      AND p.operacion IN ('leer', 'listar', 'aplicar_pago'))
   OR (p.recurso = 'programacion_pago'     AND p.operacion IN ('crear', 'leer', 'listar'))
   OR (p.recurso = 'conciliacion_bancaria' AND p.operacion IN ('crear', 'leer'))
   OR (p.recurso = 'cuenta_bancaria'       AND p.operacion IN ('crear', 'leer', 'listar'))
   OR (p.recurso = 'pago_cliente'          AND p.operacion IN ('leer', 'listar'))
ON CONFLICT DO NOTHING;

-- 2.6) gerente_rh (...0015) -- modulo: rh-nomina. Aprueba la nomina
--      (nomina:cambiar_estado) y lee el expediente de personal.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000015', p.id
FROM permiso p
WHERE (p.recurso = 'nomina' AND p.operacion = 'cambiar_estado')
   OR (p.recurso IN ('empleado', 'contrato_laboral', 'incidencia', 'nomina',
                     'puesto', 'evaluacion_desempeno', 'organigrama')
       AND p.operacion IN ('leer', 'listar'))
ON CONFLICT DO NOTHING;

-- 2.7) gerente_operaciones (...0016) -- modulos: operacion / mantenimiento.
--      Aprueba las ordenes de trabajo operativas (fabricacion, prueba de diseno,
--      levantamiento, permiso de instalacion, OTI, ticket de servicio) y publica
--      redes sociales (DECISION 5). Lee esos recursos y la cuadrilla.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000016', p.id
FROM permiso p
WHERE (p.recurso IN ('orden_fabricacion', 'prueba_diseno', 'levantamiento_sitio',
                     'permiso_instalacion', 'orden_trabajo_instalacion',
                     'ticket_servicio', 'publicacion_social')
       AND p.operacion = 'cambiar_estado')
   OR (p.recurso IN ('orden_fabricacion', 'prueba_diseno', 'levantamiento_sitio',
                     'permiso_instalacion', 'orden_trabajo_instalacion',
                     'ticket_servicio', 'cuadrilla')
       AND p.operacion IN ('leer', 'listar'))
ON CONFLICT DO NOTHING;

-- 2.8) gerente_calidad (...0017) -- modulo: operacion (DECISION 3). Aprueba los
--      recursos del SGC (no_conformidad, accion_correctiva, oportunidad_calidad,
--      cambio_sgc, riesgo) y lee todo el modulo de calidad y las quejas.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000017', p.id
FROM permiso p
WHERE (p.recurso IN ('no_conformidad', 'accion_correctiva', 'oportunidad_calidad',
                     'cambio_sgc', 'riesgo')
       AND p.operacion = 'cambiar_estado')
   OR (p.recurso IN ('no_conformidad', 'accion_correctiva', 'oportunidad_calidad',
                     'cambio_sgc', 'riesgo', 'queja_cliente', 'contexto_organizacion')
       AND p.operacion IN ('leer', 'listar'))
   OR (p.recurso = 'calidad' AND p.operacion = 'leer')
ON CONFLICT DO NOTHING;
