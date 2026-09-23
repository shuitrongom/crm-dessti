// =============================================================================
// Servicio de Plantillas de Mensaje (Req 64.7)
// -----------------------------------------------------------------------------
// Integra PlantillasController (base /api/v1):
//   GET /social/plantillas?canal&page&size
// Las plantillas aprobadas habilitan el envio FUERA de la Ventana_Servicio; la
// bandeja las ofrece en el compositor cuando la ventana esta expirada.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { PlantillaMensaje } from '../models/social.models';

@Injectable({ providedIn: 'root' })
export class PlantillasService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista las Plantillas de Mensaje del tenant, filtrables por canal (Req 64.7). */
  listar(canal: string | null, page = 0, size = 100): Observable<PaginaResponse<PlantillaMensaje>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (canal) {
      params = params.set('canal', canal);
    }
    return this.http.get<PaginaResponse<PlantillaMensaje>>(this.api.url('/social/plantillas'), {
      params,
    });
  }

  /** Consulta una Plantilla_Mensaje por su identificador. */
  consultar(id: string): Observable<PlantillaMensaje> {
    return this.http.get<PlantillaMensaje>(this.api.url(`/social/plantillas/${id}`));
  }
}
