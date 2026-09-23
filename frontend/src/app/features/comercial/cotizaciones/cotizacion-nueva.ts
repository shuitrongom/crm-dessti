// =============================================================================
// Vista de alta de Cotizacion (Req 6) — cabecera + partidas dinamicas
// -----------------------------------------------------------------------------
// Formulario reactivo con un FormArray de partidas (1..500). Muestra el subtotal
// por partida y el total de la cotizacion calculados en el cliente (Req 6.5) como
// previsualizacion; el total oficial lo devuelve el servidor al crear. El precio
// unitario es opcional: si se deja vacio y se indica un producto, el backend
// sugiere el precio desde la lista de precios (Req 59.4). Tras crear, navega al
// detalle. La accion se gobierna por el permiso cotizacion:crear.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  FormArray,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Observable } from 'rxjs';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { PaginaResponse } from '../../../core/models/pagina-response';

import { CotizacionesService } from '../services/cotizaciones.service';
import { ClientesService } from '../services/clientes.service';
import { ProductosService } from '../services/catalogo.service';
import {
  Cliente,
  CotizacionRequest,
  MONEDA_POR_DEFECTO,
  MONEDAS_COTIZACION,
  MonedaCotizacion,
  PartidaRequest,
  Producto,
  subtotalPartida,
  totalPartidas,
} from '../models/comercial.models';

/** Numero maximo de partidas por Cotizacion (Req 6.2). */
const MAX_PARTIDAS = 500;

@Component({
  selector: 'app-comercial-cotizacion-nueva',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    CurrencyPipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    EntitySelect,
  ],
  templateUrl: './cotizacion-nueva.html',
  styleUrl: './cotizacion-nueva.scss',
})
export class ComercialCotizacionNueva {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CotizacionesService);
  private readonly clientes = inject(ClientesService);
  private readonly productos = inject(ProductosService);
  private readonly toast = inject(NotificacionesService);
  private readonly router = inject(Router);

  protected readonly guardando = signal(false);
  protected readonly maxPartidas = MAX_PARTIDAS;
  protected readonly monedas = MONEDAS_COTIZACION;

  protected readonly form = this.fb.nonNullable.group({
    clienteId: ['', [Validators.required]],
    validoHasta: [''],
    moneda: [MONEDA_POR_DEFECTO as MonedaCotizacion],
    condiciones: ['', [Validators.maxLength(2000)]],
    notas: ['', [Validators.maxLength(2000)]],
    partidas: this.fb.array([this.crearPartida()]),
  });

  /** Signal del valor del formulario para recalcular totales de forma reactiva. */
  private readonly valor = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  /** Total previsualizado de la cotizacion (suma de subtotales, Req 6.5). */
  protected readonly total = computed(() => {
    const partidas = this.valor()?.partidas ?? [];
    return totalPartidas(
      partidas.map((p) => ({
        cantidad: Number(p?.cantidad ?? 0),
        precioUnitario: p?.precioUnitario == null ? 0 : Number(p.precioUnitario),
      })),
    );
  });

  /** Busca Clientes por nombre/RFC para el selector (nunca se teclea el UUID). */
  protected readonly buscarCliente = (filtro: string): Observable<PaginaResponse<Cliente>> =>
    this.clientes.listar(filtro, 0, 20);

  /** Etiqueta principal de un Cliente en el selector. */
  protected readonly etiquetaCliente = (cliente: Cliente): string => cliente.nombre;

  /** Detalle secundario (RFC) de un Cliente en el selector. */
  protected readonly detalleCliente = (cliente: Cliente): string | null => cliente.rfc || null;

  /** Busca Productos por nombre para el selector de partida (opcional, sin UUID). */
  protected readonly buscarProducto = (filtro: string): Observable<PaginaResponse<Producto>> =>
    this.productos.listar(filtro, 0, 20);

  /** Etiqueta principal de un Producto en el selector. */
  protected readonly etiquetaProducto = (producto: Producto): string => producto.nombre;

  /** Detalle secundario (unidad) de un Producto en el selector. */
  protected readonly detalleProducto = (producto: Producto): string | null =>
    producto.unidad || null;

  /** Acceso tipado al FormArray de partidas. */
  get partidas(): FormArray {
    return this.form.get('partidas') as FormArray;
  }

  /** Crea un FormGroup de partida con validaciones de campo. */
  private crearPartida(): FormGroup {
    return this.fb.nonNullable.group({
      productoId: [''],
      descripcion: ['', [Validators.required, Validators.maxLength(500)]],
      cantidad: [1, [Validators.required, Validators.min(1), Validators.max(999999)]],
      precioUnitario: [null as number | null, [Validators.min(0.01), Validators.max(999999999.99)]],
    });
  }

  /** Subtotal previsualizado de una partida por indice. */
  subtotalDe(indice: number): number {
    const grupo = this.partidas.at(indice);
    const cantidad = Number(grupo.get('cantidad')?.value ?? 0);
    const precio = Number(grupo.get('precioUnitario')?.value ?? 0);
    return subtotalPartida(cantidad, precio);
  }

  /** Agrega una partida si no se ha alcanzado el maximo (Req 6.2). */
  agregarPartida(): void {
    if (this.partidas.length >= MAX_PARTIDAS) {
      this.toast.info(`Una cotizacion admite hasta ${MAX_PARTIDAS} partidas.`);
      return;
    }
    this.partidas.push(this.crearPartida());
  }

  /** Elimina una partida; siempre debe quedar al menos una (Req 6.1). */
  quitarPartida(indice: number): void {
    if (this.partidas.length <= 1) {
      return;
    }
    this.partidas.removeAt(indice);
  }

  /** Crea la Cotizacion en borrador y navega a su detalle (Req 6.1). */
  crear(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const bruto = this.form.getRawValue();
    const partidas: PartidaRequest[] = bruto.partidas.map((p) => {
      const precio = p['precioUnitario'];
      const sinPrecio = precio == null || (precio as unknown as string) === '';
      return {
        productoId: (p['productoId'] as string)?.trim() || null,
        descripcion: (p['descripcion'] as string).trim(),
        cantidad: Number(p['cantidad']),
        precioUnitario: sinPrecio ? null : Number(precio),
      };
    });
    const request: CotizacionRequest = {
      clienteId: bruto.clienteId.trim(),
      partidas,
      moneda: bruto.moneda,
    };
    const validoHasta = bruto.validoHasta.trim();
    if (validoHasta) {
      request.validoHasta = validoHasta;
    }
    const condiciones = bruto.condiciones.trim();
    if (condiciones) {
      request.condiciones = condiciones;
    }
    const notas = bruto.notas.trim();
    if (notas) {
      request.notas = notas;
    }
    this.guardando.set(true);
    this.service.crear(request).subscribe({
      next: (cotizacion) => {
        this.guardando.set(false);
        this.toast.exito('Cotizacion creada.');
        this.router.navigate(['/empresa/comercial/cotizaciones', cotizacion.id]);
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
