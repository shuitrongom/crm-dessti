// =============================================================================
// Maquina de estados del Ticket_Servicio (Req 20.4, 20.5) — acciones en UI
// -----------------------------------------------------------------------------
// El flujo de estados es: abierto -> asignado -> en_proceso -> resuelto ->
// cerrado. El backend valida cada transicion; aqui solo se decide que accion de
// avance ofrecer segun el estado actual. La asignacion (abierto -> asignado) se
// realiza con su propio formulario (endpoint /asignacion), por lo que no se
// incluye como transicion de estado generica.
// =============================================================================

import { EstadoTicket } from './models/mantenimiento.models';

/** Accion de transicion de estado ofrecida en la UI. */
export interface AccionTicket {
  estado: EstadoTicket;
  etiqueta: string;
  icono: string;
}

/** Accion de avance de estado por estado actual (asignacion aparte). */
const AVANCE_POR_ESTADO: Record<EstadoTicket, AccionTicket | null> = {
  abierto: null, // se asigna con el formulario de asignacion
  asignado: { estado: 'en_proceso', etiqueta: 'Iniciar atencion', icono: 'play_arrow' },
  en_proceso: { estado: 'resuelto', etiqueta: 'Marcar resuelto', icono: 'task_alt' },
  resuelto: { estado: 'cerrado', etiqueta: 'Cerrar', icono: 'check_circle' },
  cerrado: null,
};

/**
 * Devuelve la accion de avance de estado permitida por la UI para el estado
 * actual del ticket, o {@code null} si no aplica (abierto/cerrado).
 */
export function avanceDeTicket(estado: EstadoTicket): AccionTicket | null {
  return AVANCE_POR_ESTADO[estado] ?? null;
}
