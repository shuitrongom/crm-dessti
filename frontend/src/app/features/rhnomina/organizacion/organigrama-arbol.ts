// =============================================================================
// Componente OrganigramaArbol (Req 61.1) — arbol recursivo accesible
// -----------------------------------------------------------------------------
// Renderiza el organigrama (bosque de nodos) como una estructura de listas
// anidadas (role tree/treeitem implicito via <ul>/<li>), reutilizandose de forma
// recursiva para cada nivel. Es de solo lectura y no modifica el sistema de
// diseno compartido.
// =============================================================================

import { Component, input } from '@angular/core';

import { OrganigramaNodo } from '../models/rhnomina.models';

@Component({
  selector: 'app-organigrama-arbol',
  template: `
    <ul class="rh-organigrama">
      @for (nodo of nodos(); track nodo.puestoId) {
        <li class="rh-organigrama__nodo">
          <span class="rh-organigrama__puesto">{{ nodo.nombre }}</span>
          @if (nodo.subordinados.length > 0) {
            <app-organigrama-arbol [nodos]="nodo.subordinados" />
          }
        </li>
      }
    </ul>
  `,
  styleUrl: '../rhnomina.scss',
})
export class OrganigramaArbol {
  /** Nodos a renderizar en este nivel del organigrama. */
  readonly nodos = input.required<OrganigramaNodo[]>();
}
