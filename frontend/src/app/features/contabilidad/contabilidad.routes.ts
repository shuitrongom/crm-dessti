// =============================================================================
// Rutas del modulo Contabilidad / finanzas (Req 36, 38, 39, 42) — bloque 51.2
// -----------------------------------------------------------------------------
// Hijas del ambito empresa (bajo ShellLayout). Cada vista es lazy y esta protegida
// por su permiso atomico (deny-by-default). El backend reimpone la autorizacion.
// Montadas bajo /empresa/contabilidad (lo cablea el orquestador).
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const contabilidadRoutes: Routes = [
  {
    path: 'cuentas-por-cobrar',
    canActivate: [guardaPorPermiso('cuenta_por_cobrar', 'listar')],
    loadComponent: () =>
      import('./cuentas-por-cobrar/cuentas-por-cobrar').then(
        (m) => m.ContabilidadCuentasPorCobrar,
      ),
  },
  {
    path: 'cuentas-por-pagar',
    canActivate: [guardaPorPermiso('cuenta_por_pagar', 'listar')],
    loadComponent: () =>
      import('./cuentas-por-pagar/cuentas-por-pagar').then((m) => m.ContabilidadCuentasPorPagar),
  },
  {
    path: 'programacion-pagos',
    canActivate: [guardaPorPermiso('programacion_pago', 'listar')],
    loadComponent: () =>
      import('./programacion-pagos/programacion-pagos').then(
        (m) => m.ContabilidadProgramacionPagos,
      ),
  },
  {
    path: 'polizas',
    canActivate: [guardaPorPermiso('poliza_contable', 'listar')],
    loadComponent: () => import('./polizas/polizas').then((m) => m.ContabilidadPolizas),
  },
  {
    path: 'catalogo-cuentas',
    canActivate: [guardaPorPermiso('cuenta_contable', 'listar')],
    loadComponent: () =>
      import('./catalogo-cuentas/catalogo-cuentas').then((m) => m.ContabilidadCatalogoCuentas),
  },
  {
    path: 'cierre-periodo',
    canActivate: [guardaPorPermiso('periodo_contable', 'leer')],
    loadComponent: () =>
      import('./cierre-periodo/cierre-periodo').then((m) => m.ContabilidadCierrePeriodo),
  },
  {
    path: 'reportes',
    canActivate: [guardaPorPermiso('reporte_financiero', 'leer')],
    loadComponent: () => import('./reportes/reportes').then((m) => m.ContabilidadReportes),
  },
  {
    path: 'estados-financieros',
    canActivate: [guardaPorPermiso('estado_financiero', 'leer')],
    loadComponent: () =>
      import('./estados-financieros/estados-financieros').then(
        (m) => m.ContabilidadEstadosFinancieros,
      ),
  },
  {
    path: 'antiguedad-saldos',
    canActivate: [guardaPorPermiso('cuenta_por_cobrar', 'leer')],
    loadComponent: () =>
      import('./antiguedad-saldos/antiguedad-saldos').then(
        (m) => m.ContabilidadAntiguedadSaldos,
      ),
  },
  {
    path: 'estado-cuenta-cliente',
    canActivate: [guardaPorPermiso('reporte_financiero', 'leer')],
    loadComponent: () =>
      import('./estado-cuenta-cliente/estado-cuenta-cliente').then(
        (m) => m.ContabilidadEstadoCuentaCliente,
      ),
  },
  {
    path: 'contabilidad-electronica',
    canActivate: [guardaPorPermiso('contabilidad_electronica', 'leer')],
    loadComponent: () =>
      import('./contabilidad-electronica/contabilidad-electronica').then(
        (m) => m.ContabilidadElectronica,
      ),
  },
  { path: '', pathMatch: 'full', redirectTo: 'cuentas-por-cobrar' },
];
