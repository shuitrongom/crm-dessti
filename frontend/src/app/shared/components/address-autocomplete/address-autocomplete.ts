// =============================================================================
// Componente AddressAutocomplete: buscador de direcciones (Photon/OpenStreetMap)
// -----------------------------------------------------------------------------
// Campo de busqueda reutilizable que, mientras el Usuario teclea, consulta
// Photon (servicio publico y gratuito de OpenStreetMap, sin clave) y lista
// sugerencias de direccion. Al elegir una, EMITE los campos ya mapeados
// (calle, ciudad, estado, cp, pais) mediante el output `direccionSeleccionada`;
// el componente NO posee el formulario: cada padre escucha el evento y hace
// `patchValue` sobre sus propios controles, que siguen siendo editables (el
// autocompletado es una ayuda, no reemplaza la captura manual).
//
// Rendimiento y cortesia con el servicio publico: la busqueda se debounce
// (~350 ms) y solo se dispara con al menos 3 caracteres significativos; los
// errores/vacios se manejan sin romper la escritura (no hay sugerencias, y ya).
//
// Accesibilidad (Req 57): etiqueta asociada (`<mat-label>`), navegacion por
// teclado gestionada por MatAutocomplete (panel `role="listbox"`), texto de
// ayuda descriptivo y estados de carga/sin resultados legibles.
// =============================================================================

import { Component, DestroyRef, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { computed } from '@angular/core';
import { debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { DireccionSugerida, PhotonService } from './photon.service';

/** Retardo (ms) antes de consultar a Photon tras dejar de teclear. */
const DEBOUNCE_MS = 350;

/** Campos de direccion emitidos al elegir una sugerencia. */
export interface DireccionAutocompletada {
  calle: string;
  ciudad: string;
  estado: string;
  cp: string;
  pais: string;
}

@Component({
  selector: 'app-address-autocomplete',
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './address-autocomplete.html',
  styleUrl: './address-autocomplete.scss',
})
export class AddressAutocomplete {
  private readonly photon = inject(PhotonService);
  private readonly destroyRef = inject(DestroyRef);

  /** Emite los campos mapeados de la direccion elegida (padre hace patchValue). */
  readonly direccionSeleccionada = output<DireccionAutocompletada>();

  /** Texto tecleado en el campo de busqueda. */
  protected readonly texto = signal('');
  /** Indica una busqueda en curso (spinner y opcion "Buscando…"). */
  protected readonly buscando = signal(false);

  /** Sugerencias reactivas al texto, con debounce y minimo de caracteres. */
  protected readonly resultados = toSignal(
    toObservable(this.texto).pipe(
      debounceTime(DEBOUNCE_MS),
      distinctUntilChanged(),
      switchMap((q) => {
        const termino = q.trim();
        if (termino.length < this.photon.minCaracteres) {
          this.buscando.set(false);
          return of([] as DireccionSugerida[]);
        }
        this.buscando.set(true);
        return this.photon.buscar(termino).pipe(
          switchMap((sugerencias) => {
            this.buscando.set(false);
            return of(sugerencias);
          }),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ),
    { initialValue: [] as DireccionSugerida[] },
  );

  /** `true` cuando el termino es suficiente, no hay busqueda en curso y no hubo resultados. */
  protected readonly sinResultados = computed(
    () =>
      this.texto().trim().length >= this.photon.minCaracteres &&
      !this.buscando() &&
      this.resultados().length === 0,
  );

  /** Actualiza el texto de busqueda conforme el Usuario teclea. */
  protected alEscribir(valor: string): void {
    this.texto.set(valor);
  }

  /** Muestra la etiqueta legible de la sugerencia en el campo tras elegirla. */
  protected mostrar(sugerencia: DireccionSugerida | null): string {
    return sugerencia ? sugerencia.etiqueta : '';
  }

  /** Al elegir una sugerencia, emite sus campos mapeados hacia el formulario padre. */
  protected alSeleccionar(evento: MatAutocompleteSelectedEvent): void {
    const s = evento.option.value as DireccionSugerida;
    this.direccionSeleccionada.emit({
      calle: s.calle,
      ciudad: s.ciudad,
      estado: s.estado,
      cp: s.cp,
      pais: s.pais,
    });
  }
}
