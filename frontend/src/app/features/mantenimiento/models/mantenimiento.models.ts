// =============================================================================
// Modelos del modulo Mantenimiento (Req 20)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan EXACTAMENTE los DTOs del backend
// (com.empresa.crm.mantenimiento.*).
// =============================================================================

/** Contrato de mantenimiento (ContratoMantenimientoDto). */
export interface ContratoMantenimiento {
  id: string;
  clienteId: string;
  tipo: string;
  slaRespuestaHoras: number;
  slaResolucionHoras: number;
  activo: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de POST /mantenimiento/contratos (CrearContratoRequest). */
export interface CrearContratoRequest {
  clienteId: string;
  tipo: string;
  slaRespuestaHoras: number;
  slaResolucionHoras: number;
}

/** Estados de un ticket de servicio (etiquetas valorBd del backend). */
export type EstadoTicket = 'abierto' | 'asignado' | 'en_proceso' | 'resuelto' | 'cerrado';

/** Ticket de servicio (TicketServicioDto). */
export interface TicketServicio {
  id: string;
  contratoMantenimientoId: string | null;
  clienteId: string;
  origen: string;
  estado: EstadoTicket;
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

/** Cuerpo de POST /mantenimiento/tickets (GenerarTicketRequest). */
export interface GenerarTicketRequest {
  contratoMantenimientoId: string | null;
  clienteId: string;
  origen: string;
}

/** Cuerpo de PUT /mantenimiento/tickets/{id}/asignacion (AsignarTicketRequest). */
export interface AsignarTicketRequest {
  asignadoTipo: string;
  asignadoId: string;
}
