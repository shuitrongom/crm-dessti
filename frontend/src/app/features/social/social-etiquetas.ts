// =============================================================================
// Etiquetas y iconografia de los dominios sociales (Req 64, 65, 66)
// -----------------------------------------------------------------------------
// Mapas centralizados de las etiquetas de negocio (Canal_Social, tipo de mensaje,
// estado de publicacion) a texto legible en espanol e iconos de Material Symbols.
// Reutilizado por todas las vistas del modulo social. Al ser Record<string,...>
// las lecturas en plantilla se hacen a traves de metodos de componente para no
// indexar por Enum en la plantilla estricta (evita TS7053).
// =============================================================================

import { CanalSocial, EstadoPublicacion, TipoMensaje } from './models/social.models';

/**
 * Etiqueta legible del Canal_Social. Es el UNICO origen de verdad de los canales
 * soportados: los selectores/filtros de la UI social derivan sus opciones de
 * aqui (ver OPCIONES_CANAL) para no divergir.
 */
export const ETIQUETA_CANAL: Record<CanalSocial, string> = {
  whatsapp: 'WhatsApp',
  facebook: 'Facebook',
  instagram: 'Instagram',
  messenger: 'Messenger',
  tiktok: 'TikTok',
};

/** Icono de Material Symbols por canal (aproximado; el color no es unico portador). */
export const ICONO_CANAL: Record<CanalSocial, string> = {
  whatsapp: 'chat',
  facebook: 'thumb_up',
  instagram: 'photo_camera',
  messenger: 'forum',
  tiktok: 'music_note',
};

/** Opcion de canal para selectores/filtros (valor tecnico + etiqueta legible). */
export interface OpcionCanal {
  valor: CanalSocial;
  etiqueta: string;
}

/**
 * Opciones de canal derivadas de ETIQUETA_CANAL (origen unico de verdad). Las
 * vistas que necesiten una opcion "todos"/"sin canal" la anteponen; asi los cinco
 * canales aparecen siempre y de forma consistente en toda la UI social.
 */
export const OPCIONES_CANAL: readonly OpcionCanal[] = (
  Object.keys(ETIQUETA_CANAL) as CanalSocial[]
).map((valor) => ({ valor, etiqueta: ETIQUETA_CANAL[valor] }));

/** Etiqueta legible del tipo de mensaje. */
export const ETIQUETA_TIPO_MENSAJE: Record<TipoMensaje, string> = {
  texto: 'Texto',
  plantilla: 'Plantilla',
  interactivo: 'Interactivo',
};

/** Etiqueta legible del estado de una Publicacion_Social. */
export const ETIQUETA_ESTADO_PUBLICACION: Record<EstadoPublicacion, string> = {
  borrador: 'Borrador',
  programada: 'Programada',
  publicada: 'Publicada',
  fallida: 'Fallida',
};
