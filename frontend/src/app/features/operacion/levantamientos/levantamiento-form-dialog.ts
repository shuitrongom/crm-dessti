// =============================================================================
// Dialogo de alta de Levantamiento de Sitio (Operacion) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de la vista de Levantamientos por un MODAL
// (MatDialog) consistente con el resto de la plataforma (form shell ds-form*).
// Captura mediciones, tipo de superficie y condiciones electricas (obligatorios)
// mas los vinculos opcionales a sitio/cotizacion/orden de fabricacion. Al guardar
// hace POST via LevantamientosService y cierra devolviendo el Levantamiento.
// =============================================================================

import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { LevantamientosService } from '../services/instalacion.service';
import { LevantamientoSitio } from '../models/operacion.models';

@Component({
  selector: 'app-levantamiento-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './levantamiento-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './levantamiento-form-dialog.scss',
})
export class LevantamientoFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(LevantamientosService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<LevantamientoFormDialog, LevantamientoSitio>);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    mediciones: ['', [Validators.required]],
    tipoSuperficie: ['', [Validators.required, Validators.maxLength(200)]],
    condicionesElectricas: ['', [Validators.required]],
    sitioId: [''],
    cotizacionId: [''],
    ordenFabricacionId: [''],
  });

  /** Persiste el alta del Levantamiento (Req 16.1). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.overlay
      .ejecutar(
        this.service.crear({
          mediciones: v.mediciones.trim(),
          tipoSuperficie: v.tipoSuperficie.trim(),
          condicionesElectricas: v.condicionesElectricas.trim(),
          sitioId: v.sitioId.trim() || null,
          cotizacionId: v.cotizacionId.trim() || null,
          ordenFabricacionId: v.ordenFabricacionId.trim() || null,
        }),
        {
          tipo: 'crear',
          textoProceso: 'Creando levantamiento…',
          textoExito: 'Levantamiento creado',
        },
      )
      .subscribe({
        next: (levantamiento) => {
          this.guardando.set(false);
          this.dialogRef.close(levantamiento);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
  }

  /** Cancela sin guardar. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
