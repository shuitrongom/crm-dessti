// =============================================================================
// Modelos del modulo Compras / abastecimiento (Req 29, 30, 31, 32, 33)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (com.empresa.crm.compras.*). Los estados se exponen como uniones de literales
// en minuscula, coherentes con `valorBd()` del backend. El dinero llega como
// numero decimal (BigDecimal escala 2 serializado por Jackson) y se muestra con
// pipes de formato; nunca se opera con float para calculos de negocio.
// =============================================================================

// --- Proveedores (Req 29) — ProveedorDto ------------------------------------

/** Proveedor (ProveedorDto del backend, GET /compras/proveedores/{id}). */
export interface Proveedor {
  id: string;
  nombre: string;
  rfc: string;
  email: string | null;
  telefono: string | null;
  activo: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /compras/proveedores y PUT /compras/proveedores/{id}. */
export interface GuardarProveedorRequest {
  nombre: string;
  rfc: string;
  email: string | null;
  telefono: string | null;
}

// --- Requisiciones de compra (Req 30) — RequisicionCompraDto ----------------

/** Estados de una Requisicion_Compra (etiquetas valorBd del backend). */
export type EstadoRequisicion =
  | 'borrador'
  | 'enviada'
  | 'aprobada'
  | 'rechazada'
  | 'cancelada';

/** Partida de requisicion (PartidaRequisicionDto). */
export interface PartidaRequisicion {
  id: string;
  materialId: string;
  cantidad: number;
}

/** Requisicion de compra (RequisicionCompraDto). */
export interface RequisicionCompra {
  id: string;
  estado: EstadoRequisicion;
  ordenCompraId: string | null;
  partidas: PartidaRequisicion[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /compras/requisiciones (partidas materialId + cantidad). */
export interface CrearRequisicionRequest {
  partidas: { materialId: string; cantidad: number }[];
}

/** Cuerpo de PUT /compras/requisiciones/{id}/estado. */
export interface CambiarEstadoRequest {
  estado: string;
}

/** Cuerpo de POST /compras/requisiciones/{id}/orden-compra. */
export interface GenerarOrdenCompraRequest {
  proveedorId: string;
  partidas: { materialId: string; cantidad: number; precioUnitario: number }[];
}

// --- Ordenes de compra (Req 31) — OrdenCompraDto ----------------------------

/** Estados de una Orden_Compra (etiquetas valorBd del backend). */
export type EstadoOrdenCompra =
  | 'abierta'
  | 'recibida_parcial'
  | 'recibida_total'
  | 'cerrada'
  | 'cancelada';

/** Partida de orden de compra (PartidaOrdenCompraDto). */
export interface PartidaOrdenCompra {
  id: string;
  materialId: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

/** Orden de compra (OrdenCompraDto). */
export interface OrdenCompra {
  id: string;
  proveedorId: string;
  requisicionCompraId: string | null;
  estado: EstadoOrdenCompra;
  total: number;
  partidas: PartidaOrdenCompra[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /compras/ordenes-compra. */
export interface CrearOrdenCompraRequest {
  proveedorId: string;
  partidas: { materialId: string; cantidad: number; precioUnitario: number }[];
}

// --- Recepciones de mercancia (Req 32) — RecepcionMercanciaDto --------------

/** Renglon recibido (PartidaRecepcionDto). */
export interface PartidaRecepcion {
  id: string;
  partidaOrdenCompraId: string;
  materialId: string;
  cantidadRecibida: number;
}

/** Recepcion de mercancia (RecepcionMercanciaDto). */
export interface RecepcionMercancia {
  id: string;
  ordenCompraId: string;
  recibidaEn: string;
  partidas: PartidaRecepcion[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /compras/recepciones. */
export interface RegistrarRecepcionRequest {
  ordenCompraId: string;
  partidas: { partidaOrdenCompraId: string; cantidadRecibida: number }[];
}

// --- Facturas de proveedor / conciliacion 3 vias (Req 33) -------------------

/** Estados de una Factura_Proveedor (etiquetas valorBd del backend). */
export type EstadoFacturaProveedor = 'registrada' | 'conciliada' | 'discrepancia' | 'pagada';

/** Factura de proveedor (FacturaProveedorDto). */
export interface FacturaProveedor {
  id: string;
  ordenCompraId: string;
  proveedorId: string;
  folioProveedor: string;
  monto: number;
  estado: EstadoFacturaProveedor;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /compras/facturas-proveedor. */
export interface RegistrarFacturaProveedorRequest {
  ordenCompraId: string;
  folioProveedor: string;
  monto: number;
}
