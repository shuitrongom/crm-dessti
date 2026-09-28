// =============================================================================
// Modelos del submódulo Cierre de periodo contable (candado contable)
// -----------------------------------------------------------------------------
// Reflejan el DTO del backend (com.dessti.crm.contabilidad.polizas.application.
// PeriodoContableDto). Un periodo sin cerrar se representa con estado 'abierto' y
// metadatos de cierre/reapertura nulos.
// =============================================================================

/** Estado de cierre de un periodo mensual. */
export type EstadoPeriodo = 'abierto' | 'cerrado';

/** Estado y metadatos de un periodo contable mensual. */
export interface PeriodoContable {
  /** Año del periodo. */
  readonly anio: number;
  /** Mes del periodo (1..12). */
  readonly mes: number;
  /** Estado del periodo: 'abierto' o 'cerrado'. */
  readonly estado: EstadoPeriodo;
  /** Instante ISO-8601 del último cierre, o null si nunca se cerró. */
  readonly fechaCierre: string | null;
  /** Actor que realizó el último cierre, o null. */
  readonly cerradoPor: string | null;
  /** Instante ISO-8601 de la última reapertura, o null. */
  readonly fechaReapertura: string | null;
  /** Actor que realizó la última reapertura, o null. */
  readonly reabiertoPor: string | null;
  /** Motivo de la última reapertura, o null. */
  readonly motivoReapertura: string | null;
}
