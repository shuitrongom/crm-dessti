// =============================================================================
// Servicio del Tablero de indicadores por area (Req 22)
// -----------------------------------------------------------------------------
// Integra TableroController (base /api/v1):
//   GET /reportes-bi/tablero?desde&hasta&clienteId
//   GET /reportes-bi/tablero/exportar?desde&hasta&clienteId
// Foto de solo lectura compuesta por el servidor a partir de los puertos de cada
// area (Req 22.2).
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { FiltroTablero, Tablero } from '../models/reportes.models';

@Injectable({ providedIn: 'root' })
export class TableroService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Compone el Tablero de indicadores por area con filtros opcionales (Req 22.1, 22.3). */
  consultar(filtro: FiltroTablero): Observable<Tablero> {
    return this.http.get<Tablero>(this.api.url('/reportes-bi/tablero'), {
      params: this.aParams(filtro),
    });
  }

  /** Exporta el Tablero (Req 22.4). */
  exportar(filtro: FiltroTablero): Observable<Tablero> {
    return this.http.get<Tablero>(this.api.url('/reportes-bi/tablero/exportar'), {
      params: this.aParams(filtro),
    });
  }

  private aParams(filtro: FiltroTablero): HttpParams {
    let params = new HttpParams();
    if (filtro.desde) {
      params = params.set('desde', filtro.desde);
    }
    if (filtro.hasta) {
      params = params.set('hasta', filtro.hasta);
    }
    if (filtro.clienteId) {
      params = params.set('clienteId', filtro.clienteId);
    }
    return params;
  }
}
