// =============================================================================
// Rutas del modulo Social / Omnicanal (Req 64, 65, 66) — bloque 51.3
// -----------------------------------------------------------------------------
// Hija del ambito empresa (bajo ShellLayout), lazy y protegida por permiso
// atomico (deny-by-default). Montada bajo /empresa/social. Cada ruta reaplica su
// propio guardaPorPermiso; el acceso efectivo lo reimpone el backend.
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const socialRoutes: Routes = [
  {
    path: 'conexiones',
    canActivate: [guardaPorPermiso('cuenta_canal_social', 'listar')],
    loadComponent: () => import('./conexiones/conexiones').then((m) => m.Conexiones),
  },
  {
    path: 'bandeja',
    canActivate: [guardaPorPermiso('bandeja', 'leer')],
    loadComponent: () => import('./bandeja/bandeja').then((m) => m.Bandeja),
  },
  {
    path: 'publicaciones',
    canActivate: [guardaPorPermiso('publicacion_social', 'listar')],
    loadComponent: () => import('./publicaciones/publicaciones').then((m) => m.Publicaciones),
  },
  {
    path: 'campanas',
    canActivate: [guardaPorPermiso('campana_publicitaria', 'listar')],
    loadComponent: () => import('./campanas/campanas').then((m) => m.Campanas),
  },
  {
    path: 'analitica',
    canActivate: [guardaPorPermiso('analitica_social', 'leer')],
    loadComponent: () => import('./analitica/analitica-social').then((m) => m.AnaliticaSocial),
  },
  { path: '', pathMatch: 'full', redirectTo: 'conexiones' },
];
