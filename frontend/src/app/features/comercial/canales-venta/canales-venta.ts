// =============================================================================
// Vista de Canales de venta (Req 63) — listado + acciones (modales)
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por nombre. El alta/edicion de un canal
// se hace en un MODAL animado (CanalVentaFormDialog), consistente con el resto de
// la plataforma. La baja es logica con confirmacion. Los KPIs abren el modal
// explicativo del indicador. Acciones gobernadas por canal_venta:{...}.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
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

import { CanalesVentaService } from '../services/catalogo.service';
import { CanalVenta } from '../models/comercial.models';
import { CanalVentaFormDialog, CanalVentaFormDialogData } from './canal-venta-form-dialog';

@Component({
  selector: 'app-comercial-canales-venta',
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    PageHeader,
    StateContainer,
    KpiTile,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './canales-venta.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './canales-venta.scss',
})
export class ComercialCanalesVenta {
  private readonly service = inject(CanalesVentaService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeCrear = this.auth.tienePermiso('canal_venta', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('canal_venta', 'actualizar');
  protected readonly puedeEliminar = this.auth.tienePermiso('canal_venta', 'eliminar');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly canales = signal<CanalVenta[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtro = signal('');
  /** Filtro de estado del listado: activos (por defecto), inactivos o todos. */
  protected readonly estadoFiltro = signal<'activo' | 'inactivo' | 'todos'>('activo');

  /** Numero de Canales activos en la pagina cargada (indicador enterprise). */
  protected readonly canalesActivos = computed<number>(
    () => this.canales().filter((c) => c.activo).length,
  );

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Nombre' },
    { clave: 'descripcion', encabezado: 'Descripción' },
    { clave: 'estado', encabezado: 'Estado', alineacion: 'centro' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service.listar(this.filtro(), this.page(), this.size(), this.estadoFiltro()).subscribe({
      next: (pagina) => {
        this.canales.set(pagina.content);
        this.total.set(pagina.totalElements);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  aplicarFiltro(valor: string): void {
    this.filtro.set(valor);
    this.page.set(0);
    this.cargar();
  }

  /** Cambia el filtro de estado (activo/inactivo/todos) y recarga desde la pagina 0. */
  cambiarEstadoFiltro(estado: 'activo' | 'inactivo' | 'todos'): void {
    this.estadoFiltro.set(estado);
    this.page.set(0);
    this.cargar();
  }

  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Abre el modal de alta de Canal y recarga si se creo. */
  nuevo(): void {
    this.abrirFormulario();
  }

  /** Abre el modal de edicion con los datos del Canal y recarga si cambio. */
  editar(canal: CanalVenta): void {
    this.abrirFormulario(canal);
  }

  /**
   * Abre el modal de formulario de Canal (alta si no se pasa `canal`, edicion si
   * se pasa) y recarga el listado cuando el dialogo confirma.
   */
  private abrirFormulario(canal?: CanalVenta): void {
    const data: CanalVentaFormDialogData = { canal };
    const ref = this.dialog.open(CanalVentaFormDialog, {
      width: 'min(720px, 96vw)',
      maxWidth: 'min(720px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((guardado?: CanalVenta) => {
      if (guardado) {
        this.toast.exito(canal ? 'Canal actualizado.' : 'Canal creado.');
        this.cargar();
      }
    });
  }

  /**
   * Abre el dialogo explicativo de un indicador de canales de venta (¿qué es? /
   * ¿cómo se calcula? / ¿por qué importa?). La clave debe coincidir con una del
   * catalogo central de indicadores.
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

  async eliminar(canal: CanalVenta): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja canal de venta',
      mensaje: `El canal "${canal.nombre}" quedara inactivo. Deseas continuar?`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.eliminar(canal.id).subscribe({
      next: () => {
        this.toast.exito('Canal dado de baja.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Reactiva un canal de venta dado de baja (Req 63.1), con confirmación. */
  async reactivar(canal: CanalVenta): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Reactivar canal de venta',
      mensaje: `El canal "${canal.nombre}" volverá a estar activo y disponible. ¿Continuar?`,
      textoConfirmar: 'Reactivar',
    });
    if (!ok) {
      return;
    }
    this.service.activar(canal.id).subscribe({
      next: () => {
        this.toast.exito('Canal reactivado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        if (e.status === 409) {
          this.toast.error('Ya existe un canal activo con ese nombre. Renómbralo antes de reactivar.');
          return;
        }
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
