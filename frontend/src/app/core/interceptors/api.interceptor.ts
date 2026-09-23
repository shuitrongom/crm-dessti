import { HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';

/**
 * Interceptor HTTP de la aplicacion (Req 1, 12, 68).
 *
 * Responsabilidades:
 *  1. Adjuntar `Authorization: Bearer <accessToken>` a toda peticion autenticada
 *     (todas salvo las rutas publicas `/auth/*`).
 *  2. Ante un 401 en una peticion autenticada (Token_Acceso expirado), intentar
 *     UNA sola renovacion via {@link AuthService#refresh} y reintentar la
 *     peticion original con el nuevo token (Req 1.5, 68). Si el refresco falla o
 *     la peticion reintentada vuelve a fallar, se limpia la sesion y se redirige
 *     a `/login` (Req 3.2).
 *
 * Se mantiene como {@link HttpInterceptorFn} funcional (estilo Angular moderno).
 */
export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // Las rutas de autenticacion son publicas: no se adjunta token ni se reintenta.
  if (esRutaAuth(req.url)) {
    return next(req);
  }

  const peticion = adjuntarToken(req, auth.accessToken());

  return next(peticion).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        auth.refreshToken()
      ) {
        return renovarYReintentar(req, next, auth, router);
      }
      return throwError(() => error);
    }),
  );
};

/** Indica si la URL corresponde a una ruta publica de autenticacion (`/auth/*`). */
function esRutaAuth(url: string): boolean {
  return url.includes('/auth/login') || url.includes('/auth/refresh') || url.includes('/auth/logout');
}

/** Devuelve una copia de la peticion con la cabecera Authorization cuando hay token. */
function adjuntarToken<T>(req: HttpRequest<T>, token: string | null): HttpRequest<T> {
  if (!token) {
    return req;
  }
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Intenta un unico refresco del Token_Acceso y reintenta la peticion original.
 * Si el refresco falla, limpia la sesion y navega a `/login`.
 */
function renovarYReintentar<T>(
  original: HttpRequest<T>,
  next: HttpHandlerFn,
  auth: AuthService,
  router: Router,
): Observable<HttpEvent<unknown>> {
  return auth.refresh().pipe(
    switchMap((respuesta) => next(adjuntarToken(original, respuesta.accessToken))),
    catchError((error: unknown) => {
      auth.limpiar();
      void router.navigate(['/login']);
      return throwError(() => error);
    }),
  ) as Observable<HttpEvent<unknown>>;
}
