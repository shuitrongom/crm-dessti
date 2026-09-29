// =============================================================================
// Dialogo de asignacion de canal de venta a una Oportunidad — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de canal por un MODAL (MatDialog). Permite
// elegir el canal de venta de la Oportunidad (o quitarlo con "Sin canal") de una
// lista de canales activos. Si no hay canales dados de alta, guia al Usuario a
// crearlos (segun permiso). Al guardar hace PUT /oportunidades/{id}/canal-venta y
// cierra devolviendo la Oportunidad actualizada (Req 2.1, 63.1).
// =============================================================================

import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { OportunidadesService } from '../services/oportunidades.service';
import { CanalVenta, Oportunidad } from '../models/comercial.models';

/** Datos de entrada: la Oportunidad, los canales disponibles y el permiso de alta. */
export interface AsignarCanalDialogData {
  oportunidad: Oportunidad;
  canales: CanalVenta[];
  puedeCrearCanal: boolean;
}

@Component({
  selector: 'app-asignar-canal-dialog',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './asignar-canal-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './asignar-canal-dialog.scss',
})
export class AsignarCanalDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(OportunidadesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<AsignarCanalDialog, Oportunidad>);
  protected readonly data = inject<AsignarCanalDialogData>(MAT_DIALOG_DATA);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Canales disponibles para el selector. */
  protected readonly canales = this.data.canales;
  /** `true` si el Usuario puede dar de alta canales (para la guia cuando no hay). */
  protected readonly puedeCrearCanal = this.data.puedeCrearCanal;

  protected readonly form = this.fb.nonNullable.group({
    canalId: [this.data.oportunidad.canalVentaId ?? ''],
  });

  /** Guarda el canal elegido (vacio = quitar) y cierra con la Oportunidad actualizada. */
  protected guardar(): void {
    if (this.guardando()) {
      return;
    }
    this.error.set(null);
    const canalId = this.form.getRawValue().canalId || null;
    this.guardando.set(true);
    this.overlay
      .ejecutar(this.service.asignarCanalVenta(this.data.oportunidad.id, canalId), {
        tipo: 'guardar',
        textoProceso: 'Guardando canal…',
        textoExito: canalId ? 'Canal asignado' : 'Canal retirado',
      })
      .subscribe({
        next: (actualizada) => {
          this.guardando.set(false);
          this.dialogRef.close(actualizada);
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
