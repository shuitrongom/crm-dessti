// =============================================================================
// Rutas del modulo Facturacion CFDI (Req 34, 35, 37) — bloque 51.2
// -----------------------------------------------------------------------------
// Hijas del ambito empresa (bajo ShellLayout). Cada vista es lazy y esta protegida
// por su permiso atomico (deny-by-default). El backend reimpone la autorizacion.
// Montadas bajo /empresa/facturacion (lo cablea el orquestador en empresa.routes).
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const facturacionRoutes: Routes = [
  {
    path: 'facturas',
    canActivate: [guardaPorPermiso('factura', 'listar')],
    loadComponent: () => import('./facturas/facturas').then((m) => m.FacturacionFacturas),
  },
  {
    path: 'notas-credito',
    canActivate: [guardaPorPermiso('nota_credito', 'listar')],
    loadComponent: () =>
      import('./notas-credito/notas-credito').then((m) => m.FacturacionNotasCredito),
  },
  { path: '', pathMatch: 'full', redirectTo: 'facturas' },
];
