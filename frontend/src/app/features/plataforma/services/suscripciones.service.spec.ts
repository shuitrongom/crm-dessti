// =============================================================================
// Pruebas unitarias del SuscripcionesService (Req 9)
// -----------------------------------------------------------------------------
// Verifican de forma determinista y sin zona el mapeo HTTP del servicio contra
// el contrato REST del backend (context-path /api/v1) usando
// provideHttpClientTesting: URL exacta, verbo, parametros de filtro y cuerpo.
// No arrancan la aplicacion completa.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { SuscripcionesService } from './suscripciones.service';
import { Suscripcion } from '../models/plataforma.models';

/** Construye una Suscripcion de prueba minima pero valida. */
function suscripcionFalsa(): Suscripcion {
  return {
    id: '22222222-2222-2222-2222-222222222222',
    tenantId: '33333333-3333-3333-3333-333333333333',
    planId: '44444444-4444-4444-4444-444444444444',
    tipoInstrumento: 'plan',
    paqueteSuscripcionId: null,
    estado: 'activa',
    vigenciaInicio: '2025-01-01',
    vigenciaFin: null,
    modulosHabilitados: null,
    monedaFacturacion: null,
    version: 0,
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
  };
}

describe('SuscripcionesService', () => {
  let service: SuscripcionesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    });
    service = TestBed.inject(SuscripcionesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listarPorEmpresa arma GET /suscripciones con el param tenantId', () => {
    const esperado = [suscripcionFalsa()];
    let recibido: Suscripcion[] | undefined;
    service.listarPorEmpresa('33333333-3333-3333-3333-333333333333').subscribe((s) => (recibido = s));
    const req = http.expectOne(
      (r) =>
        r.url === '/api/v1/suscripciones' &&
        r.params.get('tenantId') === '33333333-3333-3333-3333-333333333333',
    );
    expect(req.request.method).toBe('GET');
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('consultar hace GET /suscripciones/{id}', () => {
    const esperado = suscripcionFalsa();
    let recibido: Suscripcion | undefined;
    service.consultar('abc').subscribe((s) => (recibido = s));
    const req = http.expectOne('/api/v1/suscripciones/abc');
    expect(req.request.method).toBe('GET');
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('crear hace POST /suscripciones con el cuerpo y devuelve la suscripcion', () => {
    const esperado = suscripcionFalsa();
    const request = {
      tenantId: '33333333-3333-3333-3333-333333333333',
      planId: '44444444-4444-4444-4444-444444444444',
      vigenciaInicio: '2025-01-01',
      vigenciaFin: '2025-12-31',
    };
    let recibido: Suscripcion | undefined;
    service.crear(request).subscribe((s) => (recibido = s));
    const req = http.expectOne('/api/v1/suscripciones');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('activar hace POST /suscripciones/{id}/activar con body vacio', () => {
    const esperado = suscripcionFalsa();
    let recibido: Suscripcion | undefined;
    service.activar('abc').subscribe((s) => (recibido = s));
    const req = http.expectOne('/api/v1/suscripciones/abc/activar');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('suspender hace POST /suscripciones/{id}/suspender con body vacio', () => {
    const esperado = { ...suscripcionFalsa(), estado: 'suspendida' as const };
    let recibido: Suscripcion | undefined;
    service.suspender('abc').subscribe((s) => (recibido = s));
    const req = http.expectOne('/api/v1/suscripciones/abc/suspender');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('cancelar hace POST /suscripciones/{id}/cancelar con body vacio', () => {
    const esperado = { ...suscripcionFalsa(), estado: 'cancelada' as const };
    let recibido: Suscripcion | undefined;
    service.cancelar('abc').subscribe((s) => (recibido = s));
    const req = http.expectOne('/api/v1/suscripciones/abc/cancelar');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('actualizarVigencia hace PUT /suscripciones/{id}/vigencia con el cuerpo', () => {
    const esperado = { ...suscripcionFalsa(), vigenciaInicio: '2025-02-01', vigenciaFin: '2025-11-30' };
    const request = { vigenciaInicio: '2025-02-01', vigenciaFin: '2025-11-30' };
    let recibido: Suscripcion | undefined;
    service.actualizarVigencia('abc', request).subscribe((s) => (recibido = s));
    const req = http.expectOne('/api/v1/suscripciones/abc/vigencia');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('activarFacturacion hace POST /suscripciones/{id}/activar-facturacion con el cuerpo', () => {
    const esperado = suscripcionFalsa();
    const request = { inicioFacturacion: '2025-02-01', nuevaVigenciaFin: '2025-11-30' };
    let recibido: Suscripcion | undefined;
    service.activarFacturacion('abc', request).subscribe((s) => (recibido = s));
    const req = http.expectOne('/api/v1/suscripciones/abc/activar-facturacion');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('extenderPrueba hace POST /suscripciones/{id}/extender-prueba con el cuerpo', () => {
    const esperado = { ...suscripcionFalsa(), vigenciaFin: '2025-03-31' };
    const request = { nuevaVigenciaFin: '2025-03-31' };
    let recibido: Suscripcion | undefined;
    service.extenderPrueba('abc', request).subscribe((s) => (recibido = s));
    const req = http.expectOne('/api/v1/suscripciones/abc/extender-prueba');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('convertirAPlan hace POST /suscripciones/{id}/convertir-a-plan con el cuerpo', () => {
    const esperado = { ...suscripcionFalsa(), tipoInstrumento: 'plan' as const };
    const request = { planId: '44444444-4444-4444-4444-444444444444' };
    let recibido: Suscripcion | undefined;
    service.convertirAPlan('abc', request).subscribe((s) => (recibido = s));
    const req = http.expectOne('/api/v1/suscripciones/abc/convertir-a-plan');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });
});
