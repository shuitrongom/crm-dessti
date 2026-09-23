// =============================================================================
// Componente ChipEstado (Req 7.3, 7.4) — insignia de estado semántica
// -----------------------------------------------------------------------------
// Renderiza una insignia compacta ("chip") que comunica un estado mediante una
// variante semántica {éxito, advertencia, error, info, neutro}. Cada variante
// se mapea a su Token_CSS semántico (`--ds-color-success/warning/error/info` o
// neutro) a través del atributo `data-variante` que consume el SCSS.
//
// El texto (`etiqueta`) es SIEMPRE el portador de significado: el color es solo
// un refuerzo visual, nunca el único canal de información (WCAG 2.1, uso del
// color). Los tokens cambian por `data-theme`, de modo que el chip se ve bien
// en claro y oscuro sin lógica adicional.
// =============================================================================

import { Component, input } from '@angular/core';

/** Variantes semánticas admitidas por el chip de estado. */
export type VarianteChipEstado = 'exito' | 'advertencia' | 'error' | 'info' | 'neutro';

@Component({
  selector: 'app-chip-estado',
  template: `
    <span class="chip-estado" [attr.data-variante]="variante()">{{ etiqueta() }}</span>
  `,
  styleUrl: './chip-estado.scss',
})
export class ChipEstado {
  /** Variante semántica que determina el color del chip. */
  readonly variante = input.required<VarianteChipEstado>();
  /** Texto visible del chip; portador principal del significado (accesibilidad). */
  readonly etiqueta = input.required<string>();
}
