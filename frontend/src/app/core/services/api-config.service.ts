import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Expone la configuración base del cliente HTTP hacia la API REST versionada.
 * La URL base (`/api/v1`) proviene de la configuración de entorno (Req 12.4).
 */
@Injectable({ providedIn: 'root' })
export class ApiConfigService {
  /** URL base de la API, por ejemplo `/api/v1`. */
  readonly baseUrl = environment.apiBaseUrl;

  /** Construye la URL absoluta de un recurso relativo de la API. */
  url(path: string): string {
    const normalized = path.startsWith('/') ? path.slice(1) : path;
    return `${this.baseUrl}/${normalized}`;
  }
}
