import {
  ApplicationConfig,
  DEFAULT_CURRENCY_CODE,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MAT_ICON_DEFAULT_OPTIONS } from '@angular/material/icon';

import { routes } from './app.routes';
import { apiInterceptor } from './core/interceptors/api.interceptor';
import { provideFechaIsoDatepicker } from './shared/date/provide-fecha-iso';

// Registra los datos de configuracion regional es-MX (Angular solo incluye en-US
// por defecto). Sin este registro, los pipes currency/number con locale 'es-MX'
// lanzan NG0701 en tiempo de render. Debe ejecutarse antes de crear la app.
registerLocaleData(localeEsMx);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Locale y moneda por defecto de la aplicacion (es-MX / MXN).
    { provide: LOCALE_ID, useValue: 'es-MX' },
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'MXN' },
    // Enrutamiento por ambito con vinculacion de inputs desde parametros de ruta.
    provideRouter(routes, withComponentInputBinding()),
    // Cliente HTTP hacia `/api/v1` con el interceptor de autenticacion (Req 1, 12, 68):
    // adjunta el Token_Acceso y renueva ante 401 (refresh-and-retry).
    provideHttpClient(withInterceptors([apiInterceptor])),
    // Animaciones de Angular Material cargadas de forma diferida; respetan la
    // preferencia de reduccion de movimiento definida en los estilos (Req 55).
    provideAnimationsAsync(),
    // Fuente de iconos por defecto para <mat-icon>: el index carga
    // "Material Symbols Outlined", cuya clase CSS es `material-symbols-outlined`.
    // Sin este default, mat-icon usaria la clase clasica `material-icons` y los
    // glifos no cargarian (se veria el texto del ligature). Aplica a toda la app.
    { provide: MAT_ICON_DEFAULT_OPTIONS, useValue: { fontSet: 'material-symbols-outlined' } },
    // Datepicker de Material en toda la app (es-MX, DD/MM/YYYY) manteniendo el
    // valor del control como cadena ISO YYYY-MM-DD, sin desfase de zona horaria.
    ...provideFechaIsoDatepicker(),
  ],
};
