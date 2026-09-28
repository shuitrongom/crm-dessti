// =============================================================================
// Vista de detalle de Proyecto (Req 21) — avance consolidado por sitio
// -----------------------------------------------------------------------------
// Muestra el estado consolidado del Proyecto y, por cada Sitio, su avance en las
// cuatro fases (levantamiento, permiso, fabricacion, instalacion) con una barra
// de progreso accesible (ProgressBadge). Permite agregar Sitios (Req 21.2). Las
// acciones se gobiernan por permiso proyecto:{leer,actualizar}.
// =============================================================================

import { Component, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ProgressBadge } from '../../../shared/components/progress-badge/progress-badge';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';
import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { humanizarEstado, tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { MetricChart, MetricPoint } from '../../../shared/components/metric-chart/metric-chart';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { DashboardSection } from '../../../shared/components/dashboard-section/dashboard-section';

import { ProyectosService } from '../services/proyectos.service';
import {
  ETIQUETA_FASE_SITIO,
  FaseSitioGenerica,
  ORDEN_FASE_SITIO,
  Proyecto,
  SitioAvance,
  SitioFase,
  porcentajeAvanceSitio,
  porcentajeFaseSitio,
  siguienteFaseSitio,
} from '../models/operacion.models';

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
    MatMenuModule,
    PageHeader,
    StateContainer,
    ProgressBadge,
    EstadoChip,
    MetricChart,
    KpiTile,
    DashboardSection,
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
  private readonly overlay = inject(OperacionOverlayService);
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

  // ------------------------------------------------------------------
  // Multi-sitio (giros genericos): fase editable por Sitio (Req 3.2)
  // ------------------------------------------------------------------

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;
  protected readonly etiquetaFase = ETIQUETA_FASE_SITIO;
  protected readonly fasesTodas = ORDEN_FASE_SITIO;

  /** Correccion/retroceso de fase: operacion administrativa (proyecto:cambiar_estado). */
  protected readonly puedeCorregir = this.auth.tienePermiso('proyecto', 'cambiar_estado');

  /** Indica si el Proyecto usa el pipeline multi-sitio generico (no anuncios). */
  protected readonly esMultisitio = computed(() => this.proyecto()?.estadoMultisitio != null);

  /** Scorecard de sucursales por fase sobre el Proyecto multi-sitio cargado. */
  protected readonly resumenMultisitio = computed(() => {
    const sitios = this.proyecto()?.sitiosMultisitio ?? [];
    let pendientes = 0;
    let enCurso = 0;
    let entregados = 0;
    for (const s of sitios) {
      if (s.fase === 'entregado') {
        entregados += 1;
      } else if (s.fase === 'pendiente') {
        pendientes += 1;
      } else {
        enCurso += 1;
      }
    }
    return { total: sitios.length, pendientes, enCurso, entregados };
  });

  /**
   * Gauge: avance global del proyecto multi-sitio (0-100). Promedia el porcentaje de
   * avance de cada sucursal segun su fase (pendiente=0 … entregado=100).
   */
  protected readonly avanceGlobal = computed<number>(() => {
    const sitios = this.proyecto()?.sitiosMultisitio ?? [];
    if (sitios.length === 0) {
      return 0;
    }
    const suma = sitios.reduce((acc, s) => acc + porcentajeFaseSitio(s.fase), 0);
    return Math.max(0, Math.min(100, Math.round(suma / sitios.length)));
  });

  /** Dona: distribucion de sucursales por fase de despliegue. */
  protected readonly composicionFases = computed<MetricPoint[]>(() => {
    const sitios = this.proyecto()?.sitiosMultisitio ?? [];
    const cuenta = new Map<FaseSitioGenerica, number>();
    for (const s of sitios) {
      cuenta.set(s.fase, (cuenta.get(s.fase) ?? 0) + 1);
    }
    return (Object.keys(ETIQUETA_FASE_SITIO) as FaseSitioGenerica[]).map((f) => ({
      etiqueta: ETIQUETA_FASE_SITIO[f],
      valor: cuenta.get(f) ?? 0,
    }));
  });

  /** Porcentaje de avance de una sucursal segun su fase. */
  porcentajeSitio(sitio: SitioFase): number {
    return porcentajeFaseSitio(sitio.fase);
  }

  /** Siguiente fase disponible para una sucursal (null si ya esta entregada). */
  siguienteFase(sitio: SitioFase): FaseSitioGenerica | null {
    return this.puedeAgregarSitio ? siguienteFaseSitio(sitio.fase) : null;
  }

  /**
   * Avanza la fase de una sucursal a la fase destino (Req 3.2), permitiendo adjuntar
   * de forma opcional una referencia de evidencia (enlace a foto/acta/documento).
   */
  avanzarSitio(sitio: SitioFase, destino: FaseSitioGenerica): void {
    const evidenciaUrl = this.pedirEvidencia();
    if (evidenciaUrl === false) {
      return; // el usuario cancelo la captura de evidencia
    }
    this.overlay
      .ejecutar(
        this.service.actualizarAvanceSitio(this.id(), sitio.sitio.id, {
          fase: destino,
          evidenciaUrl: evidenciaUrl || null,
        }),
        { tipo: 'procesar', textoProceso: 'Actualizando sitio…', textoExito: 'Sitio actualizado' },
      )
      .subscribe({
        next: (proyecto) => {
          this.proyecto.set(proyecto);
          this.toast.exito('Avance del sitio actualizado.');
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }

  /**
   * Corrige (incluido retroceso) la fase de una sucursal a cualquier fase. Operacion
   * administrativa; solo visible con permiso proyecto:cambiar_estado (Req 3.2).
   */
  corregirFase(sitio: SitioFase, destino: FaseSitioGenerica): void {
    if (destino === sitio.fase) {
      return;
    }
    this.overlay
      .ejecutar(
        this.service.corregirFaseSitio(this.id(), sitio.sitio.id, { fase: destino }),
        { tipo: 'procesar', textoProceso: 'Corrigiendo fase…', textoExito: 'Fase corregida' },
      )
      .subscribe({
        next: (proyecto) => {
          this.proyecto.set(proyecto);
          this.toast.exito('Fase de la sucursal corregida.');
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }

  /**
   * Solicita una referencia de evidencia opcional para el avance. Devuelve la cadena
   * (posiblemente vacia) o {@code false} si el usuario cancela. Usa un prompt simple;
   * el enlace apunta a la evidencia ya almacenada (foto/acta/documento).
   */
  private pedirEvidencia(): string | false {
    const entrada = window.prompt(
      'Enlace de evidencia (opcional): pega la URL del documento, foto o acta. Deja vacío si no aplica.',
      '',
    );
    return entrada === null ? false : entrada.trim();
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
