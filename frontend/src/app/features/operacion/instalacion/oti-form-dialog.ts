// =============================================================================
// Dialogo de programacion de OTI (Operacion) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de "Programar OTI" por un MODAL (MatDialog)
// consistente con el resto de la plataforma (form shell ds-form*). Captura la
// Orden de Fabricacion terminada, el sitio, la cuadrilla (por identificador; sin
// catalogo backend) y la fecha programada (datepicker es-MX, valor ISO
// YYYY-MM-DD). Al guardar hace POST via OtisService y cierra devolviendo la OTI.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { provideFechaIsoDatepicker } from '../../../shared/date/provide-fecha-iso';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { OtisService } from '../services/instalacion.service';
import { OrdenTrabajoInstalacion } from '../models/operacion.models';

@Component({
  selector: 'app-oti-form-dialog',
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
  templateUrl: './oti-form-dialog.html',
  styleUrl: './oti-form-dialog.scss',
})
export class OtiFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(OtisService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<OtiFormDialog, OrdenTrabajoInstalacion>);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    ordenFabricacionId: ['', [Validators.required]],
    sitioId: ['', [Validators.required]],
    cuadrillaId: ['', [Validators.required]],
    fechaProgramada: ['', [Validators.required]],
  });

  /** Programa una OTI a partir de una Orden de Fabricacion terminada (Req 19.1). */
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
        this.service.programar({
          ordenFabricacionId: v.ordenFabricacionId.trim(),
          sitioId: v.sitioId.trim(),
          cuadrillaId: v.cuadrillaId.trim(),
          fechaProgramada: v.fechaProgramada,
        }),
        {
          tipo: 'crear',
          textoProceso: 'Programando orden de trabajo…',
          textoExito: 'Orden de trabajo programada',
        },
      )
      .subscribe({
        next: (oti) => {
          this.guardando.set(false);
          this.dialogRef.close(oti);
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
