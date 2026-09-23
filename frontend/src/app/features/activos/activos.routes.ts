// =============================================================================
// Rutas del modulo Activos fijos (Req 44) — bloque 51.2
// -----------------------------------------------------------------------------
// Hija del ambito empresa (bajo ShellLayout), lazy y protegida por permiso
// atomico (deny-by-default). Montada bajo /empresa/activos.
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const activosRoutes: Routes = [
  {
    path: 'activos-fijos',
    canActivate: [guardaPorPermiso('activo_fijo', 'listar')],
    loadComponent: () => import('./activos-fijos/activos-fijos').then((m) => m.ActivosFijos),
  },
  { path: '', pathMatch: 'full', redirectTo: 'activos-fijos' },
];
