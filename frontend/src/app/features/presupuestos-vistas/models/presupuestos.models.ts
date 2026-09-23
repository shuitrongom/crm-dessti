// =============================================================================
// Modelos del modulo Presupuestos (vistas de negocio) (Req 62)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (com.empresa.crm.presupuestos.*). La variacion real vs estimado (importe y %)
// la calcula el servidor de solo lectura; la UI solo la formatea y destaca.
// =============================================================================

/** Presupuesto por area y periodo (PresupuestoDto). */
export interface Presupuesto {
  id: string;
  area: string;
  periodo: string;
  ingresosEstimados: number;
  egresosEstimados: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /presupuestos (CrearPresupuestoRequest). */
export interface CrearPresupuestoRequest {
  area: string;
  periodo: string;
  ingresosEstimados: number;
  egresosEstimados: number;
}

/** Cuerpo de PUT /presupuestos/{id} (ActualizarPresupuestoRequest). */
export interface ActualizarPresupuestoRequest {
  ingresosEstimados: number;
  egresosEstimados: number;
}

/** Variacion real vs estimado (VariacionPresupuestoDto), solo lectura. */
export interface VariacionPresupuesto {
  presupuestoId: string;
  area: string;
  periodo: string;
  ingresosEstimados: number;
  ingresosReales: number;
  variacionIngresosImporte: number;
  variacionIngresosPorcentaje: number;
  ingresosFavorable: boolean;
  ingresosSuperaUmbral: boolean;
  egresosEstimados: number;
  egresosReales: number;
  variacionEgresosImporte: number;
  variacionEgresosPorcentaje: number;
  egresosFavorable: boolean;
  egresosSuperaUmbral: boolean;
  destacar: boolean;
}
