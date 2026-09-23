// =============================================================================
// Servicio de Modulos vivos de la Empresa (gating por modulo sin re-login)
// -----------------------------------------------------------------------------
// El claim `modulos` del Token_Acceso queda CONGELADO en el token: si el
// super_admin edita el plan del tenant (p. ej. anade "comercial"), un Usuario de
// empresa ya autenticado conserva el claim antiguo hasta que su token expira o
// vuelve a iniciar sesion, por lo que el menu no refleja el nuevo Modulo.
//
// Este servicio expone una fuente VIVA de los Modulos contratados leyendo el
// endpoint canonico del backend (GET /empresa/modulos), que devuelve el conjunto
// vigente para el tenant del llamante (misma fuente/claves que el claim). El
// gating por modulo (menu y guarda de ruta) prefiere esta lista viva cuando esta
// cargada y recae en el claim del JWT cuando aun no lo esta o falla.
//
// Alcance: SOLO ambito empresa. El super_admin (plataforma) y el cliente_portal
// no deben consultar este endpoint (responderia 403); el disparo del refresco se
// condiciona por `auth.ambito() === 'empresa'`.
//
// Seguridad: el menu/guarda es solo UX; el backend reimpone el gating (403) en
// cada peticion. Preferir la lista viva (que solo refleja el conjunto real
// contratado) nunca hace la aplicacion menos segura.
// =============================================================================

import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { ApiConfigService } from '../services/api-config.service';
import { AuthService } from './auth.service';

/** Respuesta de GET /empresa/modulos. */
interface ModulosEmpresaResponse {
  modulos: string[];
}

@Injectable({ providedIn: 'root' })
export class ModulosEmpresaService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);
  private readonly auth = inject(AuthService);

  /**
   * Lista viva de claves canonicas de Modulos contratados por el tenant. `null`
   * mientras no se ha cargado (o si la ultima carga fallo): en ese estado el
   * gating recae en el claim del JWT. Un arreglo (aunque sea vacio) indica que la
   * lista viva es la fuente autoritativa vigente.
   */
  private readonly modulosSignal = signal<string[] | null>(null);

  /** Lista viva de Modulos (solo lectura); `null` si aun no se ha cargado. */
  readonly modulos = this.modulosSignal.asReadonly();

  /**
   * Modulos EFECTIVOS para el gating (menu y guarda de ruta). Reglas:
   *
   *   - Si la lista viva NO ha cargado (`null`): se usa el claim del JWT. Asi, en
   *     una recarga en frio o mientras `GET /empresa/modulos` esta en curso, el
   *     gating no bloquea un Modulo ya contratado (el claim del token es la fuente
   *     inicial de verdad). Esto elimina la condicion de carrera en la que la
   *     guarda de ruta corria ANTES de que respondiera el fetch vivo.
   *
   *   - Si la lista viva cargo y trae AL MENOS un Modulo: manda la lista viva
   *     (refleja altas/bajas de plan sin re-login).
   *
   *   - Si la lista viva cargo VACIA (`[]`): se ignora y se usa el claim. Una
   *     empresa realmente sin Modulos es un caso extremo (y el backend reimpone el
   *     403 igualmente); en cambio, una lista viva vacia suele ser transitoria (p.
   *     ej. una lectura sin el tenant aun fijado). Antes, con el operador `??`, un
   *     arreglo vacio (`[]`) prevalecia sobre el claim (`[] ?? claim === []`) y
   *     denegaba por error el acceso a Modulos SI contratados. Esta regla corrige
   *     ese bug de raiz sin volver la app menos segura: el backend siempre reimpone
   *     el gating real (403) en cada peticion.
   */
  readonly modulosEfectivos = computed<readonly string[]>(() => {
    const viva = this.modulosSignal();
    if (viva !== null && viva.length > 0) {
      return viva;
    }
    return this.auth.modulos();
  });

  /**
   * Refresca la lista viva desde GET /empresa/modulos. Solo debe invocarse en el
   * ambito empresa: para otros ambitos el backend responde 403. Ante cualquier
   * error (403, red, forma inesperada) deja la lista viva en `null` sin propagar
   * la excepcion, de modo que el gating recae de forma segura en el claim del JWT.
   */
  refrescar(): void {
    // Guarda por ambito: plataforma/portal no consultan (evita 403 innecesario).
    if (this.auth.ambito() !== 'empresa') {
      return;
    }
    this.http.get<ModulosEmpresaResponse>(this.api.url('/empresa/modulos')).subscribe({
      next: (respuesta) => {
        const lista = Array.isArray(respuesta?.modulos) ? respuesta.modulos : [];
        this.modulosSignal.set(lista);
      },
      error: () => {
        // Fallo (403 plataforma/portal, red, etc.): recae en el claim del JWT.
        this.modulosSignal.set(null);
      },
    });
  }

  /** Limpia la lista viva (p. ej. al cerrar sesion o cambiar de Usuario). */
  limpiar(): void {
    this.modulosSignal.set(null);
  }

  /**
   * Comprueba si el tenant tiene contratado el Modulo con la clave canonica dada,
   * usando los Modulos EFECTIVOS (lista viva si esta cargada; si no, el claim del
   * JWT). Deny-by-default: si ninguna de las dos fuentes lo concede, devuelve
   * `false`. Preferir la lista viva permite reflejar altas/bajas de plan sin
   * re-login; el fallback al claim evita bloquear un Modulo ya contratado
   * mientras la lista viva aun no ha cargado (recarga en frio).
   */
  tieneModulo(clave: string): boolean {
    return this.modulosEfectivos().includes(clave);
  }
}
