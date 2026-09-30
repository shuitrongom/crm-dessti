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
  /** Usuario propietario/vendedor asignado; null si no se asignó (V81). */
  propietarioUsuarioId: string | null;
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
  /** Probabilidad de cierre en porcentaje entero [0,100] (V81, forecast). */
  probabilidad: number;
  /** Fecha esperada de cierre (ISO date); null si no se estima (V81). */
  fechaCierreEsperada: string | null;
  /** Motivo de pérdida; null salvo etapa 'perdido' (V81). */
  motivoPerdida: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Probabilidad de cierre sugerida por etapa (coincide con el backend, V81). */
export const PROBABILIDAD_POR_ETAPA: Record<EtapaOportunidad, number> = {
  nuevo: 10,
  calificado: 30,
  propuesta: 50,
  negociacion: 70,
  ganado: 100,
  perdido: 0,
};

/**
 * Valor ponderado de una oportunidad para el forecast: valor estimado por su
 * probabilidad de cierre (valor * probabilidad / 100), redondeado a 2 decimales.
 */
export function valorPonderado(oportunidad: {
  valorEstimado: number;
  probabilidad: number;
}): number {
  const bruto = (Number(oportunidad.valorEstimado) || 0) * (Number(oportunidad.probabilidad) || 0) / 100;
  return Math.round((bruto + Number.EPSILON) * 100) / 100;
}

/** Cuerpo para ajustar el forecast de una oportunidad (V81). */
export interface AjustarForecastRequest {
  probabilidad?: number | null;
  fechaCierreEsperada?: string | null;
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

/** Tasa de IVA de una partida (TasaIva.valorBd, V80). */
export type TasaIva = '16' | '8' | '0' | 'exento';

/** Tasa de IVA por defecto de una partida (16%). */
export const TASA_IVA_POR_DEFECTO: TasaIva = '16';

/** Etiqueta legible de una tasa de IVA. */
export const ETIQUETA_TASA_IVA: Record<TasaIva, string> = {
  '16': 'IVA 16%',
  '8': 'IVA 8% (frontera)',
  '0': 'IVA 0%',
  exento: 'Exento',
};

/** Factor multiplicativo de cada tasa (para la previsualización de IVA en el cliente). */
export const FACTOR_TASA_IVA: Record<TasaIva, number> = {
  '16': 0.16,
  '8': 0.08,
  '0': 0,
  exento: 0,
};

/** Opciones de tasa de IVA para el selector del formulario de partida. */
export const TASAS_IVA: readonly { valor: TasaIva; etiqueta: string }[] = [
  { valor: '16', etiqueta: ETIQUETA_TASA_IVA['16'] },
  { valor: '8', etiqueta: ETIQUETA_TASA_IVA['8'] },
  { valor: '0', etiqueta: ETIQUETA_TASA_IVA['0'] },
  { valor: 'exento', etiqueta: ETIQUETA_TASA_IVA.exento },
];

/** Partida de una Cotizacion (PartidaCotizacionDto). */
export interface PartidaCotizacion {
  id: string;
  productoId: string | null;
  descripcion: string;
  cantidad: number;
  precioUnitario: number;
  /** Descuento (monto) de la partida antes de IVA (V80). */
  descuento: number;
  /** Importe bruto = cantidad * precioUnitario, antes de descuento (V80). */
  importeBase: number;
  /** Tasa de IVA de la partida (V80). */
  tasaIva: TasaIva;
  /** IVA de la partida sobre su base neta (V80). */
  iva: number;
  /** Base neta = importe bruto - descuento; base del IVA (V80). */
  subtotal: number;
}

/** Cotizacion con sus partidas y totales (CotizacionDto). */
export interface Cotizacion {
  id: string;
  clienteId: string;
  oportunidadId: string | null;
  estado: EstadoCotizacion;
  /** Suma de las bases netas de partida, antes de descuento global (V80). */
  subtotal: number;
  /** Descuento global (monto) restado del subtotal (V80). */
  descuentoGlobal: number;
  /** IVA consolidado (suma del IVA de las partidas) (V80). */
  iva: number;
  /** Retencion de ISR (monto) restada del total (V80). */
  retencionIsr: number;
  /** Retencion de IVA (monto) restada del total (V80). */
  retencionIva: number;
  /** Total = subtotal - descuento global + IVA - retenciones (V80). */
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
  /** Descuento (monto) de la partida; opcional, por defecto 0 (V80). */
  descuento?: number | null;
  /** Tasa de IVA de la partida; opcional, por defecto 16% (V80). */
  tasaIva?: TasaIva | null;
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
  /** Descuento global (monto); opcional, por defecto 0 (V80). */
  descuentoGlobal?: number | null;
  /** Retencion de ISR (monto); opcional, por defecto 0 (V80). */
  retencionIsr?: number | null;
  /** Retencion de IVA (monto); opcional, por defecto 0 (V80). */
  retencionIva?: number | null;
}

/** Cuerpo para ajustar impuestos/descuentos de una cotizacion en borrador (V80). */
export interface AjustesFiscalesRequest {
  descuentoGlobal?: number | null;
  retencionIsr?: number | null;
  retencionIva?: number | null;
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

/**
 * Sugerencia de precio de un Producto (PrecioSugeridoDto, Req 59.4, 59.9). Es el
 * resultado de la regla de seleccion de precio del backend expuesto como consulta,
 * para previsualizar el precio de lista al armar una Cotizacion antes de guardar.
 * Si ninguna Lista_Precios vigente aplica, `disponible` es `false` y
 * `precioSugerido` es `null` (nunca se degrada a 0).
 */
export interface PrecioSugerido {
  productoId: string;
  precioSugerido: number | null;
  disponible: boolean;
  fechaReferencia: string;
  segmentoCliente: string | null;
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

// -----------------------------------------------------------------------------
// Previsualizacion del desglose fiscal CFDI en el cliente (V80)
// -----------------------------------------------------------------------------
// Replica el calculo del backend (orden CFDI) para mostrar el total en vivo
// antes de enviar. El total oficial siempre lo devuelve el servidor.

/** Redondea a 2 decimales half-up (aritmetica monetaria de previsualizacion). */
function redondear2(valor: number): number {
  return Math.round((Number(valor) + Number.EPSILON) * 100) / 100;
}

/** Base neta de una partida = cantidad*precio - descuento (acotada a >= 0). */
export function baseNetaPartida(
  cantidad: number,
  precioUnitario: number | null | undefined,
  descuento: number | null | undefined,
): number {
  const bruto = subtotalPartida(cantidad, Number(precioUnitario ?? 0));
  const desc = Math.max(0, Math.min(Number(descuento ?? 0), bruto));
  return redondear2(bruto - desc);
}

/** IVA de una partida = baseNeta * factor(tasa), redondeado a 2 decimales. */
export function ivaPartida(
  cantidad: number,
  precioUnitario: number | null | undefined,
  descuento: number | null | undefined,
  tasaIva: TasaIva | null | undefined,
): number {
  const base = baseNetaPartida(cantidad, precioUnitario, descuento);
  const factor = FACTOR_TASA_IVA[(tasaIva ?? TASA_IVA_POR_DEFECTO) as TasaIva];
  return redondear2(base * factor);
}

/** Renglon de entrada para el desglose fiscal de previsualizacion. */
export interface PartidaFiscalEntrada {
  cantidad: number;
  precioUnitario?: number | null;
  descuento?: number | null;
  tasaIva?: TasaIva | null;
}

/** Resultado del desglose fiscal de previsualizacion (mismos campos que el DTO). */
export interface DesgloseFiscal {
  subtotal: number;
  descuentoGlobal: number;
  iva: number;
  retencionIsr: number;
  retencionIva: number;
  total: number;
}

/**
 * Calcula el desglose fiscal CFDI de una cotizacion en el cliente (V80),
 * replicando el orden del backend: subtotal = Σ base neta; IVA = Σ IVA por
 * partida; base gravable = subtotal - descuento global (>= 0); total = base
 * gravable + IVA - retenciones (>= 0). Solo para previsualizacion.
 */
export function calcularDesgloseFiscal(
  partidas: readonly PartidaFiscalEntrada[],
  descuentoGlobal: number | null | undefined,
  retencionIsr: number | null | undefined,
  retencionIva: number | null | undefined,
): DesgloseFiscal {
  let subtotal = 0;
  let iva = 0;
  for (const p of partidas) {
    subtotal += baseNetaPartida(p.cantidad, p.precioUnitario, p.descuento);
    iva += ivaPartida(p.cantidad, p.precioUnitario, p.descuento, p.tasaIva);
  }
  subtotal = redondear2(subtotal);
  iva = redondear2(iva);
  const descGlobal = Math.max(0, Math.min(Number(descuentoGlobal ?? 0), subtotal));
  const retIsr = Math.max(0, Number(retencionIsr ?? 0));
  const retIva = Math.max(0, Number(retencionIva ?? 0));
  const baseGravable = redondear2(subtotal - descGlobal);
  const total = Math.max(0, redondear2(baseGravable + iva - retIsr - retIva));
  return {
    subtotal,
    descuentoGlobal: redondear2(descGlobal),
    iva,
    retencionIsr: redondear2(retIsr),
    retencionIva: redondear2(retIva),
    total,
  };
}

// -----------------------------------------------------------------------------
// Actividades de seguimiento comercial (V79)
// -----------------------------------------------------------------------------
// Refleja EXACTAMENTE el ActividadDto del backend. El tipo y el estado son
// uniones de literales que coinciden con la etiqueta lowercase `valorBd()`.

/** Tipo de una Actividad de seguimiento (TipoActividad.valorBd). */
export type TipoActividad = 'llamada' | 'correo' | 'reunion' | 'tarea' | 'nota';

/** Estado de una Actividad de seguimiento (EstadoActividad.valorBd). */
export type EstadoActividad = 'pendiente' | 'completada' | 'cancelada';

/** Actividad de seguimiento comercial (ActividadDto). */
export interface Actividad {
  id: string;
  clienteId: string;
  oportunidadId: string | null;
  tipo: TipoActividad;
  estado: EstadoActividad;
  asunto: string;
  descripcion: string | null;
  fechaProgramada: string;
  vencimiento: string | null;
  completadaEn: string | null;
  responsableUsuarioId: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de alta de una Actividad (CrearActividadRequest). */
export interface ActividadRequest {
  clienteId: string;
  oportunidadId?: string | null;
  tipo: TipoActividad;
  asunto: string;
  descripcion?: string | null;
  /** Instante planificado en formato ISO-8601 (UTC). */
  fechaProgramada: string;
  /** Fecha limite opcional en formato ISO-8601 (UTC). */
  vencimiento?: string | null;
  responsableUsuarioId?: string | null;
}

/** Cuerpo de edicion de asunto/descripcion de una Actividad (EditarActividadRequest). */
export interface EditarActividadRequest {
  asunto: string;
  descripcion?: string | null;
}

/** Cuerpo de reprogramacion de una Actividad (ReprogramarActividadRequest). */
export interface ReprogramarActividadRequest {
  fechaProgramada: string;
  vencimiento?: string | null;
}

/** Etiqueta legible del tipo de Actividad. */
export const ETIQUETA_TIPO_ACTIVIDAD: Record<TipoActividad, string> = {
  llamada: 'Llamada',
  correo: 'Correo',
  reunion: 'Reunión',
  tarea: 'Tarea',
  nota: 'Nota',
};

/** Icono Material asociado a cada tipo de Actividad (para el timeline). */
export const ICONO_TIPO_ACTIVIDAD: Record<TipoActividad, string> = {
  llamada: 'call',
  correo: 'mail',
  reunion: 'groups',
  tarea: 'task_alt',
  nota: 'sticky_note_2',
};

/** Opciones de tipo de Actividad para el selector del formulario. */
export const TIPOS_ACTIVIDAD: readonly { valor: TipoActividad; etiqueta: string; icono: string }[] =
  [
    { valor: 'llamada', etiqueta: ETIQUETA_TIPO_ACTIVIDAD.llamada, icono: ICONO_TIPO_ACTIVIDAD.llamada },
    { valor: 'correo', etiqueta: ETIQUETA_TIPO_ACTIVIDAD.correo, icono: ICONO_TIPO_ACTIVIDAD.correo },
    { valor: 'reunion', etiqueta: ETIQUETA_TIPO_ACTIVIDAD.reunion, icono: ICONO_TIPO_ACTIVIDAD.reunion },
    { valor: 'tarea', etiqueta: ETIQUETA_TIPO_ACTIVIDAD.tarea, icono: ICONO_TIPO_ACTIVIDAD.tarea },
    { valor: 'nota', etiqueta: ETIQUETA_TIPO_ACTIVIDAD.nota, icono: ICONO_TIPO_ACTIVIDAD.nota },
  ];

/** Etiqueta legible del estado de una Actividad. */
export const ETIQUETA_ESTADO_ACTIVIDAD: Record<EstadoActividad, string> = {
  pendiente: 'Pendiente',
  completada: 'Completada',
  cancelada: 'Cancelada',
};

/**
 * Indica si una Actividad esta vencida: es una tarea/seguimiento pendiente cuyo
 * vencimiento (o, en su defecto, fecha programada) ya paso. Utilidad de UI para
 * resaltar tareas atrasadas; no altera el estado del backend.
 */
export function actividadVencida(actividad: Actividad, ahora: Date = new Date()): boolean {
  if (actividad.estado !== 'pendiente') {
    return false;
  }
  const limite = actividad.vencimiento ?? actividad.fechaProgramada;
  return new Date(limite).getTime() < ahora.getTime();
}
