// =============================================================================
// Dialogo de alta de Orden de Compra (Compras) — modal premium animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline por un MODAL (MatDialog). Elige el Proveedor por
// NOMBRE/RFC (EntitySelect) y captura partidas (Material por nombre + cantidad +
// precio unitario), con total estimado en vivo. Al guardar hace POST vía
// ComprasService y cierra devolviendo la Orden creada. El backend valida que el
// proveedor y los materiales existan (404) y los rangos (422).
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe } from '@angular/common';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Observable } from 'rxjs';

import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { PaginaResponse } from '../../../core/models/pagina-response';

import { ComprasService } from '../services/compras.service';
import { MaterialesService } from '../../operacion/services/inventario.service';
import { Material } from '../../operacion/models/operacion.models';
import { OrdenCompra, Proveedor } from '../models/compras.models';

@Component({
  selector: 'app-orden-compra-form-dialog',
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
  templateUrl: './orden-compra-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './orden-compra-form-dialog.scss',
})
export class OrdenCompraFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ComprasService);
  private readonly materiales = inject(MaterialesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<OrdenCompraFormDialog, OrdenCompra>);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly buscarProveedor = (filtro: string): Observable<PaginaResponse<Proveedor>> =>
    this.service.listarProveedores(filtro || null, 0, 20, 'activo');
  protected readonly etiquetaProveedor = (p: Proveedor): string => p.nombre;
  protected readonly detalleProveedor = (p: Proveedor): string | null => p.rfc || null;

  protected readonly buscarMaterial = (filtro: string): Observable<PaginaResponse<Material>> =>
    this.materiales.listar(filtro, 'activo', false, 0, 20);
  protected readonly etiquetaMaterial = (m: Material): string => m.nombre;
  protected readonly detalleMaterial = (m: Material): string | null => m.unidadMedida || null;

  protected readonly form = this.fb.nonNullable.group({
    proveedorId: ['', [Validators.required]],
    partidas: this.fb.array([this.crearPartida()]),
  });

  protected get partidas(): FormArray {
    return this.form.get('partidas') as FormArray;
  }

  /** Total estimado en vivo (Σ cantidad × precio unitario). */
  private readonly valores = toSignal(this.partidas.valueChanges, {
    initialValue: this.partidas.getRawValue(),
  });
  protected readonly totalEstimado = computed<number>(() => {
    const filas = (this.valores() ?? []) as { cantidad: number; precioUnitario: number }[];
    return filas.reduce((acc, f) => acc + Number(f.cantidad || 0) * Number(f.precioUnitario || 0), 0);
  });

  private crearPartida() {
    return this.fb.nonNullable.group({
      materialId: ['', [Validators.required]],
      cantidad: [1, [Validators.required, Validators.min(1), Validators.max(999999)]],
      precioUnitario: [
        0.01,
        [Validators.required, Validators.min(0.01), Validators.max(999999999.99)],
      ],
    });
  }

  protected agregarPartida(): void {
    this.partidas.push(this.crearPartida());
  }

  protected quitarPartida(indice: number): void {
    if (this.partidas.length > 1) {
      this.partidas.removeAt(indice);
    }
  }

  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request = {
      proveedorId: v.proveedorId,
      partidas: v.partidas as { materialId: string; cantidad: number; precioUnitario: number }[],
    };
    this.guardando.set(true);
    this.overlay
      .ejecutar(this.service.crearOrdenCompra(request), {
        tipo: 'crear',
        textoProceso: 'Creando orden de compra…',
        textoExito: 'Orden de compra creada',
      })
      .subscribe({
        next: (orden) => {
          this.guardando.set(false);
          this.dialogRef.close(orden);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
  }

  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
