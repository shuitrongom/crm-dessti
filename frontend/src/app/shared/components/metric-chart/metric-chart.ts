// =============================================================================
// Componente MetricChart — grafica premium con ApexCharts (Req 22, 48)
// -----------------------------------------------------------------------------
// Envoltura standalone de ng-apexcharts (ApexCharts) para visualizar los
// indicadores de un area de negocio. Segun el tipo de dato elige la mejor
// visualizacion:
//   - "dona": distribucion de los indicadores de conteo del area.
//   - "barras": comparativo actual vs periodo anterior por indicador.
//
// Se integra con el tema (claro/oscuro) leyendo el color de acento del CSS y usa
// formato es-MX. Es puramente de presentacion: recibe datos ya calculados.
// =============================================================================

import { Component, computed, input, ChangeDetectionStrategy } from '@angular/core';
import { NgApexchartsModule, type ApexOptions } from 'ng-apexcharts';

/** Punto de datos para las graficas: etiqueta, valor actual y comparativo. */
export interface MetricPoint {
  etiqueta: string;
  valor: number;
  comparativo?: number | null;
  unidad?: string;
}

/** Tipo de visualizacion a renderizar. */
export type MetricChartTipo = 'dona' | 'barras' | 'columnas' | 'area' | 'gauge' | 'sparkline';

@Component({
  selector: 'app-metric-chart',
  imports: [NgApexchartsModule],
  templateUrl: './metric-chart.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './metric-chart.scss',
})
export class MetricChart {
  /** Datos a graficar. */
  readonly datos = input.required<MetricPoint[]>();
  /** Tipo de grafica. */
  readonly tipo = input<MetricChartTipo>('barras');
  /** Titulo accesible/mostrado de la grafica. */
  readonly titulo = input<string>('');
  /** Nombre de la serie principal (valor). Por defecto "Periodo actual". */
  readonly nombreSerie = input<string>('Periodo actual');
  /** Nombre de la serie comparativa. Por defecto "Periodo anterior". */
  readonly nombreComparativo = input<string>('Periodo anterior');

  /** Paleta corporativa (se resuelve del acento del tema con degradados). */
  private readonly paleta = [
    '#6750a4',
    '#22d3ee',
    '#12805c',
    '#f59e0b',
    '#ef4444',
    '#8b5cf6',
    '#0ea5e9',
    '#e11d48',
  ];

  /**
   * Porcentaje 0..100 para el gauge (tipo 'gauge'). Si es null, el gauge deriva el
   * porcentaje del primer punto respecto al total de los puntos.
   */
  readonly porcentaje = input<number | null>(null);

  /** Opciones de ApexCharts calculadas segun el tipo y los datos. */
  protected readonly opciones = computed<ApexOptions>(() => {
    const puntos = this.datos();
    switch (this.tipo()) {
      case 'dona':
        return this.opcionesDona(puntos);
      case 'columnas':
        return this.opcionesColumnas(puntos);
      case 'area':
        return this.opcionesArea(puntos);
      case 'gauge':
        return this.opcionesGauge(puntos);
      case 'sparkline':
        return this.opcionesSparkline(puntos);
      default:
        return this.opcionesBarras(puntos);
    }
  });

  /** Indica si hay datos suficientes para dibujar. */
  protected readonly hayDatos = computed(() => {
    // El gauge puede pintarse con un porcentaje explicito aunque los puntos sean 0.
    if (this.tipo() === 'gauge' && this.porcentaje() != null) {
      return true;
    }
    return this.datos().some((d) => Math.abs(d.valor) > 0);
  });

  // ------------------------------------------------------------------
  // Configuraciones
  // ------------------------------------------------------------------

  private opcionesBarras(puntos: MetricPoint[]): ApexOptions {
    const tieneComparativo = puntos.some((p) => p.comparativo != null);
    const series = [{ name: 'Periodo actual', data: puntos.map((p) => this.redondear(p.valor)) }];
    if (tieneComparativo) {
      series.push({
        name: 'Periodo anterior',
        data: puntos.map((p) => this.redondear(p.comparativo ?? 0)),
      });
    }
    return {
      series,
      chart: {
        type: 'bar',
        height: Math.max(180, puntos.length * (tieneComparativo ? 46 : 34) + 40),
        toolbar: { show: false },
        fontFamily: 'inherit',
        animations: { enabled: true, speed: 500 },
      },
      colors: [this.paleta[0], '#b8b0d6'],
      plotOptions: {
        bar: {
          horizontal: true,
          borderRadius: 6,
          borderRadiusApplication: 'end',
          barHeight: tieneComparativo ? '80%' : '55%',
          dataLabels: { position: 'top' },
        },
      },
      dataLabels: {
        enabled: true,
        offsetX: 28,
        style: { fontSize: '11px', colors: ['#6b7280'], fontWeight: 600 },
        formatter: (v: number) => this.formatoCorto(v),
      },
      grid: { borderColor: 'rgba(120,120,120,0.15)', xaxis: { lines: { show: true } } },
      xaxis: {
        categories: puntos.map((p) => p.etiqueta),
        labels: { formatter: (v: string) => this.formatoCorto(Number(v)) },
      },
      yaxis: { labels: { style: { fontSize: '12px' } } },
      legend: { show: tieneComparativo, position: 'top', horizontalAlign: 'right' },
      tooltip: { y: { formatter: (v: number) => this.formatoLargo(v) } },
    };
  }

  /**
   * Barras VERTICALES agrupadas de dos series (valor y comparativo) por categoria.
   * Ideal para series temporales (p. ej. flujo de caja mensual: entradas vs salidas).
   */
  private opcionesColumnas(puntos: MetricPoint[]): ApexOptions {
    const tieneComparativo = puntos.some((p) => p.comparativo != null);
    const series = [{ name: this.nombreSerie(), data: puntos.map((p) => this.redondear(p.valor)) }];
    if (tieneComparativo) {
      series.push({
        name: this.nombreComparativo(),
        data: puntos.map((p) => this.redondear(p.comparativo ?? 0)),
      });
    }
    return {
      series,
      chart: {
        type: 'bar',
        height: 300,
        toolbar: { show: false },
        fontFamily: 'inherit',
        animations: { enabled: true, speed: 500 },
      },
      colors: [this.paleta[2], this.paleta[4]],
      plotOptions: {
        bar: {
          horizontal: false,
          borderRadius: 5,
          borderRadiusApplication: 'end',
          columnWidth: '62%',
        },
      },
      dataLabels: { enabled: false },
      stroke: { show: true, width: 2, colors: ['transparent'] },
      grid: { borderColor: 'rgba(120,120,120,0.15)' },
      xaxis: {
        categories: puntos.map((p) => p.etiqueta),
        labels: { style: { fontSize: '11px' } },
      },
      yaxis: {
        labels: { formatter: (v: number) => this.formatoCorto(v), style: { fontSize: '11px' } },
      },
      legend: { show: tieneComparativo, position: 'top', horizontalAlign: 'right' },
      tooltip: { y: { formatter: (v: number) => this.formatoLargo(v) } },
    };
  }

  private opcionesDona(puntos: MetricPoint[]): ApexOptions {
    const positivos = puntos.filter((p) => Math.abs(p.valor) > 0);
    const fuente = positivos.length > 0 ? positivos : puntos;
    return {
      series: fuente.map((p) => this.redondear(Math.abs(p.valor))),
      labels: fuente.map((p) => p.etiqueta),
      chart: {
        type: 'donut',
        height: 300,
        fontFamily: 'inherit',
        animations: { enabled: true, speed: 500 },
      },
      colors: this.paleta,
      stroke: { width: 2, colors: ['var(--mat-sys-surface, #fff)'] },
      dataLabels: {
        enabled: true,
        formatter: (val: number) => `${Math.round(val)}%`,
        style: { fontSize: '11px', fontWeight: 700 },
      },
      plotOptions: {
        pie: {
          donut: {
            size: '62%',
            labels: {
              show: true,
              total: {
                show: true,
                label: 'Total',
                fontSize: '13px',
                formatter: (w: { globals: { seriesTotals: number[] } }) =>
                  this.formatoCorto(w.globals.seriesTotals.reduce((a, b) => a + b, 0)),
              },
            },
          },
        },
      },
      legend: { position: 'bottom', fontSize: '12px' },
      tooltip: { y: { formatter: (v: number) => this.formatoLargo(v) } },
    };
  }

  /**
   * Grafica de AREA con degradado (tendencia temporal, estilo cockpit BI). Usa el
   * valor de cada punto como serie y la etiqueta como categoria del eje X.
   */
  private opcionesArea(puntos: MetricPoint[]): ApexOptions {
    const tieneComparativo = puntos.some((p) => p.comparativo != null);
    const series = [{ name: this.nombreSerie(), data: puntos.map((p) => this.redondear(p.valor)) }];
    if (tieneComparativo) {
      series.push({
        name: this.nombreComparativo(),
        data: puntos.map((p) => this.redondear(p.comparativo ?? 0)),
      });
    }
    return {
      series,
      chart: {
        type: 'area',
        height: 300,
        toolbar: { show: false },
        fontFamily: 'inherit',
        animations: { enabled: true, speed: 700, easing: 'easeinout' },
        sparkline: { enabled: false },
      },
      colors: [this.paleta[0], this.paleta[1]],
      stroke: { curve: 'smooth', width: 3 },
      fill: {
        type: 'gradient',
        gradient: {
          shadeIntensity: 1,
          opacityFrom: 0.55,
          opacityTo: 0.05,
          stops: [0, 90, 100],
        },
      },
      dataLabels: { enabled: false },
      grid: { borderColor: 'rgba(120,120,120,0.15)', strokeDashArray: 4 },
      markers: { size: 0, hover: { size: 5 } },
      xaxis: {
        categories: puntos.map((p) => p.etiqueta),
        labels: { style: { fontSize: '11px' } },
        axisBorder: { show: false },
        axisTicks: { show: false },
      },
      yaxis: {
        labels: { formatter: (v: number) => this.formatoCorto(v), style: { fontSize: '11px' } },
      },
      legend: { show: tieneComparativo, position: 'top', horizontalAlign: 'right' },
      tooltip: { y: { formatter: (v: number) => this.formatoLargo(v) } },
    };
  }

  /**
   * GAUGE radial (radialBar) para un KPI principal: muestra un porcentaje de
   * cumplimiento con un anillo semicircular y el valor en el centro. El porcentaje
   * se toma del input `porcentaje()` o se deriva del primer punto sobre el total.
   */
  private opcionesGauge(puntos: MetricPoint[]): ApexOptions {
    const pct = this.porcentajeGauge(puntos);
    const etiqueta = puntos.length > 0 ? puntos[0].etiqueta : '';
    return {
      series: [pct],
      labels: [etiqueta],
      chart: {
        type: 'radialBar',
        height: 300,
        fontFamily: 'inherit',
        animations: { enabled: true, speed: 900, easing: 'easeinout' },
      },
      colors: [this.paleta[0]],
      fill: {
        type: 'gradient',
        gradient: {
          shade: 'dark',
          type: 'horizontal',
          gradientToColors: [this.paleta[6]],
          stops: [0, 100],
        },
      },
      stroke: { lineCap: 'round' },
      plotOptions: {
        radialBar: {
          startAngle: -135,
          endAngle: 135,
          hollow: { size: '62%' },
          track: {
            background: 'rgba(120,120,120,0.15)',
            strokeWidth: '100%',
          },
          dataLabels: {
            name: { fontSize: '13px', offsetY: 22 },
            value: {
              fontSize: '2rem',
              fontWeight: 800,
              offsetY: -12,
              formatter: (v: number) => `${Math.round(v)}%`,
            },
          },
        },
      },
    };
  }

  /**
   * SPARKLINE: mini-linea de tendencia sin ejes ni cuadricula, para incrustar en
   * tarjetas KPI. Usa solo el valor de cada punto.
   */
  private opcionesSparkline(puntos: MetricPoint[]): ApexOptions {
    return {
      series: [{ name: this.nombreSerie(), data: puntos.map((p) => this.redondear(p.valor)) }],
      chart: {
        type: 'area',
        height: 60,
        sparkline: { enabled: true },
        animations: { enabled: true, speed: 600 },
      },
      colors: [this.paleta[0]],
      stroke: { curve: 'smooth', width: 2 },
      fill: {
        type: 'gradient',
        gradient: { opacityFrom: 0.4, opacityTo: 0.02, stops: [0, 100] },
      },
      tooltip: {
        y: { formatter: (v: number) => this.formatoLargo(v) },
        marker: { show: false },
      },
    };
  }

  /** Deriva el porcentaje del gauge (input explicito o primer punto sobre total). */
  private porcentajeGauge(puntos: MetricPoint[]): number {
    const explicito = this.porcentaje();
    if (explicito != null) {
      return Math.max(0, Math.min(100, this.redondear(explicito)));
    }
    if (puntos.length === 0) {
      return 0;
    }
    const total = puntos.reduce((a, p) => a + Math.abs(p.valor), 0);
    if (total === 0) {
      return 0;
    }
    return Math.max(0, Math.min(100, this.redondear((Math.abs(puntos[0].valor) / total) * 100)));
  }

  // ------------------------------------------------------------------
  // Utilidades de formato
  // ------------------------------------------------------------------

  private redondear(n: number): number {
    return Math.round((n + Number.EPSILON) * 100) / 100;
  }

  /** Formato compacto para ejes/labels (1.2K, 3.4M). */
  private formatoCorto(n: number): string {
    return new Intl.NumberFormat('es-MX', {
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(n);
  }

  /** Formato completo es-MX para tooltips. */
  private formatoLargo(n: number): string {
    return new Intl.NumberFormat('es-MX', { maximumFractionDigits: 2 }).format(n);
  }
}
