// =============================================================================
// Vista de Facturas de proveedor / conciliación 3 vías (Req 33) — premium
// -----------------------------------------------------------------------------
// Cierra el proceso de compra. Listado paginado con filtro por estado y por
// proveedor (buscador premium con sugerencias, sin teclear UUID), KPIs clicables
// por estado, alta mediante MODAL premium (FacturaProveedorFormDialog) y las
// acciones de conciliación de tres vías (orden - recepción - factura): conciliar,
// autorizar pago y reabrir, cada una con confirmación y feedback de discrepancia.
// El proveedor se muestra por NOMBRE (nunca UUID) resolviendo el catálogo. El
// backend valida cada transición y la conciliación.
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
import { FacturaProveedor, Proveedor } from '../models/compras.models';
import { FacturaProveedorFormDialog } from './factura-proveedor-form-dialog';

@Component({
  selector: 'app-compras-facturas-proveedor',
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
  templateUrl: './facturas-proveedor.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../compras.scss',
})
export class ComprasFacturasProveedor {
  private readonly service = inject(ComprasService);
  private readonly nombres = inject(NombresComprasService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly puedeCrear = this.auth.tienePermiso('factura_proveedor', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso(
    'factura_proveedor',
    'cambiar_estado',
  );

  /** Buscador premium de proveedor para el filtro (sin teclear UUID). */
  protected readonly buscarProveedor = (filtro: string): Observable<PaginaResponse<Proveedor>> =>
    this.service.listarProveedores(filtro || null, 0, 10, 'activo');
  protected readonly etiquetaProveedor = (p: Proveedor): string => p.nombre;
  protected readonly detalleProveedor = (p: Proveedor): string | null => p.rfc;
  protected readonly iconoProveedor = (_p: Proveedor): string => 'local_shipping';

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'folio', encabezado: 'Folio proveedor' },
    { clave: 'proveedor', encabezado: 'Proveedor' },
    { clave: 'orden', encabezado: 'Orden de compra' },
    { clave: 'monto', encabezado: 'Monto', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Conciliación' },
    { clave: 'fecha', encabezado: 'Registrada' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'registrada', etiqueta: 'Registrada' },
    { valor: 'conciliada', etiqueta: 'Conciliada' },
    { valor: 'discrepancia', etiqueta: 'Discrepancia' },
    { valor: 'pagada', etiqueta: 'Pagada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<FacturaProveedor[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  /** Id del proveedor filtrado (se fija al elegir una sugerencia del buscador). */
  protected readonly filtroProveedorId = signal('');

  /** KPIs de la página cargada. */
  private readonly filas = computed(() => this.estado().datos ?? []);
  protected readonly totalRegistradas = computed(
    () => this.filas().filter((f) => f.estado === 'registrada').length,
  );
  protected readonly totalConciliadas = computed(
    () => this.filas().filter((f) => f.estado === 'conciliada').length,
  );
  protected readonly totalDiscrepancia = computed(
    () => this.filas().filter((f) => f.estado === 'discrepancia').length,
  );
  protected readonly totalPagadas = computed(
    () => this.filas().filter((f) => f.estado === 'pagada').length,
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

  /** RFC del proveedor (subdato de la celda). */
  rfcProveedor(id: string): string {
    return this.nombres.rfcProveedor(id);
  }

  /** Abre el diálogo explicativo de un KPI de facturas. */
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
    this.service
      .listarFacturasProveedor(
        this.filtroProveedorId() || null,
        null,
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

  /** Abre el modal de alta y recarga si se registró una factura. */
  nueva(): void {
    const ref = this.dialog.open(FacturaProveedorFormDialog, {
      width: 'min(760px, 96vw)',
      maxWidth: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
    });
    ref.afterClosed().subscribe((creada?: FacturaProveedor) => {
      if (creada) {
        this.toast.exito('Factura de proveedor registrada.');
        this.filtroEstado.set('');
        this.filtroProveedorId.set('');
        this.page.set(0);
        this.cargar();
      }
    });
  }

  puedeConciliar(f: FacturaProveedor): boolean {
    return this.puedeCambiarEstado && f.estado === 'registrada';
  }

  puedeAutorizar(f: FacturaProveedor): boolean {
    return this.puedeCambiarEstado && f.estado === 'conciliada';
  }

  puedeReabrir(f: FacturaProveedor): boolean {
    return this.puedeCambiarEstado && f.estado === 'discrepancia';
  }

  async conciliar(f: FacturaProveedor): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Conciliar en tres vías',
      mensaje: `Se contrastará la orden, la recepción y la factura ${f.folioProveedor}. Si algo no cuadra, quedará marcada con discrepancia. ¿Continuar?`,
      textoConfirmar: 'Conciliar',
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.service.conciliarFactura(f.id), {
        tipo: 'procesar',
        textoProceso: 'Conciliando factura…',
        textoExito: 'Conciliación lista',
      })
      .subscribe({
        next: (actualizada) => {
          if (actualizada.estado === 'discrepancia') {
            this.toast.info(
              'Conciliación con discrepancia: revisa la orden, la recepción y el monto de la factura.',
            );
          } else {
            this.toast.exito('Factura conciliada; ya puedes autorizar el pago.');
          }
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }

  async autorizarPago(f: FacturaProveedor): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Autorizar pago',
      mensaje: `Autorizar el pago de la factura ${f.folioProveedor}?`,
      textoConfirmar: 'Autorizar',
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.service.autorizarPagoFactura(f.id), {
        tipo: 'procesar',
        textoProceso: 'Autorizando pago…',
        textoExito: 'Pago autorizado',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Pago autorizado.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }

  /**
   * Reabre una factura en discrepancia (vuelve a "registrada") para re-conciliar
   * tras corregir la orden/recepción/folio. Pide confirmación por ser un cambio de
   * estado que reactiva el flujo de conciliación.
   */
  async reabrir(f: FacturaProveedor): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Reabrir factura',
      mensaje: `La factura ${f.folioProveedor} volverá a "registrada" para intentar conciliarla de nuevo. ¿Continuar?`,
      textoConfirmar: 'Reabrir',
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.service.reabrirFactura(f.id), {
        tipo: 'procesar',
        textoProceso: 'Reabriendo factura…',
        textoExito: 'Factura reabierta',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Factura reabierta; ya puedes volver a conciliarla.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }
}
