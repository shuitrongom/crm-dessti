// =============================================================================
// Servicio del Portal del Cliente (Req 45)
// -----------------------------------------------------------------------------
// Integra PortalClienteController (base /api/v1, rol cliente_portal):
//   GET  /portal/cotizaciones
//   GET  /portal/pruebas-diseno
//   POST /portal/pruebas-diseno/{id}/aprobacion
//   POST /portal/pruebas-diseno/{id}/rechazo
//   GET  /portal/proyectos
//   GET  /portal/proyectos/{id}
//   GET  /portal/tickets
//   GET  /portal/facturas
// El backend acota TODO al Cliente autenticado: NUNCA se envia un clienteId.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  Cotizacion,
  Factura,
  PerfilCliente,
  Proyecto,
  PruebaDiseno,
  QuejaPortal,
  RegistrarQuejaPortalRequest,
  ResultadoRechazoPruebaDiseno,
  TicketServicio,
} from '../models/portal.models';

@Injectable({ providedIn: 'root' })
export class PortalService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  private pagina(page: number, size: number): { params: HttpParams } {
    return { params: new HttpParams().set('page', page).set('size', size) };
  }

  /** Mis cotizaciones (Req 45.1). */
  misCotizaciones(page = 0, size = 20): Observable<PaginaResponse<Cotizacion>> {
    return this.http.get<PaginaResponse<Cotizacion>>(
      this.api.url('/portal/cotizaciones'),
      this.pagina(page, size),
    );
  }

  /** Aprueba una cotización propia enviada (Req 45.2, 6.6). */
  aprobarCotizacion(id: string): Observable<Cotizacion> {
    return this.http.post<Cotizacion>(
      this.api.url(`/portal/cotizaciones/${id}/aprobacion`),
      {},
    );
  }

  /** Rechaza una cotización propia enviada (Req 45.2, 6.6). */
  rechazarCotizacion(id: string): Observable<Cotizacion> {
    return this.http.post<Cotizacion>(
      this.api.url(`/portal/cotizaciones/${id}/rechazo`),
      {},
    );
  }

  /** Mis pruebas de diseno (Req 45.1). */
  misPruebasDiseno(page = 0, size = 20): Observable<PaginaResponse<PruebaDiseno>> {
    return this.http.get<PaginaResponse<PruebaDiseno>>(
      this.api.url('/portal/pruebas-diseno'),
      this.pagina(page, size),
    );
  }

  /** Aprueba una prueba de diseno propia (Req 45.2). */
  aprobarPrueba(id: string): Observable<PruebaDiseno> {
    return this.http.post<PruebaDiseno>(
      this.api.url(`/portal/pruebas-diseno/${id}/aprobacion`),
      {},
    );
  }

  /** Rechaza una prueba de diseno propia y genera la siguiente version (Req 45.2, 15.3). */
  rechazarPrueba(id: string): Observable<ResultadoRechazoPruebaDiseno> {
    return this.http.post<ResultadoRechazoPruebaDiseno>(
      this.api.url(`/portal/pruebas-diseno/${id}/rechazo`),
      {},
    );
  }

  /** Mis proyectos (resumen paginado; Req 45.1). */
  misProyectos(page = 0, size = 20): Observable<PaginaResponse<Proyecto>> {
    return this.http.get<PaginaResponse<Proyecto>>(
      this.api.url('/portal/proyectos'),
      this.pagina(page, size),
    );
  }

  /** Avance consolidado de un proyecto propio, con sitios y avance por fase (Req 45.1). */
  avanceDeProyecto(id: string): Observable<Proyecto> {
    return this.http.get<Proyecto>(this.api.url(`/portal/proyectos/${id}`));
  }

  /** Mis tickets de servicio (Req 45.1). */
  misTickets(page = 0, size = 20): Observable<PaginaResponse<TicketServicio>> {
    return this.http.get<PaginaResponse<TicketServicio>>(
      this.api.url('/portal/tickets'),
      this.pagina(page, size),
    );
  }

  /** Mis facturas (Req 45.1). */
  misFacturas(page = 0, size = 20): Observable<PaginaResponse<Factura>> {
    return this.http.get<PaginaResponse<Factura>>(
      this.api.url('/portal/facturas'),
      this.pagina(page, size),
    );
  }

  /** Mis quejas levantadas desde el portal (Req 45.1). */
  misQuejas(page = 0, size = 20): Observable<PaginaResponse<QuejaPortal>> {
    return this.http.get<PaginaResponse<QuejaPortal>>(
      this.api.url('/portal/quejas'),
      this.pagina(page, size),
    );
  }

  /** Levanta una queja con origen portal (Req 45.2, 70.1). */
  registrarQueja(request: RegistrarQuejaPortalRequest): Observable<QuejaPortal> {
    return this.http.post<QuejaPortal>(this.api.url('/portal/quejas'), request);
  }

  /** Mi perfil: datos del Cliente autenticado (Req 45.1). */
  miPerfil(): Observable<PerfilCliente> {
    return this.http.get<PerfilCliente>(this.api.url('/portal/perfil'));
  }
}
