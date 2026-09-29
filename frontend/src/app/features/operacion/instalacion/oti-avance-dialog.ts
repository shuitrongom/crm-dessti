// =============================================================================
// Dialogo de registro de avance de OTI (Operacion) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el panel inline de "Registrar avance" por un MODAL (MatDialog).
// Captura nuevos pendientes y evidencias como texto libre (uno por linea o
// separados por comas) y los envia como listas via OtisService.registrarAvance.
// Al menos una lista debe traer un elemento. Al guardar cierra devolviendo `true`.
// =============================================================================

import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { OtisService } from '../services/instalacion.service';
import { OrdenTrabajoInstalacion } from '../models/operacion.models';

/** Datos de entrada: la OTI sobre la que se registra avance, y el nombre del cliente. */
export interface OtiAvanceDialogData {
  oti: OrdenTrabajoInstalacion;
  nombreCliente: string;
}

@Component({
  selector: 'app-oti-avance-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './oti-avance-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './oti-avance-dialog.scss',
})
export class OtiAvanceDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(OtisService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<OtiAvanceDialog, boolean>);
  private readonly data = inject<OtiAvanceDialogData>(MAT_DIALOG_DATA);

  protected readonly oti = this.data.oti;
  protected readonly nombreCliente = this.data.nombreCliente;

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    nuevosPendientes: [''],
    evidencias: [''],
  });

  /** Registra el avance (pendientes/evidencias) de la OTI (Req 19.4). */
  protected guardar(): void {
    this.error.set(null);
    const v = this.form.getRawValue();
    const nuevosPendientes = this.aLista(v.nuevosPendientes);
    const evidencias = this.aLista(v.evidencias);
    if (nuevosPendientes.length === 0 && evidencias.length === 0) {
      this.error.set('Agrega al menos un pendiente o una evidencia.');
      return;
    }
    this.guardando.set(true);
    this.overlay
      .ejecutar(this.service.registrarAvance(this.oti.id, { nuevosPendientes, evidencias }), {
        tipo: 'crear',
        textoProceso: 'Registrando avance…',
        textoExito: 'Avance registrado',
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.dialogRef.close(true);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
  }

  /** Cancela sin registrar. */
  protected cancelar(): void {
    this.dialogRef.close(false);
  }

  /** Convierte texto por lineas o comas en una lista sin vacios. */
  private aLista(texto: string): string[] {
    return texto
      .split(/[\n,]/)
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }
}
