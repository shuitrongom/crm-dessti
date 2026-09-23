// =============================================================================
// Servicio del modulo Estrategia (vistas de negocio) (Req 58)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (com.empresa.crm.estrategia.*), base
// relativa /api/v1:
//   GET/PUT /estrategia/esencia
//   GET/POST /estrategia/objetivos, GET /objetivos/{id},
//   PUT /objetivos/{id}/avance, POST /objetivos/{id}/resultados-clave,
//   PUT /resultados-clave/{id}/valor
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  AgregarResultadoClaveRequest,
  CrearObjetivoRequest,
  EsenciaEmpresa,
  GuardarEsenciaRequest,
  ObjetivoEstrategico,
} from '../models/estrategia.models';

@Injectable({ providedIn: 'root' })
export class EstrategiaVistasService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  consultarEsencia(): Observable<EsenciaEmpresa> {
    return this.http.get<EsenciaEmpresa>(this.api.url('/estrategia/esencia'));
  }

  guardarEsencia(request: GuardarEsenciaRequest): Observable<EsenciaEmpresa> {
    return this.http.put<EsenciaEmpresa>(this.api.url('/estrategia/esencia'), request);
  }

  listarObjetivos(
    responsable: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<ObjetivoEstrategico>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (responsable) {
      params = params.set('responsable', responsable);
    }
    return this.http.get<PaginaResponse<ObjetivoEstrategico>>(
      this.api.url('/estrategia/objetivos'),
      { params },
    );
  }

  consultarObjetivo(id: string): Observable<ObjetivoEstrategico> {
    return this.http.get<ObjetivoEstrategico>(this.api.url(`/estrategia/objetivos/${id}`));
  }

  crearObjetivo(request: CrearObjetivoRequest): Observable<ObjetivoEstrategico> {
    return this.http.post<ObjetivoEstrategico>(this.api.url('/estrategia/objetivos'), request);
  }

  actualizarAvance(id: string, avance: number): Observable<ObjetivoEstrategico> {
    return this.http.put<ObjetivoEstrategico>(this.api.url(`/estrategia/objetivos/${id}/avance`), {
      avance,
    });
  }

  agregarResultadoClave(
    id: string,
    request: AgregarResultadoClaveRequest,
  ): Observable<ObjetivoEstrategico> {
    return this.http.post<ObjetivoEstrategico>(
      this.api.url(`/estrategia/objetivos/${id}/resultados-clave`),
      request,
    );
  }

  actualizarValorResultadoClave(id: string, valorActual: number): Observable<ObjetivoEstrategico> {
    return this.http.put<ObjetivoEstrategico>(
      this.api.url(`/estrategia/resultados-clave/${id}/valor`),
      { valorActual },
    );
  }
}
