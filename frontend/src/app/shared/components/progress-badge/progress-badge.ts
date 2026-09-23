// =============================================================================
// Componente ProgressBadge (Req 58) — avance + estado derivado
// -----------------------------------------------------------------------------
// Muestra el avance de un Objetivo_Estrategico (0..100) con una barra de
// progreso accesible y una insignia de estado derivado (en_riesgo / en_curso /
// cumplido) coloreada con los semanticos del Sistema de Diseno. El color nunca
// es el unico portador de significado: siempre acompana texto (Req 57).
// =============================================================================

import { Component, computed, input } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';

/** Estado derivado de un objetivo (coincide con el DTO del backend). */
export type EstadoDerivado = 'en_riesgo' | 'en_curso' | 'cumplido' | string;

@Component({
  selector: 'app-progress-badge',
  imports: [MatProgressBarModule],
  templateUrl: './progress-badge.html',
  styleUrl: './progress-badge.scss',
})
export class ProgressBadge {
  /** Porcentaje de avance en el rango [0, 100]. */
  readonly avance = input.required<number>();
  /** Estado derivado del objetivo. */
  readonly estado = input.required<EstadoDerivado>();

  /** Avance acotado al rango valido para la barra de progreso. */
  protected readonly avanceAcotado = computed(() =>
    Math.max(0, Math.min(100, Math.round(this.avance()))),
  );

  /** Etiqueta legible en espanol del estado derivado. */
  protected readonly etiquetaEstado = computed(() => {
    switch (this.estado()) {
      case 'en_riesgo':
        return 'En riesgo';
      case 'en_curso':
        return 'En curso';
      case 'cumplido':
        return 'Cumplido';
      default:
        return this.estado();
    }
  });

  /** Clase modificadora del semaforo segun el estado. */
  protected readonly claseEstado = computed(() => `progress-badge__insignia--${this.estado()}`);
}
