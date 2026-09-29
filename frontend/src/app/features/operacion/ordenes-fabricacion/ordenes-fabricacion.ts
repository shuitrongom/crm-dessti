// =============================================================================
// Vista de Ordenes de Fabricacion (Req 7) — listado premium
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por estado y por Cliente (por NOMBRE
// via app-entity-select, nunca el UUID; Req 11.1). El alta de una Orden (generar
// desde cotización o crear directa) se hace en un MODAL animado
// (OrdenFabricacionFormDialog). Ofrece SOLO las transiciones de estado validas
// (maquina de estados). Los KPIs por estado (con conteos reales del backend)
// abren el modal explicativo. Acciones gobernadas por orden_fabricacion:{...}.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ChipEstado } from '../../../shared/components/chip-estado/chip-estado';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import {
  IndicadorInfoDialog,
  type DatosIndicadorInfo,
} from '../../../shared/indicadores/indicador-info-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { Cliente } from '../../comercial/models/comercial.models';

import { ProduccionService } from '../services/produccion.service';
import { NombresOperacionService } from '../services/nombres-operacion.service';
import {
  ETIQUETA_ESTADO_OF,
  EstadoOrdenFabricacion,
  OrdenFabricacion,
  estadosDestinoOf,
} from '../models/operacion.models';
import { OrdenFabricacionFormDialog } from './orden-fabricacion-form-dialog';

@Component({
  selector: 'app-operacion-ordenes-fabricacion',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    ChipEstado,
    KpiTile,
    EntitySelect,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './ordenes-fabricacion.html',
  styleUrl: './ordenes-fabricacion.scss',
})
export class OperacionOrdenesFabricacion {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProduccionService);
  protected readonly nombres = inject(NombresOperacionService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeCrear = this.auth.tienePermiso('orden_fabricacion', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('orden_fabricacion', 'cambiar_estado');
  private readonly mapaEstado = ETIQUETA_ESTADO_OF;
  protected readonly estados: EstadoOrdenFabricacion[] = [
    'pendiente',
    'en_produccion',
    'terminada',
    'cancelada',
  ];

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly ordenes = signal<OrdenFabricacion[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly estado = signal<EstadoOrdenFabricacion | ''>('');
  /** Cliente seleccionado en el filtro (id interno; nunca visible). */
  protected readonly clienteId = signal<string>('');

  /** Conteos reales por estado (independientes del filtro/paginacion de la tabla). */
  protected readonly totalPendientes = signal<number | null>(null);
  protected readonly totalEnProduccion = signal<number | null>(null);
  protected readonly totalTerminadas = signal<number | null>(null);

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'cliente', encabezado: 'Cliente' },
    { clave: 'cotizacion', encabezado: 'Cotización' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  /** Filtro por Cliente por NOMBRE (app-entity-select expone el id internamente). */
  protected readonly formFiltro = this.fb.nonNullable.group({
    clienteId: [''],
  });

  /**
   * Busca Clientes por nombre sobre el catalogo ya cargado en memoria (sin red
   * por pulsacion), alimentando el app-entity-select del filtro (Req 11.1, 11.5).
   */
  protected readonly buscarCliente = (filtro: string): Observable<PaginaResponse<Cliente>> => {
    const termino = filtro.trim().toLowerCase();
    const lista = this.nombres.clientes();
    const content = termino
      ? lista.filter((c) => c.nombre.toLowerCase().includes(termino))
      : lista.slice(0, 20);
    return of({
      content: content.slice(0, 20),
      page: 0,
      size: 20,
      totalElements: content.length,
      totalPages: 1,
    });
  };

  protected readonly etiquetaCliente = (c: Cliente): string => c.nombre;

  constructor() {
    // Carga los catalogos de nombres (Cliente) para el selector por nombre y el
    // render de la columna Cliente sin exponer UUIDs; luego el listado y conteos.
    this.nombres.cargar().subscribe({
      next: () => {
        this.cargar();
        this.cargarConteos();
      },
      error: () => {
        this.cargar();
        this.cargarConteos();
      },
    });
    // Aplica el filtro por Cliente al elegir/limpiar una opcion del selector.
    this.formFiltro.controls.clienteId.valueChanges.subscribe((valor) => {
      this.cambiarCliente(valor ?? '');
    });
  }

  /** Etiqueta legible del estado de la orden; devuelve el valor crudo si no mapea. */
  protected etiquetaEstado(estado: string): string {
    return this.mapaEstado[estado as EstadoOrdenFabricacion] ?? estado;
  }

  /**
   * Mapea el estado de la OF a la variante semantica del Chip_Estado:
   * pendiente -> info, en_produccion -> advertencia, terminada -> exito,
   * cancelada -> neutro (por sobriedad, no es un error del sistema).
   */
  protected varianteEstado(
    estado: EstadoOrdenFabricacion,
  ): 'exito' | 'advertencia' | 'error' | 'info' | 'neutro' {
    switch (estado) {
      case 'pendiente':
        return 'info';
      case 'en_produccion':
        return 'advertencia';
      case 'terminada':
        return 'exito';
      case 'cancelada':
        return 'neutro';
      default:
        return 'neutro';
    }
  }

  /** Nombre legible del Cliente de una orden (nunca el UUID). */
  protected nombreCliente(clienteId: string | null | undefined): string {
    return this.nombres.nombreCliente(clienteId);
  }

  /** Etiqueta legible de la Cotizacion de origen o marcador "Directa". */
  protected etiquetaCotizacion(orden: OrdenFabricacion): string {
    return orden.cotizacionId ? this.nombres.nombreCotizacion(orden.cotizacionId) : 'Directa';
  }

  cargar(): void {
    this.fase.set('cargando');
    const estado = this.estado() || null;
    const clienteId = this.clienteId() || null;
    this.service.listar(estado, this.page(), this.size(), clienteId).subscribe({
      next: (pagina) => {
        this.ordenes.set(pagina.content);
        this.total.set(pagina.totalElements);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /**
   * Carga los conteos por estado con consultas de tamano 1 (solo totalElements),
   * independientes del filtro de la tabla, para que los KPIs no mientan al filtrar.
   */
  cargarConteos(): void {
    this.service.listar('pendiente', 0, 1).subscribe({
      next: (p) => this.totalPendientes.set(p.totalElements),
      error: () => this.totalPendientes.set(null),
    });
    this.service.listar('en_produccion', 0, 1).subscribe({
      next: (p) => this.totalEnProduccion.set(p.totalElements),
      error: () => this.totalEnProduccion.set(null),
    });
    this.service.listar('terminada', 0, 1).subscribe({
      next: (p) => this.totalTerminadas.set(p.totalElements),
      error: () => this.totalTerminadas.set(null),
    });
  }

  cambiarFiltro(valor: EstadoOrdenFabricacion | ''): void {
    this.estado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  /** Aplica el filtro por Cliente derivado de la seleccion por nombre (Req 11.3). */
  cambiarCliente(clienteId: string): void {
    if (clienteId === this.clienteId()) {
      return;
    }
    this.clienteId.set(clienteId);
    this.page.set(0);
    this.cargar();
  }

  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Transiciones de estado validas para una Orden dada. */
  transicionesDe(orden: OrdenFabricacion): readonly EstadoOrdenFabricacion[] {
    return estadosDestinoOf(orden.estado);
  }

  /** Abre el modal de alta de Orden (desde cotización o directa) y recarga si se creó. */
  nuevo(): void {
    const ref = this.dialog.open(OrdenFabricacionFormDialog, {
      width: 'min(760px, 96vw)',
      maxWidth: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
    });
    ref.afterClosed().subscribe((creada?: OrdenFabricacion) => {
      if (creada) {
        this.toast.exito('Orden de fabricación creada.');
        this.cargar();
        this.cargarConteos();
      }
    });
  }

  /**
   * Abre el dialogo explicativo de un indicador de producción (¿qué es? / ¿cómo se
   * calcula? / ¿por qué importa?). La clave debe coincidir con una del catálogo.
   */
  abrirInfoKpi(clave: string, etiqueta: string, valor: number, unidad: string): void {
    const datos: DatosIndicadorInfo = { clave, etiqueta, valor, unidad };
    this.dialog.open(IndicadorInfoDialog, {
      data: datos,
      width: '32rem',
      maxWidth: '92vw',
      autoFocus: false,
    });
  }

  /** Cambia el estado de una Orden con confirmacion (Req 7.5). */
  async cambiarEstado(orden: OrdenFabricacion, estado: EstadoOrdenFabricacion): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Cambiar estado de la orden',
      mensaje: `La orden pasara a "${ETIQUETA_ESTADO_OF[estado]}". Deseas continuar?`,
      textoConfirmar: 'Cambiar estado',
      destructiva: estado === 'cancelada',
    });
    if (!ok) {
      return;
    }
    this.service.cambiarEstado(orden.id, estado).subscribe({
      next: () => {
        this.toast.exito('Estado actualizado.');
        this.cargar();
        this.cargarConteos();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
