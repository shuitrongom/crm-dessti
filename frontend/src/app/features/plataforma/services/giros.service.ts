// =============================================================================
// Servicio de administracion de plataforma: Giros (verticales de negocio) (Req 9)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (super_admin):
//   - GET   /plataforma/giros?activo&page&size   (giro:listar)
//   - POST  /plataforma/giros                     (giro:crear)
//   - POST  /plataforma/giros/{id}/activar        (giro:activar)
//   - POST  /plataforma/giros/{id}/desactivar     (giro:desactivar)
//   - DELETE /plataforma/giros/{id}               (giro:eliminar)
//
// El backend normaliza la `clave` a kebab-case en minusculas. La desactivacion
// devuelve 422 si el Giro esta en uso por >= 1 Empresa; el mensaje se propaga
// para que la UI lo muestre tal cual (mensajeDeError). La eliminacion devuelve
// 422 si el Giro es definitivo (ya tiene reglas de negocio) o esta en uso por
// alguna Empresa, y 404 si no existe.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { Giro } from '../models/plataforma.models';

/** Cuerpo de POST /plataforma/giros (Req 9). */
export interface CrearGiroRequest {
  clave: string;
  nombreVisible: string;
  descripcion?: string | null;
}

/** Cuerpo de PUT /plataforma/giros/{id} (edicion de Giro). La clave es inmutable. */
export interface ActualizarGiroRequest {
  nombreVisible: string;
  descripcion?: string | null;
}

@Injectable({ providedIn: 'root' })
export class GirosService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /**
   * Lista Giros paginados. Cuando `activo` es no nulo se filtra por ese estado;
   * si es `null` se listan todos (activos e inactivos).
   */
  listar(activo: boolean | null, page = 0, size = 20): Observable<PaginaResponse<Giro>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (activo !== null) {
      params = params.set('activo', activo);
    }
    return this.http.get<PaginaResponse<Giro>>(this.api.url('/plataforma/giros'), { params });
  }

  /** Da de alta un Giro (Req 9). */
  crear(request: CrearGiroRequest): Observable<Giro> {
    return this.http.post<Giro>(this.api.url('/plataforma/giros'), request);
  }

  /** Edita un Giro (nombre visible y descripcion; la clave es inmutable). */
  actualizar(id: string, request: ActualizarGiroRequest): Observable<Giro> {
    return this.http.put<Giro>(this.api.url(`/plataforma/giros/${id}`), request);
  }

  /** Activa un Giro; queda disponible para nuevas Empresas (Req 9). */
  activar(id: string): Observable<Giro> {
    return this.http.post<Giro>(this.api.url(`/plataforma/giros/${id}/activar`), {});
  }

  /** Desactiva un Giro; 422 si esta en uso por alguna Empresa (Req 9). */
  desactivar(id: string): Observable<Giro> {
    return this.http.post<Giro>(this.api.url(`/plataforma/giros/${id}/desactivar`), {});
  }

  /**
   * Elimina un Giro (204 en exito). El backend devuelve 422 si el Giro ya es
   * definitivo (tiene reglas de negocio) o esta en uso por alguna Empresa, y
   * 404 si no existe; el mensaje se propaga para mostrarse tal cual.
   */
  eliminar(id: string): Observable<void> {
    return this.http.delete<void>(this.api.url(`/plataforma/giros/${id}`));
  }
}
