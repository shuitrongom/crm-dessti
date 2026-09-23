// =============================================================================
// Pruebas unitarias del interceptor HTTP de la aplicacion (Req 1, 12, 68)
// -----------------------------------------------------------------------------
// Verifican, a traves del pipeline real de HttpClient:
//   - Se adjunta `Authorization: Bearer <token>` a las peticiones autenticadas.
//   - NO se adjunta token ni se reintenta en las rutas publicas /auth/*.
//   - Ante un 401 en peticion autenticada, se hace UN unico refresh-and-retry.
//   - Si el refresh falla, se limpia la sesion y se redirige a /login.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { apiInterceptor } from './api.interceptor';
import { AuthService } from '../auth/auth.service';
import { TokenResponse } from '../auth/auth.models';

/** TokenResponse minima para las renovaciones simuladas. */
function tokenResponse(accessToken: string): TokenResponse {
  return { accessToken, refreshToken: 'refresh-nuevo', tokenType: 'Bearer', expiresIn: 900 };
}

describe('apiInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let auth: {
    accessToken: () => string | null;
    refreshToken: () => string | null;
    refresh: () => Observable<TokenResponse>;
    limpiar: () => void;
  };
  let router: { navigate: (comandos: unknown[]) => Promise<boolean> };
  let refreshLlamadas: number;
  let limpiarLlamado: boolean;
  let navegaciones: unknown[][];

  function configurar(overrides: Partial<typeof auth> = {}) {
    refreshLlamadas = 0;
    limpiarLlamado = false;
    navegaciones = [];

    auth = {
      accessToken: () => 'token-acceso',
      refreshToken: () => 'token-refresco',
      refresh: () => {
        refreshLlamadas += 1;
        return of(tokenResponse('token-renovado'));
      },
      limpiar: () => {
        limpiarLlamado = true;
      },
      ...overrides,
    };

    router = {
      navigate: (comandos: unknown[]) => {
        navegaciones.push(comandos);
        return Promise.resolve(true);
      },
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });

    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    controller.verify();
  });

  it('adjunta el Bearer a una peticion autenticada', () => {
    configurar();
    http.get('/api/v1/empresas').subscribe();
    const req = controller.expectOne('/api/v1/empresas');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-acceso');
    req.flush([]);
  });

  it('no adjunta token cuando no hay sesion', () => {
    configurar({ accessToken: () => null });
    http.get('/api/v1/empresas').subscribe();
    const req = controller.expectOne('/api/v1/empresas');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('no adjunta token ni reintenta en las rutas /auth/*', () => {
    configurar();
    // El handler de error consume el 401 esperado para que no quede sin manejar.
    http.post('/api/v1/auth/login', {}).subscribe({ error: () => { /* 401 esperado: no dispara refresh */ } });
    const req = controller.expectOne('/api/v1/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    // Un 401 en /auth NO debe disparar refresh.
    req.flush({ detail: 'credenciales invalidas' }, { status: 401, statusText: 'Unauthorized' });
    expect(refreshLlamadas).toBe(0);
  });

  it('ante 401 hace un unico refresh y reintenta con el nuevo token', () => {
    configurar();
    let respuesta: unknown = null;
    http.get('/api/v1/empresas').subscribe((r) => (respuesta = r));

    const primera = controller.expectOne('/api/v1/empresas');
    primera.flush({ detail: 'expirado' }, { status: 401, statusText: 'Unauthorized' });

    // Tras el refresh, la peticion se reintenta con el token renovado.
    const reintento = controller.expectOne('/api/v1/empresas');
    expect(reintento.request.headers.get('Authorization')).toBe('Bearer token-renovado');
    reintento.flush({ ok: true });

    expect(refreshLlamadas).toBe(1);
    expect(respuesta).toEqual({ ok: true });
    expect(limpiarLlamado).toBe(false);
  });

  it('si el refresh falla, limpia la sesion y navega a /login', () => {
    configurar({ refresh: () => throwError(() => new Error('refresco invalido')) });
    let errorRecibido: unknown = null;
    http.get('/api/v1/empresas').subscribe({ error: (e) => (errorRecibido = e) });

    controller
      .expectOne('/api/v1/empresas')
      .flush({ detail: 'expirado' }, { status: 401, statusText: 'Unauthorized' });

    expect(limpiarLlamado).toBe(true);
    expect(navegaciones).toContainEqual(['/login']);
    expect(errorRecibido).not.toBeNull();
  });

  it('no intenta refresh si no hay Token_Refresco disponible', () => {
    configurar({ refreshToken: () => null });
    let errorRecibido: unknown = null;
    http.get('/api/v1/empresas').subscribe({ error: (e) => (errorRecibido = e) });

    controller
      .expectOne('/api/v1/empresas')
      .flush({ detail: 'expirado' }, { status: 401, statusText: 'Unauthorized' });

    expect(refreshLlamadas).toBe(0);
    expect(errorRecibido).not.toBeNull();
  });
});
