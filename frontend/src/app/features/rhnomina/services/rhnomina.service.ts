// =============================================================================
// Servicio del modulo RH / Nomina y Organizacion (Req 40, 41, 61)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (com.empresa.crm.rhnomina.*), base
// relativa /api/v1:
//   Empleados     GET/POST /rh-nomina/empleados, GET/DELETE /{id},
//                 POST/GET /{id}/incidencias, GET /{id}/contratos
//   Nomina        GET/POST /rh-nomina/nominas, GET /{id},
//                 POST /{id}/{calculo|autorizacion|timbrado|pago},
//                 GET /{id}/recibos
//   Organizacion  GET/POST /rh-nomina/puestos, GET /{id}, PUT /{id}/superior,
//                 POST /rh-nomina/asignaciones-puesto,
//                 GET/POST /rh-nomina/evaluaciones, GET /rh-nomina/organigrama
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  AltaEmpleadoRequest,
  AsignacionPuesto,
  AsignarEmpleadoRequest,
  CalcularNominaRequest,
  ContratoLaboral,
  CrearNominaRequest,
  CrearPuestoRequest,
  Empleado,
  EvaluacionDesempeno,
  Incidencia,
  Nomina,
  OrganigramaNodo,
  Puesto,
  ReciboNomina,
  RegistrarEvaluacionRequest,
  RegistrarIncidenciaRequest,
} from '../models/rhnomina.models';

@Injectable({ providedIn: 'root' })
export class RhNominaService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  // --- Empleados (Req 40) ----------------------------------------------------

  listarEmpleados(
    nombre: string | null,
    activo: boolean | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<Empleado>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (nombre && nombre.trim().length > 0) {
      params = params.set('nombre', nombre.trim());
    }
    if (activo !== null) {
      params = params.set('activo', activo);
    }
    return this.http.get<PaginaResponse<Empleado>>(this.api.url('/rh-nomina/empleados'), { params });
  }

  altaEmpleado(request: AltaEmpleadoRequest): Observable<Empleado> {
    return this.http.post<Empleado>(this.api.url('/rh-nomina/empleados'), request);
  }

  darDeBajaEmpleado(id: string): Observable<Empleado> {
    return this.http.delete<Empleado>(this.api.url(`/rh-nomina/empleados/${id}`));
  }

  registrarIncidencia(
    empleadoId: string,
    request: RegistrarIncidenciaRequest,
  ): Observable<Incidencia> {
    return this.http.post<Incidencia>(
      this.api.url(`/rh-nomina/empleados/${empleadoId}/incidencias`),
      request,
    );
  }

  listarIncidencias(
    empleadoId: string,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<Incidencia>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PaginaResponse<Incidencia>>(
      this.api.url(`/rh-nomina/empleados/${empleadoId}/incidencias`),
      { params },
    );
  }

  listarContratos(
    empleadoId: string,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<ContratoLaboral>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PaginaResponse<ContratoLaboral>>(
      this.api.url(`/rh-nomina/empleados/${empleadoId}/contratos`),
      { params },
    );
  }

  // --- Nomina (Req 41) -------------------------------------------------------

  listarNominas(
    periodo: string | null,
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<Nomina>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (periodo) {
      params = params.set('periodo', periodo);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<Nomina>>(this.api.url('/rh-nomina/nominas'), { params });
  }

  crearNomina(request: CrearNominaRequest): Observable<Nomina> {
    return this.http.post<Nomina>(this.api.url('/rh-nomina/nominas'), request);
  }

  calcularNomina(id: string, request: CalcularNominaRequest): Observable<Nomina> {
    return this.http.post<Nomina>(this.api.url(`/rh-nomina/nominas/${id}/calculo`), request);
  }

  autorizarNomina(id: string): Observable<Nomina> {
    return this.http.post<Nomina>(this.api.url(`/rh-nomina/nominas/${id}/autorizacion`), {});
  }

  timbrarNomina(id: string): Observable<Nomina> {
    return this.http.post<Nomina>(this.api.url(`/rh-nomina/nominas/${id}/timbrado`), {});
  }

  pagarNomina(id: string): Observable<Nomina> {
    return this.http.post<Nomina>(this.api.url(`/rh-nomina/nominas/${id}/pago`), {});
  }

  listarRecibos(nominaId: string, page = 0, size = 20): Observable<PaginaResponse<ReciboNomina>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PaginaResponse<ReciboNomina>>(
      this.api.url(`/rh-nomina/nominas/${nominaId}/recibos`),
      { params },
    );
  }

  // --- Organizacion (Req 61) -------------------------------------------------

  listarPuestos(
    nombre: string | null,
    activo: boolean | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<Puesto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (nombre && nombre.trim().length > 0) {
      params = params.set('nombre', nombre.trim());
    }
    if (activo !== null) {
      params = params.set('activo', activo);
    }
    return this.http.get<PaginaResponse<Puesto>>(this.api.url('/rh-nomina/puestos'), { params });
  }

  crearPuesto(request: CrearPuestoRequest): Observable<Puesto> {
    return this.http.post<Puesto>(this.api.url('/rh-nomina/puestos'), request);
  }

  moverPuesto(id: string, nuevoSuperiorId: string | null): Observable<Puesto> {
    return this.http.put<Puesto>(this.api.url(`/rh-nomina/puestos/${id}/superior`), {
      nuevoSuperiorId,
    });
  }

  asignarEmpleado(request: AsignarEmpleadoRequest): Observable<AsignacionPuesto> {
    return this.http.post<AsignacionPuesto>(this.api.url('/rh-nomina/asignaciones-puesto'), request);
  }

  registrarEvaluacion(request: RegistrarEvaluacionRequest): Observable<EvaluacionDesempeno> {
    return this.http.post<EvaluacionDesempeno>(this.api.url('/rh-nomina/evaluaciones'), request);
  }

  listarEvaluaciones(
    empleadoId: string | null,
    periodo: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<EvaluacionDesempeno>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (empleadoId) {
      params = params.set('empleadoId', empleadoId);
    }
    if (periodo) {
      params = params.set('periodo', periodo);
    }
    return this.http.get<PaginaResponse<EvaluacionDesempeno>>(
      this.api.url('/rh-nomina/evaluaciones'),
      { params },
    );
  }

  consultarOrganigrama(): Observable<OrganigramaNodo[]> {
    return this.http.get<OrganigramaNodo[]>(this.api.url('/rh-nomina/organigrama'));
  }
}
