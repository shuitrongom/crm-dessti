// =============================================================================
// Componente EstadoChip (Req 57) — insignia de estado reutilizable
// -----------------------------------------------------------------------------
// Muestra la etiqueta de un estado de negocio (requisicion, orden de compra,
// factura, nomina, ticket, etc.) como una insignia coloreada por su tono
// semantico. El color nunca es el unico portador de significado: siempre va
// acompanado del texto legible del estado (Req 57). Es un componente propio del
// bloque 51.2 (features/finanzas-comun) reutilizado por todas las vistas de
// finanzas, RH y mantenimiento; no modifica el sistema de diseno compartido.
// =============================================================================

import { Component, computed, input } from '@angular/core';

/** Tono semantico aplicado a la insignia. */
export type TonoEstado = 'neutro' | 'info' | 'exito' | 'advertencia' | 'error';

@Component({
  selector: 'app-estado-chip',
  template: `<span class="estado-chip" [class]="'estado-chip--' + tono()">{{ etiqueta() }}</span>`,
  styleUrl: './estado-chip.scss',
})
export class EstadoChip {
  /** Texto legible en espanol del estado. */
  readonly etiqueta = input.required<string>();
  /** Tono semantico; por defecto neutro. */
  readonly tono = input<TonoEstado>('neutro');

  /** Clase efectiva (util para pruebas). */
  protected readonly clase = computed(() => `estado-chip--${this.tono()}`);
}
