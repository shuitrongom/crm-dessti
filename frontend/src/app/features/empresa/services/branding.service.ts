// =============================================================================
// Servicio de branding de la empresa (Req 26)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend:
//   - GET /empresa/branding  (permiso branding:leer)
//   - PUT /empresa/branding  (permiso branding:actualizar)
// El tenant se deriva del contexto autenticado en el servidor; nunca se envia.
// =============================================================================

import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

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

  /**
   * Branding vigente de la empresa como ESTADO COMPARTIDO reactivo. Es la fuente
   * unica de verdad para las partes de la app que muestran el nombre/logo de la
   * empresa (p. ej. la marca del shell). Se alimenta al consultar y al actualizar,
   * de modo que un cambio en la vista de Branding se refleje de inmediato en el
   * menu lateral sin recargar la pagina. `null` mientras no se ha resuelto.
   */
  private readonly _brandingActual = signal<Branding | null>(null);
  /** Vista de solo lectura del branding vigente compartido. */
  readonly brandingActual = this._brandingActual.asReadonly();

  /** Consulta el branding vigente de la empresa del Usuario (Req 26.2). */
  consultar(): Observable<Branding> {
    return this.http
      .get<Branding>(this.api.url('/empresa/branding'))
      .pipe(tap((b) => this._brandingActual.set(b)));
  }

  /** Actualiza el nombre visible y el logotipo de la empresa (Req 26.1). */
  actualizar(request: ActualizarBrandingRequest): Observable<Branding> {
    return this.http
      .put<Branding>(this.api.url('/empresa/branding'), request)
      .pipe(tap((b) => this._brandingActual.set(b)));
  }
}
