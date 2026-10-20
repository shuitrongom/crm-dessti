// =============================================================================
// Vista de Recepciones de mercancía (Req 32) — premium
// -----------------------------------------------------------------------------
// Tercera vía del proceso de compra. Listado paginado con filtro por Orden_Compra
// (buscador premium con sugerencias, sin teclear UUID), KPIs clicables, alta
// mediante MODAL premium (RecepcionFormDialog) y detalle de solo lectura
// (RecepcionDetalleDialog). El material y el proveedor se muestran por NOMBRE
// (nunca UUID) resolviendo el catálogo. El backend valida el estado de la orden y
// que el acumulado no exceda lo ordenado, y deriva el estado de la orden.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { Observable, map } from 'rxjs';

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
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
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

import { ComprasService } from '../services/compras.service';
import { NombresComprasService } from '../services/nombres-compras.service';
import { OrdenCompra, RecepcionMercancia } from '../models/compras.models';
import { RecepcionFormDialog } from './recepcion-form-dialog';
import {
  RecepcionDetalleDialog,
  RecepcionDetalleDialogData,
} from './recepcion-detalle-dialog';

@Component({
  selector: 'app-compras-recepciones',
  imports: [
    DatePipe,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    KpiTile,
    SearchAutocomplete,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './recepciones.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../compras.scss',
})
export class ComprasRecepciones {
  private readonly service = inject(ComprasService);
  private readonly nombres = inject(NombresComprasService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeCrear = this.auth.tienePermiso('recepcion_mercancia', 'crear');

  /** Buscador premium de órdenes de compra abiertas para el filtro (sin teclear UUID). */
  protected readonly buscarOrden = (filtro: string): Observable<PaginaResponse<OrdenCompra>> =>
    this.service.listarOrdenesCompra(null, null, 0, 10).pipe(
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
  protected readonly iconoOrden = (_o: OrdenCompra): string => 'receipt_long';

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'id', encabezado: 'Folio' },
    { clave: 'orden', encabezado: 'Orden de compra' },
    { clave: 'renglones', encabezado: 'Renglones', alineacion: 'centro' },
    { clave: 'fecha', encabezado: 'Recibida' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estado = signal<EstadoSolicitud<RecepcionMercancia[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  /** Id de la orden filtrada (se fija al elegir una sugerencia del buscador). */
  protected readonly filtroOrdenId = signal('');

  /** KPIs de la página cargada. */
  private readonly filas = computed(() => this.estado().datos ?? []);
  protected readonly totalMostrado = computed(() => this.filas().length);
  protected readonly totalRenglones = computed(() =>
    this.filas().reduce((acc, r) => acc + r.partidas.length, 0),
  );

  constructor() {
    // Precarga el catálogo de nombres (proveedores + materiales) para el listado.
    this.nombres.cargar().subscribe({ next: () => {}, error: () => {} });
    this.cargar();
  }

  /** Abre el diálogo explicativo de un KPI de recepciones. */
  abrirInfoKpi(clave: string, etiqueta: string, valor: number): void {
    const datos: DatosIndicadorInfo = { clave, etiqueta, valor, unidad: 'conteo' };
    this.dialog.open(IndicadorInfoDialog, {
      data: datos,
      width: '32rem',
      maxWidth: '92vw',
      autoFocus: false,
    });
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarRecepciones(this.filtroOrdenId() || null, this.page(), this.size()).subscribe({
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

  /** Al elegir una orden en el buscador premium, filtra por su id. */
  seleccionarOrden(orden: OrdenCompra): void {
    this.filtroOrdenId.set(orden.id);
    this.page.set(0);
    this.cargar();
  }

  /** Al limpiar el buscador (término vacío), quita el filtro por orden. */
  actualizarBusquedaOrden(termino: string): void {
    if (!termino && this.filtroOrdenId()) {
      this.filtroOrdenId.set('');
      this.page.set(0);
      this.cargar();
    }
  }

  /** Abre el modal de registro y recarga si se creó una recepción. */
  nueva(): void {
    const ref = this.dialog.open(RecepcionFormDialog, {
      width: 'min(760px, 96vw)',
      maxWidth: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
    });
    ref.afterClosed().subscribe((creada?: RecepcionMercancia) => {
      if (creada) {
        this.toast.exito('Recepción registrada.');
        this.filtroOrdenId.set('');
        this.page.set(0);
        this.cargar();
      }
    });
  }

  /** Abre el detalle de solo lectura de la recepción (materiales por nombre). */
  verDetalle(recepcion: RecepcionMercancia): void {
    const data: RecepcionDetalleDialogData = { recepcion };
    this.dialog.open(RecepcionDetalleDialog, {
      width: 'min(640px, 96vw)',
      maxWidth: 'min(640px, 96vw)',
      maxHeight: '92vh',
      autoFocus: false,
      panelClass: 'ds-dialog-panel',
      data,
    });
  }
}
