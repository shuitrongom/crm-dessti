// =============================================================================
// Vista de Materiales / inventario base (Req 18) — listado premium
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por nombre, por estado (activo/
// inactivo/todos) y por condicion de stock bajo. El alta/edicion de un Material y
// el registro de movimientos se hacen en MODALES animados (MaterialFormDialog,
// MaterialMovimientoDialog). La baja es logica; los Materiales inactivos se
// pueden reactivar. Los KPIs (activos/inactivos/total, con conteos reales del
// backend) abren el modal explicativo del indicador. Acciones gobernadas por
// permiso material:{...} y movimiento_inventario:crear.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
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

import { MaterialesService } from '../services/inventario.service';
import { EstadoMaterial, Material } from '../models/operacion.models';
import { MaterialFormDialog, MaterialFormDialogData } from './material-form-dialog';
import {
  MaterialMovimientoDialog,
  MaterialMovimientoDialogData,
} from './material-movimiento-dialog';

@Component({
  selector: 'app-operacion-materiales',
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatTooltipModule,
    PageHeader,
    StateContainer,
    KpiTile,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './materiales.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './materiales.scss',
})
export class OperacionMateriales {
  private readonly service = inject(MaterialesService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeCrear = this.auth.tienePermiso('material', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('material', 'actualizar');
  protected readonly puedeMovimiento = this.auth.tienePermiso('movimiento_inventario', 'crear');
  protected readonly puedeEliminar = this.auth.tienePermiso('material', 'eliminar');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly materiales = signal<Material[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly nombre = signal('');
  protected readonly estado = signal<EstadoMaterial>('activo');
  protected readonly soloStockBajo = signal(false);

  /**
   * Conteos reales del backend para los indicadores (independientes del filtro y
   * de la paginacion de la tabla). Se cargan al inicio y tras cada cambio.
   */
  protected readonly totalActivos = signal<number | null>(null);
  protected readonly totalInactivos = signal<number | null>(null);
  /** Materiales activos por debajo de su stock minimo (alerta de reabastecimiento). */
  protected readonly totalStockBajo = signal<number | null>(null);
  protected readonly totalCatalogo = computed<number | null>(() => {
    const a = this.totalActivos();
    const inac = this.totalInactivos();
    return a === null || inac === null ? null : a + inac;
  });

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Material' },
    { clave: 'unidadMedida', encabezado: 'Unidad' },
    { clave: 'existencias', encabezado: 'Existencias', alineacion: 'fin' },
    { clave: 'stockMinimo', encabezado: 'Stock mínimo', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  constructor() {
    this.cargar();
    this.cargarConteos();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service
      .listar(this.nombre(), this.estado(), this.soloStockBajo(), this.page(), this.size())
      .subscribe({
        next: (pagina) => {
          this.materiales.set(pagina.content);
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
   * totalElements), independientes del filtro de la tabla, para que los KPIs no
   * mientan cuando se filtra.
   */
  cargarConteos(): void {
    this.service.listar(null, 'activo', false, 0, 1).subscribe({
      next: (p) => this.totalActivos.set(p.totalElements),
      error: () => this.totalActivos.set(null),
    });
    this.service.listar(null, 'inactivo', false, 0, 1).subscribe({
      next: (p) => this.totalInactivos.set(p.totalElements),
      error: () => this.totalInactivos.set(null),
    });
    // Materiales activos por debajo de su stock minimo: alerta de reabastecimiento.
    this.service.listar(null, 'activo', true, 0, 1).subscribe({
      next: (p) => this.totalStockBajo.set(p.totalElements),
      error: () => this.totalStockBajo.set(null),
    });
  }

  /**
   * Abre la explicacion del KPI de stock bajo y, ademas, activa el filtro "solo
   * stock bajo" para que el usuario vea de inmediato que materiales reabastecer.
   */
  verStockBajo(): void {
    this.abrirInfoKpi(
      'materiales_bajo_stock_minimo',
      'Por reabastecer',
      this.totalStockBajo() ?? 0,
      'conteo',
    );
    if (!this.soloStockBajo()) {
      this.estado.set('activo');
      this.alternarStockBajo(true);
    }
  }

  filtrarNombre(valor: string): void {
    this.nombre.set(valor);
    this.page.set(0);
    this.cargar();
  }

  cambiarEstado(valor: EstadoMaterial): void {
    this.estado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  alternarStockBajo(valor: boolean): void {
    this.soloStockBajo.set(valor);
    this.page.set(0);
    this.cargar();
  }

  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Abre el modal de alta de Material y recarga si se creó. */
  nuevo(): void {
    this.abrirFormulario();
  }

  /** Abre el modal de edición con los datos del Material y recarga si cambió. */
  editar(material: Material): void {
    this.abrirFormulario(material);
  }

  /**
   * Abre el modal de formulario de Material (alta si no se pasa `material`,
   * edición si se pasa) y recarga el listado y los conteos si el diálogo confirma.
   */
  private abrirFormulario(material?: Material): void {
    const data: MaterialFormDialogData = { material };
    const ref = this.dialog.open(MaterialFormDialog, {
      width: 'min(720px, 96vw)',
      maxWidth: 'min(720px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((guardado?: Material) => {
      if (guardado) {
        this.toast.exito(material ? 'Material actualizado.' : 'Material creado.');
        this.cargar();
        this.cargarConteos();
      }
    });
  }

  /** Abre el modal para registrar un movimiento sobre el Material. */
  abrirMovimiento(material: Material): void {
    const data: MaterialMovimientoDialogData = { material };
    const ref = this.dialog.open(MaterialMovimientoDialog, {
      width: 'min(620px, 96vw)',
      maxWidth: 'min(620px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((registrado?: boolean) => {
      if (registrado) {
        this.toast.exito('Movimiento registrado.');
        this.cargar();
      }
    });
  }

  /**
   * Abre el dialogo explicativo de un indicador de materiales (¿qué es? / ¿cómo se
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

  /** Baja lógica de un Material con confirmación (Req 18). */
  async eliminar(material: Material): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja material',
      mensaje: `El material "${material.nombre}" quedara inactivo. Deseas continuar?`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.eliminar(material.id).subscribe({
      next: () => {
        this.toast.exito('Material dado de baja.');
        this.cargar();
        this.cargarConteos();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Reactiva un Material dado de baja (Req 18, 3.1). */
  activar(material: Material): void {
    this.service.activar(material.id).subscribe({
      next: () => {
        this.toast.exito('Material reactivado.');
        this.cargar();
        this.cargarConteos();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
