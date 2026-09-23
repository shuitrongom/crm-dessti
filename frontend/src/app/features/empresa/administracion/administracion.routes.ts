// =============================================================================
// Rutas del area de ADMINISTRACION de empresa (admin_empresa) (Req 4/26/27/28/68/58/62)
// -----------------------------------------------------------------------------
// Hijas del ambito empresa (bajo ShellLayout). Cada vista es lazy y esta
// protegida por su permiso atomico (deny-by-default), ademas de la guarda de
// area `guardaAdminEmpresa` aplicada en app.routes.ts para el prefijo
// /empresa/administracion.
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../../core/auth/auth.guard';

export const administracionRoutes: Routes = [
  {
    path: 'usuarios',
    canActivate: [guardaPorPermiso('usuario', 'crear')],
    loadComponent: () => import('./usuarios/usuarios').then((m) => m.AdminUsuarios),
  },
  {
    path: 'roles',
    canActivate: [guardaPorPermiso('rol', 'listar')],
    loadComponent: () => import('./roles/roles').then((m) => m.AdminRoles),
  },
  {
    path: 'sesiones',
    canActivate: [guardaPorPermiso('sesion', 'listar')],
    loadComponent: () => import('./sesiones/sesiones').then((m) => m.AdminSesiones),
  },
  {
    path: 'branding',
    canActivate: [guardaPorPermiso('branding', 'leer')],
    loadComponent: () => import('./branding/branding').then((m) => m.AdminBranding),
  },
  { path: '', pathMatch: 'full', redirectTo: 'usuarios' },
];
