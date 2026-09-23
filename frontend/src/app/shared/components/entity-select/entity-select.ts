// =============================================================================
// Componente EntitySelect: selector de entidad por NOMBRE (Req 5, 6, 59)
// -----------------------------------------------------------------------------
// Autocompletado reutilizable que permite elegir una entidad (Cliente, Producto,
// ...) buscando por su nombre y expone al formulario UNICAMENTE su identificador
// (UUID). El Usuario nunca teclea ni ve un UUID: escribe el nombre, el componente
// consulta el listado paginado del backend con debounce y, al elegir una opcion,
// fija el id de la entidad como valor del control (patron ControlValueAccessor).
//
// Es agnostico de la entidad: recibe una funcion de busqueda que devuelve una
// pagina y dos resolutores (etiqueta principal y secundaria). Asi lo comparten el
// selector de Cliente y el de Producto sin duplicar logica de debounce/estado.
//
// Accesibilidad (Req 57): el campo tiene etiqueta asociada (`<mat-label>`),
// `autocomplete="off"`, panel `role="listbox"` gestionado por MatAutocomplete y
// mensajes de estado (sin coincidencias / seleccion requerida) legibles.
// =============================================================================

import {
  Component,
  computed,
  forwardRef,
  inject,
  input,
  signal,
  DestroyRef,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import {
  ControlValueAccessor,
  NG_VALUE_ACCESSOR,
  NG_VALIDATORS,
  Validator,
  AbstractControl,
  ValidationErrors,
} from '@angular/forms';
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

/** Entidad minima que el selector sabe manejar: al menos id y un nombre resoluble. */
export interface EntidadSeleccionable {
  id: string;
}

/** Funcion que busca entidades por texto y devuelve una pagina (page 0). */
export type BuscadorEntidad<T extends EntidadSeleccionable> = (
  filtro: string,
) => Observable<PaginaResponse<T>>;

/**
 * Selector de entidad por nombre. Implementa ControlValueAccessor para integrarse
 * con formularios reactivos: el valor del control es el `id` (UUID) de la entidad
 * elegida, o `''` cuando no hay seleccion valida. Tambien actua como Validator:
 * si es requerido y no hay entidad elegida, reporta el error `seleccionRequerida`.
 */
@Component({
  selector: 'app-entity-select',
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './entity-select.html',
  styleUrl: './entity-select.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => EntitySelect), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => EntitySelect), multi: true },
  ],
})
export class EntitySelect<T extends EntidadSeleccionable = EntidadSeleccionable>
  implements ControlValueAccessor, Validator
{
  private readonly destroyRef = inject(DestroyRef);

  /** Etiqueta del campo (por ejemplo "Cliente" o "Producto"). */
  readonly etiqueta = input.required<string>();
  /** Texto de ayuda opcional bajo el campo. */
  readonly ayuda = input<string | undefined>(undefined);
  /** Placeholder del campo de busqueda. */
  readonly placeholder = input<string>('Busca por nombre…');
  /** Marca el campo como obligatorio (para la validacion y el asterisco). */
  readonly obligatorio = input<boolean>(false);
  /** Funcion de busqueda que devuelve una pagina de entidades. */
  readonly buscador = input.required<BuscadorEntidad<T>>();
  /** Resuelve la etiqueta principal visible de una entidad (por ejemplo su nombre). */
  readonly etiquetaDe = input.required<(entidad: T) => string>();
  /** Resuelve una etiqueta secundaria opcional (por ejemplo el RFC). */
  readonly detalleDe = input<(entidad: T) => string | null>(() => null);
  /** Minimo de caracteres antes de disparar la busqueda. */
  readonly minCaracteres = input<number>(1);

  /** Texto tecleado en el campo de busqueda. */
  protected readonly texto = signal('');
  /** Entidad actualmente seleccionada (o null). */
  protected readonly seleccionada = signal<T | null>(null);
  /** Indica una busqueda en curso. */
  protected readonly buscando = signal(false);
  /** Marca el campo como tocado para mostrar el error de seleccion requerida. */
  protected readonly tocado = signal(false);
  /** Deshabilita la interaccion (via ControlValueAccessor.setDisabledState). */
  protected readonly deshabilitado = signal(false);

  /** Valor del control (id de la entidad elegida) para reflejar el estado externo. */
  private readonly valorId = signal<string>('');

  private alCambiar: (valor: string) => void = () => {};
  private alTocar: () => void = () => {};

  /** Resultados del autocompletado, reactivos al texto con debounce. */
  protected readonly resultados = toSignal(
    toObservable(this.texto).pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap((q) => {
        const termino = q.trim();
        if (termino.length < this.minCaracteres()) {
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

  /** `true` cuando el termino es suficiente para haber buscado y no hay resultados. */
  protected readonly sinCoincidencias = computed(
    () =>
      this.texto().trim().length >= this.minCaracteres() &&
      !this.buscando() &&
      this.resultados().length === 0,
  );

  /** `true` cuando se debe mostrar el error de seleccion obligatoria. */
  protected readonly errorRequerido = computed(
    () => this.obligatorio() && this.tocado() && this.seleccionada() === null && !this.valorId(),
  );

  // ---------------------------------------------------------------------------
  // Interaccion de la vista
  // ---------------------------------------------------------------------------

  /** Actualiza el texto de busqueda; editar invalida la seleccion previa. */
  protected alEscribir(valor: string): void {
    this.texto.set(valor);
    if (this.seleccionada() !== null) {
      this.seleccionada.set(null);
      this.valorId.set('');
      this.alCambiar('');
    }
  }

  /** Fija la entidad elegida del autocompletado y emite su id al formulario. */
  protected alSeleccionar(evento: MatAutocompleteSelectedEvent): void {
    const entidad = evento.option.value as T;
    this.seleccionada.set(entidad);
    this.valorId.set(entidad.id);
    this.texto.set('');
    this.alCambiar(entidad.id);
    this.marcarTocado();
  }

  /** Limpia la seleccion para elegir otra entidad. */
  protected limpiar(): void {
    this.seleccionada.set(null);
    this.valorId.set('');
    this.texto.set('');
    this.alCambiar('');
    this.marcarTocado();
  }

  /** Marca el control como tocado (para la validacion y el error visible). */
  protected marcarTocado(): void {
    if (!this.tocado()) {
      this.tocado.set(true);
    }
    this.alTocar();
  }

  // ---------------------------------------------------------------------------
  // ControlValueAccessor
  // ---------------------------------------------------------------------------

  writeValue(valor: string | null): void {
    const id = valor ?? '';
    this.valorId.set(id);
    if (!id) {
      this.seleccionada.set(null);
      this.texto.set('');
    }
  }

  registerOnChange(fn: (valor: string) => void): void {
    this.alCambiar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.alTocar = fn;
  }

  setDisabledState(deshabilitado: boolean): void {
    this.deshabilitado.set(deshabilitado);
  }

  // ---------------------------------------------------------------------------
  // Validator
  // ---------------------------------------------------------------------------

  validate(_control: AbstractControl): ValidationErrors | null {
    if (this.obligatorio() && !this.valorId()) {
      return { seleccionRequerida: true };
    }
    return null;
  }
}
