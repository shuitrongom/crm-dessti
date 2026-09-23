// =============================================================================
// Rutas del ambito COMERCIAL (Req 5, 6, 14, 15, 59, 63) — vistas de negocio (51.1)
// -----------------------------------------------------------------------------
// Hijas del ambito empresa (bajo ShellLayout). Cada vista es lazy (loadComponent)
// y esta protegida por su permiso atomico (deny-by-default). Este arreglo se
// monta desde empresa.routes.ts con `loadChildren` en el prefijo
// /empresa/comercial (el orquestador anade la entrada padre; ver el reporte).
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const comercialRoutes: Routes = [
  {
    path: 'clientes',
    canActivate: [guardaPorPermiso('cliente', 'listar')],
    loadComponent: () => import('./clientes/clientes').then((m) => m.ComercialClientes),
  },

  {
    // Ficha 360 del Cliente: datos + actividad comercial conectada (Req 1).
    path: 'clientes/:id',
    canActivate: [guardaPorPermiso('cliente', 'leer')],
    loadComponent: () =>
      import('./clientes/cliente-detalle').then((m) => m.ComercialClienteDetalle),
  },

  {
    path: 'oportunidades',
    canActivate: [guardaPorPermiso('oportunidad', 'listar')],
    loadComponent: () =>
      import('./oportunidades/oportunidades').then((m) => m.ComercialOportunidades),
  },
  {
    path: 'cotizaciones',
    canActivate: [guardaPorPermiso('cotizacion', 'listar')],
    loadComponent: () => import('./cotizaciones/cotizaciones').then((m) => m.ComercialCotizaciones),
  },
  {
    path: 'cotizaciones/nueva',
    canActivate: [guardaPorPermiso('cotizacion', 'crear')],
    loadComponent: () =>
      import('./cotizaciones/cotizacion-nueva').then((m) => m.ComercialCotizacionNueva),
  },
  {
    path: 'cotizaciones/:id',
    canActivate: [guardaPorPermiso('cotizacion', 'leer')],
    loadComponent: () =>
      import('./cotizaciones/cotizacion-detalle').then((m) => m.ComercialCotizacionDetalle),
  },
  {
    path: 'productos',
    canActivate: [guardaPorPermiso('producto', 'listar')],
    loadComponent: () => import('./productos/productos').then((m) => m.ComercialProductos),
  },
  {
    path: 'listas-precios',
    canActivate: [guardaPorPermiso('lista_precios', 'listar')],
    loadComponent: () =>
      import('./listas-precios/listas-precios').then((m) => m.ComercialListasPrecios),
  },
  {
    path: 'canales-venta',
    canActivate: [guardaPorPermiso('canal_venta', 'listar')],
    loadComponent: () =>
      import('./canales-venta/canales-venta').then((m) => m.ComercialCanalesVenta),
  },
  { path: '', pathMatch: 'full', redirectTo: 'clientes' },
];
