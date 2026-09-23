// =============================================================================
// Modelos del Portal del Cliente (Req 45)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs reutilizados por el
// PortalClienteController (cotizaciones, pruebas de diseno, proyectos/avance,
// tickets y facturas). El backend acota cada consulta al Cliente autenticado: la
// UI NUNCA envia un clienteId. Importes decimales (escala 2); instantes ISO UTC.
// =============================================================================

/** Partida de una Cotizacion (PartidaCotizacionDto — proyeccion parcial usada en el portal). */
export interface PartidaCotizacion {
  id?: string;
  descripcion: string;
  cantidad: number;
  precioUnitario?: number;
  subtotal?: number;
  [clave: string]: unknown;
}

/** Cotizacion del Cliente (CotizacionDto). */
export interface Cotizacion {
  id: string;
  clienteId: string;
  oportunidadId: string | null;
  estado: string;
  subtotal: number;
  total: number;
  partidas: PartidaCotizacion[];
  canalVentaId: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Prueba de diseno del Cliente (PruebaDisenoDto). */
export interface PruebaDiseno {
  id: string;
  cotizacionId: string;
  numeroVersion: number;
  /** pendiente | aprobada | rechazada. */
  estado: string;
  aprobadaPor: string | null;
  rechazadaPor: string | null;
  decididaEn: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Resultado de rechazar una prueba: la rechazada + la nueva version pendiente. */
export interface ResultadoRechazoPruebaDiseno {
  rechazada: PruebaDiseno;
  nuevaVersion: PruebaDiseno;
}

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

/** Sitio con su avance por fase (SitioAvanceDto). */
export interface SitioAvance {
  sitio: Sitio;
  tieneLevantamientoCompletado: boolean;
  tienePermisoAprobado: boolean;
  tieneOrdenFabricacionTerminada: boolean;
  tieneInstalacionCompletada: boolean;
}

/** Proyecto del Cliente (ProyectoDto). En el listado estadoConsolidado es null y sitios []. */
export interface Proyecto {
  id: string;
  clienteId: string;
  nombre: string;
  estadoConsolidado: string | null;
  sitios: SitioAvance[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Ticket de servicio del Cliente (TicketServicioDto). */
export interface TicketServicio {
  id: string;
  contratoMantenimientoId: string | null;
  clienteId: string;
  origen: string;
  estado: string;
  asignadoTipo: string | null;
  asignadoId: string | null;
  abiertoEn: string;
  resueltoEn: string | null;
  slaRespuestaCumplido: boolean | null;
  slaResolucionCumplido: boolean | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Factura del Cliente (FacturaDto). */
export interface Factura {
  id: string;
  cotizacionId: string | null;
  ordenFabricacionId: string | null;
  clienteId: string;
  receptorRfc: string;
  receptorNombre: string;
  receptorCp: string;
  receptorRegimenFiscal: string;
  usoCfdi: string;
  subtotal: number;
  iva: number;
  retenciones: number;
  total: number;
  estado: string;
  folioFiscal: string | null;
  fechaTimbrado: string | null;
  motivoCancelacion: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}
