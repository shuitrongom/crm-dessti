// =============================================================================
// Dialogo de alta/edicion de Canal de venta (Comercial) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline por un MODAL (MatDialog) consistente con el
// resto de la plataforma (form shell ds-form*). Sirve para ALTA (sin canal en
// los datos) y EDICION (con canal). Captura nombre y descripcion. Al guardar hace
// POST/PUT via CanalesVentaService y cierra devolviendo el Canal resultante.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { CanalesVentaService } from '../services/catalogo.service';
import { CanalVenta, CanalVentaRequest } from '../models/comercial.models';

/**
 * Datos de entrada del dialogo. `canal` presente => modo EDICION (prellena y hace
 * PUT); ausente => modo ALTA (form vacio y POST).
 */
export interface CanalVentaFormDialogData {
  canal?: CanalVenta;
}

@Component({
  selector: 'app-canal-venta-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './canal-venta-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './canal-venta-form-dialog.scss',
})
export class CanalVentaFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CanalesVentaService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<CanalVentaFormDialog, CanalVenta>);
  private readonly data = inject<CanalVentaFormDialogData>(MAT_DIALOG_DATA);

  /** Canal en edicion (o `null` en alta). */
  private readonly canal = this.data?.canal ?? null;
  /** `true` cuando el dialogo edita un Canal existente. */
  protected readonly esEdicion = !!this.canal;

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly titulo = computed(() =>
    this.esEdicion ? 'Editar canal de venta' : 'Nuevo canal de venta',
  );
  protected readonly subtitulo = computed(() =>
    this.esEdicion
      ? 'Actualiza el nombre y la descripción del canal de venta.'
      : 'Registra una vía comercial (Directo, Referido, Redes, etc.) para clasificar tus ventas.',
  );

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    descripcion: ['', [Validators.maxLength(500)]],
  });

  constructor() {
    if (this.canal) {
      this.form.reset({
        nombre: this.canal.nombre,
        descripcion: this.canal.descripcion ?? '',
      });
    }
  }

  /** Persiste el alta o la edicion segun el modo (Req 63.1). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: CanalVentaRequest = {
      nombre: v.nombre.trim(),
      descripcion: v.descripcion.trim() || null,
    };
    const id = this.canal?.id;
    this.guardando.set(true);
    const peticion = id ? this.service.actualizar(id, request) : this.service.crear(request);
    this.overlay
      .ejecutar(peticion, {
        tipo: id ? 'guardar' : 'crear',
        textoProceso: id ? 'Guardando canal…' : 'Creando canal…',
        textoExito: id ? 'Canal guardado' : 'Canal creado',
      })
      .subscribe({
        next: (canal) => {
          this.guardando.set(false);
          this.dialogRef.close(canal);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          // Nombre duplicado (409): mensaje claro inline en el campo nombre.
          if (e.status === 409) {
            this.form.controls.nombre.setErrors({ duplicado: true });
            this.error.set('Ya existe un canal de venta activo con ese nombre.');
          } else {
            this.error.set(mensajeDeError(e));
          }
        },
      });
  }

  /** Cancela sin guardar. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
