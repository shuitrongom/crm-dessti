// =============================================================================
// Vista de Listas de precios (Req 59) — listado + acciones (modales)
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por nombre. El alta/edicion de una
// lista y la asignacion de precios se hacen en MODALES animados (ListaFormDialog,
// AsignarPrecioDialog). La baja es logica con confirmacion. Los KPIs abren el
// modal explicativo del indicador. Acciones gobernadas por lista_precios:{...}.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
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
import {
  IndicadorInfoDialog,
  type DatosIndicadorInfo,
} from '../../../shared/indicadores/indicador-info-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ListasPreciosService } from '../services/catalogo.service';
import { ListaPrecios } from '../models/comercial.models';
import { ListaFormDialog, ListaFormDialogData } from './lista-form-dialog';
import { AsignarPrecioDialog, AsignarPrecioDialogData } from './asignar-precio-dialog';

@Component({
  selector: 'app-comercial-listas-precios',
  imports: [
    MatFormFieldModule,
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
  templateUrl: './listas-precios.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './listas-precios.scss',
})
export class ComercialListasPrecios {
  private readonly service = inject(ListasPreciosService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeCrear = this.auth.tienePermiso('lista_precios', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('lista_precios', 'actualizar');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly listas = signal<ListaPrecios[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtro = signal('');

  /**
   * Numero de Listas VIGENTES hoy en la pagina cargada: activas y cuya ventana de
   * vigencia (inicio..fin, fin abierto si es null) incluye la fecha actual.
   */
  protected readonly listasVigentes = computed<number>(() => {
    const hoy = new Date().toISOString().slice(0, 10);
    return this.listas().filter(
      (l) => l.activo && l.vigenciaInicio <= hoy && (l.vigenciaFin == null || l.vigenciaFin >= hoy),
    ).length;
  });

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Nombre' },
    { clave: 'prioridad', encabezado: 'Prioridad', alineacion: 'centro' },
    { clave: 'segmento', encabezado: 'Segmento' },
    { clave: 'vigencia', encabezado: 'Vigencia' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service.listar(this.filtro(), this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.listas.set(pagina.content);
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

  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Abre el modal de alta de Lista y recarga si se creo. */
  nuevo(): void {
    this.abrirFormulario();
  }

  /** Abre el modal de edicion con los datos de la Lista y recarga si cambio. */
  editar(lista: ListaPrecios): void {
    this.abrirFormulario(lista);
  }

  /**
   * Abre el modal de formulario de Lista (alta si no se pasa `lista`, edicion si
   * se pasa) y recarga el listado cuando el dialogo confirma.
   */
  private abrirFormulario(lista?: ListaPrecios): void {
    const data: ListaFormDialogData = { lista };
    const ref = this.dialog.open(ListaFormDialog, {
      width: 'min(760px, 96vw)',
      maxWidth: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((guardada?: ListaPrecios) => {
      if (guardada) {
        this.toast.exito(lista ? 'Lista actualizada.' : 'Lista creada.');
        this.cargar();
      }
    });
  }

  /** Abre el modal para asignar precios a los productos de la lista. */
  abrirAsignarPrecio(lista: ListaPrecios): void {
    const data: AsignarPrecioDialogData = { lista };
    const ref = this.dialog.open(AsignarPrecioDialog, {
      width: 'min(620px, 96vw)',
      maxWidth: 'min(620px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((huboCambios?: boolean) => {
      if (huboCambios) {
        // Los precios no cambian las columnas del listado, pero se recarga por
        // consistencia (p. ej. si se quiere reflejar conteos en el futuro).
        this.cargar();
      }
    });
  }

  /**
   * Abre el dialogo explicativo de un indicador de listas de precios (¿qué es? /
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

  /** Da de baja logica una Lista con confirmacion (Req 59). */
  async eliminar(lista: ListaPrecios): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja lista de precios',
      mensaje: `La lista "${lista.nombre}" quedara inactiva. Deseas continuar?`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.eliminar(lista.id).subscribe({
      next: () => {
        this.toast.exito('Lista dada de baja.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
