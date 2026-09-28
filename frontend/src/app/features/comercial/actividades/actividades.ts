// =============================================================================
// Vista de Actividades / agenda de seguimiento comercial (V79)
// -----------------------------------------------------------------------------
// Panel del vendedor con TODAS las actividades de seguimiento del tenant,
// filtrables por estado y tipo. Complementa al timeline embebido en la ficha del
// Cliente: aqui se ve la agenda global (pendientes, vencidas, completadas) para
// priorizar el trabajo del dia. Cada fila enlaza a la ficha del Cliente para el
// contexto completo. Consume exclusivamente ActividadesService (sin endpoints
// nuevos). Gated por permiso actividad:listar (deny-by-default).
// =============================================================================

import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
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

@Component({
  selector: 'app-comercial-actividades',
  imports: [
    DatePipe,
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatTooltipModule,
    PageHeader,
    StateContainer,
  ],
  templateUrl: './actividades.html',
  styleUrl: './actividades.scss',
})
export class ComercialActividades implements OnInit {
  private readonly actividadesService = inject(ActividadesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly puedeActualizar = this.auth.tienePermiso('actividad', 'actualizar');

  protected readonly tiposActividad = TIPOS_ACTIVIDAD;
  protected readonly etiquetaTipo = ETIQUETA_TIPO_ACTIVIDAD;
  protected readonly etiquetaEstado = ETIQUETA_ESTADO_ACTIVIDAD;
  protected readonly iconoTipo = ICONO_TIPO_ACTIVIDAD;

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly error = signal<string | undefined>(undefined);
  protected readonly actividades = signal<Actividad[]>([]);

  /** Filtros de la agenda. Por defecto muestra las pendientes. */
  protected readonly filtros = this.fb.nonNullable.group({
    estado: ['pendiente' as EstadoActividad | ''],
    tipo: ['' as TipoActividad | ''],
  });

  protected readonly pendientes = computed(
    () => this.actividades().filter((a) => a.estado === 'pendiente').length,
  );

  protected readonly vencidas = computed(
    () => this.actividades().filter((a) => actividadVencida(a)).length,
  );

  ngOnInit(): void {
    this.cargar();
  }

  /** Carga las actividades del tenant aplicando los filtros seleccionados. */
  cargar(): void {
    this.fase.set('cargando');
    const v = this.filtros.getRawValue();
    const filtro: FiltroActividades = {
      estado: v.estado || undefined,
      tipo: v.tipo || undefined,
    };
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

  /** Marca una actividad como completada desde la agenda. */
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
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.notificaciones.error(mensajeDeError(e)),
      });
  }

  /** Indica si una actividad esta vencida (para resaltarla). */
  protected esVencida(actividad: Actividad): boolean {
    return actividadVencida(actividad);
  }
}
