// =============================================================================
// Vista de Permisos de Instalacion (Req 17) — listado premium
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtros por tipo y estado. El alta se hace en
// un MODAL animado (PermisoFormDialog) y la decision (aprobar/rechazar) pide
// confirmacion, solo cuando el permiso esta solicitado. Los KPIs (solicitados/
// aprobados/rechazados, con conteos reales del backend) abren el modal
// explicativo del indicador. Acciones gobernadas por permiso
// permiso_instalacion:{...}.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import {
  ChipEstado,
  VarianteChipEstado,
} from '../../../shared/components/chip-estado/chip-estado';
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

import { PermisosService } from '../services/instalacion.service';
import {
  ETIQUETA_ESTADO_PERMISO,
  ETIQUETA_TIPO_PERMISO,
  EstadoPermiso,
  PermisoInstalacion,
  TipoPermiso,
} from '../models/operacion.models';
import { PermisoFormDialog } from './permiso-form-dialog';

@Component({
  selector: 'app-operacion-permisos',
  imports: [
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
  templateUrl: './permisos.html',
  styleUrl: './permisos.scss',
})
export class OperacionPermisos {
  private readonly service = inject(PermisosService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeCrear = this.auth.tienePermiso('permiso_instalacion', 'crear');
  protected readonly puedeDecidir = this.auth.tienePermiso('permiso_instalacion', 'cambiar_estado');
  private readonly mapaTipo = ETIQUETA_TIPO_PERMISO;
  private readonly mapaEstado = ETIQUETA_ESTADO_PERMISO;
  protected readonly tipos: TipoPermiso[] = ['municipal', 'arrendador'];
  protected readonly estadosFiltro: EstadoPermiso[] = ['solicitado', 'aprobado', 'rechazado'];

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly permisos = signal<PermisoInstalacion[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly tipoFiltro = signal<TipoPermiso | ''>('');
  protected readonly estadoFiltro = signal<EstadoPermiso | ''>('');

  /** Conteos reales del backend para los indicadores (independientes del filtro). */
  protected readonly totalSolicitados = signal<number | null>(null);
  protected readonly totalAprobados = signal<number | null>(null);
  protected readonly totalRechazados = signal<number | null>(null);

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'tipo', encabezado: 'Tipo' },
    { clave: 'fechaVencimiento', encabezado: 'Vencimiento' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  constructor() {
    this.cargar();
    this.cargarConteos();
  }

  /** Etiqueta legible del tipo de permiso; devuelve el valor crudo si no mapea. */
  protected etiquetaTipo(tipo: string): string {
    return this.mapaTipo[tipo as TipoPermiso] ?? tipo;
  }

  /** Etiqueta legible del estado de permiso; devuelve el valor crudo si no mapea. */
  protected etiquetaEstado(estado: string): string {
    return this.mapaEstado[estado as EstadoPermiso] ?? estado;
  }

  /** Variante semantica del chip de estado: solicitado→info, aprobado→exito, rechazado→error. */
  protected varianteEstado(estado: EstadoPermiso): VarianteChipEstado {
    const mapa: Record<EstadoPermiso, VarianteChipEstado> = {
      solicitado: 'info',
      aprobado: 'exito',
      rechazado: 'error',
    };
    return mapa[estado] ?? 'neutro';
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service
      .listar(this.tipoFiltro() || null, this.estadoFiltro() || null, this.page(), this.size())
      .subscribe({
        next: (pagina) => {
          this.permisos.set(pagina.content);
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
    this.service.listar(null, 'solicitado', 0, 1).subscribe({
      next: (p) => this.totalSolicitados.set(p.totalElements),
      error: () => this.totalSolicitados.set(null),
    });
    this.service.listar(null, 'aprobado', 0, 1).subscribe({
      next: (p) => this.totalAprobados.set(p.totalElements),
      error: () => this.totalAprobados.set(null),
    });
    this.service.listar(null, 'rechazado', 0, 1).subscribe({
      next: (p) => this.totalRechazados.set(p.totalElements),
      error: () => this.totalRechazados.set(null),
    });
  }

  cambiarTipo(valor: TipoPermiso | ''): void {
    this.tipoFiltro.set(valor);
    this.page.set(0);
    this.cargar();
  }

  cambiarEstadoFiltro(valor: EstadoPermiso | ''): void {
    this.estadoFiltro.set(valor);
    this.page.set(0);
    this.cargar();
  }

  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Abre el modal de alta de Permiso y recarga si se registró. */
  nuevo(): void {
    const ref = this.dialog.open(PermisoFormDialog, {
      width: 'min(680px, 96vw)',
      maxWidth: 'min(680px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
    });
    ref.afterClosed().subscribe((creado?: PermisoInstalacion) => {
      if (creado) {
        this.toast.exito('Permiso registrado.');
        this.cargar();
        this.cargarConteos();
      }
    });
  }

  /**
   * Abre el dialogo explicativo de un indicador de permisos. La clave debe
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

  /** Aprueba o rechaza un Permiso solicitado con confirmacion (Req 17.2). */
  async decidir(permiso: PermisoInstalacion, accion: 'aprobar' | 'rechazar'): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: accion === 'aprobar' ? 'Aprobar permiso' : 'Rechazar permiso',
      mensaje: `El permiso quedara ${accion === 'aprobar' ? 'aprobado' : 'rechazado'}. Deseas continuar?`,
      textoConfirmar: accion === 'aprobar' ? 'Aprobar' : 'Rechazar',
      destructiva: accion === 'rechazar',
    });
    if (!ok) {
      return;
    }
    this.service.cambiarEstado(permiso.id, accion).subscribe({
      next: () => {
        this.toast.exito(accion === 'aprobar' ? 'Permiso aprobado.' : 'Permiso rechazado.');
        this.cargar();
        this.cargarConteos();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
