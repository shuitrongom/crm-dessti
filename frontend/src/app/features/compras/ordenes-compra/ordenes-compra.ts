// =============================================================================
// Vista de Órdenes de compra (Req 31) — premium
// -----------------------------------------------------------------------------
// Listado paginado con filtro por estado y por proveedor (buscador premium con
// sugerencias, sin teclear UUID), KPIs clicables, y alta mediante MODAL premium
// (OrdenCompraFormDialog). El proveedor se muestra por NOMBRE (nunca UUID)
// resolviendo el catálogo. Las transiciones de estado manuales (cancelar, cerrar)
// se gobiernan por permiso; las de recepción parcial/total las deriva el flujo de
// recepciones. Todo con deny-by-default; el backend reimpone autorización y la
// máquina de estados.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { SearchAutocomplete } from '../../../shared/components/search-autocomplete/search-autocomplete';
import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import {
  IndicadorInfoDialog,
  type DatosIndicadorInfo,
} from '../../../shared/indicadores/indicador-info-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { humanizarEstado, tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { ComprasService } from '../services/compras.service';
import { NombresComprasService } from '../services/nombres-compras.service';
import { OrdenCompra, Proveedor } from '../models/compras.models';
import { OrdenCompraFormDialog } from './orden-compra-form-dialog';
import {
  OrdenCompraDetalleDialog,
  OrdenCompraDetalleDialogData,
} from './orden-compra-detalle-dialog';

/** Transiciones MANUALES ofrecidas en la UI por estado (parcial/total las deriva la recepción). */
const TRANSICIONES: Record<string, { estado: string; etiqueta: string; destructiva: boolean }[]> = {
  abierta: [{ estado: 'cancelada', etiqueta: 'Cancelar', destructiva: true }],
  recibida_parcial: [{ estado: 'cancelada', etiqueta: 'Cancelar', destructiva: true }],
  recibida_total: [{ estado: 'cerrada', etiqueta: 'Cerrar', destructiva: false }],
};

@Component({
  selector: 'app-compras-ordenes-compra',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    KpiTile,
    SearchAutocomplete,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './ordenes-compra.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../compras.scss',
})
export class ComprasOrdenesCompra {
  private readonly service = inject(ComprasService);
  private readonly nombres = inject(NombresComprasService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly puedeCrear = this.auth.tienePermiso('orden_compra', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('orden_compra', 'cambiar_estado');

  /** Buscador premium de proveedor para el filtro (sin teclear UUID). */
  protected readonly buscarProveedor = (filtro: string): Observable<PaginaResponse<Proveedor>> =>
    this.service.listarProveedores(filtro || null, 0, 10, 'activo');
  protected readonly etiquetaProveedor = (p: Proveedor): string => p.nombre;
  protected readonly detalleProveedor = (p: Proveedor): string | null => p.rfc;
  protected readonly iconoProveedor = (_p: Proveedor): string => 'local_shipping';

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'id', encabezado: 'Folio' },
    { clave: 'proveedor', encabezado: 'Proveedor' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'total', encabezado: 'Total', alineacion: 'fin' },
    { clave: 'fecha', encabezado: 'Creada' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'abierta', etiqueta: 'Abierta' },
    { valor: 'recibida_parcial', etiqueta: 'Recibida parcial' },
    { valor: 'recibida_total', etiqueta: 'Recibida total' },
    { valor: 'cerrada', etiqueta: 'Cerrada' },
    { valor: 'cancelada', etiqueta: 'Cancelada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<OrdenCompra[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  /** Id del proveedor filtrado (se fija al elegir una sugerencia del buscador). */
  protected readonly filtroProveedorId = signal('');

  /** KPIs de la página cargada. */
  private readonly filas = computed(() => this.estado().datos ?? []);
  protected readonly totalMostrado = computed(() => this.filas().length);
  protected readonly totalAbiertas = computed(
    () => this.filas().filter((o) => o.estado === 'abierta').length,
  );
  protected readonly totalEnRecepcion = computed(
    () =>
      this.filas().filter(
        (o) => o.estado === 'recibida_parcial' || o.estado === 'recibida_total',
      ).length,
  );
  /** Monto total comprometido (Σ total) de las órdenes de la página, excluyendo canceladas. */
  protected readonly montoComprometido = computed(() =>
    this.filas()
      .filter((o) => o.estado !== 'cancelada')
      .reduce((acc, o) => acc + o.total, 0),
  );

  constructor() {
    // Precarga el catálogo de nombres (proveedores + materiales) para el listado.
    this.nombres.cargar().subscribe({ next: () => {}, error: () => {} });
    this.cargar();
  }

  /** Nombre del proveedor (nunca el UUID). */
  nombreProveedor(id: string): string {
    return this.nombres.nombreProveedor(id);
  }

  /** RFC del proveedor (para el subdato de la celda). */
  rfcProveedor(id: string): string {
    return this.nombres.rfcProveedor(id);
  }

  /** Abre el diálogo explicativo de un KPI de órdenes de compra. */
  abrirInfoKpi(clave: string, etiqueta: string, valor: number, unidad = 'conteo'): void {
    const datos: DatosIndicadorInfo = { clave, etiqueta, valor, unidad };
    this.dialog.open(IndicadorInfoDialog, {
      data: datos,
      width: '32rem',
      maxWidth: '92vw',
      autoFocus: false,
    });
  }

  transicionesDe(estado: string): { estado: string; etiqueta: string; destructiva: boolean }[] {
    return TRANSICIONES[estado] ?? [];
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarOrdenesCompra(
        this.filtroProveedorId() || null,
        this.filtroEstado() || null,
        this.page(),
        this.size(),
      )
      .subscribe({
        next: (pagina) => {
          this.total.set(pagina.totalElements);
          this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
        },
        error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
      });
  }

  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  aplicarFiltroEstado(valor: string): void {
    this.filtroEstado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  /** Al elegir un proveedor en el buscador premium, filtra por su id. */
  seleccionarProveedor(proveedor: Proveedor): void {
    this.filtroProveedorId.set(proveedor.id);
    this.page.set(0);
    this.cargar();
  }

  /** Al limpiar el buscador (término vacío), quita el filtro por proveedor. */
  actualizarBusquedaProveedor(termino: string): void {
    if (!termino && this.filtroProveedorId()) {
      this.filtroProveedorId.set('');
      this.page.set(0);
      this.cargar();
    }
  }

  /** Abre el modal de alta y recarga si se creó. */
  nueva(): void {
    const ref = this.dialog.open(OrdenCompraFormDialog, {
      width: 'min(880px, 96vw)',
      maxWidth: 'min(880px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
    });
    ref.afterClosed().subscribe((creada?: OrdenCompra) => {
      if (creada) {
        this.toast.exito('Orden de compra creada.');
        this.filtroEstado.set('');
        this.filtroProveedorId.set('');
        this.page.set(0);
        this.cargar();
      }
    });
  }

  /** Abre el detalle de solo lectura de la orden (proveedor, partidas, total). */
  verDetalle(orden: OrdenCompra): void {
    const data: OrdenCompraDetalleDialogData = { orden };
    this.dialog.open(OrdenCompraDetalleDialog, {
      width: 'min(760px, 96vw)',
      maxWidth: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: false,
      panelClass: 'ds-dialog-panel',
      data,
    });
  }

  async cambiarEstado(
    orden: OrdenCompra,
    transicion: { estado: string; etiqueta: string; destructiva: boolean },
  ): Promise<void> {
    if (transicion.destructiva) {
      const ok = await this.confirm.confirmar({
        titulo: transicion.etiqueta,
        mensaje: `¿Confirmas "${transicion.etiqueta.toLowerCase()}" de la orden de compra?`,
        textoConfirmar: transicion.etiqueta,
        destructiva: true,
      });
      if (!ok) {
        return;
      }
    }
    this.overlay
      .ejecutar(this.service.cambiarEstadoOrdenCompra(orden.id, transicion.estado), {
        tipo: 'procesar',
        textoProceso: 'Actualizando estado…',
        textoExito: 'Estado actualizado',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Estado actualizado.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }
}
