// =============================================================================
// Servicio del modulo Calidad / SGC ISO 9001:2026 (Req 70)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (com.dessti.crm.calidad.*), base relativa
// /api/v1:
//   Quejas               GET/POST /calidad/quejas, GET /{id},
//                        PUT /{id}/vinculo, PUT /{id}/atencion
//   Riesgos              GET/POST /calidad/riesgos, GET /{id}, PUT /{id}/estado
//   Oportunidades        GET/POST /calidad/oportunidades, GET /{id}, PUT /{id}/estado
//   No conformidades     GET/POST /calidad/no-conformidades, GET /{id}, PUT /{id}/estado
//   Acciones correctivas GET/POST /calidad/acciones-correctivas, GET /{id},
//                        PUT /{id}/estado, PUT /{id}/verificacion-eficacia, PUT /{id}/cierre
//   Cambios SGC          GET/POST /calidad/cambios-sgc, GET /{id},
//                        PUT /{id}/{aprobacion|rechazo|implementacion}
//   Contexto             GET/POST /calidad/contexto, GET /{id}
//   Indicadores          GET /calidad/indicadores
//   Trazabilidad ISO     GET /calidad/trazabilidad-iso
//
// Los importes/estados los calcula y valida el servidor; la UI solo formatea y
// pinta. Cada endpoint esta gobernado por permiso atomico (deny-by-default).
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  AbrirAccionCorrectivaRequest,
  AccionCorrectiva,
  CambioSgc,
  CambiarEstadoRequest,
  ContextoOrganizacion,
  DeterminarContextoRequest,
  IdentificarOportunidadRequest,
  IdentificarRiesgoRequest,
  IndicadoresCalidad,
  NoConformidad,
  OportunidadCalidad,
  ProponerCambioSgcRequest,
  Queja,
  RegistrarNoConformidadRequest,
  RegistrarQuejaRequest,
  Riesgo,
  TrazabilidadClausula,
  VerificarEficaciaRequest,
} from '../models/calidad.models';

@Injectable({ providedIn: 'root' })
export class CalidadService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  // --- Indicadores y trazabilidad (Req 70.6, 70.7, 70.10) --------------------

  indicadores(): Observable<IndicadoresCalidad> {
    return this.http.get<IndicadoresCalidad>(this.api.url('/calidad/indicadores'));
  }

  trazabilidadIso(): Observable<TrazabilidadClausula[]> {
    return this.http.get<TrazabilidadClausula[]>(this.api.url('/calidad/trazabilidad-iso'));
  }

  // --- Quejas de cliente (Req 70.1) ------------------------------------------

  listarQuejas(
    origen: string | null,
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<Queja>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (origen) {
      params = params.set('origen', origen);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<Queja>>(this.api.url('/calidad/quejas'), { params });
  }

  registrarQueja(request: RegistrarQuejaRequest): Observable<Queja> {
    return this.http.post<Queja>(this.api.url('/calidad/quejas'), request);
  }

  vincularQueja(id: string, accionCorrectivaId: string): Observable<Queja> {
    return this.http.put<Queja>(this.api.url(`/calidad/quejas/${id}/vinculo`), {
      accionCorrectivaId,
    });
  }

  atenderQueja(id: string): Observable<Queja> {
    return this.http.put<Queja>(this.api.url(`/calidad/quejas/${id}/atencion`), {});
  }

  // --- Riesgos (Req 70.3) ----------------------------------------------------

  listarRiesgos(
    estado: string | null,
    nivel: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<Riesgo>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) {
      params = params.set('estado', estado);
    }
    if (nivel) {
      params = params.set('nivel', nivel);
    }
    return this.http.get<PaginaResponse<Riesgo>>(this.api.url('/calidad/riesgos'), { params });
  }

  identificarRiesgo(request: IdentificarRiesgoRequest): Observable<Riesgo> {
    return this.http.post<Riesgo>(this.api.url('/calidad/riesgos'), request);
  }

  cambiarEstadoRiesgo(id: string, estado: string): Observable<Riesgo> {
    const cuerpo: CambiarEstadoRequest = { estado };
    return this.http.put<Riesgo>(this.api.url(`/calidad/riesgos/${id}/estado`), cuerpo);
  }

  // --- Oportunidades de calidad (Req 70.3) -----------------------------------

  listarOportunidades(
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<OportunidadCalidad>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<OportunidadCalidad>>(
      this.api.url('/calidad/oportunidades'),
      { params },
    );
  }

  identificarOportunidad(request: IdentificarOportunidadRequest): Observable<OportunidadCalidad> {
    return this.http.post<OportunidadCalidad>(this.api.url('/calidad/oportunidades'), request);
  }

  cambiarEstadoOportunidad(id: string, estado: string): Observable<OportunidadCalidad> {
    const cuerpo: CambiarEstadoRequest = { estado };
    return this.http.put<OportunidadCalidad>(
      this.api.url(`/calidad/oportunidades/${id}/estado`),
      cuerpo,
    );
  }

  // --- No conformidades (Req 70.2) -------------------------------------------

  listarNoConformidades(
    origen: string | null,
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<NoConformidad>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (origen) {
      params = params.set('origen', origen);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<NoConformidad>>(
      this.api.url('/calidad/no-conformidades'),
      { params },
    );
  }

  registrarNoConformidad(request: RegistrarNoConformidadRequest): Observable<NoConformidad> {
    return this.http.post<NoConformidad>(this.api.url('/calidad/no-conformidades'), request);
  }

  cambiarEstadoNoConformidad(id: string, estado: string): Observable<NoConformidad> {
    const cuerpo: CambiarEstadoRequest = { estado };
    return this.http.put<NoConformidad>(
      this.api.url(`/calidad/no-conformidades/${id}/estado`),
      cuerpo,
    );
  }

  // --- Acciones correctivas (Req 70.2) ---------------------------------------

  listarAccionesCorrectivas(
    estado: string | null,
    noConformidadId: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<AccionCorrectiva>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) {
      params = params.set('estado', estado);
    }
    if (noConformidadId) {
      params = params.set('noConformidadId', noConformidadId);
    }
    return this.http.get<PaginaResponse<AccionCorrectiva>>(
      this.api.url('/calidad/acciones-correctivas'),
      { params },
    );
  }

  abrirAccionCorrectiva(request: AbrirAccionCorrectivaRequest): Observable<AccionCorrectiva> {
    return this.http.post<AccionCorrectiva>(
      this.api.url('/calidad/acciones-correctivas'),
      request,
    );
  }

  avanzarAccionCorrectiva(id: string, estado: string): Observable<AccionCorrectiva> {
    const cuerpo: CambiarEstadoRequest = { estado };
    return this.http.put<AccionCorrectiva>(
      this.api.url(`/calidad/acciones-correctivas/${id}/estado`),
      cuerpo,
    );
  }

  verificarEficacia(id: string, evidencia: string | null): Observable<AccionCorrectiva> {
    const cuerpo: VerificarEficaciaRequest = { evidencia };
    return this.http.put<AccionCorrectiva>(
      this.api.url(`/calidad/acciones-correctivas/${id}/verificacion-eficacia`),
      cuerpo,
    );
  }

  cerrarAccionCorrectiva(id: string): Observable<AccionCorrectiva> {
    return this.http.put<AccionCorrectiva>(
      this.api.url(`/calidad/acciones-correctivas/${id}/cierre`),
      {},
    );
  }

  // --- Cambios del SGC (Req 70.4) --------------------------------------------

  listarCambiosSgc(
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<CambioSgc>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<CambioSgc>>(this.api.url('/calidad/cambios-sgc'), {
      params,
    });
  }

  proponerCambioSgc(request: ProponerCambioSgcRequest): Observable<CambioSgc> {
    return this.http.post<CambioSgc>(this.api.url('/calidad/cambios-sgc'), request);
  }

  aprobarCambioSgc(id: string): Observable<CambioSgc> {
    return this.http.put<CambioSgc>(this.api.url(`/calidad/cambios-sgc/${id}/aprobacion`), {});
  }

  rechazarCambioSgc(id: string): Observable<CambioSgc> {
    return this.http.put<CambioSgc>(this.api.url(`/calidad/cambios-sgc/${id}/rechazo`), {});
  }

  implementarCambioSgc(id: string): Observable<CambioSgc> {
    return this.http.put<CambioSgc>(this.api.url(`/calidad/cambios-sgc/${id}/implementacion`), {});
  }

  // --- Contexto de la organizacion (Req 70.5) --------------------------------

  listarContexto(
    tipo: string | null,
    climaPertinente: boolean | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<ContextoOrganizacion>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (tipo) {
      params = params.set('tipo', tipo);
    }
    if (climaPertinente !== null) {
      params = params.set('climaPertinente', climaPertinente);
    }
    return this.http.get<PaginaResponse<ContextoOrganizacion>>(this.api.url('/calidad/contexto'), {
      params,
    });
  }

  determinarContexto(request: DeterminarContextoRequest): Observable<ContextoOrganizacion> {
    return this.http.post<ContextoOrganizacion>(this.api.url('/calidad/contexto'), request);
  }
}
