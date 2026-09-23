// =============================================================================
// Modelos del ambito COMERCIAL (Req 5, 6, 14, 15, 59, 63)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (comercial-crm). Los enums de negocio se modelan como uniones de literales que
// coinciden con la etiqueta lowercase `valorBd()` del backend. No se inventan
// campos: cada interfaz espeja su record Java correspondiente.
// =============================================================================

/** Tipo de persona del Cliente (coincide con el `valorBd` del backend). */
export type TipoPersona = 'fisica' | 'moral';

/** Cliente (ClienteDto). */
export interface Cliente {
  id: string;
  nombre: string;
  rfc: string;
  email: string | null;
  telefono: string | null;
  // Datos basicos de negocio (migracion V59): todos opcionales.
  nombreComercial: string | null;
  tipoPersona: TipoPersona | null;
  telefonoAdicional: string | null;
  direccionCalle: string | null;
  direccionCiudad: string | null;
  direccionEstado: string | null;
  direccionCp: string | null;
  direccionPais: string | null;
  notas: string | null;
  activo: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Contacto asociado a un Cliente (ContactoDto). */
export interface Contacto {
  id: string;
  clienteId: string;
  nombre: string;
  email: string | null;
  telefono: string | null;
  activo: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de alta/edicion de Cliente (Crear/ActualizarClienteRequest). */
export interface ClienteRequest {
  nombre: string;
  rfc: string;
  email?: string | null;
  telefono?: string | null;
  // Datos basicos de negocio (migracion V59): todos opcionales.
  nombreComercial?: string | null;
  tipoPersona?: TipoPersona | null;
  telefonoAdicional?: string | null;
  direccionCalle?: string | null;
  direccionCiudad?: string | null;
  direccionEstado?: string | null;
  direccionCp?: string | null;
  direccionPais?: string | null;
  notas?: string | null;
}

/** Etiqueta legible del tipo de persona de un Cliente. */
export const ETIQUETA_TIPO_PERSONA: Record<TipoPersona, string> = {
  fisica: 'Persona física',
  moral: 'Persona moral',
};

/** Opciones de tipo de persona para el selector del formulario de Cliente. */
export const TIPOS_PERSONA: readonly { valor: TipoPersona; etiqueta: string }[] = [
  { valor: 'fisica', etiqueta: ETIQUETA_TIPO_PERSONA.fisica },
  { valor: 'moral', etiqueta: ETIQUETA_TIPO_PERSONA.moral },
];

/** Cuerpo de alta de Contacto (CrearContactoRequest). */
export interface ContactoRequest {
  nombre: string;
  email?: string | null;
  telefono?: string | null;
}

/** Etapa del pipeline de una Oportunidad (EtapaOportunidad.valorBd). */
export type EtapaOportunidad =
  | 'nuevo'
  | 'calificado'
  | 'propuesta'
  | 'negociacion'
  | 'ganado'
  | 'perdido';

/** Oportunidad del pipeline (OportunidadDto). */
export interface Oportunidad {
  id: string;
  clienteId: string;
  titulo: string;
  valorEstimado: number;
  etapa: EtapaOportunidad;
  responsableUsuarioId: string | null;
  cotizacionId: string | null;
  canalVentaId: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de alta de Oportunidad (CrearOportunidadRequest). */
export interface OportunidadRequest {
  clienteId: string;
  titulo: string;
  valorEstimado: number;
}

/** Respuesta de conversion a Cotizacion (ConversionCotizacionResponse). */
export interface ConversionCotizacion {
  cotizacionId: string;
}

/** Estado de una Cotizacion (EstadoCotizacion.valorBd). */
export type EstadoCotizacion = 'borrador' | 'enviada' | 'aprobada' | 'rechazada';

/** Moneda de una Cotizacion (codigo ISO-4217 aceptado por el backend). */
export type MonedaCotizacion = 'MXN' | 'USD' | 'EUR';

/** Moneda por defecto de una Cotizacion cuando no se especifica. */
export const MONEDA_POR_DEFECTO: MonedaCotizacion = 'MXN';

/** Opciones de moneda para el selector del formulario de Cotizacion. */
export const MONEDAS_COTIZACION: readonly { valor: MonedaCotizacion; etiqueta: string }[] = [
  { valor: 'MXN', etiqueta: 'Peso mexicano (MXN)' },
  { valor: 'USD', etiqueta: 'Dolar estadounidense (USD)' },
  { valor: 'EUR', etiqueta: 'Euro (EUR)' },
];

/** Partida de una Cotizacion (PartidaCotizacionDto). */
export interface PartidaCotizacion {
  id: string;
  productoId: string | null;
  descripcion: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

/** Cotizacion con sus partidas y totales (CotizacionDto). */
export interface Cotizacion {
  id: string;
  clienteId: string;
  oportunidadId: string | null;
  estado: EstadoCotizacion;
  subtotal: number;
  total: number;
  partidas: PartidaCotizacion[];
  canalVentaId: string | null;
  // Cabecera descriptiva y comercial (backend comercial-crm).
  folio: string | null;
  fechaEmision: string | null;
  validoHasta: string | null;
  condiciones: string | null;
  notas: string | null;
  moneda: MonedaCotizacion | string | null;
  enviadaEn: string | null;
  /**
   * `true` cuando la Empresa emisora tiene datos fiscales incompletos (sin RFC o
   * sin direccion). El PDF se genera igual; la UI muestra un aviso no intrusivo
   * para que el administrador complete esos datos en "Mi empresa" (Req 3).
   */
  emisorIncompleto?: boolean;
  // Datos del cliente incrustados en el DTO de detalle (pueden faltar en el listado).
  clienteNombre?: string | null;
  clienteRfc?: string | null;
  clienteEmail?: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de una partida en las peticiones (PartidaRequest). El precio es opcional:
 *  si se omite y hay productoId, el backend sugiere el precio (Req 59.4). */
export interface PartidaRequest {
  productoId?: string | null;
  descripcion: string;
  cantidad: number;
  precioUnitario?: number | null;
}

/** Cuerpo de alta de Cotizacion (CrearCotizacionRequest). Los campos descriptivos
 *  son opcionales: si se omiten, el backend aplica sus valores por defecto. */
export interface CotizacionRequest {
  clienteId: string;
  partidas: PartidaRequest[];
  /** Fecha limite de validez de la cotizacion (ISO date, opcional). */
  validoHasta?: string | null;
  /** Condiciones comerciales en texto libre (opcional). */
  condiciones?: string | null;
  /** Notas internas o para el cliente (opcional). */
  notas?: string | null;
  /** Moneda de la cotizacion; por defecto MXN si se omite. */
  moneda?: MonedaCotizacion | null;
}

/** Cuerpo para enviar una Cotizacion por correo (EnviarCotizacionCorreoRequest).
 *  Si se omite el email, el backend usa el correo del cliente (Req 6). */
export interface EnviarCorreoRequest {
  email?: string | null;
}

/** Estado de una Prueba de Diseno (EstadoPruebaDiseno.valorBd). */
export type EstadoPruebaDiseno = 'pendiente' | 'aprobada' | 'rechazada';

/** Prueba de Diseno versionada (PruebaDisenoDto). */
export interface PruebaDiseno {
  id: string;
  cotizacionId: string;
  numeroVersion: number;
  estado: EstadoPruebaDiseno;
  aprobadaPor: string | null;
  rechazadaPor: string | null;
  decididaEn: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Resultado de rechazar una Prueba de Diseno (ResultadoRechazoPruebaDiseno). */
export interface ResultadoRechazoPruebaDiseno {
  rechazada: PruebaDiseno;
  nuevaVersion: PruebaDiseno;
}

/** Producto del catalogo (ProductoDto). */
export interface Producto {
  id: string;
  nombre: string;
  unidad: string;
  descripcion: string;
  clienteMeta: string | null;
  alianzas: string | null;
  competencia: string | null;
  /** Foto del producto: URL o data-URI (base64), o `null` si no tiene (Req 59, V61). */
  foto: string | null;
  activo: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de alta/edicion de Producto (Crear/ActualizarProductoRequest). */
export interface ProductoRequest {
  nombre: string;
  unidad: string;
  descripcion: string;
  clienteMeta?: string | null;
  alianzas?: string | null;
  competencia?: string | null;
  /** Foto del producto: URL o data-URI (base64, hasta ~1 MB), o `null` (V61). */
  foto?: string | null;
}

/** Lista de precios (ListaPreciosDto). */
export interface ListaPrecios {
  id: string;
  nombre: string;
  prioridad: number;
  segmento: string | null;
  vigenciaInicio: string;
  vigenciaFin: string | null;
  activo: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de definicion/edicion de Lista de precios (DefinirListaPreciosRequest). */
export interface ListaPreciosRequest {
  nombre: string;
  prioridad: number;
  segmento?: string | null;
  vigenciaInicio: string;
  vigenciaFin?: string | null;
}

/** Precio de un Producto en una lista (PrecioProductoDto). */
export interface PrecioProducto {
  id: string;
  listaPreciosId: string;
  productoId: string;
  precio: number;
  version: number;
}

/** Precio asignado en una lista, con el nombre del Producto (PrecioListaDto). */
export interface PrecioLista {
  productoId: string;
  productoNombre: string;
  precio: number;
}

/** Cuerpo para asignar precio (AsignarPrecioRequest). */
export interface AsignarPrecioRequest {
  productoId: string;
  precio: number;
}

/** Canal de venta (CanalVentaDto). */
export interface CanalVenta {
  id: string;
  nombre: string;
  descripcion: string | null;
  activo: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de alta/edicion de Canal de venta (Crear/ActualizarCanalVentaRequest). */
export interface CanalVentaRequest {
  nombre: string;
  descripcion?: string | null;
}

// -----------------------------------------------------------------------------
// Etiquetas y transiciones de negocio (UI en espanol)
// -----------------------------------------------------------------------------

/** Etiqueta legible de una etapa del pipeline. */
export const ETIQUETA_ETAPA: Record<EtapaOportunidad, string> = {
  nuevo: 'Nuevo',
  calificado: 'Calificado',
  propuesta: 'Propuesta',
  negociacion: 'Negociación',
  ganado: 'Ganado',
  perdido: 'Perdido',
};

/** Orden de las etapas del embudo para la vista de pipeline (kanban). */
export const ETAPAS_PIPELINE: readonly EtapaOportunidad[] = [
  'nuevo',
  'calificado',
  'propuesta',
  'negociacion',
  'ganado',
  'perdido',
];

/**
 * Maquina de estados del pipeline de Oportunidades (Req 14.3, 14.4). Refleja las
 * transiciones que el backend acepta; una transicion que no aparezca aqui no se
 * ofrece en la UI (el backend la rechazaria con 409). Las etapas terminales
 * (ganado/perdido) no tienen transiciones de salida.
 */
export const TRANSICIONES_ETAPA: Record<EtapaOportunidad, readonly EtapaOportunidad[]> = {
  nuevo: ['calificado', 'perdido'],
  calificado: ['propuesta', 'perdido'],
  propuesta: ['negociacion', 'perdido'],
  negociacion: ['ganado', 'perdido'],
  ganado: [],
  perdido: [],
};

/**
 * Devuelve las etapas destino validas desde una etapa dada segun la maquina de
 * estados. Deny-by-default: una etapa desconocida no ofrece transiciones.
 */
export function etapasDestino(desde: EtapaOportunidad): readonly EtapaOportunidad[] {
  return TRANSICIONES_ETAPA[desde] ?? [];
}

/** Etiqueta legible del estado de una Cotizacion. */
export const ETIQUETA_ESTADO_COTIZACION: Record<EstadoCotizacion, string> = {
  borrador: 'Borrador',
  enviada: 'Enviada',
  aprobada: 'Aprobada',
  rechazada: 'Rechazada',
};

/**
 * Transiciones de estado de una Cotizacion (Req 6.6, 6.7). Una Cotizacion en
 * borrador puede enviarse; una enviada puede aprobarse o rechazarse. Los estados
 * aprobada/rechazada son terminales.
 */
export const TRANSICIONES_ESTADO_COTIZACION: Record<EstadoCotizacion, readonly EstadoCotizacion[]> =
  {
    borrador: ['enviada'],
    enviada: ['aprobada', 'rechazada'],
    aprobada: [],
    rechazada: [],
  };

/** Estados destino validos de una Cotizacion desde un estado dado. */
export function estadosDestinoCotizacion(desde: EstadoCotizacion): readonly EstadoCotizacion[] {
  return TRANSICIONES_ESTADO_COTIZACION[desde] ?? [];
}

/** Etiqueta legible del estado de una Prueba de Diseno. */
export const ETIQUETA_ESTADO_PRUEBA: Record<EstadoPruebaDiseno, string> = {
  pendiente: 'Pendiente',
  aprobada: 'Aprobada',
  rechazada: 'Rechazada',
};

/**
 * Calcula el subtotal de una partida (cantidad * precioUnitario) redondeado a 2
 * decimales half-up, coherente con el backend (Req 6.3, 6.5). Es una utilidad de
 * previsualizacion en el cliente; el total oficial lo devuelve el servidor.
 */
export function subtotalPartida(cantidad: number, precioUnitario: number): number {
  const bruto = (Number(cantidad) || 0) * (Number(precioUnitario) || 0);
  return Math.round((bruto + Number.EPSILON) * 100) / 100;
}

/**
 * Calcula el total de una lista de partidas como suma de subtotales, redondeado a
 * 2 decimales half-up (Req 6.5). Utilidad de previsualizacion en el cliente.
 */
export function totalPartidas(
  partidas: readonly { cantidad: number; precioUnitario?: number | null }[],
): number {
  const suma = partidas.reduce(
    (acc, p) => acc + subtotalPartida(p.cantidad, Number(p.precioUnitario ?? 0)),
    0,
  );
  return Math.round((suma + Number.EPSILON) * 100) / 100;
}
