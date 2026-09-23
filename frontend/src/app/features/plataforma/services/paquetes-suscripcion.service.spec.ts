// =============================================================================
// Pruebas unitarias del PaquetesSuscripcionService (Req 3, 10)
// -----------------------------------------------------------------------------
// Verifican de forma determinista y sin zona el mapeo HTTP del servicio contra
// el contrato REST del backend (context-path /api/v1) usando
// provideHttpClientTesting: URL exacta, verbo, parametros de paginacion y cuerpo.
// No arrancan la aplicacion completa.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import {
  GuardarPaqueteSuscripcionRequest,
  PaquetesSuscripcionService,
} from './paquetes-suscripcion.service';
import { PaqueteSuscripcion } from '../models/plataforma.models';
import { PaginaResponse } from '../../../core/models/pagina-response';

/** Construye un Paquete de Suscripcion de prueba minimo pero valido. */
function paqueteFalso(): PaqueteSuscripcion {
  return {
    id: '55555555-5555-5555-5555-555555555555',
    nombre: 'Paquete temporada',
    maxUsuarios: 10,
    giroId: '66666666-6666-6666-6666-666666666666',
    monedaCodigo: 'MXN',
    preciosModulos: { ventas: 500, inventario: 300 },
    total: 800,
    modulosHabilitados: ['ventas', 'inventario'],
    duracionDias: 90,
    admitePrueba: true,
    duracionPruebaMeses: 1,
    version: 0,
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
  };
}

/** Construye un cuerpo de guardado valido, espejo de {@link paqueteFalso}. */
function guardarRequestFalso(): GuardarPaqueteSuscripcionRequest {
  return {
    nombre: 'Paquete temporada',
    maxUsuarios: 10,
    duracionDias: 90,
    admitePrueba: true,
    duracionPruebaMeses: 1,
    giroId: '66666666-6666-6666-6666-666666666666',
    monedaCodigo: 'MXN',
    preciosModulos: { ventas: 500, inventario: 300 },
  };
}

describe('PaquetesSuscripcionService', () => {
  let service: PaquetesSuscripcionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    });
    service = TestBed.inject(PaquetesSuscripcionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listarPaquetes arma GET /paquetes-suscripcion con los params page/size', () => {
    const esperado: PaginaResponse<PaqueteSuscripcion> = {
      content: [paqueteFalso()],
      page: 1,
      size: 5,
      totalElements: 1,
      totalPages: 1,
    };
    let recibido: PaginaResponse<PaqueteSuscripcion> | undefined;
    service.listarPaquetes(1, 5).subscribe((p) => (recibido = p));
    const req = http.expectOne(
      (r) =>
        r.url === '/api/v1/paquetes-suscripcion' &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '5',
    );
    expect(req.request.method).toBe('GET');
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('consultarPaquete hace GET /paquetes-suscripcion/{id}', () => {
    const esperado = paqueteFalso();
    let recibido: PaqueteSuscripcion | undefined;
    service.consultarPaquete('abc').subscribe((p) => (recibido = p));
    const req = http.expectOne('/api/v1/paquetes-suscripcion/abc');
    expect(req.request.method).toBe('GET');
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('crearPaquete hace POST /paquetes-suscripcion con el cuerpo y devuelve el paquete', () => {
    const esperado = paqueteFalso();
    const request = guardarRequestFalso();
    let recibido: PaqueteSuscripcion | undefined;
    service.crearPaquete(request).subscribe((p) => (recibido = p));
    const req = http.expectOne('/api/v1/paquetes-suscripcion');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('actualizarPaquete hace PUT /paquetes-suscripcion/{id} con el cuerpo', () => {
    const esperado = paqueteFalso();
    const request = guardarRequestFalso();
    let recibido: PaqueteSuscripcion | undefined;
    service.actualizarPaquete('abc', request).subscribe((p) => (recibido = p));
    const req = http.expectOne('/api/v1/paquetes-suscripcion/abc');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('eliminarPaquete hace DELETE /paquetes-suscripcion/{id}', () => {
    let completo = false;
    service.eliminarPaquete('abc').subscribe(() => (completo = true));
    const req = http.expectOne('/api/v1/paquetes-suscripcion/abc');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    expect(completo).toBe(true);
  });
});
