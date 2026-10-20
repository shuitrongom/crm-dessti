// =============================================================================
// Diálogo de alta de Factura de proveedor (Compras, Req 33) — modal premium
// -----------------------------------------------------------------------------
// Reemplaza el "teclea el UUID de la orden" por un MODAL premium: se elige la
// Orden_Compra por NOMBRE (folio + proveedor, EntitySelect) y se captura el folio
// del proveedor y el monto. Al elegir la orden se muestra el proveedor derivado
// (por nombre) y el total de la orden como referencia para cotejar el monto. Al
// guardar hace POST vía ComprasService y cierra devolviendo la Factura creada. El
// backend deriva el proveedor de la orden, valida folio/monto (422) y que la orden
// exista (404); la factura nace en estado "registrada".
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Observable, map } from 'rxjs';

import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { PaginaResponse } from '../../../core/models/pagina-response';

import { ComprasService } from '../services/compras.service';
import { NombresComprasService } from '../services/nombres-compras.service';
import { FacturaProveedor, OrdenCompra } from '../models/compras.models';

@Component({
  selector: 'app-factura-proveedor-form-dialog',
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
  templateUrl: './factura-proveedor-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './factura-proveedor-form-dialog.scss',
})
export class FacturaProveedorFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ComprasService);
  private readonly nombres = inject(NombresComprasService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<FacturaProveedorFormDialog, FacturaProveedor>);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Orden elegida (para mostrar proveedor por nombre y total de referencia). */
  protected readonly orden = signal<OrdenCompra | null>(null);

  /** Asegura el catálogo de nombres (proveedores + materiales) para las etiquetas. */
  private readonly nombresListos = toSignal(this.nombres.cargar(), { initialValue: null });

  /** Buscador de órdenes de compra por folio o proveedor. */
  protected readonly buscarOrden = (filtro: string): Observable<PaginaResponse<OrdenCompra>> =>
    this.service.listarOrdenesCompra(null, null, 0, 20).pipe(
      map((pagina) => {
        const texto = (filtro || '').trim().toLowerCase();
        const content = texto
          ? pagina.content.filter(
              (o) =>
                o.id.toLowerCase().includes(texto) ||
                this.nombres.nombreProveedor(o.proveedorId).toLowerCase().includes(texto),
            )
          : pagina.content;
        return { ...pagina, content };
      }),
    );
  protected readonly etiquetaOrden = (o: OrdenCompra): string =>
    `OC ${o.id.slice(0, 8)} · ${this.nombres.nombreProveedor(o.proveedorId)}`;
  protected readonly detalleOrden = (o: OrdenCompra): string | null =>
    this.nombres.rfcProveedor(o.proveedorId) || null;

  protected readonly form = this.fb.nonNullable.group({
    ordenCompraId: ['', [Validators.required]],
    folioProveedor: ['', [Validators.required, Validators.maxLength(100)]],
    monto: [0.01, [Validators.required, Validators.min(0.01)]],
  });

  /** Proveedor de la orden elegida (por nombre; nunca UUID). */
  protected readonly proveedorOrden = computed<string>(() => {
    this.nombresListos();
    const orden = this.orden();
    return orden ? this.nombres.nombreProveedor(orden.proveedorId) : '';
  });

  constructor() {
    // Al elegir la orden, carga su detalle para mostrar proveedor y total.
    this.form.controls.ordenCompraId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((id) => this.alElegirOrden(id || null));
  }

  private alElegirOrden(ordenCompraId: string | null): void {
    this.error.set(null);
    this.orden.set(null);
    if (!ordenCompraId) {
      return;
    }
    this.service.consultarOrdenCompra(ordenCompraId).subscribe({
      next: (orden) => this.orden.set(orden),
      // Silencioso: la orden vino del buscador; si falla la consulta el POST lo
      // reportará. Se conserva el id para no bloquear el alta.
      error: () => this.orden.set(null),
    });
  }

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
        this.service.registrarFacturaProveedor({
          ordenCompraId: v.ordenCompraId,
          folioProveedor: v.folioProveedor,
          monto: v.monto,
        }),
        { tipo: 'crear', textoProceso: 'Registrando factura…', textoExito: 'Factura registrada' },
      )
      .subscribe({
        next: (factura) => {
          this.guardando.set(false);
          this.dialogRef.close(factura);
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
