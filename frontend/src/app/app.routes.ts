import { Routes } from '@angular/router';

import {
  authGuard,
  guardaEmpresa,
  guardaPlataforma,
  guardaPortal,
  guardaRaiz,
} from './core/auth/auth.guard';

/**
 * Enrutador de nivel superior por ambito (Req 3, deny-by-default).
 *
 * Estructura:
 *   /login                 -> publico (autenticacion).
 *   /plataforma/**          -> super_admin (gestion de plataforma: 24/25/69).
 *   /empresa/**             -> usuarios de empresa (inicio 50.2 + administracion 50.4).
 *   /empresa/administracion -> admin_empresa (guarda de area adicional).
 *   /portal/**              -> cliente_portal (portal del cliente).
 *   /acceso-denegado        -> vista 403 para usuarios autenticados sin permiso.
 *   **                      -> redireccion a la raiz (resuelta por scope).
 *
 * Cada ambito carga sus rutas de forma diferida (lazy) para mantener el bundle
 * inicial reducido; el shell de navegacion lo aporta cada ambito.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    // Cambio de contrasena forzado tras iniciar sesion con una contrasena
    // temporal (V69). Requiere sesion (el login ya emitio tokens) y espera la
    // contrasena actual por estado de navegacion.
    path: 'cambiar-password',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/cambiar-password/cambiar-password').then((m) => m.CambiarPassword),
  },
  {
    path: 'plataforma',
    canActivate: [guardaPlataforma],
    loadChildren: () =>
      import('./features/plataforma/plataforma.routes').then((m) => m.plataformaRoutes),
  },
  {
    path: 'empresa',
    canActivate: [guardaEmpresa],
    // La sub-area de administracion refuerza la autorizacion con la guarda de
    // area `guardaAdminEmpresa` en su definicion de ruta hija (empresa.routes ->
    // administracion). Aqui se protege el ambito completo.
    loadChildren: () => import('./features/empresa/empresa.routes').then((m) => m.empresaRoutes),
  },
  {
    path: 'portal',
    canActivate: [guardaPortal],
    loadChildren: () => import('./features/portal/portal.routes').then((m) => m.portalRoutes),
  },
  {
    path: 'acceso-denegado',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/acceso-denegado/acceso-denegado').then((m) => m.AccesoDenegado),
  },
  // Raiz: la guarda `guardaRaiz` redirige al ambito del Usuario (o a /login) y
  // nunca deja continuar, por lo que el componente nunca se instancia.
  {
    path: '',
    pathMatch: 'full',
    canActivate: [guardaRaiz],
    loadComponent: () =>
      import('./features/acceso-denegado/acceso-denegado').then((m) => m.AccesoDenegado),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
