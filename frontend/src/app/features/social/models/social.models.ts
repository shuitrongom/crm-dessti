// =============================================================================
// Modelos del modulo Social / Omnicanal (Req 64, 65, 66)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (com.empresa.crm.social.* y com.empresa.crm.social.analitica.*). Los importes
// llegan como decimales (escala 2); los instantes como ISO-8601 UTC (string).
// =============================================================================

/** Canal social soportado (etiquetas valorBd del backend). */
export type CanalSocial = 'whatsapp' | 'facebook' | 'instagram' | 'messenger' | 'tiktok';

/** Estado de una Conversacion (abierta/asignada/cerrada). */
export type EstadoConversacion = 'abierta' | 'asignada' | 'cerrada' | string;

/** Sentido de un Mensaje_Social. */
export type DireccionMensaje = 'entrante' | 'saliente' | string;

/** Tipo de un Mensaje_Social. */
export type TipoMensaje = 'texto' | 'plantilla' | 'interactivo' | string;

/** Estado del Consentimiento (Opt_In/Opt_Out). */
export type EstadoConsentimiento = 'opt_in' | 'opt_out' | string;

/** Estado de una Publicacion_Social. */
export type EstadoPublicacion = 'borrador' | 'programada' | 'publicada' | 'fallida' | string;

/** Conversacion de la Bandeja_Unificada (ConversacionDto). */
export interface Conversacion {
  id: string;
  cuentaCanalSocialId: string;
  canal: CanalSocial;
  remitenteExterno: string;
  clienteId: string | null;
  contactoId: string | null;
  estado: EstadoConversacion;
  asignadoA: string | null;
  /** Instante del ultimo entrante (UTC); gobierna la Ventana_Servicio; null si no hay entrantes. */
  ultimoEntranteUtc: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mensaje_Social del historial de una Conversacion (MensajeSocialDto). */
export interface MensajeSocial {
  id: string;
  conversacionId: string;
  direccion: DireccionMensaje;
  tipo: TipoMensaje;
  contenido: string;
  esMarketing: boolean;
  estadoEntrega: string | null;
  externoId: string | null;
  enviadoEn: string | null;
  recibidoEn: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /social/bandeja/{id}/mensajes (EnviarMensajeRequest). */
export interface EnviarMensajeRequest {
  tipo: TipoMensaje;
  contenido: string;
  esMarketing: boolean;
}

/** Cuerpo de PUT /social/bandeja/{id}/asignacion. */
export interface AsignarConversacionRequest {
  usuarioId: string;
}

/** Plantilla_Mensaje aprobada para envio fuera de la Ventana_Servicio (PlantillaMensajeDto). */
export interface PlantillaMensaje {
  id: string;
  canal: CanalSocial;
  nombre: string;
  contenido: string;
  aprobada: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Publicacion_Social programada/publicada (PublicacionSocialDto). */
export interface PublicacionSocial {
  id: string;
  cuentaCanalSocialId: string;
  canal: CanalSocial;
  contenido: string;
  fechaProgramada: string;
  estado: EstadoPublicacion;
  externoId: string | null;
  publicadaEn: string | null;
  motivoFallo: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /social/publicaciones (CrearPublicacionSocialRequest). */
export interface CrearPublicacionSocialRequest {
  cuentaCanalSocialId: string;
  contenido: string;
  /** Instante ISO-8601 UTC no anterior al momento actual. */
  fechaProgramada: string;
}

/** Campana_Publicitaria (CampanaPublicitariaDto). */
export interface CampanaPublicitaria {
  id: string;
  cuentaCanalSocialId: string | null;
  canal: CanalSocial | null;
  nombre: string;
  presupuesto: number;
  fechaInicio: string;
  fechaFin: string;
  externoId: string | null;
  /** Ultima instantanea del estado externo (Meta); NO autoritativa. */
  estadoExterno: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /social/campanas (CrearCampanaPublicitariaRequest). */
export interface CrearCampanaPublicitariaRequest {
  cuentaCanalSocialId?: string | null;
  canal?: CanalSocial | null;
  nombre: string;
  presupuesto: number;
  /** Fecha ISO (yyyy-MM-dd) de inicio del periodo. */
  fechaInicio: string;
  /** Fecha ISO (yyyy-MM-dd) de fin del periodo (>= inicio). */
  fechaFin: string;
  externoId?: string | null;
}

/** Instantanea de SOLO LECTURA del estado externo de una campana (EstadoCampanaExternoDto). */
export interface EstadoCampanaExterno {
  externoId: string | null;
  estado: string | null;
  consultadoEn: string | null;
  [clave: string]: unknown;
}

/** Metricas sociales de un Canal_Social (MetricasSocialesDto). */
export interface MetricasSociales {
  canal: CanalSocial;
  alcance: number;
  interacciones: number;
  mensajesRecibidos: number;
  mensajesEnviados: number;
  tiempoRespuestaPromedioSegundos: number;
  conversiones: number;
}

/** Resumen de la analitica social (ResumenAnaliticaSocialDto). */
export interface ResumenAnaliticaSocial {
  desde: string | null;
  hasta: string | null;
  canal: CanalSocial | null;
  canalVentaId: string | null;
  exportacion: boolean;
  canales: MetricasSociales[];
}

/** Cuenta de Canal_Social conectada (CuentaCanalSocialDto). No expone credenciales. */
export interface CuentaCanalSocial {
  id: string;
  canal: CanalSocial;
  identificadorExterno: string;
  nombre: string;
  credencialesRef: string | null;
  activa: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Cuerpo de POST /social/cuentas-canal (CrearCuentaCanalSocialRequest). La
 * `credencialesRef` es solo el NOMBRE/apuntador del secreto gestionado fuera de
 * la BD; nunca la credencial en claro (Req 11 de la plataforma).
 */
export interface CrearCuentaCanalSocialRequest {
  canal: CanalSocial;
  identificadorExterno: string;
  nombre: string;
  credencialesRef: string;
}
