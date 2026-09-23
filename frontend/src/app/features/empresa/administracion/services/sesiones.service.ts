// =============================================================================
// Servicio de gestion de Sesiones (admin_empresa) (Req 68)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (platform.security.sesiones):
//   - GET  /usuarios/{id}/sesiones?page&size   (sesion:listar)
//   - POST /usuarios/{id}/sesiones/revocar      (sesion:cambiar_estado)
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../../core/services/api-config.service';
import { PaginaResponse } from '../../../../core/models/pagina-response';

/** Sesion activa (Token_Refresco) del backend (SesionActivaResponse). */
export interface SesionActiva {
  jti: string;
  usuarioId: string;
  tenantId: string | null;
  emitidoEn: string;
  expiraEn: string;
}

@Injectable({ providedIn: 'root' })
export class SesionesService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista de forma paginada las sesiones activas de una cuenta (Req 68.5). */
  listar(usuarioId: string, page = 0, size = 20): Observable<PaginaResponse<SesionActiva>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PaginaResponse<SesionActiva>>(
      this.api.url(`/usuarios/${usuarioId}/sesiones`),
      { params },
    );
  }

  /** Revoca todas las sesiones vigentes de una cuenta (Req 68.2). */
  revocar(usuarioId: string): Observable<void> {
    return this.http.post<void>(this.api.url(`/usuarios/${usuarioId}/sesiones/revocar`), {});
  }
}
