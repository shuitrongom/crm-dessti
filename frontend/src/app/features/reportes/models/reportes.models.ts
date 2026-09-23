// =============================================================================
// Modelos del modulo Reportes / BI (Req 22, 48)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (com.empresa.crm.reportesbi.*). Los valores de indicador llegan como decimales
// (BigDecimal serializado a number); los instantes como ISO-8601 UTC (string).
// =============================================================================

/**
 * Indicador individual del Tablero o del consolidado (IndicadorDto). El backend
 * ya calcula la variacion = valor - comparativo cuando hay historico.
 * Coincide con el modelo `Indicador` del shared IndicatorCard.
 */
export interface Indicador {
  clave: string;
  etiqueta: string;
  valor: number;
  unidad: string;
  /** Valor del periodo anterior; null si no hay historico. */
  comparativo: number | null;
  /** Diferencia valor - comparativo; null si no hay comparativo. */
  variacion: number | null;
}

/** Indicadores agrupados por area (IndicadoresAreaDto). */
export interface IndicadoresArea {
  area: string;
  indicadores: Indicador[];
}

/** Tablero de indicadores por area (TableroDto). */
export interface Tablero {
  generadoEn: string;
  desde: string | null;
  hasta: string | null;
  clienteId: string | null;
  areas: IndicadoresArea[];
}

/** Filtros del Tablero (Req 22.3). */
export interface FiltroTablero {
  desde?: string | null;
  hasta?: string | null;
  clienteId?: string | null;
}

/** Analisis consolidado de Inteligencia de Negocio (InteligenciaNegocioDto). */
export interface InteligenciaNegocio {
  generadoEn: string;
  desde: string | null;
  hasta: string | null;
  desdeComparativo: string | null;
  hastaComparativo: string | null;
  area: string | null;
  dimension: string | null;
  areas: IndicadoresArea[];
}

/** Filtros del consolidado (Req 48.4). */
export interface FiltroInteligencia {
  desde?: string | null;
  hasta?: string | null;
  area?: string | null;
  dimension?: string | null;
}

/** Widget de un tablero personalizado (WidgetTableroDto). */
export interface WidgetTablero {
  id: string;
  area: string;
  metrica: string;
  orden: number;
  configuracionJson: string | null;
}

/** Tablero analitico personalizado (TableroPersonalizadoDto). */
export interface TableroPersonalizado {
  id: string;
  nombre: string;
  descripcion: string | null;
  propietarioUsuarioId: string | null;
  widgets: WidgetTablero[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de un widget en el alta/edicion de un tablero personalizado. */
export interface WidgetRequest {
  area: string;
  metrica: string;
  orden: number;
  configuracionJson?: string | null;
}

/** Cuerpo de POST/PUT tableros-personalizados (GuardarTableroPersonalizadoRequest). */
export interface GuardarTableroPersonalizadoRequest {
  nombre: string;
  descripcion?: string | null;
  propietarioUsuarioId?: string | null;
  widgets: WidgetRequest[];
}
