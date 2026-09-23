// =============================================================================
// Componente StateContainer (Req 54)
// -----------------------------------------------------------------------------
// Envuelve un bloque de contenido y muestra de forma consistente los estados de
// carga (spinner), vacio (mensaje) y error (mensaje + reintentar), delegando en
// el contenido proyectado unicamente cuando hay datos. Se controla con la fase
// del modelo EstadoSolicitud.
// =============================================================================

import { Component, input, output } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { FaseSolicitud } from '../../models/estado-solicitud';

@Component({
  selector: 'app-state-container',
  imports: [MatProgressSpinnerModule, MatButtonModule, MatIconModule],
  templateUrl: './state-container.html',
  styleUrl: './state-container.scss',
})
export class StateContainer {
  /** Fase actual de la solicitud (cargando / ok / vacio / error). */
  readonly fase = input.required<FaseSolicitud>();
  /** Mensaje mostrado en el estado de error (Req 56). */
  readonly mensajeError = input<string | undefined>(undefined);
  /** Mensaje mostrado en el estado vacio. */
  readonly mensajeVacio = input('No hay información para mostrar.');
  /** Texto accesible del spinner de carga. */
  readonly etiquetaCarga = input('Cargando informacion');

  /** Emite cuando el Usuario solicita reintentar tras un error. */
  readonly reintentar = output<void>();
}
