// =============================================================================
// Pruebas unitarias del GirosService (Req 9)
// -----------------------------------------------------------------------------
// Verifican de forma determinista y sin zona el mapeo HTTP del servicio contra
// el contrato REST del backend usando provideHttpClientTesting: URL, verbo,
// parametros de paginacion/filtro y cuerpo. No arrancan la aplicacion completa.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { GirosService } from './giros.service';
import { Giro } from '../models/plataforma.models';

/** Construye un Giro de prueba minimo pero valido. */
function giroFalso(): Giro {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    clave: 'telas-textiles',
    nombreVisible: 'Telas y textiles',
    descripcion: null,
    activo: true,
    version: 0,
    tieneReglasNegocio: false,
    modulosEspecificos: 0,
  };
}

describe('GirosService', () => {
  let service: GirosService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    });
    service = TestBed.inject(GirosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listar arma la URL con page, size y filtro de estado activo', () => {
    service.listar(true, 2, 50).subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === '/api/v1/plataforma/giros' &&
        r.params.get('page') === '2' &&
        r.params.get('size') === '50' &&
        r.params.get('activo') === 'true',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('listar filtra por inactivos cuando activo es false', () => {
    service.listar(false, 0, 20).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/plataforma/giros');
    expect(req.request.params.get('activo')).toBe('false');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('listar omite el filtro de estado cuando es nulo', () => {
    service.listar(null, 0, 20).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/plataforma/giros');
    expect(req.request.params.has('activo')).toBe(false);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('crear envia POST /plataforma/giros con el cuerpo y devuelve el giro', () => {
    const esperado = giroFalso();
    let recibido: Giro | undefined;
    service
      .crear({ clave: 'telas-textiles', nombreVisible: 'Telas y textiles', descripcion: null })
      .subscribe((g) => (recibido = g));
    const req = http.expectOne('/api/v1/plataforma/giros');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      clave: 'telas-textiles',
      nombreVisible: 'Telas y textiles',
      descripcion: null,
    });
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('activar envia POST /plataforma/giros/{id}/activar', () => {
    service.activar('abc').subscribe();
    const req = http.expectOne('/api/v1/plataforma/giros/abc/activar');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(giroFalso());
  });

  it('desactivar envia POST /plataforma/giros/{id}/desactivar', () => {
    service.desactivar('abc').subscribe();
    const req = http.expectOne('/api/v1/plataforma/giros/abc/desactivar');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ ...giroFalso(), activo: false });
  });

  it('eliminar envia DELETE /plataforma/giros/{id}', () => {
    service.eliminar('abc').subscribe();
    const req = http.expectOne('/api/v1/plataforma/giros/abc');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
