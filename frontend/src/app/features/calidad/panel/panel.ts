// =============================================================================
// Panel del Sistema de Gestion de Calidad (SGC ISO 9001:2026, Req 70.6/70.7)
// -----------------------------------------------------------------------------
// Vista de indicadores de solo lectura del modulo calidad: scorecard de cultura
// de calidad (no conformidades, acciones correctivas, quejas, tiempo medio de
// cierre, tasa de reincidencia) + graficas de composicion. Consume
// GET /calidad/indicadores (permiso calidad:leer). Look enterprise consistente
// con los dashboards de activos/tesoreria.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe, PercentPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';
import { MetricChart, MetricPoint } from '../../../shared/components/metric-chart/metric-chart';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { DashboardSection } from '../../../shared/components/dashboard-section/dashboard-section';
import { CalidadService } from '../services/calidad.service';
import { IndicadoresCalidad } from '../models/calidad.models';

@Component({
  selector: 'app-calidad-panel',
  imports: [
    DecimalPipe,
    PercentPipe,
    MatIconModule,
    PageHeader,
    StateContainer,
    MetricChart,
    KpiTile,
    DashboardSection,
  ],
  templateUrl: './panel.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../calidad.scss',
})
export class CalidadPanel {
  private readonly service = inject(CalidadService);

  protected readonly estado = signal<EstadoSolicitud<IndicadoresCalidad>>(cargando());

  constructor() {
    this.cargar();
  }

  /** Dona: composicion de las No_Conformidades por estado. */
  protected readonly composicionNc = computed<MetricPoint[]>(() => {
    const d = this.estado().datos;
    if (!d) {
      return [];
    }
    return [
      { etiqueta: 'Abiertas', valor: d.noConformidadesAbiertas },
      { etiqueta: 'En tratamiento', valor: d.noConformidadesEnTratamiento },
      { etiqueta: 'Cerradas', valor: d.noConformidadesCerradas },
    ];
  });

  /** Dona: composicion de las Quejas por estado. */
  protected readonly composicionQuejas = computed<MetricPoint[]>(() => {
    const d = this.estado().datos;
    if (!d) {
      return [];
    }
    return [
      { etiqueta: 'Registradas', valor: d.quejasRegistradas },
      { etiqueta: 'Vinculadas', valor: d.quejasVinculadas },
      { etiqueta: 'Atendidas', valor: d.quejasAtendidas },
    ];
  });

  /** Barras: acciones correctivas abiertas vs cerradas. */
  protected readonly accionesBarras = computed<MetricPoint[]>(() => {
    const d = this.estado().datos;
    if (!d) {
      return [];
    }
    return [
      { etiqueta: 'Abiertas', valor: d.accionesCorrectivasAbiertas },
      { etiqueta: 'Cerradas', valor: d.accionesCorrectivasCerradas },
    ];
  });

  /**
   * Gauge: tasa de cierre de acciones correctivas (cerradas / total) en 0-100. Es un
   * indicador de eficacia del SGC: cuanto mas alto, mas acciones resueltas.
   */
  protected readonly tasaCierre = computed<number>(() => {
    const d = this.estado().datos;
    if (!d) {
      return 0;
    }
    const total = d.accionesCorrectivasAbiertas + d.accionesCorrectivasCerradas;
    if (total <= 0) {
      return 0;
    }
    return Math.max(0, Math.min(100, Math.round((d.accionesCorrectivasCerradas / total) * 100)));
  });

  cargar(): void {
    this.estado.set(cargando());
    this.service.indicadores().subscribe({
      next: (dto) => this.estado.set(conDatos(dto)),
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }
}
