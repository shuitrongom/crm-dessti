// =============================================================================
// Traduccion de errores de API a mensajes de negocio (Req 56)
// -----------------------------------------------------------------------------
// El backend responde errores con Problem Details (RFC 7807): { title, detail,
// status, errores? }. Aqui se traducen a mensajes en espanol aptos para el
// Usuario, SIN filtrar detalle tecnico interno (Req 56.2). Cuando el backend
// aporta un `detail`/`title` de negocio se prefiere; en su defecto se usa un
// mensaje generico por codigo de estado.
// =============================================================================

import { HttpErrorResponse } from '@angular/common/http';

/** Detalle de un error de validacion por campo (RFC 7807 extendido). */
export interface ErrorCampo {
  campo: string;
  mensaje: string;
}

/** Forma del cuerpo Problem Details que devuelve el backend. */
interface ProblemDetails {
  title?: string;
  detail?: string;
  status?: number;
  errores?: ErrorCampo[];
}

/** Mensajes genericos por codigo de estado (Req 56.1). */
const MENSAJES_POR_ESTADO: Record<number, string> = {
  0: 'No fue posible conectar con el servidor. Revisa tu conexion e intenta de nuevo.',
  400: 'La solicitud no es valida. Revisa los datos e intenta de nuevo.',
  401: 'Tu sesion no es valida o expiro. Inicia sesion nuevamente.',
  403: 'No tienes permiso para realizar esta accion.',
  404: 'No se encontro el recurso solicitado.',
  409: 'La operacion no se pudo completar por un conflicto con el estado actual.',
  422: 'No se pudo procesar la solicitud por una regla de negocio.',
  429: 'Se recibieron demasiadas solicitudes. Espera un momento e intenta de nuevo.',
  500: 'Ocurrio un problema en el servidor. Intenta mas tarde.',
};

/**
 * Traduce un error HTTP a un mensaje de negocio en espanol (Req 56).
 *
 * @param error error capturado (idealmente {@link HttpErrorResponse}).
 * @returns un mensaje apto para mostrar al Usuario.
 */
export function mensajeDeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const cuerpo = (error.error ?? {}) as ProblemDetails;

    // Prefiere el mensaje de negocio del backend cuando existe y es de texto.
    if (typeof cuerpo.detail === 'string' && cuerpo.detail.trim().length > 0) {
      return cuerpo.detail;
    }
    if (typeof cuerpo.title === 'string' && cuerpo.title.trim().length > 0) {
      return cuerpo.title;
    }
    return MENSAJES_POR_ESTADO[error.status] ?? MENSAJES_POR_ESTADO[500];
  }
  return MENSAJES_POR_ESTADO[500];
}

/** Extrae, si existen, los errores de validacion por campo (Req 56.4). */
export function erroresDeCampo(error: unknown): ErrorCampo[] {
  if (error instanceof HttpErrorResponse) {
    const cuerpo = (error.error ?? {}) as ProblemDetails;
    if (Array.isArray(cuerpo.errores)) {
      return cuerpo.errores;
    }
  }
  return [];
}
