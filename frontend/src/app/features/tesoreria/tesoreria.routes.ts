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
  { path: '', pathMatch: 'full', redirectTo: 'cuentas-bancarias' },
];
