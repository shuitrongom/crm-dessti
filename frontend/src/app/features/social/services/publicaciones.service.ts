// =============================================================================
// Servicio de Publicacion_Social (Req 65.1-65.6, 65.10, 65.11)
// -----------------------------------------------------------------------------
// Integra PublicacionesController (base /api/v1):
//   GET  /social/publicaciones?canal&estado&page&size
//   POST /social/publicaciones
//   PUT  /social/publicaciones/{id}/estado
//   POST /social/publicaciones/{id}/publicar
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  CrearPublicacionSocialRequest,
  EstadoPublicacion,
  PublicacionSocial,
} from '../models/social.models';

/** Filtros opcionales del listado de publicaciones. */
export interface FiltroPublicacion {
  canal?: string | null;
  estado?: string | null;
}

@Injectable({ providedIn: 'root' })
export class PublicacionesService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista las Publicacion_Social del tenant, filtrables por canal y estado (Req 65.10). */
  listar(
    filtro: FiltroPublicacion,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<PublicacionSocial>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.canal) {
      params = params.set('canal', filtro.canal);
    }
    if (filtro.estado) {
      params = params.set('estado', filtro.estado);
    }
    return this.http.get<PaginaResponse<PublicacionSocial>>(this.api.url('/social/publicaciones'), {
      params,
    });
  }

  /** Consulta una Publicacion_Social por su identificador. */
  consultar(id: string): Observable<PublicacionSocial> {
    return this.http.get<PublicacionSocial>(this.api.url(`/social/publicaciones/${id}`));
  }

  /** Crea una Publicacion_Social en estado borrador (Req 65.1, 65.2). */
  crear(request: CrearPublicacionSocialRequest): Observable<PublicacionSocial> {
    return this.http.post<PublicacionSocial>(this.api.url('/social/publicaciones'), request);
  }

  /** Aplica un cambio de estado manual (borrador -> programada) (Req 65.3, 65.4). */
  cambiarEstado(id: string, estado: EstadoPublicacion): Observable<PublicacionSocial> {
    return this.http.put<PublicacionSocial>(this.api.url(`/social/publicaciones/${id}/estado`), {
      estado,
    });
  }

  /** Publica una Publicacion_Social programada con politica de reintentos (Req 65.5, 65.6). */
  publicar(id: string): Observable<PublicacionSocial> {
    return this.http.post<PublicacionSocial>(this.api.url(`/social/publicaciones/${id}/publicar`), {});
  }
}
