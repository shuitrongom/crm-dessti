// =============================================================================
// Dialogo de alta/edicion de Almacen (Inventario avanzado) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de la pestaña Almacenes por un MODAL (MatDialog)
// consistente con el resto de la plataforma (form shell ds-form*). Sirve para
// ALTA (sin almacen en los datos) y EDICION (con almacen). Captura nombre y tipo
// (sucursal/bodega). Al guardar hace POST/PUT via InventarioAvanzadoService y
// cierra devolviendo el Almacen resultante.
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

import { InventarioAvanzadoService } from '../services/inventario.service';
import { Almacen } from '../models/operacion.models';

/**
 * Datos de entrada del dialogo. `almacen` presente => modo EDICION (prellena y
 * hace PUT); ausente => modo ALTA (form vacio y POST).
 */
export interface AlmacenFormDialogData {
  almacen?: Almacen;
}

@Component({
  selector: 'app-almacen-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './almacen-form-dialog.html',
  styleUrl: './almacen-form-dialog.scss',
})
export class AlmacenFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(InventarioAvanzadoService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<AlmacenFormDialog, Almacen>);
  private readonly data = inject<AlmacenFormDialogData>(MAT_DIALOG_DATA);

  /** Almacen en edicion (o `null` en alta). */
  private readonly almacen = this.data?.almacen ?? null;
  /** `true` cuando el dialogo edita un Almacen existente. */
  protected readonly esEdicion = !!this.almacen;

  protected readonly tipos: { valor: string; etiqueta: string }[] = [
    { valor: 'sucursal', etiqueta: 'Sucursal' },
    { valor: 'bodega', etiqueta: 'Bodega' },
  ];

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly titulo = computed(() =>
    this.esEdicion ? 'Editar almacén' : 'Nuevo almacén',
  );
  protected readonly subtitulo = computed(() =>
    this.esEdicion
      ? 'Actualiza el nombre y el tipo del almacén.'
      : 'Registra un almacén (sucursal o bodega) para operar el inventario avanzado.',
  );

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    tipo: ['sucursal', [Validators.required]],
  });

  constructor() {
    if (this.almacen) {
      this.form.reset({ nombre: this.almacen.nombre, tipo: this.almacen.tipo });
    }
  }

  /** Persiste el alta o la edicion segun el modo (Req 60). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request = { nombre: v.nombre.trim(), tipo: v.tipo };
    const id = this.almacen?.id;
    this.guardando.set(true);
    const peticion = id
      ? this.service.actualizarAlmacen(id, request)
      : this.service.crearAlmacen(request);
    this.overlay
      .ejecutar(peticion, {
        tipo: id ? 'guardar' : 'crear',
        textoProceso: id ? 'Guardando almacén…' : 'Creando almacén…',
        textoExito: id ? 'Almacén guardado' : 'Almacén creado',
      })
      .subscribe({
        next: (almacen) => {
          this.guardando.set(false);
          this.dialogRef.close(almacen);
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
