// =============================================================================
// Componente IndicatorCard (Req 22, 48)
// -----------------------------------------------------------------------------
// Tarjeta de un indicador del Tablero: clave/etiqueta, valor con su unidad y,
// cuando existe, el comparativo del periodo anterior con su variacion y
// tendencia (subida / bajada / sin cambio). La tendencia se comunica con icono
// + texto, no solo por color (Req 57).
// =============================================================================

import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

/** Indicador individual del Tablero (coincide con IndicadorDto del backend). */
export interface Indicador {
  clave: string;
  etiqueta: string;
  valor: number;
  unidad: string;
  comparativo: number | null;
  variacion: number | null;
}

@Component({
  selector: 'app-indicator-card',
  imports: [DecimalPipe, MatIconModule],
  templateUrl: './indicator-card.html',
  styleUrl: './indicator-card.scss',
})
export class IndicatorCard {
  /** Indicador a representar. */
  readonly indicador = input.required<Indicador>();

  /** Tendencia derivada de la variacion (subida / bajada / neutra / ninguna). */
  protected readonly tendencia = computed<'subida' | 'bajada' | 'neutra' | 'ninguna'>(() => {
    const v = this.indicador().variacion;
    if (v === null || v === undefined) {
      return 'ninguna';
    }
    if (v > 0) {
      return 'subida';
    }
    if (v < 0) {
      return 'bajada';
    }
    return 'neutra';
  });

  /** Icono de tendencia de Material Symbols. */
  protected readonly iconoTendencia = computed(() => {
    switch (this.tendencia()) {
      case 'subida':
        return 'trending_up';
      case 'bajada':
        return 'trending_down';
      default:
        return 'trending_flat';
    }
  });

  /** Texto accesible de la tendencia. */
  protected readonly textoTendencia = computed(() => {
    switch (this.tendencia()) {
      case 'subida':
        return 'al alza';
      case 'bajada':
        return 'a la baja';
      case 'neutra':
        return 'sin cambio';
      default:
        return '';
    }
  });
}
