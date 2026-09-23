// =============================================================================
// Servicio de administracion de plataforma: Planes + catalogos (Req 25)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (super_admin) para el agregado "Plan" y
// sus catalogos de apoyo. Las operaciones de suscripcion viven en
// SuscripcionesService (separacion de responsabilidades, decision D2).
//   Planes:
//   - GET   /planes?page&size            (plan:listar)
//   - POST  /planes                       (plan:crear)
//   - PUT   /planes/{id}                  (plan:actualizar)
//   - DELETE /planes/{id}                 (plan:eliminar)
//   Catalogo de modulos y monedas:
//   - GET   /plataforma/modulos           (plan:listar)
//   - GET   /monedas                      (moneda:listar)
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { DependenciasModulos, ModuloCatalogo, Moneda, Plan } from '../models/plataforma.models';

/**
 * Cuerpo de POST /planes y PUT /planes/{id} (Req 25.1, plataforma-multigiro).
 * El Plan pertenece a un Giro, fija una moneda y captura un precio explicito por
 * cada modulo habilitado (`preciosModulos`); las claves NO habilitadas se omiten.
 */
export interface GuardarPlanRequest {
  nombre: string;
  maxUsuarios: number;
  /** Duración del contrato en días; debe ser > 365 para un Plan. */
  duracionDias: number;
  /** Id del Giro (uuid) al que pertenece el Plan. */
  giroId: string;
  /** Codigo ISO 4217 de la moneda del Plan. */
  monedaCodigo: string;
  /** Precio por modulo habilitado: `{ claveModulo: precio }` (puede ir vacio). */
  preciosModulos: Record<string, number>;
}

@Injectable({ providedIn: 'root' })
export class PlanesService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista Planes paginados (Req 25.1). */
  listarPlanes(page = 0, size = 20): Observable<PaginaResponse<Plan>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PaginaResponse<Plan>>(this.api.url('/planes'), { params });
  }

  /** Lista el catalogo de modulos de la plataforma para el selector de modulos. */
  listarModulos(): Observable<ModuloCatalogo[]> {
    return this.http.get<ModuloCatalogo[]>(this.api.url('/plataforma/modulos'));
  }

  /**
   * Lista el mapa de dependencias entre modulos (GET /plataforma/dependencias-modulos,
   * plan:listar). Devuelve `{ claveDependiente: [clavesRequeridas] }`; el super_admin
   * lo usa para derivar avisos y el bloqueo de deseleccion sin hardcode.
   */
  listarDependenciasModulos(): Observable<DependenciasModulos> {
    return this.http.get<DependenciasModulos>(this.api.url('/plataforma/dependencias-modulos'));
  }

  /** Lista las monedas del catalogo de la plataforma (GET /monedas, moneda:listar). */
  listarMonedas(): Observable<Moneda[]> {
    return this.http.get<Moneda[]>(this.api.url('/monedas'));
  }

  /** Define un Plan (Req 25.1). */
  crearPlan(request: GuardarPlanRequest): Observable<Plan> {
    return this.http.post<Plan>(this.api.url('/planes'), request);
  }

  /** Actualiza los limites de un Plan (Req 25.1). */
  actualizarPlan(id: string, request: GuardarPlanRequest): Observable<Plan> {
    return this.http.put<Plan>(this.api.url(`/planes/${id}`), request);
  }

  /**
   * Elimina un Plan (204 en exito). El backend devuelve 422 si alguna Empresa
   * tiene el Plan asignado ("Primero cambia el plan de esas empresas.") y 404 si
   * no existe; el mensaje se propaga para mostrarse tal cual.
   */
  eliminarPlan(id: string): Observable<void> {
    return this.http.delete<void>(this.api.url(`/planes/${id}`));
  }
}
