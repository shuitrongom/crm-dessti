import { decodificarClaims, estaExpirado } from './jwt.util';
import { construirJwt } from './jwt.test-util';

describe('jwt.util', () => {
  describe('decodificarClaims', () => {
    it('decodifica los claims de un JWT valido', () => {
      const token = construirJwt({
        sub: 'ana',
        tenant_id: 'tenant-1',
        roles: ['admin_empresa'],
        permisos: ['usuario:crear', 'branding:leer'],
        exp: 2000000000,
      });
      const claims = decodificarClaims(token);
      expect(claims).not.toBeNull();
      expect(claims?.sub).toBe('ana');
      expect(claims?.tenant_id).toBe('tenant-1');
      expect(claims?.roles).toEqual(['admin_empresa']);
      expect(claims?.permisos).toContain('usuario:crear');
      expect(claims?.exp).toBe(2000000000);
    });

    it('devuelve null ante un token nulo o vacio', () => {
      expect(decodificarClaims(null)).toBeNull();
      expect(decodificarClaims(undefined)).toBeNull();
      expect(decodificarClaims('')).toBeNull();
    });

    it('devuelve null ante un token con formato invalido', () => {
      expect(decodificarClaims('no-es-un-jwt')).toBeNull();
      expect(decodificarClaims('solo.dos')).toBeNull();
    });

    it('decodifica el claim identificador legible cuando esta presente', () => {
      const token = construirJwt({
        sub: 'b0000000-0000-0000-0000-000000000001',
        identificador: 'superadmin@dessti',
      });
      const claims = decodificarClaims(token);
      expect(claims?.identificador).toBe('superadmin@dessti');
    });

    it('deja identificador en null cuando el token no lo trae', () => {
      const token = construirJwt({ sub: 'ana' });
      const claims = decodificarClaims(token);
      expect(claims?.identificador).toBeNull();
    });

    it('normaliza roles y permisos ausentes a arreglos vacios', () => {
      const token = construirJwt({ sub: 'sin-roles', roles: [], permisos: [] });
      const claims = decodificarClaims(token);
      expect(claims?.roles).toEqual([]);
      expect(claims?.permisos).toEqual([]);
    });

    it('decodifica el claim modulos cuando esta presente', () => {
      const token = construirJwt({ sub: 'ana', modulos: ['estrategia', 'comercial'] });
      const claims = decodificarClaims(token);
      expect(claims?.modulos).toEqual(['estrategia', 'comercial']);
    });

    it('normaliza modulos ausente a arreglo vacio (p. ej. super_admin)', () => {
      const token = construirJwt({ sub: 'root', roles: ['super_admin'] });
      const claims = decodificarClaims(token);
      expect(claims?.modulos).toEqual([]);
    });

    it('conserva un arreglo vacio de modulos (Empresa sin modulos contratados)', () => {
      const token = construirJwt({ sub: 'ana', modulos: [] });
      const claims = decodificarClaims(token);
      expect(claims?.modulos).toEqual([]);
    });
  });

  describe('estaExpirado', () => {
    it('considera expirado un exp en el pasado', () => {
      const pasado = Math.floor(Date.now() / 1000) - 100;
      expect(estaExpirado(pasado)).toBe(true);
    });

    it('considera vigente un exp lejano en el futuro', () => {
      const futuro = Math.floor(Date.now() / 1000) + 3600;
      expect(estaExpirado(futuro)).toBe(false);
    });

    it('considera expirado un exp nulo o ausente', () => {
      expect(estaExpirado(null)).toBe(true);
      expect(estaExpirado(undefined)).toBe(true);
      expect(estaExpirado(0)).toBe(true);
    });

    it('aplica el margen de anticipacion', () => {
      const casiExpira = Math.floor(Date.now() / 1000) + 5;
      // Con un margen de 10s, un exp a 5s ya se considera expirado.
      expect(estaExpirado(casiExpira, 10)).toBe(true);
    });
  });
});
