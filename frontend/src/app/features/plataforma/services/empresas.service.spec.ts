// =============================================================================
// Pruebas del EmpresasService: armado de la URL de listado (Req 24.5)
// -----------------------------------------------------------------------------
// Verifican, sin red real, que `listar` arma correctamente los parametros
// page/size, incluye estado y `q` cuando corresponde y omite `q` cuando esta en
// blanco (o solo espacios).
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { EmpresasService } from './empresas.service';

describe('EmpresasService', () => {
  let service: EmpresasService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        EmpresasService,
      ],
    });
    service = TestBed.inject(EmpresasService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('arma la URL con page y size y omite estado y q por defecto', () => {
    service.listar(null).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/empresas');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('estado')).toBe(false);
    expect(req.request.params.has('q')).toBe(false);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('incluye estado y q cuando se proporcionan (combinables)', () => {
    service.listar('activa', 'acme', 1, 50).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/empresas');
    expect(req.request.params.get('estado')).toBe('activa');
    expect(req.request.params.get('q')).toBe('acme');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ content: [], page: 1, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('omite q cuando esta en blanco o solo tiene espacios', () => {
    service.listar(null, '   ').subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/empresas');
    expect(req.request.params.has('q')).toBe(false);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('recorta los espacios del termino de busqueda', () => {
    service.listar(null, '  acme  ').subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/empresas');
    expect(req.request.params.get('q')).toBe('acme');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('cambiarGiro hace PUT a /empresas/{id}/giro con { giroId }', () => {
    service.cambiarGiro('e1', 'g2').subscribe();
    const req = http.expectOne('/api/v1/empresas/e1/giro');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ giroId: 'g2' });
    req.flush({ id: 'e1', giroId: 'g2' });
  });
});
