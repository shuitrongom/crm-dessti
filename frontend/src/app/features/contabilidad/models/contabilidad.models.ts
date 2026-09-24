// =============================================================================
// Modelos del modulo Contabilidad / finanzas (Req 36, 38, 39, 42)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (com.empresa.crm.contabilidad.*). Los importes llegan como decimales (escala 2)
// y se formatean con pipes; nunca se opera con float para calculos de negocio.
// =============================================================================

// --- Cuentas por cobrar (CxC) y pagos (Req 36) ------------------------------

/** Estados de una CxC / CxP (etiquetas valorBd del backend). */
export type EstadoCuenta = 'pendiente' | 'parcial' | 'pagada' | 'cancelada';

/** Cuenta por cobrar (CuentaPorCobrarDto). */
export interface CuentaPorCobrar {
  id: string;
  facturaId: string;
  clienteId: string;
  total: number;
  saldo: number;
  estado: EstadoCuenta;
  fechaEmision: string;
  fechaVencimiento: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Aplicacion de un pago a una factura (AplicacionPagoDto). */
export interface AplicacionPago {
  id: string;
  cuentaPorCobrarId: string;
  facturaId: string;
  montoAplicado: number;
}

/** Pago de cliente con su desglose (PagoClienteDto). */
export interface PagoCliente {
  id: string;
  clienteId: string;
  monto: number;
  fechaPago: string;
  formaPago: string | null;
  esParcialidad: boolean;
  complementoFolioFiscal: string | null;
  complementoFechaTimbrado: string | null;
  aplicaciones: AplicacionPago[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Linea de aplicacion en el registro de un pago (AplicacionPagoRequest). */
export interface LineaAplicacionPago {
  facturaId: string;
  monto: number;
}

/** Cuerpo de POST /contabilidad/pagos-cliente (RegistrarPagoClienteRequest). */
export interface RegistrarPagoClienteRequest {
  clienteId: string;
  monto: number;
  formaPago: string | null;
  esParcialidad: boolean | null;
  aplicaciones: LineaAplicacionPago[];
}

// --- Catalogo de cuentas (Req 38.1) -----------------------------------------

/** Cuenta del catalogo contable (CuentaContableDto). */
export interface CuentaContable {
  id: string;
  codigo: string;
  nombre: string;
  tipo: string;
  naturaleza: string;
  activa: boolean;
  /** Codigo agrupador del SAT amarrado (Anexo 24), o null si no esta amarrada. */
  codigoAgrupadorSat: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

// --- Polizas contables (Req 38) ---------------------------------------------

/** Renglon de una poliza (MovimientoPolizaDto). */
export interface MovimientoPoliza {
  id: string;
  cuentaContableId: string;
  cargo: number;
  abono: number;
}

/** Poliza contable balanceada (PolizaContableDto). */
export interface PolizaContable {
  id: string;
  fecha: string;
  tipo: string;
  concepto: string;
  origen: string | null;
  origenId: string | null;
  polizaRevertidaId: string | null;
  totalCargos: number;
  totalAbonos: number;
  renglones: MovimientoPoliza[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Linea de una poliza a registrar (RenglonPolizaRequest). */
export interface RenglonPolizaRequest {
  cuentaContableId: string;
  cargo: number;
  abono: number;
}

/** Cuerpo de POST /contabilidad/polizas (RegistrarPolizaRequest). */
export interface RegistrarPolizaRequest {
  fecha: string;
  tipo: string;
  concepto: string;
  origen: string | null;
  origenId: string | null;
  renglones: RenglonPolizaRequest[];
}

// --- Cuentas por pagar (CxP) (Req 42) ---------------------------------------

/** Cuenta por pagar (CuentaPorPagarDto). */
export interface CuentaPorPagar {
  id: string;
  facturaProveedorId: string;
  proveedorId: string;
  total: number;
  saldo: number;
  estado: EstadoCuenta;
  fechaRegistro: string;
  fechaVencimiento: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

// --- Reportes financieros (Req 39) — solo lectura ---------------------------

/** Ingresos por periodo (IngresosPeriodoDto). */
export interface IngresosPeriodo {
  desde: string;
  hasta: string;
  clienteId: string | null;
  numeroFacturas: number;
  subtotal: number;
  iva: number;
  retenciones: number;
  total: number;
}

/** IVA trasladado/retenido por periodo (IvaPeriodoDto). */
export interface IvaPeriodo {
  desde: string;
  hasta: string;
  clienteId: string | null;
  numeroFacturas: number;
  baseGravable: number;
  ivaTrasladado: number;
  ivaRetenido: number;
}
