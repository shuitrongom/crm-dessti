// =============================================================================
// Modelos del ambito OPERACION (Req 7, 16, 17, 19, 21, 18, 60)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (operacion-produccion). Los enums de negocio se modelan como uniones de
// literales que coinciden con la etiqueta lowercase `valorBd()` del backend. No
// se inventan campos: cada interfaz espeja su record Java correspondiente.
// =============================================================================

// -----------------------------------------------------------------------------
// Ordenes de Fabricacion (Req 7)
// -----------------------------------------------------------------------------

/** Estado de una Orden de Fabricacion (EstadoOrdenFabricacion.valorBd). */
export type EstadoOrdenFabricacion = 'pendiente' | 'en_produccion' | 'terminada' | 'cancelada';

/** Orden de Fabricacion (OrdenFabricacionDto). */
export interface OrdenFabricacion {
  id: string;
  cotizacionId: string;
  clienteId: string;
  estado: EstadoOrdenFabricacion;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo para generar una Orden de Fabricacion (GenerarOrdenFabricacionRequest). */
export interface GenerarOrdenFabricacionRequest {
  cotizacionId: string;
}

/**
 * Partida (BOM) de una Orden de Fabricacion (PartidaOrdenFabricacionDto): un
 * Material y su cantidad. El frontend muestra el nombre del Material (via
 * NombresOperacionService), nunca el UUID.
 */
export interface PartidaOrdenFabricacion {
  materialId: string;
  cantidad: number;
}

/**
 * Detalle de una Orden de Fabricacion (OrdenFabricacionDetalleDto): la OF mas su
 * lista de partidas. Lo devuelve GET /ordenes-fabricacion/{id}.
 */
export interface OrdenFabricacionDetalle extends OrdenFabricacion {
  partidas: PartidaOrdenFabricacion[];
}

/**
 * Cuerpo para crear una Orden de Fabricacion directa (sin Cotizacion):
 * POST /ordenes-fabricacion/directa. El Cliente es obligatorio y cada partida
 * lleva Material y cantidad positiva.
 */
export interface CrearOrdenDirectaRequest {
  clienteId: string;
  partidas: PartidaOrdenFabricacion[];
}

/** Etiquetas legibles de los estados de una Orden de Fabricacion. */
export const ETIQUETA_ESTADO_OF: Record<EstadoOrdenFabricacion, string> = {
  pendiente: 'Pendiente',
  en_produccion: 'En produccion',
  terminada: 'Terminada',
  cancelada: 'Cancelada',
};

/**
 * Transiciones de estado de una Orden de Fabricacion (Req 7.5, 7.6). Solo se
 * ofrecen las transiciones validas; los estados terminada/cancelada no salen.
 */
export const TRANSICIONES_ESTADO_OF: Record<EstadoOrdenFabricacion, readonly EstadoOrdenFabricacion[]> =
  {
    pendiente: ['en_produccion', 'cancelada'],
    en_produccion: ['terminada', 'cancelada'],
    terminada: [],
    cancelada: [],
  };

/** Estados destino validos de una Orden de Fabricacion desde un estado dado. */
export function estadosDestinoOf(desde: EstadoOrdenFabricacion): readonly EstadoOrdenFabricacion[] {
  return TRANSICIONES_ESTADO_OF[desde] ?? [];
}

// -----------------------------------------------------------------------------
// Levantamientos de Sitio (Req 16)
// -----------------------------------------------------------------------------

/** Estado de un Levantamiento de Sitio (EstadoLevantamiento.valorBd). */
export type EstadoLevantamiento = 'en_proceso' | 'completado';

/** Levantamiento de Sitio (LevantamientoSitioDto). */
export interface LevantamientoSitio {
  id: string;
  sitioId: string | null;
  cotizacionId: string | null;
  ordenFabricacionId: string | null;
  mediciones: string;
  tipoSuperficie: string;
  condicionesElectricas: string;
  estado: EstadoLevantamiento;
  completadoPor: string | null;
  completadoEn: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de alta de Levantamiento (CrearLevantamientoRequest). */
export interface LevantamientoRequest {
  mediciones: string;
  tipoSuperficie: string;
  condicionesElectricas: string;
  sitioId?: string | null;
  cotizacionId?: string | null;
  ordenFabricacionId?: string | null;
}

/** Etiquetas legibles del estado de un Levantamiento. */
export const ETIQUETA_ESTADO_LEVANTAMIENTO: Record<EstadoLevantamiento, string> = {
  en_proceso: 'En proceso',
  completado: 'Completado',
};

/**
 * Fotografia adjunta a un Levantamiento_Sitio (LevantamientoFotoDto). La
 * `referencia` es la URL o clave del objeto de la fotografia (Req 12.1, 16.3).
 */
export interface LevantamientoFoto {
  id: string;
  levantamientoId: string;
  referencia: string;
  createdAt: string;
}

/** Levantamiento con sus fotos vinculadas (LevantamientoSitioDetalleDto). */
export interface LevantamientoSitioDetalle extends LevantamientoSitio {
  fotos: LevantamientoFoto[];
}

// -----------------------------------------------------------------------------
// Permisos de Instalacion (Req 17)
// -----------------------------------------------------------------------------

/** Tipo de Permiso de Instalacion (TipoPermiso.valorBd). */
export type TipoPermiso = 'municipal' | 'arrendador';

/** Estado de un Permiso de Instalacion (EstadoPermiso.valorBd). */
export type EstadoPermiso = 'solicitado' | 'aprobado' | 'rechazado';

/** Permiso de Instalacion (PermisoInstalacionDto). */
export interface PermisoInstalacion {
  id: string;
  sitioId: string | null;
  tipo: TipoPermiso;
  fechaVencimiento: string;
  estado: EstadoPermiso;
  decididoPor: string | null;
  decididoEn: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de alta de Permiso (CrearPermisoInstalacionRequest). */
export interface PermisoRequest {
  tipo: TipoPermiso;
  fechaVencimiento: string;
  sitioId: string;
}

/** Etiquetas legibles del tipo de Permiso. */
export const ETIQUETA_TIPO_PERMISO: Record<TipoPermiso, string> = {
  municipal: 'Municipal',
  arrendador: 'Arrendador',
};

/** Etiquetas legibles del estado de un Permiso. */
export const ETIQUETA_ESTADO_PERMISO: Record<EstadoPermiso, string> = {
  solicitado: 'Solicitado',
  aprobado: 'Aprobado',
  rechazado: 'Rechazado',
};

// -----------------------------------------------------------------------------
// Ordenes de Trabajo de Instalacion / OTI (Req 19)
// -----------------------------------------------------------------------------

/** Estado de una OTI (EstadoOrdenTrabajoInstalacion.valorBd). */
export type EstadoOti = 'programada' | 'en_curso' | 'completada' | 'cancelada';

/** Orden de Trabajo de Instalacion (OrdenTrabajoInstalacionDto). */
export interface OrdenTrabajoInstalacion {
  id: string;
  ordenFabricacionId: string;
  sitioId: string;
  cuadrillaId: string;
  clienteId: string;
  fechaProgramada: string;
  estado: EstadoOti;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo para programar una OTI (ProgramarOrdenTrabajoInstalacionRequest). */
export interface ProgramarOtiRequest {
  ordenFabricacionId: string;
  sitioId: string;
  cuadrillaId: string;
  fechaProgramada: string;
}

/** Cuerpo para registrar avance de una OTI (RegistrarAvanceRequest). */
export interface RegistrarAvanceRequest {
  nuevosPendientes?: string[];
  evidencias?: string[];
  pendientesResueltos?: string[];
}

/**
 * Entrada de la Lista_Pendientes de una OTI (PendienteInstalacionDto): una tarea
 * pendiente con su bandera `resuelto` (Req 8.2). La guarda de cierre de la OTI
 * enumera las descripciones de los pendientes no resueltos (Req 8.5).
 */
export interface PendienteInstalacion {
  id: string;
  ordenTrabajoInstalacionId: string;
  descripcion: string;
  resuelto: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Evidencia fotografica adjunta a una OTI (EvidenciaInstalacionDto). La `url` es
 * la referencia (URL o clave de objeto) de la fotografia (Req 8.3).
 */
export interface EvidenciaInstalacion {
  id: string;
  ordenTrabajoInstalacionId: string;
  url: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Detalle de una OTI (OrdenTrabajoInstalacionDetalleDto): la OTI mas sus
 * pendientes (cada uno con `resuelto`) y sus evidencias. Lo devuelve
 * GET /ordenes-trabajo-instalacion/{id} (listas vacias cuando no haya).
 */
export interface OrdenTrabajoInstalacionDetalle extends OrdenTrabajoInstalacion {
  pendientes: PendienteInstalacion[];
  evidencias: EvidenciaInstalacion[];
}

/** Etiquetas legibles del estado de una OTI. */
export const ETIQUETA_ESTADO_OTI: Record<EstadoOti, string> = {
  programada: 'Programada',
  en_curso: 'En curso',
  completada: 'Completada',
  cancelada: 'Cancelada',
};

/**
 * Transiciones de estado de una OTI (Req 19.5). Solo se ofrecen las transiciones
 * validas; los estados completada/cancelada son terminales.
 */
export const TRANSICIONES_ESTADO_OTI: Record<EstadoOti, readonly EstadoOti[]> = {
  programada: ['en_curso', 'cancelada'],
  en_curso: ['completada', 'cancelada'],
  completada: [],
  cancelada: [],
};

/** Estados destino validos de una OTI desde un estado dado. */
export function estadosDestinoOti(desde: EstadoOti): readonly EstadoOti[] {
  return TRANSICIONES_ESTADO_OTI[desde] ?? [];
}

// -----------------------------------------------------------------------------
// Proyectos y Sitios (Req 21)
// -----------------------------------------------------------------------------

/** Sitio de un Proyecto (SitioDto). */
export interface Sitio {
  id: string;
  proyectoId: string;
  nombre: string;
  direccion: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Avance de un Sitio en las cuatro fases (SitioAvanceDto). */
export interface SitioAvance {
  sitio: Sitio;
  tieneLevantamientoCompletado: boolean;
  tienePermisoAprobado: boolean;
  tieneOrdenFabricacionTerminada: boolean;
  tieneInstalacionCompletada: boolean;
}

/** Proyecto con estado consolidado y sitios (ProyectoDto). */
export interface Proyecto {
  id: string;
  clienteId: string;
  nombre: string;
  /** Estado consolidado derivado; null en la proyeccion de resumen del listado. */
  estadoConsolidado: string | null;
  /** Sitios con avance; vacio en la proyeccion de resumen del listado. */
  sitios: SitioAvance[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de alta de Proyecto (CrearProyectoRequest). */
export interface ProyectoRequest {
  clienteId: string;
  nombre: string;
}

/** Cuerpo de alta de Sitio (AgregarSitioRequest). */
export interface SitioRequest {
  nombre: string;
  direccion?: string | null;
}

/**
 * Cuenta las fases cubiertas de un Sitio (0..4) para derivar un porcentaje de
 * avance en la UI (Req 21.3, 21.4). Es una utilidad de presentacion en el
 * cliente; el estado consolidado oficial lo deriva el backend.
 */
export function fasesCubiertas(avance: SitioAvance): number {
  return (
    (avance.tieneLevantamientoCompletado ? 1 : 0) +
    (avance.tienePermisoAprobado ? 1 : 0) +
    (avance.tieneOrdenFabricacionTerminada ? 1 : 0) +
    (avance.tieneInstalacionCompletada ? 1 : 0)
  );
}

/** Porcentaje de avance de un Sitio (0..100) segun las fases cubiertas. */
export function porcentajeAvanceSitio(avance: SitioAvance): number {
  return Math.round((fasesCubiertas(avance) / 4) * 100);
}

// -----------------------------------------------------------------------------
// Inventario base (Req 18)
// -----------------------------------------------------------------------------

/** Tipo de movimiento de inventario base (TipoMovimiento.valorBd). */
export type TipoMovimiento = 'entrada' | 'salida' | 'ajuste';

/** Material del inventario base (MaterialDto). */
export interface Material {
  id: string;
  nombre: string;
  unidadMedida: string;
  stockMinimo: number;
  existencias: number;
  stockBajo: boolean;
  activo: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de alta de Material (CrearMaterialRequest). */
export interface MaterialRequest {
  nombre: string;
  unidadMedida: string;
  stockMinimo: number;
}

/** Movimiento de inventario base (MovimientoInventarioDto). */
export interface MovimientoInventario {
  id: string;
  materialId: string;
  tipo: TipoMovimiento;
  cantidad: number;
  existenciasResultantes: number;
  ordenFabricacionId: string | null;
  motivo: string | null;
  version: number;
  createdAt: string;
}

/** Cuerpo para registrar un movimiento (RegistrarMovimientoRequest). */
export interface MovimientoRequest {
  tipo: TipoMovimiento;
  cantidad: number;
  motivo?: string | null;
}

/** Etiquetas legibles del tipo de movimiento. */
export const ETIQUETA_TIPO_MOVIMIENTO: Record<TipoMovimiento, string> = {
  entrada: 'Entrada',
  salida: 'Salida',
  ajuste: 'Ajuste',
};

// -----------------------------------------------------------------------------
// Inventario avanzado por Almacen (Req 60)
// -----------------------------------------------------------------------------

/** Almacen (AlmacenDto). */
export interface Almacen {
  id: string;
  nombre: string;
  tipo: string;
  activo: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de alta/edicion de Almacen (Crear/ActualizarAlmacenRequest). */
export interface AlmacenRequest {
  nombre: string;
  tipo: string;
}

/** Saldo de existencias de un Material en un Almacen (ExistenciaAlmacenDto). */
export interface ExistenciaAlmacen {
  id: string;
  almacenId: string;
  materialId: string;
  cantidad: number;
  costoPromedio: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Fila del Kardex por Almacen (MovimientoAlmacenDto). */
export interface MovimientoAlmacen {
  id: string;
  almacenId: string;
  materialId: string;
  loteId: string | null;
  tipo: string;
  cantidad: number;
  costoUnitario: number;
  costoTotal: number;
  saldoCantidad: number;
  saldoCostoTotal: number;
  transferenciaId: string | null;
  motivo: string | null;
  version: number;
  createdAt: string;
}

// -----------------------------------------------------------------------------
// Inventario avanzado: movimientos, configuracion y lotes (Req 60)
// -----------------------------------------------------------------------------

/**
 * Metodo de costeo de un Material (ConfigInventarioMaterial.metodoCosteo).
 * `promedio` = costo promedio ponderado; `peps` = primeras entradas, primeras
 * salidas. Coincide con la etiqueta lowercase del backend.
 */
export type MetodoCosteo = 'promedio' | 'peps';

/** Etiquetas legibles es-MX del metodo de costeo. */
export const ETIQUETA_METODO_COSTEO: Record<MetodoCosteo, string> = {
  promedio: 'Promedio ponderado',
  peps: 'PEPS (primeras entradas, primeras salidas)',
};

/**
 * Cuerpo para registrar una ENTRADA de inventario en un Almacen
 * (RegistrarEntradaRequest). El Almacen viaja en la ruta. El costo unitario es
 * obligatorio (>= 0); el lote es opcional y se envia por su codigo.
 */
export interface RegistrarEntradaRequest {
  materialId: string;
  loteCodigo?: string | null;
  cantidad: number;
  costoUnitario: number;
  motivo?: string | null;
}

/**
 * Cuerpo para registrar una SALIDA de inventario de un Almacen
 * (RegistrarSalidaRequest). El Almacen viaja en la ruta. En una salida NO se
 * envia el costo: lo determina el metodo de costeo configurado del Material.
 */
export interface RegistrarSalidaRequest {
  materialId: string;
  loteCodigo?: string | null;
  cantidad: number;
  motivo?: string | null;
}

/**
 * Cuerpo para transferir existencias de un Material entre dos Almacenes
 * (TransferirRequest). Origen y destino deben ser distintos.
 */
export interface TransferirRequest {
  almacenOrigenId: string;
  almacenDestinoId: string;
  materialId: string;
  cantidad: number;
  motivo?: string | null;
}

/**
 * Cuerpo para configurar (upsert) el inventario avanzado de un Material
 * (ConfigurarInventarioMaterialRequest). El Material viaja en la ruta.
 * `stockMaximo` nulo = sin tope; el resto de parametros son >= 0.
 */
export interface ConfigurarInventarioMaterialRequest {
  metodoCosteo: MetodoCosteo;
  stockMaximo?: number | null;
  controlLote: boolean;
  consumoPromedio: number;
  tiempoEntregaDias: number;
  stockSeguridad: number;
}

/**
 * Configuracion de inventario por Material (ConfigInventarioMaterialDto).
 * Incluye el `puntoReorden` DERIVADO por el backend
 * (`consumoPromedio * tiempoEntregaDias + stockSeguridad`).
 */
export interface ConfigInventarioMaterial {
  id: string;
  materialId: string;
  metodoCosteo: MetodoCosteo;
  stockMaximo: number | null;
  controlLote: boolean;
  consumoPromedio: number;
  tiempoEntregaDias: number;
  stockSeguridad: number;
  puntoReorden: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Cuerpo para dar de alta un Lote de un Material (CrearLoteRequest). El Material
 * viaja en la ruta. El codigo es unico por Material; la caducidad es opcional.
 */
export interface CrearLoteRequest {
  codigo: string;
  fechaCaducidad?: string | null;
}

/** Lote de un Material (LoteDto). */
export interface Lote {
  id: string;
  materialId: string;
  codigo: string;
  fechaCaducidad: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}
