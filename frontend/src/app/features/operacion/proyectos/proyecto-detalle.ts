// =============================================================================
// Vista de detalle de Proyecto (Req 21) — avance consolidado por sitio
// -----------------------------------------------------------------------------
// Muestra el estado consolidado del Proyecto y, por cada Sitio, su avance en las
// cuatro fases (levantamiento, permiso, fabricacion, instalacion) con una barra
// de progreso accesible (ProgressBadge). Permite agregar Sitios (Req 21.2). Las
// acciones se gobiernan por permiso proyecto:{leer,actualizar}.
// =============================================================================

import { Component, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ProgressBadge } from '../../../shared/components/progress-badge/progress-badge';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ProyectosService } from '../services/proyectos.service';
import { Proyecto, SitioAvance, porcentajeAvanceSitio } from '../models/operacion.models';

@Component({
  selector: 'app-operacion-proyecto-detalle',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    ProgressBadge,
  ],
  templateUrl: './proyecto-detalle.html',
  styleUrl: './proyecto-detalle.scss',
})
export class OperacionProyectoDetalle {
  /** Identificador del Proyecto tomado de la ruta (:id). */
  readonly id = input.required<string>();

  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProyectosService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeAgregarSitio = this.auth.tienePermiso('proyecto', 'actualizar');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly proyecto = signal<Proyecto | null>(null);
  protected readonly guardando = signal(false);
  protected readonly formularioAbierto = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    direccion: ['', [Validators.maxLength(500)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service.consultar(this.id()).subscribe({
      next: (proyecto) => {
        this.proyecto.set(proyecto);
        this.fase.set('ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Porcentaje de avance de un Sitio (0..100) segun las fases cubiertas. */
  avanceDe(sitio: SitioAvance): number {
    return porcentajeAvanceSitio(sitio);
  }

  /**
   * Estado derivado del avance del Sitio para colorear ProgressBadge sin ser el
   * color el unico portador de significado: 100% cumplido, >=50% en_curso, resto
   * en_riesgo.
   */
  estadoDe(sitio: SitioAvance): 'cumplido' | 'en_curso' | 'en_riesgo' {
    const avance = this.avanceDe(sitio);
    if (avance >= 100) {
      return 'cumplido';
    }
    return avance >= 50 ? 'en_curso' : 'en_riesgo';
  }

  alternarFormulario(): void {
    this.formularioAbierto.update((v) => !v);
    if (this.formularioAbierto()) {
      this.form.reset({ nombre: '', direccion: '' });
    }
  }

  /** Agrega un Sitio al Proyecto (Req 21.2). */
  agregarSitio(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.service
      .agregarSitio(this.id(), { nombre: v.nombre.trim(), direccion: v.direccion.trim() || null })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Sitio agregado.');
          this.formularioAbierto.set(false);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }
}
