// =============================================================================
// Dialogo explicativo de un indicador (Req 22, 48)
// -----------------------------------------------------------------------------
// Al hacer clic en cualquier tarjeta del tablero (scorecard o indicator-card) se
// abre este dialogo, que muestra el valor actual y una explicacion en lenguaje de
// negocio de que significa el indicador, como se calcula y por que importa. Toda
// la informacion (texto, icono, color) se resuelve del catalogo central por la
// clave estable del indicador, de modo que es consistente en todo el sistema.
// =============================================================================

import { Component, computed, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { fichaIndicador, type FichaIndicador } from './indicadores-catalogo';

/** Datos que recibe el dialogo: el indicador sobre el que se pidio informacion. */
export interface DatosIndicadorInfo {
  clave: string;
  etiqueta: string;
  valor: number;
  unidad: string;
  comparativo?: number | null;
  variacion?: number | null;
}

@Component({
  selector: 'app-indicador-info-dialog',
  imports: [DecimalPipe, MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './indicador-info-dialog.html',
  styleUrl: './indicador-info-dialog.scss',
})
export class IndicadorInfoDialog {
  protected readonly datos = inject<DatosIndicadorInfo>(MAT_DIALOG_DATA);

  /** Ficha de presentacion (icono, tono, textos) resuelta del catalogo central. */
  protected readonly ficha = computed<FichaIndicador>(() =>
    fichaIndicador(this.datos.clave, this.datos.unidad),
  );

  /** Clase de tono para el encabezado/acento del dialogo. */
  protected readonly claseTono = computed(() => `iid--${this.ficha().tono}`);

  /** ¿El indicador es monetario? (para anteponer la unidad como prefijo). */
  protected readonly esMonetario = computed(() => {
    const u = (this.datos.unidad ?? '').toUpperCase();
    return u === 'MXN' || u === 'USD' || u === 'EUR';
  });

  /** Unidad visible (vacía si es "conteo"). */
  protected readonly unidadVisible = computed(() =>
    (this.datos.unidad ?? '').toLowerCase() === 'conteo' ? '' : this.datos.unidad,
  );

  /** Variacion porcentual respecto al periodo anterior; null si no aplica. */
  protected readonly porcentaje = computed<number | null>(() => {
    const c = this.datos.comparativo;
    if (c === null || c === undefined || c === 0) {
      return null;
    }
    const v = this.datos.variacion ?? this.datos.valor - c;
    return (v / Math.abs(c)) * 100;
  });
}
