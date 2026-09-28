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
//   - Un formulario compacto de alta de actividad (tipo, asunto, fecha, etc.).
//   - Una línea de tiempo cronológica (desc) con acciones por actividad:
//     completar, cancelar, reprogramar, editar y eliminar.
//
// Gating por permiso (deny-by-default): el alta/edición requiere actividad:crear
// / actividad:actualizar / actividad:eliminar; el timeline requiere
// actividad:listar. Reutiliza el overlay de operación premium para el feedback.
// No inventa endpoints: consume exclusivamente ActividadesService.
// =============================================================================

import { Component, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';

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
  TIPOS_ACTIVIDAD,
  TipoActividad,
} from '../models/comercial.models';

/** Tamano de pagina del timeline (resumen amplio, no paginacion visible). */
const TAMANO_TIMELINE = 100;

@Component({
  selector: 'app-comercial-timeline-actividades',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatMenuModule,
    MatTooltipModule,
    MatChipsModule,
    StateContainer,
  ],
  templateUrl: './timeline-actividades.html',
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
  private readonly fb = inject(FormBuilder);

  // Gating por permiso (deny-by-default, Req 3).
  protected readonly puedeListar = this.auth.tienePermiso('actividad', 'listar');
  protected readonly puedeCrear = this.auth.tienePermiso('actividad', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('actividad', 'actualizar');
  protected readonly puedeEliminar = this.auth.tienePermiso('actividad', 'eliminar');

  protected readonly tiposActividad = TIPOS_ACTIVIDAD;
  protected readonly etiquetaTipo = ETIQUETA_TIPO_ACTIVIDAD;
  protected readonly etiquetaEstado = ETIQUETA_ESTADO_ACTIVIDAD;
  protected readonly iconoTipo = ICONO_TIPO_ACTIVIDAD;

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly error = signal<string | undefined>(undefined);
  protected readonly actividades = signal<Actividad[]>([]);

  /** Controla la visibilidad del formulario de alta. */
  protected readonly mostrarFormulario = signal(false);

  /** Id de la actividad en edicion de asunto/descripcion (o null si ninguna). */
  protected readonly editandoId = signal<string | null>(null);

  /** Formulario de alta de actividad. La fecha usa datetime-local (hora local). */
  protected readonly formulario = this.fb.nonNullable.group({
    tipo: ['llamada' as TipoActividad, Validators.required],
    asunto: ['', [Validators.required, Validators.maxLength(200)]],
    descripcion: ['', Validators.maxLength(4000)],
    fechaProgramada: [this.ahoraLocalInput(), Validators.required],
    vencimiento: [''],
  });

  /** Formulario de edicion de asunto/descripcion (edicion en linea). */
  protected readonly formularioEdicion = this.fb.nonNullable.group({
    asunto: ['', [Validators.required, Validators.maxLength(200)]],
    descripcion: ['', Validators.maxLength(4000)],
  });

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

  /** Abre/cierra el formulario de alta, restableciendo sus valores. */
  alternarFormulario(): void {
    const abierto = !this.mostrarFormulario();
    this.mostrarFormulario.set(abierto);
    if (abierto) {
      this.formulario.reset({
        tipo: 'llamada',
        asunto: '',
        descripcion: '',
        fechaProgramada: this.ahoraLocalInput(),
        vencimiento: '',
      });
    }
  }

  /** Registra una nueva actividad de seguimiento. */
  crear(): void {
    if (this.formulario.invalid || !this.puedeCrear) {
      this.formulario.markAllAsTouched();
      return;
    }
    const v = this.formulario.getRawValue();
    const request = {
      clienteId: this.clienteId(),
      oportunidadId: this.oportunidadId() ?? undefined,
      tipo: v.tipo,
      asunto: v.asunto.trim(),
      descripcion: v.descripcion.trim() || undefined,
      fechaProgramada: this.aIso(v.fechaProgramada),
      vencimiento: v.vencimiento ? this.aIso(v.vencimiento) : undefined,
    };
    this.overlay
      .ejecutar(this.actividadesService.crear(request), {
        tipo: 'crear',
        textoProceso: 'Registrando actividad…',
        textoExito: 'Actividad registrada',
      })
      .subscribe({
        next: () => {
          this.notificaciones.exito('Actividad registrada en el historial.');
          this.mostrarFormulario.set(false);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.notificaciones.error(mensajeDeError(e)),
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

  /** Abre la edicion en linea de asunto/descripcion de una actividad. */
  editar(actividad: Actividad): void {
    this.editandoId.set(actividad.id);
    this.formularioEdicion.reset({
      asunto: actividad.asunto,
      descripcion: actividad.descripcion ?? '',
    });
  }

  /** Cancela la edicion en linea. */
  cancelarEdicion(): void {
    this.editandoId.set(null);
  }

  /** Guarda la edicion de asunto/descripcion de la actividad en edicion. */
  guardarEdicion(actividad: Actividad): void {
    if (this.formularioEdicion.invalid) {
      this.formularioEdicion.markAllAsTouched();
      return;
    }
    const v = this.formularioEdicion.getRawValue();
    this.overlay
      .ejecutar(
        this.actividadesService.editar(actividad.id, {
          asunto: v.asunto.trim(),
          descripcion: v.descripcion.trim() || undefined,
        }),
        { tipo: 'guardar', textoProceso: 'Guardando…', textoExito: 'Actividad actualizada' },
      )
      .subscribe({
        next: () => {
          this.notificaciones.exito('Actividad actualizada.');
          this.editandoId.set(null);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.notificaciones.error(mensajeDeError(e)),
      });
  }

  /** Indica si una actividad esta vencida (para resaltarla en el timeline). */
  protected esVencida(actividad: Actividad): boolean {
    return actividadVencida(actividad);
  }

  /** Convierte un valor de <input type="datetime-local"> a ISO-8601 (UTC). */
  private aIso(valorLocal: string): string {
    // El input datetime-local entrega 'YYYY-MM-DDTHH:mm' en hora local; el
    // constructor Date lo interpreta como local y toISOString lo pasa a UTC.
    return new Date(valorLocal).toISOString();
  }

  /** Valor 'YYYY-MM-DDTHH:mm' del instante actual en hora local para el input. */
  private ahoraLocalInput(): string {
    const ahora = new Date();
    const offset = ahora.getTimezoneOffset() * 60000;
    return new Date(ahora.getTime() - offset).toISOString().slice(0, 16);
  }
}
