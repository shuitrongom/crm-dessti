// =============================================================================
// Componente IndicatorCard — tarjeta KPI premium (Req 22, 48, 57)
// -----------------------------------------------------------------------------
// Tarjeta ejecutiva de un indicador, al estilo de los BI empresariales: valor
// destacado con su unidad, chip de tendencia (icono + porcentaje + color
// semantico) y una mini-barra comparativa (periodo actual vs anterior) DENTRO de
// la propia tarjeta. Concentra toda la informacion en un solo objeto, evitando
// duplicar barras y tarjetas. La tendencia nunca depende solo del color: siempre
// hay icono + texto accesible (Req 57).
// =============================================================================

import { Component, computed, inject, input, ChangeDetectionStrategy } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';

import { fichaIndicador } from '../../indicadores/indicadores-catalogo';
import {
  IndicadorInfoDialog,
  type DatosIndicadorInfo,
} from '../../indicadores/indicador-info-dialog';

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
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './indicator-card.scss',
})
export class IndicatorCard {
  private readonly dialog = inject(MatDialog);

  /** Indicador a representar. */
  readonly indicador = input.required<Indicador>();

  /** Ficha de presentacion (icono, tono, textos) del catalogo central. */
  protected readonly ficha = computed(() =>
    fichaIndicador(this.indicador().clave, this.indicador().unidad),
  );

  /** Icono representativo del indicador (del catalogo). */
  protected readonly icono = computed(() => this.ficha().icono);

  /** Clase de tono semantico para el acento de la tarjeta. */
  protected readonly claseTono = computed(() => `kpi--${this.ficha().tono}`);

  /** Subtitulo breve (del catalogo) que da contexto en la tarjeta. */
  protected readonly descripcionCorta = computed(() => this.ficha().corta);

  /**
   * Tamano de fuente del numero, escalado por longitud para que NUNCA se
   * desborde de la tarjeta (miles = grande; millones+ = mas pequeno).
   */
  protected readonly tamanoNumero = computed<string>(() => {
    const digitos = Math.round(Math.abs(this.indicador().valor)).toString().length;
    let rem: number;
    if (digitos <= 4) {
      rem = 1.6;
    } else if (digitos <= 6) {
      rem = 1.4;
    } else if (digitos <= 7) {
      rem = 1.2;
    } else if (digitos <= 9) {
      rem = 1.05;
    } else {
      rem = 0.95;
    }
    return `${rem}rem`;
  });

  /** Abre el dialogo que explica el indicador en lenguaje de negocio. */
  protected abrirInfo(): void {
    const ind = this.indicador();
    const datos: DatosIndicadorInfo = {
      clave: ind.clave,
      etiqueta: ind.etiqueta,
      valor: ind.valor,
      unidad: ind.unidad,
      comparativo: ind.comparativo,
      variacion: ind.variacion,
    };
    this.dialog.open(IndicadorInfoDialog, {
      data: datos,
      width: '32rem',
      maxWidth: '92vw',
      autoFocus: false,
    });
  }

  /** Indica si la unidad del indicador es monetaria (para el formato). */
  protected readonly esMonetario = computed(() => {
    const u = (this.indicador().unidad ?? '').toUpperCase();
    return u === 'MXN' || u === 'USD' || u === 'EUR';
  });

  /** Unidad legible que se muestra bajo el valor (vacia para conteo). */
  protected readonly unidadVisible = computed(() => {
    const u = this.indicador().unidad ?? '';
    return u.toLowerCase() === 'conteo' ? '' : u;
  });

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

  /** Variacion porcentual respecto del periodo anterior; null si no aplica. */
  protected readonly porcentaje = computed<number | null>(() => {
    const ind = this.indicador();
    if (ind.comparativo === null || ind.comparativo === undefined || ind.comparativo === 0) {
      return null;
    }
    const v = ind.variacion ?? ind.valor - ind.comparativo;
    return (v / Math.abs(ind.comparativo)) * 100;
  });

  /** Indica si hay comparativo para pintar la mini-barra. */
  protected readonly tieneComparativo = computed(
    () => this.indicador().comparativo !== null && this.indicador().comparativo !== undefined,
  );

  /** Ancho (0..100) de la barra del periodo actual respecto del maximo del par. */
  protected readonly anchoActual = computed(() => this.ancho(this.indicador().valor));

  /** Ancho (0..100) de la barra del periodo anterior respecto del maximo del par. */
  protected readonly anchoPrevio = computed(() => {
    const c = this.indicador().comparativo;
    return c === null || c === undefined ? 0 : this.ancho(c);
  });

  /** Escala un valor al maximo entre actual y comparativo (para las mini-barras). */
  private ancho(valor: number): number {
    const ind = this.indicador();
    const max = Math.max(Math.abs(ind.valor), Math.abs(ind.comparativo ?? 0), 1);
    return Math.min(100, (Math.abs(valor) / max) * 100);
  }
}
