// =============================================================================
// Dialogo de DETALLE de una Orden de Compra (solo lectura) — modal premium
// -----------------------------------------------------------------------------
// Muestra la orden completa: proveedor por nombre/RFC, estado, total y sus
// partidas (material por nombre, cantidad, precio unitario y subtotal). No edita;
// es la vista de consulta que faltaba para no dejar el listado "sin fondo". El
// nombre de proveedor/material se resuelve con NombresComprasService.
// =============================================================================

import { Component, computed, inject, ChangeDetectionStrategy } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { humanizarEstado, tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { NombresComprasService } from '../services/nombres-compras.service';
import { OrdenCompra, PartidaOrdenCompra } from '../models/compras.models';

/** Datos de entrada: la orden a mostrar en detalle. */
export interface OrdenCompraDetalleDialogData {
  orden: OrdenCompra;
}

@Component({
  selector: 'app-orden-compra-detalle-dialog',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './orden-compra-detalle-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './orden-compra-detalle-dialog.scss',
})
export class OrdenCompraDetalleDialog {
  private readonly nombres = inject(NombresComprasService);
  private readonly dialogRef = inject(MatDialogRef<OrdenCompraDetalleDialog>);
  private readonly data = inject<OrdenCompraDetalleDialogData>(MAT_DIALOG_DATA);

  protected readonly orden = this.data.orden;
  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  /** Asegura que el catálogo de nombres esté cargado para resolver materiales/proveedor. */
  private readonly nombresListos = toSignal(this.nombres.cargar(), { initialValue: null });

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'material', encabezado: 'Material' },
    { clave: 'cantidad', encabezado: 'Cantidad', alineacion: 'fin' },
    { clave: 'precio', encabezado: 'Precio unitario', alineacion: 'fin' },
    { clave: 'subtotal', encabezado: 'Subtotal', alineacion: 'fin' },
  ];

  protected readonly nombreProveedor = computed(() => {
    this.nombresListos();
    return this.nombres.nombreProveedor(this.orden.proveedorId);
  });
  protected readonly rfcProveedor = computed(() => {
    this.nombresListos();
    return this.nombres.rfcProveedor(this.orden.proveedorId);
  });

  /** Nombre del material de una partida (nunca el UUID). */
  nombreMaterial(partida: PartidaOrdenCompra): string {
    this.nombresListos();
    return this.nombres.nombreMaterial(partida.materialId);
  }

  cerrar(): void {
    this.dialogRef.close();
  }
}
