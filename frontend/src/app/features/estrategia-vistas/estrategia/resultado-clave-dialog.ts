// =============================================================================
// Dialogo de alta de Resultado clave (Key Result) (Req 58)
// -----------------------------------------------------------------------------
// Formulario reactivo para agregar un Resultado_Clave ponderado a un Objetivo.
// Al guardar, el backend recalcula el avance ponderado del objetivo; el dialogo
// devuelve el Objetivo actualizado. Ante error se muestra el mensaje de negocio.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { EstrategiaVistasService } from '../services/estrategia-vistas.service';
import { ObjetivoEstrategico } from '../models/estrategia.models';
import { mensajeDeError } from '../../../core/services/error-mensajes';

/** Datos de entrada del dialogo: objetivo al que se agrega el resultado clave. */
export interface DatosResultadoClaveDialog {
  objetivoId: string;
  objetivoNombre: string;
}

@Component({
  selector: 'app-resultado-clave-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './resultado-clave-dialog.html',
  styleUrl: './estrategia-dialogs.scss',
})
export class ResultadoClaveDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(EstrategiaVistasService);
  private readonly dialogRef = inject(MatDialogRef<ResultadoClaveDialog, ObjetivoEstrategico>);
  protected readonly datos = inject<DatosResultadoClaveDialog>(MAT_DIALOG_DATA);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly formulario = this.fb.nonNullable.group({
    descripcion: ['', [Validators.required, Validators.maxLength(500)]],
    valorObjetivo: [100, [Validators.required]],
    valorActual: [0, [Validators.required]],
    peso: [1, [Validators.required, Validators.min(0.01)]],
  });

  /** Envia el alta del Resultado clave. */
  protected guardar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const v = this.formulario.getRawValue();
    this.service.agregarResultadoClave(this.datos.objetivoId, v).subscribe({
      next: (objetivo) => {
        this.guardando.set(false);
        this.dialogRef.close(objetivo);
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  /** Cancela el alta. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
