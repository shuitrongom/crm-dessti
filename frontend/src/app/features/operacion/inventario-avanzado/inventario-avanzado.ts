// =============================================================================
// Vista de Inventario Avanzado enterprise (Req 60, spec inventario-avanzado-enterprise)
// -----------------------------------------------------------------------------
// Reorganiza la vista en secciones (mat-tabs), todas gobernadas por permiso:
//   - Resumen: indicadores calculados + alertas de stock bajo.
//   - Existencias: saldos por Almacen/Material mostrados por NOMBRE (nunca UUID),
//     con filtros por Almacen/Material (entity-select) y accion "Ver Kardex".
//   - Movimientos: formularios de Entrada / Salida / Transferencia.
//   - Kardex: historial cronologico por Almacen+Material (por seleccion).
//   - Lotes: alta y listado de Lotes por Material, con resalte de caducidad.
//   - Configuracion: config de inventario por Material (metodo de costeo, etc.).
//   - Almacenes: CRUD de Almacenes (ya existente).
//
// Resolucion de nombres: NombresInventarioService carga una vez Almacenes y
// Materiales y expone helpers id -> nombre; los selectores usan entity-select
// sobre esas listas en memoria. Ningun UUID se muestra ni se teclea.
// =============================================================================

import {
  Component,
  computed,
  inject,
  signal,
  WritableSignal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { of, Observable, distinctUntilChanged } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { StatCard } from '../../../shared/components/stat-card/stat-card';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import {
  IndicadorInfoDialog,
  type DatosIndicadorInfo,
} from '../../../shared/indicadores/indicador-info-dialog';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { InventarioAvanzadoService } from '../services/inventario.service';
import { AlmacenFormDialog, AlmacenFormDialogData } from './almacen-form-dialog';
import { NombresInventarioService } from '../services/nombres-inventario.service';
import {
  Almacen,
  ConfigInventarioMaterial,
  ETIQUETA_METODO_COSTEO,
  ExistenciaAlmacen,
  Lote,
  Material,
  MetodoCosteo,
  MovimientoAlmacen,
} from '../models/operacion.models';

/** Umbral (dias) para marcar un lote como "proximo a caducar". */
const DIAS_PROXIMO_CADUCAR = 30;

/** Etiquetas es-MX de los tipos de movimiento del Kardex avanzado. */
const ETIQUETA_TIPO_KARDEX: Record<string, string> = {
  entrada: 'Entrada',
  salida: 'Salida',
  ajuste: 'Ajuste',
  transferencia: 'Transferencia',
  transferencia_entrada: 'Transferencia (entrada)',
  transferencia_salida: 'Transferencia (salida)',
};

@Component({
  selector: 'app-operacion-inventario-avanzado',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatSlideToggleModule,
    MatDatepickerModule,
    MatTooltipModule,
    PageHeader,
    StateContainer,
    StatCard,
    KpiTile,
    EntitySelect,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './inventario-avanzado.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './inventario-avanzado.scss',
})
export class OperacionInventarioAvanzado {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(InventarioAvanzadoService);
  private readonly nombres = inject(NombresInventarioService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);

  // --- Permisos (deny-by-default) ---
  protected readonly puedeCrearAlmacen = this.auth.tienePermiso('almacen', 'crear');
  protected readonly puedeActualizarAlmacen = this.auth.tienePermiso('almacen', 'actualizar');
  protected readonly puedeLeerAlmacen = this.auth.tienePermiso('almacen', 'listar');
  protected readonly puedeLeerExistencias = this.auth.tienePermiso('material', 'leer');
  protected readonly puedeLeerKardex = this.auth.tienePermiso('kardex', 'leer');
  protected readonly puedeCrearMovimiento = this.auth.tienePermiso(
    'movimiento_inventario',
    'crear',
  );
  protected readonly puedeConfigurarMaterial = this.auth.tienePermiso('material', 'actualizar');
  protected readonly puedeListarLotes = this.auth.tienePermiso('lote', 'listar');
  protected readonly puedeCrearLote = this.auth.tienePermiso('lote', 'crear');

  protected readonly tipos = ['sucursal', 'bodega'];
  protected readonly metodosCosteo: { valor: MetodoCosteo; etiqueta: string }[] = (
    Object.keys(ETIQUETA_METODO_COSTEO) as MetodoCosteo[]
  ).map((valor) => ({ valor, etiqueta: ETIQUETA_METODO_COSTEO[valor] }));

  /** `true` cuando los catalogos de nombres ya cargaron. */
  protected readonly catalogosCargados = this.nombres.cargado;

  // --- Buscadores en memoria para los entity-select (sin red por seleccion) ---
  /** Busca Almacenes por nombre sobre el catalogo ya cargado. */
  protected readonly buscarAlmacen = (filtro: string): Observable<PaginaResponse<Almacen>> =>
    this.paginaEnMemoria(this.nombres.almacenes(), filtro);

  /** Busca Materiales por nombre sobre el catalogo del Nucleo ya cargado. */
  protected readonly buscarMaterial = (filtro: string): Observable<PaginaResponse<Material>> =>
    this.paginaEnMemoria(this.nombres.listaMateriales(), filtro);

  protected readonly etiquetaAlmacen = (a: Almacen): string => a.nombre;
  protected readonly etiquetaMaterial = (m: Material): string => m.nombre;
  protected readonly detalleMaterial = (m: Material): string | null =>
    m.unidadMedida ? `Unidad: ${m.unidadMedida}` : null;

  private paginaEnMemoria<T extends { nombre: string }>(
    lista: T[],
    filtro: string,
  ): Observable<PaginaResponse<T>> {
    const termino = filtro.trim().toLowerCase();
    const content = termino
      ? lista.filter((x) => x.nombre.toLowerCase().includes(termino))
      : lista.slice(0, 20);
    return of({
      content: content.slice(0, 20),
      page: 0,
      size: 20,
      totalElements: content.length,
      totalPages: 1,
    });
  }

  // ---------------------------------------------------------------------------
  // Almacenes
  // ---------------------------------------------------------------------------
  protected readonly faseAlmacenes = signal<FaseSolicitud>('cargando');
  protected readonly errorAlmacenes = signal<string | undefined>(undefined);
  protected readonly almacenes = signal<Almacen[]>([]);
  protected readonly totalAlmacenes = signal(0);
  protected readonly pageAlmacenes = signal(0);
  protected readonly sizeAlmacenes = signal(20);
  protected readonly guardando = signal(false);

  protected readonly columnasAlmacenes: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Almacen' },
    { clave: 'tipo', encabezado: 'Tipo' },
    { clave: 'activo', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  // ---------------------------------------------------------------------------
  // Existencias
  // ---------------------------------------------------------------------------
  protected readonly faseExistencias = signal<FaseSolicitud>('vacio');
  protected readonly errorExistencias = signal<string | undefined>(undefined);
  protected readonly existencias = signal<ExistenciaAlmacen[]>([]);
  protected readonly totalExistencias = signal(0);
  protected readonly pageExistencias = signal(0);
  protected readonly sizeExistencias = signal(20);

  protected readonly columnasExistencias: ColumnaTabla[] = [
    { clave: 'almacen', encabezado: 'Almacen' },
    { clave: 'material', encabezado: 'Material' },
    { clave: 'cantidad', encabezado: 'Cantidad', alineacion: 'fin' },
    { clave: 'costoPromedio', encabezado: 'Costo promedio', alineacion: 'fin' },
    { clave: 'valorizacion', encabezado: 'Valorización', alineacion: 'fin' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  /** Valorizacion de una fila de existencia (cantidad * costo promedio). */
  valorizacionDe(existencia: ExistenciaAlmacen): number {
    return existencia.cantidad * existencia.costoPromedio;
  }

  protected readonly formFiltroExistencias = this.fb.nonNullable.group({
    almacenId: [''],
    materialId: [''],
  });

  // ---------------------------------------------------------------------------
  // Resumen / alertas (dashboard operativo)
  // ---------------------------------------------------------------------------
  /**
   * Fase del tablero Resumen en conjunto: 'cargando' mientras se precargan los
   * catalogos de nombres, 'error' si esa carga falla, 'vacio' cuando no hay
   * Materiales dados de alta (para orientar a crearlos primero) y 'ok' cuando
   * hay datos que mostrar. Evita ceros enganosos: si no hay datos, no se pintan
   * KPIs en cero sino un estado vacio descriptivo.
   */
  protected readonly faseResumen = signal<FaseSolicitud>('cargando');
  protected readonly errorResumen = signal<string | undefined>(undefined);
  protected readonly faseAlertas = signal<FaseSolicitud>('vacio');
  protected readonly errorAlertas = signal<string | undefined>(undefined);
  protected readonly materialesStockBajo = signal<Material[]>([]);

  /** Numero de Almacenes cargados en el catalogo de nombres. */
  protected readonly totalAlmacenesResumen = computed(() => this.nombres.almacenes().length);
  /** Numero total de Materiales dados de alta (catalogo base cargado). */
  protected readonly totalMateriales = computed(() => this.nombres.listaMateriales().length);
  /** `true` cuando existe al menos un Material dado de alta. */
  protected readonly hayMateriales = computed(() => this.totalMateriales() > 0);
  /** Numero de Materiales bajo su stock minimo. */
  protected readonly totalStockBajo = computed(() => this.materialesStockBajo().length);
  /**
   * Valor total del inventario sobre las existencias CARGADAS en la tabla actual
   * (suma cantidad * costoPromedio). Es un calculo sobre lo paginado, no un total
   * global del servidor.
   */
  protected readonly valorInventarioCargado = computed(() =>
    this.existencias().reduce((acc, e) => acc + e.cantidad * e.costoPromedio, 0),
  );
  /** `true` cuando hay al menos una existencia cargada para valorizar. */
  protected readonly hayExistenciasCargadas = computed(() => this.existencias().length > 0);

  /**
   * Materiales por reabastecer segun el punto de reorden CONFIGURADO por Material.
   * El catalogo en memoria (NombresInventarioService) no carga la configuracion
   * de inventario por Material (metodo de costeo / punto de reorden), por lo que
   * este dato no esta disponible en el Resumen y su KPI no se pinta (no cero
   * enganoso). Se llena cuando exista una fuente de configuracion cargada.
   */
  protected readonly materialesPorReabastecer = signal<Material[]>([]);
  /** `true` cuando la configuracion de reorden esta disponible para el KPI. */
  protected readonly configReordenDisponible = signal(false);
  /** Numero de Materiales por reabastecer (solo con config disponible). */
  protected readonly totalPorReabastecer = computed(() => this.materialesPorReabastecer().length);

  // ---------------------------------------------------------------------------
  // Movimientos
  // ---------------------------------------------------------------------------
  /**
   * Tipo de movimiento capturado en el formulario unico de la seccion Movimientos.
   * El selector solo decide que campos se muestran y que metodo se invoca al enviar;
   * la logica de cada movimiento (FormGroups y metodos) se conserva sin cambios.
   */
  protected readonly tipoMovimiento: WritableSignal<'entrada' | 'salida' | 'transferencia'> =
    signal('entrada');
  protected readonly enviandoEntrada = signal(false);
  protected readonly enviandoSalida = signal(false);
  protected readonly enviandoTransferencia = signal(false);
  protected readonly errorEntrada = signal<string | undefined>(undefined);
  protected readonly errorSalida = signal<string | undefined>(undefined);
  protected readonly errorTransferencia = signal<string | undefined>(undefined);

  protected readonly formEntrada = this.fb.nonNullable.group({
    almacenId: ['', [Validators.required]],
    materialId: ['', [Validators.required]],
    cantidad: [0, [Validators.required, Validators.min(0.0001)]],
    costoUnitario: [0, [Validators.required, Validators.min(0)]],
    loteCodigo: [''],
    motivo: ['', [Validators.maxLength(500)]],
  });

  protected readonly formSalida = this.fb.nonNullable.group({
    almacenId: ['', [Validators.required]],
    materialId: ['', [Validators.required]],
    cantidad: [0, [Validators.required, Validators.min(0.0001)]],
    loteCodigo: [''],
    motivo: ['', [Validators.maxLength(500)]],
  });

  protected readonly formTransferencia = this.fb.nonNullable.group({
    almacenOrigenId: ['', [Validators.required]],
    almacenDestinoId: ['', [Validators.required]],
    materialId: ['', [Validators.required]],
    cantidad: [0, [Validators.required, Validators.min(0.0001)]],
    motivo: ['', [Validators.maxLength(500)]],
  });

  // ---------------------------------------------------------------------------
  // Kardex
  // ---------------------------------------------------------------------------
  protected readonly faseKardex = signal<FaseSolicitud>('vacio');
  protected readonly errorKardex = signal<string | undefined>(undefined);
  protected readonly kardex = signal<MovimientoAlmacen[]>([]);
  protected readonly totalKardex = signal(0);
  protected readonly pageKardex = signal(0);
  protected readonly sizeKardex = signal(20);

  protected readonly columnasKardex: ColumnaTabla[] = [
    { clave: 'createdAt', encabezado: 'Fecha' },
    { clave: 'tipo', encabezado: 'Tipo' },
    { clave: 'cantidad', encabezado: 'Cantidad', alineacion: 'fin' },
    { clave: 'costoUnitario', encabezado: 'Costo unitario', alineacion: 'fin' },
    { clave: 'costoTotal', encabezado: 'Costo total', alineacion: 'fin' },
    { clave: 'saldoCantidad', encabezado: 'Saldo', alineacion: 'fin' },
    { clave: 'saldoCostoTotal', encabezado: 'Saldo costo', alineacion: 'fin' },
    { clave: 'motivo', encabezado: 'Motivo' },
  ];

  protected readonly formKardex = this.fb.nonNullable.group({
    almacenId: ['', [Validators.required]],
    materialId: ['', [Validators.required]],
    // Rango de fechas opcional (el backend acota por createdAt); ISO yyyy-MM-dd.
    desde: [null as string | null],
    hasta: [null as string | null],
  });

  /** Indice de pestana seleccionada (para navegar por codigo). */
  protected readonly pestanaSeleccionada = signal(0);

  // ---------------------------------------------------------------------------
  // Configuracion
  // ---------------------------------------------------------------------------
  protected readonly guardandoConfig = signal(false);
  protected readonly cargandoConfig = signal(false);
  protected readonly configGuardada = signal<ConfigInventarioMaterial | null>(null);

  protected readonly formConfig = this.fb.nonNullable.group({
    materialId: ['', [Validators.required]],
    metodoCosteo: ['promedio' as MetodoCosteo, [Validators.required]],
    stockMaximo: [null as number | null, [Validators.min(0)]],
    controlLote: [false],
    consumoPromedio: [0, [Validators.required, Validators.min(0)]],
    tiempoEntregaDias: [0, [Validators.required, Validators.min(0)]],
    stockSeguridad: [0, [Validators.required, Validators.min(0)]],
  });

  /**
   * Valor reactivo del formulario de configuracion, para derivar el punto de
   * reorden en vivo mientras el Usuario captura (misma formula que el backend:
   * consumoPromedio * tiempoEntregaDias + stockSeguridad).
   */
  private readonly valorConfig = toSignal(this.formConfig.valueChanges, {
    initialValue: this.formConfig.getRawValue(),
  });

  /**
   * Punto de reorden derivado previsualizado (Req 60). Replica el calculo del
   * backend para orientar la captura; el valor oficial lo devuelve el servidor.
   */
  protected readonly puntoReordenPreview = computed<number>(() => {
    const v = this.valorConfig();
    const consumo = Number(v?.consumoPromedio ?? 0);
    const dias = Number(v?.tiempoEntregaDias ?? 0);
    const seguridad = Number(v?.stockSeguridad ?? 0);
    const bruto = consumo * dias + seguridad;
    return Math.round((bruto + Number.EPSILON) * 1000) / 1000;
  });

  // ---------------------------------------------------------------------------
  // Lotes
  // ---------------------------------------------------------------------------
  protected readonly faseLotes = signal<FaseSolicitud>('vacio');
  protected readonly errorLotes = signal<string | undefined>(undefined);
  protected readonly lotes = signal<Lote[]>([]);
  protected readonly totalLotes = signal(0);
  protected readonly pageLotes = signal(0);
  protected readonly sizeLotes = signal(20);
  protected readonly guardandoLote = signal(false);
  protected readonly materialLotesId = signal<string>('');

  protected readonly columnasLotes: ColumnaTabla[] = [
    { clave: 'codigo', encabezado: 'Codigo' },
    { clave: 'fechaCaducidad', encabezado: 'Caducidad' },
    { clave: 'estado', encabezado: 'Estado' },
  ];

  protected readonly formLoteMaterial = this.fb.nonNullable.group({
    materialId: ['', [Validators.required]],
  });

  protected readonly formLote = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(100)]],
    fechaCaducidad: [null as string | null],
  });

  constructor() {
    if (this.puedeLeerAlmacen) {
      this.cargarAlmacenes();
    }
    this.faseResumen.set('cargando');
    this.nombres.cargar().subscribe({
      next: () => {
        // El tablero se considera vacio (no cero enganoso) mientras no exista
        // ningun Material dado de alta: en ese caso orienta a crearlos primero.
        this.faseResumen.set(this.hayMateriales() ? 'ok' : 'vacio');
        if (this.puedeLeerExistencias) {
          this.cargarExistencias();
          this.cargarAlertas();
        }
      },
      error: (e: HttpErrorResponse) => {
        // La UI degrada: los selectores quedaran vacios pero no rompe la vista.
        this.errorResumen.set(mensajeDeError(e));
        this.faseResumen.set('error');
      },
    });

    // Al elegir un Material DISTINTO en Configuracion, se relee su config (GET,
    // sin reescribir) para prellenar el formulario y no guardar a ciegas. Se usa
    // distinctUntilChanged para NO re-precargar (y pisar los ajustes del Usuario)
    // cuando el control re-emite el mismo material.
    this.formConfig.controls.materialId.valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((materialId) => this.precargarConfig(materialId));
  }

  /** Reintenta la carga de catalogos y del tablero Resumen tras un error. */
  reintentarResumen(): void {
    this.faseResumen.set('cargando');
    this.errorResumen.set(undefined);
    this.nombres.cargar().subscribe({
      next: () => {
        this.faseResumen.set(this.hayMateriales() ? 'ok' : 'vacio');
        if (this.puedeLeerExistencias) {
          this.cargarExistencias();
          this.cargarAlertas();
        }
      },
      error: (e: HttpErrorResponse) => {
        this.errorResumen.set(mensajeDeError(e));
        this.faseResumen.set('error');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Resolucion de nombres (helpers de plantilla)
  // ---------------------------------------------------------------------------

  nombreAlmacen(id: string | null | undefined): string {
    return this.nombres.nombreAlmacen(id);
  }

  nombreMaterial(id: string | null | undefined): string {
    return this.nombres.nombreMaterial(id);
  }

  etiquetaTipoKardex(tipo: string): string {
    return ETIQUETA_TIPO_KARDEX[tipo] ?? tipo;
  }

  /** `true` si la existencia esta por debajo del stock minimo del Material. */
  esStockBajo(existencia: ExistenciaAlmacen): boolean {
    const material = this.nombres.material(existencia.materialId);
    return material !== undefined && existencia.cantidad < material.stockMinimo;
  }

  // ---------------------------------------------------------------------------
  // Almacenes
  // ---------------------------------------------------------------------------

  cargarAlmacenes(): void {
    this.faseAlmacenes.set('cargando');
    this.service.listarAlmacenes(null, null, this.pageAlmacenes(), this.sizeAlmacenes()).subscribe({
      next: (pagina) => {
        this.almacenes.set(pagina.content);
        this.totalAlmacenes.set(pagina.totalElements);
        this.faseAlmacenes.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.errorAlmacenes.set(mensajeDeError(e));
        this.faseAlmacenes.set('error');
      },
    });
  }

  onPaginaAlmacenes(evento: { page: number; size: number }): void {
    this.pageAlmacenes.set(evento.page);
    this.sizeAlmacenes.set(evento.size);
    this.cargarAlmacenes();
  }

  /** Abre el modal de alta de Almacen y recarga si se creó. */
  nuevoAlmacen(): void {
    this.abrirAlmacen();
  }

  /** Abre el modal de edición con los datos del Almacen y recarga si cambió. */
  editarAlmacen(almacen: Almacen): void {
    this.abrirAlmacen(almacen);
  }

  /**
   * Abre el modal de formulario de Almacen (alta si no se pasa `almacen`, edición
   * si se pasa) y recarga el listado (y el catálogo de nombres) al confirmar.
   */
  private abrirAlmacen(almacen?: Almacen): void {
    const data: AlmacenFormDialogData = { almacen };
    const ref = this.dialog.open(AlmacenFormDialog, {
      width: 'min(680px, 96vw)',
      maxWidth: 'min(680px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((guardado?: Almacen) => {
      if (guardado) {
        this.toast.exito(almacen ? 'Almacén actualizado.' : 'Almacén creado.');
        this.cargarAlmacenes();
        // Refresca el catálogo de nombres para que los selectores incluyan el cambio.
        this.nombres.cargar().subscribe({ next: () => {}, error: () => {} });
      }
    });
  }

  /** Da de baja lógica un Almacen con confirmación (Req 60). */
  async desactivarAlmacen(almacen: Almacen): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja almacén',
      mensaje: `El almacén "${almacen.nombre}" quedará inactivo. Sus existencias y kardex se conservan. ¿Deseas continuar?`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.desactivarAlmacen(almacen.id).subscribe({
      next: () => {
        this.toast.exito('Almacén dado de baja.');
        this.cargarAlmacenes();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Reactiva un Almacen dado de baja (Req 60). */
  activarAlmacen(almacen: Almacen): void {
    this.service.activarAlmacen(almacen.id).subscribe({
      next: () => {
        this.toast.exito('Almacén reactivado.');
        this.cargarAlmacenes();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /**
   * Abre el dialogo explicativo de un indicador de inventario (¿qué es? / ¿cómo se
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

  // ---------------------------------------------------------------------------
  // Existencias
  // ---------------------------------------------------------------------------

  cargarExistencias(): void {
    if (!this.puedeLeerExistencias) {
      return;
    }
    const filtro = this.formFiltroExistencias.getRawValue();
    const almacenId = filtro.almacenId || null;
    const materialId = filtro.materialId || null;
    this.faseExistencias.set('cargando');
    this.service
      .listarExistencias(almacenId, materialId, this.pageExistencias(), this.sizeExistencias())
      .subscribe({
        next: (pagina) => {
          this.existencias.set(pagina.content);
          this.totalExistencias.set(pagina.totalElements);
          this.faseExistencias.set(pagina.content.length === 0 ? 'vacio' : 'ok');
        },
        error: (e: HttpErrorResponse) => {
          this.errorExistencias.set(mensajeDeError(e));
          this.faseExistencias.set('error');
        },
      });
  }

  aplicarFiltroExistencias(): void {
    this.pageExistencias.set(0);
    this.cargarExistencias();
  }

  limpiarFiltroExistencias(): void {
    this.formFiltroExistencias.reset({ almacenId: '', materialId: '' });
    this.pageExistencias.set(0);
    this.cargarExistencias();
  }

  onPaginaExistencias(evento: { page: number; size: number }): void {
    this.pageExistencias.set(evento.page);
    this.sizeExistencias.set(evento.size);
    this.cargarExistencias();
  }

  /** Abre el Kardex con el Almacen y Material de la existencia preseleccionados. */
  verKardexDeExistencia(existencia: ExistenciaAlmacen): void {
    this.formKardex.setValue({
      almacenId: existencia.almacenId,
      materialId: existencia.materialId,
      desde: null,
      hasta: null,
    });
    this.pageKardex.set(0);
    this.pestanaSeleccionada.set(this.indicePestana('kardex'));
    this.consultarKardex();
  }

  // ---------------------------------------------------------------------------
  // Resumen / alertas
  // ---------------------------------------------------------------------------

  /**
   * Deriva las alertas de stock bajo del catalogo del Nucleo ya cargado por
   * NombresInventarioService (Material.stockBajo o existencias < stockMinimo).
   */
  cargarAlertas(): void {
    if (!this.puedeLeerExistencias) {
      return;
    }
    const bajos = this.nombres
      .listaMateriales()
      .filter((m) => m.stockBajo || m.existencias < m.stockMinimo);
    this.materialesStockBajo.set(bajos);
    this.faseAlertas.set(bajos.length === 0 ? 'vacio' : 'ok');
  }

  /** Desde una alerta, abre el Kardex del material (primer almacen no aplica: solo material). */
  verKardexDeMaterial(material: Material): void {
    this.formKardex.patchValue({ materialId: material.id });
    this.pestanaSeleccionada.set(this.indicePestana('kardex'));
  }

  /** Desde una alerta, prepara una entrada para el material bajo minimo. */
  registrarEntradaDeMaterial(material: Material): void {
    this.tipoMovimiento.set('entrada');
    this.formEntrada.patchValue({ materialId: material.id });
    this.pestanaSeleccionada.set(this.indicePestana('movimientos'));
  }

  // ---------------------------------------------------------------------------
  // Movimientos
  // ---------------------------------------------------------------------------

  /**
   * Despachador del formulario unico de Movimientos: segun el tipo seleccionado
   * delega en el metodo existente correspondiente, reutilizando su FormGroup,
   * validaciones (incluida "origen != destino") y manejo de errores (422 por
   * existencias insuficientes). No cambia ningun contrato de servicio.
   */
  registrarMovimiento(): void {
    switch (this.tipoMovimiento()) {
      case 'entrada':
        this.registrarEntrada();
        break;
      case 'salida':
        this.registrarSalida();
        break;
      case 'transferencia':
        this.registrarTransferencia();
        break;
    }
  }

  registrarEntrada(): void {
    if (this.formEntrada.invalid) {
      this.formEntrada.markAllAsTouched();
      return;
    }
    const v = this.formEntrada.getRawValue();
    this.errorEntrada.set(undefined);
    this.enviandoEntrada.set(true);
    this.service
      .registrarEntrada(v.almacenId, {
        materialId: v.materialId,
        cantidad: v.cantidad,
        costoUnitario: v.costoUnitario,
        loteCodigo: v.loteCodigo?.trim() || null,
        motivo: v.motivo?.trim() || null,
      })
      .subscribe({
        next: () => {
          this.enviandoEntrada.set(false);
          this.toast.exito('Entrada registrada.');
          this.formEntrada.reset({
            almacenId: '',
            materialId: '',
            cantidad: 0,
            costoUnitario: 0,
            loteCodigo: '',
            motivo: '',
          });
          this.recargarTrasMovimiento();
        },
        error: (e: HttpErrorResponse) => {
          this.enviandoEntrada.set(false);
          this.errorEntrada.set(mensajeDeError(e));
        },
      });
  }

  registrarSalida(): void {
    if (this.formSalida.invalid) {
      this.formSalida.markAllAsTouched();
      return;
    }
    const v = this.formSalida.getRawValue();
    this.errorSalida.set(undefined);
    this.enviandoSalida.set(true);
    this.service
      .registrarSalida(v.almacenId, {
        materialId: v.materialId,
        cantidad: v.cantidad,
        loteCodigo: v.loteCodigo?.trim() || null,
        motivo: v.motivo?.trim() || null,
      })
      .subscribe({
        next: () => {
          this.enviandoSalida.set(false);
          this.toast.exito('Salida registrada.');
          this.formSalida.reset({
            almacenId: '',
            materialId: '',
            cantidad: 0,
            loteCodigo: '',
            motivo: '',
          });
          this.recargarTrasMovimiento();
        },
        error: (e: HttpErrorResponse) => {
          this.enviandoSalida.set(false);
          this.errorSalida.set(mensajeDeError(e));
        },
      });
  }

  registrarTransferencia(): void {
    if (this.formTransferencia.invalid) {
      this.formTransferencia.markAllAsTouched();
      return;
    }
    const v = this.formTransferencia.getRawValue();
    if (v.almacenOrigenId === v.almacenDestinoId) {
      this.errorTransferencia.set('El almacen de origen y el de destino deben ser distintos.');
      return;
    }
    this.errorTransferencia.set(undefined);
    this.enviandoTransferencia.set(true);
    this.service
      .transferir({
        almacenOrigenId: v.almacenOrigenId,
        almacenDestinoId: v.almacenDestinoId,
        materialId: v.materialId,
        cantidad: v.cantidad,
        motivo: v.motivo?.trim() || null,
      })
      .subscribe({
        next: () => {
          this.enviandoTransferencia.set(false);
          this.toast.exito('Transferencia registrada.');
          this.formTransferencia.reset({
            almacenOrigenId: '',
            almacenDestinoId: '',
            materialId: '',
            cantidad: 0,
            motivo: '',
          });
          this.recargarTrasMovimiento();
        },
        error: (e: HttpErrorResponse) => {
          this.enviandoTransferencia.set(false);
          this.errorTransferencia.set(mensajeDeError(e));
        },
      });
  }

  private recargarTrasMovimiento(): void {
    if (this.puedeLeerExistencias) {
      this.cargarExistencias();
    }
    if (this.puedeLeerKardex && this.formKardex.valid) {
      this.consultarKardex();
    }
  }

  // ---------------------------------------------------------------------------
  // Kardex
  // ---------------------------------------------------------------------------

  consultarKardex(): void {
    if (this.formKardex.invalid) {
      this.formKardex.markAllAsTouched();
      return;
    }
    const v = this.formKardex.getRawValue();
    // Las fechas se capturan como yyyy-MM-dd; se convierten a instantes ISO para
    // acotar el dia completo (desde = inicio del dia; hasta = fin del dia).
    const desde = v.desde ? `${v.desde}T00:00:00.000Z` : null;
    const hasta = v.hasta ? `${v.hasta}T23:59:59.999Z` : null;
    this.faseKardex.set('cargando');
    this.service
      .consultarKardex(v.almacenId, v.materialId, this.pageKardex(), this.sizeKardex(), desde, hasta)
      .subscribe({
        next: (pagina) => {
          this.kardex.set(pagina.content);
          this.totalKardex.set(pagina.totalElements);
          this.faseKardex.set(pagina.content.length === 0 ? 'vacio' : 'ok');
        },
        error: (e: HttpErrorResponse) => {
          this.errorKardex.set(mensajeDeError(e));
          this.faseKardex.set('error');
        },
      });
  }

  onPaginaKardex(evento: { page: number; size: number }): void {
    this.pageKardex.set(evento.page);
    this.sizeKardex.set(evento.size);
    this.consultarKardex();
  }

  /** `true` si hay filas de Kardex cargadas para exportar. */
  protected readonly hayKardex = computed(() => this.kardex().length > 0);

  /**
   * Exporta a CSV las filas del Kardex actualmente cargadas (la pagina consultada),
   * resolviendo Almacen y Material por nombre (nunca UUID). Es una utilidad de
   * cliente sobre los datos ya traidos; no consulta de nuevo al servidor.
   */
  exportarKardexCsv(): void {
    const filas = this.kardex();
    if (filas.length === 0) {
      return;
    }
    const v = this.formKardex.getRawValue();
    const almacen = this.nombreAlmacen(v.almacenId);
    const material = this.nombreMaterial(v.materialId);
    const encabezados = [
      'Fecha',
      'Almacen',
      'Material',
      'Tipo',
      'Cantidad',
      'Costo unitario',
      'Costo total',
      'Saldo',
      'Saldo costo',
      'Motivo',
    ];
    const escapar = (valor: string): string => {
      // Entrecomilla y duplica comillas si el valor contiene separador, comillas o salto.
      const limpio = valor ?? '';
      return /[",\n;]/.test(limpio) ? `"${limpio.replace(/"/g, '""')}"` : limpio;
    };
    const lineas = filas.map((m) =>
      [
        new Date(m.createdAt).toISOString(),
        almacen,
        material,
        this.etiquetaTipoKardex(m.tipo),
        String(m.cantidad),
        String(m.costoUnitario),
        String(m.costoTotal),
        String(m.saldoCantidad),
        String(m.saldoCostoTotal),
        m.motivo ?? '',
      ]
        .map((c) => escapar(c))
        .join(','),
    );
    const csv = [encabezados.join(','), ...lineas].join('\r\n');
    // BOM UTF-8 para que Excel respete acentos.
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `kardex-${material}-${almacen}.csv`.replace(/\s+/g, '_');
    a.click();
    URL.revokeObjectURL(url);
  }

  // ---------------------------------------------------------------------------
  // Configuracion
  // ---------------------------------------------------------------------------

  /**
   * Al elegir un Material en la pestana Configuracion, relee su configuracion del
   * backend (GET, sin reescribir) y prellena el formulario. Si el Material aun no
   * tiene configuracion, el backend devuelve la predeterminada (promedio, sin lote,
   * ceros), de modo que el Usuario ve valores coherentes en vez de un formulario
   * en blanco. Evita el "guardado ciego".
   */
  precargarConfig(materialId: string): void {
    const id = (materialId ?? '').trim();
    if (!id) {
      this.configGuardada.set(null);
      return;
    }
    this.cargandoConfig.set(true);
    this.service.consultarConfigInventario(id).subscribe({
      next: (config) => {
        this.cargandoConfig.set(false);
        // Si el Usuario cambio de Material mientras cargaba, se ignora.
        if (this.formConfig.getRawValue().materialId !== id) {
          return;
        }
        this.configGuardada.set(config);
        this.formConfig.patchValue(
          {
            metodoCosteo: config.metodoCosteo,
            stockMaximo: config.stockMaximo,
            controlLote: config.controlLote,
            consumoPromedio: config.consumoPromedio,
            tiempoEntregaDias: config.tiempoEntregaDias,
            stockSeguridad: config.stockSeguridad,
          },
          { emitEvent: true },
        );
      },
      error: (e: HttpErrorResponse) => {
        this.cargandoConfig.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  guardarConfig(): void {
    if (this.formConfig.invalid) {
      this.formConfig.markAllAsTouched();
      return;
    }
    const v = this.formConfig.getRawValue();
    this.guardandoConfig.set(true);
    this.service
      .configurarInventarioMaterial(v.materialId, {
        metodoCosteo: v.metodoCosteo,
        stockMaximo: v.stockMaximo ?? null,
        controlLote: v.controlLote,
        consumoPromedio: v.consumoPromedio,
        tiempoEntregaDias: v.tiempoEntregaDias,
        stockSeguridad: v.stockSeguridad,
      })
      .subscribe({
        next: (config) => {
          this.guardandoConfig.set(false);
          this.configGuardada.set(config);
          this.toast.exito('Configuración guardada.');
        },
        error: (e: HttpErrorResponse) => {
          this.guardandoConfig.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  // ---------------------------------------------------------------------------
  // Lotes
  // ---------------------------------------------------------------------------

  cargarLotes(): void {
    const materialId = this.formLoteMaterial.getRawValue().materialId;
    if (!materialId) {
      this.formLoteMaterial.markAllAsTouched();
      return;
    }
    this.materialLotesId.set(materialId);
    this.faseLotes.set('cargando');
    this.service.listarLotes(materialId, this.pageLotes(), this.sizeLotes()).subscribe({
      next: (pagina) => {
        this.lotes.set(pagina.content);
        this.totalLotes.set(pagina.totalElements);
        this.faseLotes.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.errorLotes.set(mensajeDeError(e));
        this.faseLotes.set('error');
      },
    });
  }

  onPaginaLotes(evento: { page: number; size: number }): void {
    this.pageLotes.set(evento.page);
    this.sizeLotes.set(evento.size);
    this.cargarLotes();
  }

  crearLote(): void {
    const materialId = this.materialLotesId() || this.formLoteMaterial.getRawValue().materialId;
    if (!materialId) {
      this.formLoteMaterial.markAllAsTouched();
      return;
    }
    if (this.formLote.invalid) {
      this.formLote.markAllAsTouched();
      return;
    }
    const v = this.formLote.getRawValue();
    this.guardandoLote.set(true);
    this.service
      .crearLote(materialId, {
        codigo: v.codigo.trim(),
        fechaCaducidad: v.fechaCaducidad || null,
      })
      .subscribe({
        next: () => {
          this.guardandoLote.set(false);
          this.toast.exito('Lote creado.');
          this.formLote.reset({ codigo: '', fechaCaducidad: null });
          this.materialLotesId.set(materialId);
          this.cargarLotes();
        },
        error: (e: HttpErrorResponse) => {
          this.guardandoLote.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  /** Estado de caducidad de un lote: 'caducado' | 'proximo' | 'vigente' | 'sin_caducidad'. */
  estadoLote(lote: Lote): 'caducado' | 'proximo' | 'vigente' | 'sin_caducidad' {
    if (!lote.fechaCaducidad) {
      return 'sin_caducidad';
    }
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    const caducidad = new Date(lote.fechaCaducidad);
    caducidad.setHours(0, 0, 0, 0);
    const dias = Math.round((caducidad.getTime() - hoy.getTime()) / (1000 * 60 * 60 * 24));
    if (dias < 0) {
      return 'caducado';
    }
    if (dias <= DIAS_PROXIMO_CADUCAR) {
      return 'proximo';
    }
    return 'vigente';
  }

  etiquetaEstadoLote(lote: Lote): string {
    switch (this.estadoLote(lote)) {
      case 'caducado':
        return 'Caducado';
      case 'proximo':
        return 'Proximo a caducar';
      case 'vigente':
        return 'Vigente';
      default:
        return 'Sin caducidad';
    }
  }

  /**
   * Icono (Material Symbols) que acompana al estado de caducidad de un lote.
   * El icono refuerza el estado ademas del color (WCAG: no solo color), y los
   * cuatro estados (incluidos "Vigente" y "Sin caducidad") tienen tratamiento
   * visual propio.
   */
  iconoEstadoLote(lote: Lote): string {
    switch (this.estadoLote(lote)) {
      case 'caducado':
        return 'dangerous';
      case 'proximo':
        return 'schedule';
      case 'vigente':
        return 'check_circle';
      default:
        return 'all_inclusive';
    }
  }

  // ---------------------------------------------------------------------------
  // Almacenes (helpers de presentacion)
  // ---------------------------------------------------------------------------

  /** Etiqueta legible es-MX del tipo de Almacen (sucursal / bodega). */
  etiquetaTipoAlmacen(tipo: string): string {
    switch (tipo) {
      case 'sucursal':
        return 'Sucursal';
      case 'bodega':
        return 'Bodega';
      default:
        return tipo;
    }
  }

  // ---------------------------------------------------------------------------
  // Navegacion de pestanas
  // ---------------------------------------------------------------------------

  /** Orden logico de las pestanas visibles, para navegar por codigo entre ellas. */
  private pestanasVisibles(): string[] {
    const orden: { clave: string; visible: boolean }[] = [
      { clave: 'resumen', visible: this.puedeLeerExistencias },
      { clave: 'existencias', visible: this.puedeLeerExistencias },
      { clave: 'movimientos', visible: this.puedeCrearMovimiento },
      { clave: 'kardex', visible: this.puedeLeerKardex },
      { clave: 'lotes', visible: this.puedeListarLotes || this.puedeCrearLote },
      { clave: 'configuracion', visible: this.puedeConfigurarMaterial },
      { clave: 'almacenes', visible: this.puedeLeerAlmacen },
    ];
    return orden.filter((o) => o.visible).map((o) => o.clave);
  }

  private indicePestana(clave: string): number {
    const idx = this.pestanasVisibles().indexOf(clave);
    return idx < 0 ? 0 : idx;
  }

  onCambioPestana(indice: number): void {
    this.pestanaSeleccionada.set(indice);
  }
}
