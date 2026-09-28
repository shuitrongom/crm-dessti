// =============================================================================
// Rutas del modulo Calidad / SGC ISO 9001:2026 (Req 70)
// -----------------------------------------------------------------------------
// Hijas del ambito empresa (bajo ShellLayout). Cada vista es lazy y esta
// protegida por su permiso atomico (deny-by-default). Montadas bajo
// /empresa/calidad. Bloque COMUN a todos los giros (sin guarda de giro): el
// backend (com.dessti.crm.calidad) solo exige permisos, no modulo ni giro.
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const calidadRoutes: Routes = [
  {
    path: 'panel',
    canActivate: [guardaPorPermiso('calidad', 'leer')],
    loadComponent: () => import('./panel/panel').then((m) => m.CalidadPanel),
  },
  {
    path: 'quejas',
    canActivate: [guardaPorPermiso('queja_cliente', 'listar')],
    loadComponent: () => import('./quejas/quejas').then((m) => m.CalidadQuejas),
  },
  {
    path: 'no-conformidades',
    canActivate: [guardaPorPermiso('no_conformidad', 'listar')],
    loadComponent: () =>
      import('./no-conformidades/no-conformidades').then((m) => m.CalidadNoConformidades),
  },
  {
    path: 'acciones-correctivas',
    canActivate: [guardaPorPermiso('accion_correctiva', 'listar')],
    loadComponent: () =>
      import('./acciones-correctivas/acciones-correctivas').then(
        (m) => m.CalidadAccionesCorrectivas,
      ),
  },
  {
    path: 'riesgos',
    canActivate: [guardaPorPermiso('riesgo', 'listar')],
    loadComponent: () => import('./riesgos/riesgos').then((m) => m.CalidadRiesgos),
  },
  { path: '', pathMatch: 'full', redirectTo: 'panel' },
];
