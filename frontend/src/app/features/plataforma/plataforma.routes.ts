// =============================================================================
// Rutas del ambito PLATAFORMA (super_admin) (Req 24, 25, 69)
// -----------------------------------------------------------------------------
// Se monta bajo el ShellLayout. Cada vista es lazy y esta protegida por su
// permiso atomico de plataforma (deny-by-default), ademas de la guarda de ambito
// `guardaPlataforma` aplicada en app.routes.ts.
// =============================================================================

import { Routes } from '@angular/router';

import { ShellLayout } from '../../core/layout/shell-layout/shell-layout';
import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const plataformaRoutes: Routes = [
  {
    path: '',
    component: ShellLayout,
    children: [
      {
        path: 'giros',
        canActivate: [guardaPorPermiso('giro', 'listar')],
        loadComponent: () => import('./giros/giros').then((m) => m.PlataformaGiros),
      },
      {
        path: 'empresas',
        canActivate: [guardaPorPermiso('empresa', 'listar')],
        loadComponent: () => import('./empresas/empresas').then((m) => m.PlataformaEmpresas),
      },
      {
        path: 'planes-suscripciones',
        canActivate: [guardaPorPermiso('plan', 'listar')],
        loadComponent: () => import('./planes/planes').then((m) => m.PlataformaPlanes),
      },
      {
        path: 'facturacion',
        canActivate: [guardaPorPermiso('factura_renta', 'listar')],
        loadComponent: () =>
          import('./facturacion/facturacion').then((m) => m.PlataformaFacturacion),
      },
      {
        path: 'offboarding',
        canActivate: [guardaPorPermiso('offboarding', 'cambiar_estado')],
        loadComponent: () =>
          import('./offboarding/offboarding').then((m) => m.PlataformaOffboarding),
      },
      {
        // Perfil propio del super_admin: disponible para cualquier Usuario del
        // ambito plataforma (ya protegido por guardaPlataforma), sin permiso
        // atomico adicional. Consulta datos de cuenta y cambia la contrasena propia.
        path: 'perfil',
        loadComponent: () => import('./perfil/perfil').then((m) => m.PlataformaPerfil),
      },
      { path: '', pathMatch: 'full', redirectTo: 'empresas' },
    ],
  },
];
