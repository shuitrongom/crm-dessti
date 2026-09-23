// =============================================================================
// Servicios de instalacion: Levantamientos, Permisos y OTIs (Req 16, 17, 19)
// -----------------------------------------------------------------------------
// Integran los contratos REST operacion-produccion (LevantamientoSitioController,
// PermisoInstalacionController, OrdenTrabajoInstalacionController).
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  EstadoLevantamiento,
  EstadoOti,
  EstadoPermiso,
  LevantamientoFoto,
  LevantamientoRequest,
  LevantamientoSitio,
  LevantamientoSitioDetalle,
  OrdenTrabajoInstalacion,
  OrdenTrabajoInstalacionDetalle,
  PermisoInstalacion,
  PermisoRequest,
  ProgramarOtiRequest,
  RegistrarAvanceRequest,
  TipoPermiso,
} from '../models/operacion.models';

@Injectable({ providedIn: 'root' })
export class LevantamientosService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista Levantamientos de forma paginada, filtrando por estado (Req 16.6). */
  listar(
    estado: EstadoLevantamiento | null,
    page: number,
    size: number,
  ): Observable<PaginaResponse<LevantamientoSitio>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<LevantamientoSitio>>(this.api.url('/levantamientos'), {
      params,
    });
  }

  /**
   * Consulta un Levantamiento por su identificador (Req 16). Devuelve el DTO de
   * detalle, que incluye las fotos vinculadas (lista vacia si no hay).
   */
  consultar(id: string): Observable<LevantamientoSitioDetalle> {
    return this.http.get<LevantamientoSitioDetalle>(this.api.url(`/levantamientos/${id}`));
  }

  /** Crea un Levantamiento con datos obligatorios y vinculos opcionales (Req 16.1). */
  crear(request: LevantamientoRequest): Observable<LevantamientoSitio> {
    return this.http.post<LevantamientoSitio>(this.api.url('/levantamientos'), request);
  }

  /** Marca un Levantamiento como completado (Req 16.4). */
  completar(id: string): Observable<LevantamientoSitio> {
    return this.http.post<LevantamientoSitio>(this.api.url(`/levantamientos/${id}/completar`), {});
  }

  /**
   * Adjunta una o mas fotografias a un Levantamiento (Req 12.1). Envia las
   * referencias (URL o clave de objeto) a POST /levantamientos/{id}/fotos y
   * devuelve la lista de fotos resultante.
   */
  agregarFotos(id: string, referencias: string[]): Observable<LevantamientoFoto[]> {
    return this.http.post<LevantamientoFoto[]>(this.api.url(`/levantamientos/${id}/fotos`), {
      referencias,
    });
  }

  /**
   * Lista las fotografias vinculadas a un Levantamiento (Req 12.1). Util para
   * recargar la galeria tras adjuntar; devuelve una lista vacia si no hay fotos.
   */
  fotosDe(id: string): Observable<LevantamientoFoto[]> {
    return this.http.get<LevantamientoFoto[]>(this.api.url(`/levantamientos/${id}/fotos`));
  }
}

@Injectable({ providedIn: 'root' })
export class PermisosService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista Permisos de forma paginada, filtrando por tipo y estado (Req 17.6). */
  listar(
    tipo: TipoPermiso | null,
    estado: EstadoPermiso | null,
    page: number,
    size: number,
  ): Observable<PaginaResponse<PermisoInstalacion>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (tipo) {
      params = params.set('tipo', tipo);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<PermisoInstalacion>>(
      this.api.url('/permisos-instalacion'),
      { params },
    );
  }

  /** Consulta un Permiso por su identificador (Req 17). */
  consultar(id: string): Observable<PermisoInstalacion> {
    return this.http.get<PermisoInstalacion>(this.api.url(`/permisos-instalacion/${id}`));
  }

  /** Crea un Permiso de Instalacion en estado solicitado (Req 17.1). */
  crear(request: PermisoRequest): Observable<PermisoInstalacion> {
    return this.http.post<PermisoInstalacion>(this.api.url('/permisos-instalacion'), request);
  }

  /** Cambia el estado de un Permiso mediante la accion aprobar/rechazar (Req 17.2). */
  cambiarEstado(id: string, accion: 'aprobar' | 'rechazar'): Observable<PermisoInstalacion> {
    const params = new HttpParams().set('accion', accion);
    return this.http.put<PermisoInstalacion>(
      this.api.url(`/permisos-instalacion/${id}/estado`),
      {},
      { params },
    );
  }
}

/** Filtros opcionales del listado de OTIs. */
export interface FiltroOti {
  estado?: EstadoOti | null;
  cuadrillaId?: string | null;
  clienteId?: string | null;
}

@Injectable({ providedIn: 'root' })
export class OtisService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista OTIs de forma paginada con filtros opcionales (Req 19.7). */
  listar(
    filtro: FiltroOti,
    page: number,
    size: number,
  ): Observable<PaginaResponse<OrdenTrabajoInstalacion>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.estado) {
      params = params.set('estado', filtro.estado);
    }
    if (filtro.cuadrillaId) {
      params = params.set('cuadrillaId', filtro.cuadrillaId);
    }
    if (filtro.clienteId) {
      params = params.set('clienteId', filtro.clienteId);
    }
    return this.http.get<PaginaResponse<OrdenTrabajoInstalacion>>(
      this.api.url('/ordenes-trabajo-instalacion'),
      { params },
    );
  }

  /** Consulta una OTI por su identificador (Req 19). */
  consultar(id: string): Observable<OrdenTrabajoInstalacion> {
    return this.http.get<OrdenTrabajoInstalacion>(
      this.api.url(`/ordenes-trabajo-instalacion/${id}`),
    );
  }

  /**
   * Consulta el detalle de una OTI (Req 8.1, 8.2, 8.3). GET
   * /ordenes-trabajo-instalacion/{id} devuelve el DTO enriquecido con la lista de
   * pendientes (cada uno con `resuelto`) y la lista de evidencias (listas vacias
   * cuando no haya).
   */
  consultarDetalle(id: string): Observable<OrdenTrabajoInstalacionDetalle> {
    return this.http.get<OrdenTrabajoInstalacionDetalle>(
      this.api.url(`/ordenes-trabajo-instalacion/${id}`),
    );
  }

  /** Programa una OTI a partir de una Orden de Fabricacion terminada (Req 19.1). */
  programar(request: ProgramarOtiRequest): Observable<OrdenTrabajoInstalacion> {
    return this.http.post<OrdenTrabajoInstalacion>(
      this.api.url('/ordenes-trabajo-instalacion'),
      request,
    );
  }

  /** Registra avance (pendientes, evidencias, resoluciones) de una OTI (Req 19.4). */
  registrarAvance(
    id: string,
    request: RegistrarAvanceRequest,
  ): Observable<OrdenTrabajoInstalacion> {
    return this.http.post<OrdenTrabajoInstalacion>(
      this.api.url(`/ordenes-trabajo-instalacion/${id}/avance`),
      request,
    );
  }

  /** Cambia el estado de una OTI segun la maquina de estados (Req 19.5). */
  cambiarEstado(id: string, estado: EstadoOti): Observable<OrdenTrabajoInstalacion> {
    return this.http.put<OrdenTrabajoInstalacion>(
      this.api.url(`/ordenes-trabajo-instalacion/${id}/estado`),
      { estado },
    );
  }
}
