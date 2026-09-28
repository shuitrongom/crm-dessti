// =============================================================================
// Servicio de Actividades de seguimiento comercial (V79)
// -----------------------------------------------------------------------------
// Integra el contrato REST comercial-crm (ActividadController):
//   - GET    /actividades?clienteId&oportunidadId&tipo&estado&responsableId&page&size (actividad:listar)
//   - GET    /actividades/{id}                 (actividad:leer)
//   - POST   /actividades                      (actividad:crear)
//   - PUT    /actividades/{id}                 (actividad:actualizar) editar asunto/descripcion
//   - PUT    /actividades/{id}/completar       (actividad:actualizar)
//   - PUT    /actividades/{id}/cancelar        (actividad:actualizar)
//   - PUT    /actividades/{id}/reprogramar     (actividad:actualizar)
//   - PUT    /actividades/{id}/responsable     (actividad:actualizar)
//   - DELETE /actividades/{id}                 (actividad:eliminar)
//
// El listado devuelve las actividades ordenadas por fecha programada descendente
// (timeline). No se inventan campos ni endpoints: cada metodo espeja el contrato.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  Actividad,
  ActividadRequest,
  EditarActividadRequest,
  EstadoActividad,
  ReprogramarActividadRequest,
  TipoActividad,
} from '../models/comercial.models';

/** Filtros opcionales del listado de Actividades. */
export interface FiltroActividades {
  clienteId?: string | null;
  oportunidadId?: string | null;
  tipo?: TipoActividad | null;
  estado?: EstadoActividad | null;
  responsableId?: string | null;
}

@Injectable({ providedIn: 'root' })
export class ActividadesService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista Actividades de forma paginada con filtros opcionales (timeline, orden desc). */
  listar(
    filtro: FiltroActividades,
    page: number,
    size: number,
  ): Observable<PaginaResponse<Actividad>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.clienteId) {
      params = params.set('clienteId', filtro.clienteId);
    }
    if (filtro.oportunidadId) {
      params = params.set('oportunidadId', filtro.oportunidadId);
    }
    if (filtro.tipo) {
      params = params.set('tipo', filtro.tipo);
    }
    if (filtro.estado) {
      params = params.set('estado', filtro.estado);
    }
    if (filtro.responsableId) {
      params = params.set('responsableId', filtro.responsableId);
    }
    return this.http.get<PaginaResponse<Actividad>>(this.api.url('/actividades'), { params });
  }

  /** Consulta una Actividad por su identificador. */
  consultar(id: string): Observable<Actividad> {
    return this.http.get<Actividad>(this.api.url(`/actividades/${id}`));
  }

  /** Registra una Actividad de seguimiento. */
  crear(request: ActividadRequest): Observable<Actividad> {
    return this.http.post<Actividad>(this.api.url('/actividades'), request);
  }

  /** Edita el asunto y la descripcion de una Actividad. */
  editar(id: string, request: EditarActividadRequest): Observable<Actividad> {
    return this.http.put<Actividad>(this.api.url(`/actividades/${id}`), request);
  }

  /** Marca una Actividad como completada. */
  completar(id: string): Observable<Actividad> {
    return this.http.put<Actividad>(this.api.url(`/actividades/${id}/completar`), {});
  }

  /** Marca una Actividad como cancelada. */
  cancelar(id: string): Observable<Actividad> {
    return this.http.put<Actividad>(this.api.url(`/actividades/${id}/cancelar`), {});
  }

  /** Reprograma la fecha y el vencimiento de una Actividad no finalizada. */
  reprogramar(id: string, request: ReprogramarActividadRequest): Observable<Actividad> {
    return this.http.put<Actividad>(this.api.url(`/actividades/${id}/reprogramar`), request);
  }

  /** Asigna o limpia el Usuario responsable del seguimiento (usuarioId nulo desasigna). */
  asignarResponsable(id: string, usuarioId: string | null): Observable<Actividad> {
    return this.http.put<Actividad>(this.api.url(`/actividades/${id}/responsable`), { usuarioId });
  }

  /** Elimina una Actividad del tenant. */
  eliminar(id: string): Observable<void> {
    return this.http.delete<void>(this.api.url(`/actividades/${id}`));
  }
}
