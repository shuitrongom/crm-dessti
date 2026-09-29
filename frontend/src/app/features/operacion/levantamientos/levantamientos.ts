// =============================================================================
// Vista de Levantamientos de Sitio (Req 16) — listado premium
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por estado. El alta se hace en un MODAL
// animado (LevantamientoFormDialog) y la gestion de fotos en otro MODAL
// (LevantamientoFotosDialog). La accion de completar pide confirmacion. Los KPIs
// (en proceso / completados, con conteos reales del backend) abren el modal
// explicativo del indicador. Acciones gobernadas por permiso
// levantamiento_sitio:{...}.
// =============================================================================

import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { ChipEstado, VarianteChipEstado } from '../../../shared/components/chip-estado/chip-estado';
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

import { LevantamientosService } from '../services/instalacion.service';
import {
  ETIQUETA_ESTADO_LEVANTAMIENTO,
  EstadoLevantamiento,
  LevantamientoSitio,
} from '../models/operacion.models';
import { LevantamientoFormDialog } from './levantamiento-form-dialog';
import {
  LevantamientoFotosDialog,
  LevantamientoFotosDialogData,
} from './levantamiento-fotos-dialog';

@Component({
  selector: 'app-operacion-levantamientos',
  imports: [
    DatePipe,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    KpiTile,
    DataTable,
    CeldaTablaDirective,
    ChipEstado,
  ],
  templateUrl: './levantamientos.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './levantamientos.scss',
})
export class OperacionLevantamientos {
  private readonly service = inject(LevantamientosService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeCrear = this.auth.tienePermiso('levantamiento_sitio', 'crear');
  protected readonly puedeCompletar = this.auth.tienePermiso(
    'levantamiento_sitio',
    'cambiar_estado',
  );
  private readonly mapaEstado = ETIQUETA_ESTADO_LEVANTAMIENTO;
  protected readonly estados: EstadoLevantamiento[] = ['en_proceso', 'completado'];

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly levantamientos = signal<LevantamientoSitio[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly estado = signal<EstadoLevantamiento | ''>('');

  /** Conteos reales del backend para los indicadores (independientes del filtro). */
  protected readonly totalEnProceso = signal<number | null>(null);
  protected readonly totalCompletados = signal<number | null>(null);

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'tipoSuperficie', encabezado: 'Superficie' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'completadoEn', encabezado: 'Completado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  constructor() {
    this.cargar();
    this.cargarConteos();
  }

  /** Etiqueta legible del estado del levantamiento; devuelve el valor crudo si no mapea. */
  protected etiquetaEstado(estado: string): string {
    return this.mapaEstado[estado as EstadoLevantamiento] ?? estado;
  }

  /**
   * Variante semantica del Chip_Estado segun el estado del Levantamiento:
   * en_proceso -> advertencia (trabajo pendiente), completado -> exito.
   */
  protected varianteEstado(estado: EstadoLevantamiento): VarianteChipEstado {
    return estado === 'completado' ? 'exito' : 'advertencia';
  }

  cargar(): void {
    this.fase.set('cargando');
    const estado = this.estado() || null;
    this.service.listar(estado, this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.levantamientos.set(pagina.content);
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
   * Carga los conteos de los indicadores con consultas de tamano 1 (solo
   * totalElements), independientes del filtro de la tabla.
   */
  cargarConteos(): void {
    this.service.listar('en_proceso', 0, 1).subscribe({
      next: (p) => this.totalEnProceso.set(p.totalElements),
      error: () => this.totalEnProceso.set(null),
    });
    this.service.listar('completado', 0, 1).subscribe({
      next: (p) => this.totalCompletados.set(p.totalElements),
      error: () => this.totalCompletados.set(null),
    });
  }

  cambiarFiltro(valor: EstadoLevantamiento | ''): void {
    this.estado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Abre el modal de alta de Levantamiento y recarga si se creó. */
  nuevo(): void {
    const ref = this.dialog.open(LevantamientoFormDialog, {
      width: 'min(720px, 96vw)',
      maxWidth: 'min(720px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
    });
    ref.afterClosed().subscribe((creado?: LevantamientoSitio) => {
      if (creado) {
        this.toast.exito('Levantamiento creado.');
        this.cargar();
        this.cargarConteos();
      }
    });
  }

  /** Abre el modal de gestión de fotos del Levantamiento. */
  abrirFotos(levantamiento: LevantamientoSitio): void {
    const data: LevantamientoFotosDialogData = {
      levantamiento,
      puedeAgregar: this.puedeCrear,
    };
    this.dialog.open(LevantamientoFotosDialog, {
      width: 'min(680px, 96vw)',
      maxWidth: 'min(680px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
  }

  /**
   * Abre el dialogo explicativo de un indicador de levantamientos. La clave debe
   * coincidir con una del catálogo de indicadores.
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

  /** Marca un Levantamiento como completado con confirmacion (Req 16.4). */
  async completar(levantamiento: LevantamientoSitio): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Completar levantamiento',
      mensaje: 'El levantamiento quedara marcado como completado. Deseas continuar?',
      textoConfirmar: 'Completar',
    });
    if (!ok) {
      return;
    }
    this.service.completar(levantamiento.id).subscribe({
      next: () => {
        this.toast.exito('Levantamiento completado.');
        this.cargar();
        this.cargarConteos();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
