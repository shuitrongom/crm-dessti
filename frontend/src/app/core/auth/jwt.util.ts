// =============================================================================
// Utilidad de decodificacion de JWT en cliente (Req 3)
// -----------------------------------------------------------------------------
// Decodifica el segmento de carga util (payload) de un JWT para leer los claims
// (sub, tenant_id, roles, permisos, giro, exp) SIN verificar la firma: la verificacion
// criptografica es responsabilidad EXCLUSIVA del servidor. En el cliente solo se
// usa para componer la navegacion y las guardas por permiso (deny-by-default).
//
// Cualquier decision de seguridad efectiva la reimpone el backend en cada
// peticion; una manipulacion del token en cliente no concede acceso real.
// =============================================================================

import { ClaimsToken } from './auth.models';

/**
 * Decodifica un segmento base64url (el usado por JWT) a texto UTF-8.
 *
 * @param segmento segmento base64url (sin relleno).
 * @returns el texto decodificado.
 */
function decodificarBase64Url(segmento: string): string {
  // base64url -> base64 estandar y relleno a multiplo de 4.
  const base64 = segmento.replace(/-/g, '+').replace(/_/g, '/');
  const relleno = base64.length % 4 === 0 ? '' : '='.repeat(4 - (base64.length % 4));
  const binario = atob(base64 + relleno);

  // Reconstruye la cadena UTF-8 a partir de los bytes decodificados.
  const bytes = Uint8Array.from(binario, (c) => c.charCodeAt(0));
  return new TextDecoder('utf-8').decode(bytes);
}

/**
 * Extrae los claims del payload de un Token_Acceso (JWT). Es tolerante a errores:
 * ante un token con formato invalido devuelve `null` en lugar de lanzar.
 *
 * @param token Token_Acceso en formato JWT (`header.payload.signature`).
 * @returns los claims tipados o `null` si el token no es decodificable.
 */
export function decodificarClaims(token: string | null | undefined): ClaimsToken | null {
  if (!token) {
    return null;
  }
  const partes = token.split('.');
  if (partes.length !== 3) {
    return null;
  }
  try {
    const json = decodificarBase64Url(partes[1]);
    const bruto = JSON.parse(json) as Record<string, unknown>;

    const sub = typeof bruto['sub'] === 'string' ? (bruto['sub'] as string) : '';
    // Identificador legible del Usuario para la UI (p. ej. "superadmin@dessti");
    // ausente en tokens antiguos, en cuyo caso se recae en `sub` aguas arriba.
    const identificador =
      typeof bruto['identificador'] === 'string' ? (bruto['identificador'] as string) : null;
    const exp = typeof bruto['exp'] === 'number' ? (bruto['exp'] as number) : 0;
    const tenantId =
      typeof bruto['tenant_id'] === 'string' ? (bruto['tenant_id'] as string) : null;
    const roles = Array.isArray(bruto['roles'])
      ? (bruto['roles'] as unknown[]).filter((r): r is string => typeof r === 'string')
      : [];
    const permisos = Array.isArray(bruto['permisos'])
      ? (bruto['permisos'] as unknown[]).filter((p): p is string => typeof p === 'string')
      : [];
    // Clave del Giro del tenant (Req 9.1); ausente/nulo para super_admin.
    const giro = typeof bruto['giro'] === 'string' ? (bruto['giro'] as string) : null;
    // Claves de Modulos contratados por el tenant (gating por modulo). Ausente
    // para super_admin (sin gating); se normaliza a [] cuando falta o no es
    // arreglo, coherente con una Empresa sin modulos contratados.
    const modulos = Array.isArray(bruto['modulos'])
      ? (bruto['modulos'] as unknown[]).filter((m): m is string => typeof m === 'string')
      : [];

    return { sub, identificador, tenant_id: tenantId, roles, permisos, giro, modulos, exp };
  } catch {
    return null;
  }
}

/**
 * Indica si el instante de expiracion (`exp`, segundos epoch) ya paso, con un
 * margen de seguridad para adelantarse a expiraciones inminentes (Req 68).
 *
 * @param exp             instante de expiracion en segundos epoch (UTC).
 * @param margenSegundos  margen de anticipacion en segundos (por defecto 10).
 * @returns `true` si el token se considera expirado.
 */
export function estaExpirado(exp: number | null | undefined, margenSegundos = 10): boolean {
  if (!exp) {
    return true;
  }
  const ahoraSegundos = Math.floor(Date.now() / 1000);
  return ahoraSegundos >= exp - margenSegundos;
}
