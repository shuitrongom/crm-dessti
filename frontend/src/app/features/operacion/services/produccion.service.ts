// =============================================================================
// Servicio de Ordenes de Fabricacion (Req 7)
// -----------------------------------------------------------------------------
// Integra el contrato REST operacion-produccion (OrdenFabricacionController):
//   - GET  /ordenes-fabricacion?estado&clienteId&page&size (orden_fabricacion:listar)
//   - GET  /ordenes-fabricacion/{id}                       (orden_fabricacion:leer)
//   - POST /ordenes-fabricacion                            (orden_fabricacion:crear)
//   - POST /ordenes-fabricacion/directa                    (orden_fabricacion:crear)
//   - POST /ordenes-fabricacion/{id}/partidas              (orden_fabricacion:cambiar_estado)
//   - PUT  /ordenes-fabricacion/{id}/estado                (orden_fabricacion:cambiar_estado)
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  CrearOrdenDirectaRequest,
  EstadoOrdenFabricacion,
  OrdenFabricacion,
  OrdenFabricacionDetalle,
  PartidaOrdenFabricacion,
} from '../models/operacion.models';

@Injectable({ providedIn: 'root' })
export class ProduccionService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /**
   * Lista Ordenes de Fabricacion de forma paginada, filtrando por estado y,
   * opcionalmente, por Cliente (Req 7.7, 11.1). El parametro `clienteId` es
   * opcional y se ubica al final para no romper las llamadas existentes.
   */
  listar(
    estado: EstadoOrdenFabricacion | null,
    page: number,
    size: number,
    clienteId?: string | null,
  ): Observable<PaginaResponse<OrdenFabricacion>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) {
      params = params.set('estado', estado);
    }
    if (clienteId) {
      params = params.set('clienteId', clienteId);
    }
    return this.http.get<PaginaResponse<OrdenFabricacion>>(this.api.url('/ordenes-fabricacion'), {
      params,
    });
  }

  /**
   * Consulta el detalle de una Orden de Fabricacion por su identificador,
   * incluyendo sus partidas (Material + cantidad) (Req 5.5).
   */
  consultar(id: string): Observable<OrdenFabricacionDetalle> {
    return this.http.get<OrdenFabricacionDetalle>(this.api.url(`/ordenes-fabricacion/${id}`));
  }

  /** Genera una Orden de Fabricacion a partir de una Cotizacion aprobada (Req 7.1). */
  generar(cotizacionId: string): Observable<OrdenFabricacion> {
    return this.http.post<OrdenFabricacion>(this.api.url('/ordenes-fabricacion'), { cotizacionId });
  }

  /**
   * Crea una Orden de Fabricacion directa (sin Cotizacion) con su Cliente y sus
   * partidas iniciales (Req 1.1-1.7): POST /ordenes-fabricacion/directa.
   */
  crearDirecta(cuerpo: CrearOrdenDirectaRequest): Observable<OrdenFabricacion> {
    return this.http.post<OrdenFabricacion>(this.api.url('/ordenes-fabricacion/directa'), cuerpo);
  }

  /**
   * Reemplaza las partidas de una Orden de Fabricacion (solo mientras la OF este
   * en `pendiente`) (Req 5.1-5.5): POST /ordenes-fabricacion/{id}/partidas.
   */
  reemplazarPartidas(
    id: string,
    partidas: PartidaOrdenFabricacion[],
  ): Observable<OrdenFabricacionDetalle> {
    return this.http.post<OrdenFabricacionDetalle>(
      this.api.url(`/ordenes-fabricacion/${id}/partidas`),
      { partidas },
    );
  }

  /** Cambia el estado de una Orden de Fabricacion segun la maquina de estados (Req 7.5). */
  cambiarEstado(id: string, estado: EstadoOrdenFabricacion): Observable<OrdenFabricacion> {
    return this.http.put<OrdenFabricacion>(this.api.url(`/ordenes-fabricacion/${id}/estado`), {
      estado,
    });
  }
}
