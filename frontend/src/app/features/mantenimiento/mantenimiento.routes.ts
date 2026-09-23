// =============================================================================
// Rutas del modulo Mantenimiento (Req 20) — bloque 51.2
// -----------------------------------------------------------------------------
// Hijas del ambito empresa (bajo ShellLayout). Cada vista es lazy y esta protegida
// por su permiso atomico (deny-by-default). Montadas bajo /empresa/mantenimiento.
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const mantenimientoRoutes: Routes = [
  {
    path: 'contratos',
    canActivate: [guardaPorPermiso('contrato_mantenimiento', 'listar')],
    loadComponent: () => import('./contratos/contratos').then((m) => m.MantenimientoContratos),
  },
  {
    path: 'tickets',
    canActivate: [guardaPorPermiso('ticket_servicio', 'listar')],
    loadComponent: () => import('./tickets/tickets').then((m) => m.MantenimientoTickets),
  },
  { path: '', pathMatch: 'full', redirectTo: 'tickets' },
];
