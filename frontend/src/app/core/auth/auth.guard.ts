// =============================================================================
// Guardas de ruta (deny-by-default â€” Req 3)
// -----------------------------------------------------------------------------
// Guardas funcionales (CanActivateFn) que aplican autorizacion por ambito, rol
// y permiso atomico. La politica es de denegacion por defecto:
//   - Sin sesion valida            -> redireccion a /login.
//   - Con sesion pero sin permiso  -> redireccion a /acceso-denegado (403 vista).
//
// El acceso efectivo lo reimpone el backend en cada peticion; estas guardas solo
// evitan renderizar vistas para las que el Usuario no esta autorizado.
// =============================================================================

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';
import { ModulosEmpresaService } from './modulos-empresa.service';
import { ROL_ADMIN_EMPRESA, ROL_CLIENTE_PORTAL, ROL_SUPER_ADMIN } from './auth.models';

/** Redirige a /login preservando la URL de retorno solicitada. */
function haciaLogin(router: Router, urlRetorno: string) {
  return router.createUrlTree(['/login'], { queryParams: { retorno: urlRetorno } });
}

/** Redirige a la vista de acceso denegado (403). */
function haciaAccesoDenegado(router: Router) {
  return router.createUrlTree(['/acceso-denegado']);
}

/**
 * Exige unicamente que exista una sesion autenticada valida (Req 1, 68). Es la
 * base de todas las guardas de ambito.
 */
export const authGuard: CanActivateFn = (_ruta, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? true : haciaLogin(router, estado.url);
};

/**
 * Construye una guarda que exige autenticacion y al menos uno de los roles
 * indicados (deny-by-default). Util para proteger un ambito completo.
 */
export function guardaPorRoles(...roles: string[]): CanActivateFn {
  return (_ruta, estado) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) {
      return haciaLogin(router, estado.url);
    }
    return auth.tieneAlgunRol(...roles) ? true : haciaAccesoDenegado(router);
  };
}

/**
 * Construye una guarda que exige autenticacion y un permiso atomico
 * `recurso:operacion` (deny-by-default). Util para proteger vistas concretas
 * dentro de un ambito.
 */
export function guardaPorPermiso(recurso: string, operacion: string): CanActivateFn {
  return (_ruta, estado) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) {
      return haciaLogin(router, estado.url);
    }
    return auth.tienePermiso(recurso, operacion) ? true : haciaAccesoDenegado(router);
  };
}

/**
 * Construye una guarda que exige autenticacion y que el Giro del tenant del
 * Usuario este entre los `giros` indicados (deny-by-default, Req 9.4). Protege
 * las ramas de un Modulo_Vertical para que solo las Empresas del Giro
 * correspondiente accedan a ellas, en coherencia con el 403 por Gating_Por_Giro
 * que reimpone el backend.
 *
 * Politica:
 *   - Sin sesion valida                 -> redireccion a /login.
 *   - Con sesion pero giro ausente o     -> redireccion a /acceso-denegado.
 *     distinto de los permitidos
 *   - Con sesion y giro permitido        -> acceso concedido.
 *
 * Como super_admin no tiene giro, esta guarda lo deniega; por eso solo debe
 * aplicarse a ramas del ambito /empresa, nunca a /plataforma ni /portal (Req 9.5).
 */
export function guardaGiro(...giros: string[]): CanActivateFn {
  return (_ruta, estado) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) {
      return haciaLogin(router, estado.url);
    }
    const giroActual = auth.giro();
    if (giroActual === null || !giros.includes(giroActual)) {
      return haciaAccesoDenegado(router);
    }
    return true;
  };
}

/**
 * Construye una guarda que exige autenticacion y que el tenant del Usuario tenga
 * contratado el Modulo con la clave canonica indicada (gating por modulo,
 * deny-by-default). Bloquea el acceso directo por URL a las ramas de negocio de
 * un Modulo no contratado, en coherencia con el 403 que reimpone el backend.
 *
 * Politica (analoga a {@link guardaGiro}):
 *   - Sin sesion valida                 -> redireccion a /login.
 *   - Con sesion pero sin el Modulo      -> redireccion a /acceso-denegado.
 *   - Con sesion y Modulo contratado     -> acceso concedido.
 *
 * Como super_admin no trae claim de modulos (opera a nivel plataforma sin gating
 * por modulo), esta guarda lo deniega; por eso solo debe aplicarse a ramas del
 * ambito /empresa, nunca a /plataforma ni /portal.
 *
 * Fuente de modulos: usa los Modulos EFECTIVOS ({@link ModulosEmpresaService}):
 * la lista VIVA del backend si esta cargada; en su defecto, el claim del JWT. Asi
 * el gating refleja altas/bajas de plan sin re-login, y en una recarga en frio
 * (con el fetch vivo aun en curso) recae en el claim, evitando bloquear por error
 * un Modulo ya contratado. Deny-by-default solo cuando NINGUNA fuente lo concede.
 */
export function guardaModulo(clave: string): CanActivateFn {
  return (_ruta, estado) => {
    const auth = inject(AuthService);
    const modulosEmpresa = inject(ModulosEmpresaService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) {
      return haciaLogin(router, estado.url);
    }
    return modulosEmpresa.tieneModulo(clave) ? true : haciaAccesoDenegado(router);
  };
}

/**
 * Construye una guarda que exige autenticacion y que el tenant tenga contratado
 * AL MENOS UNO de los Modulos indicados (deny-by-default). Util para "compuertas
 * gruesas" de una rama que agrupa varios Modulos distintos: la rama se abre si el
 * plan incluye cualquiera de ellos, y cada ruta hija reaplica su propio Modulo y
 * permiso especifico.
 *
 * Caso concreto: la rama `/empresa/operacion` agrupa el Modulo `operacion`
 * (produccion/instalacion/proyectos/materiales) y el Modulo propio
 * `inventario-avanzado`. Una Empresa que solo contrate `inventario-avanzado`
 * debe poder entrar a la rama para llegar a su vista de Inventario; con
 * `guardaModulo('operacion')` a secas quedaba bloqueada aunque tuviera el Modulo
 * de inventario. El gating fino lo reimpone cada hoja (y el backend con su 403).
 *
 * Misma politica y fuente de Modulos que {@link guardaModulo}: usa los Modulos
 * EFECTIVOS de {@link ModulosEmpresaService} (lista viva del backend o, en su
 * defecto, el claim del JWT). Deny-by-default solo cuando NINGUNA clave coincide.
 */
export function guardaModuloAlguno(...claves: string[]): CanActivateFn {
  return (_ruta, estado) => {
    const auth = inject(AuthService);
    const modulosEmpresa = inject(ModulosEmpresaService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) {
      return haciaLogin(router, estado.url);
    }
    const concede = claves.some((clave) => modulosEmpresa.tieneModulo(clave));
    return concede ? true : haciaAccesoDenegado(router);
  };
}

/** Guarda del ambito de plataforma (Req 24, 25, 69): solo super_admin. */
export const guardaPlataforma: CanActivateFn = guardaPorRoles(ROL_SUPER_ADMIN);

/** Guarda del ambito de portal del cliente (Req 45): solo cliente_portal. */
export const guardaPortal: CanActivateFn = guardaPorRoles(ROL_CLIENTE_PORTAL);

/**
 * Guarda del ambito de empresa: cualquier Usuario de empresa (todo rol que no sea
 * super_admin ni exclusivamente cliente_portal). Se define como "autenticado y no
 * es super_admin" para admitir todos los roles internos (gerente, comercial, etc.).
 */
export const guardaEmpresa: CanActivateFn = (_ruta, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return haciaLogin(router, estado.url);
  }
  // super_admin opera en /plataforma; los usuarios de portal en /portal.
  if (auth.tieneRol(ROL_SUPER_ADMIN)) {
    return router.createUrlTree(['/plataforma']);
  }
  return true;
};

/** Guarda del area de administracion de empresa (Req 4, 27, 28, 68): admin_empresa. */
export const guardaAdminEmpresa: CanActivateFn = guardaPorRoles(ROL_ADMIN_EMPRESA);

/**
 * Guarda de la ruta raiz: redirige al inicio del ambito del Usuario segun su rol
 * (super_admin -> /plataforma, cliente_portal -> /portal, resto -> /empresa) o a
 * /login si no hay sesion. Nunca deja renderizar la raiz directamente.
 */
export const guardaRaiz: CanActivateFn = (_ruta, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return haciaLogin(router, estado.url);
  }
  const destino =
    auth.ambito() === 'plataforma'
      ? '/plataforma'
      : auth.ambito() === 'portal'
        ? '/portal'
        : '/empresa';
  return router.createUrlTree([destino]);
};
