// =============================================================================
// Vista de Oportunidades / pipeline (Req 14, 63) — tablero por etapa (kanban)
// -----------------------------------------------------------------------------
// Presenta las Oportunidades agrupadas por etapa del embudo (nuevo -> ... ->
// ganado/perdido). El avance principal es por DRAG & DROP: arrastrar una tarjeta
// a otra columna cambia su etapa, respetando la maquina de estados (una
// transicion invalida se rechaza en el cliente y el backend la rechazaria con
// 409). Como alternativa accesible, cada tarjeta ofrece un menu "Mover" con las
// transiciones validas. El alta y la asignacion de canal se hacen en modales
// animados. La conversion de una Oportunidad ganada en Cotizacion sigue
// disponible (Req 14.5). Las acciones se gobiernan por permiso atomico.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';

import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import {
  IndicadorInfoDialog,
  type DatosIndicadorInfo,
} from '../../../shared/indicadores/indicador-info-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { OportunidadesService } from '../services/oportunidades.service';
import { ClientesService } from '../services/clientes.service';
import { CanalesVentaService } from '../services/catalogo.service';
import {
  CanalVenta,
  Cliente,
  ETAPAS_PIPELINE,
  ETIQUETA_ETAPA,
  EtapaOportunidad,
  Oportunidad,
  TRANSICIONES_ETAPA,
  etapasDestino,
  valorPonderado,
} from '../models/comercial.models';
import { NuevaOportunidadDialog } from './nueva-oportunidad-dialog';
import { AsignarCanalDialog, AsignarCanalDialogData } from './asignar-canal-dialog';

/** Columna del tablero: una etapa con sus Oportunidades y su valor agregado. */
interface ColumnaPipeline {
  etapa: EtapaOportunidad;
  etiqueta: string;
  oportunidades: Oportunidad[];
  /** Suma del valor estimado de las Oportunidades de la columna (MXN). */
  valor: number;
  /** Suma del valor ponderado por probabilidad (forecast) de la columna (MXN, V81). */
  valorPonderado: number;
}

/** Etapas terminales del embudo: NO cuentan como pipeline abierto. */
const ETAPAS_TERMINALES: ReadonlySet<EtapaOportunidad> = new Set<EtapaOportunidad>([
  'ganado',
  'perdido',
]);

@Component({
  selector: 'app-comercial-oportunidades',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    DragDropModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
    MatTooltipModule,
    PageHeader,
    StateContainer,
    KpiTile,
  ],
  templateUrl: './oportunidades.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './oportunidades.scss',
})
export class ComercialOportunidades {
  private readonly service = inject(OportunidadesService);
  private readonly clientes = inject(ClientesService);
  private readonly canales = inject(CanalesVentaService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeCrear = this.auth.tienePermiso('oportunidad', 'crear');
  protected readonly puedeCambiarEtapa = this.auth.tienePermiso('oportunidad', 'cambiar_estado');
  protected readonly puedeConvertir = this.auth.tienePermiso('oportunidad', 'actualizar');
  /** El mismo permiso `actualizar` gobierna la asignacion de canal (Req 2.1). */
  protected readonly puedeAsignarCanal = this.auth.tienePermiso('oportunidad', 'actualizar');
  /** Permite guiar a crear un canal cuando no hay ninguno (bugfix #7). */
  protected readonly puedeCrearCanal = this.auth.tienePermiso('canal_venta', 'crear');
  /** El enlace al Cliente solo se ofrece si el Usuario puede abrir su ficha (Req 2.4, 7.2). */
  protected readonly puedeVerCliente = this.auth.tienePermiso('cliente', 'leer');
  /** El enlace "Ver cotizacion" solo se ofrece si el Usuario puede leerla (Req 2.3, 7.2). */
  protected readonly puedeVerCotizacion = this.auth.tienePermiso('cotizacion', 'leer');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly oportunidades = signal<Oportunidad[]>([]);
  protected readonly etiquetaEtapa = ETIQUETA_ETAPA;

  /** Filtro por canal de venta seleccionado (id, o '' para todos). */
  protected readonly filtroCanalId = signal('');
  /** Canales de venta disponibles para el filtro y los selectores (cargados una vez). */
  protected readonly canalesDisponibles = signal<CanalVenta[]>([]);
  /** Mapa id -> nombre de canal para mostrar el canal actual sin exponer el UUID. */
  private readonly nombreCanalPorId = computed<Map<string, string>>(
    () => new Map(this.canalesDisponibles().map((c) => [c.id, c.nombre])),
  );
  /** Mapa id -> nombre de cliente para mostrar/enlazar al cliente sin exponer el UUID. */
  protected readonly nombreClientePorId = signal<Map<string, string>>(new Map());

  /** Ids de las columnas cdkDropList, para conectarlas entre si (drag & drop). */
  protected readonly idsColumnas = ETAPAS_PIPELINE.map((etapa) => 'col-' + etapa);

  /** Agrupa las Oportunidades por etapa, respetando el orden del embudo, y
   * calcula el valor agregado de cada columna para el tablero (kanban premium). */
  protected readonly columnas = computed<ColumnaPipeline[]>(() => {
    const items = this.oportunidades();
    return ETAPAS_PIPELINE.map((etapa) => {
      const oportunidades = items.filter((o) => o.etapa === etapa);
      const valor = oportunidades.reduce((acc, o) => acc + (Number(o.valorEstimado) || 0), 0);
      const valorPond = oportunidades.reduce((acc, o) => acc + valorPonderado(o), 0);
      return {
        etapa,
        etiqueta: ETIQUETA_ETAPA[etapa],
        oportunidades,
        valor,
        valorPonderado: Math.round((valorPond + Number.EPSILON) * 100) / 100,
      };
    });
  });

  // ---------------------------------------------------------------------------
  // Indicadores del embudo (KPIs) — patron enterprise: outcome-first arriba a la
  // izquierda. Se calculan en el cliente a partir de las Oportunidades cargadas.
  // ---------------------------------------------------------------------------

  /** Oportunidades ABIERTAS (no terminales): las que siguen vivas en el embudo. */
  private readonly abiertas = computed<Oportunidad[]>(() =>
    this.oportunidades().filter((o) => !ETAPAS_TERMINALES.has(o.etapa)),
  );

  /** Numero de Oportunidades abiertas. */
  protected readonly totalAbiertas = computed<number>(() => this.abiertas().length);

  /** Valor total del pipeline ABIERTO (MXN): suma del valor estimado de las abiertas. */
  protected readonly valorPipeline = computed<number>(() =>
    this.abiertas().reduce((acc, o) => acc + (Number(o.valorEstimado) || 0), 0),
  );

  /** Forecast ponderado del pipeline abierto (MXN): Σ valor * probabilidad / 100 (V81). */
  protected readonly forecastPonderado = computed<number>(
    () =>
      Math.round(
        (this.abiertas().reduce((acc, o) => acc + valorPonderado(o), 0) + Number.EPSILON) * 100,
      ) / 100,
  );

  /** Ticket promedio del pipeline abierto (MXN); 0 si no hay abiertas. */
  protected readonly ticketPromedio = computed<number>(() => {
    const n = this.totalAbiertas();
    return n === 0 ? 0 : this.valorPipeline() / n;
  });

  /** Numero de Oportunidades ganadas (etapa terminal 'ganado'). */
  protected readonly totalGanados = computed<number>(
    () => this.oportunidades().filter((o) => o.etapa === 'ganado').length,
  );

  /** Tasa de conversion a ganado sobre el total (0-100); 0 si no hay Oportunidades. */
  protected readonly tasaGanados = computed<number>(() => {
    const total = this.oportunidades().length;
    return total === 0 ? 0 : Math.round((this.totalGanados() / total) * 100);
  });

  constructor() {
    this.cargarCanales();
    this.cargar();
  }

  /** Carga los canales de venta una sola vez para el filtro y los selectores (Req 63). */
  cargarCanales(): void {
    this.canales.listar(null, 0, 100).subscribe({
      next: (pagina) => this.canalesDisponibles.set(pagina.content),
      // Sin canales el filtro/selector quedan vacios; la vista degrada sin romper.
      error: () => this.canalesDisponibles.set([]),
    });
  }

  /** Nombre del canal de una Oportunidad, o null si no tiene canal asignado. */
  nombreCanal(oportunidad: Oportunidad): string | null {
    return oportunidad.canalVentaId
      ? (this.nombreCanalPorId().get(oportunidad.canalVentaId) ?? null)
      : null;
  }

  /** Nombre del cliente de una Oportunidad, o null si aun no se resolvio. */
  nombreCliente(oportunidad: Oportunidad): string | null {
    return this.nombreClientePorId().get(oportunidad.clienteId) ?? null;
  }

  /** Carga las Oportunidades (hasta 100, el maximo permitido por pagina) con el filtro por canal. */
  cargar(): void {
    this.fase.set('cargando');
    this.service.listar({ canalVentaId: this.filtroCanalId() || null }, 0, 100).subscribe({
      next: (pagina) => {
        this.oportunidades.set(pagina.content);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
        this.resolverNombresCliente(pagina.content);
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Aplica el filtro por canal de venta y recarga el pipeline (Req 2.2). */
  aplicarFiltroCanal(canalId: string): void {
    this.filtroCanalId.set(canalId ?? '');
    this.cargar();
  }

  /**
   * Resuelve los nombres de los Clientes referidos por las Oportunidades visibles
   * para poder mostrarlos como enlace navegable sin exponer el UUID (Req 2.4, 7.1).
   * Consulta solo los ids que aun no estan en el mapa; si el Usuario no puede
   * abrir la ficha del cliente, se omite la resolucion (degradacion sin romper).
   */
  private resolverNombresCliente(items: Oportunidad[]): void {
    if (!this.puedeVerCliente) {
      return;
    }
    const mapa = this.nombreClientePorId();
    const pendientes = [...new Set(items.map((o) => o.clienteId))].filter((id) => !mapa.has(id));
    if (pendientes.length === 0) {
      return;
    }
    forkJoin(
      pendientes.map((id) =>
        this.clientes.consultar(id).pipe(catchError(() => of(null as Cliente | null))),
      ),
    ).subscribe((clientes) => {
      const actualizado = new Map(this.nombreClientePorId());
      clientes.forEach((cliente, indice) => {
        if (cliente) {
          actualizado.set(pendientes[indice], cliente.nombre);
        }
      });
      this.nombreClientePorId.set(actualizado);
    });
  }

  /** Transiciones de etapa validas desde la etapa actual de una Oportunidad. */
  transicionesDe(oportunidad: Oportunidad): readonly EtapaOportunidad[] {
    return etapasDestino(oportunidad.etapa);
  }

  /**
   * Rango de la probabilidad de cierre para colorear el badge (baja/media/alta),
   * de modo que el forecast se lea de un vistazo. El significado no depende solo
   * del color: el badge siempre muestra el porcentaje (Req 57).
   */
  /**
   * Abre el dialogo explicativo de un indicador del pipeline (¿qué es? / ¿cómo
   * se calcula? / ¿por qué importa?). La clave debe coincidir con una del
   * catalogo central de indicadores para mostrar la explicacion correcta.
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

  rangoProbabilidad(probabilidad: number): 'baja' | 'media' | 'alta' {
    const p = Number(probabilidad) || 0;
    if (p >= 70) {
      return 'alta';
    }
    return p >= 40 ? 'media' : 'baja';
  }

  // ---------------------------------------------------------------------------
  // Drag & drop entre columnas (avance principal del pipeline)
  // ---------------------------------------------------------------------------

  /**
   * Maneja el drop de una tarjeta en una columna. Si cae en la MISMA etapa no
   * hace nada. Si cae en otra, valida contra la maquina de estados
   * ({@link TRANSICIONES_ETAPA}): una transicion invalida se avisa y se ignora
   * (el backend la rechazaria con 409). Una transicion valida delega en
   * {@link cambiarEtapa}, que pide el motivo cuando el destino es 'perdido'.
   */
  soltarEnColumna(evento: CdkDragDrop<EtapaOportunidad>, destino: EtapaOportunidad): void {
    const oportunidad = evento.item.data as Oportunidad;
    if (!oportunidad || oportunidad.etapa === destino) {
      return;
    }
    if (!this.puedeCambiarEtapa) {
      return;
    }
    const permitidas = TRANSICIONES_ETAPA[oportunidad.etapa] ?? [];
    if (!permitidas.includes(destino)) {
      this.toast.info(
        `No se puede mover de "${ETIQUETA_ETAPA[oportunidad.etapa]}" a "${ETIQUETA_ETAPA[destino]}".`,
      );
      return;
    }
    this.cambiarEtapa(oportunidad, destino);
  }

  /**
   * Cambia la etapa de una Oportunidad segun la maquina de estados (Req 14.3).
   * Al mover a 'perdido' se solicita el motivo de perdida obligatorio (V81): si el
   * Usuario cancela la captura, la transicion no se realiza.
   */
  cambiarEtapa(oportunidad: Oportunidad, etapa: EtapaOportunidad): void {
    let motivo: string | null = null;
    if (etapa === 'perdido') {
      motivo = (window.prompt('Motivo de la pérdida (obligatorio):') ?? '').trim();
      if (!motivo) {
        this.toast.info('Se requiere un motivo para marcar la oportunidad como perdida.');
        return;
      }
    }
    this.service.cambiarEtapa(oportunidad.id, etapa, motivo).subscribe({
      next: (actualizada) => {
        this.toast.exito(`Oportunidad movida a "${ETIQUETA_ETAPA[etapa]}".`);
        // Actualiza en memoria para un feedback inmediato tras el drag & drop.
        this.oportunidades.update((items) =>
          items.map((o) => (o.id === actualizada.id ? actualizada : o)),
        );
      },
      error: (e: HttpErrorResponse) => {
        this.toast.error(mensajeDeError(e));
        // Recarga para revertir el estado visual si el backend rechazo el cambio.
        this.cargar();
      },
    });
  }

  /** Abre el modal de alta de Oportunidad y recarga el pipeline si se creo (Req 14.1). */
  nuevaOportunidad(): void {
    const ref = this.dialog.open(NuevaOportunidadDialog, {
      width: 'min(620px, 96vw)',
      maxWidth: 'min(620px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
    });
    ref.afterClosed().subscribe((creada?: Oportunidad) => {
      if (creada) {
        this.toast.exito('Oportunidad creada.');
        this.cargar();
      }
    });
  }

  /** Convierte una Oportunidad ganada en Cotizacion y navega a su detalle (Req 14.5). */
  async convertir(oportunidad: Oportunidad): Promise<void> {
    // Guarda preventiva de conversion unica (Req 14.7): si la Oportunidad ya tiene
    // una Cotizacion vinculada, no se vuelve a convertir. La UI ya oculta el boton en
    // ese caso; esta guarda evita una segunda conversion si el estado en memoria
    // quedo desactualizado. El backend aplica la misma regla y responde 422.
    if (oportunidad.cotizacionId) {
      this.toast.error(
        'Esta oportunidad ya fue convertida en una cotizacion. Abre la cotizacion existente desde "Ver cotizacion".',
      );
      return;
    }
    const ok = await this.confirm.confirmar({
      titulo: 'Convertir en cotizacion',
      mensaje: `Se generara una cotizacion a partir de "${oportunidad.titulo}". Deseas continuar?`,
      textoConfirmar: 'Convertir',
    });
    if (!ok) {
      return;
    }
    this.service.convertir(oportunidad.id).subscribe({
      next: (resultado) => {
        this.toast.exito('Cotizacion generada.');
        this.router.navigate(['/empresa/comercial/cotizaciones', resultado.cotizacionId]);
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /**
   * Abre el modal para asignar/cambiar el canal de venta de una Oportunidad
   * (Req 2.1). Al confirmar, actualiza la Oportunidad en memoria sin recargar todo
   * el pipeline.
   */
  asignarCanal(oportunidad: Oportunidad): void {
    const data: AsignarCanalDialogData = {
      oportunidad,
      canales: this.canalesDisponibles(),
      puedeCrearCanal: this.puedeCrearCanal,
    };
    const ref = this.dialog.open(AsignarCanalDialog, {
      width: 'min(480px, 96vw)',
      maxWidth: 'min(480px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((actualizada?: Oportunidad) => {
      if (actualizada) {
        this.oportunidades.update((items) =>
          items.map((o) => (o.id === actualizada.id ? actualizada : o)),
        );
        this.toast.exito(
          actualizada.canalVentaId ? 'Canal de venta asignado.' : 'Canal de venta retirado.',
        );
      }
    });
  }
}
