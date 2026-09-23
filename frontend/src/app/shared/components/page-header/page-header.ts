// =============================================================================
// Componente PageHeader (Req 52, 53)
// -----------------------------------------------------------------------------
// Encabezado de pagina reutilizable: titulo (h1 accesible como landmark de la
// vista), subtitulo opcional y una zona de acciones proyectada a la derecha.
// =============================================================================

import { Component, input } from '@angular/core';

@Component({
  selector: 'app-page-header',
  template: `
    <header class="page-header">
      <div class="page-header__textos">
        <h1 class="page-header__titulo">{{ titulo() }}</h1>
        @if (subtitulo()) {
          <p class="page-header__subtitulo">{{ subtitulo() }}</p>
        }
      </div>
      <div class="page-header__acciones">
        <ng-content select="[acciones]" />
      </div>
    </header>
  `,
  styleUrl: './page-header.scss',
})
export class PageHeader {
  /** Titulo principal de la vista. */
  readonly titulo = input.required<string>();
  /** Subtitulo/descripcion opcional. */
  readonly subtitulo = input<string | undefined>(undefined);
}
