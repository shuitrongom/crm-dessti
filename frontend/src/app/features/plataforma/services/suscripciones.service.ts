// =============================================================================
// Servicio de administracion de plataforma: Suscripciones (super_admin)
// -----------------------------------------------------------------------------
// Concentra TODAS las operaciones del contrato REST de suscripciones expuesto por
// el backend (context-path /api/v1). Separa el agregado "Suscripcion" del de
// "Plan" (que vive en PlanesService), evitando mezclar responsabilidades y
// duplicar operaciones (decision D2 del diseno).
//
//   - GET   /suscripciones?tenantId       (suscripcion:listar)
//   - GET   /suscripciones/{id}           (suscripcion:leer)
//   - POST  /suscripciones                (suscripcion:crear)
//   - POST  /suscripciones/{id}/activar   (suscripcion:cambiar_estado)
//   - POST  /suscripciones/{id}/suspender (suscripcion:cambiar_estado)
//   - POST  /suscripciones/{id}/cancelar  (suscripcion:cambiar_estado)
//   - PUT   /suscripciones/{id}/vigencia  (suscripcion:actualizar)
//   Acciones de contrato (rediseno plan-vs-suscripcion-contratacion):
//   - POST  /suscripciones/{id}/activar-facturacion (suscripcion:cambiar_estado)
//   - POST  /suscripciones/{id}/extender-prueba     (suscripcion:actualizar)
//   - POST  /suscripciones/{id}/convertir-a-plan    (suscripcion:crear)
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { Suscripcion } from '../models/plataforma.models';

/**
 * Cuerpo de POST /suscripciones (Req 9.2).
 * Asocia una Empresa (`tenantId`) con un Plan (`planId`); la vigencia es opcional
 * en el alta. Las fechas viajan como cadena ISO `YYYY-MM-DD`.
 */
export interface CrearSuscripcionRequest {
  tenantId: string;
  planId: string;
  vigenciaInicio?: string | null;
  vigenciaFin?: string | null;
}

/**
 * Cuerpo de PUT /suscripciones/{id}/vigencia (Req 9.4).
 * `vigenciaInicio` es OBLIGATORIO (el backend lo marca `@NotNull`); `vigenciaFin`
 * es opcional (`null` = sin fecha de fin). Fechas en formato ISO `YYYY-MM-DD`.
 */
export interface ActualizarVigenciaRequest {
  vigenciaInicio: string;
  vigenciaFin?: string | null;
}

/**
 * Cuerpo OPCIONAL de POST /suscripciones/{id}/activar-facturacion (Req 8.1, 8.4).
 * Activa la facturacion de un Contrato en prueba (`EN_PRUEBA` -> `ACTIVA`). Ambos
 * campos son opcionales: si se omiten, el backend usa los valores por omision
 * (inicio de facturacion = hoy y nuevo fin de vigencia derivado del paquete).
 * Las fechas viajan como cadena ISO `YYYY-MM-DD`.
 */
export interface ActivarFacturacionRequest {
  inicioFacturacion?: string | null;
  nuevaVigenciaFin?: string | null;
}

/**
 * Cuerpo de POST /suscripciones/{id}/extender-prueba (Req 8).
 * `nuevaVigenciaFin` es OBLIGATORIO (el backend lo marca `@NotNull`); el servicio
 * acota la nueva fecha a la duracion del paquete. Fecha en formato ISO
 * `YYYY-MM-DD`.
 */
export interface ExtenderPruebaRequest {
  nuevaVigenciaFin: string;
}

/**
 * Cuerpo de POST /suscripciones/{id}/convertir-a-plan (Req 9.2).
 * Crea un Contrato de Plan nuevo y cierra el anterior. `planId` es OBLIGATORIO
 * (uuid del Plan destino).
 */
export interface ConvertirAPlanRequest {
  planId: string;
}

@Injectable({ providedIn: 'root' })
export class SuscripcionesService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista las Suscripciones de una Empresa (Req 9.1). */
  listarPorEmpresa(tenantId: string): Observable<Suscripcion[]> {
    const params = new HttpParams().set('tenantId', tenantId);
    return this.http.get<Suscripcion[]>(this.api.url('/suscripciones'), { params });
  }

  /** Consulta una Suscripcion por su identificador (Req 9.5). */
  consultar(id: string): Observable<Suscripcion> {
    return this.http.get<Suscripcion>(this.api.url(`/suscripciones/${id}`));
  }

  /** Asocia una Empresa con un Plan creando una Suscripcion (Req 9.2). */
  crear(request: CrearSuscripcionRequest): Observable<Suscripcion> {
    return this.http.post<Suscripcion>(this.api.url('/suscripciones'), request);
  }

  /** Activa una Suscripcion (Req 9.3). */
  activar(id: string): Observable<Suscripcion> {
    return this.http.post<Suscripcion>(this.api.url(`/suscripciones/${id}/activar`), {});
  }

  /** Suspende una Suscripcion (Req 9.3). */
  suspender(id: string): Observable<Suscripcion> {
    return this.http.post<Suscripcion>(this.api.url(`/suscripciones/${id}/suspender`), {});
  }

  /** Cancela una Suscripcion; estado final e irreversible (Req 9.3). */
  cancelar(id: string): Observable<Suscripcion> {
    return this.http.post<Suscripcion>(this.api.url(`/suscripciones/${id}/cancelar`), {});
  }

  /** Actualiza la vigencia (inicio/fin) de una Suscripcion (Req 9.4). */
  actualizarVigencia(id: string, request: ActualizarVigenciaRequest): Observable<Suscripcion> {
    return this.http.put<Suscripcion>(this.api.url(`/suscripciones/${id}/vigencia`), request);
  }

  /**
   * Activa la facturacion de un Contrato en prueba (`EN_PRUEBA` -> `ACTIVA`;
   * Req 8.1, 8.4). El cuerpo es opcional: los campos omitidos se calculan en el
   * backend.
   */
  activarFacturacion(id: string, request: ActivarFacturacionRequest): Observable<Suscripcion> {
    return this.http.post<Suscripcion>(this.api.url(`/suscripciones/${id}/activar-facturacion`), request);
  }

  /** Extiende el periodo de prueba de un Contrato (Req 8). */
  extenderPrueba(id: string, request: ExtenderPruebaRequest): Observable<Suscripcion> {
    return this.http.post<Suscripcion>(this.api.url(`/suscripciones/${id}/extender-prueba`), request);
  }

  /** Convierte un Contrato de Suscripcion a un Contrato de Plan (Req 9.2). */
  convertirAPlan(id: string, request: ConvertirAPlanRequest): Observable<Suscripcion> {
    return this.http.post<Suscripcion>(this.api.url(`/suscripciones/${id}/convertir-a-plan`), request);
  }
}
