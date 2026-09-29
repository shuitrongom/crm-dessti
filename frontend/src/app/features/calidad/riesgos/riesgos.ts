// =============================================================================
// Vista de Riesgos del SGC (Req 70.3, clausula 6.1.2)
// -----------------------------------------------------------------------------
// Listado paginado con filtro por estado/nivel; identificacion de un riesgo
// (probabilidad x impacto -> nivel derivado por el servidor); y avance de estado
// (identificado -> en_tratamiento -> mitigado/aceptado). Dashboard con scorecard
// por nivel y dona de composicion. Acciones gobernadas por permiso atomico.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
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

import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { humanizarEstado, tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { MetricChart, MetricPoint } from '../../../shared/components/metric-chart/metric-chart';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { DashboardSection } from '../../../shared/components/dashboard-section/dashboard-section';
import { CalidadService } from '../services/calidad.service';
import { IMPACTOS, PROBABILIDADES, Riesgo } from '../models/calidad.models';

/** Transiciones de estado permitidas desde el estado actual de un Riesgo. */
const TRANSICIONES_RIESGO: Record<string, string[]> = {
  identificado: ['en_tratamiento', 'aceptado'],
  en_tratamiento: ['mitigado', 'aceptado'],
  mitigado: [],
  aceptado: [],
};

@Component({
  selector: 'app-calidad-riesgos',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
    MetricChart,
    KpiTile,
    DashboardSection,
  ],
  templateUrl: './riesgos.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../calidad.scss',
})
export class CalidadRiesgos {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CalidadService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;
  protected readonly probabilidades = PROBABILIDADES;
  protected readonly impactos = IMPACTOS;

  protected readonly puedeCrear = this.auth.tienePermiso('riesgo', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('riesgo', 'cambiar_estado');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'descripcion', encabezado: 'Riesgo' },
    { clave: 'probabilidad', encabezado: 'Probabilidad' },
    { clave: 'impacto', encabezado: 'Impacto' },
    { clave: 'nivel', encabezado: 'Nivel' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'identificado', etiqueta: 'Identificado' },
    { valor: 'en_tratamiento', etiqueta: 'En tratamiento' },
    { valor: 'mitigado', etiqueta: 'Mitigado' },
    { valor: 'aceptado', etiqueta: 'Aceptado' },
  ];

  protected readonly nivelesFiltro = [
    { valor: '', etiqueta: 'Todos los niveles' },
    { valor: 'bajo', etiqueta: 'Bajo' },
    { valor: 'medio', etiqueta: 'Medio' },
    { valor: 'alto', etiqueta: 'Alto' },
    { valor: 'critico', etiqueta: 'Crítico' },
  ];

  protected readonly estado = signal<EstadoSolicitud<Riesgo[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly filtroNivel = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  protected readonly formAlta = this.fb.nonNullable.group({
    descripcion: ['', [Validators.required]],
    probabilidad: ['media', [Validators.required]],
    impacto: ['medio', [Validators.required]],
    acciones: [''],
  });

  constructor() {
    this.cargar();
  }

  /** Scorecard por nivel derivado sobre la pagina cargada. */
  protected readonly resumen = computed(() => {
    const riesgos = this.estado().datos ?? [];
    let critico = 0;
    let alto = 0;
    let medio = 0;
    let bajo = 0;
    for (const r of riesgos) {
      if (r.nivelDerivado === 'critico') {
        critico += 1;
      } else if (r.nivelDerivado === 'alto') {
        alto += 1;
      } else if (r.nivelDerivado === 'medio') {
        medio += 1;
      } else {
        bajo += 1;
      }
    }
    return { critico, alto, medio, bajo, total: riesgos.length };
  });

  /** Dona: composicion de riesgos por nivel derivado. */
  protected readonly composicionNivel = computed<MetricPoint[]>(() => {
    const r = this.resumen();
    return [
      { etiqueta: 'Crítico', valor: r.critico },
      { etiqueta: 'Alto', valor: r.alto },
      { etiqueta: 'Medio', valor: r.medio },
      { etiqueta: 'Bajo', valor: r.bajo },
    ];
  });

  /** Dona: composicion de riesgos por estado de tratamiento. */
  protected readonly composicionEstado = computed<MetricPoint[]>(() => {
    const riesgos = this.estado().datos ?? [];
    const etiquetas: Record<string, string> = {
      identificado: 'Identificado',
      en_tratamiento: 'En tratamiento',
      mitigado: 'Mitigado',
      aceptado: 'Aceptado',
    };
    const cuenta = new Map<string, number>();
    for (const r of riesgos) {
      cuenta.set(r.estado, (cuenta.get(r.estado) ?? 0) + 1);
    }
    return Object.keys(etiquetas)
      .map((clave) => ({ etiqueta: etiquetas[clave], valor: cuenta.get(clave) ?? 0 }))
      .filter((p) => p.valor > 0);
  });

  /**
   * Gauge: porcentaje de riesgos ya tratados (mitigados o aceptados) sobre el total
   * (0-100). Mide cuanto del mapa de riesgos ya esta gestionado.
   */
  protected readonly porcentajeTratados = computed<number>(() => {
    const riesgos = this.estado().datos ?? [];
    if (riesgos.length === 0) {
      return 0;
    }
    const tratados = riesgos.filter(
      (r) => r.estado === 'mitigado' || r.estado === 'aceptado',
    ).length;
    return Math.max(0, Math.min(100, Math.round((tratados / riesgos.length) * 100)));
  });

  protected readonly hayDatos = computed(() => (this.estado().datos ?? []).length > 0);

  /** Clase CSS del badge de nivel (color por severidad). */
  claseNivel(nivel: string): string {
    return `cal-nivel cal-nivel--${nivel}`;
  }

  /** Estados a los que puede transitar el riesgo desde su estado actual. */
  transiciones(r: Riesgo): string[] {
    return this.puedeCambiarEstado ? (TRANSICIONES_RIESGO[r.estado] ?? []) : [];
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarRiesgos(
        this.filtroEstado() || null,
        this.filtroNivel() || null,
        this.page(),
        this.size(),
      )
      .subscribe({
        next: (pagina) => {
          this.total.set(pagina.totalElements);
          this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
        },
        error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
      });
  }

  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  aplicarFiltroEstado(valor: string): void {
    this.filtroEstado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  aplicarFiltroNivel(valor: string): void {
    this.filtroNivel.set(valor);
    this.page.set(0);
    this.cargar();
  }

  alternarAlta(): void {
    this.mostrarAlta.update((v) => !v);
  }

  crear(): void {
    if (this.formAlta.invalid) {
      this.formAlta.markAllAsTouched();
      return;
    }
    const v = this.formAlta.getRawValue();
    this.guardando.set(true);
    this.overlay
      .ejecutar(
        this.service.identificarRiesgo({
          descripcion: v.descripcion,
          probabilidad: v.probabilidad as Riesgo['probabilidad'],
          impacto: v.impacto as Riesgo['impacto'],
          acciones: v.acciones.trim() || null,
        }),
        { tipo: 'crear', textoProceso: 'Identificando riesgo…', textoExito: 'Riesgo identificado' },
      )
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Riesgo identificado.');
          this.formAlta.reset({
            descripcion: '',
            probabilidad: 'media',
            impacto: 'medio',
            acciones: '',
          });
          this.mostrarAlta.set(false);
          this.page.set(0);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  cambiarEstado(r: Riesgo, destino: string): void {
    this.overlay
      .ejecutar(this.service.cambiarEstadoRiesgo(r.id, destino), {
        tipo: 'procesar',
        textoProceso: 'Actualizando riesgo…',
        textoExito: 'Riesgo actualizado',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Estado del riesgo actualizado.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }
}
