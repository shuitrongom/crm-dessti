// =============================================================================
// Rutas del modulo RH / Nomina y Organizacion (Req 40, 41, 61) — bloque 51.2
// -----------------------------------------------------------------------------
// Hijas del ambito empresa (bajo ShellLayout). Cada vista es lazy y esta protegida
// por su permiso atomico (deny-by-default). Montadas bajo /empresa/rh-nomina.
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const rhNominaRoutes: Routes = [
  {
    path: 'empleados',
    canActivate: [guardaPorPermiso('empleado', 'listar')],
    loadComponent: () => import('./empleados/empleados').then((m) => m.RhNominaEmpleados),
  },
  {
    path: 'nomina',
    canActivate: [guardaPorPermiso('nomina', 'listar')],
    loadComponent: () => import('./nomina/nomina').then((m) => m.RhNominaNomina),
  },
  {
    path: 'organizacion',
    canActivate: [guardaPorPermiso('puesto', 'listar')],
    loadComponent: () => import('./organizacion/organizacion').then((m) => m.RhNominaOrganizacion),
  },
  { path: '', pathMatch: 'full', redirectTo: 'empleados' },
];
