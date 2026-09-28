// =============================================================================
// Rutas del modulo Tesoreria (Req 43) — bloque 51.2
// -----------------------------------------------------------------------------
// Hijas del ambito empresa (bajo ShellLayout). Cada vista es lazy y esta protegida
// por su permiso atomico (deny-by-default). Montadas bajo /empresa/tesoreria.
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const tesoreriaRoutes: Routes = [
  {
    path: 'cuentas-bancarias',
    canActivate: [guardaPorPermiso('cuenta_bancaria', 'listar')],
    loadComponent: () =>
      import('./cuentas-bancarias/cuentas-bancarias').then((m) => m.TesoreriaCuentasBancarias),
  },
  {
    path: 'movimientos',
    canActivate: [guardaPorPermiso('movimiento_bancario', 'leer')],
    loadComponent: () => import('./movimientos/movimientos').then((m) => m.TesoreriaMovimientos),
  },
  {
    path: 'conciliaciones',
    canActivate: [guardaPorPermiso('conciliacion_bancaria', 'leer')],
    loadComponent: () =>
      import('./conciliaciones/conciliaciones').then((m) => m.TesoreriaConciliaciones),
  },
  {
    path: 'transferencias',
    canActivate: [guardaPorPermiso('transferencia_bancaria', 'listar')],
    loadComponent: () =>
      import('./transferencias/transferencias').then((m) => m.TesoreriaTransferencias),
  },
  {
    path: 'flujo-caja',
    canActivate: [guardaPorPermiso('movimiento_bancario', 'leer')],
    loadComponent: () => import('./flujo-caja/flujo-caja').then((m) => m.TesoreriaFlujoCaja),
  },
  { path: '', pathMatch: 'full', redirectTo: 'cuentas-bancarias' },
];
