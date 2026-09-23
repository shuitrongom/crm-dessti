// =============================================================================
// Rutas del ambito EMPRESA (Req 3, 22, 26, 58, administracion 4/27/28/68/62,
// y vistas de negocio del bloque 51: comercial/produccion/finanzas/etc.)
// -----------------------------------------------------------------------------
// El ambito empresa se monta bajo el ShellLayout (barra + drawer con navegacion
// dinamica). Contiene la pagina principal (50.2), el area de administracion
// (50.4) y las vistas de los modulos de negocio (bloque 51). Cada vista es lazy
// y esta protegida por su permiso atomico (deny-by-default) ademas de la guarda
// de ambito aplicada en app.routes.ts. El guard de cada rama es una compuerta
// gruesa; cada ruta hija reaplica su propio guardaPorPermiso(recurso, operacion).
// =============================================================================

import { Routes } from '@angular/router';

import { ShellLayout } from '../../core/layout/shell-layout/shell-layout';
import {
  guardaAdminEmpresa,
  guardaGiro,
  guardaModulo,
  guardaModuloAlguno,
  guardaPorPermiso,
} from '../../core/auth/auth.guard';
import { GIRO_ANUNCIOS } from '../../core/auth/auth.models';

export const empresaRoutes: Routes = [
  {
    path: '',
    component: ShellLayout,
    children: [
      {
        path: 'inicio',
        loadComponent: () => import('./home/empresa-home').then((m) => m.EmpresaHome),
      },
      {
        // "Mi perfil" del ambito empresa: reutiliza el mismo componente de perfil
        // del ambito plataforma (datos de cuenta + cambio de contrasena, validos
        // para cualquier Usuario autenticado via GET /auth/perfil). Ademas, para
        // admin_empresa renderiza la seccion "Datos de mi empresa". No requiere
        // permiso atomico adicional (ya protegido por guardaEmpresa).
        path: 'perfil',
        loadComponent: () =>
          import('../plataforma/perfil/perfil').then((m) => m.PlataformaPerfil),
      },
      {
        path: 'administracion',
        canActivate: [guardaAdminEmpresa],
        loadChildren: () =>
          import('./administracion/administracion.routes').then((m) => m.administracionRoutes),
      },

      // --- Bloque 51.1: vistas comerciales y de produccion/operacion ---
      {
        path: 'comercial',
        canActivate: [guardaModulo('comercial'), guardaPorPermiso('cliente', 'listar')],
        loadChildren: () => import('../comercial/comercial.routes').then((m) => m.comercialRoutes),
      },
      {
        // Rama que agrupa DOS Modulos distintos: 'operacion' (produccion,
        // instalacion, proyectos e inventario base/Materiales) y el Modulo propio
        // 'inventario-avanzado'. La compuerta del padre es GRUESA: abre la rama si
        // el plan incluye CUALQUIERA de esos Modulos (guardaModuloAlguno), para que
        // una Empresa que solo contrate 'inventario-avanzado' pueda llegar a su
        // vista de Inventario. El gating FINO lo reimpone cada hoja en
        // operacion.routes.ts: las vistas del Vertical_Anuncios exigen su permiso Y
        // el giro anuncios-luminosos (guardaGiro), mientras que Materiales e
        // Inventario avanzado (Nucleo) solo exigen su permiso/Modulo, sin filtrar
        // por giro. El backend reimpone todo con su 403.
        path: 'operacion',
        canActivate: [guardaModuloAlguno('operacion', 'inventario-avanzado')],
        loadChildren: () => import('../operacion/operacion.routes').then((m) => m.operacionRoutes),
      },

      // --- Bloque 51.2: vistas de finanzas, RH, estrategia y mantenimiento ---
      {
        path: 'compras',
        canActivate: [guardaModulo('compras'), guardaPorPermiso('requisicion_compra', 'listar')],
        loadChildren: () => import('../compras/compras.routes').then((m) => m.comprasRoutes),
      },
      {
        path: 'facturacion',
        canActivate: [guardaModulo('facturacion'), guardaPorPermiso('factura', 'listar')],
        loadChildren: () => import('../facturacion/facturacion.routes').then((m) => m.facturacionRoutes),
      },
      {
        path: 'contabilidad',
        canActivate: [guardaModulo('contabilidad'), guardaPorPermiso('cuenta_por_cobrar', 'listar')],
        loadChildren: () => import('../contabilidad/contabilidad.routes').then((m) => m.contabilidadRoutes),
      },
      {
        path: 'tesoreria',
        canActivate: [guardaModulo('tesoreria'), guardaPorPermiso('cuenta_bancaria', 'listar')],
        loadChildren: () => import('../tesoreria/tesoreria.routes').then((m) => m.tesoreriaRoutes),
      },
      {
        path: 'activos',
        canActivate: [guardaModulo('activos-fijos'), guardaPorPermiso('activo_fijo', 'listar')],
        loadChildren: () => import('../activos/activos.routes').then((m) => m.activosRoutes),
      },
      {
        path: 'rh-nomina',
        canActivate: [guardaModulo('rh-nomina'), guardaPorPermiso('empleado', 'listar')],
        loadChildren: () => import('../rhnomina/rhnomina.routes').then((m) => m.rhNominaRoutes),
      },
      {
        path: 'estrategia-vistas',
        canActivate: [guardaModulo('estrategia'), guardaPorPermiso('objetivo_estrategico', 'listar')],
        loadChildren: () =>
          import('../estrategia-vistas/estrategia-vistas.routes').then((m) => m.estrategiaVistasRoutes),
      },
      {
        path: 'presupuestos-vistas',
        canActivate: [guardaModulo('presupuestos'), guardaPorPermiso('presupuesto', 'listar')],
        loadChildren: () =>
          import('../presupuestos-vistas/presupuestos-vistas.routes').then((m) => m.presupuestosVistasRoutes),
      },
      {
        // Rama del Vertical_Anuncios: compuerta gruesa giro + permiso (Req 9.2,
        // 9.4) MAS gating por Modulo 'mantenimiento'. El backend reimpone ambos.
        path: 'mantenimiento',
        canActivate: [
          guardaModulo('mantenimiento'),
          guardaGiro(GIRO_ANUNCIOS),
          guardaPorPermiso('contrato_mantenimiento', 'listar'),
        ],
        loadChildren: () => import('../mantenimiento/mantenimiento.routes').then((m) => m.mantenimientoRoutes),
      },

      // --- Bloque 51.3: redes sociales y reportes/BI ---
      {
        path: 'social',
        canActivate: [guardaModulo('redes-sociales'), guardaPorPermiso('bandeja', 'leer')],
        loadChildren: () => import('../social/social.routes').then((m) => m.socialRoutes),
      },
      {
        path: 'reportes',
        canActivate: [guardaModulo('reportes-bi'), guardaPorPermiso('tablero', 'leer')],
        loadChildren: () => import('../reportes/reportes.routes').then((m) => m.reportesRoutes),
      },

      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
    ],
  },
];

// Reexport del helper de guarda por permiso para las vistas hijas.
export { guardaPorPermiso };