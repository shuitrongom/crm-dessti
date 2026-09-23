// =============================================================================
// Servicio del modulo Mantenimiento (Req 20)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (com.empresa.crm.mantenimiento.*), base
// relativa /api/v1:
//   Contratos  GET/POST /mantenimiento/contratos, GET /{id}
//   Tickets    GET/POST /mantenimiento/tickets, GET /{id},
//              PUT /{id}/asignacion, PUT /{id}/estado
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  AsignarTicketRequest,
  ContratoMantenimiento,
  CrearContratoRequest,
  GenerarTicketRequest,
  TicketServicio,
} from '../models/mantenimiento.models';

@Injectable({ providedIn: 'root' })
export class MantenimientoService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  // --- Contratos -------------------------------------------------------------

  listarContratos(
    clienteId: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<ContratoMantenimiento>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (clienteId) {
      params = params.set('clienteId', clienteId);
    }
    return this.http.get<PaginaResponse<ContratoMantenimiento>>(
      this.api.url('/mantenimiento/contratos'),
      { params },
    );
  }

  crearContrato(request: CrearContratoRequest): Observable<ContratoMantenimiento> {
    return this.http.post<ContratoMantenimiento>(this.api.url('/mantenimiento/contratos'), request);
  }

  // --- Tickets ---------------------------------------------------------------

  listarTickets(
    estado: string | null,
    slaVencido: boolean | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<TicketServicio>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) {
      params = params.set('estado', estado);
    }
    if (slaVencido !== null) {
      params = params.set('slaVencido', slaVencido);
    }
    return this.http.get<PaginaResponse<TicketServicio>>(this.api.url('/mantenimiento/tickets'), {
      params,
    });
  }

  generarTicket(request: GenerarTicketRequest): Observable<TicketServicio> {
    return this.http.post<TicketServicio>(this.api.url('/mantenimiento/tickets'), request);
  }

  asignarTicket(id: string, request: AsignarTicketRequest): Observable<TicketServicio> {
    return this.http.put<TicketServicio>(
      this.api.url(`/mantenimiento/tickets/${id}/asignacion`),
      request,
    );
  }

  cambiarEstadoTicket(id: string, estado: string): Observable<TicketServicio> {
    return this.http.put<TicketServicio>(this.api.url(`/mantenimiento/tickets/${id}/estado`), {
      estado,
    });
  }
}
