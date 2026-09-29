// =============================================================================
// Dialogo de alta de Orden de Fabricacion (Operacion) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de la vista de Ordenes de Fabricacion por un
// MODAL (MatDialog) consistente con el resto de la plataforma (form shell
// ds-form*). Soporta los dos origenes de alta del backend:
//   - GENERAR desde una Cotizacion aprobada (POST /ordenes-fabricacion): se elige
//     la cotizacion por su folio (selector por nombre, nunca el UUID).
//   - CREAR DIRECTA (POST /ordenes-fabricacion/directa): Cliente + partidas
//     (Material + cantidad) capturadas dinamicamente.
// Al guardar, cierra devolviendo la Orden creada.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { Observable, of } from 'rxjs';

import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { PaginaResponse } from '../../../core/models/pagina-response';

import { ProduccionService } from '../services/produccion.service';
import { NombresOperacionService } from '../services/nombres-operacion.service';
import { OrdenFabricacion } from '../models/operacion.models';
import { Cliente, Cotizacion } from '../../comercial/models/comercial.models';
import { Material } from '../models/operacion.models';

/** Modo de alta de la Orden. */
type ModoAlta = 'cotizacion' | 'directa';

/** Este dialogo no recibe datos de entrada (siempre es alta). */
export type OrdenFabricacionFormDialogData = Record<string, never>;

@Component({
  selector: 'app-orden-fabricacion-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    EntitySelect,
  ],
  templateUrl: './orden-fabricacion-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './orden-fabricacion-form-dialog.scss',
})
export class OrdenFabricacionFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProduccionService);
  private readonly nombres = inject(NombresOperacionService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<OrdenFabricacionFormDialog, OrdenFabricacion>);

  protected readonly modo = signal<ModoAlta>('cotizacion');
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly subtitulo = computed(() =>
    this.modo() === 'cotizacion'
      ? 'Genera la orden a partir de una cotización aprobada (con prueba de diseño aprobada).'
      : 'Crea una orden directa asociada a un cliente, capturando sus materiales.',
  );

  /** Modo "desde cotización": solo la cotización aprobada. */
  protected readonly formCotizacion = this.fb.nonNullable.group({
    cotizacionId: ['', [Validators.required]],
  });

  /** Modo "directa": cliente + partidas (material + cantidad). */
  protected readonly formDirecta = this.fb.nonNullable.group({
    clienteId: ['', [Validators.required]],
    partidas: this.fb.array([this.crearPartida()]),
  });

  get partidas(): FormArray {
    return this.formDirecta.get('partidas') as FormArray;
  }

  private crearPartida(): FormGroup {
    return this.fb.nonNullable.group({
      materialId: ['', [Validators.required]],
      cantidad: [1, [Validators.required, Validators.min(0.001)]],
    });
  }

  // --- Buscadores para los selectores por nombre (sobre catalogos en memoria) ---

  /** Cotizaciones APROBADAS por folio (nunca el UUID). */
  protected readonly buscarCotizacion = (
    filtro: string,
  ): Observable<PaginaResponse<Cotizacion>> => {
    const termino = filtro.trim().toLowerCase();
    const lista = this.nombres.cotizaciones().filter((c) => c.estado === 'aprobada');
    const content = termino
      ? lista.filter((c) => (c.folio ?? '').toLowerCase().includes(termino))
      : lista.slice(0, 20);
    return of(this.paginar(content));
  };

  protected readonly etiquetaCotizacion = (c: Cotizacion): string =>
    c.folio ?? 'Cotización sin folio';

  protected readonly detalleCotizacion = (c: Cotizacion): string | null => c.clienteNombre ?? null;

  /** Clientes por nombre (nunca el UUID). */
  protected readonly buscarCliente = (filtro: string): Observable<PaginaResponse<Cliente>> => {
    const termino = filtro.trim().toLowerCase();
    const lista = this.nombres.clientes();
    const content = termino
      ? lista.filter((c) => c.nombre.toLowerCase().includes(termino))
      : lista.slice(0, 20);
    return of(this.paginar(content));
  };

  protected readonly etiquetaCliente = (c: Cliente): string => c.nombre;

  /** Materiales activos por nombre (nunca el UUID). */
  protected readonly buscarMaterial = (filtro: string): Observable<PaginaResponse<Material>> => {
    const termino = filtro.trim().toLowerCase();
    const lista = this.nombres.materiales();
    const content = termino
      ? lista.filter((m) => m.nombre.toLowerCase().includes(termino))
      : lista.slice(0, 20);
    return of(this.paginar(content));
  };

  protected readonly etiquetaMaterial = (m: Material): string => m.nombre;

  protected readonly detalleMaterial = (m: Material): string | null => m.unidadMedida ?? null;

  private paginar<T>(content: T[]): PaginaResponse<T> {
    return {
      content: content.slice(0, 20),
      page: 0,
      size: 20,
      totalElements: content.length,
      totalPages: 1,
    };
  }

  constructor() {
    // Asegura que los catalogos de nombres esten cargados para los selectores.
    if (!this.nombres.cargado()) {
      this.nombres.cargar().subscribe({ next: () => {}, error: () => {} });
    }
  }

  /** Cambia el modo de alta (desde cotización / directa). */
  protected cambiarModo(modo: ModoAlta): void {
    this.modo.set(modo);
    this.error.set(null);
  }

  /** Agrega una partida vacía al formulario directo. */
  protected agregarPartida(): void {
    this.partidas.push(this.crearPartida());
  }

  /** Quita la partida indicada (siempre queda al menos una). */
  protected quitarPartida(indice: number): void {
    if (this.partidas.length > 1) {
      this.partidas.removeAt(indice);
    }
  }

  /** Persiste el alta según el modo activo. */
  protected guardar(): void {
    this.error.set(null);
    if (this.modo() === 'cotizacion') {
      this.generarDesdeCotizacion();
    } else {
      this.crearDirecta();
    }
  }

  private generarDesdeCotizacion(): void {
    if (this.formCotizacion.invalid) {
      this.formCotizacion.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    this.overlay
      .ejecutar(this.service.generar(this.formCotizacion.getRawValue().cotizacionId), {
        tipo: 'crear',
        textoProceso: 'Generando orden…',
        textoExito: 'Orden generada',
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

  private crearDirecta(): void {
    if (this.formDirecta.invalid) {
      this.formDirecta.markAllAsTouched();
      return;
    }
    const v = this.formDirecta.getRawValue() as {
      clienteId: string;
      partidas: { materialId: string; cantidad: number }[];
    };
    const partidas = v.partidas.map((p) => ({
      materialId: p.materialId,
      cantidad: Number(p.cantidad),
    }));
    this.guardando.set(true);
    this.overlay
      .ejecutar(this.service.crearDirecta({ clienteId: v.clienteId, partidas }), {
        tipo: 'crear',
        textoProceso: 'Creando orden…',
        textoExito: 'Orden creada',
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

  /** Cancela sin guardar. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
