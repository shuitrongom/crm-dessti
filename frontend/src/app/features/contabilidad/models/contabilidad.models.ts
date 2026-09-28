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

/** Programación de pago de una CxP (ProgramacionPagoDto). */
export interface ProgramacionPago {
  id: string;
  cuentaPorPagarId: string;
  fechaProgramada: string;
  monto: number;
  aplicada: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /contabilidad/programaciones-pago (CrearProgramacionPagoRequest). */
export interface CrearProgramacionPagoRequest {
  cuentaPorPagarId: string;
  fechaProgramada: string;
  monto: number;
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

// --- Estados financieros (Req 47) — solo lectura ----------------------------

/** Balance general del periodo (BalanceGeneralDto). Property 17: activo = pasivo + capital. */
export interface BalanceGeneral {
  desde: string | null;
  hasta: string | null;
  activo: number;
  pasivo: number;
  capital: number;
  capitalBase: number;
  resultadoEjercicio: number;
  cuadra: boolean;
}

/** Estado de resultados del periodo (EstadoResultadosDto). */
export interface EstadoResultados {
  desde: string | null;
  hasta: string | null;
  ingresos: number;
  gastos: number;
  utilidad: number;
}

/** Renglón de la balanza de comprobación (BalanzaComprobacionDto.RenglonDto). */
export interface RenglonBalanza {
  cuentaId: string;
  codigo: string;
  nombre: string;
  cargos: number;
  abonos: number;
}

/** Balanza de comprobación del periodo (BalanzaComprobacionDto). */
export interface BalanzaComprobacion {
  desde: string | null;
  hasta: string | null;
  renglones: RenglonBalanza[];
  totalCargos: number;
  totalAbonos: number;
  cuadra: boolean;
}

/** Renglón de aging de un cliente (AntiguedadSaldosDto.RenglonCliente). */
export interface RenglonAgingCliente {
  clienteId: string;
  saldoTotal: number;
  rango0a30: number;
  rango31a60: number;
  rango61a90: number;
  rangoMas90: number;
}

/** Antigüedad de saldos de CxC agrupada por cliente (AntiguedadSaldosDto). */
export interface AntiguedadSaldos {
  clientes: RenglonAgingCliente[];
}

/** Renglón de aging de un proveedor (AntiguedadSaldosProveedorDto.RenglonProveedor). */
export interface RenglonAgingProveedor {
  proveedorId: string;
  saldoTotal: number;
  rango0a30: number;
  rango31a60: number;
  rango61a90: number;
  rangoMas90: number;
}

/** Antigüedad de saldos de CxP agrupada por proveedor (AntiguedadSaldosProveedorDto). */
export interface AntiguedadSaldosProveedor {
  proveedores: RenglonAgingProveedor[];
}

/** Renglón del estado de cuenta de un cliente (EstadoCuentaClienteDto.RenglonDto). */
export interface RenglonEstadoCuentaCliente {
  cxcId: string;
  facturaId: string;
  total: number;
  saldo: number;
  estado: string;
  fechaEmision: string;
  fechaVencimiento: string | null;
}

/** Estado de cuenta de un cliente (EstadoCuentaClienteDto). */
export interface EstadoCuentaCliente {
  clienteId: string;
  desde: string | null;
  hasta: string | null;
  renglones: RenglonEstadoCuentaCliente[];
  totalFacturado: number;
  saldoPendiente: number;
}

// --- Alta de cuenta contable (Req 38.1) -------------------------------------

/** Cuerpo de POST /contabilidad/cuentas-contables (CrearCuentaContableRequest). */
export interface CrearCuentaContableRequest {
  codigo: string;
  nombre: string;
  tipo: string;
  naturaleza: string;
}
