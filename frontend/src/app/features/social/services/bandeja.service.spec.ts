// =============================================================================
// Pruebas unitarias del BandejaService (Req 64)
// -----------------------------------------------------------------------------
// Verifican de forma determinista y sin zona el mapeo HTTP del servicio contra
// el contrato REST del backend (BandejaController) usando provideHttpClientTesting:
// URL, verbo, parametros de paginacion/filtro y cuerpo. No arrancan la aplicacion.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { BandejaService } from './bandeja.service';
import { Conversacion, MensajeSocial } from '../models/social.models';

function conversacionFalsa(): Conversacion {
  return {
    id: 'c-1',
    cuentaCanalSocialId: 'cta-1',
    canal: 'whatsapp',
    remitenteExterno: '+521234567890',
    clienteId: null,
    contactoId: null,
    estado: 'abierta',
    asignadoA: null,
    ultimoEntranteUtc: '2026-01-10T10:00:00Z',
    version: 0,
    createdAt: '2026-01-10T10:00:00Z',
    updatedAt: '2026-01-10T10:00:00Z',
  };
}

function mensajeFalso(): MensajeSocial {
  return {
    id: 'm-1',
    conversacionId: 'c-1',
    direccion: 'saliente',
    tipo: 'texto',
    contenido: 'Hola',
    esMarketing: false,
    estadoEntrega: 'enviado',
    externoId: null,
    enviadoEn: '2026-01-10T11:00:00Z',
    recibidoEn: null,
    version: 0,
    createdAt: '2026-01-10T11:00:00Z',
    updatedAt: '2026-01-10T11:00:00Z',
  };
}

describe('BandejaService', () => {
  let service: BandejaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    });
    service = TestBed.inject(BandejaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listar arma la URL con page, size y filtros de canal y estado', () => {
    service.listar({ canal: 'whatsapp', estado: 'abierta' }, 1, 50).subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === '/api/v1/social/bandeja' &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '50' &&
        r.params.get('canal') === 'whatsapp' &&
        r.params.get('estado') === 'abierta',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], page: 1, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('listar omite los filtros cuando son nulos', () => {
    service.listar({}, 0, 20).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/social/bandeja');
    expect(req.request.params.has('canal')).toBe(false);
    expect(req.request.params.has('estado')).toBe(false);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('listarMensajes consulta el historial paginado de la conversacion', () => {
    service.listarMensajes('c-1', 0, 100).subscribe();
    const req = http.expectOne(
      (r) => r.url === '/api/v1/social/bandeja/c-1/mensajes' && r.params.get('size') === '100',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [mensajeFalso()], page: 0, size: 100, totalElements: 1, totalPages: 1 });
  });

  it('enviarMensaje hace POST con tipo, contenido y esMarketing', () => {
    let recibido: MensajeSocial | undefined;
    service
      .enviarMensaje('c-1', { tipo: 'texto', contenido: 'Hola', esMarketing: false })
      .subscribe((m) => (recibido = m));
    const req = http.expectOne('/api/v1/social/bandeja/c-1/mensajes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ tipo: 'texto', contenido: 'Hola', esMarketing: false });
    const esperado = mensajeFalso();
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('asignar hace PUT /asignacion con el usuarioId', () => {
    service.asignar('c-1', 'u-9').subscribe();
    const req = http.expectOne('/api/v1/social/bandeja/c-1/asignacion');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ usuarioId: 'u-9' });
    req.flush(conversacionFalsa());
  });

  it('cerrar hace PUT /cierre con cuerpo vacio', () => {
    service.cerrar('c-1').subscribe();
    const req = http.expectOne('/api/v1/social/bandeja/c-1/cierre');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({});
    req.flush({ ...conversacionFalsa(), estado: 'cerrada' });
  });
});
