// =============================================================================
// Dialogo de actualizacion de Vigencia de una Suscripcion (super_admin)
// (plan-suscripcion-empresa-super-admin) (Req 8)
// -----------------------------------------------------------------------------
// Permite al super_admin ajustar la vigencia (inicio y fin) de la suscripcion
// vigente de una Empresa. Captura las fechas mediante datepickers ISO
// compartidos (el control emite 'YYYY-MM-DD', exactamente lo que espera el
// backend), con etiquetas en espanol (Req 8.2). Al confirmar hace
// PUT /suscripciones/{id}/vigencia con { vigenciaInicio, vigenciaFin } y:
//   - 200 -> cierra devolviendo la Suscripcion actualizada (Req 8.6).
//   - 422 -> vigencia invalida: muestra el mensaje del backend SIN cerrar el
//            dialogo para que el Usuario lo lea (Req 8.4).
//   - 404 -> suscripcion inexistente: muestra el mensaje del backend (Req 8.5).
// `vigenciaInicio` es OBLIGATORIO (el backend lo marca @NotNull); `vigenciaFin`
// es opcional (null = sin fecha de fin). Accesible (labels, foco, aria) y
// responsive; solo tokens del Sistema de Diseno.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { SuscripcionesService } from '../services/suscripciones.service';
import { Suscripcion } from '../models/plataforma.models';
import { provideFechaIsoDatepicker } from '../../../shared/date/provide-fecha-iso';
import { mensajeDeError } from '../../../core/services/error-mensajes';

/**
 * Datos de entrada del dialogo: la suscripcion cuya vigencia se actualiza, las
 * fechas actuales para preseleccion y el nombre de la Empresa para el encabezado.
 */
export interface VigenciaDialogData {
  suscripcionId: string;
  vigenciaInicioActual?: string | null;
  vigenciaFinActual?: string | null;
  empresaNombre?: string;
}

@Component({
  selector: 'app-vigencia-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
  ],
  providers: [provideFechaIsoDatepicker()],
  templateUrl: './vigencia-dialog.html',
  styleUrl: './vigencia-dialog.scss',
})
export class VigenciaDialog {
  private readonly fb = inject(FormBuilder);
  private readonly suscripcionesService = inject(SuscripcionesService);
  private readonly dialogRef = inject(MatDialogRef<VigenciaDialog, Suscripcion>);
  protected readonly data = inject<VigenciaDialogData>(MAT_DIALOG_DATA);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly formulario = this.fb.nonNullable.group({
    // vigenciaInicio es OBLIGATORIO (backend @NotNull); preselecciona la actual.
    vigenciaInicio: [this.data.vigenciaInicioActual ?? '', [Validators.required]],
    // vigenciaFin es opcional (cadena vacia = sin fecha de fin -> null al enviar).
    vigenciaFin: [this.data.vigenciaFinActual ?? ''],
  });

  /** Confirma la actualizacion contra el backend (PUT /suscripciones/{id}/vigencia). */
  protected guardar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    const { vigenciaInicio, vigenciaFin } = this.formulario.getRawValue();
    this.guardando.set(true);
    this.suscripcionesService
      .actualizarVigencia(this.data.suscripcionId, {
        vigenciaInicio,
        vigenciaFin: vigenciaFin || null,
      })
      .subscribe({
        next: (suscripcion) => {
          this.guardando.set(false);
          this.dialogRef.close(suscripcion);
        },
        error: (e: HttpErrorResponse) => {
          // El backend impone las reglas de vigencia (422) y existencia (404):
          // se muestra su mensaje y el dialogo permanece abierto para leerlo.
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
  }

  /** Cancela la actualizacion de vigencia. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
