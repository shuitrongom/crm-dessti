// =============================================================================
// Comparativos de Inteligencia de Negocio (Req 48.1)
// -----------------------------------------------------------------------------
// Logica PURA para derivar la tendencia y la variacion porcentual de un
// indicador respecto de su periodo anterior. El backend ya entrega la variacion
// absoluta (valor - comparativo); aqui derivamos la TENDENCIA (subida/bajada/
// neutra/ninguna) y el porcentaje de cambio para la presentacion accesible
// (icono + texto, no solo color). Son funciones deterministas y sin efectos.
// =============================================================================

import { Indicador } from './models/reportes.models';

/** Tendencia derivada del comparativo de un indicador. */
export type Tendencia = 'subida' | 'bajada' | 'neutra' | 'ninguna';

/** Comparativo derivado listo para presentacion. */
export interface Comparativo {
  /** Tendencia del indicador respecto del periodo anterior. */
  tendencia: Tendencia;
  /** Variacion absoluta (valor - comparativo); null si no hay comparativo. */
  variacion: number | null;
  /**
   * Variacion porcentual respecto del comparativo, redondeada a 2 decimales;
   * null si no hay comparativo o el comparativo es cero (crecimiento indefinido).
   */
  porcentaje: number | null;
  /** Icono de Material Symbols que representa la tendencia. */
  icono: string;
  /** Texto accesible de la tendencia (no solo color). */
  texto: string;
}

/** Redondea a 2 decimales de forma estable (evita -0). */
function redondear2(valor: number): number {
  return Math.round((valor + Number.EPSILON) * 100) / 100;
}

/**
 * Funcion pura: deriva la tendencia de una variacion absoluta.
 *
 * @param variacion diferencia valor - comparativo; null si no hay comparativo.
 * @returns 'ninguna' si es null, 'neutra' si es 0, 'subida' si > 0, 'bajada' si < 0.
 */
export function tendenciaDe(variacion: number | null | undefined): Tendencia {
  if (variacion === null || variacion === undefined) {
    return 'ninguna';
  }
  if (variacion > 0) {
    return 'subida';
  }
  if (variacion < 0) {
    return 'bajada';
  }
  return 'neutra';
}

/**
 * Funcion pura: calcula la variacion porcentual de un indicador respecto de su
 * comparativo. Devuelve null cuando no hay comparativo o el comparativo es cero
 * (el crecimiento porcentual seria indefinido/infinito).
 *
 * @param valor       valor del periodo actual.
 * @param comparativo valor del periodo anterior; null si no hay historico.
 * @returns porcentaje redondeado a 2 decimales, o null.
 */
export function porcentajeVariacion(
  valor: number,
  comparativo: number | null | undefined,
): number | null {
  if (comparativo === null || comparativo === undefined || comparativo === 0) {
    return null;
  }
  return redondear2(((valor - comparativo) / Math.abs(comparativo)) * 100);
}

/** Icono de Material Symbols de una tendencia. */
export function iconoTendencia(tendencia: Tendencia): string {
  switch (tendencia) {
    case 'subida':
      return 'trending_up';
    case 'bajada':
      return 'trending_down';
    default:
      return 'trending_flat';
  }
}

/** Texto accesible de una tendencia (siempre acompana al color/icono). */
export function textoTendencia(tendencia: Tendencia): string {
  switch (tendencia) {
    case 'subida':
      return 'al alza';
    case 'bajada':
      return 'a la baja';
    case 'neutra':
      return 'sin cambio';
    default:
      return 'sin comparativo';
  }
}

/**
 * Funcion pura: compone el comparativo de presentacion de un indicador a partir
 * de su valor y su comparativo (usando la variacion del backend si viene, o
 * derivandola). Deriva tendencia, porcentaje, icono y texto accesible (Req 48.1).
 *
 * @param indicador indicador con valor, comparativo y (opcional) variacion.
 * @returns el comparativo derivado listo para la vista.
 */
export function comparativoDe(indicador: Indicador): Comparativo {
  const tieneComparativo = indicador.comparativo !== null && indicador.comparativo !== undefined;
  const variacion = tieneComparativo
    ? (indicador.variacion ?? indicador.valor - (indicador.comparativo as number))
    : null;
  const tendencia = tendenciaDe(variacion);
  return {
    tendencia,
    variacion,
    porcentaje: tieneComparativo
      ? porcentajeVariacion(indicador.valor, indicador.comparativo)
      : null,
    icono: iconoTendencia(tendencia),
    texto: textoTendencia(tendencia),
  };
}
