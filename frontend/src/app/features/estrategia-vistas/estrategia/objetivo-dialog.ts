// =============================================================================
// Dialogo de alta de Objetivo estrategico (OKR) (Req 58)
// -----------------------------------------------------------------------------
// Formulario reactivo para crear un Objetivo_Estrategico: nombre, responsable,
// meta y periodo (inicio/fin). El fin debe ser igual o posterior al inicio
// (validador de grupo). Ante error del backend se muestra el mensaje de negocio
// (422 = campo faltante / regla de negocio) sin filtrar detalle tecnico.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { EstrategiaVistasService } from '../services/estrategia-vistas.service';
import { ObjetivoEstrategico } from '../models/estrategia.models';
import { mensajeDeError } from '../../../core/services/error-mensajes';

/**
 * Validador de grupo: el periodo fin no puede ser anterior al inicio. Solo
 * valida cuando ambas fechas estan presentes (los `required` cubren la ausencia).
 */
function periodoCoherente(grupo: AbstractControl): ValidationErrors | null {
  const inicio = grupo.get('periodoInicio')?.value as string;
  const fin = grupo.get('periodoFin')?.value as string;
  if (inicio && fin && fin < inicio) {
    return { periodoInvalido: true };
  }
  return null;
}

@Component({
  selector: 'app-objetivo-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './objetivo-dialog.html',
  styleUrl: './estrategia-dialogs.scss',
})
export class ObjetivoDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(EstrategiaVistasService);
  private readonly dialogRef = inject(MatDialogRef<ObjetivoDialog, ObjetivoEstrategico>);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly formulario = this.fb.nonNullable.group(
    {
      nombre: ['', [Validators.required, Validators.maxLength(200)]],
      responsable: ['', [Validators.required, Validators.maxLength(200)]],
      meta: ['', [Validators.required, Validators.maxLength(500)]],
      periodoInicio: ['', [Validators.required]],
      periodoFin: ['', [Validators.required]],
    },
    { validators: periodoCoherente },
  );

  /** Envia el alta del Objetivo. */
  protected guardar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const v = this.formulario.getRawValue();
    this.service.crearObjetivo(v).subscribe({
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
