// =============================================================================
// Maquina de estados de la Nomina (Req 41) — habilitacion de acciones en UI
// -----------------------------------------------------------------------------
// La transicion de estado la impone y valida el backend; aqui solo se decide que
// accion mostrar segun el estado actual, para no ofrecer botones invalidos. El
// flujo es lineal: borrador -> calculada -> autorizada -> timbrada -> pagada.
// =============================================================================

import { EstadoNomina } from './models/rhnomina.models';

/** Accion de transicion de una Nomina expuesta en la UI. */
export interface AccionNomina {
  /** Clave de la operacion del servicio (calcular/autorizar/timbrar/pagar). */
  accion: 'calcular' | 'autorizar' | 'timbrar' | 'pagar';
  /** Etiqueta legible en espanol. */
  etiqueta: string;
  /** Icono Material Symbols. */
  icono: string;
}

/** Accion habilitada por estado actual (una unica accion de avance por estado). */
const ACCION_POR_ESTADO: Record<EstadoNomina, AccionNomina | null> = {
  borrador: { accion: 'calcular', etiqueta: 'Calcular', icono: 'calculate' },
  calculada: { accion: 'autorizar', etiqueta: 'Autorizar', icono: 'task_alt' },
  autorizada: { accion: 'timbrar', etiqueta: 'Timbrar', icono: 'verified' },
  timbrada: { accion: 'pagar', etiqueta: 'Marcar pagada', icono: 'payments' },
  pagada: null,
};

/**
 * Devuelve la accion de avance permitida por la UI para el estado actual, o
 * {@code null} si el estado es terminal (pagada) o desconocido.
 */
export function accionDeNomina(estado: EstadoNomina): AccionNomina | null {
  return ACCION_POR_ESTADO[estado] ?? null;
}
