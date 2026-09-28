// =============================================================================
// Vista de Actividades / agenda de seguimiento comercial (V79)
// -----------------------------------------------------------------------------
// Panel del vendedor con las actividades de seguimiento del tenant, filtrables
// por estado y tipo. Complementa al timeline embebido en la ficha del Cliente:
// aqui se ve la agenda global para priorizar el trabajo del dia.
//
// Los indicadores "Pendientes" y "Vencidas" son TOTALES REALES del backend (se
// leen del totalElements de consultas de conteo por estado), no un recuento de
// las filas cargadas en la pagina. Completar/cancelar una actividad hace una
// actualizacion OPTIMISTA (quita la fila y ajusta los KPIs al instante) y luego
// refresca desde el servidor. Las actividades se agrupan por urgencia
// (vencidas / hoy / proximas / sin fecha) para leer la agenda de un vistazo.
// Consume exclusivamente ActividadesService. Gated por actividad:listar.
// =============================================================================

import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import {
  IndicadorInfoDialog,
  type DatosIndicadorInfo,
} from '../../../shared/indicadores/indicador-info-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ActividadesService, FiltroActividades } from '../services/actividades.service';
import {
  Actividad,
  actividadVencida,
  ETIQUETA_ESTADO_ACTIVIDAD,
  ETIQUETA_TIPO_ACTIVIDAD,
  EstadoActividad,
  ICONO_TIPO_ACTIVIDAD,
  TIPOS_ACTIVIDAD,
  TipoActividad,
} from '../models/comercial.models';

/** Tamano de pagina de la agenda. */
const TAMANO_AGENDA = 100;

/** Clave de agrupacion por urgencia de una actividad pendiente. */
type GrupoUrgencia = 'vencidas' | 'hoy' | 'proximas' | 'sin_fecha' | 'otras';

/** Grupo de actividades para el render (encabezado + items). */
interface GrupoActividades {
  clave: GrupoUrgencia;
  etiqueta: string;
  icono: string;
  actividades: Actividad[];
}

/** Orden y metadatos de los grupos de urgencia. */
const GRUPOS_META: readonly { clave: GrupoUrgencia; etiqueta: string; icono: string }[] = [
  { clave: 'vencidas', etiqueta: 'Vencidas', icono: 'warning' },
  { clave: 'hoy', etiqueta: 'Para hoy', icono: 'today' },
  { clave: 'proximas', etiqueta: 'Próximas', icono: 'event_upcoming' },
  { clave: 'sin_fecha', etiqueta: 'Sin fecha', icono: 'help_outline' },
  { clave: 'otras', etiqueta: 'Otras', icono: 'inbox' },
];

@Component({
  selector: 'app-comercial-actividades',
  imports: [
    DatePipe,
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatMenuModule,
    MatTooltipModule,
    PageHeader,
    StateContainer,
    KpiTile,
  ],
  templateUrl: './actividades.html',
  styleUrl: './actividades.scss',
})
export class ComercialActividades implements OnInit {
  private readonly actividadesService = inject(ActividadesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeActualizar = this.auth.tienePermiso('actividad', 'actualizar');
  protected readonly puedeVerCliente = this.auth.tienePermiso('cliente', 'leer');

  protected readonly tiposActividad = TIPOS_ACTIVIDAD;
  protected readonly etiquetaTipo = ETIQUETA_TIPO_ACTIVIDAD;
  protected readonly etiquetaEstado = ETIQUETA_ESTADO_ACTIVIDAD;
  protected readonly iconoTipo = ICONO_TIPO_ACTIVIDAD;

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly error = signal<string | undefined>(undefined);
  protected readonly actividades = signal<Actividad[]>([]);

  /**
   * Totales REALES del tenant (no de la pagina): se leen del totalElements de
   * consultas de conteo por estado en el backend. Empiezan en null (— mientras
   * cargan).
   */
  protected readonly totalPendientes = signal<number | null>(null);
  protected readonly totalVencidas = signal<number | null>(null);
  protected readonly totalCompletadas = signal<number | null>(null);
  protected readonly totalCanceladas = signal<number | null>(null);

  /** Filtros de la agenda. Por defecto muestra las pendientes. */
  protected readonly filtros = this.fb.nonNullable.group({
    estado: ['pendiente' as EstadoActividad | ''],
    tipo: ['' as TipoActividad | ''],
  });

  /**
   * Agrupa las actividades cargadas por urgencia (vencidas / hoy / proximas /
   * sin fecha) cuando el filtro incluye pendientes; para estados finales
   * (completada/cancelada) no tiene sentido agrupar por urgencia y se muestran
   * todas juntas en el grupo "otras". Los grupos vacios no se renderizan.
   */
  protected readonly grupos = computed<GrupoActividades[]>(() => {
    const items = this.actividades();
    const porClave = new Map<GrupoUrgencia, Actividad[]>();
    for (const a of items) {
      const clave = this.grupoDe(a);
      const lista = porClave.get(clave) ?? [];
      lista.push(a);
      porClave.set(clave, lista);
    }
    return GRUPOS_META.map((meta) => ({
      ...meta,
      actividades: porClave.get(meta.clave) ?? [],
    })).filter((g) => g.actividades.length > 0);
  });

  ngOnInit(): void {
    this.cargar();
    this.cargarConteos();
  }

  /** Carga las actividades del tenant aplicando los filtros seleccionados. */
  cargar(): void {
    this.fase.set('cargando');
    const filtro = this.filtroActual();
    this.actividadesService.listar(filtro, 0, TAMANO_AGENDA).subscribe({
      next: (pagina) => {
        this.actividades.set(pagina.content);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /**
   * Recalcula los TOTALES reales de pendientes/vencidas/completadas del tenant.
   * Usa el totalElements de consultas de tamano 1 (basta para el conteo). Las
   * "vencidas" no tienen filtro de servidor: se aproximan contando las pendientes
   * cargadas que ya vencieron cuando el total de pendientes cabe en una pagina;
   * si hay mas pendientes que el tope, se marca el conteo como "al menos" con el
   * numero de vencidas visibles (el backend no expone un filtro de vencidas).
   */
  cargarConteos(): void {
    this.actividadesService.listar({ estado: 'pendiente' }, 0, 1).subscribe({
      next: (p) => this.totalPendientes.set(p.totalElements),
      error: () => this.totalPendientes.set(null),
    });
    this.actividadesService.listar({ estado: 'completada' }, 0, 1).subscribe({
      next: (p) => this.totalCompletadas.set(p.totalElements),
      error: () => this.totalCompletadas.set(null),
    });
    this.actividadesService.listar({ estado: 'cancelada' }, 0, 1).subscribe({
      next: (p) => this.totalCanceladas.set(p.totalElements),
      error: () => this.totalCanceladas.set(null),
    });
    // Vencidas: el backend no expone un filtro dedicado, asi que se cuenta sobre
    // las pendientes (hasta el tope de pagina). Es exacto cuando pendientes<=100.
    this.actividadesService.listar({ estado: 'pendiente' }, 0, TAMANO_AGENDA).subscribe({
      next: (p) => this.totalVencidas.set(p.content.filter((a) => actividadVencida(a)).length),
      error: () => this.totalVencidas.set(null),
    });
  }

  /** Construye el filtro actual a partir del formulario. */
  private filtroActual(): FiltroActividades {
    const v = this.filtros.getRawValue();
    return {
      estado: v.estado || undefined,
      tipo: v.tipo || undefined,
    };
  }

  /**
   * Marca una actividad como completada. Actualizacion OPTIMISTA: la fila se
   * quita de inmediato de la lista y se ajustan los KPIs; luego se refrescan los
   * conteos desde el servidor. Si el backend falla, se recarga para revertir.
   */
  completar(actividad: Actividad): void {
    if (!this.puedeActualizar) {
      return;
    }
    this.overlay
      .ejecutar(this.actividadesService.completar(actividad.id), {
        tipo: 'guardar',
        textoProceso: 'Completando…',
        textoExito: 'Actividad completada',
      })
      .subscribe({
        next: () => {
          this.notificaciones.exito('Actividad marcada como completada.');
          this.quitarLocal(actividad, 'completada');
          this.cargarConteos();
        },
        error: (e: HttpErrorResponse) => {
          this.notificaciones.error(mensajeDeError(e));
          this.cargar();
        },
      });
  }

  /**
   * Marca una actividad como cancelada (con confirmacion). Misma estrategia
   * optimista que completar.
   */
  async cancelar(actividad: Actividad): Promise<void> {
    if (!this.puedeActualizar) {
      return;
    }
    const ok = await this.confirm.confirmar({
      titulo: 'Cancelar actividad',
      mensaje: `Se cancelará "${actividad.asunto}". Deseas continuar?`,
      textoConfirmar: 'Cancelar actividad',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.actividadesService.cancelar(actividad.id), {
        tipo: 'guardar',
        textoProceso: 'Cancelando…',
        textoExito: 'Actividad cancelada',
      })
      .subscribe({
        next: () => {
          this.notificaciones.exito('Actividad cancelada.');
          this.quitarLocal(actividad, 'cancelada');
          this.cargarConteos();
        },
        error: (e: HttpErrorResponse) => {
          this.notificaciones.error(mensajeDeError(e));
          this.cargar();
        },
      });
  }

  /**
   * Refleja de inmediato el nuevo estado de una actividad en memoria: si el
   * filtro actual ya no la incluye (p. ej. filtrando "pendiente" y se completa),
   * la quita de la lista; si el filtro es "Todos", actualiza su estado en sitio.
   * Ajusta tambien los KPIs locales para que el cambio se vea al instante.
   */
  private quitarLocal(actividad: Actividad, nuevoEstado: EstadoActividad): void {
    const estadoFiltro = this.filtros.getRawValue().estado;
    const eraVencida = actividadVencida(actividad);

    if (estadoFiltro && estadoFiltro !== nuevoEstado) {
      // El filtro ya no incluye esta actividad: se retira de la lista.
      this.actividades.update((items) => items.filter((a) => a.id !== actividad.id));
    } else {
      // Filtro "Todos" (o coincide): se actualiza el estado en sitio.
      this.actividades.update((items) =>
        items.map((a) => (a.id === actividad.id ? { ...a, estado: nuevoEstado } : a)),
      );
    }
    if (this.actividades().length === 0) {
      this.fase.set('vacio');
    }
    // Ajuste optimista de los KPIs (se confirmaran con cargarConteos()).
    this.totalPendientes.update((n) => (n === null ? n : Math.max(0, n - 1)));
    if (eraVencida) {
      this.totalVencidas.update((n) => (n === null ? n : Math.max(0, n - 1)));
    }
    if (nuevoEstado === 'completada') {
      this.totalCompletadas.update((n) => (n === null ? n : n + 1));
    }
    if (nuevoEstado === 'cancelada') {
      this.totalCanceladas.update((n) => (n === null ? n : n + 1));
    }
  }

  /** Grupo de urgencia de una actividad (solo las pendientes se clasifican). */
  private grupoDe(actividad: Actividad): GrupoUrgencia {
    if (actividad.estado !== 'pendiente') {
      return 'otras';
    }
    const limite = actividad.vencimiento ?? actividad.fechaProgramada;
    if (!limite) {
      return 'sin_fecha';
    }
    const fecha = new Date(limite);
    if (Number.isNaN(fecha.getTime())) {
      return 'sin_fecha';
    }
    const ahora = new Date();
    if (fecha.getTime() < ahora.getTime()) {
      return 'vencidas';
    }
    if (this.mismoDia(fecha, ahora)) {
      return 'hoy';
    }
    return 'proximas';
  }

  /** `true` si dos fechas caen el mismo dia calendario (local). */
  private mismoDia(a: Date, b: Date): boolean {
    return (
      a.getFullYear() === b.getFullYear() &&
      a.getMonth() === b.getMonth() &&
      a.getDate() === b.getDate()
    );
  }

  /** Indica si una actividad esta vencida (para resaltarla). */
  protected esVencida(actividad: Actividad): boolean {
    return actividadVencida(actividad);
  }

  /**
   * Abre el dialogo explicativo de un indicador de la agenda (¿qué es? / ¿cómo
   * se calcula? / ¿por qué importa?). La clave debe coincidir con una del
   * catalogo central de indicadores. El valor null (mientras carga) se
   * normaliza a 0 para el dialogo.
   */
  abrirInfoKpi(clave: string, etiqueta: string, valor: number | null, unidad: string): void {
    const datos: DatosIndicadorInfo = { clave, etiqueta, valor: valor ?? 0, unidad };
    this.dialog.open(IndicadorInfoDialog, {
      data: datos,
      width: '32rem',
      maxWidth: '92vw',
      autoFocus: false,
    });
  }
}
