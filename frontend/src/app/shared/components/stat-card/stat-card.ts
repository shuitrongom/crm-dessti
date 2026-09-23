// =============================================================================
// Componente StatCard (tarjeta KPI) (Req 52, 53, 57)
// -----------------------------------------------------------------------------
// Tarjeta de indicador clave (KPI) para tableros de administracion: un chip de
// icono tintado con el tono semantico, el valor destacado, la etiqueta y un
// texto de ayuda opcional. El tono mapea a los tokens semanticos del Sistema de
// Diseno y funciona en tema claro y oscuro. El significado nunca se transmite
// solo por color: siempre hay icono + valor + etiqueta (Req 57).
// =============================================================================

import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Tono semantico de la tarjeta KPI. */
export type TonoStatCard = 'neutro' | 'exito' | 'advertencia' | 'error' | 'info';

@Component({
  selector: 'app-stat-card',
  imports: [MatIconModule],
  templateUrl: './stat-card.html',
  styleUrl: './stat-card.scss',
})
export class StatCard {
  /** Etiqueta descriptiva del indicador (p. ej. "Empresas activas"). */
  readonly etiqueta = input.required<string>();
  /** Valor del indicador (numero o texto ya formateado). */
  readonly valor = input.required<string | number>();
  /** Nombre del icono de Material Symbols a mostrar en el chip. */
  readonly icono = input.required<string>();
  /** Tono semantico; determina el color del chip y del icono. */
  readonly tono = input<TonoStatCard>('neutro');
  /** Texto de ayuda opcional (pequeno, bajo la etiqueta). */
  readonly ayuda = input<string | undefined>(undefined);

  /** Clase de modificador segun el tono, para tintar el chip de icono. */
  protected readonly claseTono = computed(() => `stat-card--${this.tono()}`);

  /** Etiqueta accesible que combina etiqueta + valor para lectores de pantalla. */
  protected readonly etiquetaAccesible = computed(
    () => `${this.etiqueta()}: ${this.valor()}`,
  );
}
