// =============================================================================
// Servicio de Inteligencia de Negocio consolidada (Req 48)
// -----------------------------------------------------------------------------
// Integra InteligenciaNegocioController (base /api/v1):
//   GET    /reportes-bi/inteligencia-negocio/consolidado?desde&hasta&area&dimension
//   GET    /reportes-bi/inteligencia-negocio/consolidado/exportar?...
//   GET    /reportes-bi/inteligencia-negocio/tableros-personalizados?page&size
//   POST   /reportes-bi/inteligencia-negocio/tableros-personalizados
//   GET    /reportes-bi/inteligencia-negocio/tableros-personalizados/{id}
//   PUT    /reportes-bi/inteligencia-negocio/tableros-personalizados/{id}
//   DELETE /reportes-bi/inteligencia-negocio/tableros-personalizados/{id}
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  FiltroInteligencia,
  GuardarTableroPersonalizadoRequest,
  InteligenciaNegocio,
  TableroPersonalizado,
} from '../models/reportes.models';

const BASE = '/reportes-bi/inteligencia-negocio';
const TABLEROS = `${BASE}/tableros-personalizados`;

@Injectable({ providedIn: 'root' })
export class InteligenciaNegocioService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Analisis consolidado con comparativos por periodo (Req 48.1, 48.4). */
  consolidado(filtro: FiltroInteligencia): Observable<InteligenciaNegocio> {
    return this.http.get<InteligenciaNegocio>(this.api.url(`${BASE}/consolidado`), {
      params: this.aParams(filtro),
    });
  }

  /** Exportacion del consolidado (Req 48.4, 48.7). */
  exportarConsolidado(filtro: FiltroInteligencia): Observable<InteligenciaNegocio> {
    return this.http.get<InteligenciaNegocio>(this.api.url(`${BASE}/consolidado/exportar`), {
      params: this.aParams(filtro),
    });
  }

  /** Lista los tableros analiticos personalizados (Req 48.3). */
  listarTableros(page = 0, size = 20): Observable<PaginaResponse<TableroPersonalizado>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PaginaResponse<TableroPersonalizado>>(this.api.url(TABLEROS), { params });
  }

  /** Consulta un tablero personalizado (Req 48.3). */
  consultarTablero(id: string): Observable<TableroPersonalizado> {
    return this.http.get<TableroPersonalizado>(this.api.url(`${TABLEROS}/${id}`));
  }

  /** Crea un tablero analitico personalizado (Req 48.3). */
  crearTablero(request: GuardarTableroPersonalizadoRequest): Observable<TableroPersonalizado> {
    return this.http.post<TableroPersonalizado>(this.api.url(TABLEROS), request);
  }

  /** Actualiza un tablero personalizado y reemplaza sus widgets (Req 48.3). */
  actualizarTablero(
    id: string,
    request: GuardarTableroPersonalizadoRequest,
  ): Observable<TableroPersonalizado> {
    return this.http.put<TableroPersonalizado>(this.api.url(`${TABLEROS}/${id}`), request);
  }

  /** Elimina un tablero personalizado (Req 48.3). */
  eliminarTablero(id: string): Observable<void> {
    return this.http.delete<void>(this.api.url(`${TABLEROS}/${id}`));
  }

  private aParams(filtro: FiltroInteligencia): HttpParams {
    let params = new HttpParams();
    if (filtro.desde) {
      params = params.set('desde', filtro.desde);
    }
    if (filtro.hasta) {
      params = params.set('hasta', filtro.hasta);
    }
    if (filtro.area) {
      params = params.set('area', filtro.area);
    }
    if (filtro.dimension) {
      params = params.set('dimension', filtro.dimension);
    }
    return params;
  }
}
