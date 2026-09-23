// =============================================================================
// Servicio del modulo Tesoreria (Req 43)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (com.empresa.crm.tesoreria.*), base
// relativa /api/v1:
//   Cuentas       GET/POST /tesoreria/cuentas-bancarias, GET /{id}
//   Estados       POST /tesoreria/cuentas-bancarias/{id}/estados-cuenta
//   Conciliar     POST /tesoreria/estados-cuenta/{id}/conciliacion
//   Movimientos   GET /tesoreria/movimientos-bancarios (cuentaBancariaId/desde/hasta/estadoConciliacion)
//   Conciliaciones GET /tesoreria/conciliaciones (cuentaBancariaId/estado)
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  ConciliacionBancaria,
  CrearCuentaBancariaRequest,
  CuentaBancaria,
  EstadoCuentaBancario,
  ImportarEstadoCuentaRequest,
  MovimientoBancario,
} from '../models/tesoreria.models';

@Injectable({ providedIn: 'root' })
export class TesoreriaService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  // --- Cuentas bancarias -----------------------------------------------------

  listarCuentas(
    activa: boolean | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<CuentaBancaria>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (activa !== null) {
      params = params.set('activa', activa);
    }
    return this.http.get<PaginaResponse<CuentaBancaria>>(
      this.api.url('/tesoreria/cuentas-bancarias'),
      { params },
    );
  }

  crearCuenta(request: CrearCuentaBancariaRequest): Observable<CuentaBancaria> {
    return this.http.post<CuentaBancaria>(this.api.url('/tesoreria/cuentas-bancarias'), request);
  }

  // --- Estados de cuenta y conciliacion --------------------------------------

  importarEstadoCuenta(
    cuentaId: string,
    request: ImportarEstadoCuentaRequest,
  ): Observable<EstadoCuentaBancario> {
    return this.http.post<EstadoCuentaBancario>(
      this.api.url(`/tesoreria/cuentas-bancarias/${cuentaId}/estados-cuenta`),
      request,
    );
  }

  conciliar(estadoCuentaId: string): Observable<ConciliacionBancaria> {
    return this.http.post<ConciliacionBancaria>(
      this.api.url(`/tesoreria/estados-cuenta/${estadoCuentaId}/conciliacion`),
      {},
    );
  }

  // --- Movimientos y conciliaciones ------------------------------------------

  listarMovimientos(
    cuentaBancariaId: string | null,
    estadoConciliacion: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<MovimientoBancario>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (cuentaBancariaId) {
      params = params.set('cuentaBancariaId', cuentaBancariaId);
    }
    if (estadoConciliacion) {
      params = params.set('estadoConciliacion', estadoConciliacion);
    }
    return this.http.get<PaginaResponse<MovimientoBancario>>(
      this.api.url('/tesoreria/movimientos-bancarios'),
      { params },
    );
  }

  listarConciliaciones(
    cuentaBancariaId: string | null,
    estado: string | null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<ConciliacionBancaria>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (cuentaBancariaId) {
      params = params.set('cuentaBancariaId', cuentaBancariaId);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaResponse<ConciliacionBancaria>>(
      this.api.url('/tesoreria/conciliaciones'),
      { params },
    );
  }
}
