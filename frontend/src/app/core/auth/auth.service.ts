// =============================================================================
// Servicio de autenticacion (Req 1, 3, 68) — signals-first
// -----------------------------------------------------------------------------
// Mantiene la sesion del Usuario como signals, expone helpers de autorizacion
// deny-by-default (tienePermiso / tieneRol) y orquesta login/refresh/logout
// contra los endpoints publicos /auth/*.
//
// Persistencia: sessionStorage (no localStorage). Se elige sessionStorage por
// seguridad: la sesion vive solo mientras la pestana esta abierta y no persiste
// tras cerrar el navegador, reduciendo la ventana de robo de tokens frente a
// XSS/dispositivos compartidos (Req 68). El backend, ademas, acota el
// Token_Acceso a vida corta y permite revocar el Token_Refresco.
// =============================================================================

import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { ApiConfigService } from '../services/api-config.service';
import {
  Ambito,
  ClaimsToken,
  LoginRequest,
  ROL_CLIENTE_PORTAL,
  ROL_SUPER_ADMIN,
  RefreshRequest,
  TokenResponse,
} from './auth.models';
import { decodificarClaims, estaExpirado } from './jwt.util';

/** Clave de almacenamiento de los tokens en sessionStorage. */
const CLAVE_SESION = 'crm.auth.tokens';

/** Estructura persistida de la sesion (solo tokens; los claims se derivan). */
interface SesionPersistida {
  accessToken: string;
  refreshToken: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  // ---------------------------------------------------------------------------
  // Estado reactivo (signals)
  // ---------------------------------------------------------------------------
  private readonly accessTokenSignal = signal<string | null>(null);
  private readonly refreshTokenSignal = signal<string | null>(null);

  /** Token_Acceso vigente (solo lectura). */
  readonly accessToken = this.accessTokenSignal.asReadonly();
  /** Token_Refresco vigente (solo lectura). */
  readonly refreshToken = this.refreshTokenSignal.asReadonly();

  /** Claims decodificados del Token_Acceso (derivados, no verificados en cliente). */
  readonly claims = computed<ClaimsToken | null>(() =>
    decodificarClaims(this.accessTokenSignal()),
  );

  /**
   * Identificador tecnico del Usuario (claim `sub`, UUID). Uso interno; NO debe
   * mostrarse en la UI (para eso esta {@link nombreMostrado}).
   */
  readonly identificador = computed(() => this.claims()?.sub ?? null);

  /**
   * Nombre a mostrar del Usuario en la UI (menu de cuenta). Prefiere el claim
   * legible `identificador` (p. ej. "superadmin@dessti") y, solo si esta ausente
   * (tokens antiguos), recae en el `sub` (UUID). `null` sin sesion.
   */
  readonly nombreMostrado = computed(() => {
    const c = this.claims();
    if (!c) {
      return null;
    }
    return c.identificador ?? c.sub;
  });
  /** Empresa (tenant) del Usuario; `null` para super_admin. */
  readonly tenantId = computed(() => this.claims()?.tenant_id ?? null);
  /** Roles del Usuario. */
  readonly roles = computed<readonly string[]>(() => this.claims()?.roles ?? []);
  /** Permisos atomicos del Usuario ("recurso:operacion"). */
  readonly permisos = computed<readonly string[]>(() => this.claims()?.permisos ?? []);
  /**
   * Clave del Giro del tenant del Usuario (Req 9.1); `null` para super_admin
   * (nivel plataforma, sin giro) o cuando el claim esta ausente.
   */
  readonly giro = computed<string | null>(() => this.claims()?.giro ?? null);
  /**
   * Claves canonicas de los Modulos contratados por el tenant (gating por
   * modulo). Vacio (`[]`) sin sesion, para super_admin (sin gating por modulo)
   * o para una Empresa sin modulos contratados. Deny-by-default: el gating por
   * modulo solo aplica al ambito empresa; el ambito plataforma no lo usa.
   */
  readonly modulos = computed<readonly string[]>(() => this.claims()?.modulos ?? []);

  /**
   * Indica si hay una sesion autenticada valida: existe Token_Acceso, es
   * decodificable y aun no ha expirado (Req 68). No verifica la firma (servidor).
   */
  readonly isAuthenticated = computed<boolean>(() => {
    const c = this.claims();
    return c !== null && !estaExpirado(c.exp);
  });

  /**
   * Ambito de navegacion del Usuario derivado de sus roles (Req 3):
   * super_admin -> plataforma; cliente_portal -> portal; resto -> empresa.
   */
  readonly ambito = computed<Ambito>(() => {
    const roles = this.roles();
    if (roles.includes(ROL_SUPER_ADMIN)) {
      return 'plataforma';
    }
    if (roles.includes(ROL_CLIENTE_PORTAL)) {
      return 'portal';
    }
    return 'empresa';
  });

  constructor() {
    this.restaurar();
  }

  // ---------------------------------------------------------------------------
  // Helpers de autorizacion (deny-by-default — Req 3.2)
  // ---------------------------------------------------------------------------

  /**
   * Comprueba si el Usuario posee un permiso atomico `recurso:operacion`.
   * Deny-by-default: sin sesion o sin el permiso exacto, devuelve `false`.
   */
  tienePermiso(recurso: string, operacion: string): boolean {
    return this.permisos().includes(`${recurso}:${operacion}`);
  }

  /** Comprueba si el Usuario tiene un rol concreto. */
  tieneRol(rol: string): boolean {
    return this.roles().includes(rol);
  }

  /** Comprueba si el Usuario tiene al menos uno de los roles indicados. */
  tieneAlgunRol(...roles: string[]): boolean {
    const propios = this.roles();
    return roles.some((r) => propios.includes(r));
  }

  /** Comprueba si el Usuario tiene al menos uno de los permisos indicados. */
  tieneAlgunPermiso(...permisos: string[]): boolean {
    const propios = this.permisos();
    return permisos.some((p) => propios.includes(p));
  }

  /**
   * Comprueba si el Giro del tenant del Usuario coincide con la clave indicada
   * (Req 9.2, 9.3). Deny-by-default: sin giro (p. ej. super_admin) devuelve
   * `false`, por lo que ningun item/rama del vertical queda expuesto.
   */
  esGiro(clave: string): boolean {
    return this.giro() === clave;
  }

  /**
   * Comprueba si el tenant del Usuario tiene contratado el Modulo con la clave
   * canonica indicada (gating por modulo). Deny-by-default: sin el modulo en la
   * Suscripcion activa (incluida la Empresa sin modulos) devuelve `false`, por
   * lo que ningun item/rama gestionado por modulo queda expuesto. Solo aplica al
   * ambito empresa; el ambito plataforma no usa gating por modulo.
   */
  tieneModulo(clave: string): boolean {
    return this.modulos().includes(clave);
  }

  // ---------------------------------------------------------------------------
  // Operaciones contra /auth/* (publicas)
  // ---------------------------------------------------------------------------

  /**
   * Inicia sesion (Req 1.1). Ante credenciales invalidas el backend responde
   * 401 con un mensaje generico; el error se propaga para que la UI muestre un
   * mensaje sin filtrar detalle (Req 1.3).
   */
  login(credenciales: LoginRequest): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(this.api.url('/auth/login'), credenciales)
      .pipe(tap((respuesta) => this.aplicarTokens(respuesta)));
  }

  /**
   * Renueva el Token_Acceso a partir del Token_Refresco vigente (Req 1.5). Un
   * refresco invalido/expirado produce 401; en tal caso la sesion se limpia.
   */
  refresh(): Observable<TokenResponse> {
    const cuerpo: RefreshRequest = { refreshToken: this.refreshTokenSignal() ?? '' };
    return this.http
      .post<TokenResponse>(this.api.url('/auth/refresh'), cuerpo)
      .pipe(tap((respuesta) => this.aplicarTokens(respuesta)));
  }

  /**
   * Cierra la sesion (Req 68.1): revoca el Token_Refresco en el backend (idempotente)
   * y limpia el estado local. Siempre limpia la sesion local, incluso si la
   * llamada de red falla.
   */
  logout(): Observable<void> {
    const cuerpo: RefreshRequest = { refreshToken: this.refreshTokenSignal() ?? '' };
    return new Observable<void>((observador) => {
      const sub = this.http
        .post<void>(this.api.url('/auth/logout'), cuerpo)
        .subscribe({
          next: () => {
            this.limpiar();
            observador.next();
            observador.complete();
          },
          error: () => {
            // Idempotente: el cierre local se aplica aunque el servidor falle.
            this.limpiar();
            observador.next();
            observador.complete();
          },
        });
      return () => sub.unsubscribe();
    });
  }

  /** Limpia por completo la sesion (tokens + almacenamiento). */
  limpiar(): void {
    this.accessTokenSignal.set(null);
    this.refreshTokenSignal.set(null);
    try {
      sessionStorage.removeItem(CLAVE_SESION);
    } catch {
      // Almacenamiento no disponible (modo privado extremo): estado en memoria.
    }
  }

  // ---------------------------------------------------------------------------
  // Persistencia
  // ---------------------------------------------------------------------------

  /** Aplica los tokens recibidos al estado y los persiste. */
  private aplicarTokens(respuesta: TokenResponse): void {
    this.accessTokenSignal.set(respuesta.accessToken);
    this.refreshTokenSignal.set(respuesta.refreshToken);
    try {
      const persistida: SesionPersistida = {
        accessToken: respuesta.accessToken,
        refreshToken: respuesta.refreshToken,
      };
      sessionStorage.setItem(CLAVE_SESION, JSON.stringify(persistida));
    } catch {
      // Sin almacenamiento: la sesion vive solo en memoria de la pestana.
    }
  }

  /** Restaura la sesion persistida al iniciar la aplicacion. */
  private restaurar(): void {
    try {
      const bruto = sessionStorage.getItem(CLAVE_SESION);
      if (!bruto) {
        return;
      }
      const persistida = JSON.parse(bruto) as SesionPersistida;
      if (persistida?.accessToken && persistida?.refreshToken) {
        this.accessTokenSignal.set(persistida.accessToken);
        this.refreshTokenSignal.set(persistida.refreshToken);
      }
    } catch {
      // Datos corruptos: se ignoran y la sesion queda vacia.
    }
  }
}
