// =============================================================================
// Servicio de Cotizaciones y Pruebas de Diseno (Req 6, 15)
// -----------------------------------------------------------------------------
// Integra el contrato REST comercial-crm (CotizacionController + PruebaDisenoController):
//   - GET  /cotizaciones?clienteId&estado&canalVentaId&page&size (cotizacion:listar)
//   - GET  /cotizaciones/{id}                     (cotizacion:leer)
//   - POST /cotizaciones                          (cotizacion:crear)
//   - POST /cotizaciones/{id}/partidas            (cotizacion:actualizar)
//   - PUT  /cotizaciones/{id}/estado              (cotizacion:cambiar_estado)
//   - PUT  /cotizaciones/{id}/canal-venta         (cotizacion:actualizar)
//   - GET  /cotizaciones/{id}/pdf                 (cotizacion:leer)      -> application/pdf
//   - POST /cotizaciones/{id}/enviar-correo       (cotizacion:actualizar)
//   - POST /cotizaciones/{cotizacionId}/pruebas-diseno   (prueba_diseno:crear)
//   - GET  /cotizaciones/{cotizacionId}/pruebas-diseno   (prueba_diseno:listar)
//   - POST /pruebas-diseno/{id}/aprobar           (prueba_diseno:cambiar_estado)
//   - POST /pruebas-diseno/{id}/rechazar          (prueba_diseno:cambiar_estado)
//
// El precio sugerido de una partida lo resuelve el backend cuando se envia una
// partida con productoId y sin precioUnitario (Req 59.4); el DTO devuelto trae el
// precioUnitario final aplicado, que la vista muestra al Usuario.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  Cotizacion,
  CotizacionRequest,
  EnviarCorreoRequest,
  EstadoCotizacion,
  PartidaRequest,
  PruebaDiseno,
  ResultadoRechazoPruebaDiseno,
} from '../models/comercial.models';

/** Filtros opcionales del listado de Cotizaciones. */
export interface FiltroCotizaciones {
  clienteId?: string | null;
  estado?: EstadoCotizacion | null;
  canalVentaId?: string | null;
}

@Injectable({ providedIn: 'root' })
export class CotizacionesService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista Cotizaciones de forma paginada con filtros opcionales (Req 6.8). */
  listar(
    filtro: FiltroCotizaciones,
    page: number,
    size: number,
  ): Observable<PaginaResponse<Cotizacion>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.clienteId) {
      params = params.set('clienteId', filtro.clienteId);
    }
    if (filtro.estado) {
      params = params.set('estado', filtro.estado);
    }
    if (filtro.canalVentaId) {
      params = params.set('canalVentaId', filtro.canalVentaId);
    }
    return this.http.get<PaginaResponse<Cotizacion>>(this.api.url('/cotizaciones'), { params });
  }

  /** Consulta una Cotizacion con sus partidas y totales (Req 6). */
  consultar(id: string): Observable<Cotizacion> {
    return this.http.get<Cotizacion>(this.api.url(`/cotizaciones/${id}`));
  }

  /** Da de alta una Cotizacion en borrador con 1..500 partidas (Req 6.1, 6.2). */
  crear(request: CotizacionRequest): Observable<Cotizacion> {
    return this.http.post<Cotizacion>(this.api.url('/cotizaciones'), request);
  }

  /** Descarga el PDF de una Cotizacion como blob (Content-Type application/pdf, Req 6). */
  descargarPdf(id: string): Observable<Blob> {
    return this.http.get(this.api.url(`/cotizaciones/${id}/pdf`), { responseType: 'blob' });
  }

  /**
   * Envia la Cotizacion por correo y devuelve el DTO actualizado (fija enviadaEn y
   * mueve borrador->enviada). Si no se indica email, el backend usa el del cliente;
   * si tampoco existe, responde 422 pidiendo un correo (Req 6).
   */
  enviarCorreo(id: string, request: EnviarCorreoRequest = {}): Observable<Cotizacion> {
    return this.http.post<Cotizacion>(this.api.url(`/cotizaciones/${id}/enviar-correo`), request);
  }

  /** Agrega una partida a una Cotizacion en borrador y recalcula totales (Req 6.3). */
  agregarPartida(id: string, partida: PartidaRequest): Observable<Cotizacion> {
    return this.http.post<Cotizacion>(this.api.url(`/cotizaciones/${id}/partidas`), partida);
  }

  /** Cambia el estado de una Cotizacion segun la maquina de estados (Req 6.6). */
  cambiarEstado(id: string, estado: EstadoCotizacion): Observable<Cotizacion> {
    return this.http.put<Cotizacion>(this.api.url(`/cotizaciones/${id}/estado`), { estado });
  }

  /** Clasifica una Cotizacion por canal de venta; canalVentaId nulo limpia (Req 63.1). */
  asignarCanalVenta(id: string, canalVentaId: string | null): Observable<Cotizacion> {
    return this.http.put<Cotizacion>(this.api.url(`/cotizaciones/${id}/canal-venta`), {
      canalVentaId,
    });
  }

  // ---------------------------------------------------------------------------
  // Pruebas de Diseno de una Cotizacion (Req 15)
  // ---------------------------------------------------------------------------

  /** Lista las Pruebas de Diseno de una Cotizacion, mas reciente primero (Req 15.6). */
  listarPruebas(
    cotizacionId: string,
    page: number,
    size: number,
  ): Observable<PaginaResponse<PruebaDiseno>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PaginaResponse<PruebaDiseno>>(
      this.api.url(`/cotizaciones/${cotizacionId}/pruebas-diseno`),
      { params },
    );
  }

  /** Genera la Prueba de Diseno inicial (version 1, pendiente) (Req 15.1). */
  generarPrueba(cotizacionId: string): Observable<PruebaDiseno> {
    return this.http.post<PruebaDiseno>(
      this.api.url(`/cotizaciones/${cotizacionId}/pruebas-diseno`),
      {},
    );
  }

  /** Aprueba una Prueba de Diseno pendiente (Req 15.2). */
  aprobarPrueba(id: string): Observable<PruebaDiseno> {
    return this.http.post<PruebaDiseno>(this.api.url(`/pruebas-diseno/${id}/aprobar`), {});
  }

  /** Rechaza una Prueba de Diseno y genera la nueva version pendiente (Req 15.3). */
  rechazarPrueba(id: string): Observable<ResultadoRechazoPruebaDiseno> {
    return this.http.post<ResultadoRechazoPruebaDiseno>(
      this.api.url(`/pruebas-diseno/${id}/rechazar`),
      {},
    );
  }
}
