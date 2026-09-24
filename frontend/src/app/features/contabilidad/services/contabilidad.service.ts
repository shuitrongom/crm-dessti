// =============================================================================
// Servicio del modulo Contabilidad / finanzas (Req 36, 38, 39, 42)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (com.empresa.crm.contabilidad.*), base
// relativa /api/v1:
//   CxC       GET /contabilidad/cuentas-por-cobrar (clienteId/estado), GET /{id}
//   Pagos     GET/POST /contabilidad/pagos-cliente, GET /{id}
//   Polizas   GET/POST /contabilidad/polizas (desde/hasta/cuentaContableId),
//             GET /{id}, POST /{id}/reverso
//   Reportes  GET /contabilidad/reportes/ingresos, /iva, /libro-polizas
//   CxP       GET /contabilidad/cuentas-por-pagar (proveedorId/estado), GET /{id},
//             POST /{id}/pagos
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  CuentaContable,
  CuentaPorCobrar,
  CuentaPorPagar,
  IngresosPeriodo,
  IvaPeriodo,
  PagoCliente,
  PolizaContable,
  RegistrarPagoClienteRequest,
  RegistrarPolizaRequest,
} from '../models/contabilidad.models';

@Injectable({ providedIn: 'root' })
export class ContabilidadService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  // --- Cuentas por cobrar (Req 36) -------------------------------------------

  listarCuentasPorCobrar(
    clienteId: string | null,
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<CuentaPorCobrar>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (clienteId) {
      params = params.set('clienteId', clienteId);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<CuentaPorCobrar>>(
      this.api.url('/contabilidad/cuentas-por-cobrar'),
      { params },
    );
  }

  // --- Pagos de cliente (Req 36) ---------------------------------------------

  listarPagos(
    clienteId: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<PagoCliente>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (clienteId) {
      params = params.set('clienteId', clienteId);
    }
    return this.http.get<PaginaResponse<PagoCliente>>(this.api.url('/contabilidad/pagos-cliente'), {
      params,
    });
  }

  registrarPago(request: RegistrarPagoClienteRequest): Observable<PagoCliente> {
    return this.http.post<PagoCliente>(this.api.url('/contabilidad/pagos-cliente'), request);
  }

  // --- Catalogo de cuentas (Req 38.1) ----------------------------------------

  listarCuentasContables(
    activa: boolean | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<CuentaContable>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (activa !== null) {
      params = params.set('activa', activa);
    }
    return this.http.get<PaginaResponse<CuentaContable>>(
      this.api.url('/contabilidad/cuentas-contables'),
      { params },
    );
  }

  // --- Polizas contables (Req 38) --------------------------------------------

  listarPolizas(
    desde: string | null,
    hasta: string | null,
    cuentaContableId: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<PolizaContable>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (desde) {
      params = params.set('desde', desde);
    }
    if (hasta) {
      params = params.set('hasta', hasta);
    }
    if (cuentaContableId) {
      params = params.set('cuentaContableId', cuentaContableId);
    }
    return this.http.get<PaginaResponse<PolizaContable>>(this.api.url('/contabilidad/polizas'), {
      params,
    });
  }

  registrarPoliza(request: RegistrarPolizaRequest): Observable<PolizaContable> {
    return this.http.post<PolizaContable>(this.api.url('/contabilidad/polizas'), request);
  }

  reversarPoliza(id: string, fecha: string | null): Observable<PolizaContable> {
    return this.http.post<PolizaContable>(
      this.api.url(`/contabilidad/polizas/${id}/reverso`),
      fecha ? { fecha } : {},
    );
  }

  // --- Cuentas por pagar (Req 42) --------------------------------------------

  listarCuentasPorPagar(
    proveedorId: string | null,
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<CuentaPorPagar>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (proveedorId) {
      params = params.set('proveedorId', proveedorId);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<CuentaPorPagar>>(
      this.api.url('/contabilidad/cuentas-por-pagar'),
      { params },
    );
  }

  aplicarPagoCxP(id: string, monto: number): Observable<CuentaPorPagar> {
    return this.http.post<CuentaPorPagar>(
      this.api.url(`/contabilidad/cuentas-por-pagar/${id}/pagos`),
      { monto },
    );
  }

  // --- Reportes financieros (Req 39) — solo lectura --------------------------

  reporteIngresos(
    desde: string,
    hasta: string,
    clienteId: string | null,
  ): Observable<IngresosPeriodo> {
    let params = new HttpParams().set('desde', desde).set('hasta', hasta);
    if (clienteId) {
      params = params.set('clienteId', clienteId);
    }
    return this.http.get<IngresosPeriodo>(this.api.url('/contabilidad/reportes/ingresos'), {
      params,
    });
  }

  reporteIva(desde: string, hasta: string, clienteId: string | null): Observable<IvaPeriodo> {
    let params = new HttpParams().set('desde', desde).set('hasta', hasta);
    if (clienteId) {
      params = params.set('clienteId', clienteId);
    }
    return this.http.get<IvaPeriodo>(this.api.url('/contabilidad/reportes/iva'), { params });
  }

  libroPolizas(
    desde: string | null,
    hasta: string | null,
    cuentaContableId: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<PolizaContable>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (desde) {
      params = params.set('desde', desde);
    }
    if (hasta) {
      params = params.set('hasta', hasta);
    }
    if (cuentaContableId) {
      params = params.set('cuentaContableId', cuentaContableId);
    }
    return this.http.get<PaginaResponse<PolizaContable>>(
      this.api.url('/contabilidad/reportes/libro-polizas'),
      { params },
    );
  }
}
