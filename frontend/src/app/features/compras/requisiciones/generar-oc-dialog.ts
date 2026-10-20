// =============================================================================
// Dialogo para GENERAR una Orden de Compra desde una Requisición aprobada
// -----------------------------------------------------------------------------
// Modal premium (MatDialog) que toma una Requisición APROBADA, deja elegir el
// Proveedor por NOMBRE (EntitySelect, sin teclear UUID) y capturar el precio
// unitario de cada partida (los materiales y cantidades provienen de la propia
// requisición). Al confirmar hace POST /compras/requisiciones/{id}/orden-compra y
// cierra devolviendo la Requisición con la Orden generada enlazada.
//
// Reglas (Req 30.3, 30.4): la requisición debe estar aprobada (el backend lo
// reimpone con 422); el proveedor es obligatorio; el precio unitario > 0.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { toSignal } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';

import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { PaginaResponse } from '../../../core/models/pagina-response';

import { ComprasService } from '../services/compras.service';
import { NombresInventarioService } from '../../operacion/services/nombres-inventario.service';
import { Proveedor, RequisicionCompra } from '../models/compras.models';

/** Datos de entrada: la requisición aprobada desde la que se genera la Orden. */
export interface GenerarOcDialogData {
  requisicion: RequisicionCompra;
}

@Component({
  selector: 'app-generar-oc-dialog',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    EntitySelect,
  ],
  templateUrl: './generar-oc-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './generar-oc-dialog.scss',
})
export class GenerarOcDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ComprasService);
  private readonly nombres = inject(NombresInventarioService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly toast = inject(NotificacionesService);
  private readonly dialogRef = inject(MatDialogRef<GenerarOcDialog, RequisicionCompra>);
  private readonly data = inject<GenerarOcDialogData>(MAT_DIALOG_DATA);

  protected readonly requisicion = this.data.requisicion;
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Catálogo de nombres de materiales (para mostrar cada partida por nombre). */
  private readonly nombresListos = toSignal(this.nombres.cargar(), { initialValue: null });

  /** Busca Proveedores activos por nombre o RFC para el selector (sin UUID). */
  protected readonly buscarProveedor = (filtro: string): Observable<PaginaResponse<Proveedor>> =>
    this.service.listarProveedores(filtro || null, 0, 20, 'activo');
  protected readonly etiquetaProveedor = (p: Proveedor): string => p.nombre;
  protected readonly detalleProveedor = (p: Proveedor): string | null => p.rfc;

  protected readonly form = this.fb.nonNullable.group({
    proveedorId: ['', [Validators.required]],
    partidas: this.fb.array(
      this.requisicion.partidas.map((p) =>
        this.fb.nonNullable.group({
          materialId: [p.materialId],
          cantidad: [p.cantidad],
          precioUnitario: [
            0,
            [Validators.required, Validators.min(0.01), Validators.max(999999999.99)],
          ],
        }),
      ),
    ),
  });

  protected get partidas(): FormArray {
    return this.form.get('partidas') as FormArray;
  }

  /** Total estimado en vivo (Σ cantidad × precio unitario) para orientar al usuario. */
  private readonly valores = toSignal(this.partidas.valueChanges, {
    initialValue: this.partidas.getRawValue(),
  });
  protected readonly totalEstimado = computed<number>(() => {
    const filas = (this.valores() ?? []) as { cantidad: number; precioUnitario: number }[];
    return filas.reduce((acc, f) => acc + Number(f.cantidad || 0) * Number(f.precioUnitario || 0), 0);
  });

  /** Nombre del material de una partida (nunca el UUID). */
  protected nombreMaterial(materialId: string): string {
    // Referencia a nombresListos para recomputar cuando el catálogo cargue.
    this.nombresListos();
    return this.nombres.nombreMaterial(materialId);
  }

  /** Genera la Orden de Compra desde la requisición aprobada (Req 30.3). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request = {
      proveedorId: v.proveedorId,
      partidas: (v.partidas as { materialId: string; cantidad: number; precioUnitario: number }[]).map(
        (p) => ({ materialId: p.materialId, cantidad: p.cantidad, precioUnitario: p.precioUnitario }),
      ),
    };
    this.guardando.set(true);
    this.overlay
      .ejecutar(this.service.generarOrdenCompra(this.requisicion.id, request), {
        tipo: 'procesar',
        textoProceso: 'Generando orden de compra…',
        textoExito: 'Orden de compra generada',
      })
      .subscribe({
        next: (requisicion) => {
          this.guardando.set(false);
          this.dialogRef.close(requisicion);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          if (e.status === 422) {
            this.error.set(
              'Solo puede generarse una orden de compra desde una requisición aprobada.',
            );
            return;
          }
          this.error.set(mensajeDeError(e));
        },
      });
  }

  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
