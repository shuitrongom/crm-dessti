-- ============================================================================
-- V58__admin_empresa_permisos_modulos_negocio.sql
--
-- Concede al rol predefinido `admin_empresa` (UUID fijo
-- a0000000-0000-0000-0000-000000000002, sembrado en V5) los permisos
-- OPERACIONALES de TODOS los modulos de negocio vendibles, de modo que el
-- administrador de una Empresa vea/acceda AUTOMATICAMENTE cualquier bloque que
-- la Empresa contrate.
--
-- ----------------------------------------------------------------------------
-- PROBLEMA / DECISION (confirmada con el usuario)
-- ----------------------------------------------------------------------------
--   Un elemento de menu (y su endpoint) exige DOS condiciones simultaneas:
--     1) que la Empresa TENGA CONTRATADO el modulo (gating por Plan,
--        `@autorizador.moduloHabilitado(...)` + el filtro de modulos del menu
--        que consume GET /empresa/modulos en vivo), y
--     2) que el Usuario posea el PERMISO ATOMICO del recurso (p. ej.
--        `cliente:listar`), evaluado por RBAC deny-by-default (Req 3).
--
--   Hasta V57 el `admin_empresa` solo tenia permisos de administracion de la
--   Empresa (usuario/rol/sesion/branding, V5), planeacion/presupuesto/reportes/
--   tableros (V5/V44), notificaciones (V42) y SOLO lectura de calidad (V47). Le
--   FALTABAN los permisos operacionales de comercial, operacion, inventario
--   avanzado, compras, facturacion, contabilidad, tesoreria, activos fijos,
--   rh-nomina, redes sociales y mantenimiento. Resultado: aunque la Empresa
--   contratara "comercial", el menu no lo mostraba porque el admin carecia de
--   `cliente:listar` (fallaba la condicion 2).
--
--   DECISION: el `admin_empresa` debe tener acceso AUTOMATICO a TODOS los
--   bloques contratados. Por ello se le concede la UNION de los permisos de
--   TODOS los roles predefinidos de nivel EMPRESA (operativos). El gating por
--   modulo (condicion 1) sigue restringiendo la VISIBILIDAD/ACCESO al conjunto
--   de modulos realmente contratado: tener el permiso NO basta para saltarse
--   `moduloHabilitado`. Efecto neto: el admin ve exactamente los bloques
--   contratados, con acceso pleno dentro de ellos; los demas roles (ventas,
--   contabilidad, etc.) siguen acotando al personal regular.
--
-- ----------------------------------------------------------------------------
-- ESTRATEGIA DE LA CONCESION (robusta y mantenible)
-- ----------------------------------------------------------------------------
--   En lugar de enumerar a mano una lista de recursos (fragil y facil de dejar
--   incompleta cuando se agregan modulos), se concede al `admin_empresa` la
--   UNION de TODOS los permisos ya asignados a los roles predefinidos de nivel
--   EMPRESA:
--       gerente, supervisor, ventas, diseno, produccion, almacen, instalacion,
--       mantenimiento, contabilidad, rh, marketing, calidad.
--
--   Esta union cubre por construccion CADA recurso de negocio (comercial,
--   operacion, inventario avanzado, compras, facturacion, contabilidad,
--   tesoreria, activos fijos, rh-nomina, redes sociales, mantenimiento y
--   calidad) con TODAS sus operaciones (crear/leer/listar/actualizar/
--   cambiar_estado/timbrar/exportar/etc.), incluidas las anadidas por
--   migraciones posteriores a V5 (V11..V47), sin hardcodear una lista que
--   pudiera quedar incompleta.
--
--   POR QUE ESTO EXCLUYE LOS RECURSOS DE PLATAFORMA (super_admin): los recursos
--   de plataforma (`empresa`, `plan`, `suscripcion`, `giro`, `offboarding`,
--   `modulo_catalogo`, `precio_modulo`, `moneda`, `factura_renta`, `respaldo`,
--   etc.) SOLO se asignan al rol `super_admin` (V5 3.1, V22, V23, V46, V50,
--   V56). Como NINGUN rol de empresa los tiene, la union NUNCA los incluye. El
--   `admin_empresa` sigue SIN poder crear Empresas, listar Planes ni ver
--   Facturas de Renta.
--
--   ROL EXTERNO `cliente_portal` (V45): se EXCLUYE explicitamente del origen de
--   la union. Es un rol EXTERNO del Portal del Cliente que, por diseno, NO tiene
--   filas en `rol_permiso` (autorizacion por hasRole, no por permisos atomicos),
--   por lo que no aportaria nada; se nombra la lista-blanca de roles internos
--   para dejar la intencion explicita y a prueba de futuros cambios.
--
--   GATING POR GIRO INTACTO: los recursos de la vertical de anuncios
--   (orden_fabricacion, prueba_diseno, levantamiento_sitio, permiso_instalacion,
--   orden_trabajo_instalacion, etc.) entran en la union porque pertenecen a
--   roles de empresa (produccion/diseno/instalacion). El control
--   `@autorizador.giroCorresponde('anuncios')` de esos controladores sigue
--   aplicando: un admin de una Empresa de otro Giro no accede a ellos aunque
--   tenga el permiso atomico.
--
-- ----------------------------------------------------------------------------
-- SEGURIDAD / ALCANCE
-- ----------------------------------------------------------------------------
--   * NO se modifican politicas RLS ni el esquema: solo se agregan filas en
--     `rol_permiso` (enlace N:M rol<->permiso, V1).
--   * Idempotente y NO destructiva: ON CONFLICT DO NOTHING sobre la PK
--     (rol_id, permiso_id). Re-aplicaciones manuales no rompen ni duplican.
--   * Enlace por SUBCONSULTA (no por UUID de permiso), replicando el patron de
--     V5 seccion 3.
--
-- Requisitos cubiertos:
--   - Req 3 (RBAC): el admin_empresa recibe los permisos atomicos operacionales
--     de los modulos de negocio; la denegacion por defecto se mantiene para lo
--     no concedido (plataforma).
--   - Req 27.10 / 25.4: acceso del administrador a los bloques CONTRATADOS; el
--     gating por Plan (moduloHabilitado) sigue restringiendo la visibilidad.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- admin_empresa -> UNION de los permisos de TODOS los roles predefinidos de
-- nivel EMPRESA (operativos). Enlace por la relacion existente rol_permiso,
-- filtrando por el NOMBRE de rol (columna `rol.nombre`, V1) para no depender de
-- los UUID de permiso. Solo roles internos con permisos atomicos; se excluye el
-- rol externo `cliente_portal` (sin permisos) y, por construccion, todo recurso
-- exclusivo de `super_admin` (plataforma).
-- ----------------------------------------------------------------------------
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000002', p.id
FROM permiso p
WHERE p.id IN (
        SELECT rp.permiso_id
          FROM rol_permiso rp
          JOIN rol r ON r.id = rp.rol_id
         WHERE r.predefinido = TRUE
           AND r.tenant_id IS NULL
           AND r.nombre IN (
                 'gerente', 'supervisor', 'ventas', 'diseno', 'produccion',
                 'almacen', 'instalacion', 'mantenimiento', 'contabilidad',
                 'rh', 'marketing', 'calidad')
      )
ON CONFLICT DO NOTHING;
