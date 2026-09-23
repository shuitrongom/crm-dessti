// =============================================================================
// Pruebas unitarias del CuentasCanalService (Req 64.1, 64.2 / conexiones)
// -----------------------------------------------------------------------------
// Verifican de forma determinista y sin zona el mapeo HTTP del servicio contra
// el contrato REST del backend (CuentasCanalSocialController) usando
// provideHttpClientTesting: URL, verbo, parametros de paginacion/filtro y el
// cuerpo del alta. No arrancan la aplicacion ni exponen credenciales.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { CuentasCanalService } from './cuentas-canal.service';
import { CrearCuentaCanalSocialRequest, CuentaCanalSocial } from '../models/social.models';

function cuentaFalsa(): CuentaCanalSocial {
  return {
    id: 'cta-1',
    canal: 'facebook',
    identificadorExterno: 'pagina-123',
    nombre: 'Pagina de la empresa',
    credencialesRef: 'secreto-fb',
    activa: true,
    version: 0,
    createdAt: '2026-01-10T10:00:00Z',
    updatedAt: '2026-01-10T10:00:00Z',
  };
}

describe('CuentasCanalService', () => {
  let service: CuentasCanalService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    });
    service = TestBed.inject(CuentasCanalService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listar arma la URL con page, size y el filtro de canal', () => {
    service.listar('tiktok', 2, 50).subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === '/api/v1/social/cuentas-canal' &&
        r.params.get('page') === '2' &&
        r.params.get('size') === '50' &&
        r.params.get('canal') === 'tiktok',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('listar omite el filtro de canal cuando es nulo', () => {
    service.listar(null, 0, 100).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/social/cuentas-canal');
    expect(req.request.params.has('canal')).toBe(false);
    req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  });

  it('crear hace POST a /social/cuentas-canal con el cuerpo correcto y devuelve la cuenta', () => {
    const solicitud: CrearCuentaCanalSocialRequest = {
      canal: 'facebook',
      identificadorExterno: 'pagina-123',
      nombre: 'Pagina de la empresa',
      credencialesRef: 'secreto-fb',
    };
    let recibida: CuentaCanalSocial | undefined;
    service.crear(solicitud).subscribe((c) => (recibida = c));

    const req = http.expectOne('/api/v1/social/cuentas-canal');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(solicitud);

    const esperada = cuentaFalsa();
    req.flush(esperada);
    expect(recibida).toEqual(esperada);
  });
});
