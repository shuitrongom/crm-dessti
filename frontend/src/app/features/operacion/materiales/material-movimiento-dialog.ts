// =============================================================================
// Dialogo de registro de movimiento de inventario (Operacion) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de "Registrar movimiento" de la vista de
// Materiales por un MODAL (MatDialog). Captura tipo (entrada/salida/ajuste),
// cantidad y motivo opcional sobre un Material dado. Al guardar hace POST via
// MaterialesService y cierra devolviendo `true` si se registro.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { MaterialesService } from '../services/inventario.service';
import { ETIQUETA_TIPO_MOVIMIENTO, Material, TipoMovimiento } from '../models/operacion.models';

/** Datos de entrada: el Material sobre el que se registra el movimiento (obligatorio). */
export interface MaterialMovimientoDialogData {
  material: Material;
}

@Component({
  selector: 'app-material-movimiento-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './material-movimiento-dialog.html',
  styleUrl: './material-movimiento-dialog.scss',
})
export class MaterialMovimientoDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MaterialesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<MaterialMovimientoDialog, boolean>);
  private readonly data = inject<MaterialMovimientoDialogData>(MAT_DIALOG_DATA);

  protected readonly material = this.data.material;
  protected readonly etiquetaTipo = ETIQUETA_TIPO_MOVIMIENTO;
  protected readonly tipos: TipoMovimiento[] = ['entrada', 'salida', 'ajuste'];

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly subtitulo = computed(
    () => `Existencias actuales: ${this.material.existencias} ${this.material.unidadMedida}`,
  );

  protected readonly form = this.fb.nonNullable.group({
    tipo: ['entrada' as TipoMovimiento, [Validators.required]],
    cantidad: [1, [Validators.required]],
    motivo: ['', [Validators.maxLength(500)]],
  });

  /** Registra el movimiento sobre el Material (Req 18.2). */
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
        this.service.registrarMovimiento(this.material.id, {
          tipo: v.tipo,
          cantidad: Number(v.cantidad),
          motivo: v.motivo.trim() || null,
        }),
        {
          tipo: 'crear',
          textoProceso: 'Registrando movimiento…',
          textoExito: 'Movimiento registrado',
        },
      )
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
}
