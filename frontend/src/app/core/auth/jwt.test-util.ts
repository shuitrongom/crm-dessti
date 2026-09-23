// =============================================================================
// Utilidad de PRUEBAS: construccion de JWT sin firmar
// -----------------------------------------------------------------------------
// Genera un token con formato `header.payload.signature` cuyo payload contiene
// los claims indicados, codificado en base64url. La firma es un marcador; el
// cliente no la verifica. Solo se usa en specs.
// =============================================================================

import { ClaimsToken } from './auth.models';

/** Codifica un objeto JSON a base64url (sin relleno). */
function base64UrlDeJson(obj: unknown): string {
  const json = JSON.stringify(obj);
  const bytes = new TextEncoder().encode(json);
  let binario = '';
  for (const b of bytes) {
    binario += String.fromCharCode(b);
  }
  return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Construye un JWT de prueba con los claims dados. */
export function construirJwt(claims: Partial<ClaimsToken>): string {
  const header = base64UrlDeJson({ alg: 'none', typ: 'JWT' });
  const expPorDefecto = Math.floor(Date.now() / 1000) + 3600;
  const payload = base64UrlDeJson({
    sub: claims.sub ?? 'usuario-prueba',
    // El claim `identificador` es opcional: se omite si no se indica (para poder
    // simular tokens antiguos que solo traian `sub`).
    ...(claims.identificador !== undefined ? { identificador: claims.identificador } : {}),
    tenant_id: claims.tenant_id ?? null,
    roles: claims.roles ?? [],
    permisos: claims.permisos ?? [],
    // El claim `giro` es opcional: se omite si no se indica (p. ej. super_admin).
    ...(claims.giro !== undefined ? { giro: claims.giro } : {}),
    // El claim `modulos` es opcional: se omite si no se indica (p. ej.
    // super_admin, que no usa gating por modulo).
    ...(claims.modulos !== undefined ? { modulos: claims.modulos } : {}),
    exp: claims.exp ?? expPorDefecto,
  });
  return `${header}.${payload}.firma-de-prueba`;
}
