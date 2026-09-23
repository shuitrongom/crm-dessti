// =============================================================================
// Vista de administracion de Giros (super_admin) (Req 9)
// -----------------------------------------------------------------------------
// Tablero de Giros (verticales de negocio): fila de indicadores KPI (totales,
// activos, inactivos), lista paginada con filtro por estado, alta de Giro
// (dialogo) y activacion/desactivacion con confirmacion para la desactivacion
// (accion sensible que puede fallar con 422 si el Giro esta en uso). Estados de
// carga/vacio/error consistentes.
//
// Los conteos globales de los KPI se calculan sin depender de un endpoint nuevo:
// se consultan en paralelo (forkJoin) paginas de tamano 1 por estado y se lee
// `totalElements` de cada respuesta (mismo patron que la vista de Empresas).
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { forkJoin } from 'rxjs';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { StatCard } from '../../../shared/components/stat-card/stat-card';
import {
  ColumnaTabla,
  DataTable,
  CeldaTablaDirective,
  CambioPagina,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { AuthService } from '../../../core/auth/auth.service';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { GirosService } from '../services/giros.service';
import { Giro } from '../models/plataforma.models';
import { GiroDialog } from './giro-dialog';

/** Conteos globales de Giros por estado para los indicadores KPI. */
interface ConteosGiros {
  total: number;
  activos: number;
  inactivos: number;
}

@Component({
  selector: 'app-plataforma-giros',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatMenuModule,
    MatTooltipModule,
    PageHeader,
    StateContainer,
    StatCard,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './giros.html',
  styleUrl: './giros.scss',
})
export class PlataformaGiros {
  private readonly service = inject(GirosService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  /**
   * Columnas de la tabla de Giros. La celda de nombre concentra el nombre
   * visible y la clave (dos lineas), por lo que no hay columna de clave aparte.
   */
  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Giro' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'completitud', encabezado: 'Completitud' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  /** Estado de la pagina de Giros. */
  protected readonly estado = signal<EstadoSolicitud<Giro[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  /** Filtro por estado: `null` = todos, `true` = activos, `false` = inactivos. */
  protected readonly filtroActivo = signal<boolean | null>(null);

  /** Conteos globales por estado para los KPI; `null` mientras se resuelven. */
  protected readonly conteos = signal<ConteosGiros | null>(null);
  /** `true` mientras se calculan los conteos KPI (estado de carga sutil). */
  protected readonly cargandoConteos = signal(true);

  /** Valor mostrado en cada KPI: un guion mientras carga o si no hay dato. */
  protected readonly kpiTotal = computed(() => this.valorKpi(this.conteos()?.total));
  protected readonly kpiActivos = computed(() => this.valorKpi(this.conteos()?.activos));
  protected readonly kpiInactivos = computed(() => this.valorKpi(this.conteos()?.inactivos));

  /** Permisos de Giros (deny-by-default). */
  protected readonly puedeCrear = this.auth.tienePermiso('giro', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('giro', 'actualizar');
  protected readonly puedeActivar = this.auth.tienePermiso('giro', 'activar');
  protected readonly puedeDesactivar = this.auth.tienePermiso('giro', 'desactivar');
  protected readonly puedeEliminar = this.auth.tienePermiso('giro', 'eliminar');

  constructor() {
    this.cargar();
    this.cargarConteos();
  }

  /** Carga la pagina actual de Giros (respeta filtro + paginacion). */
  cargar(): void {
    this.estado.set(cargando());
    this.service.listar(this.filtroActivo(), this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.total.set(pagina.totalElements);
        this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  /**
   * Calcula los conteos globales por estado en paralelo, leyendo `totalElements`
   * de paginas de tamano 1. Ante un error se muestran guiones sin romper la vista.
   */
  cargarConteos(): void {
    this.cargandoConteos.set(true);
    forkJoin({
      total: this.service.listar(null, 0, 1),
      activos: this.service.listar(true, 0, 1),
      inactivos: this.service.listar(false, 0, 1),
    }).subscribe({
      next: (r) => {
        this.conteos.set({
          total: r.total.totalElements,
          activos: r.activos.totalElements,
          inactivos: r.inactivos.totalElements,
        });
        this.cargandoConteos.set(false);
      },
      error: () => {
        this.conteos.set(null);
        this.cargandoConteos.set(false);
      },
    });
  }

  /** Valor a mostrar en un KPI: un guion mientras carga o si no hay dato. */
  private valorKpi(valor: number | undefined): string {
    if (this.cargandoConteos() || valor === undefined) {
      return '—';
    }
    return String(valor);
  }

  /** Etiqueta humana del estado de un Giro. */
  protected etiquetaEstado(activo: boolean): string {
    return activo ? 'Activo' : 'Inactivo';
  }

  /**
   * Etiqueta de completitud del Giro: "Completo" cuando tiene reglas de negocio
   * programadas (un vertical), o "Base" cuando solo hereda los modulos base.
   */
  protected etiquetaCompletitud(tieneReglasNegocio: boolean): string {
    return tieneReglasNegocio ? 'Completo' : 'Base';
  }

  /**
   * Tooltip explicativo de la completitud del Giro (accesible: el significado no
   * depende solo del color, sino tambien del texto del chip y de este tooltip).
   */
  protected tooltipCompletitud(tieneReglasNegocio: boolean): string {
    return tieneReglasNegocio
      ? 'Incluye modulos base + reglas de negocio especificas de este giro.'
      : 'Solo incluye los modulos base compartidos. Las reglas de negocio especificas de este giro aun no estan programadas.';
  }

  /** Reacciona al cambio de filtro por estado. */
  cambiarFiltro(activo: boolean | null): void {
    this.filtroActivo.set(activo);
    this.page.set(0);
    this.cargar();
  }

  /** Reacciona al cambio de pagina. */
  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Abre el dialogo de alta de Giro (Req 9). */
  crear(): void {
    const ref = this.dialog.open(GiroDialog, {
      width: 'min(560px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
    });
    ref.afterClosed().subscribe((creado) => {
      if (creado) {
        this.toast.exito('Giro creado correctamente.');
        this.cargar();
        this.cargarConteos();
      }
    });
  }

  /** Abre el dialogo de edicion de un Giro (nombre visible y descripcion). */
  editar(giro: Giro): void {
    const ref = this.dialog.open(GiroDialog, {
      width: 'min(560px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      data: { giro },
    });
    ref.afterClosed().subscribe((actualizado) => {
      if (actualizado) {
        this.toast.exito('Giro actualizado correctamente.');
        this.cargar();
      }
    });
  }

  /** Activa un Giro (Req 9). */
  activar(giro: Giro): void {
    this.service.activar(giro.id).subscribe({
      next: () => {
        this.toast.exito(`Giro "${giro.nombreVisible}" activado.`);
        this.cargar();
        this.cargarConteos();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /**
   * Desactiva un Giro con confirmacion (accion sensible). Puede fallar con 422
   * si el Giro esta en uso por alguna Empresa; en tal caso se muestra el mensaje
   * del backend.
   */
  async desactivar(giro: Giro): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Desactivar giro',
      mensaje: `Desactivar "${giro.nombreVisible}" impedira asignarlo a nuevas empresas. Deseas continuar?`,
      textoConfirmar: 'Desactivar',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.desactivar(giro.id).subscribe({
      next: () => {
        this.toast.exito(`Giro "${giro.nombreVisible}" desactivado.`);
        this.cargar();
        this.cargarConteos();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /**
   * Motivo por el que un Giro no se puede eliminar desde la UI, o `null` si el
   * boton debe permanecer habilitado. Un Giro con reglas de negocio ya es
   * definitivo (el frontend lo sabe por `tieneReglasNegocio`), asi que se
   * bloquea de forma proactiva con este mensaje. La regla de "en uso por
   * empresas" no es visible en el cliente: se permite el clic y se muestra el
   * mensaje del backend (422) si aplica.
   */
  protected motivoNoEliminable(giro: Giro): string | null {
    return giro.tieneReglasNegocio
      ? 'No se puede eliminar: el giro ya tiene reglas de negocio y es definitivo.'
      : null;
  }

  /**
   * Elimina un Giro con confirmacion (accion destructiva e irreversible). Puede
   * fallar con 422 si el Giro es definitivo o esta en uso por alguna Empresa; en
   * tal caso se muestra el mensaje del backend tal cual.
   */
  async eliminar(giro: Giro): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Eliminar giro',
      mensaje: `Se eliminará el giro "${giro.nombreVisible}". Esta acción no se puede deshacer. ¿Continuar?`,
      textoConfirmar: 'Eliminar',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.eliminar(giro.id).subscribe({
      next: () => {
        this.toast.exito(`Giro "${giro.nombreVisible}" eliminado.`);
        this.cargar();
        this.cargarConteos();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
