// =============================================================================
// Servicio de Campana_Publicitaria (Req 65.7-65.11)
// -----------------------------------------------------------------------------
// Integra CampanasController (base /api/v1):
//   GET  /social/campanas?canal&page&size
//   POST /social/campanas
//   GET  /social/campanas/{id}/estado-externo  (SOLO LECTURA desde Meta)
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  CampanaPublicitaria,
  CrearCampanaPublicitariaRequest,
  EstadoCampanaExterno,
} from '../models/social.models';

@Injectable({ providedIn: 'root' })
export class CampanasService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista las Campana_Publicitaria del tenant, filtrables por canal (Req 65.10). */
  listar(canal: string | null, page = 0, size = 20): Observable<PaginaResponse<CampanaPublicitaria>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (canal) {
      params = params.set('canal', canal);
    }
    return this.http.get<PaginaResponse<CampanaPublicitaria>>(this.api.url('/social/campanas'), {
      params,
    });
  }

  /** Consulta una Campana_Publicitaria por su identificador. */
  consultar(id: string): Observable<CampanaPublicitaria> {
    return this.http.get<CampanaPublicitaria>(this.api.url(`/social/campanas/${id}`));
  }

  /** Crea una Campana_Publicitaria validando presupuesto y periodo (Req 65.7, 65.8). */
  crear(request: CrearCampanaPublicitariaRequest): Observable<CampanaPublicitaria> {
    return this.http.post<CampanaPublicitaria>(this.api.url('/social/campanas'), request);
  }

  /** Consulta el estado externo de SOLO LECTURA de la campana en Meta (Req 65.9). */
  consultarEstadoExterno(id: string): Observable<EstadoCampanaExterno> {
    return this.http.get<EstadoCampanaExterno>(this.api.url(`/social/campanas/${id}/estado-externo`));
  }
}
