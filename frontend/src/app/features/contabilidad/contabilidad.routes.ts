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
    path: 'reportes',
    canActivate: [guardaPorPermiso('reporte_financiero', 'leer')],
    loadComponent: () => import('./reportes/reportes').then((m) => m.ContabilidadReportes),
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
