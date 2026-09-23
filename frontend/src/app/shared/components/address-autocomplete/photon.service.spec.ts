// =============================================================================
// Pruebas de PhotonService: geocoding via backend propio (revision R2)
// -----------------------------------------------------------------------------
// Verifican de forma determinista, con provideHttpClientTesting, que:
//   - La consulta se dirige al endpoint RELATIVO del backend
//     (`/api/v1/geocoding/direcciones`, via proxy `/api`), NUNCA al tercero.
//   - El parametro `q` lleva el termino normalizado (espacios colapsados).
//   - La respuesta del backend (ya mapeada) se tipa y se propaga tal cual.
//   - Con texto insuficiente (< 3 caracteres) no se emite ninguna peticion.
//   - Ante error de red se degrada a lista vacia sin lanzar.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { PhotonService, DireccionSugerida } from './photon.service';

const GEOCODING_URL = '/api/v1/geocoding/direcciones';

describe('PhotonService', () => {
  let service: PhotonService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    });
    service = TestBed.inject(PhotonService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('consulta el endpoint relativo del backend y propaga la respuesta ya mapeada', () => {
    let recibido: DireccionSugerida[] | undefined;
    service.buscar('Toluca').subscribe((r) => (recibido = r));

    const req = http.expectOne((r) => r.url === GEOCODING_URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('q')).toBe('Toluca');
    // Ya no se llama al tercero ni se envian sus parametros (lang/limit/bbox).
    expect(req.request.url.startsWith('http')).toBe(false);

    const sugerencias: DireccionSugerida[] = [
      {
        etiqueta: 'Avenida Juarez 100, Toluca, Estado de Mexico, Mexico',
        calle: 'Avenida Juarez 100',
        ciudad: 'Toluca',
        estado: 'Estado de Mexico',
        cp: '50000',
        pais: 'Mexico',
      },
    ];
    req.flush(sugerencias);

    expect(recibido).toEqual(sugerencias);
  });

  it('normaliza el termino (colapsa espacios extra y recorta) antes de consultar', () => {
    service.buscar('  Avenida   Juarez   ').subscribe();
    const req = http.expectOne((r) => r.url === GEOCODING_URL);
    expect(req.request.params.get('q')).toBe('Avenida Juarez');
    req.flush([]);
  });

  it('no emite peticion cuando el texto tiene menos de 3 caracteres', () => {
    let recibido: DireccionSugerida[] | undefined;
    service.buscar('to').subscribe((r) => (recibido = r));
    http.expectNone((r) => r.url === GEOCODING_URL);
    expect(recibido).toEqual([]);
  });

  it('degrada a lista vacia ante un error de red', () => {
    let recibido: DireccionSugerida[] | undefined;
    service.buscar('Toluca').subscribe((r) => (recibido = r));
    http
      .expectOne((r) => r.url === GEOCODING_URL)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Network Error' });
    expect(recibido).toEqual([]);
  });
});
