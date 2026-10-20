// =============================================================================
// Diálogo de registro de Recepción de mercancía (Compras, Req 32) — modal premium
// -----------------------------------------------------------------------------
// Reemplaza el "teclea el UUID de la orden" por un MODAL premium: se elige la
// Orden_Compra por NOMBRE (folio + proveedor, EntitySelect que solo lista órdenes
// que admiten recepción: abierta / recibida_parcial), se cargan sus partidas y se
// captura la cantidad recibida por renglón, con el material por NOMBRE y la
// cantidad ordenada como referencia. Al guardar hace POST vía ComprasService y
// cierra devolviendo la Recepción creada. El backend valida el estado de la orden
// (422), que el acumulado no exceda lo ordenado (422) y actualiza el inventario.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Observable, map } from 'rxjs';

import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { PaginaResponse } from '../../../core/models/pagina-response';

import { ComprasService } from '../services/compras.service';
import { NombresComprasService } from '../services/nombres-compras.service';
import { OrdenCompra, PartidaOrdenCompra, RecepcionMercancia } from '../models/compras.models';

@Component({
  selector: 'app-recepcion-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    EntitySelect,
  ],
  templateUrl: './recepcion-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './recepcion-form-dialog.scss',
})
export class RecepcionFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ComprasService);
  private readonly nombres = inject(NombresComprasService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly toast = inject(NotificacionesService);
  private readonly dialogRef = inject(MatDialogRef<RecepcionFormDialog, RecepcionMercancia>);

  protected readonly guardando = signal(false);
  protected readonly cargandoOrden = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Orden seleccionada (con sus partidas) para capturar lo recibido. */
  protected readonly orden = signal<OrdenCompra | null>(null);

  /** Asegura el catálogo de nombres (proveedores + materiales) para las etiquetas. */
  private readonly nombresListos = toSignal(this.nombres.cargar(), { initialValue: null });

  /**
   * Buscador de órdenes que ADMITEN recepción. El backend no filtra por
   * "recepcionables" en un solo parámetro, así que se listan las abiertas y las
   * parciales y se combinan; EntitySelect solo necesita una página (page 0).
   */
  protected readonly buscarOrden = (filtro: string): Observable<PaginaResponse<OrdenCompra>> =>
    this.service.listarOrdenesCompra(null, 'abierta', 0, 20).pipe(
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

  /** Etiqueta legible de una orden: folio corto + proveedor (nunca solo UUID). */
  protected readonly etiquetaOrden = (o: OrdenCompra): string =>
    `OC ${o.id.slice(0, 8)} · ${this.nombres.nombreProveedor(o.proveedorId)}`;
  protected readonly detalleOrden = (o: OrdenCompra): string | null =>
    this.nombres.rfcProveedor(o.proveedorId) || null;

  protected readonly form = this.fb.nonNullable.group({
    ordenCompraId: ['', [Validators.required]],
    partidas: this.fb.array<ReturnType<RecepcionFormDialog['crearRenglon']>>([]),
  });

  constructor() {
    // Al cambiar la orden (EntitySelect es un ControlValueAccessor), recarga sus
    // partidas para capturar lo recibido por renglón.
    this.form.controls.ordenCompraId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((id) => this.alElegirOrden(id || null));
  }

  protected get partidas(): FormArray {
    return this.form.get('partidas') as FormArray;
  }

  /** `true` si al menos un renglón tiene cantidad > 0 (habilita el guardar). */
  private readonly valores = toSignal(this.partidas.valueChanges, {
    initialValue: this.partidas.getRawValue(),
  });
  protected readonly hayCantidad = computed<boolean>(() => {
    const filas = (this.valores() ?? []) as { cantidadRecibida: number }[];
    return filas.some((f) => Number(f.cantidadRecibida || 0) > 0);
  });

  private crearRenglon(partidaOrdenCompraId: string) {
    return this.fb.nonNullable.group({
      partidaOrdenCompraId: [partidaOrdenCompraId, [Validators.required]],
      cantidadRecibida: [0, [Validators.required, Validators.min(0)]],
    });
  }

  /** Al elegir una orden en el EntitySelect, carga sus partidas y arma los renglones. */
  protected alElegirOrden(ordenCompraId: string | null): void {
    this.error.set(null);
    this.orden.set(null);
    this.form.setControl(
      'partidas',
      this.fb.array<ReturnType<RecepcionFormDialog['crearRenglon']>>([]),
    );
    if (!ordenCompraId) {
      return;
    }
    this.cargandoOrden.set(true);
    this.service.consultarOrdenCompra(ordenCompraId).subscribe({
      next: (orden) => {
        this.cargandoOrden.set(false);
        this.orden.set(orden);
        const arreglo = this.fb.array(orden.partidas.map((p) => this.crearRenglon(p.id)));
        this.form.setControl('partidas', arreglo);
      },
      error: (e: HttpErrorResponse) => {
        this.cargandoOrden.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  /** Nombre del material del renglón (nunca el UUID). */
  protected nombreMaterial(indice: number): string {
    this.nombresListos();
    const partida = this.orden()?.partidas[indice];
    return partida ? this.nombres.nombreMaterial(partida.materialId) : '';
  }

  /** Cantidad ordenada de la partida (referencia para no exceder). */
  protected cantidadOrdenada(indice: number): number {
    return this.orden()?.partidas[indice]?.cantidad ?? 0;
  }

  /** Partida de la orden por índice (para el máximo del input). */
  protected partidaOrden(indice: number): PartidaOrdenCompra | undefined {
    return this.orden()?.partidas[indice];
  }

  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid || this.partidas.length === 0) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const partidas = (
      v.partidas as { partidaOrdenCompraId: string; cantidadRecibida: number }[]
    ).filter((p) => p.cantidadRecibida > 0);
    if (partidas.length === 0) {
      this.toast.info('Captura al menos una cantidad recibida.');
      return;
    }
    this.guardando.set(true);
    this.overlay
      .ejecutar(
        this.service.registrarRecepcion({ ordenCompraId: v.ordenCompraId, partidas }),
        { tipo: 'crear', textoProceso: 'Registrando recepción…', textoExito: 'Recepción registrada' },
      )
      .subscribe({
        next: (recepcion) => {
          this.guardando.set(false);
          this.dialogRef.close(recepcion);
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
