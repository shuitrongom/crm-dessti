// =============================================================================
// TimelineActividades — historial de interacciones y tareas de seguimiento (V79)
// -----------------------------------------------------------------------------
// Componente reutilizable que muestra y gestiona las Actividades de seguimiento
// de un Cliente y, opcionalmente, de una Oportunidad concreta. Se embebe en la
// ficha 360 del Cliente (nivel Cliente) y en el detalle de una Oportunidad
// (nivel Oportunidad, pasando `oportunidadId`).
//
// Presenta:
//   - Una fila de indicadores de seguimiento (pendientes, vencidas, completadas).
//   - Una línea de tiempo cronológica (desc) con acciones por actividad.
//
// El alta y la edición se hacen en un MODAL animado (ActividadFormDialog),
// consistente con el resto de la plataforma; el resto de acciones (completar,
// cancelar, eliminar) usan el overlay de operación premium.
//
// Gating por permiso (deny-by-default): el alta/edición requiere actividad:crear
// / actividad:actualizar / actividad:eliminar; el timeline requiere
// actividad:listar. No inventa endpoints: consume exclusivamente
// ActividadesService.
// =============================================================================

import {
  Component,
  OnInit,
  computed,
  effect,
  inject,
  input,
  signal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';

import { StateContainer } from '../../../shared/components/state-container/state-container';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ActividadesService } from '../services/actividades.service';
import {
  Actividad,
  actividadVencida,
  ETIQUETA_ESTADO_ACTIVIDAD,
  ETIQUETA_TIPO_ACTIVIDAD,
  ICONO_TIPO_ACTIVIDAD,
} from '../models/comercial.models';
import { ActividadFormDialog, ActividadFormDialogData } from './actividad-form-dialog';

/** Tamano de pagina del timeline (resumen amplio, no paginacion visible). */
const TAMANO_TIMELINE = 100;

@Component({
  selector: 'app-comercial-timeline-actividades',
  imports: [
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    StateContainer,
  ],
  templateUrl: './timeline-actividades.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './timeline-actividades.scss',
})
export class TimelineActividades implements OnInit {
  /** Cliente al que pertenecen las actividades (obligatorio). */
  readonly clienteId = input.required<string>();

  /** Oportunidad a la que se acotan/vinculan las actividades (opcional). */
  readonly oportunidadId = input<string | null>(null);

  private readonly actividadesService = inject(ActividadesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  // Gating por permiso (deny-by-default, Req 3).
  protected readonly puedeListar = this.auth.tienePermiso('actividad', 'listar');
  protected readonly puedeCrear = this.auth.tienePermiso('actividad', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('actividad', 'actualizar');
  protected readonly puedeEliminar = this.auth.tienePermiso('actividad', 'eliminar');

  protected readonly etiquetaTipo = ETIQUETA_TIPO_ACTIVIDAD;
  protected readonly etiquetaEstado = ETIQUETA_ESTADO_ACTIVIDAD;
  protected readonly iconoTipo = ICONO_TIPO_ACTIVIDAD;

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly error = signal<string | undefined>(undefined);
  protected readonly actividades = signal<Actividad[]>([]);

  // --- Indicadores de seguimiento (calculados en cliente) --------------------

  protected readonly pendientes = computed(
    () => this.actividades().filter((a) => a.estado === 'pendiente').length,
  );

  protected readonly vencidas = computed(
    () => this.actividades().filter((a) => actividadVencida(a)).length,
  );

  protected readonly completadas = computed(
    () => this.actividades().filter((a) => a.estado === 'completada').length,
  );

  constructor() {
    // Recarga el timeline cuando cambia el cliente o la oportunidad de contexto.
    effect(() => {
      this.clienteId();
      this.oportunidadId();
      this.cargar();
    });
  }

  ngOnInit(): void {
    // La carga inicial la dispara el effect del constructor al leer las inputs.
  }

  /** Carga las actividades del contexto (cliente y, si aplica, oportunidad). */
  cargar(): void {
    if (!this.puedeListar) {
      this.fase.set('ok');
      return;
    }
    const clienteId = this.clienteId();
    if (!clienteId) {
      return;
    }
    this.fase.set('cargando');
    this.actividadesService
      .listar({ clienteId, oportunidadId: this.oportunidadId() ?? undefined }, 0, TAMANO_TIMELINE)
      .subscribe({
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

  /** Abre el modal de alta de actividad y recarga el timeline si se creó. */
  nueva(): void {
    this.abrirFormulario();
  }

  /** Abre el modal de edición con los datos de la actividad y recarga si cambió. */
  editar(actividad: Actividad): void {
    this.abrirFormulario(actividad);
  }

  /**
   * Abre el modal de formulario de Actividad (alta si no se pasa `actividad`,
   * edición si se pasa) y recarga el timeline cuando el diálogo confirma.
   */
  private abrirFormulario(actividad?: Actividad): void {
    const data: ActividadFormDialogData = {
      actividad,
      clienteId: this.clienteId(),
      oportunidadId: this.oportunidadId(),
    };
    const ref = this.dialog.open(ActividadFormDialog, {
      width: 'min(720px, 96vw)',
      maxWidth: 'min(720px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((guardada?: Actividad) => {
      if (guardada) {
        this.notificaciones.exito(
          actividad ? 'Actividad actualizada.' : 'Actividad registrada en el historial.',
        );
        this.cargar();
      }
    });
  }

  /** Marca una actividad como completada. */
  completar(actividad: Actividad): void {
    this.overlay
      .ejecutar(this.actividadesService.completar(actividad.id), {
        tipo: 'guardar',
        textoProceso: 'Completando…',
        textoExito: 'Actividad completada',
      })
      .subscribe({
        next: () => {
          this.notificaciones.exito('Actividad marcada como completada.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.notificaciones.error(mensajeDeError(e)),
      });
  }

  /** Marca una actividad como cancelada. */
  cancelar(actividad: Actividad): void {
    this.overlay
      .ejecutar(this.actividadesService.cancelar(actividad.id), {
        tipo: 'guardar',
        textoProceso: 'Cancelando…',
        textoExito: 'Actividad cancelada',
      })
      .subscribe({
        next: () => {
          this.notificaciones.exito('Actividad cancelada.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.notificaciones.error(mensajeDeError(e)),
      });
  }

  /** Elimina una actividad del historial. */
  eliminar(actividad: Actividad): void {
    this.overlay
      .ejecutar(this.actividadesService.eliminar(actividad.id), {
        tipo: 'eliminar',
        textoProceso: 'Eliminando…',
        textoExito: 'Actividad eliminada',
      })
      .subscribe({
        next: () => {
          this.notificaciones.exito('Actividad eliminada del historial.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.notificaciones.error(mensajeDeError(e)),
      });
  }

  /** Indica si una actividad esta vencida (para resaltarla en el timeline). */
  protected esVencida(actividad: Actividad): boolean {
    return actividadVencida(actividad);
  }
}
