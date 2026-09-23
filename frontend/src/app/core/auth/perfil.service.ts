// =============================================================================
// Servicio de perfil del Usuario autenticado (Req 1, 68)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend para la gestion del perfil propio:
//   - GET  /auth/perfil            -> Perfil { id, identificador, roles, tenantId }
//   - PUT  /auth/perfil/password   -> 204 (204 ok; 422 contrasena actual invalida;
//                                     400 validacion de la contrasena nueva)
// Reutiliza el HttpClient (con los interceptores de la aplicacion) y la URL base
// versionada de ApiConfigService.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../services/api-config.service';
import { CambioPasswordRequest, Perfil } from './auth.models';

@Injectable({ providedIn: 'root' })
export class PerfilService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Obtiene el perfil del Usuario autenticado (GET /auth/perfil). */
  obtenerPerfil(): Observable<Perfil> {
    return this.http.get<Perfil>(this.api.url('/auth/perfil'));
  }

  /**
   * Cambia la contrasena propia (PUT /auth/perfil/password). Responde 204 sin
   * cuerpo en caso de exito; el consumidor mapea 422 (contrasena actual
   * incorrecta) y 400 (contrasena nueva invalida) a mensajes en espanol.
   */
  cambiarPassword(body: CambioPasswordRequest): Observable<void> {
    return this.http.put<void>(this.api.url('/auth/perfil/password'), body);
  }
}
