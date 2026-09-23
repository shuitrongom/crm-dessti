// =============================================================================
// Modelo y servicio de navegacion dinamica (deny-by-default â€” Req 3)
// -----------------------------------------------------------------------------
// La navegacion visible se compone dinamicamente a partir de los roles y
// permisos atomicos del Usuario. Cada item declara su predicado de visibilidad;
// solo se muestran los enlaces para los que el Usuario esta autorizado. La
// autorizacion efectiva la reimpone el backend en cada peticion.
// =============================================================================

import { Injectable, computed, inject } from '@angular/core';

import { AuthService } from '../auth/auth.service';
import { ModulosEmpresaService } from '../auth/modulos-empresa.service';
import { GIRO_ANUNCIOS, ROL_SUPER_ADMIN } from '../auth/auth.models';

/** Un elemento de navegacion resuelto (ya filtrado por autorizacion). */
export interface ItemNavegacion {
  /** Etiqueta visible en espanol. */
  etiqueta: string;
  /** Ruta destino (routerLink). */
  ruta: string;
  /** Nombre del icono de Material Symbols. */
  icono: string;
}

/** Definicion interna de un item con su predicado de visibilidad. */
interface DefinicionItem extends ItemNavegacion {
  /** Predicado que decide si el item es visible para el Usuario actual. */
  visible: (auth: AuthService) => boolean;
  /**
   * Titulo de la seccion a la que pertenece el item (encabezado del grupo en el
   * menu). Opcional: los items sin seccion se agrupan en un unico grupo sin
   * encabezado (comportamiento historico de empresa/portal).
   */
  seccion?: string;
  /**
   * Clave canonica del Modulo que habilita este item (gating por modulo). Solo
   * aplica al ambito empresa: el item se muestra unicamente si el tenant tiene
   * contratado el Modulo (AND con el predicado `visible`). Los items sin `modulo`
   * (p. ej. la administracion base) no se filtran por modulo. El ambito
   * plataforma/portal ignora este campo (no usan gating por modulo).
   */
  modulo?: string;
}

/** Grupo de items de navegacion bajo un mismo encabezado (o sin el). */
export interface GrupoNavegacion {
  /** Titulo del grupo; `null` para un grupo sin encabezado. */
  titulo: string | null;
  /** Items visibles del grupo, en orden. */
  items: ItemNavegacion[];
}

@Injectable({ providedIn: 'root' })
export class NavigationService {
  private readonly auth = inject(AuthService);
  private readonly modulosEmpresa = inject(ModulosEmpresaService);

  /**
   * Menu del ambito de plataforma (super_admin, Req 24, 25, 69). Se muestra solo
   * a super_admin; el control fino adicional queda cubierto por las guardas.
   */
  private readonly itemsPlataforma: DefinicionItem[] = [
    // --- Seccion CATALOGO: la oferta configurable de la plataforma ---
    {
      // Los Giros son la base del catalogo: van primero en el menu de plataforma.
      etiqueta: 'Giros',
      ruta: '/plataforma/giros',
      icono: 'category',
      seccion: 'Catálogo',
      visible: (a) => a.tieneRol(ROL_SUPER_ADMIN) && a.tienePermiso('giro', 'listar'),
    },
    {
      etiqueta: 'Planes y suscripciones',
      ruta: '/plataforma/planes-suscripciones',
      icono: 'workspace_premium',
      seccion: 'Catálogo',
      visible: (a) => a.tieneRol(ROL_SUPER_ADMIN) && a.tienePermiso('plan', 'listar'),
    },
    // --- Seccion CLIENTES: los tenants y su ciclo de vida ---
    {
      etiqueta: 'Empresas',
      ruta: '/plataforma/empresas',
      icono: 'domain',
      seccion: 'Clientes',
      visible: (a) => a.tieneRol(ROL_SUPER_ADMIN) && a.tienePermiso('empresa', 'listar'),
    },
    {
      etiqueta: 'Facturación',
      ruta: '/plataforma/facturacion',
      icono: 'receipt_long',
      seccion: 'Clientes',
      visible: (a) => a.tieneRol(ROL_SUPER_ADMIN) && a.tienePermiso('factura_renta', 'listar'),
    },
    {
      etiqueta: 'Offboarding',
      ruta: '/plataforma/offboarding',
      icono: 'logout',
      seccion: 'Clientes',
      visible: (a) =>
        a.tieneRol(ROL_SUPER_ADMIN) && a.tienePermiso('offboarding', 'cambiar_estado'),
    },
  ];

  /**
   * Menu del ambito de empresa (Req 22, 26, 58, y administracion Req 4, 27, 28, 68, 62).
   *
   * Frontera Nucleo / Vertical de Anuncios (Req 9.2, 9.3, 5): la mayoria de los
   * items son del Nucleo_Comun (comercial, compras, facturacion, contabilidad,
   * tesoreria, activos, rh-nomina, estrategia, presupuestos, social, reportes,
   * administracion, inicio) y su visibilidad depende SOLO del permiso.
   *
   * Los items del Vertical_Anuncios se identifican por su recurso, no solo por su
   * ruta, y componen ademas `esGiro(GIRO_ANUNCIOS)` (AND con el permiso):
   *   - /empresa/operacion : orden_fabricacion, levantamiento_sitio,
   *     permiso_instalacion, orden_trabajo_instalacion, proyecto.
   *   - /empresa/mantenimiento : contrato_mantenimiento, ticket_servicio.
   * NOTA: "Materiales" (material) e "Inventario avanzado" (almacen), aun colgando
   * de /empresa/operacion, son inventario base del Nucleo y NO se filtran por giro.
   * Asi, una Empresa de otro giro con permiso de operacion NO ve el vertical.
   */
  private readonly itemsEmpresa: DefinicionItem[] = [
    // -------------------------------------------------------------------------
    // 1. Cuenta (administracion base, sin modulo: siempre visible para el admin)
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Inicio',
      ruta: '/empresa/inicio',
      icono: 'dashboard',
      seccion: 'Cuenta',
      visible: () => true,
    },
    {
      etiqueta: 'Usuarios',
      ruta: '/empresa/administracion/usuarios',
      icono: 'group',
      seccion: 'Cuenta',
      visible: (a) => a.tienePermiso('usuario', 'crear') || a.tienePermiso('usuario', 'actualizar'),
    },
    {
      etiqueta: 'Roles y permisos',
      ruta: '/empresa/administracion/roles',
      icono: 'admin_panel_settings',
      seccion: 'Cuenta',
      visible: (a) => a.tieneAlgunPermiso('rol:crear', 'rol:actualizar', 'rol:listar'),
    },
    {
      etiqueta: 'Sesiones',
      ruta: '/empresa/administracion/sesiones',
      icono: 'devices',
      seccion: 'Cuenta',
      visible: (a) => a.tienePermiso('sesion', 'listar'),
    },
    {
      etiqueta: 'Branding',
      ruta: '/empresa/administracion/branding',
      icono: 'palette',
      seccion: 'Cuenta',
      visible: (a) => a.tienePermiso('branding', 'actualizar') || a.tienePermiso('branding', 'leer'),
    },
    // -------------------------------------------------------------------------
    // 2. Comercial (CRM)
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Clientes',
      ruta: '/empresa/comercial/clientes',
      icono: 'contacts',
      seccion: 'Comercial (CRM)',
      modulo: 'comercial',
      visible: (a) => a.tienePermiso('cliente', 'listar'),
    },
    {
      etiqueta: 'Oportunidades',
      ruta: '/empresa/comercial/oportunidades',
      icono: 'trending_up',
      seccion: 'Comercial (CRM)',
      modulo: 'comercial',
      visible: (a) => a.tienePermiso('oportunidad', 'listar'),
    },
    {
      etiqueta: 'Cotizaciones',
      ruta: '/empresa/comercial/cotizaciones',
      icono: 'request_quote',
      seccion: 'Comercial (CRM)',
      modulo: 'comercial',
      visible: (a) => a.tienePermiso('cotizacion', 'listar'),
    },
    {
      etiqueta: 'Productos',
      ruta: '/empresa/comercial/productos',
      icono: 'category',
      seccion: 'Comercial (CRM)',
      modulo: 'comercial',
      visible: (a) => a.tienePermiso('producto', 'listar'),
    },
    {
      etiqueta: 'Listas de precios',
      ruta: '/empresa/comercial/listas-precios',
      icono: 'price_change',
      seccion: 'Comercial (CRM)',
      modulo: 'comercial',
      visible: (a) => a.tienePermiso('lista_precios', 'listar'),
    },
    {
      etiqueta: 'Canales de venta',
      ruta: '/empresa/comercial/canales-venta',
      icono: 'hub',
      seccion: 'Comercial (CRM)',
      modulo: 'comercial',
      visible: (a) => a.tienePermiso('canal_venta', 'listar'),
    },
    // -------------------------------------------------------------------------
    // 3. Operacion y produccion
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Órdenes de fabricación',
      ruta: '/empresa/operacion/ordenes-fabricacion',
      icono: 'precision_manufacturing',
      seccion: 'Operación y producción',
      modulo: 'operacion',
      // Vertical_Anuncios: permiso Y giro anuncios-luminosos (Req 9.2, 9.3).
      visible: (a) => a.tienePermiso('orden_fabricacion', 'listar') && a.esGiro(GIRO_ANUNCIOS),
    },
    {
      etiqueta: 'Levantamientos',
      ruta: '/empresa/operacion/levantamientos',
      icono: 'straighten',
      seccion: 'Operación y producción',
      modulo: 'operacion',
      // Vertical_Anuncios: permiso Y giro anuncios-luminosos (Req 9.2, 9.3).
      visible: (a) => a.tienePermiso('levantamiento_sitio', 'listar') && a.esGiro(GIRO_ANUNCIOS),
    },
    {
      etiqueta: 'Permisos de instalación',
      ruta: '/empresa/operacion/permisos',
      icono: 'verified',
      seccion: 'Operación y producción',
      modulo: 'operacion',
      // Vertical_Anuncios: permiso Y giro anuncios-luminosos (Req 9.2, 9.3).
      visible: (a) => a.tienePermiso('permiso_instalacion', 'listar') && a.esGiro(GIRO_ANUNCIOS),
    },
    {
      etiqueta: 'Instalación (OTIs)',
      ruta: '/empresa/operacion/instalacion',
      icono: 'construction',
      seccion: 'Operación y producción',
      modulo: 'operacion',
      // Vertical_Anuncios: permiso Y giro anuncios-luminosos (Req 9.2, 9.3).
      visible: (a) => a.tienePermiso('orden_trabajo_instalacion', 'listar') && a.esGiro(GIRO_ANUNCIOS),
    },
    {
      etiqueta: 'Proyectos',
      ruta: '/empresa/operacion/proyectos',
      icono: 'account_tree',
      seccion: 'Operación y producción',
      // Proyectos forma parte del Modulo operacion (no es clave propia).
      modulo: 'operacion',
      // Vertical_Anuncios: permiso Y giro anuncios-luminosos (Req 9.2, 9.3).
      visible: (a) => a.tienePermiso('proyecto', 'listar') && a.esGiro(GIRO_ANUNCIOS),
    },
    {
      etiqueta: 'Materiales',
      ruta: '/empresa/operacion/materiales',
      icono: 'inventory_2',
      seccion: 'Operación y producción',
      modulo: 'operacion',
      visible: (a) => a.tienePermiso('material', 'listar'),
    },
    // -------------------------------------------------------------------------
    // 4. Inventario avanzado
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Inventario avanzado',
      ruta: '/empresa/operacion/inventario-avanzado',
      icono: 'warehouse',
      seccion: 'Inventario avanzado',
      modulo: 'inventario-avanzado',
      visible: (a) => a.tienePermiso('almacen', 'listar'),
    },
    // -------------------------------------------------------------------------
    // 5. Compras
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Compras',
      ruta: '/empresa/compras/requisiciones',
      icono: 'shopping_cart',
      seccion: 'Compras',
      modulo: 'compras',
      visible: (a) => a.tienePermiso('requisicion_compra', 'listar'),
    },
    {
      etiqueta: 'Órdenes de compra',
      ruta: '/empresa/compras/ordenes-compra',
      icono: 'receipt',
      seccion: 'Compras',
      modulo: 'compras',
      visible: (a) => a.tienePermiso('orden_compra', 'listar'),
    },
    {
      etiqueta: 'Recepciones',
      ruta: '/empresa/compras/recepciones',
      icono: 'inventory',
      seccion: 'Compras',
      modulo: 'compras',
      visible: (a) => a.tienePermiso('recepcion_mercancia', 'listar'),
    },
    {
      etiqueta: 'Facturas de proveedor',
      ruta: '/empresa/compras/facturas-proveedor',
      icono: 'request_page',
      seccion: 'Compras',
      modulo: 'compras',
      visible: (a) => a.tienePermiso('factura_proveedor', 'listar'),
    },
    // -------------------------------------------------------------------------
    // 6. Facturacion (CFDI)
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Facturación CFDI',
      ruta: '/empresa/facturacion/facturas',
      icono: 'description',
      seccion: 'Facturación (CFDI)',
      modulo: 'facturacion',
      visible: (a) => a.tienePermiso('factura', 'listar'),
    },
    {
      etiqueta: 'Notas de crédito',
      ruta: '/empresa/facturacion/notas-credito',
      icono: 'note',
      seccion: 'Facturación (CFDI)',
      modulo: 'facturacion',
      visible: (a) => a.tienePermiso('nota_credito', 'listar'),
    },
    // -------------------------------------------------------------------------
    // 7. Contabilidad y finanzas
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Cuentas por cobrar',
      ruta: '/empresa/contabilidad/cuentas-por-cobrar',
      icono: 'payments',
      seccion: 'Contabilidad y finanzas',
      modulo: 'contabilidad',
      visible: (a) => a.tienePermiso('cuenta_por_cobrar', 'listar'),
    },
    {
      etiqueta: 'Cuentas por pagar',
      ruta: '/empresa/contabilidad/cuentas-por-pagar',
      icono: 'credit_card',
      seccion: 'Contabilidad y finanzas',
      modulo: 'contabilidad',
      visible: (a) => a.tienePermiso('cuenta_por_pagar', 'listar'),
    },
    {
      etiqueta: 'Pólizas',
      ruta: '/empresa/contabilidad/polizas',
      icono: 'book',
      seccion: 'Contabilidad y finanzas',
      modulo: 'contabilidad',
      visible: (a) => a.tienePermiso('poliza_contable', 'listar'),
    },
    {
      etiqueta: 'Reportes financieros',
      ruta: '/empresa/contabilidad/reportes',
      icono: 'assessment',
      seccion: 'Contabilidad y finanzas',
      modulo: 'contabilidad',
      visible: (a) => a.tienePermiso('reporte_financiero', 'leer'),
    },
    // -------------------------------------------------------------------------
    // 8. Tesoreria
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Tesorería',
      ruta: '/empresa/tesoreria/cuentas-bancarias',
      icono: 'account_balance',
      seccion: 'Tesorería',
      modulo: 'tesoreria',
      visible: (a) => a.tienePermiso('cuenta_bancaria', 'listar'),
    },
    // -------------------------------------------------------------------------
    // 9. Activos fijos
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Activos fijos',
      ruta: '/empresa/activos/activos-fijos',
      icono: 'apartment',
      seccion: 'Activos fijos',
      modulo: 'activos-fijos',
      visible: (a) => a.tienePermiso('activo_fijo', 'listar'),
    },
    // -------------------------------------------------------------------------
    // 10. RH y nomina
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Empleados',
      ruta: '/empresa/rh-nomina/empleados',
      icono: 'badge',
      seccion: 'RH y nómina',
      modulo: 'rh-nomina',
      visible: (a) => a.tienePermiso('empleado', 'listar'),
    },
    {
      etiqueta: 'Nómina',
      ruta: '/empresa/rh-nomina/nomina',
      icono: 'paid',
      seccion: 'RH y nómina',
      modulo: 'rh-nomina',
      visible: (a) => a.tienePermiso('nomina', 'listar'),
    },
    {
      etiqueta: 'Organización',
      ruta: '/empresa/rh-nomina/organizacion',
      icono: 'diversity_3',
      seccion: 'RH y nómina',
      modulo: 'rh-nomina',
      visible: (a) => a.tienePermiso('puesto', 'listar'),
    },
    // -------------------------------------------------------------------------
    // 11. Mantenimiento (Vertical_Anuncios)
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Contratos de mantenimiento',
      ruta: '/empresa/mantenimiento/contratos',
      icono: 'handyman',
      seccion: 'Mantenimiento',
      modulo: 'mantenimiento',
      // Vertical_Anuncios: permiso Y giro anuncios-luminosos (Req 9.2, 9.3).
      visible: (a) => a.tienePermiso('contrato_mantenimiento', 'listar') && a.esGiro(GIRO_ANUNCIOS),
    },
    {
      etiqueta: 'Tickets de servicio',
      ruta: '/empresa/mantenimiento/tickets',
      icono: 'support_agent',
      seccion: 'Mantenimiento',
      modulo: 'mantenimiento',
      // Vertical_Anuncios: permiso Y giro anuncios-luminosos (Req 9.2, 9.3).
      visible: (a) => a.tienePermiso('ticket_servicio', 'listar') && a.esGiro(GIRO_ANUNCIOS),
    },
    // -------------------------------------------------------------------------
    // 12. Redes sociales
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Conexiones',
      ruta: '/empresa/social/conexiones',
      icono: 'hub',
      seccion: 'Redes sociales',
      modulo: 'redes-sociales',
      visible: (a) => a.tienePermiso('cuenta_canal_social', 'listar'),
    },
    {
      etiqueta: 'Bandeja unificada',
      ruta: '/empresa/social/bandeja',
      icono: 'forum',
      seccion: 'Redes sociales',
      modulo: 'redes-sociales',
      visible: (a) => a.tienePermiso('bandeja', 'leer'),
    },
    {
      etiqueta: 'Publicaciones',
      ruta: '/empresa/social/publicaciones',
      icono: 'campaign',
      seccion: 'Redes sociales',
      modulo: 'redes-sociales',
      visible: (a) => a.tienePermiso('publicacion_social', 'listar'),
    },
    {
      etiqueta: 'Campañas',
      ruta: '/empresa/social/campanas',
      icono: 'ads_click',
      seccion: 'Redes sociales',
      modulo: 'redes-sociales',
      visible: (a) => a.tienePermiso('campana_publicitaria', 'listar'),
    },
    {
      etiqueta: 'Analítica social',
      ruta: '/empresa/social/analitica',
      icono: 'insights',
      seccion: 'Redes sociales',
      modulo: 'redes-sociales',
      visible: (a) => a.tienePermiso('analitica_social', 'leer'),
    },
    // -------------------------------------------------------------------------
    // 13. Estrategia (unica entrada: la vista completa de OKR/resultados clave)
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Estrategia',
      ruta: '/empresa/estrategia-vistas',
      icono: 'flag_circle',
      seccion: 'Estrategia',
      modulo: 'estrategia',
      visible: (a) => a.tienePermiso('objetivo_estrategico', 'listar'),
    },
    // -------------------------------------------------------------------------
    // 14. Presupuestos (unica entrada: la vista completa con variacion/alta)
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Presupuestos',
      ruta: '/empresa/presupuestos-vistas',
      icono: 'savings',
      seccion: 'Presupuestos',
      modulo: 'presupuestos',
      visible: (a) => a.tienePermiso('presupuesto', 'listar'),
    },
    // -------------------------------------------------------------------------
    // 15. Reportes y BI
    // -------------------------------------------------------------------------
    {
      etiqueta: 'Tablero de indicadores',
      ruta: '/empresa/reportes/tablero',
      icono: 'dashboard',
      seccion: 'Reportes y BI',
      modulo: 'reportes-bi',
      visible: (a) => a.tienePermiso('tablero', 'leer'),
    },
    {
      etiqueta: 'Inteligencia de negocio',
      ruta: '/empresa/reportes/inteligencia',
      icono: 'query_stats',
      seccion: 'Reportes y BI',
      modulo: 'reportes-bi',
      visible: (a) => a.tienePermiso('inteligencia_negocio', 'leer'),
    },
    {
      etiqueta: 'Tableros personalizados',
      ruta: '/empresa/reportes/tableros-personalizados',
      icono: 'space_dashboard',
      seccion: 'Reportes y BI',
      modulo: 'reportes-bi',
      visible: (a) => a.tienePermiso('inteligencia_negocio', 'leer'),
    },
  ];

  /** Menu del ambito de portal del cliente (Req 45). */
  private readonly itemsPortal: DefinicionItem[] = [
    {
      etiqueta: 'Mi portal',
      ruta: '/portal/inicio',
      icono: 'home',
      visible: () => true,
    },
    {
      etiqueta: 'Mis cotizaciones',
      ruta: '/portal/cotizaciones',
      icono: 'request_quote',
      visible: () => true,
    },
    {
      etiqueta: 'Mis pruebas de diseño',
      ruta: '/portal/pruebas-diseno',
      icono: 'draw',
      visible: () => true,
    },
    {
      etiqueta: 'Mis proyectos',
      ruta: '/portal/proyectos',
      icono: 'account_tree',
      visible: () => true,
    },
    {
      etiqueta: 'Mis tickets',
      ruta: '/portal/tickets',
      icono: 'support_agent',
      visible: () => true,
    },
    {
      etiqueta: 'Mis facturas',
      ruta: '/portal/facturas',
      icono: 'receipt_long',
      visible: () => true,
    },
  ];

  /**
   * Items de navegacion visibles para el Usuario actual, segun su ambito y sus
   * permisos (deny-by-default). Reactivo: cambia con la sesion.
   */
  readonly items = computed<ItemNavegacion[]>(() => {
    const ambito = this.auth.ambito();
    const definiciones =
      ambito === 'plataforma'
        ? this.itemsPlataforma
        : ambito === 'portal'
          ? this.itemsPortal
          : this.itemsEmpresa;
    return definiciones
      .filter((d) => this.esVisible(d, ambito))
      .map(({ etiqueta, ruta, icono }) => ({ etiqueta, ruta, icono }));
  });

  /**
   * Decide si un item es visible: exige su predicado de permiso/rol/giro y, solo
   * en el ambito empresa, que el tenant tenga contratado el Modulo del item
   * (gating por modulo, deny-by-default). Los items sin `modulo` y los ambitos
   * plataforma/portal no aplican gating por modulo.
   */
  private esVisible(def: DefinicionItem, ambito: string): boolean {
    if (!def.visible(this.auth)) {
      return false;
    }
    if (ambito !== 'empresa' || !def.modulo) {
      return true;
    }
    // Gating por modulo con la lista VIVA (si esta cargada) o el claim del JWT
    // (fallback). Leer el signal vivo dentro del computed que invoca a esVisible
    // hace que el menu se repinte cuando cambian los Modulos contratados, sin
    // necesidad de re-login (Req: reflejar altas/bajas de plan al navegar).
    return this.modulosEmpresa.tieneModulo(def.modulo);
  }

  /**
   * Navegacion visible agrupada por seccion, preservando el orden de definicion
   * (deny-by-default). Los items con la misma `seccion` consecutiva se agrupan
   * bajo un encabezado; los items sin `seccion` forman un grupo sin titulo. Los
   * grupos que quedan vacios tras el filtrado por permisos se omiten. Lo consume
   * el shell para pintar el menu con encabezados de seccion accesibles.
   */
  readonly grupos = computed<GrupoNavegacion[]>(() => {
    const ambito = this.auth.ambito();
    const definiciones =
      ambito === 'plataforma'
        ? this.itemsPlataforma
        : ambito === 'portal'
          ? this.itemsPortal
          : this.itemsEmpresa;

    const grupos: GrupoNavegacion[] = [];
    for (const def of definiciones) {
      if (!this.esVisible(def, ambito)) {
        continue;
      }
      const titulo = def.seccion ?? null;
      const ultimo = grupos.at(-1);
      const item: ItemNavegacion = { etiqueta: def.etiqueta, ruta: def.ruta, icono: def.icono };
      if (ultimo && ultimo.titulo === titulo) {
        ultimo.items.push(item);
      } else {
        grupos.push({ titulo, items: [item] });
      }
    }
    return grupos;
  });
}



