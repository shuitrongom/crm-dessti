// =============================================================================
// Pruebas unitarias del AuthService (Req 1, 3, 68)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona:
//   - Decodificacion de claims y derivacion de signals (identificador, tenant,
//     roles, permisos, isAuthenticated, ambito).
//   - Helpers de autorizacion deny-by-default (tienePermiso/tieneRol/tieneAlgun*).
//   - Manejo de tokens en login/refresh/logout, persistencia en sessionStorage,
//     restauracion al iniciar y limpieza de sesion.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { AuthService } from './auth.service';
import { TokenResponse } from './auth.models';
import { construirJwt } from './jwt.test-util';

/** Clave de persistencia usada por el servicio (debe coincidir con la interna). */
const CLAVE_SESION = 'crm.auth.tokens';

/** Construye una TokenResponse de prueba a partir de un access token dado. */
function tokenResponse(accessToken: string, refreshToken = 'refresh-xyz'): TokenResponse {
  return { accessToken, refreshToken, tokenType: 'Bearer', expiresIn: 900 };
}

describe('AuthService', () => {
  let http: HttpTestingController;

  /** Crea una instancia fresca del servicio tras configurar el TestBed. */
  function crearServicio(): AuthService {
    return TestBed.inject(AuthService);
  }

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  describe('estado inicial y decodificacion de claims', () => {
    it('sin sesion, no esta autenticado y expone estado vacio', () => {
      const auth = crearServicio();
      expect(auth.isAuthenticated()).toBe(false);
      expect(auth.accessToken()).toBeNull();
      expect(auth.identificador()).toBeNull();
      expect(auth.tenantId()).toBeNull();
      expect(auth.roles()).toEqual([]);
      expect(auth.permisos()).toEqual([]);
    });

    it('deriva identificador, tenant, roles y permisos de los claims del token', () => {
      const auth = crearServicio();
      const token = construirJwt({
        sub: 'ana',
        tenant_id: 'tenant-1',
        roles: ['admin_empresa'],
        permisos: ['usuario:crear', 'branding:leer'],
      });

      auth.login({ identificador: 'ana', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(token));

      expect(auth.isAuthenticated()).toBe(true);
      expect(auth.identificador()).toBe('ana');
      expect(auth.tenantId()).toBe('tenant-1');
      expect(auth.roles()).toEqual(['admin_empresa']);
      expect(auth.permisos()).toContain('usuario:crear');
    });

    it('nombreMostrado prefiere el claim identificador legible sobre el sub (UUID)', () => {
      const auth = crearServicio();
      const token = construirJwt({
        sub: 'b0000000-0000-0000-0000-000000000001',
        identificador: 'superadmin@dessti',
        roles: ['super_admin'],
      });

      auth.login({ identificador: 'superadmin@dessti', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(token));

      expect(auth.nombreMostrado()).toBe('superadmin@dessti');
      // El sub tecnico (UUID) sigue disponible por separado, sin exponerse en UI.
      expect(auth.identificador()).toBe('b0000000-0000-0000-0000-000000000001');
    });

    it('nombreMostrado recae en el sub cuando el token no trae identificador (tokens antiguos)', () => {
      const auth = crearServicio();
      const token = construirJwt({ sub: 'b0000000-0000-0000-0000-000000000002' });

      auth.login({ identificador: 'x', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(token));

      expect(auth.nombreMostrado()).toBe('b0000000-0000-0000-0000-000000000002');
    });

    it('sin sesion, nombreMostrado es null', () => {
      const auth = crearServicio();
      expect(auth.nombreMostrado()).toBeNull();
    });

    it('considera no autenticado un token expirado', () => {
      const auth = crearServicio();
      const expirado = construirJwt({ sub: 'ana', exp: Math.floor(Date.now() / 1000) - 60 });

      auth.login({ identificador: 'ana', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(expirado));

      expect(auth.isAuthenticated()).toBe(false);
    });
  });

  describe('giro del tenant (Req 9.1, 9.4)', () => {
    it('deriva el giro del claim del token', () => {
      const auth = crearServicio();
      const token = construirJwt({ roles: ['admin_empresa'], giro: 'anuncios-luminosos' });

      auth.login({ identificador: 'u', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(token));

      expect(auth.giro()).toBe('anuncios-luminosos');
    });

    it('esGiro es verdadero solo para la clave exacta', () => {
      const auth = crearServicio();
      const token = construirJwt({ roles: ['admin_empresa'], giro: 'anuncios-luminosos' });

      auth.login({ identificador: 'u', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(token));

      expect(auth.esGiro('anuncios-luminosos')).toBe(true);
      expect(auth.esGiro('manufactura')).toBe(false);
    });

    it('giro es null y esGiro deniega para un token sin claim (super_admin)', () => {
      const auth = crearServicio();
      const token = construirJwt({ roles: ['super_admin'] });

      auth.login({ identificador: 'root', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(token));

      expect(auth.giro()).toBeNull();
      expect(auth.esGiro('anuncios-luminosos')).toBe(false);
    });

    it('sin sesion, giro es null y esGiro deniega', () => {
      const auth = crearServicio();
      expect(auth.giro()).toBeNull();
      expect(auth.esGiro('anuncios-luminosos')).toBe(false);
    });
  });

  describe('modulos contratados del tenant (gating por modulo)', () => {
    it('expone los modulos del claim del token', () => {
      const auth = crearServicio();
      const token = construirJwt({ roles: ['admin_empresa'], modulos: ['estrategia', 'comercial'] });

      auth.login({ identificador: 'u', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(token));

      expect(auth.modulos()).toEqual(['estrategia', 'comercial']);
    });

    it('modulos es [] cuando el claim esta ausente (super_admin)', () => {
      const auth = crearServicio();
      const token = construirJwt({ roles: ['super_admin'] });

      auth.login({ identificador: 'root', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(token));

      expect(auth.modulos()).toEqual([]);
    });

    it('modulos es [] para una Empresa sin modulos contratados', () => {
      const auth = crearServicio();
      const token = construirJwt({ roles: ['admin_empresa'], modulos: [] });

      auth.login({ identificador: 'u', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(token));

      expect(auth.modulos()).toEqual([]);
    });

    it('tieneModulo es verdadero solo para las claves contratadas (deny-by-default)', () => {
      const auth = crearServicio();
      const token = construirJwt({ roles: ['admin_empresa'], modulos: ['estrategia'] });

      auth.login({ identificador: 'u', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(token));

      expect(auth.tieneModulo('estrategia')).toBe(true);
      expect(auth.tieneModulo('facturacion')).toBe(false);
      expect(auth.tieneModulo('comercial')).toBe(false);
    });

    it('sin sesion, modulos es [] y tieneModulo deniega', () => {
      const auth = crearServicio();
      expect(auth.modulos()).toEqual([]);
      expect(auth.tieneModulo('estrategia')).toBe(false);
    });
  });

  describe('derivacion de ambito (Req 3)', () => {
    function loginConRoles(auth: AuthService, roles: string[]): void {
      auth.login({ identificador: 'u', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(construirJwt({ roles })));
    }

    it('super_admin -> plataforma', () => {
      const auth = crearServicio();
      loginConRoles(auth, ['super_admin']);
      expect(auth.ambito()).toBe('plataforma');
    });

    it('cliente_portal -> portal', () => {
      const auth = crearServicio();
      loginConRoles(auth, ['cliente_portal']);
      expect(auth.ambito()).toBe('portal');
    });

    it('cualquier otro rol interno -> empresa', () => {
      const auth = crearServicio();
      loginConRoles(auth, ['admin_empresa']);
      expect(auth.ambito()).toBe('empresa');
    });
  });

  describe('autorizacion deny-by-default', () => {
    function autenticar(auth: AuthService): void {
      const token = construirJwt({
        roles: ['admin_empresa', 'calidad'],
        permisos: ['usuario:crear', 'branding:leer'],
      });
      auth.login({ identificador: 'u', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(token));
    }

    it('tienePermiso solo es verdadero para el permiso exacto', () => {
      const auth = crearServicio();
      autenticar(auth);
      expect(auth.tienePermiso('usuario', 'crear')).toBe(true);
      expect(auth.tienePermiso('usuario', 'eliminar')).toBe(false);
      expect(auth.tienePermiso('branding', 'actualizar')).toBe(false);
    });

    it('tieneRol / tieneAlgunRol reflejan los roles del token', () => {
      const auth = crearServicio();
      autenticar(auth);
      expect(auth.tieneRol('admin_empresa')).toBe(true);
      expect(auth.tieneRol('super_admin')).toBe(false);
      expect(auth.tieneAlgunRol('super_admin', 'calidad')).toBe(true);
      expect(auth.tieneAlgunRol('ventas', 'compras')).toBe(false);
    });

    it('tieneAlgunPermiso reconoce cualquiera de los permisos indicados', () => {
      const auth = crearServicio();
      autenticar(auth);
      expect(auth.tieneAlgunPermiso('rol:crear', 'branding:leer')).toBe(true);
      expect(auth.tieneAlgunPermiso('rol:crear', 'rol:listar')).toBe(false);
    });

    it('sin sesion, todos los helpers deniegan', () => {
      const auth = crearServicio();
      expect(auth.tienePermiso('usuario', 'crear')).toBe(false);
      expect(auth.tieneRol('admin_empresa')).toBe(false);
      expect(auth.tieneAlgunRol('super_admin')).toBe(false);
    });
  });

  describe('manejo de tokens y persistencia', () => {
    it('login persiste los tokens en sessionStorage', () => {
      const auth = crearServicio();
      const token = construirJwt({ sub: 'ana' });

      auth.login({ identificador: 'ana', password: 'secreto' }).subscribe();
      const req = http.expectOne('/api/v1/auth/login');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ identificador: 'ana', password: 'secreto' });
      req.flush(tokenResponse(token, 'refresh-1'));

      expect(auth.accessToken()).toBe(token);
      expect(auth.refreshToken()).toBe('refresh-1');
      const persistido = JSON.parse(sessionStorage.getItem(CLAVE_SESION) ?? '{}');
      expect(persistido.accessToken).toBe(token);
      expect(persistido.refreshToken).toBe('refresh-1');
    });

    it('refresh envia el Token_Refresco vigente y aplica los nuevos tokens', () => {
      const auth = crearServicio();
      auth.login({ identificador: 'u', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(construirJwt({}), 'refresh-1'));

      const nuevo = construirJwt({ sub: 'renovado' });
      auth.refresh().subscribe();
      const req = http.expectOne('/api/v1/auth/refresh');
      expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
      req.flush(tokenResponse(nuevo, 'refresh-2'));

      expect(auth.accessToken()).toBe(nuevo);
      expect(auth.refreshToken()).toBe('refresh-2');
    });

    it('logout revoca en el backend y limpia la sesion local', () => {
      const auth = crearServicio();
      auth.login({ identificador: 'u', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(construirJwt({}), 'refresh-1'));

      auth.logout().subscribe();
      const req = http.expectOne('/api/v1/auth/logout');
      expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(auth.isAuthenticated()).toBe(false);
      expect(auth.accessToken()).toBeNull();
      expect(sessionStorage.getItem(CLAVE_SESION)).toBeNull();
    });

    it('logout limpia la sesion local aunque el backend falle (idempotente)', () => {
      const auth = crearServicio();
      auth.login({ identificador: 'u', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(construirJwt({}), 'refresh-1'));

      auth.logout().subscribe();
      http
        .expectOne('/api/v1/auth/logout')
        .flush(null, { status: 500, statusText: 'Server Error' });

      expect(auth.accessToken()).toBeNull();
      expect(sessionStorage.getItem(CLAVE_SESION)).toBeNull();
    });

    it('limpiar elimina el estado y el almacenamiento', () => {
      const auth = crearServicio();
      auth.login({ identificador: 'u', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(tokenResponse(construirJwt({})));

      auth.limpiar();
      expect(auth.accessToken()).toBeNull();
      expect(auth.refreshToken()).toBeNull();
      expect(sessionStorage.getItem(CLAVE_SESION)).toBeNull();
    });
  });

  describe('restauracion de sesion al iniciar', () => {
    it('restaura los tokens persistidos en sessionStorage', () => {
      const token = construirJwt({ sub: 'persistida', roles: ['admin_empresa'] });
      sessionStorage.setItem(
        CLAVE_SESION,
        JSON.stringify({ accessToken: token, refreshToken: 'refresh-persistido' }),
      );

      const auth = crearServicio();
      expect(auth.accessToken()).toBe(token);
      expect(auth.refreshToken()).toBe('refresh-persistido');
      expect(auth.isAuthenticated()).toBe(true);
      expect(auth.identificador()).toBe('persistida');
    });

    it('ignora datos corruptos sin lanzar', () => {
      sessionStorage.setItem(CLAVE_SESION, 'no-es-json-valido');
      const auth = crearServicio();
      expect(auth.accessToken()).toBeNull();
      expect(auth.isAuthenticated()).toBe(false);
    });
  });
});
