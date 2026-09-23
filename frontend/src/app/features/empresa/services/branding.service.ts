// =============================================================================
// Servicio de branding de la empresa (Req 26)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend:
//   - GET /empresa/branding  (permiso branding:leer)
//   - PUT /empresa/branding  (permiso branding:actualizar)
// El tenant se deriva del contexto autenticado en el servidor; nunca se envia.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { Branding } from '../home/home.models';

/**
 * Cuerpo de PUT /empresa/branding. Req 6.2, 4.1.
 * `colorPrimario` es el color primario de marca en formato `#RRGGBB` o `null`
 * para limpiarlo; el backend valida el patron y preserva nombre/logo si aplica.
 */
export interface ActualizarBrandingRequest {
  nombreVisible: string | null;
  logo: string | null;
  colorPrimario: string | null;
}

@Injectable({ providedIn: 'root' })
export class BrandingService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Consulta el branding vigente de la empresa del Usuario (Req 26.2). */
  consultar(): Observable<Branding> {
    return this.http.get<Branding>(this.api.url('/empresa/branding'));
  }

  /** Actualiza el nombre visible y el logotipo de la empresa (Req 26.1). */
  actualizar(request: ActualizarBrandingRequest): Observable<Branding> {
    return this.http.put<Branding>(this.api.url('/empresa/branding'), request);
  }
}
