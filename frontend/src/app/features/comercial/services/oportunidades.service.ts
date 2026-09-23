// =============================================================================
// Servicio de Oportunidades / pipeline (Req 14, 63)
// -----------------------------------------------------------------------------
// Integra el contrato REST comercial-crm (OportunidadController):
//   - GET  /oportunidades?clienteId&etapa&responsableId&canalVentaId&page&size (oportunidad:listar)
//   - GET  /oportunidades/{id}                 (oportunidad:leer)
//   - POST /oportunidades                      (oportunidad:crear)
//   - PUT  /oportunidades/{id}/etapa           (oportunidad:cambiar_estado)
//   - PUT  /oportunidades/{id}/responsable     (oportunidad:actualizar)
//   - PUT  /oportunidades/{id}/canal-venta     (oportunidad:actualizar)
//   - POST /oportunidades/{id}/convertir       (oportunidad:actualizar)
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  ConversionCotizacion,
  EtapaOportunidad,
  Oportunidad,
  OportunidadRequest,
} from '../models/comercial.models';

/** Filtros opcionales del listado de Oportunidades. */
export interface FiltroOportunidades {
  clienteId?: string | null;
  etapa?: EtapaOportunidad | null;
  responsableId?: string | null;
  canalVentaId?: string | null;
}

@Injectable({ providedIn: 'root' })
export class OportunidadesService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista Oportunidades de forma paginada con filtros opcionales (Req 14.7). */
  listar(
    filtro: FiltroOportunidades,
    page: number,
    size: number,
  ): Observable<PaginaResponse<Oportunidad>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.clienteId) {
      params = params.set('clienteId', filtro.clienteId);
    }
    if (filtro.etapa) {
      params = params.set('etapa', filtro.etapa);
    }
    if (filtro.responsableId) {
      params = params.set('responsableId', filtro.responsableId);
    }
    if (filtro.canalVentaId) {
      params = params.set('canalVentaId', filtro.canalVentaId);
    }
    return this.http.get<PaginaResponse<Oportunidad>>(this.api.url('/oportunidades'), { params });
  }

  /** Consulta una Oportunidad por su identificador (Req 14). */
  consultar(id: string): Observable<Oportunidad> {
    return this.http.get<Oportunidad>(this.api.url(`/oportunidades/${id}`));
  }

  /** Da de alta una Oportunidad en etapa inicial (Req 14.1). */
  crear(request: OportunidadRequest): Observable<Oportunidad> {
    return this.http.post<Oportunidad>(this.api.url('/oportunidades'), request);
  }

  /** Cambia la etapa de una Oportunidad segun la maquina de estados (Req 14.3). */
  cambiarEtapa(id: string, etapa: EtapaOportunidad): Observable<Oportunidad> {
    return this.http.put<Oportunidad>(this.api.url(`/oportunidades/${id}/etapa`), { etapa });
  }

  /** Asigna el Usuario responsable de una Oportunidad (Req 14.2). */
  asignarResponsable(id: string, usuarioId: string): Observable<Oportunidad> {
    return this.http.put<Oportunidad>(this.api.url(`/oportunidades/${id}/responsable`), {
      usuarioId,
    });
  }

  /** Clasifica una Oportunidad por canal de venta; canalVentaId nulo limpia (Req 63.1). */
  asignarCanalVenta(id: string, canalVentaId: string | null): Observable<Oportunidad> {
    return this.http.put<Oportunidad>(this.api.url(`/oportunidades/${id}/canal-venta`), {
      canalVentaId,
    });
  }

  /** Convierte una Oportunidad ganada en una Cotizacion (Req 14.5). */
  convertir(id: string): Observable<ConversionCotizacion> {
    return this.http.post<ConversionCotizacion>(
      this.api.url(`/oportunidades/${id}/convertir`),
      {},
    );
  }
}
