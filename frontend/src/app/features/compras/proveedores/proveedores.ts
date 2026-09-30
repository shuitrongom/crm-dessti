// =============================================================================
// Vista de Proveedores (Req 29)
// -----------------------------------------------------------------------------
// Catálogo de proveedores del módulo Compras: listado paginado con búsqueda por
// nombre o RFC (el usuario no necesita saber el RFC exacto), filtro por estado,
// KPIs de activos/inactivos, y alta/edición mediante un MODAL premium
// (ProveedorFormDialog). La baja y la reactivación son con confirmación y, tras
// aplicarlas, la vista deja al usuario VIENDO el proveedor afectado (ajusta el
// filtro de estado). Todo gobernado por permiso atómico (deny-by-default); el
// backend reimpone la autorización, la unicidad del RFC (409) y el formato (422).
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
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
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import {
  IndicadorInfoDialog,
  type DatosIndicadorInfo,
} from '../../../shared/indicadores/indicador-info-dialog';
import { SearchAutocomplete } from '../../../shared/components/search-autocomplete/search-autocomplete';
import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { ComprasService } from '../services/compras.service';
import { Proveedor } from '../models/compras.models';
import { ProveedorFormDialog, ProveedorFormDialogData } from './proveedor-form-dialog';

@Component({
  selector: 'app-compras-proveedores',
  imports: [
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    KpiTile,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
    SearchAutocomplete,
  ],
  templateUrl: './proveedores.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../compras.scss',
})
export class ComprasProveedores {
  private readonly service = inject(ComprasService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeCrear = this.auth.tienePermiso('proveedor', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('proveedor', 'actualizar');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Proveedor' },
    { clave: 'rfc', encabezado: 'RFC' },
    { clave: 'contacto', encabezado: 'Contacto' },
    { clave: 'credito', encabezado: 'Crédito', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado', alineacion: 'centro' },
    { clave: 'alta', encabezado: 'Alta' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estado = signal<EstadoSolicitud<Proveedor[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtro = signal('');
  /** Filtro de estado del listado: activos (por defecto), inactivos o todos. */
  protected readonly estadoFiltro = signal<'activo' | 'inactivo' | 'todos'>('activo');

  /** KPI: proveedores mostrados en la página actual (activos según el filtro). */
  protected readonly totalMostrado = computed(() => this.estado().datos?.length ?? 0);
  /** KPI: activos en la página cargada. */
  protected readonly totalActivos = computed(
    () => (this.estado().datos ?? []).filter((p) => p.activo).length,
  );
  /** KPI: inactivos en la página cargada. */
  protected readonly totalInactivos = computed(
    () => (this.estado().datos ?? []).filter((p) => !p.activo).length,
  );

  // --- Buscador premium con sugerencias (SearchAutocomplete) ---
  /**
   * Busca proveedores para las SUGERENCIAS del buscador premium: consulta el
   * listado del backend por nombre o RFC, acotado al estado actualmente filtrado,
   * y devuelve la primera página. El backend filtra por nombre O RFC sin distinguir
   * mayúsculas, así el Usuario encuentra el proveedor sin saber el RFC exacto.
   */
  protected readonly buscarProveedor = (filtro: string) =>
    this.service.listarProveedores(filtro || null, 0, 10, this.estadoFiltro());

  /** Etiqueta principal de una sugerencia: la razón social. */
  protected readonly etiquetaProveedor = (p: Proveedor): string => p.nombre;
  /** Detalle secundario de una sugerencia: el RFC. */
  protected readonly detalleProveedor = (p: Proveedor): string | null => p.rfc;
  /** Ícono de cada sugerencia de proveedor. */
  protected readonly iconoProveedor = (_p: Proveedor): string => 'local_shipping';

  constructor() {
    // Carga inicial inmediata (no espera debounce) para pintar el listado al entrar.
    this.cargar();
    // Búsqueda EN VIVO (patrón de Inventario Avanzado, mejorado): cada cambio del
    // término recarga con debounce, sin presionar Enter. Se ignora el PRIMER valor
    // que emite el signal (el inicial, que coincide con la carga del constructor)
    // comparándolo con el último término ya aplicado, de modo que no se pierda la
    // primera búsqueda real del usuario ni se duplique la carga inicial.
    let ultimoAplicado = this.filtro();
    toObservable(this.filtro)
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((termino) => {
        if (termino === ultimoAplicado) {
          return;
        }
        ultimoAplicado = termino;
        this.page.set(0);
        this.cargar();
      });
  }

  /**
   * Abre el diálogo explicativo de un KPI de proveedores (qué es / cómo se calcula /
   * por qué importa), resuelto del catálogo central por su clave estable. Mismo
   * patrón que los indicadores de Inventario Avanzado.
   */
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
      .listarProveedores(this.filtro() || null, this.page(), this.size(), this.estadoFiltro())
      .subscribe({
        next: (pagina) => {
          this.total.set(pagina.totalElements);
          this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
        },
        error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
      });
  }

  /** Cambia el filtro de estado (activo/inactivo/todos) y recarga desde la página 0. */
  cambiarEstadoFiltro(estado: 'activo' | 'inactivo' | 'todos'): void {
    this.estadoFiltro.set(estado);
    this.page.set(0);
    this.cargar();
  }

  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /**
   * Actualiza el término de búsqueda EN VIVO (lo emite el buscador premium con
   * debounce). La recarga la ejecuta el flujo del constructor que observa `filtro`.
   */
  actualizarFiltro(valor: string): void {
    this.filtro.set(valor.trim());
  }

  /**
   * Al elegir una sugerencia del buscador premium, filtra el listado por el nombre
   * del proveedor elegido (coincide con lo que el buscador deja escrito), de modo
   * que la tabla muestre justo ese proveedor. El backend filtra por nombre o RFC.
   */
  seleccionarSugerencia(proveedor: Proveedor): void {
    this.filtro.set(proveedor.nombre);
  }

  /** Abre el modal en modo alta y recarga si se creó. */
  nuevo(): void {
    this.abrir();
  }

  /** Abre el modal en modo edición con los datos del proveedor y recarga si cambió. */
  editar(proveedor: Proveedor): void {
    this.abrir(proveedor);
  }

  /**
   * Abre el modal de formulario de Proveedor (alta si no se pasa `proveedor`,
   * edición si se pasa) y, al confirmar, recarga el listado. Tras el alta se
   * posiciona en la primera página para que el nuevo proveedor sea visible.
   */
  private abrir(proveedor?: Proveedor): void {
    const data: ProveedorFormDialogData = { proveedor };
    const ref = this.dialog.open(ProveedorFormDialog, {
      width: 'min(760px, 96vw)',
      maxWidth: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((guardado?: Proveedor) => {
      if (!guardado) {
        return;
      }
      this.toast.exito(proveedor ? 'Proveedor actualizado.' : 'Proveedor creado.');
      // Al crear, asegurar que el proveedor recién dado de alta sea visible:
      // se ve entre los activos y desde la primera página.
      if (!proveedor) {
        this.estadoFiltro.set('activo');
        this.page.set(0);
      }
      this.cargar();
    });
  }

  /**
   * Da de baja lógica al proveedor (conserva histórico), con confirmación. Tras la
   * baja, si se estaba filtrando por "activos" el proveedor saldría del listado; se
   * cambia el filtro a "inactivos" para que el usuario lo siga viendo.
   */
  async desactivar(proveedor: Proveedor): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja proveedor',
      mensaje: `El proveedor "${proveedor.nombre}" quedará inactivo y no podrá usarse en nuevas órdenes. Su RFC se libera. ¿Continuar?`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.service.desactivarProveedor(proveedor.id), {
        tipo: 'eliminar',
        textoProceso: 'Dando de baja…',
        textoExito: 'Proveedor dado de baja',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Proveedor dado de baja.');
          this.mostrarTrasCambioEstado('inactivo');
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }

  /**
   * Reactiva un proveedor dado de baja (Req 29.5), con confirmación. Tras
   * reactivar, si se estaba filtrando por "inactivos" el proveedor saldría del
   * listado; se cambia el filtro a "activos" para que el usuario lo siga viendo.
   */
  async reactivar(proveedor: Proveedor): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Reactivar proveedor',
      mensaje: `El proveedor "${proveedor.nombre}" volverá a estar activo y disponible para nuevas órdenes. ¿Continuar?`,
      textoConfirmar: 'Reactivar',
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.service.reactivarProveedor(proveedor.id), {
        tipo: 'guardar',
        textoProceso: 'Reactivando…',
        textoExito: 'Proveedor reactivado',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Proveedor reactivado.');
          this.mostrarTrasCambioEstado('activo');
        },
        error: (e: HttpErrorResponse) => {
          if (e.status === 409) {
            this.toast.error('Ya existe un proveedor activo con ese RFC. Edítalo antes de reactivar.');
            return;
          }
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  /**
   * Tras una baja o reactivación, deja al usuario VIENDO el proveedor afectado: si
   * el filtro actual lo ocultaría (p. ej. reactivar mientras se ven "inactivos"),
   * cambia el filtro al estado destino; si ya se ven "todos", solo recarga.
   */
  private mostrarTrasCambioEstado(destino: 'activo' | 'inactivo'): void {
    if (this.estadoFiltro() === 'todos') {
      this.cargar();
      return;
    }
    if (this.estadoFiltro() !== destino) {
      this.estadoFiltro.set(destino);
      this.page.set(0);
    }
    this.cargar();
  }
}
