// =============================================================================
// Servicio de la Bandeja_Unificada (Req 64)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (com.empresa.crm.social.* / BandejaController),
// base relativa /api/v1:
//   GET  /social/bandeja?canal&clienteId&estado&page&size
//   GET  /social/bandeja/{id}
//   GET  /social/bandeja/{id}/mensajes?page&size
//   POST /social/bandeja/{id}/mensajes   (422 fuera de la Ventana_Servicio con texto)
//   PUT  /social/bandeja/{id}/asignacion (handover)
//   PUT  /social/bandeja/{id}/cierre
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  Conversacion,
  EnviarMensajeRequest,
  MensajeSocial,
} from '../models/social.models';

/** Filtros opcionales del listado de la bandeja. */
export interface FiltroBandeja {
  canal?: string | null;
  clienteId?: string | null;
  estado?: string | null;
}

@Injectable({ providedIn: 'root' })
export class BandejaService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista la Bandeja_Unificada paginada con filtros opcionales (Req 64.5, 64.14). */
  listar(filtro: FiltroBandeja, page = 0, size = 20): Observable<PaginaResponse<Conversacion>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.canal) {
      params = params.set('canal', filtro.canal);
    }
    if (filtro.clienteId) {
      params = params.set('clienteId', filtro.clienteId);
    }
    if (filtro.estado) {
      params = params.set('estado', filtro.estado);
    }
    return this.http.get<PaginaResponse<Conversacion>>(this.api.url('/social/bandeja'), { params });
  }

  /** Consulta una Conversacion por su identificador. */
  consultar(id: string): Observable<Conversacion> {
    return this.http.get<Conversacion>(this.api.url(`/social/bandeja/${id}`));
  }

  /** Historial paginado de mensajes de una Conversacion (Req 64.5). */
  listarMensajes(id: string, page = 0, size = 20): Observable<PaginaResponse<MensajeSocial>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PaginaResponse<MensajeSocial>>(
      this.api.url(`/social/bandeja/${id}/mensajes`),
      { params },
    );
  }

  /** Envia un Mensaje_Social aplicando las guardas de Ventana_Servicio y Opt_In. */
  enviarMensaje(id: string, request: EnviarMensajeRequest): Observable<MensajeSocial> {
    return this.http.post<MensajeSocial>(this.api.url(`/social/bandeja/${id}/mensajes`), request);
  }

  /** Asigna/transfiere la Conversacion a un Usuario (handover, Req 64.10). */
  asignar(id: string, usuarioId: string): Observable<Conversacion> {
    return this.http.put<Conversacion>(this.api.url(`/social/bandeja/${id}/asignacion`), {
      usuarioId,
    });
  }

  /** Cierra la Conversacion (Req 64.10). */
  cerrar(id: string): Observable<Conversacion> {
    return this.http.put<Conversacion>(this.api.url(`/social/bandeja/${id}/cierre`), {});
  }

  /**
   * Vincula la Conversacion a un Cliente existente del tenant (lead social,
   * Req 5.1, 5.2). PUT /social/bandeja/{id}/vinculacion; 404 si el Cliente no
   * existe en el tenant.
   */
  vincular(id: string, clienteId: string): Observable<Conversacion> {
    return this.http.put<Conversacion>(this.api.url(`/social/bandeja/${id}/vinculacion`), {
      clienteId,
    });
  }
}
