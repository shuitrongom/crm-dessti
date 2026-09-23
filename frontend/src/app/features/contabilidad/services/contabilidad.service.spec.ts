// =============================================================================
// Pruebas unitarias del ContabilidadService (Req 36, 38, 42) — mapeo HTTP
// -----------------------------------------------------------------------------
// Verifican, a traves del pipeline real de HttpClient con backend simulado, que:
//   - El listado de CxC construye la URL y los parametros de paginacion/filtro.
//   - El registro de pago envia el cuerpo esperado a /contabilidad/pagos-cliente.
//   - La aplicacion de pago de CxP hace POST al endpoint /{id}/pagos con el monto.
// Deterministas, sin zona, con provideHttpClientTesting.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { ContabilidadService } from './contabilidad.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { CuentaPorCobrar, CuentaPorPagar, PagoCliente } from '../models/contabilidad.models';

const BASE = '/api/v1';

describe('ContabilidadService', () => {
  let service: ContabilidadService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ContabilidadService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ContabilidadService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista CxC con paginacion y filtro por estado', () => {
    const pagina: PaginaResponse<CuentaPorCobrar> = {
      content: [],
      page: 1,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    };
    service.listarCuentasPorCobrar(null, 'pendiente', 1, 50).subscribe((r) => {
      expect(r.totalElements).toBe(0);
    });
    const req = http.expectOne(
      (r) => r.url === `${BASE}/contabilidad/cuentas-por-cobrar`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.get('estado')).toBe('pendiente');
    expect(req.request.params.get('clienteId')).toBeNull();
    req.flush(pagina);
  });

  it('registra un pago de cliente enviando el cuerpo esperado', () => {
    const pago = { id: 'p1' } as PagoCliente;
    service
      .registrarPago({
        clienteId: 'c1',
        monto: 100.5,
        formaPago: '03',
        esParcialidad: false,
        aplicaciones: [{ facturaId: 'f1', monto: 100.5 }],
      })
      .subscribe((r) => expect(r.id).toBe('p1'));
    const req = http.expectOne(`${BASE}/contabilidad/pagos-cliente`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.clienteId).toBe('c1');
    expect(req.request.body.monto).toBe(100.5);
    expect(req.request.body.aplicaciones).toEqual([{ facturaId: 'f1', monto: 100.5 }]);
    req.flush(pago);
  });

  it('aplica un pago a una CxP con POST al endpoint de pagos', () => {
    const cxp = { id: 'x1', saldo: 0 } as CuentaPorPagar;
    service.aplicarPagoCxP('x1', 250).subscribe((r) => expect(r.id).toBe('x1'));
    const req = http.expectOne(`${BASE}/contabilidad/cuentas-por-pagar/x1/pagos`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ monto: 250 });
    req.flush(cxp);
  });
});
