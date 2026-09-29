// =============================================================================
// Servicio de Proyectos y Sitios (Req 21)
// -----------------------------------------------------------------------------
// Integra el contrato REST operacion-produccion (ProyectoController):
//   - GET  /proyectos?clienteId&page&size   (proyecto:listar) -> resumen
//   - GET  /proyectos/{id}                  (proyecto:leer)   -> consolidado con sitios
//   - POST /proyectos                       (proyecto:crear)
//   - POST /proyectos/{id}/sitios           (proyecto:actualizar)
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  AvanceSitioRequest,
  Proyecto,
  ProyectoRequest,
  Sitio,
  SitioRequest,
} from '../models/operacion.models';

@Injectable({ providedIn: 'root' })
export class ProyectosService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista Proyectos de forma paginada (proyeccion de resumen) (Req 21.5). */
  listar(
    clienteId: string | null,
    page: number,
    size: number,
  ): Observable<PaginaResponse<Proyecto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (clienteId) {
      params = params.set('clienteId', clienteId);
    }
    return this.http.get<PaginaResponse<Proyecto>>(this.api.url('/proyectos'), { params });
  }

  /** Consulta un Proyecto detallado con estado consolidado y avance por sitio (Req 21.4). */
  consultar(id: string): Observable<Proyecto> {
    return this.http.get<Proyecto>(this.api.url(`/proyectos/${id}`));
  }

  /** Crea un Proyecto asociado a un Cliente (Req 21.1). */
  crear(request: ProyectoRequest): Observable<Proyecto> {
    return this.http.post<Proyecto>(this.api.url('/proyectos'), request);
  }

  /**
   * Edita el nombre de un Proyecto existente (Req 21.1, 21.6). Devuelve el Proyecto
   * detallado tras el cambio. El Cliente asociado es inmutable.
   */
  editar(proyectoId: string, request: { nombre: string }): Observable<Proyecto> {
    return this.http.put<Proyecto>(this.api.url(`/proyectos/${proyectoId}`), request);
  }

  /** Agrega un Sitio a un Proyecto existente (Req 21.2). */
  agregarSitio(proyectoId: string, request: SitioRequest): Observable<Sitio> {
    return this.http.post<Sitio>(this.api.url(`/proyectos/${proyectoId}/sitios`), request);
  }

  /**
   * Edita los datos descriptivos (nombre y direccion) de un Sitio de un Proyecto
   * (Req 21.2, 21.6). Devuelve el Proyecto detallado tras el cambio.
   */
  editarSitio(
    proyectoId: string,
    sitioId: string,
    request: SitioRequest,
  ): Observable<Proyecto> {
    return this.http.put<Proyecto>(
      this.api.url(`/proyectos/${proyectoId}/sitios/${sitioId}`),
      request,
    );
  }

  /**
   * Avanza la fase operativa generica de un Sitio siguiendo la secuencia lineal
   * (Req 3.2). Requiere proyecto:actualizar. Devuelve el Proyecto detallado
   * (variante multi-sitio) tras el cambio.
   */
  actualizarAvanceSitio(
    proyectoId: string,
    sitioId: string,
    request: AvanceSitioRequest,
  ): Observable<Proyecto> {
    return this.http.put<Proyecto>(
      this.api.url(`/proyectos/${proyectoId}/sitios/${sitioId}/avance`),
      request,
    );
  }

  /**
   * Corrige (incluido retroceso) la fase de un Sitio. Operacion administrativa que
   * requiere proyecto:cambiar_estado. Devuelve el Proyecto detallado tras la correccion.
   */
  corregirFaseSitio(
    proyectoId: string,
    sitioId: string,
    request: AvanceSitioRequest,
  ): Observable<Proyecto> {
    return this.http.put<Proyecto>(
      this.api.url(`/proyectos/${proyectoId}/sitios/${sitioId}/correccion-fase`),
      request,
    );
  }
}
