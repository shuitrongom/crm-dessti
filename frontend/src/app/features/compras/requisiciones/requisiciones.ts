// =============================================================================
// Vista de Requisiciones de compra (Req 30) — premium
// -----------------------------------------------------------------------------
// Listado paginado con filtro por estado y KPIs clicables. El alta y la
// generación de Orden de Compra se hacen mediante MODALES premium
// (RequisicionFormDialog, GenerarOcDialog). Las acciones de cambio de estado se
// gobiernan por la máquina de estados del backend (borrador → enviada →
// aprobada/rechazada; cancelar desde borrador/enviada) y por permiso atómico
// (deny-by-default, segregación V75 para aprobar/rechazar). Las partidas se
// muestran por NOMBRE de material (nunca UUID) resolviendo el catálogo.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
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
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { RequierePermiso } from '../../../shared/directivas/requiere-permiso';
import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { humanizarEstado, tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { NombresInventarioService } from '../../operacion/services/nombres-inventario.service';
import { ComprasService } from '../services/compras.service';
import { RequisicionCompra } from '../models/compras.models';
import { RequisicionFormDialog } from './requisicion-form-dialog';
import { GenerarOcDialog, GenerarOcDialogData } from './generar-oc-dialog';

/** Transiciones ofrecidas en la UI por estado actual (el backend valida). */
const TRANSICIONES: Record<string, { estado: string; etiqueta: string; destructiva: boolean }[]> = {
  borrador: [
    { estado: 'enviada', etiqueta: 'Enviar a revisión', destructiva: false },
    { estado: 'cancelada', etiqueta: 'Cancelar', destructiva: true },
  ],
  enviada: [
    { estado: 'aprobada', etiqueta: 'Aprobar', destructiva: false },
    { estado: 'rechazada', etiqueta: 'Rechazar', destructiva: true },
    { estado: 'cancelada', etiqueta: 'Cancelar', destructiva: true },
  ],
  // 'aprobada' ya no se cancela: deriva en Orden de Compra (no hay transición de salida).
};

@Component({
  selector: 'app-compras-requisiciones',
  imports: [
    DatePipe,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    PageHeader,
    StateContainer,
    KpiTile,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
    RequierePermiso,
  ],
  templateUrl: './requisiciones.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../compras.scss',
})
export class ComprasRequisiciones {
  private readonly service = inject(ComprasService);
  private readonly nombres = inject(NombresInventarioService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly puedeCrear = this.auth.tienePermiso('requisicion_compra', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso(
    'requisicion_compra',
    'cambiar_estado',
  );
  /** Segregación de funciones (V75): aprobar/rechazar exige permiso dedicado. */
  protected readonly puedeAprobar = this.auth.tienePermiso('requisicion_compra', 'aprobar');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'id', encabezado: 'Folio' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'materiales', encabezado: 'Materiales' },
    { clave: 'orden', encabezado: 'Orden de compra' },
    { clave: 'fecha', encabezado: 'Creada' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'borrador', etiqueta: 'Borrador' },
    { valor: 'enviada', etiqueta: 'Enviada' },
    { valor: 'aprobada', etiqueta: 'Aprobada' },
    { valor: 'rechazada', etiqueta: 'Rechazada' },
    { valor: 'cancelada', etiqueta: 'Cancelada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<RequisicionCompra[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');

  /** KPIs de la página cargada (por estado). */
  private readonly filas = computed(() => this.estado().datos ?? []);
  protected readonly totalMostrado = computed(() => this.filas().length);
  protected readonly totalBorrador = computed(
    () => this.filas().filter((r) => r.estado === 'borrador').length,
  );
  protected readonly totalEnviadas = computed(
    () => this.filas().filter((r) => r.estado === 'enviada').length,
  );
  protected readonly totalAprobadas = computed(
    () => this.filas().filter((r) => r.estado === 'aprobada').length,
  );

  constructor() {
    // Precarga el catálogo de materiales para mostrar las partidas por nombre.
    this.nombres.cargar().subscribe({ next: () => {}, error: () => {} });
    this.cargar();
  }

  /** Nombre del material (nunca el UUID). */
  nombreMaterial(id: string): string {
    return this.nombres.nombreMaterial(id);
  }

  /** Resumen legible de los materiales de una requisición (para la celda y el tooltip). */
  resumenMateriales(req: RequisicionCompra): string {
    return req.partidas
      .map((p) => `${this.nombreMaterial(p.materialId)} × ${p.cantidad}`)
      .join(', ');
  }

  /** Abre el diálogo explicativo de un KPI de requisiciones. */
  abrirInfoKpi(clave: string, etiqueta: string, valor: number): void {
    const datos: DatosIndicadorInfo = { clave, etiqueta, valor, unidad: 'conteo' };
    this.dialog.open(IndicadorInfoDialog, {
      data: datos,
      width: '32rem',
      maxWidth: '92vw',
      autoFocus: false,
    });
  }

  /**
   * Transiciones ofrecidas para un estado. A diferencia de antes, NO se ocultan
   * las de aprobar/rechazar cuando falta el permiso: se muestran igualmente pero
   * la vista las marca como "requiere autorización" (directiva appRequierePermiso),
   * para que el usuario sepa que la acción existe y qué permiso necesita. Las que
   * usan 'cambiar_estado' (enviar/cancelar) sí se ocultan si no tiene ese permiso,
   * pues es el permiso base del flujo.
   */
  transicionesDe(estado: string): { estado: string; etiqueta: string; destructiva: boolean }[] {
    return (TRANSICIONES[estado] ?? []).filter((t) => {
      if (this.requiereAprobar(t.estado)) {
        return true; // Se muestra siempre; el gating visual lo pone la directiva.
      }
      return this.puedeCambiarEstado;
    });
  }

  /** `true` si la transición hacia ese estado exige el permiso dedicado 'aprobar' (V75). */
  requiereAprobar(estadoDestino: string): boolean {
    return estadoDestino === 'aprobada' || estadoDestino === 'rechazada';
  }

  /** `true` si la requisición aprobada aún no ha generado su orden de compra. */
  puedeGenerarOrden(req: RequisicionCompra): boolean {
    return req.estado === 'aprobada' && !req.ordenCompraId && this.puedeCambiarEstado;
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarRequisiciones(this.filtroEstado() || null, this.page(), this.size())
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

  aplicarFiltro(valor: string): void {
    this.filtroEstado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  /** Abre el modal de alta y recarga si se creó. */
  nueva(): void {
    const ref = this.dialog.open(RequisicionFormDialog, {
      width: 'min(760px, 96vw)',
      maxWidth: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
    });
    ref.afterClosed().subscribe((creada?: RequisicionCompra) => {
      if (creada) {
        this.toast.exito('Requisición creada en borrador.');
        this.filtroEstado.set('');
        this.page.set(0);
        this.cargar();
      }
    });
  }

  /** Abre el modal para generar la orden de compra desde una requisición aprobada. */
  generarOrden(req: RequisicionCompra): void {
    const data: GenerarOcDialogData = { requisicion: req };
    const ref = this.dialog.open(GenerarOcDialog, {
      width: 'min(760px, 96vw)',
      maxWidth: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((actualizada?: RequisicionCompra) => {
      if (actualizada) {
        this.toast.exito('Orden de compra generada.');
        this.cargar();
      }
    });
  }

  async cambiarEstado(
    req: RequisicionCompra,
    transicion: { estado: string; etiqueta: string; destructiva: boolean },
  ): Promise<void> {
    if (transicion.destructiva) {
      const ok = await this.confirm.confirmar({
        titulo: transicion.etiqueta,
        mensaje: `¿Confirmas "${transicion.etiqueta.toLowerCase()}" de la requisición?`,
        textoConfirmar: transicion.etiqueta,
        destructiva: true,
      });
      if (!ok) {
        return;
      }
    }
    this.overlay
      .ejecutar(this.service.cambiarEstadoRequisicion(req.id, transicion.estado), {
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
