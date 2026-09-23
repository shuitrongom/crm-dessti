// =============================================================================
// Rutas del modulo Reportes / BI (Req 22, 48) — bloque 51.3
// -----------------------------------------------------------------------------
// Hija del ambito empresa (bajo ShellLayout), lazy y protegida por permiso
// atomico (deny-by-default). Montada bajo /empresa/reportes. Cada ruta reaplica
// su propio guardaPorPermiso; el acceso efectivo lo reimpone el backend.
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const reportesRoutes: Routes = [
  {
    path: 'tablero',
    canActivate: [guardaPorPermiso('tablero', 'leer')],
    loadComponent: () => import('./tablero/tablero').then((m) => m.Tablero),
  },
  {
    path: 'inteligencia',
    canActivate: [guardaPorPermiso('inteligencia_negocio', 'leer')],
    loadComponent: () => import('./inteligencia/inteligencia').then((m) => m.Inteligencia),
  },
  {
    path: 'tableros-personalizados',
    canActivate: [guardaPorPermiso('inteligencia_negocio', 'leer')],
    loadComponent: () =>
      import('./tableros-personalizados/tableros-personalizados').then(
        (m) => m.TablerosPersonalizados,
      ),
  },
  { path: '', pathMatch: 'full', redirectTo: 'tablero' },
];
