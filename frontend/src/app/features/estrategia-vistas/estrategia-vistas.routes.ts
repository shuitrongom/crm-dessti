// =============================================================================
// Rutas del modulo Estrategia (vistas de negocio) (Req 58) — bloque 51.2
// -----------------------------------------------------------------------------
// Vista de negocio mas completa que la pantalla de administracion existente. Es
// lazy y esta protegida por permiso atomico (deny-by-default). Montada bajo
// /empresa/estrategia-vistas.
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const estrategiaVistasRoutes: Routes = [
  {
    path: '',
    canActivate: [guardaPorPermiso('objetivo_estrategico', 'listar')],
    loadComponent: () => import('./estrategia/estrategia').then((m) => m.EstrategiaVistas),
  },
];
