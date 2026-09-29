// =============================================================================
// Dialogo de asignacion de precio a un producto en una Lista de precios (modal)
// -----------------------------------------------------------------------------
// Reemplaza el panel inline por un MODAL (MatDialog). Permite elegir un producto
// (entity-select, sin teclear UUID), capturar su precio y asignarlo/actualizarlo
// en la lista. Muestra los precios ya asignados y permite asignar varios sin
// cerrar. Al cerrar informa si hubo cambios para que el listado recargue. Corrige
// el markup roto del campo de precio del panel inline anterior.
// =============================================================================

import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { CurrencyPipe } from '@angular/common';
import { Observable } from 'rxjs';

import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { PaginaResponse } from '../../../core/models/pagina-response';

import { ListasPreciosService, ProductosService } from '../services/catalogo.service';
import { ListaPrecios, PrecioLista, Producto } from '../models/comercial.models';

/** Datos de entrada: la Lista en la que se asignan precios. */
export interface AsignarPrecioDialogData {
  lista: ListaPrecios;
}

@Component({
  selector: 'app-asignar-precio-dialog',
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
  templateUrl: './asignar-precio-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './asignar-precio-dialog.scss',
})
export class AsignarPrecioDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ListasPreciosService);
  private readonly productos = inject(ProductosService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly toast = inject(NotificacionesService);
  private readonly dialogRef = inject(MatDialogRef<AsignarPrecioDialog, boolean>);
  protected readonly data = inject<AsignarPrecioDialogData>(MAT_DIALOG_DATA);

  /** La Lista en la que se asignan precios. */
  protected readonly lista = this.data.lista;

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Precios ya asignados en la lista (para confirmar visualmente el guardado). */
  protected readonly precios = signal<PrecioLista[]>([]);
  protected readonly cargandoPrecios = signal(true);
  /** `true` si se asigno al menos un precio (para avisar al listado que recargue). */
  private huboCambios = false;

  protected readonly form = this.fb.nonNullable.group({
    productoId: ['', [Validators.required]],
    precio: [
      null as number | null,
      [Validators.required, Validators.min(0.01), Validators.max(999999999.99)],
    ],
  });

  /** Busca Productos por nombre para el selector (nunca UUID a mano). */
  protected readonly buscarProducto = (filtro: string): Observable<PaginaResponse<Producto>> =>
    this.productos.listar(filtro, 0, 20);

  /** Etiqueta principal de un Producto en el selector. */
  protected readonly etiquetaProducto = (producto: Producto): string => producto.nombre;

  /** Detalle secundario (unidad) de un Producto en el selector. */
  protected readonly detalleProducto = (producto: Producto): string | null =>
    producto.unidad || null;

  constructor() {
    this.cargarPrecios();
  }

  /** Carga los precios ya asignados en la lista. */
  private cargarPrecios(): void {
    this.cargandoPrecios.set(true);
    this.service.listarPrecios(this.lista.id).subscribe({
      next: (precios) => {
        this.precios.set(precios);
        this.cargandoPrecios.set(false);
      },
      error: () => {
        this.precios.set([]);
        this.cargandoPrecios.set(false);
      },
    });
  }

  /**
   * Asigna el precio del Producto elegido en la lista (Req 59.3, 59.10). Mantiene
   * el dialogo abierto y refresca los precios para poder asignar varios seguidos.
   */
  protected asignar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.overlay
      .ejecutar(
        this.service.asignarPrecio(this.lista.id, {
          productoId: v.productoId.trim(),
          precio: Number(v.precio),
        }),
        { tipo: 'guardar', textoProceso: 'Asignando precio…', textoExito: 'Precio asignado' },
      )
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.huboCambios = true;
          this.toast.exito('Precio asignado.');
          this.form.reset({ productoId: '', precio: null });
          this.cargarPrecios();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
  }

  /** Cierra el dialogo informando si hubo cambios (para que el listado recargue). */
  protected cerrar(): void {
    this.dialogRef.close(this.huboCambios);
  }
}
