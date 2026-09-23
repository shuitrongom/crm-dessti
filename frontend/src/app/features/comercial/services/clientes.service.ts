// =============================================================================
// Servicio de Clientes y Contactos (Req 5)
// -----------------------------------------------------------------------------
// Integra el contrato REST comercial-crm (ClienteController):
//   - GET    /clientes?filtro&page&size      (cliente:listar)
//   - GET    /clientes/{id}                  (cliente:leer)
//   - POST   /clientes                       (cliente:crear)
//   - PUT    /clientes/{id}                  (cliente:actualizar)
//   - DELETE /clientes/{id}                  (cliente:eliminar) baja logica
//   - POST   /clientes/{id}/contactos        (contacto:crear)
//
// NOTA DE INTEGRACION (unica y explicita): el backend NO expone un LISTADO de
// Contactos por Cliente (solo el alta POST /clientes/{id}/contactos). La vista de
// detalle permite asociar Contactos y muestra el resultado de cada alta; el
// listado de Contactos se conectara aqui cuando el backend publique el endpoint.
// No se simula un listado inexistente.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { Cliente, ClienteRequest, Contacto, ContactoRequest } from '../models/comercial.models';

@Injectable({ providedIn: 'root' })
export class ClientesService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista Clientes activos de forma paginada, filtrando por nombre o RFC (Req 5.7). */
  listar(filtro: string | null, page: number, size: number): Observable<PaginaResponse<Cliente>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro && filtro.trim().length > 0) {
      params = params.set('filtro', filtro.trim());
    }
    return this.http.get<PaginaResponse<Cliente>>(this.api.url('/clientes'), { params });
  }

  /** Consulta un Cliente por su identificador (Req 5). */
  consultar(id: string): Observable<Cliente> {
    return this.http.get<Cliente>(this.api.url(`/clientes/${id}`));
  }

  /** Da de alta un Cliente (Req 5.1). */
  crear(request: ClienteRequest): Observable<Cliente> {
    return this.http.post<Cliente>(this.api.url('/clientes'), request);
  }

  /** Actualiza un Cliente activo (Req 5.4). */
  actualizar(id: string, request: ClienteRequest): Observable<Cliente> {
    return this.http.put<Cliente>(this.api.url(`/clientes/${id}`), request);
  }

  /** Baja logica de un Cliente (Req 5.9); devuelve el Cliente desactivado. */
  eliminar(id: string): Observable<Cliente> {
    return this.http.delete<Cliente>(this.api.url(`/clientes/${id}`));
  }

  /** Asocia un Contacto a un Cliente activo (Req 5.5). */
  asociarContacto(clienteId: string, request: ContactoRequest): Observable<Contacto> {
    return this.http.post<Contacto>(this.api.url(`/clientes/${clienteId}/contactos`), request);
  }
}
