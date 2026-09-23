// =============================================================================
// Servicio de Cuentas de Canal_Social (Req 64.1, 64.2)
// -----------------------------------------------------------------------------
// Integra CuentasCanalSocialController (base /api/v1):
//   GET /social/cuentas-canal?canal&page&size
// La UI de publicaciones lo usa para ofrecer la cuenta emisora en el alta. El
// DTO nunca expone credenciales, solo su referencia (Req 11).
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { CrearCuentaCanalSocialRequest, CuentaCanalSocial } from '../models/social.models';

@Injectable({ providedIn: 'root' })
export class CuentasCanalService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista las Cuentas de Canal_Social del tenant, filtrables por canal. */
  listar(canal: string | null, page = 0, size = 100): Observable<PaginaResponse<CuentaCanalSocial>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (canal) {
      params = params.set('canal', canal);
    }
    return this.http.get<PaginaResponse<CuentaCanalSocial>>(this.api.url('/social/cuentas-canal'), {
      params,
    });
  }

  /**
   * Registra una nueva Cuenta de Canal_Social (conexion). El cuerpo lleva solo la
   * referencia de credencial, nunca la credencial en claro. Responde 409 si ya
   * existe una cuenta para ese canal e identificador; 422 con datos invalidos.
   */
  crear(request: CrearCuentaCanalSocialRequest): Observable<CuentaCanalSocial> {
    return this.http.post<CuentaCanalSocial>(this.api.url('/social/cuentas-canal'), request);
  }
}
