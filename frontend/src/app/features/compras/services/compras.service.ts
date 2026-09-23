// =============================================================================
// Servicio del modulo Compras / abastecimiento (Req 29, 30, 31, 32, 33)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (com.empresa.crm.compras.*), base
// relativa /api/v1:
//   Proveedores       GET/POST /compras/proveedores, GET/PUT/DELETE /{id}
//   Requisiciones     GET/POST /compras/requisiciones, GET /{id},
//                     PUT /{id}/estado, POST /{id}/orden-compra
//   Ordenes de compra GET/POST /compras/ordenes-compra, GET /{id},
//                     PUT /{id}/estado
//   Recepciones       GET/POST /compras/recepciones, GET /{id}
//   Facturas prov.    GET/POST /compras/facturas-proveedor, GET /{id},
//                     POST /{id}/conciliar, POST /{id}/autorizar-pago
//
// Todos los listados usan paginacion del servidor (page/size, PaginaResponse).
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  CambiarEstadoRequest,
  CrearOrdenCompraRequest,
  CrearRequisicionRequest,
  FacturaProveedor,
  GenerarOrdenCompraRequest,
  GuardarProveedorRequest,
  OrdenCompra,
  Proveedor,
  RecepcionMercancia,
  RegistrarFacturaProveedorRequest,
  RegistrarRecepcionRequest,
  RequisicionCompra,
} from '../models/compras.models';

@Injectable({ providedIn: 'root' })
export class ComprasService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  // ---------------------------------------------------------------------------
  // Proveedores (Req 29)
  // ---------------------------------------------------------------------------

  listarProveedores(filtro: string | null, page = 0, size = 20): Observable<PaginaResponse<Proveedor>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro && filtro.trim().length > 0) {
      params = params.set('filtro', filtro.trim());
    }
    return this.http.get<PaginaResponse<Proveedor>>(this.api.url('/compras/proveedores'), { params });
  }

  crearProveedor(request: GuardarProveedorRequest): Observable<Proveedor> {
    return this.http.post<Proveedor>(this.api.url('/compras/proveedores'), request);
  }

  actualizarProveedor(id: string, request: GuardarProveedorRequest): Observable<Proveedor> {
    return this.http.put<Proveedor>(this.api.url(`/compras/proveedores/${id}`), request);
  }

  desactivarProveedor(id: string): Observable<Proveedor> {
    return this.http.delete<Proveedor>(this.api.url(`/compras/proveedores/${id}`));
  }

  // ---------------------------------------------------------------------------
  // Requisiciones de compra (Req 30)
  // ---------------------------------------------------------------------------

  listarRequisiciones(
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<RequisicionCompra>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<RequisicionCompra>>(
      this.api.url('/compras/requisiciones'),
      { params },
    );
  }

  consultarRequisicion(id: string): Observable<RequisicionCompra> {
    return this.http.get<RequisicionCompra>(this.api.url(`/compras/requisiciones/${id}`));
  }

  crearRequisicion(request: CrearRequisicionRequest): Observable<RequisicionCompra> {
    return this.http.post<RequisicionCompra>(this.api.url('/compras/requisiciones'), request);
  }

  cambiarEstadoRequisicion(id: string, estado: string): Observable<RequisicionCompra> {
    const cuerpo: CambiarEstadoRequest = { estado };
    return this.http.put<RequisicionCompra>(
      this.api.url(`/compras/requisiciones/${id}/estado`),
      cuerpo,
    );
  }

  generarOrdenCompra(
    id: string,
    request: GenerarOrdenCompraRequest,
  ): Observable<RequisicionCompra> {
    return this.http.post<RequisicionCompra>(
      this.api.url(`/compras/requisiciones/${id}/orden-compra`),
      request,
    );
  }

  // ---------------------------------------------------------------------------
  // Ordenes de compra (Req 31)
  // ---------------------------------------------------------------------------

  listarOrdenesCompra(
    proveedorId: string | null,
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<OrdenCompra>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (proveedorId) {
      params = params.set('proveedorId', proveedorId);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<OrdenCompra>>(this.api.url('/compras/ordenes-compra'), {
      params,
    });
  }

  consultarOrdenCompra(id: string): Observable<OrdenCompra> {
    return this.http.get<OrdenCompra>(this.api.url(`/compras/ordenes-compra/${id}`));
  }

  crearOrdenCompra(request: CrearOrdenCompraRequest): Observable<OrdenCompra> {
    return this.http.post<OrdenCompra>(this.api.url('/compras/ordenes-compra'), request);
  }

  cambiarEstadoOrdenCompra(id: string, estado: string): Observable<OrdenCompra> {
    const cuerpo: CambiarEstadoRequest = { estado };
    return this.http.put<OrdenCompra>(this.api.url(`/compras/ordenes-compra/${id}/estado`), cuerpo);
  }

  // ---------------------------------------------------------------------------
  // Recepciones de mercancia (Req 32)
  // ---------------------------------------------------------------------------

  listarRecepciones(
    ordenCompraId: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<RecepcionMercancia>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (ordenCompraId) {
      params = params.set('ordenCompraId', ordenCompraId);
    }
    return this.http.get<PaginaResponse<RecepcionMercancia>>(this.api.url('/compras/recepciones'), {
      params,
    });
  }

  consultarRecepcion(id: string): Observable<RecepcionMercancia> {
    return this.http.get<RecepcionMercancia>(this.api.url(`/compras/recepciones/${id}`));
  }

  registrarRecepcion(request: RegistrarRecepcionRequest): Observable<RecepcionMercancia> {
    return this.http.post<RecepcionMercancia>(this.api.url('/compras/recepciones'), request);
  }

  // ---------------------------------------------------------------------------
  // Facturas de proveedor / conciliacion 3 vias (Req 33)
  // ---------------------------------------------------------------------------

  listarFacturasProveedor(
    proveedorId: string | null,
    ordenCompraId: string | null,
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<FacturaProveedor>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (proveedorId) {
      params = params.set('proveedorId', proveedorId);
    }
    if (ordenCompraId) {
      params = params.set('ordenCompraId', ordenCompraId);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<FacturaProveedor>>(
      this.api.url('/compras/facturas-proveedor'),
      { params },
    );
  }

  consultarFacturaProveedor(id: string): Observable<FacturaProveedor> {
    return this.http.get<FacturaProveedor>(this.api.url(`/compras/facturas-proveedor/${id}`));
  }

  registrarFacturaProveedor(
    request: RegistrarFacturaProveedorRequest,
  ): Observable<FacturaProveedor> {
    return this.http.post<FacturaProveedor>(this.api.url('/compras/facturas-proveedor'), request);
  }

  conciliarFactura(id: string): Observable<FacturaProveedor> {
    return this.http.post<FacturaProveedor>(
      this.api.url(`/compras/facturas-proveedor/${id}/conciliar`),
      {},
    );
  }

  autorizarPagoFactura(id: string): Observable<FacturaProveedor> {
    return this.http.post<FacturaProveedor>(
      this.api.url(`/compras/facturas-proveedor/${id}/autorizar-pago`),
      {},
    );
  }
}
