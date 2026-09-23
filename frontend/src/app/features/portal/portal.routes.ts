// =============================================================================
// Rutas del ambito PORTAL DEL CLIENTE (cliente_portal) (Req 45)
// -----------------------------------------------------------------------------
// Se monta bajo el ShellLayout. En esta fase solo incluye la landing; el bloque
// 51.3 anadira las vistas del portal como rutas hijas de este arreglo, sin
// reestructurar el shell.
// =============================================================================

import { Routes } from '@angular/router';

import { ShellLayout } from '../../core/layout/shell-layout/shell-layout';

export const portalRoutes: Routes = [
  {
    path: '',
    component: ShellLayout,
    children: [
      {
        path: 'inicio',
        loadComponent: () => import('./home/portal-home').then((m) => m.PortalHome),
      },
      // --- Bloque 51.3: vistas del portal del cliente ---
      {
        path: 'cotizaciones',
        loadComponent: () =>
          import('./cotizaciones/portal-cotizaciones').then((m) => m.PortalCotizaciones),
      },
      {
        path: 'pruebas-diseno',
        loadComponent: () =>
          import('./pruebas-diseno/portal-pruebas-diseno').then((m) => m.PortalPruebasDiseno),
      },
      {
        path: 'proyectos',
        loadComponent: () => import('./proyectos/portal-proyectos').then((m) => m.PortalProyectos),
      },
      {
        path: 'tickets',
        loadComponent: () => import('./tickets/portal-tickets').then((m) => m.PortalTickets),
      },
      {
        path: 'facturas',
        loadComponent: () => import('./facturas/portal-facturas').then((m) => m.PortalFacturas),
      },
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
    ],
  },
];
