// =============================================================================
// Modelos del modulo Tesoreria (Req 43)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (com.empresa.crm.tesoreria.*). Los importes llegan como decimales (escala 2).
// =============================================================================

/** Cuenta bancaria (CuentaBancariaDto). */
export interface CuentaBancaria {
  id: string;
  nombre: string;
  banco: string;
  clabe: string | null;
  moneda: string;
  activa: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /tesoreria/cuentas-bancarias (CrearCuentaBancariaRequest). */
export interface CrearCuentaBancariaRequest {
  nombre: string;
  banco: string;
  clabe: string | null;
  moneda: string | null;
}

/** Estado de conciliacion de un movimiento bancario. */
export type EstadoConciliacionMovimiento = 'pendiente' | 'conciliado' | 'excepcion' | string;

/** Movimiento bancario (MovimientoBancarioDto). */
export interface MovimientoBancario {
  id: string;
  estadoCuentaBancarioId: string;
  cuentaBancariaId: string;
  fecha: string;
  monto: number;
  referencia: string | null;
  descripcion: string | null;
  estadoConciliacion: EstadoConciliacionMovimiento;
  polizaContableId: string | null;
  pagoId: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Estado de cuenta bancario con sus movimientos (EstadoCuentaBancarioDto). */
export interface EstadoCuentaBancario {
  id: string;
  cuentaBancariaId: string;
  periodoInicio: string;
  periodoFin: string;
  saldoInicial: number;
  saldoFinal: number;
  movimientos: MovimientoBancario[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Linea de movimiento importado (MovimientoImportadoRequest). */
export interface MovimientoImportadoRequest {
  fecha: string;
  monto: number;
  referencia: string | null;
  descripcion: string | null;
}

/** Cuerpo de POST .../estados-cuenta (ImportarEstadoCuentaRequest). */
export interface ImportarEstadoCuentaRequest {
  referenciaArchivo: string | null;
  periodoInicio: string;
  periodoFin: string;
  movimientos: MovimientoImportadoRequest[] | null;
}

/** Estado de una conciliacion bancaria. */
export type EstadoConciliacion = 'en_proceso' | 'completa' | string;

/** Conciliacion bancaria (ConciliacionBancariaDto). */
export interface ConciliacionBancaria {
  id: string;
  cuentaBancariaId: string;
  estadoCuentaBancarioId: string;
  saldoBancario: number;
  saldoContable: number;
  diferencia: number;
  estado: EstadoConciliacion;
  fecha: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}
