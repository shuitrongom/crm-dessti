// =============================================================================
// Componente SearchAutocomplete: buscador PREMIUM con sugerencias en vivo
// -----------------------------------------------------------------------------
// Buscador reutilizable para FILTRAR listados (no para seleccionar y enlazar un
// UUID como EntitySelect). Mientras el Usuario escribe, muestra un panel de
// sugerencias (autocompletado) con las coincidencias por cualquier criterio del
// backend (p. ej. nombre O RFC de un proveedor); al elegir una sugerencia, se
// emite tanto el texto elegido (para filtrar) como la entidad seleccionada.
//
// A diferencia de EntitySelect:
//   - No implementa ControlValueAccessor: no fija un id en un formulario.
//   - Emite `terminoChange` EN VIVO con debounce (para filtrar la tabla al vuelo)
//     y `seleccion` cuando el Usuario elige una sugerencia concreta.
//   - Es agnostico de la entidad: recibe un `buscador`, y resolutores de etiqueta
//     principal y detalle. Resalta la coincidencia del término en la etiqueta.
//
// Accesibilidad (Req 57): campo con `<mat-label>`, `autocomplete="off"`, panel
// `role="listbox"` gestionado por MatAutocomplete, botón de limpiar etiquetado y
// estado "sin resultados" legible.
// =============================================================================

import {
  Component,
  computed,
  inject,
  input,
  output,
  signal,
  DestroyRef,
  ChangeDetectionStrategy,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { Observable, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { PaginaResponse } from '../../../core/models/pagina-response';

/** Entidad mínima que el buscador sabe sugerir: al menos un id estable. */
export interface EntidadBuscable {
  id: string;
}

/** Función que busca entidades por texto y devuelve una página (page 0). */
export type BuscadorSugerencias<T extends EntidadBuscable> = (
  filtro: string,
) => Observable<PaginaResponse<T>>;

/** Segmento de una etiqueta para resaltar la coincidencia del término. */
interface SegmentoResaltado {
  texto: string;
  match: boolean;
}

@Component({
  selector: 'app-search-autocomplete',
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './search-autocomplete.html',
  styleUrl: './search-autocomplete.scss',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class SearchAutocomplete<T extends EntidadBuscable = EntidadBuscable> {
  private readonly destroyRef = inject(DestroyRef);

  /** Etiqueta del campo (por ejemplo "Buscar por nombre o RFC"). */
  readonly etiqueta = input.required<string>();
  /** Placeholder del campo. */
  readonly placeholder = input<string>('Escribe para filtrar…');
  /** Texto de ayuda opcional bajo el campo. */
  readonly ayuda = input<string | undefined>(undefined);
  /** Función de búsqueda que devuelve una página de entidades sugeridas. */
  readonly buscador = input.required<BuscadorSugerencias<T>>();
  /** Resuelve la etiqueta principal visible de una sugerencia (p. ej. su nombre). */
  readonly etiquetaDe = input.required<(entidad: T) => string>();
  /** Resuelve una etiqueta secundaria opcional (p. ej. el RFC). */
  readonly detalleDe = input<(entidad: T) => string | null>(() => null);
  /** Ícono opcional (Material Symbols) de cada sugerencia. */
  readonly iconoDe = input<(entidad: T) => string>(() => 'chevron_right');
  /** Mínimo de caracteres antes de sugerir. */
  readonly minCaracteres = input<number>(1);
  /**
   * Si es `true`, al enfocar el campo (sin teclear) ya muestra las primeras
   * sugerencias (búsqueda con término vacío). Útil cuando el Usuario no sabe qué
   * hay dado de alta.
   */
  readonly precargar = input<boolean>(true);
  /** Debounce (ms) de la emisión del término y de la búsqueda de sugerencias. */
  readonly debounce = input<number>(300);
  /** Valor inicial del término (para reflejar un filtro externo). */
  readonly terminoInicial = input<string>('');

  /** Emite el término EN VIVO (con debounce) para filtrar el listado externo. */
  readonly terminoChange = output<string>();
  /** Emite la entidad elegida cuando el Usuario selecciona una sugerencia. */
  readonly seleccion = output<T>();

  /** Texto tecleado en el campo. */
  protected readonly texto = signal('');
  /** Marca el foco (para disparar la precarga con término vacío). */
  protected readonly enfocado = signal(false);
  /** Indica una búsqueda de sugerencias en curso. */
  protected readonly buscando = signal(false);

  constructor() {
    // Inicializa el texto con el término externo (si lo hay) sin emitir.
    this.texto.set(this.terminoInicial());

    // Emisión EN VIVO del término (con debounce) para filtrar el listado externo.
    toObservable(this.texto)
      .pipe(debounceTime(this.debounce()), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((t) => this.terminoChange.emit(t.trim()));
  }

  /** Disparador de sugerencias: combina texto y foco (para la precarga). */
  private readonly consulta = computed(() => ({
    termino: this.texto().trim(),
    enfocado: this.enfocado(),
  }));

  /** Sugerencias del autocompletado, reactivas al texto/foco con debounce. */
  protected readonly sugerencias = toSignal(
    toObservable(this.consulta).pipe(
      debounceTime(250),
      distinctUntilChanged((a, b) => a.termino === b.termino && a.enfocado === b.enfocado),
      switchMap(({ termino, enfocado }) => {
        const precargando = this.precargar() && enfocado && termino.length === 0;
        if (!precargando && termino.length < this.minCaracteres()) {
          this.buscando.set(false);
          return of([] as T[]);
        }
        this.buscando.set(true);
        return this.buscador()(termino).pipe(
          switchMap((pagina) => {
            this.buscando.set(false);
            return of(pagina.content);
          }),
          catchError(() => {
            this.buscando.set(false);
            return of([] as T[]);
          }),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ),
    { initialValue: [] as T[] },
  );

  /** `true` cuando ya se buscó y no hay coincidencias. */
  protected readonly sinCoincidencias = computed(
    () =>
      this.texto().trim().length >= this.minCaracteres() &&
      !this.buscando() &&
      this.sugerencias().length === 0,
  );

  /** `true` si hay texto (para mostrar el botón de limpiar). */
  protected readonly hayTexto = computed(() => this.texto().trim().length > 0);

  /**
   * Parte la etiqueta de una entidad en segmentos para resaltar la coincidencia
   * del término (sin distinguir mayúsculas). Si no hay término o no coincide,
   * devuelve un único segmento sin resaltar.
   */
  protected segmentos(entidad: T): SegmentoResaltado[] {
    const etiqueta = this.etiquetaDe()(entidad);
    const termino = this.texto().trim();
    if (!termino) {
      return [{ texto: etiqueta, match: false }];
    }
    const idx = etiqueta.toLowerCase().indexOf(termino.toLowerCase());
    if (idx < 0) {
      return [{ texto: etiqueta, match: false }];
    }
    const segmentos: SegmentoResaltado[] = [];
    if (idx > 0) {
      segmentos.push({ texto: etiqueta.slice(0, idx), match: false });
    }
    segmentos.push({ texto: etiqueta.slice(idx, idx + termino.length), match: true });
    if (idx + termino.length < etiqueta.length) {
      segmentos.push({ texto: etiqueta.slice(idx + termino.length), match: false });
    }
    return segmentos;
  }

  /** Actualiza el texto tecleado (emite el término vía el flujo con debounce). */
  protected alEscribir(valor: string): void {
    this.texto.set(valor);
  }

  /** Marca el campo como enfocado (dispara la precarga de sugerencias si aplica). */
  protected alEnfocar(): void {
    this.enfocado.set(true);
  }

  /** Quita el foco. */
  protected alDesenfocar(): void {
    this.enfocado.set(false);
  }

  /**
   * Al elegir una sugerencia: fija su etiqueta como texto (filtra por ese valor) y
   * emite la entidad seleccionada para que el host reaccione (p. ej. filtre a ese
   * proveedor concreto).
   */
  protected alSeleccionar(evento: MatAutocompleteSelectedEvent): void {
    const entidad = evento.option.value as T;
    this.texto.set(this.etiquetaDe()(entidad));
    this.enfocado.set(false);
    this.seleccion.emit(entidad);
  }

  /** Limpia el texto y notifica el término vacío. */
  protected limpiar(): void {
    this.texto.set('');
  }
}
