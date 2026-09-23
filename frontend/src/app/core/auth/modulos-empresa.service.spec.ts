// =============================================================================
// Pruebas unitarias del ModulosEmpresaService (Modulos vivos sin re-login)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real, que el servicio:
//   - refresca la lista viva desde GET /empresa/modulos (solo ambito empresa),
//   - concede tieneModulo por la lista viva cuando esta cargada,
//   - recae en el claim del JWT (AuthService) cuando la lista viva es null,
//   - deja la lista viva en null ante error, sin lanzar,
//   - no consulta el endpoint fuera del ambito empresa (evita 403).
// Se inyecta un AuthService simulado (solo lo que consulta el servicio).
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { ModulosEmpresaService } from './modulos-empresa.service';
import { AuthService } from './auth.service';

const URL_MODULOS = '/api/v1/empresa/modulos';

/** AuthService simulado: solo el ambito y el claim de modulos (fallback). */
interface AuthFake {
  ambito: () => 'plataforma' | 'empresa' | 'portal';
  modulos: () => readonly string[];
}

function crearServicio(auth: AuthFake): {
  service: ModulosEmpresaService;
  http: HttpTestingController;
} {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptorsFromDi()),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: auth },
    ],
  });
  return {
    service: TestBed.inject(ModulosEmpresaService),
    http: TestBed.inject(HttpTestingController),
  };
}

describe('ModulosEmpresaService', () => {
  it('refrescar() consulta GET /empresa/modulos y fija la lista viva', () => {
    const { service, http } = crearServicio({ ambito: () => 'empresa', modulos: () => [] });

    service.refrescar();
    const req = http.expectOne(URL_MODULOS);
    expect(req.request.method).toBe('GET');
    req.flush({ modulos: ['estrategia', 'comercial'] });

    expect(service.modulos()).toEqual(['estrategia', 'comercial']);
    expect(service.tieneModulo('comercial')).toBe(true);
    http.verify();
  });

  it('tieneModulo usa la lista viva cuando esta cargada (ignora el claim)', () => {
    // El claim solo tiene "estrategia"; la lista viva anade "comercial".
    const { service, http } = crearServicio({
      ambito: () => 'empresa',
      modulos: () => ['estrategia'],
    });

    service.refrescar();
    http.expectOne(URL_MODULOS).flush({ modulos: ['estrategia', 'comercial'] });

    expect(service.tieneModulo('comercial')).toBe(true);
    expect(service.tieneModulo('estrategia')).toBe(true);
    http.verify();
  });

  it('la lista viva puede QUITAR un modulo respecto del claim (baja de plan)', () => {
    // El claim aun trae "comercial", pero el plan vigente ya no lo incluye.
    const { service, http } = crearServicio({
      ambito: () => 'empresa',
      modulos: () => ['estrategia', 'comercial'],
    });

    service.refrescar();
    http.expectOne(URL_MODULOS).flush({ modulos: ['estrategia'] });

    expect(service.tieneModulo('estrategia')).toBe(true);
    expect(service.tieneModulo('comercial')).toBe(false);
    http.verify();
  });

  it('una lista viva VACIA no anula el claim (evita denegar por lectura transitoria)', () => {
    // Regresion: antes `[] ?? claim` dejaba `[]` y denegaba Modulos SI contratados.
    // Ahora una lista viva vacia se ignora y el gating recae en el claim del JWT.
    const { service, http } = crearServicio({
      ambito: () => 'empresa',
      modulos: () => ['inventario-avanzado', 'comercial'],
    });

    service.refrescar();
    http.expectOne(URL_MODULOS).flush({ modulos: [] });

    expect(service.tieneModulo('inventario-avanzado')).toBe(true);
    expect(service.tieneModulo('comercial')).toBe(true);
    http.verify();
  });

  it('sin cargar la lista viva (null), recae en el claim del JWT', () => {
    const { service, http } = crearServicio({
      ambito: () => 'empresa',
      modulos: () => ['estrategia'],
    });

    // Sin refrescar: modulos() es null y el gating usa el claim.
    expect(service.modulos()).toBeNull();
    expect(service.tieneModulo('estrategia')).toBe(true);
    expect(service.tieneModulo('comercial')).toBe(false);
    http.verify();
  });

  it('ante error deja la lista viva en null sin lanzar (recae en el claim)', () => {
    const { service, http } = crearServicio({
      ambito: () => 'empresa',
      modulos: () => ['estrategia'],
    });

    service.refrescar();
    http.expectOne(URL_MODULOS).flush('fallo', { status: 500, statusText: 'Server Error' });

    expect(service.modulos()).toBeNull();
    // Fallback al claim: sigue concediendo lo contratado, sin romper nada.
    expect(service.tieneModulo('estrategia')).toBe(true);
    http.verify();
  });

  it('no consulta el endpoint fuera del ambito empresa (plataforma -> evita 403)', () => {
    const { service, http } = crearServicio({ ambito: () => 'plataforma', modulos: () => [] });

    service.refrescar();
    http.expectNone(URL_MODULOS);
    expect(service.modulos()).toBeNull();
    http.verify();
  });

  it('no consulta el endpoint en el ambito portal', () => {
    const { service, http } = crearServicio({ ambito: () => 'portal', modulos: () => [] });

    service.refrescar();
    http.expectNone(URL_MODULOS);
    http.verify();
  });

  it('limpiar() descarta la lista viva y vuelve al fallback por claim', () => {
    const { service, http } = crearServicio({
      ambito: () => 'empresa',
      modulos: () => ['estrategia'],
    });

    service.refrescar();
    http.expectOne(URL_MODULOS).flush({ modulos: ['estrategia', 'comercial'] });
    expect(service.tieneModulo('comercial')).toBe(true);

    service.limpiar();
    expect(service.modulos()).toBeNull();
    // Vuelve al claim: comercial ya no concede (solo estaba en la lista viva).
    expect(service.tieneModulo('comercial')).toBe(false);
    expect(service.tieneModulo('estrategia')).toBe(true);
    http.verify();
  });
});
