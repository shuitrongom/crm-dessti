// =============================================================================
// Estado de una solicitud asincrona (Req 54)
// -----------------------------------------------------------------------------
// Modelo generico y reutilizable para representar el ciclo de vida de una
// consulta de datos (cargando / con datos / vacio / error). Lo consumen el
// componente StateContainer y las vistas de features para renderizar los
// estados de carga, vacio y error de forma consistente.
// =============================================================================

/** Fase del ciclo de vida de una solicitud de datos. */
export type FaseSolicitud = 'cargando' | 'ok' | 'vacio' | 'error';

/**
 * Instantanea del estado de una solicitud.
 *
 * @template T tipo de los datos cuando la solicitud es exitosa.
 */
export interface EstadoSolicitud<T> {
  fase: FaseSolicitud;
  /** Datos disponibles cuando `fase === 'ok'`. */
  datos?: T;
  /** Mensaje de error de negocio (sin detalle tecnico, Req 56) cuando `fase === 'error'`. */
  mensajeError?: string;
}

/** Estado inicial de carga. */
export function cargando<T>(): EstadoSolicitud<T> {
  return { fase: 'cargando' };
}

/** Estado exitoso con datos; se marca `vacio` cuando la coleccion no tiene elementos. */
export function conDatos<T>(datos: T, estaVacio = false): EstadoSolicitud<T> {
  return { fase: estaVacio ? 'vacio' : 'ok', datos };
}

/** Estado de error con un mensaje apto para el Usuario (Req 56). */
export function conError<T>(mensajeError: string): EstadoSolicitud<T> {
  return { fase: 'error', mensajeError };
}
