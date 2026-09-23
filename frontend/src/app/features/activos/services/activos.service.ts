// =============================================================================
// Servicio del modulo Activos fijos (Req 44)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (com.empresa.crm.activosfijos.*), base
// relativa /api/v1:
//   GET/POST /activos-fijos, GET /{id},
//   POST /activos-fijos/{id}/depreciacion (corre la depreciacion de un periodo),
//   POST /activos-fijos/{id}/baja
//
// NOTA: el backend calcula la depreciacion por periodo (POST). No existe un
// endpoint de listado historico de depreciaciones; la UI muestra la acumulada del
// Activo_Fijo (campo del DTO) y el resultado de cada corrida.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { ActivoFijo, CrearActivoFijoRequest, Depreciacion } from '../models/activos.models';

@Injectable({ providedIn: 'root' })
export class ActivosService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  listar(estado: string | null, page = 0, size = 20): Observable<PaginaResponse<ActivoFijo>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<ActivoFijo>>(this.api.url('/activos-fijos'), { params });
  }

  consultar(id: string): Observable<ActivoFijo> {
    return this.http.get<ActivoFijo>(this.api.url(`/activos-fijos/${id}`));
  }

  crear(request: CrearActivoFijoRequest): Observable<ActivoFijo> {
    return this.http.post<ActivoFijo>(this.api.url('/activos-fijos'), request);
  }

  /** Corre la depreciacion del periodo mensual 'AAAA-MM' indicado. */
  depreciar(id: string, periodo: string): Observable<Depreciacion> {
    return this.http.post<Depreciacion>(this.api.url(`/activos-fijos/${id}/depreciacion`), {
      periodo,
    });
  }

  darDeBaja(id: string): Observable<ActivoFijo> {
    return this.http.post<ActivoFijo>(this.api.url(`/activos-fijos/${id}/baja`), {});
  }
}
