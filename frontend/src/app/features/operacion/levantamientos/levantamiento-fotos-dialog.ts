// =============================================================================
// Dialogo de fotos de un Levantamiento (Operacion) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el panel inline de fotos de la vista de Levantamientos por un MODAL
// (MatDialog). Muestra la galeria actual y permite adjuntar una o mas fotos por
// referencia (URL o clave de objeto). Se usa tanto desde el listado como desde el
// detalle. Al cerrar devuelve `true` si se adjunto al menos una foto (para que el
// origen recargue).
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { StateContainer } from '../../../shared/components/state-container/state-container';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { LevantamientosService } from '../services/instalacion.service';
import { LevantamientoFoto, LevantamientoSitio } from '../models/operacion.models';

/** Datos de entrada: el Levantamiento cuyas fotos se gestionan (obligatorio). */
export interface LevantamientoFotosDialogData {
  levantamiento: LevantamientoSitio;
  /** Cuando es `false` oculta el formulario de alta (solo lectura de galeria). */
  puedeAgregar?: boolean;
}

@Component({
  selector: 'app-levantamiento-fotos-dialog',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    StateContainer,
  ],
  templateUrl: './levantamiento-fotos-dialog.html',
  styleUrl: './levantamiento-fotos-dialog.scss',
})
export class LevantamientoFotosDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(LevantamientosService);
  private readonly dialogRef = inject(MatDialogRef<LevantamientoFotosDialog, boolean>);
  private readonly data = inject<LevantamientoFotosDialogData>(MAT_DIALOG_DATA);

  protected readonly levantamiento = this.data.levantamiento;
  protected readonly puedeAgregar = this.data.puedeAgregar ?? true;

  protected readonly fotosFase = signal<FaseSolicitud>('cargando');
  protected readonly fotos = signal<LevantamientoFoto[]>([]);
  protected readonly adjuntando = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Marca si a lo largo del ciclo del modal se adjunto al menos una foto. */
  private adjuntoAlgo = false;

  protected readonly fotosForm = this.fb.group({
    referencias: this.fb.array<FormControl<string>>([this.nuevaReferencia()]),
  });

  constructor() {
    this.cargarFotos();
  }

  /** Crea un control de referencia de foto (obligatorio, sin espacios). */
  private nuevaReferencia(): FormControl<string> {
    return this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(500)]);
  }

  /** Acceso tipado al arreglo de controles de referencia. */
  protected get referencias(): FormArray<FormControl<string>> {
    return this.fotosForm.controls.referencias;
  }

  /** Agrega un nuevo campo de referencia para adjuntar varias fotos a la vez. */
  protected agregarCampoReferencia(): void {
    this.referencias.push(this.nuevaReferencia());
  }

  /** Elimina un campo de referencia; conserva al menos uno. */
  protected quitarCampoReferencia(indice: number): void {
    if (this.referencias.length > 1) {
      this.referencias.removeAt(indice);
    }
  }

  /** Carga (o recarga) la galeria de fotos del levantamiento (Req 12.1). */
  protected cargarFotos(): void {
    this.fotosFase.set('cargando');
    this.service.fotosDe(this.levantamiento.id).subscribe({
      next: (fotos) => {
        this.fotos.set(fotos);
        this.fotosFase.set(fotos.length === 0 ? 'vacio' : 'ok');
      },
      error: () => this.fotosFase.set('error'),
    });
  }

  private reiniciarFormulario(): void {
    this.fotosForm.setControl('referencias', this.fb.array([this.nuevaReferencia()]));
  }

  /**
   * Adjunta las referencias capturadas al Levantamiento (Req 12.1). Sin
   * referencias validas no llama al backend y muestra validacion es-MX (Req 12.2).
   */
  protected agregarFotos(): void {
    const referencias = this.referencias.controls
      .map((c) => c.value.trim())
      .filter((r) => r.length > 0);
    if (referencias.length === 0) {
      this.referencias.markAllAsTouched();
      this.error.set('Agrega al menos una referencia de foto.');
      return;
    }
    this.error.set(null);
    this.adjuntando.set(true);
    this.service.agregarFotos(this.levantamiento.id, referencias).subscribe({
      next: (fotos) => {
        this.adjuntando.set(false);
        this.adjuntoAlgo = true;
        this.fotos.set(fotos);
        this.fotosFase.set(fotos.length === 0 ? 'vacio' : 'ok');
        this.reiniciarFormulario();
      },
      error: (e: HttpErrorResponse) => {
        this.adjuntando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  /** Cierra el modal devolviendo si hubo cambios en la galeria. */
  protected cerrar(): void {
    this.dialogRef.close(this.adjuntoAlgo);
  }
}
