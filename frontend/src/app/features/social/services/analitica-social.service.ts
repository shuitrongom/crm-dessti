// =============================================================================
// Servicio de la analitica social de solo lectura (Req 66)
// -----------------------------------------------------------------------------
// Integra AnaliticaSocialController (base /api/v1):
//   GET /social/analitica/metricas?desde&hasta&canal&canalVentaId
//   GET /social/analitica/metricas/exportar?desde&hasta&canal&canalVentaId
// Agregacion de solo lectura; no modifica datos de origen (Req 66.1).
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { ResumenAnaliticaSocial } from '../models/social.models';

/** Filtros de la analitica social (Req 66.4). */
export interface FiltroAnalitica {
  /** Fecha ISO (yyyy-MM-dd) inicio del periodo; opcional. */
  desde?: string | null;
  /** Fecha ISO (yyyy-MM-dd) fin del periodo; opcional. */
  hasta?: string | null;
  /** Etiqueta del Canal_Social; opcional. */
  canal?: string | null;
  /** Canal_Venta al que segmentar (Req 66.2); opcional. */
  canalVentaId?: string | null;
}

@Injectable({ providedIn: 'root' })
export class AnaliticaSocialService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Metricas sociales por Canal_Social y periodo (Req 66.1, 66.4). */
  metricas(filtro: FiltroAnalitica): Observable<ResumenAnaliticaSocial> {
    return this.http.get<ResumenAnaliticaSocial>(this.api.url('/social/analitica/metricas'), {
      params: this.aParams(filtro),
    });
  }

  /** Exportacion del mismo resumen de metricas (Req 66.4). */
  exportar(filtro: FiltroAnalitica): Observable<ResumenAnaliticaSocial> {
    return this.http.get<ResumenAnaliticaSocial>(
      this.api.url('/social/analitica/metricas/exportar'),
      { params: this.aParams(filtro) },
    );
  }

  private aParams(filtro: FiltroAnalitica): HttpParams {
    let params = new HttpParams();
    if (filtro.desde) {
      params = params.set('desde', filtro.desde);
    }
    if (filtro.hasta) {
      params = params.set('hasta', filtro.hasta);
    }
    if (filtro.canal) {
      params = params.set('canal', filtro.canal);
    }
    if (filtro.canalVentaId) {
      params = params.set('canalVentaId', filtro.canalVentaId);
    }
    return params;
  }
}
