// =============================================================================
// Mapeo de estados de negocio a tono semantico (Req 57)
// -----------------------------------------------------------------------------
// Traduce las etiquetas de estado (valorBd del backend) a un tono para el
// EstadoChip y a una etiqueta legible en espanol. Centralizado para reutilizarse
// por todas las vistas del bloque 51.2 (finanzas, RH, mantenimiento).
// =============================================================================

import { TonoEstado } from './estado-chip/estado-chip';

/** Diccionario de tonos por etiqueta de estado. */
const TONOS: Record<string, TonoEstado> = {
  // Genericos favorables / desfavorables
  aprobada: 'exito',
  aprobado: 'exito',
  conciliada: 'exito',
  cumplido: 'exito',
  pagada: 'exito',
  pagado: 'exito',
  autorizada: 'exito',
  autorizado: 'exito',
  cerrada: 'exito',
  cerrado: 'exito',
  resuelto: 'exito',
  timbrada: 'exito',
  vigente: 'exito',
  activo: 'exito',
  recibida_total: 'exito',
  // En curso / informativos
  borrador: 'neutro',
  enviada: 'info',
  abierta: 'info',
  abierto: 'info',
  registrada: 'info',
  calculada: 'info',
  en_proceso: 'info',
  asignado: 'info',
  recibida_parcial: 'info',
  en_curso: 'info',
  // Advertencias
  discrepancia: 'advertencia',
  vencida: 'advertencia',
  parcial: 'advertencia',
  en_riesgo: 'advertencia',
  pendiente: 'advertencia',
  // Errores / finales negativos
  rechazada: 'error',
  rechazado: 'error',
  cancelada: 'error',
  cancelado: 'error',
  inactivo: 'error',
  incumplido: 'error',
};

/** Devuelve el tono semantico de un estado; neutro si no esta mapeado. */
export function tonoDeEstado(estado: string | null | undefined): TonoEstado {
  if (!estado) {
    return 'neutro';
  }
  return TONOS[estado] ?? 'neutro';
}

/** Convierte una etiqueta snake_case a texto legible ("recibida_parcial" -> "Recibida parcial"). */
export function humanizarEstado(estado: string | null | undefined): string {
  if (!estado) {
    return '-';
  }
  const texto = estado.replace(/_/g, ' ');
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
