// =============================================================================
// Vista de Productos (Req 59, V61) — catalogo (listado + acciones)
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por nombre y por estado, miniatura de
// la foto y chip activo/inactivo. El alta y la edicion se hacen en un MODAL
// animado (ProductoFormDialog). Cada producto puede activarse o desactivarse
// (baja/reactivacion logica) desde sus acciones. Los KPIs abren el modal
// explicativo del indicador. Acciones gobernadas por permiso (deny-by-default).
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
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
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import {
  IndicadorInfoDialog,
  type DatosIndicadorInfo,
} from '../../../shared/indicadores/indicador-info-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ProductosService } from '../services/catalogo.service';
import { Producto } from '../models/comercial.models';
import { ProductoFormDialog, ProductoFormDialogData } from './producto-form-dialog';

/** Estado del filtro del listado. */
type FiltroEstado = 'activo' | 'inactivo' | 'todos';

@Component({
  selector: 'app-comercial-productos',
  imports: [
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    PageHeader,
    StateContainer,
    KpiTile,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './productos.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './productos.scss',
})
export class ComercialProductos {
  private readonly service = inject(ProductosService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeCrear = this.auth.tienePermiso('producto', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('producto', 'actualizar');
  protected readonly puedeEliminar = this.auth.tienePermiso('producto', 'eliminar');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly productos = signal<Producto[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtro = signal('');
  /** Filtro por estado del listado (activo por defecto). */
  protected readonly estado = signal<FiltroEstado>('activo');

  /**
   * Totales REALES del catalogo (no de la pagina): se leen del totalElements de
   * consultas de conteo por estado en el backend. Empiezan en null (— mientras
   * cargan) y se refrescan tras cada alta/baja/reactivacion.
   */
  protected readonly totalActivos = signal<number | null>(null);
  protected readonly totalInactivos = signal<number | null>(null);
  /** Total del catalogo completo (activos + inactivos). */
  protected readonly totalCatalogo = computed<number | null>(() => {
    const a = this.totalActivos();
    const inac = this.totalInactivos();
    return a === null || inac === null ? null : a + inac;
  });

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'foto', encabezado: 'Foto' },
    { clave: 'nombre', encabezado: 'Nombre' },
    { clave: 'unidad', encabezado: 'Unidad', ocultarEnMovil: true },
    { clave: 'descripcion', encabezado: 'Descripción', ocultarEnMovil: true },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  constructor() {
    this.cargar();
    this.cargarConteos();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service.listar(this.filtro(), this.page(), this.size(), this.estado()).subscribe({
      next: (pagina) => {
        this.productos.set(pagina.content);
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
   * Recalcula los TOTALES reales del catalogo (activos e inactivos) del tenant,
   * leyendo el totalElements de consultas de tamano 1 por estado. Independiente
   * del filtro de la tabla, de modo que los KPIs no mienten cuando se filtra.
   */
  cargarConteos(): void {
    this.service.listar(null, 0, 1, 'activo').subscribe({
      next: (p) => this.totalActivos.set(p.totalElements),
      error: () => this.totalActivos.set(null),
    });
    this.service.listar(null, 0, 1, 'inactivo').subscribe({
      next: (p) => this.totalInactivos.set(p.totalElements),
      error: () => this.totalInactivos.set(null),
    });
  }

  aplicarFiltro(valor: string): void {
    this.filtro.set(valor);
    this.page.set(0);
    this.cargar();
  }

  /** Cambia el filtro por estado (activo/inactivo/todos) y recarga. */
  cambiarEstado(valor: FiltroEstado): void {
    this.estado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Abre el modal de alta de Producto y recarga si se creo (Req 59.1). */
  nuevo(): void {
    this.abrirFormulario();
  }

  /** Abre el modal de edicion con los datos del Producto y recarga si cambio. */
  editar(producto: Producto): void {
    this.abrirFormulario(producto);
  }

  /**
   * Abre el modal de formulario de Producto (alta si no se pasa `producto`,
   * edicion si se pasa) y recarga el listado cuando el dialogo confirma.
   */
  private abrirFormulario(producto?: Producto): void {
    const data: ProductoFormDialogData = { producto };
    const ref = this.dialog.open(ProductoFormDialog, {
      width: 'min(920px, 96vw)',
      maxWidth: 'min(920px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((guardado?: Producto) => {
      if (guardado) {
        this.toast.exito(producto ? 'Producto actualizado.' : 'Producto creado.');
        this.cargar();
        this.cargarConteos();
      }
    });
  }

  /**
   * Abre el dialogo explicativo de un indicador del catalogo (¿qué es? / ¿cómo se
   * calcula? / ¿por qué importa?). La clave debe coincidir con una del catalogo
   * central de indicadores.
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

  /** Da de baja logica un Producto con confirmacion (Req 59.6). */
  async desactivar(producto: Producto): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Desactivar producto',
      mensaje: `El producto "${producto.nombre}" quedara inactivo y no se podra cotizar. Deseas continuar?`,
      textoConfirmar: 'Desactivar',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.service.eliminar(producto.id), {
        tipo: 'eliminar',
        textoProceso: 'Desactivando…',
        textoExito: 'Producto desactivado',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Producto desactivado.');
          this.cargar();
          this.cargarConteos();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }

  /** Reactiva un Producto dado de baja (Req 59.6). */
  activar(producto: Producto): void {
    this.overlay
      .ejecutar(this.service.activar(producto.id), {
        tipo: 'guardar',
        textoProceso: 'Activando…',
        textoExito: 'Producto activado',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Producto activado.');
          this.cargar();
          this.cargarConteos();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }
}
