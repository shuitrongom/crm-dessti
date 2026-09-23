// =============================================================================
// Pruebas unitarias de las guardas de ruta (deny-by-default, Req 3)
// -----------------------------------------------------------------------------
// Verifican la politica de denegacion por defecto:
//   - Sin sesion -> redireccion a /login (preservando la URL de retorno).
//   - Con sesion pero sin rol/permiso -> redireccion a /acceso-denegado.
//   - guardaRaiz redirige al inicio del ambito segun el rol.
//
// Las guardas usan inject(); se ejecutan dentro de un contexto de inyeccion con
// un AuthService simulado y un Router real. No se toca la red.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

import { AuthService } from './auth.service';
import { ModulosEmpresaService } from './modulos-empresa.service';
import {
  authGuard,
  guardaGiro,
  guardaModulo,
  guardaModuloAlguno,
  guardaPorPermiso,
  guardaPorRoles,
  guardaRaiz,
} from './auth.guard';

/** AuthService simulado: solo los metodos que consultan las guardas. */
interface AuthFake {
  isAuthenticated: () => boolean;
  ambito: () => 'plataforma' | 'empresa' | 'portal';
  roles: () => readonly string[];
  giro: () => string | null;
  tieneRol: (rol: string) => boolean;
  tieneAlgunRol: (...roles: string[]) => boolean;
  tienePermiso: (recurso: string, operacion: string) => boolean;
  esGiro: (clave: string) => boolean;
  tieneModulo: (clave: string) => boolean;
}

/** Construye un AuthService simulado con valores por defecto sobrescribibles. */
function authFake(overrides: Partial<AuthFake> = {}): AuthFake {
  const roles: readonly string[] = [];
  const base: AuthFake = {
    isAuthenticated: () => false,
    ambito: () => 'empresa',
    roles: () => roles,
    giro: () => null,
    tieneRol: () => false,
    tieneAlgunRol: () => false,
    tienePermiso: () => false,
    esGiro: () => false,
    tieneModulo: () => false,
    ...overrides,
  };
  // Coherencia: si se sobrescribe `giro` pero no `esGiro`, derivar esGiro del giro.
  if (overrides.giro && !overrides.esGiro) {
    base.esGiro = (clave) => base.giro() === clave;
  }
  return base;
}

/** Estado de ruta minimo con la URL solicitada. */
function estado(url: string): RouterStateSnapshot {
  return { url } as RouterStateSnapshot;
}

const RUTA = {} as ActivatedRouteSnapshot;

describe('guardas de ruta (deny-by-default)', () => {
  function configurar(
    auth: AuthFake,
    modulos: Pick<ModulosEmpresaService, 'tieneModulo'> = {
      // Por defecto delega en el claim del AuthFake (Modulos EFECTIVOS con la
      // lista viva sin cargar): preserva la semantica de los casos existentes.
      tieneModulo: (clave: string) => auth.tieneModulo(clave),
    },
  ) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ModulosEmpresaService, useValue: modulos },
      ],
    });
  }

  /** Ejecuta una guarda dentro del contexto de inyeccion. */
  function ejecutar(guarda: typeof authGuard, url = '/empresa/inicio') {
    return TestBed.runInInjectionContext(() => guarda(RUTA, estado(url)));
  }

  describe('authGuard', () => {
    it('permite el acceso con sesion valida', () => {
      configurar(authFake({ isAuthenticated: () => true }));
      expect(ejecutar(authGuard)).toBe(true);
    });

    it('redirige a /login preservando la URL de retorno cuando no hay sesion', () => {
      configurar(authFake({ isAuthenticated: () => false }));
      const resultado = ejecutar(authGuard, '/empresa/administracion/usuarios');
      expect(resultado).toBeInstanceOf(UrlTree);
      const router = TestBed.inject(Router);
      const esperado = router.createUrlTree(['/login'], {
        queryParams: { retorno: '/empresa/administracion/usuarios' },
      });
      expect((resultado as UrlTree).toString()).toBe(esperado.toString());
    });
  });

  describe('guardaPorRoles', () => {
    it('permite el acceso si el Usuario tiene alguno de los roles', () => {
      configurar(
        authFake({
          isAuthenticated: () => true,
          tieneAlgunRol: (...r) => r.includes('super_admin'),
        }),
      );
      expect(ejecutar(guardaPorRoles('super_admin'))).toBe(true);
    });

    it('redirige a /acceso-denegado si esta autenticado pero sin el rol', () => {
      configurar(
        authFake({ isAuthenticated: () => true, tieneAlgunRol: () => false }),
      );
      const resultado = ejecutar(guardaPorRoles('super_admin'));
      expect(resultado).toBeInstanceOf(UrlTree);
      expect((resultado as UrlTree).toString()).toContain('acceso-denegado');
    });

    it('redirige a /login si no hay sesion (prioridad sobre el rol)', () => {
      configurar(authFake({ isAuthenticated: () => false }));
      const resultado = ejecutar(guardaPorRoles('super_admin'));
      expect((resultado as UrlTree).toString()).toContain('login');
    });
  });

  describe('guardaPorPermiso', () => {
    it('permite el acceso con el permiso atomico exacto', () => {
      configurar(
        authFake({
          isAuthenticated: () => true,
          tienePermiso: (r, o) => r === 'empresa' && o === 'listar',
        }),
      );
      expect(ejecutar(guardaPorPermiso('empresa', 'listar'))).toBe(true);
    });

    it('redirige a /acceso-denegado sin el permiso (deny-by-default)', () => {
      configurar(
        authFake({ isAuthenticated: () => true, tienePermiso: () => false }),
      );
      const resultado = ejecutar(guardaPorPermiso('empresa', 'listar'));
      expect((resultado as UrlTree).toString()).toContain('acceso-denegado');
    });
  });

  describe('guardaGiro (Req 9.4)', () => {
    it('permite el acceso si el giro del Usuario esta entre los permitidos', () => {
      configurar(
        authFake({ isAuthenticated: () => true, giro: () => 'anuncios-luminosos' }),
      );
      expect(ejecutar(guardaGiro('anuncios-luminosos'), '/empresa/operacion')).toBe(true);
    });

    it('redirige a /acceso-denegado si el giro no coincide', () => {
      configurar(authFake({ isAuthenticated: () => true, giro: () => 'manufactura' }));
      const resultado = ejecutar(guardaGiro('anuncios-luminosos'), '/empresa/operacion');
      expect(resultado).toBeInstanceOf(UrlTree);
      expect((resultado as UrlTree).toString()).toContain('acceso-denegado');
    });

    it('redirige a /acceso-denegado si no hay giro (p. ej. super_admin)', () => {
      configurar(authFake({ isAuthenticated: () => true, giro: () => null }));
      const resultado = ejecutar(guardaGiro('anuncios-luminosos'), '/empresa/operacion');
      expect((resultado as UrlTree).toString()).toContain('acceso-denegado');
    });

    it('redirige a /login si no hay sesion (prioridad sobre el giro)', () => {
      configurar(authFake({ isAuthenticated: () => false }));
      const resultado = ejecutar(guardaGiro('anuncios-luminosos'), '/empresa/operacion');
      expect((resultado as UrlTree).toString()).toContain('login');
    });
  });

  describe('guardaModulo (gating por modulo)', () => {
    it('permite el acceso si el tenant tiene contratado el modulo', () => {
      configurar(
        authFake({ isAuthenticated: () => true, tieneModulo: (c) => c === 'facturacion' }),
      );
      expect(ejecutar(guardaModulo('facturacion'), '/empresa/facturacion')).toBe(true);
    });

    it('redirige a /acceso-denegado si el modulo no esta contratado (deny-by-default)', () => {
      configurar(authFake({ isAuthenticated: () => true, tieneModulo: () => false }));
      const resultado = ejecutar(guardaModulo('facturacion'), '/empresa/facturacion');
      expect(resultado).toBeInstanceOf(UrlTree);
      expect((resultado as UrlTree).toString()).toContain('acceso-denegado');
    });

    it('redirige a /acceso-denegado si no hay modulos (p. ej. super_admin)', () => {
      configurar(authFake({ isAuthenticated: () => true, tieneModulo: () => false }));
      const resultado = ejecutar(guardaModulo('estrategia'), '/empresa/estrategia-vistas');
      expect((resultado as UrlTree).toString()).toContain('acceso-denegado');
    });

    it('redirige a /login si no hay sesion (prioridad sobre el modulo)', () => {
      configurar(authFake({ isAuthenticated: () => false }));
      const resultado = ejecutar(guardaModulo('facturacion'), '/empresa/facturacion');
      expect((resultado as UrlTree).toString()).toContain('login');
    });

    // -------------------------------------------------------------------------
    // Modulos EFECTIVOS: lista viva si esta cargada, con fallback al claim del
    // JWT. Se conduce con un ModulosEmpresaService simulado.
    // -------------------------------------------------------------------------
    it('permite el acceso cuando la lista VIVA tiene el modulo (aunque el claim no)', () => {
      // El claim NO trae comercial, pero la lista viva (plan vigente) SI: se
      // permite sin re-login.
      configurar(
        authFake({ isAuthenticated: () => true, tieneModulo: () => false }),
        { tieneModulo: (c) => c === 'comercial' },
      );
      expect(ejecutar(guardaModulo('comercial'), '/empresa/comercial/clientes')).toBe(true);
    });

    it('permite el acceso cuando la lista viva no ha cargado pero el claim tiene el modulo (recarga en frio, sin regresion)', () => {
      // ModulosEmpresaService.tieneModulo recae en el claim cuando la lista viva
      // es null: aqui el fake delega en el claim del AuthFake (comercial=true).
      configurar(authFake({ isAuthenticated: () => true, tieneModulo: (c) => c === 'comercial' }));
      expect(ejecutar(guardaModulo('comercial'), '/empresa/comercial/clientes')).toBe(true);
    });

    it('deniega cuando NINGUNA fuente concede el modulo (ni viva ni claim)', () => {
      configurar(
        authFake({ isAuthenticated: () => true, tieneModulo: () => false }),
        { tieneModulo: () => false },
      );
      const resultado = ejecutar(guardaModulo('comercial'), '/empresa/comercial/clientes');
      expect(resultado).toBeInstanceOf(UrlTree);
      expect((resultado as UrlTree).toString()).toContain('acceso-denegado');
    });
  });

  describe('guardaModuloAlguno (compuerta gruesa por varios modulos)', () => {
    it('permite el acceso si el tenant tiene AL MENOS UNO de los modulos', () => {
      // Solo 'inventario-avanzado' contratado; la rama /empresa/operacion abre.
      configurar(
        authFake({ isAuthenticated: () => true, tieneModulo: (c) => c === 'inventario-avanzado' }),
      );
      const guarda = guardaModuloAlguno('operacion', 'inventario-avanzado');
      expect(ejecutar(guarda, '/empresa/operacion/inventario-avanzado')).toBe(true);
    });

    it('permite el acceso si tiene el otro modulo del conjunto', () => {
      configurar(authFake({ isAuthenticated: () => true, tieneModulo: (c) => c === 'operacion' }));
      const guarda = guardaModuloAlguno('operacion', 'inventario-avanzado');
      expect(ejecutar(guarda, '/empresa/operacion')).toBe(true);
    });

    it('redirige a /acceso-denegado si NINGUN modulo del conjunto esta contratado', () => {
      configurar(authFake({ isAuthenticated: () => true, tieneModulo: () => false }));
      const guarda = guardaModuloAlguno('operacion', 'inventario-avanzado');
      const resultado = ejecutar(guarda, '/empresa/operacion');
      expect(resultado).toBeInstanceOf(UrlTree);
      expect((resultado as UrlTree).toString()).toContain('acceso-denegado');
    });

    it('redirige a /login si no hay sesion (prioridad sobre el modulo)', () => {
      configurar(authFake({ isAuthenticated: () => false }));
      const guarda = guardaModuloAlguno('operacion', 'inventario-avanzado');
      const resultado = ejecutar(guarda, '/empresa/operacion');
      expect((resultado as UrlTree).toString()).toContain('login');
    });
  });

  describe('guardaRaiz', () => {
    it('redirige a /plataforma para el ambito plataforma', () => {
      configurar(authFake({ isAuthenticated: () => true, ambito: () => 'plataforma' }));
      const resultado = ejecutar(guardaRaiz, '/');
      expect((resultado as UrlTree).toString()).toContain('plataforma');
    });

    it('redirige a /portal para el ambito portal', () => {
      configurar(authFake({ isAuthenticated: () => true, ambito: () => 'portal' }));
      const resultado = ejecutar(guardaRaiz, '/');
      expect((resultado as UrlTree).toString()).toContain('portal');
    });

    it('redirige a /empresa para el ambito empresa', () => {
      configurar(authFake({ isAuthenticated: () => true, ambito: () => 'empresa' }));
      const resultado = ejecutar(guardaRaiz, '/');
      expect((resultado as UrlTree).toString()).toContain('empresa');
    });

    it('redirige a /login si no hay sesion', () => {
      configurar(authFake({ isAuthenticated: () => false }));
      const resultado = ejecutar(guardaRaiz, '/');
      expect((resultado as UrlTree).toString()).toContain('login');
    });
  });
});
