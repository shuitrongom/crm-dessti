// =============================================================================
// Dialogo de alta/edicion de Material (Operacion) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de la vista de Materiales por un MODAL
// (MatDialog) consistente con el resto de la plataforma (form shell ds-form*).
// Sirve para ALTA (sin material en los datos) y EDICION (con material). Captura
// nombre, unidad de medida y stock minimo. Al guardar hace POST/PUT via
// MaterialesService y cierra devolviendo el Material resultante.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { MaterialesService } from '../services/inventario.service';
import { Material, MaterialRequest } from '../models/operacion.models';

/**
 * Datos de entrada del dialogo. `material` presente => modo EDICION (prellena y
 * hace PUT); ausente => modo ALTA (form vacio y POST).
 */
export interface MaterialFormDialogData {
  material?: Material;
}

@Component({
  selector: 'app-material-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './material-form-dialog.html',
  styleUrl: './material-form-dialog.scss',
})
export class MaterialFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MaterialesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<MaterialFormDialog, Material>);
  private readonly data = inject<MaterialFormDialogData>(MAT_DIALOG_DATA);

  /** Material en edicion (o `null` en alta). */
  private readonly material = this.data?.material ?? null;
  /** `true` cuando el dialogo edita un Material existente. */
  protected readonly esEdicion = !!this.material;

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly titulo = computed(() =>
    this.esEdicion ? 'Editar material' : 'Nuevo material',
  );
  protected readonly subtitulo = computed(() =>
    this.esEdicion
      ? 'Actualiza el nombre, la unidad de medida y el stock mínimo del material.'
      : 'Registra un insumo del inventario. Las existencias inician en 0 y se ajustan con movimientos.',
  );

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    unidadMedida: ['', [Validators.required, Validators.maxLength(50)]],
    stockMinimo: [0, [Validators.required, Validators.min(0)]],
  });

  constructor() {
    if (this.material) {
      this.form.reset({
        nombre: this.material.nombre,
        unidadMedida: this.material.unidadMedida,
        stockMinimo: this.material.stockMinimo,
      });
    }
  }

  /** Persiste el alta o la edicion segun el modo (Req 18.1). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: MaterialRequest = {
      nombre: v.nombre.trim(),
      unidadMedida: v.unidadMedida.trim(),
      stockMinimo: Number(v.stockMinimo),
    };
    const id = this.material?.id;
    this.guardando.set(true);
    const peticion = id ? this.service.editar(id, request) : this.service.crear(request);
    this.overlay
      .ejecutar(peticion, {
        tipo: id ? 'guardar' : 'crear',
        textoProceso: id ? 'Guardando material…' : 'Creando material…',
        textoExito: id ? 'Material guardado' : 'Material creado',
      })
      .subscribe({
        next: (material) => {
          this.guardando.set(false);
          this.dialogRef.close(material);
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
