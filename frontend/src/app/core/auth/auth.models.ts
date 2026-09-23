// =============================================================================
// Modelos de autenticacion (Req 1, 3, 68)
// -----------------------------------------------------------------------------
// Contratos TypeScript que reflejan EXACTAMENTE el contrato REST del backend
// para autenticacion, asi como la estructura de claims del Token_Acceso (JWT).
//
// Backend de referencia:
//   - POST /auth/login    -> TokenResponse
//   - POST /auth/refresh   -> TokenResponse
//   - POST /auth/logout    -> 204
//   com.empresa.crm.platform.security.auth.rest.{LoginRequest,RefreshRequest,TokenResponse}
// =============================================================================

/** Cuerpo de POST /auth/login (Req 1.1). */
export interface LoginRequest {
  /** Identificador de acceso del Usuario. */
  identificador: string;
  /** Contrasena en claro (se verifica contra el hash BCrypt en el servidor). */
  password: string;
}

/** Cuerpo de POST /auth/refresh y POST /auth/logout. */
export interface RefreshRequest {
  refreshToken: string;
}

/**
 * Respuesta de /auth/login y /auth/refresh (Req 1.4, 1.5, 1.7).
 * Coincide con `com.empresa.crm.platform.security.auth.rest.TokenResponse`.
 */
export interface TokenResponse {
  /** Token_Acceso (JWT) de vida corta. */
  accessToken: string;
  /** Token_Refresco (JWT) de vida mas larga. */
  refreshToken: string;
  /** Esquema de autenticacion; siempre `"Bearer"`. */
  tokenType: string;
  /** Segundos de vigencia restante del Token_Acceso. */
  expiresIn: number;
  /**
   * true cuando la cuenta debe cambiar su contrasena antes de operar
   * (contrasena temporal, V69). El cliente fuerza el cambio tras el login.
   * Opcional por compatibilidad; ausente se trata como false.
   */
  debeCambiarPassword?: boolean;
}

/**
 * Claims (payload) del Token_Acceso (JWT) que el frontend decodifica en cliente
 * SIN verificar la firma (la verificacion la realiza el servidor). Solo se lee
 * para componer la navegacion y las guardas por permiso (deny-by-default, Req 3).
 *
 * @property sub      identificador tecnico del Usuario (UUID); no apto para mostrar en UI.
 * @property identificador identificador legible de acceso del Usuario (p. ej.
 *           "superadmin@dessti"); es el valor que debe mostrarse en la UI. Puede
 *           estar ausente en tokens antiguos, en cuyo caso se recae en `sub`.
 * @property tenant_id empresa (tenant) del Usuario; ausente/nulo para super_admin.
 * @property roles    nombres de rol (p. ej. "admin_empresa", "super_admin").
 * @property permisos autoridades atomicas "recurso:operacion" (p. ej. "cliente:leer").
 * @property giro     clave del Giro del tenant (p. ej. "anuncios-luminosos");
 *                    ausente/nulo para super_admin (nivel plataforma, sin giro).
 * @property modulos  claves canonicas de los Modulos que el tenant tiene
 *                    contratados (efectivas de la Suscripcion activa). Determina
 *                    que areas de negocio ve/accede la Empresa (gating por
 *                    modulo). AUSENTE para super_admin (opera a nivel plataforma,
 *                    sin gating por modulo). Una Empresa con cero modulos trae un
 *                    arreglo vacio `[]`.
 * @property exp      instante de expiracion en segundos epoch (UTC).
 */
export interface ClaimsToken {
  sub: string;
  /**
   * Identificador legible de acceso del Usuario (p. ej. "superadmin@dessti").
   * Es el valor que debe mostrarse en la UI (menu de cuenta). Ausente en tokens
   * antiguos que aun no incluian el claim; en tal caso la UI recae en `sub`.
   */
  identificador?: string | null;
  tenant_id?: string | null;
  roles: readonly string[];
  permisos: readonly string[];
  /**
   * Clave del Giro del tenant al que pertenece la Empresa del Usuario
   * (Req 9.1). Determina que Modulo_Vertical aplica. Ausente/nulo para
   * super_admin, que opera a nivel plataforma y no tiene giro.
   */
  giro?: string | null;
  /**
   * Claves canonicas de los Modulos contratados por el tenant (Req gating por
   * modulo). Ausente para super_admin (sin gating por modulo); `[]` para una
   * Empresa sin modulos contratados.
   */
  modulos?: readonly string[];
  exp: number;
}

/** Roles de nivel superior que determinan el ambito de navegacion (Req 3). */
export const ROL_SUPER_ADMIN = 'super_admin';
export const ROL_ADMIN_EMPRESA = 'admin_empresa';
export const ROL_CLIENTE_PORTAL = 'cliente_portal';

/**
 * Clave canonica del Giro de anuncios luminosos, primer Modulo_Vertical
 * enchufable (Req 10.2). Se usa para componer la visibilidad de la navegacion y
 * las guardas del vertical (deny-by-default).
 */
export const GIRO_ANUNCIOS = 'anuncios-luminosos';

/** Ambitos de la aplicacion (Req 3): plataforma, empresa y portal del cliente. */
export type Ambito = 'plataforma' | 'empresa' | 'portal';

/**
 * Perfil del Usuario autenticado (respuesta de GET /auth/perfil). Refleja el
 * DTO del backend `com.dessti.crm.platform.security.perfil`.
 *
 * @property id            identificador tecnico del Usuario (UUID).
 * @property identificador identificador legible de acceso (p. ej. "superadmin@dessti").
 * @property roles         nombres de rol del Usuario.
 * @property tenantId      empresa (tenant) del Usuario; `null` a nivel plataforma.
 */
export interface Perfil {
  id: string;
  identificador: string;
  roles: string[];
  tenantId: string | null;
}

/**
 * Cuerpo de PUT /auth/perfil/password (cambio de contrasena propia).
 * El backend valida `passwordNueva` (@NotBlank, 8..255) y responde 422 cuando
 * `passwordActual` no coincide.
 */
export interface CambioPasswordRequest {
  passwordActual: string;
  passwordNueva: string;
}
