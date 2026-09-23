// =============================================================================
// Servicio del modulo Facturacion CFDI (Req 34, 35, 37)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (com.empresa.crm.facturacion.*), base
// relativa /api/v1:
//   Facturas       GET/POST /facturacion/facturas, GET /{id},
//                  POST /{id}/timbrado, POST /{id}/cancelacion
//   Notas credito  GET/POST /facturacion/notas-credito, GET /{id},
//                  POST /{id}/timbrado
//
// GAP HONESTO (complementos de pago): el backend actual NO expone un controlador
// dedicado de Complementos de Pago (solo se menciona en el package-info como
// alcance del modulo). El complemento de pago se genera al aplicar pagos de
// cliente (Cuentas por cobrar); esta vista lo enlaza desde CxC y no inventa un
// endpoint inexistente. Ver el modulo Contabilidad/CxC (pagos-cliente).
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  CancelarFacturaRequest,
  EmitirFacturaRequest,
  EmitirNotaCreditoRequest,
  Factura,
  NotaCredito,
} from '../models/facturacion.models';

@Injectable({ providedIn: 'root' })
export class FacturacionService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  // --- Facturas CFDI (Req 34, 35) --------------------------------------------

  listarFacturas(
    clienteId: string | null,
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<Factura>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (clienteId) {
      params = params.set('clienteId', clienteId);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<Factura>>(this.api.url('/facturacion/facturas'), { params });
  }

  consultarFactura(id: string): Observable<Factura> {
    return this.http.get<Factura>(this.api.url(`/facturacion/facturas/${id}`));
  }

  emitirFactura(request: EmitirFacturaRequest): Observable<Factura> {
    return this.http.post<Factura>(this.api.url('/facturacion/facturas'), request);
  }

  timbrarFactura(id: string): Observable<Factura> {
    return this.http.post<Factura>(this.api.url(`/facturacion/facturas/${id}/timbrado`), {});
  }

  cancelarFactura(id: string, motivoSat: string): Observable<Factura> {
    const cuerpo: CancelarFacturaRequest = { motivoSat };
    return this.http.post<Factura>(this.api.url(`/facturacion/facturas/${id}/cancelacion`), cuerpo);
  }

  // --- Notas de credito / CFDI de egreso (Req 37) ----------------------------

  listarNotasCredito(
    facturaId: string | null,
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<NotaCredito>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (facturaId) {
      params = params.set('facturaId', facturaId);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<NotaCredito>>(this.api.url('/facturacion/notas-credito'), {
      params,
    });
  }

  emitirNotaCredito(request: EmitirNotaCreditoRequest): Observable<NotaCredito> {
    return this.http.post<NotaCredito>(this.api.url('/facturacion/notas-credito'), request);
  }

  timbrarNotaCredito(id: string): Observable<NotaCredito> {
    return this.http.post<NotaCredito>(this.api.url(`/facturacion/notas-credito/${id}/timbrado`), {});
  }
}
