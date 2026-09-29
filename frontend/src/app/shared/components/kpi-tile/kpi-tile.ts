// =============================================================================
// Componente KpiTile — tarjeta KPI premium reutilizable (Req 22, 48, 52, 57)
// -----------------------------------------------------------------------------
// Consolida el patron de tarjeta de indicador que estaba duplicado en muchos
// bloques (activos, tesoreria, nomina, facturacion, calidad, proyectos): chip de
// icono con gradiente, valor destacado con unidad opcional, sublabel, un delta de
// tendencia opcional (icono + % + color semantico) y una sparkline opcional
// (mini-tendencia). El significado nunca depende solo del color: siempre hay
// icono + valor + etiqueta (Req 57). Usa tokens del Sistema de Diseno.
// =============================================================================

import { Component, computed, input, ChangeDetectionStrategy } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MetricChart, type MetricPoint } from '../metric-chart/metric-chart';

/** Tono semantico del acento de la tarjeta. */
export type TonoKpi = 'primario' | 'exito' | 'advertencia' | 'error' | 'info' | 'neutro';

/** Direccion de la tendencia del delta. */
export type TendenciaKpi = 'subida' | 'bajada' | 'neutra';

@Component({
  selector: 'app-kpi-tile',
  imports: [DecimalPipe, MatIconModule, MetricChart],
  templateUrl: './kpi-tile.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './kpi-tile.scss',
})
export class KpiTile {
  /** Etiqueta principal del indicador (p. ej. "Valor en pipeline"). */
  readonly etiqueta = input.required<string>();
  /**
   * Valor mostrado (numero o texto ya formateado). Acepta null/undefined para
   * poder recibir directamente el resultado de pipes como currency/number/percent
   * (que devuelven string | null); en ese caso se pinta un guion.
   */
  readonly valor = input.required<string | number | null | undefined>();
  /** Icono de Material Symbols en el chip. */
  readonly icono = input<string>('insights');
  /** Tono semantico del acento (color del chip, delta, borde). */
  readonly tono = input<TonoKpi>('primario');
  /** Prefijo del valor (p. ej. "MXN"); se muestra antes del numero. */
  readonly prefijo = input<string>('');
  /** Sufijo/unidad del valor (p. ej. "%", "dias"); se muestra despues del numero. */
  readonly sufijo = input<string>('');
  /** Texto de ayuda pequeno bajo la etiqueta. */
  readonly sublabel = input<string>('');
  /** Porcentaje de variacion respecto al periodo anterior; null oculta el delta. */
  readonly deltaPorcentaje = input<number | null>(null);
  /**
   * Semantica del delta: si un aumento es "bueno" (verde) o "malo" (rojo). Por
   * defecto true. Solo afecta el color, no el signo mostrado.
   */
  readonly aumentoEsBueno = input<boolean>(true);
  /** Datos opcionales de sparkline (mini-tendencia dentro de la tarjeta). */
  readonly sparkline = input<MetricPoint[] | null>(null);

  /** Clase de acento segun el tono. */
  protected readonly claseTono = computed(() => `kpi-tile--${this.tono()}`);

  /** Direccion del delta a partir del porcentaje. */
  protected readonly tendencia = computed<TendenciaKpi>(() => {
    const p = this.deltaPorcentaje();
    if (p === null || p === undefined || p === 0) {
      return 'neutra';
    }
    return p > 0 ? 'subida' : 'bajada';
  });

  /** Icono del delta segun la tendencia. */
  protected readonly iconoDelta = computed(() => {
    switch (this.tendencia()) {
      case 'subida':
        return 'trending_up';
      case 'bajada':
        return 'trending_down';
      default:
        return 'trending_flat';
    }
  });

  /**
   * Color semantico del delta: verde/rojo segun si la direccion coincide con lo
   * "bueno". Un aumento cuando aumentoEsBueno=true es 'bueno'; si no, 'malo'.
   */
  protected readonly tonoDelta = computed<'bueno' | 'malo' | 'neutro'>(() => {
    const t = this.tendencia();
    if (t === 'neutra') {
      return 'neutro';
    }
    const sube = t === 'subida';
    return sube === this.aumentoEsBueno() ? 'bueno' : 'malo';
  });

  /** Indica si hay sparkline con datos para pintar. */
  protected readonly haySparkline = computed(() => {
    const s = this.sparkline();
    return !!s && s.length > 1;
  });
}
