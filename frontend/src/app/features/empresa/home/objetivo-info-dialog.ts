// =============================================================================
// Dialogo informativo de un Objetivo estrategico (Req 58)
// -----------------------------------------------------------------------------
// Al hacer clic en un objetivo del home se abre este dialogo, que explica en
// lenguaje llano que es el objetivo, quien es su responsable, su meta, el periodo
// y como va su avance. Ayuda al administrador a entender su planeacion sin tener
// que abrir la vista completa de estrategia. Solo-lectura.
// =============================================================================

import { Component, computed, inject, ChangeDetectionStrategy } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** Datos del objetivo que recibe el dialogo. */
export interface DatosObjetivoInfo {
  nombre: string;
  responsable: string;
  meta: string;
  avance: number;
  estadoDerivado: string;
  periodoInicio: string;
  periodoFin: string;
}

@Component({
  selector: 'app-objetivo-info-dialog',
  imports: [DecimalPipe, MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './objetivo-info-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './objetivo-info-dialog.scss',
})
export class ObjetivoInfoDialog {
  protected readonly datos = inject<DatosObjetivoInfo>(MAT_DIALOG_DATA);

  /** Tono semantico segun el estado derivado del objetivo. */
  protected readonly tono = computed<'exito' | 'advertencia' | 'error' | 'info'>(() => {
    switch (this.datos.estadoDerivado) {
      case 'cumplido':
        return 'exito';
      case 'en_curso':
        return 'info';
      case 'en_riesgo':
        return 'error';
      default:
        return 'advertencia';
    }
  });

  /** Clase de tono para el acento del dialogo. */
  protected readonly claseTono = computed(() => `obj--${this.tono()}`);

  /** Etiqueta legible del estado derivado. */
  protected readonly estadoTexto = computed(() => {
    switch (this.datos.estadoDerivado) {
      case 'cumplido':
        return 'Cumplido';
      case 'en_curso':
        return 'En curso';
      case 'en_riesgo':
        return 'En riesgo';
      default:
        return this.datos.estadoDerivado;
    }
  });

  /** Explicacion del significado del estado, en lenguaje de negocio. */
  protected readonly estadoExplicacion = computed(() => {
    switch (this.datos.estadoDerivado) {
      case 'cumplido':
        return 'Este objetivo ya alcanzó su meta. ¡Buen trabajo!';
      case 'en_curso':
        return 'El objetivo avanza a buen ritmo hacia su meta.';
      case 'en_riesgo':
        return 'El avance está por debajo de lo esperado. Conviene darle seguimiento.';
      default:
        return 'Estado del objetivo dentro del periodo definido.';
    }
  });
}
