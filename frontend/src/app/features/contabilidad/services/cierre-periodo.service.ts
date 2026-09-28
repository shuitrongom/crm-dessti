// =============================================================================
// Servicio del submódulo Cierre de periodo contable (candado contable)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend
// (com.dessti.crm.contabilidad.polizas.adapter.in.rest.PeriodoContableController),
// base relativa /api/v1:
//   Listar   GET  /contabilidad/periodos?anio=
//   Cerrar   POST /contabilidad/periodos/cerrar   { anio, mes }
//   Reabrir  POST /contabilidad/periodos/reabrir  { anio, mes, motivo }
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PeriodoContable } from '../models/cierre-periodo.models';

@Injectable({ providedIn: 'root' })
export class CierrePeriodoService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  private readonly base = '/contabilidad/periodos';

  /** Lista los 12 meses del año con su estado de cierre. */
  listar(anio: number): Observable<PeriodoContable[]> {
    const params = new HttpParams().set('anio', anio);
    return this.http.get<PeriodoContable[]>(this.api.url(this.base), { params });
  }

  /** Cierra un periodo mensual (valida el cuadre de la balanza en el backend). */
  cerrar(anio: number, mes: number): Observable<PeriodoContable> {
    return this.http.post<PeriodoContable>(this.api.url(`${this.base}/cerrar`), { anio, mes });
  }

  /** Reabre un periodo mensual cerrado indicando un motivo obligatorio (auditado). */
  reabrir(anio: number, mes: number, motivo: string): Observable<PeriodoContable> {
    return this.http.post<PeriodoContable>(this.api.url(`${this.base}/reabrir`), {
      anio,
      mes,
      motivo,
    });
  }
}
