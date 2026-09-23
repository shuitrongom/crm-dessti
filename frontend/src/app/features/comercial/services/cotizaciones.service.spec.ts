// =============================================================================
// Pruebas unitarias del CotizacionesService (Req 6)
// -----------------------------------------------------------------------------
// Verifican de forma determinista y sin zona el mapeo HTTP del servicio contra
// el contrato REST del backend usando provideHttpClientTesting: URL, verbo,
// parametros de paginacion/filtro y cuerpo. No arrancan la aplicacion completa.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { CotizacionesService } from './cotizaciones.service';
import { Cotizacion } from '../models/comercial.models';

/** Construye una Cotizacion de prueba minima pero valida. */
function cotizacionFalsa(): Cotizacion {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    clienteId: '22222222-2222-2222-2222-222222222222',
    oportunidadId: null,
    estado: 'borrador',
    subtotal: 100,
    total: 100,
    partidas: [],
    canalVentaId: null,
    folio: 'COT-2026-0001',
    fechaEmision: '2026-01-01',
    validoHasta: '2026-01-31',
    condiciones: 'Precios en MXN, mas IVA.',
    notas: 'Entrega en 10 dias habiles.',
    moneda: 'MXN',
    enviadaEn: null,
    clienteNombre: 'Acme',
    clienteRfc: 'ABCD901231XYZ',
    clienteEmail: 'contacto@acme.com',
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

describe('CotizacionesService', () => {
  let service: CotizacionesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(CotizacionesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listar arma la URL con page, size y filtro de estado', () => {
    service.listar({ estado: 'enviada' }, 2, 50).subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === '/api/v1/cotizaciones' &&
        r.params.get('page') === '2' &&
        r.params.get('size') === '50' &&
        r.params.get('estado') === 'enviada',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('listar omite el filtro de estado cuando es nulo', () => {
    service.listar({}, 0, 20).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/cotizaciones');
    expect(req.request.params.has('estado')).toBe(false);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('crear envia POST /cotizaciones con el cuerpo y devuelve la cotizacion', () => {
    const esperado = cotizacionFalsa();
    let recibido: Cotizacion | undefined;
    service
      .crear({ clienteId: esperado.clienteId, partidas: [{ descripcion: 'Rotulo', cantidad: 1 }] })
      .subscribe((c) => (recibido = c));
    const req = http.expectOne('/api/v1/cotizaciones');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      clienteId: esperado.clienteId,
      partidas: [{ descripcion: 'Rotulo', cantidad: 1 }],
    });
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('crear envia los campos descriptivos opcionales (validoHasta, condiciones, notas, moneda)', () => {
    service
      .crear({
        clienteId: 'cli-1',
        partidas: [{ descripcion: 'Rotulo', cantidad: 1 }],
        validoHasta: '2026-02-15',
        condiciones: 'Vigencia 30 dias.',
        notas: 'Incluye instalacion.',
        moneda: 'USD',
      })
      .subscribe();
    const req = http.expectOne('/api/v1/cotizaciones');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      clienteId: 'cli-1',
      partidas: [{ descripcion: 'Rotulo', cantidad: 1 }],
      validoHasta: '2026-02-15',
      condiciones: 'Vigencia 30 dias.',
      notas: 'Incluye instalacion.',
      moneda: 'USD',
    });
    req.flush(cotizacionFalsa());
  });

  it('consultar devuelve el DTO con los nuevos campos descriptivos', () => {
    const esperado = cotizacionFalsa();
    let recibido: Cotizacion | undefined;
    service.consultar('11111111-1111-1111-1111-111111111111').subscribe((c) => (recibido = c));
    const req = http.expectOne('/api/v1/cotizaciones/11111111-1111-1111-1111-111111111111');
    expect(req.request.method).toBe('GET');
    req.flush(esperado);
    expect(recibido?.folio).toBe('COT-2026-0001');
    expect(recibido?.moneda).toBe('MXN');
    expect(recibido?.clienteEmail).toBe('contacto@acme.com');
    expect(recibido?.condiciones).toBe('Precios en MXN, mas IVA.');
  });

  it('descargarPdf hace GET /cotizaciones/{id}/pdf con responseType blob', () => {
    let recibido: Blob | undefined;
    service.descargarPdf('cot-9').subscribe((b) => (recibido = b));
    const req = http.expectOne('/api/v1/cotizaciones/cot-9/pdf');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    const blob = new Blob(['%PDF-1.7'], { type: 'application/pdf' });
    req.flush(blob);
    expect(recibido).toBeInstanceOf(Blob);
  });

  it('enviarCorreo hace POST /cotizaciones/{id}/enviar-correo con el email y devuelve el DTO', () => {
    const esperado = cotizacionFalsa();
    let recibido: Cotizacion | undefined;
    service.enviarCorreo('cot-7', { email: 'nuevo@correo.com' }).subscribe((c) => (recibido = c));
    const req = http.expectOne('/api/v1/cotizaciones/cot-7/enviar-correo');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'nuevo@correo.com' });
    req.flush({ ...esperado, estado: 'enviada', enviadaEn: '2026-01-02T10:00:00Z' });
    expect(recibido?.estado).toBe('enviada');
  });

  it('enviarCorreo sin argumentos envia un cuerpo vacio', () => {
    service.enviarCorreo('cot-7').subscribe();
    const req = http.expectOne('/api/v1/cotizaciones/cot-7/enviar-correo');
    expect(req.request.body).toEqual({});
    req.flush(cotizacionFalsa());
  });

  it('cambiarEstado envia PUT /cotizaciones/{id}/estado con la etiqueta', () => {
    service.cambiarEstado('abc', 'enviada').subscribe();
    const req = http.expectOne('/api/v1/cotizaciones/abc/estado');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ estado: 'enviada' });
    req.flush(cotizacionFalsa());
  });

  it('agregarPartida envia POST /cotizaciones/{id}/partidas', () => {
    service.agregarPartida('abc', { descripcion: 'Lona', cantidad: 2, precioUnitario: 50 }).subscribe();
    const req = http.expectOne('/api/v1/cotizaciones/abc/partidas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ descripcion: 'Lona', cantidad: 2, precioUnitario: 50 });
    req.flush(cotizacionFalsa());
  });

  it('generarPrueba envia POST a la ruta de pruebas de diseno de la cotizacion', () => {
    service.generarPrueba('cot-1').subscribe();
    const req = http.expectOne('/api/v1/cotizaciones/cot-1/pruebas-diseno');
    expect(req.request.method).toBe('POST');
    req.flush({
      id: 'p1',
      cotizacionId: 'cot-1',
      numeroVersion: 1,
      estado: 'pendiente',
      aprobadaPor: null,
      rechazadaPor: null,
      decididaEn: null,
      version: 0,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });
  });
});
