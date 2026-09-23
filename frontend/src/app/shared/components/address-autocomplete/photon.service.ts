// =============================================================================
// PhotonService: autocompletado de direcciones via backend propio (revision R2)
// -----------------------------------------------------------------------------
// Tras verificar que el navegador no alcanza de forma fiable al proveedor OSM
// (fallos CORS/red que quedaban ocultos por el `catchError -> []` y siempre
// mostraban "Sin resultados"), el geocoding pasa por NUESTRO backend: este
// servicio consulta `GET /api/v1/geocoding/direcciones?q=...` con URL RELATIVA
// (a traves del proxy `/api`), y el backend hace la llamada al proveedor y el
// mapeo. Aqui solo se normaliza el termino, se respeta el minimo de caracteres
// y se tipa la respuesta.
//
// Ante entradas insuficientes o errores de red devuelve una lista vacia (nunca
// lanza), de modo que la UI degrada con elegancia sin romper la escritura del
// Usuario. Se conserva el nombre `PhotonService` para minimizar el churn en el
// componente y los formularios que dependen de esta abstraccion.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';

/** Endpoint propio de geocoding (URL relativa, via proxy `/api`). */
const GEOCODING_URL = '/api/v1/geocoding/direcciones';
/** Minimo de caracteres significativos antes de consultar al backend. */
const MIN_CARACTERES = 3;

/**
 * Sugerencia de direccion ya mapeada a los campos del formulario. `etiqueta` es
 * el texto legible mostrado en la lista; el resto son los valores que se
 * vuelcan en los controles al elegir la opcion. El backend garantiza la forma;
 * cualquier campo puede venir vacio (`''`) cuando el proveedor no lo aporta.
 */
export interface DireccionSugerida {
  /** Texto legible de la sugerencia (para el `mat-option`). */
  etiqueta: string;
  /** Calle y numero (calle/nombre + numero exterior si existe). */
  calle: string;
  /** Ciudad (con respaldo a distrito/condado cuando falta la ciudad). */
  ciudad: string;
  /** Estado o provincia. */
  estado: string;
  /** Codigo postal. */
  cp: string;
  /** Pais. */
  pais: string;
}

@Injectable({ providedIn: 'root' })
export class PhotonService {
  private readonly http = inject(HttpClient);

  /** Minimo de caracteres expuesto para que el componente lo comparta. */
  readonly minCaracteres = MIN_CARACTERES;

  /**
   * Busca direcciones que coincidan con `texto` consultando al backend propio y
   * devuelve las sugerencias ya mapeadas por el servidor. Con menos de
   * {@link MIN_CARACTERES} caracteres significativos, o ante cualquier error de
   * red, emite una lista vacia sin lanzar.
   */
  buscar(texto: string): Observable<DireccionSugerida[]> {
    const termino = this.normalizar(texto);
    if (termino.length < MIN_CARACTERES) {
      return of([]);
    }
    const params = new HttpParams().set('q', termino);
    return this.http.get<DireccionSugerida[]>(GEOCODING_URL, { params }).pipe(
      catchError(() => of([] as DireccionSugerida[])),
    );
  }

  /**
   * Normaliza el termino de busqueda: colapsa espacios en blanco multiples a un
   * solo espacio y recorta los extremos. Conserva los acentos y es tolerante al
   * uso de mayusculas/minusculas (la tolerancia final la aplica el proveedor).
   */
  private normalizar(texto: string): string {
    return texto.replace(/\s+/g, ' ').trim();
  }
}
