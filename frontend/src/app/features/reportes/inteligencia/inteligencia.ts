// =============================================================================
// Vista de Inteligencia de Negocio consolidada (Req 48)
// -----------------------------------------------------------------------------
// Dashboard ejecutivo: un copiloto de IA arriba (resumen + hallazgos) y, debajo,
// los indicadores por area como tarjetas KPI premium con comparativo del periodo
// anterior integrado. Filtros por periodo, area y dimension. Exportacion si hay
// permiso. Los insights de IA se cargan en paralelo y su fallo no bloquea la vista.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { IndicatorCard } from '../../../shared/components/indicator-card/indicator-card';
import { MetricChart, type MetricPoint } from '../../../shared/components/metric-chart/metric-chart';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { Scorecard } from '../scorecard/scorecard';
import { InteligenciaNegocioService } from '../services/inteligencia-negocio.service';
import { IndicadoresArea, InsightsBi, InteligenciaNegocio } from '../models/reportes.models';
import { humanizarArea } from '../areas-etiquetas';

@Component({
  selector: 'app-inteligencia',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    PageHeader,
    StateContainer,
    IndicatorCard,
    MetricChart,
    Scorecard,
  ],
  templateUrl: './inteligencia.html',
  styleUrl: './inteligencia.scss',
})
export class Inteligencia {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(InteligenciaNegocioService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly humanizarArea = humanizarArea;
  protected readonly puedeExportar = this.auth.tienePermiso('inteligencia_negocio', 'exportar');

  protected readonly estado = signal<EstadoSolicitud<InteligenciaNegocio>>(cargando());
  protected readonly exportando = signal(false);

  /** Insights de IA: se cargan en paralelo y no bloquean el consolidado. */
  protected readonly insights = signal<InsightsBi | null>(null);
  protected readonly insightsCargando = signal(false);
  protected readonly insightsError = signal<string | null>(null);

  protected readonly formFiltro = this.fb.nonNullable.group({
    desde: [''],
    hasta: [''],
    area: [''],
    dimension: [''],
  });

  constructor() {
    this.consultar();
  }

  /** Puntos para la grafica de un area (etiqueta, valor actual y comparativo). */
  puntos(grupo: IndicadoresArea): MetricPoint[] {
    return grupo.indicadores.map((i) => ({
      etiqueta: i.etiqueta,
      valor: i.valor,
      comparativo: i.comparativo,
      unidad: i.unidad,
    }));
  }

  /**
   * Elige la visualizacion del area: dona cuando son varios conteos comparables
   * (distribucion) y no hay comparativo; barras en el resto (mejor para comparar
   * actual vs anterior o magnitudes monetarias).
   */
  tipoGrafica(grupo: IndicadoresArea): 'dona' | 'barras' {
    const hayComparativo = grupo.indicadores.some((i) => i.comparativo != null);
    const todosConteo = grupo.indicadores.every((i) => (i.unidad ?? '').toLowerCase() === 'conteo');
    if (!hayComparativo && todosConteo && grupo.indicadores.length >= 3) {
      return 'dona';
    }
    return 'barras';
  }

  /** Indicador principal (KPI destacado) de un area: el de mayor valor absoluto. */
  indicadorPrincipal(grupo: IndicadoresArea) {
    return grupo.indicadores.reduce(
      (mejor, actual) => (Math.abs(actual.valor) > Math.abs(mejor.valor) ? actual : mejor),
      grupo.indicadores[0],
    );
  }

  /**
   * Porcentaje 0..100 del gauge del area: si el indicador principal tiene
   * comparativo, es su cumplimiento (valor/comparativo); si no, su peso relativo
   * frente a la suma del area. Acotado a [0, 100].
   */
  porcentajeGauge(grupo: IndicadoresArea): number {
    const principal = this.indicadorPrincipal(grupo);
    if (!principal) {
      return 0;
    }
    if (principal.comparativo && principal.comparativo !== 0) {
      const pct = (principal.valor / Math.abs(principal.comparativo)) * 100;
      return Math.max(0, Math.min(100, Math.round(pct)));
    }
    const total = grupo.indicadores.reduce((a, i) => a + Math.abs(i.valor), 0);
    if (total === 0) {
      return 0;
    }
    return Math.max(0, Math.min(100, Math.round((Math.abs(principal.valor) / total) * 100)));
  }

  /** Puntos del gauge: solo el indicador principal (etiqueta para el centro). */
  puntoGauge(grupo: IndicadoresArea): MetricPoint[] {
    const principal = this.indicadorPrincipal(grupo);
    return principal
      ? [{ etiqueta: principal.etiqueta, valor: principal.valor, unidad: principal.unidad }]
      : [];
  }

  /** Tono semantico de un hallazgo, derivado de su prefijo ("Al alza", etc.). */
  tonoHallazgo(hallazgo: string): 'alza' | 'baja' | 'alerta' | 'neutro' {
    const h = hallazgo.toLowerCase();
    if (h.startsWith('alerta:')) {
      return 'alerta';
    }
    if (h.startsWith('al alza:')) {
      return 'alza';
    }
    if (h.startsWith('a la baja:')) {
      return 'baja';
    }
    return 'neutro';
  }

  /** Icono de Material Symbols acorde al tono del hallazgo (acompana al color). */
  iconoHallazgo(hallazgo: string): string {
    switch (this.tonoHallazgo(hallazgo)) {
      case 'alza':
        return 'trending_up';
      case 'baja':
        return 'trending_down';
      case 'alerta':
        return 'warning';
      default:
        return 'insights';
    }
  }

  private filtroActual() {
    const v = this.formFiltro.getRawValue();
    return {
      desde: v.desde || null,
      hasta: v.hasta || null,
      area: v.area || null,
      dimension: v.dimension || null,
    };
  }

  consultar(): void {
    this.estado.set(cargando());
    this.service.consolidado(this.filtroActual()).subscribe({
      next: (datos) => this.estado.set(conDatos(datos, (datos.areas?.length ?? 0) === 0)),
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
    this.consultarInsights();
  }

  /**
   * Solicita los insights de IA del periodo. Se ejecuta en paralelo al consolidado
   * y su fallo no rompe la vista: se muestra un mensaje discreto y el consolidado
   * (tarjetas KPI) sigue disponible.
   */
  consultarInsights(): void {
    this.insightsCargando.set(true);
    this.insightsError.set(null);
    this.service.insights(this.filtroActual()).subscribe({
      next: (datos) => {
        this.insights.set(datos);
        this.insightsCargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.insights.set(null);
        this.insightsError.set(mensajeDeError(e));
        this.insightsCargando.set(false);
      },
    });
  }

  exportar(): void {
    this.exportando.set(true);
    this.service.exportarConsolidado(this.filtroActual()).subscribe({
      next: (datos) => {
        this.exportando.set(false);
        const blob = new Blob([JSON.stringify(datos, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = 'inteligencia-negocio.json';
        enlace.click();
        URL.revokeObjectURL(url);
        this.toast.exito('Análisis exportado.');
      },
      error: (e: HttpErrorResponse) => {
        this.exportando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
