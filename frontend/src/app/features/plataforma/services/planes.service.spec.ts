// =============================================================================
// Pruebas unitarias del PlanesService (Req 25, plataforma-multigiro)
// -----------------------------------------------------------------------------
// Verifican de forma determinista y sin zona el mapeo HTTP del servicio contra
// el contrato REST del backend usando provideHttpClientTesting: URL y verbo de
// las operaciones (incluida la eliminacion de Planes). No arrancan la app.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { PlanesService } from './planes.service';

describe('PlanesService', () => {
  let service: PlanesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    });
    service = TestBed.inject(PlanesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listarPlanes arma la URL con page y size', () => {
    service.listarPlanes(1, 50).subscribe();
    const req = http.expectOne(
      (r) => r.url === '/api/v1/planes' && r.params.get('page') === '1' && r.params.get('size') === '50',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], page: 1, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('eliminarPlan envia DELETE /planes/{id}', () => {
    service.eliminarPlan('abc').subscribe();
    const req = http.expectOne('/api/v1/planes/abc');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
