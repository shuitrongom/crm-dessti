// =============================================================================
// Ventana de Servicio de la mensajeria omnicanal (Req 64.6, 64.7)
// -----------------------------------------------------------------------------
// Logica PURA que refleja la guarda del backend (GuardaVentanaServicio): la
// Ventana_Servicio es el intervalo de 24 horas (configurable) contado desde el
// ultimo Mensaje_Social entrante durante el cual la Empresa puede responder con
// TEXTO LIBRE. Fuera de ese intervalo solo se admiten plantillas aprobadas.
//
// Estas funciones son deterministas y reciben `ahora` explicitamente (reloj
// inyectado), por lo que se prueban de forma trivial y sin dependencias de
// framework. La UI las usa para pintar el indicador de ventana y para decidir si
// habilitar el texto libre en el compositor. La autorizacion efectiva la reimpone
// el backend (422 fuera de la ventana con texto libre).
// =============================================================================

/** Duracion por defecto de la Ventana_Servicio: 24 horas (Req 64.6). */
export const VENTANA_HORAS_DEFECTO = 24;

/** Umbral (en minutos) para considerar la ventana "por expirar" (aviso proactivo). */
export const UMBRAL_POR_EXPIRAR_MINUTOS = 60;

const MS_POR_HORA = 60 * 60 * 1000;
const MS_POR_MINUTO = 60 * 1000;

/** Estado de la Ventana_Servicio de una Conversacion. */
export type EstadoVentana = 'dentro' | 'por_expirar' | 'expirada';

/** Resultado del calculo de la Ventana_Servicio. */
export interface VentanaServicio {
  /** Estado derivado de la ventana. */
  estado: EstadoVentana;
  /** true si aun se admite texto libre (dentro o por expirar). */
  permiteTextoLibre: boolean;
  /** Milisegundos restantes dentro de la ventana; 0 si ya expiro o no hay entrantes. */
  restanteMs: number;
  /** Minutos restantes (redondeados hacia abajo); util para el indicador. */
  restanteMinutos: number;
}

/**
 * Convierte un instante (ISO-8601 o Date) a milisegundos epoch, o null si no es
 * valido. Acepta null/undefined (sin entrantes).
 */
function aEpochMs(instante: string | Date | null | undefined): number | null {
  if (instante === null || instante === undefined) {
    return null;
  }
  const ms = instante instanceof Date ? instante.getTime() : Date.parse(instante);
  return Number.isFinite(ms) ? ms : null;
}

/**
 * Funcion pura: calcula el estado de la Ventana_Servicio de una Conversacion.
 *
 * Reglas (coherentes con GuardaVentanaServicio del backend):
 * - Sin ultimo entrante (null) -> `expirada` (se exige plantilla; Meta no permite
 *   iniciar con texto libre).
 * - `ahora - ultimoEntrante <= ventana` -> dentro de la ventana. Un `ahora`
 *   anterior al entrante (restante > ventana) tambien cuenta como dentro (no
 *   expira "hacia atras").
 * - Dentro de la ventana pero con <= UMBRAL_POR_EXPIRAR_MINUTOS restantes ->
 *   `por_expirar` (aviso), aun con texto libre permitido.
 * - Pasada la ventana -> `expirada` (solo plantillas).
 *
 * @param ultimoEntranteUtc instante del ultimo entrante (ISO/Date); null si no hay.
 * @param ahora             instante de referencia (ISO/Date); obligatorio.
 * @param ventanaHoras      amplitud de la ventana en horas; > 0.
 * @param umbralMinutos     minutos restantes para marcar "por expirar"; >= 0.
 * @returns el estado de la ventana con el tiempo restante.
 */
export function calcularVentanaServicio(
  ultimoEntranteUtc: string | Date | null | undefined,
  ahora: string | Date,
  ventanaHoras: number = VENTANA_HORAS_DEFECTO,
  umbralMinutos: number = UMBRAL_POR_EXPIRAR_MINUTOS,
): VentanaServicio {
  const expirada: VentanaServicio = {
    estado: 'expirada',
    permiteTextoLibre: false,
    restanteMs: 0,
    restanteMinutos: 0,
  };

  const ahoraMs = aEpochMs(ahora);
  if (ahoraMs === null || !(ventanaHoras > 0)) {
    return expirada;
  }

  const entranteMs = aEpochMs(ultimoEntranteUtc);
  if (entranteMs === null) {
    // Sin ningun entrante previo: fuera de la ventana (se exige plantilla).
    return expirada;
  }

  const ventanaMs = ventanaHoras * MS_POR_HORA;
  const finVentanaMs = entranteMs + ventanaMs;
  const restanteMs = finVentanaMs - ahoraMs;

  if (restanteMs < 0) {
    return expirada;
  }

  const restanteMinutos = Math.floor(restanteMs / MS_POR_MINUTO);
  const umbralMs = Math.max(0, umbralMinutos) * MS_POR_MINUTO;
  // "Por expirar" solo aplica cuando ya transcurrio algo de la ventana (restante
  // acotado al ancho de la ventana) y quedan <= umbral minutos.
  const estado: EstadoVentana = restanteMs <= umbralMs && restanteMs <= ventanaMs ? 'por_expirar' : 'dentro';

  return {
    estado,
    permiteTextoLibre: true,
    restanteMs,
    restanteMinutos,
  };
}

/** Etiqueta legible en espanol del estado de la ventana (para el chip). */
export function etiquetaVentana(estado: EstadoVentana): string {
  switch (estado) {
    case 'dentro':
      return 'Dentro de ventana';
    case 'por_expirar':
      return 'Por expirar';
    default:
      return 'Ventana expirada';
  }
}

/**
 * Texto accesible del tiempo restante para lectores de pantalla (no solo color).
 * Ejemplos: "Quedan 3 h 20 min", "Quedan 45 min", "Ventana cerrada".
 */
export function textoRestante(ventana: VentanaServicio): string {
  if (ventana.estado === 'expirada') {
    return 'Ventana cerrada: solo plantillas aprobadas';
  }
  const totalMin = ventana.restanteMinutos;
  const horas = Math.floor(totalMin / 60);
  const minutos = totalMin % 60;
  if (horas > 0) {
    return `Quedan ${horas} h ${minutos} min`;
  }
  return `Quedan ${minutos} min`;
}

/** Tono semantico del chip de ventana (verde/ambar/rojo), acompanado de texto. */
export function tonoVentana(estado: EstadoVentana): 'exito' | 'advertencia' | 'error' {
  switch (estado) {
    case 'dentro':
      return 'exito';
    case 'por_expirar':
      return 'advertencia';
    default:
      return 'error';
  }
}
