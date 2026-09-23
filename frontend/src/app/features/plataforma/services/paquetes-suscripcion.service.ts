// =============================================================================
// Servicio de administracion de plataforma: Paquetes de Suscripcion (Req 3, 10)
// -----------------------------------------------------------------------------
// Espejo de PlanesService para el catalogo de "contratos de corto plazo"
// (duracion de un ano o menos) con opcion de periodo de prueba. Un Paquete
// pertenece a un Giro, fija una moneda y captura un precio explicito por cada
// modulo habilitado; las operaciones de suscripcion viven en SuscripcionesService
// (separacion de responsabilidades, decision D2).
//   - GET    /paquetes-suscripcion?page&size   (suscripcion:listar)
//   - GET    /paquetes-suscripcion/{id}         (suscripcion:leer)
//   - POST   /paquetes-suscripcion              (suscripcion:crear)
//   - PUT    /paquetes-suscripcion/{id}         (suscripcion:actualizar)
//   - DELETE /paquetes-suscripcion/{id}         (suscripcion:cambiar_estado)
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { PaqueteSuscripcion } from '../models/plataforma.models';

/**
 * Cuerpo de POST /paquetes-suscripcion y PUT /paquetes-suscripcion/{id}
 * (Req 3.1, 10.1, 10.4). Espejo de GuardarPlanRequest, con los atributos propios
 * del Paquete: la duracion del contrato y el periodo de prueba. El Paquete
 * pertenece a un Giro, fija una moneda y captura un precio explicito por cada
 * modulo habilitado (`preciosModulos`); las claves NO habilitadas se omiten.
 */
export interface GuardarPaqueteSuscripcionRequest {
  nombre: string;
  maxUsuarios: number;
  /** Duracion del contrato en dias; `1..365` (un ano o menos). */
  duracionDias: number;
  /** Indica si el Paquete admite periodo de prueba. */
  admitePrueba: boolean;
  /** Duracion del periodo de prueba en meses; `null` cuando no admite prueba. */
  duracionPruebaMeses?: number | null;
  /** Id del Giro (uuid) al que pertenece el Paquete. */
  giroId: string;
  /** Codigo ISO 4217 de la moneda del Paquete. */
  monedaCodigo: string;
  /** Precio por modulo habilitado: `{ claveModulo: precio }` (puede ir vacio). */
  preciosModulos: Record<string, number>;
}

@Injectable({ providedIn: 'root' })
export class PaquetesSuscripcionService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista Paquetes de Suscripcion paginados (Req 3.1, 10.3). */
  listarPaquetes(page = 0, size = 20): Observable<PaginaResponse<PaqueteSuscripcion>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PaginaResponse<PaqueteSuscripcion>>(this.api.url('/paquetes-suscripcion'), { params });
  }

  /** Consulta un Paquete de Suscripcion por su identificador (Req 3.1, 10.3). */
  consultarPaquete(id: string): Observable<PaqueteSuscripcion> {
    return this.http.get<PaqueteSuscripcion>(this.api.url(`/paquetes-suscripcion/${id}`));
  }

  /** Define un Paquete de Suscripcion (Req 3.1, 10.1). */
  crearPaquete(request: GuardarPaqueteSuscripcionRequest): Observable<PaqueteSuscripcion> {
    return this.http.post<PaqueteSuscripcion>(this.api.url('/paquetes-suscripcion'), request);
  }

  /** Actualiza un Paquete de Suscripcion (Req 3.1, 10.4). */
  actualizarPaquete(id: string, request: GuardarPaqueteSuscripcionRequest): Observable<PaqueteSuscripcion> {
    return this.http.put<PaqueteSuscripcion>(this.api.url(`/paquetes-suscripcion/${id}`), request);
  }

  /**
   * Elimina un Paquete de Suscripcion (204 en exito). El backend devuelve 422 si
   * algun Contrato (Suscripcion) lo referencia y 404 si no existe; el mensaje se
   * propaga para mostrarse tal cual (Req 10.5).
   */
  eliminarPaquete(id: string): Observable<void> {
    return this.http.delete<void>(this.api.url(`/paquetes-suscripcion/${id}`));
  }
}
