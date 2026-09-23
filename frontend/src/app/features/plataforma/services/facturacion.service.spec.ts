// =============================================================================
// Pruebas del FacturacionService (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Verifican, sin red real, el armado de URLs y parametros de cada operacion y
// que la descarga del PDF use responseType 'blob' sobre la URL del PDF (para que
// el interceptor adjunte el token de autenticacion).
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { FacturacionService } from './facturacion.service';

describe('FacturacionService', () => {
  let service: FacturacionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        FacturacionService,
      ],
    });
    service = TestBed.inject(FacturacionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listarPorEmpresa consulta GET /empresas/{tenantId}/facturas-renta', () => {
    service.listarPorEmpresa('t1').subscribe();
    const req = http.expectOne('/api/v1/empresas/t1/facturas-renta');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('previsualizar consulta GET /empresas/{tenantId}/renta con el periodo', () => {
    service.previsualizar('t1', '2026-03-01').subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/empresas/t1/renta');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('periodo')).toBe('2026-03-01');
    req.flush({});
  });

  it('emitir hace POST /empresas/{tenantId}/renta con el periodo', () => {
    service.emitir('t1', '2026-03-01').subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/empresas/t1/renta');
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('periodo')).toBe('2026-03-01');
    req.flush({});
  });

  it('pdfUrl arma la URL absoluta del PDF de la factura', () => {
    expect(service.pdfUrl('f1')).toBe('/api/v1/facturas-renta/f1/pdf');
  });

  it('descargarPdf usa GET con responseType blob sobre la URL del PDF', () => {
    service.descargarPdf('f1').subscribe();
    const req = http.expectOne('/api/v1/facturas-renta/f1/pdf');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['%PDF-1.4'], { type: 'application/pdf' }));
  });
});
