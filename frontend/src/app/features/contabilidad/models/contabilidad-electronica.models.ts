// =============================================================================
// Modelos del submódulo Contabilidad Electrónica SAT (Anexo 24)
// -----------------------------------------------------------------------------
// Reflejan los DTO del backend (com.dessti.crm.contabilidad.electronica.*):
//   - Vista previa de catálogo/balanza/pólizas antes de exportar.
//   - Código agrupador del SAT para el autocompletar del amarre.
// Los XML se descargan como Blob (no se modelan aquí).
// =============================================================================

/** Vista previa del Catálogo de Cuentas XML (Req 5.1, 5.2). */
export interface VistaPreviaCatalogo {
  /** Número de cuentas activas amarradas que se exportarán. */
  readonly cuentasAmarradas: number;
  /** Códigos de cuentas activas SIN código agrupador (advertencia). */
  readonly cuentasSinAmarrar: readonly string[];
}

/** Vista previa de la Balanza de Comprobación XML (Req 5.1, 5.3). */
export interface VistaPreviaBalanza {
  /** Número de cuentas con movimiento/saldo en el periodo. */
  readonly numeroCuentas: number;
  /** Total de cargos del periodo. */
  readonly totalDebe: number;
  /** Total de abonos del periodo. */
  readonly totalHaber: number;
  /** true si la balanza cuadra (totalDebe == totalHaber). */
  readonly cuadra: boolean;
}

/** Vista previa del Pólizas del Periodo XML (Req 5.1). */
export interface VistaPreviaPolizas {
  /** Número de pólizas del periodo. */
  readonly numeroPolizas: number;
  /** Número total de renglones (transacciones) del periodo. */
  readonly numeroTransacciones: number;
}

/** Código agrupador del SAT (Apartado B del Anexo 24) para el amarre. */
export interface CodigoAgrupadorSat {
  /** Clave del agrupador (p. ej. 101.01). */
  readonly codigo: string;
  /** Nombre descriptivo (p. ej. Caja y efectivo). */
  readonly nombre: string;
  /** 1 = cuenta de mayor, 2 = subcuenta de primer nivel. */
  readonly nivel: number;
  /** D (deudora) o A (acreedora). */
  readonly naturaleza: string;
}
