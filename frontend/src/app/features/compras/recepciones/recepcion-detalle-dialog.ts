// =============================================================================
// Diálogo de DETALLE de una Recepción de mercancía (solo lectura) — modal premium
// -----------------------------------------------------------------------------
// Muestra la recepción completa: folio, orden de compra, instante y sus renglones
// (material por NOMBRE y cantidad recibida). No edita; es la vista de consulta que
// evita mostrar UUIDs crudos en el listado. El nombre del material se resuelve con
// NombresComprasService.
// =============================================================================

import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { NombresComprasService } from '../services/nombres-compras.service';
import { PartidaRecepcion, RecepcionMercancia } from '../models/compras.models';

/** Datos de entrada: la recepción a mostrar en detalle. */
export interface RecepcionDetalleDialogData {
  recepcion: RecepcionMercancia;
}

@Component({
  selector: 'app-recepcion-detalle-dialog',
  imports: [DatePipe, MatDialogModule, MatButtonModule, DataTable, CeldaTablaDirective],
  templateUrl: './recepcion-detalle-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './recepcion-detalle-dialog.scss',
})
export class RecepcionDetalleDialog {
  private readonly nombres = inject(NombresComprasService);
  private readonly dialogRef = inject(MatDialogRef<RecepcionDetalleDialog>);
  private readonly data = inject<RecepcionDetalleDialogData>(MAT_DIALOG_DATA);

  protected readonly recepcion = this.data.recepcion;

  /** Asegura que el catálogo de nombres esté cargado para resolver materiales. */
  private readonly nombresListos = toSignal(this.nombres.cargar(), { initialValue: null });

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'material', encabezado: 'Material' },
    { clave: 'cantidad', encabezado: 'Cantidad recibida', alineacion: 'fin' },
  ];

  /** Nombre del material de una partida (nunca el UUID). */
  nombreMaterial(partida: PartidaRecepcion): string {
    this.nombresListos();
    return this.nombres.nombreMaterial(partida.materialId);
  }

  cerrar(): void {
    this.dialogRef.close();
  }
}
