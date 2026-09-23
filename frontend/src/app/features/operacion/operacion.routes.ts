// =============================================================================
// Rutas del ambito OPERACION (Req 7, 16, 17, 19, 21, 18, 60) â€” vistas 51.1
// -----------------------------------------------------------------------------
// Agrupa produccion (ordenes de fabricacion), instalacion (levantamientos,
// permisos, OTIs), proyectos e inventario (base y avanzado). Cada vista es lazy
// (loadComponent) y esta protegida por su permiso atomico (deny-by-default).
// Este arreglo se monta desde empresa.routes.ts con `loadChildren` en el prefijo
// /empresa/operacion (el orquestador anade la entrada padre; ver el reporte).
// =============================================================================

import { Routes } from '@angular/router';

import { guardaGiro, guardaModulo, guardaPorPermiso } from '../../core/auth/auth.guard';
import { GIRO_ANUNCIOS } from '../../core/auth/auth.models';

export const operacionRoutes: Routes = [
  {
    // Lista de Ordenes de Fabricacion: vista GENERICA sin guardaGiro (Req 2.5,
    // 11.1). Conserva modulo 'operacion' + permiso atomico + RLS.
    path: 'ordenes-fabricacion',
    canActivate: [guardaModulo('operacion'), guardaPorPermiso('orden_fabricacion', 'listar')],
    loadComponent: () =>
      import('./ordenes-fabricacion/ordenes-fabricacion').then((m) => m.OperacionOrdenesFabricacion),
  },
  {
    // Detalle de Orden de Fabricacion: vista GENERICA sin guardaGiro (Req 9.1,
    // 2.5). Conserva modulo + permiso atomico + RLS. La retirada de guardaGiro de
    // la ruta de lista se realiza en la tarea 7.4.
    path: 'ordenes-fabricacion/:id',
    canActivate: [guardaModulo('operacion'), guardaPorPermiso('orden_fabricacion', 'leer')],
    loadComponent: () =>
      import('./ordenes-fabricacion/orden-fabricacion-detalle').then(
        (m) => m.OperacionOrdenFabricacionDetalle,
      ),
  },
  {
    path: 'levantamientos',
    canActivate: [guardaModulo('operacion'), guardaGiro(GIRO_ANUNCIOS), guardaPorPermiso('levantamiento_sitio', 'listar')],
    loadComponent: () =>
      import('./levantamientos/levantamientos').then((m) => m.OperacionLevantamientos),
  },
  {
    // Detalle de Levantamiento: vista ESPECIFICA de anuncios (Req 9.2). Conserva
    // guardaGiro(GIRO_ANUNCIOS) ademas de modulo + permiso atomico + RLS.
    path: 'levantamientos/:id',
    canActivate: [
      guardaModulo('operacion'),
      guardaGiro(GIRO_ANUNCIOS),
      guardaPorPermiso('levantamiento_sitio', 'leer'),
    ],
    loadComponent: () =>
      import('./levantamientos/levantamiento-detalle').then(
        (m) => m.OperacionLevantamientoDetalle,
      ),
  },
  {
    path: 'permisos',
    canActivate: [guardaModulo('operacion'), guardaGiro(GIRO_ANUNCIOS), guardaPorPermiso('permiso_instalacion', 'listar')],
    loadComponent: () => import('./permisos/permisos').then((m) => m.OperacionPermisos),
  },
  {
    // Detalle de Permiso: vista ESPECIFICA de anuncios (Req 9.3). Conserva
    // guardaGiro(GIRO_ANUNCIOS) ademas de modulo + permiso atomico + RLS.
    path: 'permisos/:id',
    canActivate: [
      guardaModulo('operacion'),
      guardaGiro(GIRO_ANUNCIOS),
      guardaPorPermiso('permiso_instalacion', 'leer'),
    ],
    loadComponent: () =>
      import('./permisos/permiso-detalle').then((m) => m.OperacionPermisoDetalle),
  },
  {
    path: 'instalacion',
    canActivate: [guardaModulo('operacion'), guardaGiro(GIRO_ANUNCIOS), guardaPorPermiso('orden_trabajo_instalacion', 'listar')],
    loadComponent: () => import('./instalacion/instalacion').then((m) => m.OperacionInstalacion),
  },
  {
    // Detalle de OTI: vista ESPECIFICA de anuncios (Req 9.4). Conserva
    // guardaGiro(GIRO_ANUNCIOS) ademas de modulo + permiso atomico + RLS.
    path: 'instalacion/:id',
    canActivate: [
      guardaModulo('operacion'),
      guardaGiro(GIRO_ANUNCIOS),
      guardaPorPermiso('orden_trabajo_instalacion', 'leer'),
    ],
    loadComponent: () =>
      import('./instalacion/oti-detalle').then((m) => m.OperacionOtiDetalle),
  },
  {
    // Lista de Proyectos: vista GENERICA sin guardaGiro (Req 2.5, 3.1). Conserva
    // modulo 'operacion' + permiso atomico + RLS.
    path: 'proyectos',
    canActivate: [guardaModulo('operacion'), guardaPorPermiso('proyecto', 'listar')],
    loadComponent: () => import('./proyectos/proyectos').then((m) => m.OperacionProyectos),
  },
  {
    // Detalle de Proyecto: vista GENERICA sin guardaGiro (Req 2.5, 3.1). Conserva
    // modulo 'operacion' + permiso atomico + RLS.
    path: 'proyectos/:id',
    canActivate: [guardaModulo('operacion'), guardaPorPermiso('proyecto', 'leer')],
    loadComponent: () =>
      import('./proyectos/proyecto-detalle').then((m) => m.OperacionProyectoDetalle),
  },
  {
    path: 'materiales',
    canActivate: [guardaModulo('operacion'), guardaPorPermiso('material', 'listar')],
    loadComponent: () => import('./materiales/materiales').then((m) => m.OperacionMateriales),
  },
  {
    // El Inventario avanzado es su propio Modulo ('inventario-avanzado'), distinto
    // del Modulo 'operacion' que cubre el resto de esta rama (incl. Materiales).
    // Se reaplica aqui el gating por Modulo especifico (ademas del permiso).
    path: 'inventario-avanzado',
    canActivate: [guardaModulo('inventario-avanzado'), guardaPorPermiso('almacen', 'listar')],
    loadComponent: () =>
      import('./inventario-avanzado/inventario-avanzado').then((m) => m.OperacionInventarioAvanzado),
  },
  { path: '', pathMatch: 'full', redirectTo: 'ordenes-fabricacion' },
];
