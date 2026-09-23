// =============================================================================
// Rutas del modulo Compras / abastecimiento (Req 29, 30, 31, 32, 33) — bloque 51.2
// -----------------------------------------------------------------------------
// Hijas del ambito empresa (bajo ShellLayout). Cada vista es lazy y esta protegida
// por su permiso atomico (deny-by-default). El backend reimpone la autorizacion en
// cada peticion. Se montan bajo el prefijo /empresa/compras (lo cablea el
// orquestador en empresa.routes.ts).
// =============================================================================

import { Routes } from '@angular/router';

import { guardaPorPermiso } from '../../core/auth/auth.guard';

export const comprasRoutes: Routes = [
  {
    path: 'requisiciones',
    canActivate: [guardaPorPermiso('requisicion_compra', 'listar')],
    loadComponent: () => import('./requisiciones/requisiciones').then((m) => m.ComprasRequisiciones),
  },
  {
    path: 'ordenes-compra',
    canActivate: [guardaPorPermiso('orden_compra', 'listar')],
    loadComponent: () =>
      import('./ordenes-compra/ordenes-compra').then((m) => m.ComprasOrdenesCompra),
  },
  {
    path: 'recepciones',
    canActivate: [guardaPorPermiso('recepcion_mercancia', 'listar')],
    loadComponent: () => import('./recepciones/recepciones').then((m) => m.ComprasRecepciones),
  },
  {
    path: 'facturas-proveedor',
    canActivate: [guardaPorPermiso('factura_proveedor', 'listar')],
    loadComponent: () =>
      import('./facturas-proveedor/facturas-proveedor').then((m) => m.ComprasFacturasProveedor),
  },
  { path: '', pathMatch: 'full', redirectTo: 'requisiciones' },
];
