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

import {
  Component,
  computed,
  inject,
  signal,
  DestroyRef,
  ChangeDetectionStrategy,
} from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { toSignal, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
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
  calcularDesgloseFiscal,
  Cliente,
  CotizacionRequest,
  DesgloseFiscal,
  MONEDA_POR_DEFECTO,
  MONEDAS_COTIZACION,
  MonedaCotizacion,
  PartidaRequest,
  Producto,
  subtotalPartida,
  TASA_IVA_POR_DEFECTO,
  TASAS_IVA,
  TasaIva,
} from '../models/comercial.models';

/** Numero maximo de partidas por Cotizacion (Req 6.2). */
const MAX_PARTIDAS = 500;

/**
 * Estado de la sugerencia de precio de una partida (Req 59.4). Alimenta el aviso
 * de la UI para que el importe no aparente ser cero mientras se arma la cotizacion:
 *  - `sugiriendo`: se esta consultando el precio de lista del Producto;
 *  - `aplicado`: se coloco el precio de lista sugerido en el control;
 *  - `sin-precio`: el Producto no tiene precio de lista vigente (capturar manual);
 *  - `manual`: el Usuario capturo/ajusto el precio a mano (no se sugiere);
 *  - `''` (vacio): sin Producto o sin estado.
 */
type EstadoPrecio = 'sugiriendo' | 'aplicado' | 'sin-precio' | 'manual' | '';

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
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './cotizacion-nueva.scss',
})
export class ComercialCotizacionNueva {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CotizacionesService);
  private readonly clientes = inject(ClientesService);
  private readonly productos = inject(ProductosService);
  private readonly toast = inject(NotificacionesService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /**
   * Estado de la sugerencia de precio por partida, indexado por el FormGroup de la
   * partida. Es un signal (no un valor de FormControl leido en el template) para
   * que la vista lo lea de forma estable dentro de un ciclo de deteccion: al ser
   * signal, un cambio agenda un ciclo nuevo y no dispara NG0100
   * (ExpressionChangedAfterItHasBeenChecked). Se indexa por FormGroup para que el
   * estado viaje con la partida aunque cambie de posicion.
   */
  private readonly estados = signal(new Map<FormGroup, EstadoPrecio>());

  protected readonly guardando = signal(false);
  protected readonly maxPartidas = MAX_PARTIDAS;
  protected readonly monedas = MONEDAS_COTIZACION;
  protected readonly tasasIva = TASAS_IVA;

  protected readonly form = this.fb.nonNullable.group({
    clienteId: ['', [Validators.required]],
    validoHasta: [''],
    moneda: [MONEDA_POR_DEFECTO as MonedaCotizacion],
    condiciones: ['', [Validators.maxLength(2000)]],
    notas: ['', [Validators.maxLength(2000)]],
    // Ajustes fiscales globales (V80): montos opcionales, no negativos.
    descuentoGlobal: [null as number | null, [Validators.min(0)]],
    retencionIsr: [null as number | null, [Validators.min(0)]],
    retencionIva: [null as number | null, [Validators.min(0)]],
    partidas: this.fb.array([this.crearPartida()]),
  });

  /** Signal del valor del formulario para recalcular totales de forma reactiva. */
  private readonly valor = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  /** Desglose fiscal previsualizado (subtotal, descuento, IVA, retenciones, total) (V80). */
  protected readonly desglose = computed<DesgloseFiscal>(() => {
    const v = this.valor();
    const partidas = (v?.partidas ?? []).map((p) => ({
      cantidad: Number(p?.cantidad ?? 0),
      precioUnitario: p?.precioUnitario == null ? 0 : Number(p.precioUnitario),
      descuento: p?.descuento == null ? 0 : Number(p.descuento),
      tasaIva: (p?.tasaIva ?? TASA_IVA_POR_DEFECTO) as TasaIva,
    }));
    return calcularDesgloseFiscal(
      partidas,
      v?.descuentoGlobal == null ? 0 : Number(v.descuentoGlobal),
      v?.retencionIsr == null ? 0 : Number(v.retencionIsr),
      v?.retencionIva == null ? 0 : Number(v.retencionIva),
    );
  });

  /** Total previsualizado de la cotizacion (con desglose fiscal, V80). */
  protected readonly total = computed(() => this.desglose().total);

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

  /**
   * Crea un FormGroup de partida con validaciones de campo. Incluye un control
   * auxiliar `estadoPrecio` (no se envia al backend) que registra el estado de la
   * sugerencia de precio de la partida y viaja con ella aunque cambie de indice.
   */
  private crearPartida(): FormGroup {
    const grupo = this.fb.nonNullable.group({
      productoId: [''],
      descripcion: ['', [Validators.required, Validators.maxLength(500)]],
      cantidad: [1, [Validators.required, Validators.min(1), Validators.max(999999)]],
      precioUnitario: [null as number | null, [Validators.min(0.01), Validators.max(999999999.99)]],
      // Desglose fiscal por partida (V80): descuento (monto) y tasa de IVA.
      descuento: [null as number | null, [Validators.min(0), Validators.max(999999999.99)]],
      tasaIva: [TASA_IVA_POR_DEFECTO as TasaIva],
    });

    // Al elegir un Producto se consulta su precio de lista vigente y se coloca
    // como sugerencia (Req 59.4), de modo que el subtotal deje de verse en cero.
    // No se pisa un precio ya capturado por el Usuario.
    grupo
      .get('productoId')!
      .valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((productoId) => this.resolverPrecioSugerido(grupo, productoId as string | null));

    // Si el Usuario captura/ajusta el precio a mano (no por la sugerencia), se
    // marca como manual para no volver a pisarlo mientras conserve ese Producto.
    grupo
      .get('precioUnitario')!
      .valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.estadoDe(grupo) !== 'sugiriendo') {
          this.fijarEstado(grupo, 'manual');
        }
      });

    return grupo;
  }

  /**
   * Consulta el precio de lista vigente del Producto elegido y lo coloca como
   * sugerencia en la partida cuando el precio esta vacio (Req 59.4, 59.9). Si el
   * Producto no tiene precio de lista, se marca `sin-precio` para que el Usuario
   * lo capture. Un precio ya capturado a mano no se sobrescribe.
   */
  private resolverPrecioSugerido(grupo: FormGroup, productoId: string | null): void {
    const control = grupo.get('precioUnitario')!;
    const idLimpio = (productoId ?? '').trim();
    if (!idLimpio) {
      this.fijarEstado(grupo, '');
      return;
    }
    // No pisar un precio que el Usuario ya capturo manualmente.
    if (control.value != null && this.estadoDe(grupo) === 'manual') {
      return;
    }
    this.fijarEstado(grupo, 'sugiriendo');
    this.productos
      .precioSugerido(idLimpio)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (sugerencia) => {
          // Si mientras cargaba cambio el Producto de esta partida, se ignora.
          if ((grupo.get('productoId')!.value ?? '').trim() !== idLimpio) {
            return;
          }
          if (sugerencia.disponible && sugerencia.precioSugerido != null) {
            control.setValue(sugerencia.precioSugerido, { emitEvent: false });
            control.markAsDirty();
            this.fijarEstado(grupo, 'aplicado');
          } else {
            this.fijarEstado(grupo, 'sin-precio');
          }
        },
        error: () => {
          // Ante un fallo de red no se bloquea el flujo: el Usuario captura el
          // precio a mano. Se limpia el estado para no mostrar un aviso erroneo.
          this.fijarEstado(grupo, '');
        },
      });
  }

  /** Lee el estado de sugerencia de precio de una partida desde el signal. */
  private estadoDe(grupo: FormGroup): EstadoPrecio {
    return this.estados().get(grupo) ?? '';
  }

  /**
   * Fija el estado de sugerencia de precio de una partida en el signal. Se crea un
   * Map nuevo (referencia nueva) para que el signal notifique el cambio y la vista
   * lo lea de forma estable en el siguiente ciclo (sin NG0100).
   */
  private fijarEstado(grupo: FormGroup, estado: EstadoPrecio): void {
    const copia = new Map(this.estados());
    if (estado === '') {
      copia.delete(grupo);
    } else {
      copia.set(grupo, estado);
    }
    this.estados.set(copia);
  }

  /** Estado de sugerencia de precio de una partida por indice (para la vista). */
  estadoPrecioDe(indice: number): EstadoPrecio {
    return this.estadoDe(this.partidas.at(indice) as FormGroup);
  }

  /** Importe bruto previsualizado de una partida por indice (cantidad * precio). */
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
      const desc = p['descuento'];
      const sinDescuento = desc == null || (desc as unknown as string) === '';
      return {
        productoId: (p['productoId'] as string)?.trim() || null,
        descripcion: (p['descripcion'] as string).trim(),
        cantidad: Number(p['cantidad']),
        precioUnitario: sinPrecio ? null : Number(precio),
        descuento: sinDescuento ? null : Number(desc),
        tasaIva: (p['tasaIva'] as TasaIva) ?? TASA_IVA_POR_DEFECTO,
      };
    });
    const request: CotizacionRequest = {
      clienteId: bruto.clienteId.trim(),
      partidas,
      moneda: bruto.moneda,
    };
    if (bruto.descuentoGlobal != null && Number(bruto.descuentoGlobal) > 0) {
      request.descuentoGlobal = Number(bruto.descuentoGlobal);
    }
    if (bruto.retencionIsr != null && Number(bruto.retencionIsr) > 0) {
      request.retencionIsr = Number(bruto.retencionIsr);
    }
    if (bruto.retencionIva != null && Number(bruto.retencionIva) > 0) {
      request.retencionIva = Number(bruto.retencionIva);
    }
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
