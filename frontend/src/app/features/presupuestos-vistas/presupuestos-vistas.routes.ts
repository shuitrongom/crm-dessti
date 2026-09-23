// =============================================================================
// Rutas del modulo Presupuestos (vistas de negocio) (Req 62) — bloque 51.2
// -----------------------------------------------------------------------------
// Vista de negocio mas completa que la pantalla de administracion existente. Es
// lazy y esta protegida por permiso atomico (deny-by-default). Montada bajo
// /empresa/presupuestos-vistas.
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const presupuestosVistasRoutes: Routes = [
  {
    path: '',
    canActivate: [guardaPorPermiso('presupuesto', 'listar')],
    loadComponent: () => import('./presupuestos/presupuestos').then((m) => m.PresupuestosVistas),
  },
];
