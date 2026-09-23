// =============================================================================
// Servicio del modulo Presupuestos (vistas de negocio) (Req 62)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (com.empresa.crm.presupuestos.*), base
// relativa /api/v1:
//   GET/POST /presupuestos, GET/PUT /{id}, GET /{id}/variacion
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  ActualizarPresupuestoRequest,
  CrearPresupuestoRequest,
  Presupuesto,
  VariacionPresupuesto,
} from '../models/presupuestos.models';

@Injectable({ providedIn: 'root' })
export class PresupuestosVistasService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  listar(
    area: string | null,
    periodo: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<Presupuesto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (area) {
      params = params.set('area', area);
    }
    if (periodo) {
      params = params.set('periodo', periodo);
    }
    return this.http.get<PaginaResponse<Presupuesto>>(this.api.url('/presupuestos'), { params });
  }

  crear(request: CrearPresupuestoRequest): Observable<Presupuesto> {
    return this.http.post<Presupuesto>(this.api.url('/presupuestos'), request);
  }

  actualizar(id: string, request: ActualizarPresupuestoRequest): Observable<Presupuesto> {
    return this.http.put<Presupuesto>(this.api.url(`/presupuestos/${id}`), request);
  }

  consultarVariacion(id: string): Observable<VariacionPresupuesto> {
    return this.http.get<VariacionPresupuesto>(this.api.url(`/presupuestos/${id}/variacion`));
  }
}
