// =============================================================================
// Modelos del modulo Activos fijos (Req 44)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (com.empresa.crm.activosfijos.*). Los importes llegan como decimales (escala 2).
// =============================================================================

/** Metodo de depreciacion (etiquetas valorBd del backend). */
export type MetodoDepreciacion = 'linea_recta' | 'saldos_decrecientes';

/** Estado de un activo fijo. */
export type EstadoActivoFijo = 'activo' | 'baja' | string;

/** Activo fijo (ActivoFijoDto). */
export interface ActivoFijo {
  id: string;
  nombre: string;
  costo: number;
  fechaAdquisicion: string;
  vidaUtilMeses: number;
  metodoDepreciacion: MetodoDepreciacion;
  valorResidual: number;
  depreciacionAcumulada: number;
  estado: EstadoActivoFijo;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /activos-fijos (CrearActivoFijoRequest). */
export interface CrearActivoFijoRequest {
  nombre: string;
  costo: number;
  fechaAdquisicion: string;
  vidaUtilMeses: number;
  metodoDepreciacion: MetodoDepreciacion;
  valorResidual: number | null;
}

/** Depreciacion de periodo (DepreciacionDto). */
export interface Depreciacion {
  id: string;
  activoFijoId: string;
  periodo: string;
  monto: number;
  depreciacionAcumuladaResultante: number;
  polizaContableId: string | null;
  registradaEn: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}
