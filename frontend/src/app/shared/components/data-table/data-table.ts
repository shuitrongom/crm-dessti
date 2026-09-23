// =============================================================================
// Componente DataTable (Req 12, 52) — tabla paginada reutilizable
// -----------------------------------------------------------------------------
// Envoltura sobre MatTable + MatPaginator para listados paginados del servidor
// (paginacion controlada por el contrato PaginaResponse: page/size/totalElements).
// Recibe columnas declarativas y celdas por plantilla proyectada; emite el
// cambio de pagina para que la vista recargue los datos desde la API.
//
// Uso:
//   <app-data-table [columnas]="cols" [filas]="items()" [total]="total()"
//       [page]="page()" [size]="size()" (cambioPagina)="onPagina($event)">
//     <ng-template appCelda="nombre" let-fila>{{ fila.nombre }}</ng-template>
//   </app-data-table>
// =============================================================================

import {
  AfterContentInit,
  Component,
  ContentChildren,
  Directive,
  Input,
  QueryList,
  TemplateRef,
  input,
  output,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';

import { PaginatorIntlEs } from './paginator-intl-es';

/** Definicion declarativa de una columna de la tabla. */
export interface ColumnaTabla {
  /** Clave estable de la columna (coincide con el nombre de la plantilla de celda). */
  clave: string;
  /** Encabezado visible en espanol. */
  encabezado: string;
  /** Alineacion del contenido; por defecto a la izquierda. */
  alineacion?: 'inicio' | 'centro' | 'fin';
  /**
   * Si es `true`, la columna (encabezado + celdas) se OCULTA en pantallas
   * estrechas (por debajo del breakpoint `md`), reservando el espacio disponible
   * para las columnas esenciales y evitando el desplazamiento horizontal. Es una
   * mejora opcional y no disruptiva: por omision (`false`/ausente) la columna se
   * muestra en todos los tamanos, conservando el comportamiento actual (Req 52).
   */
  ocultarEnMovil?: boolean;
}

/** Evento de cambio de pagina (0-index) y tamano. */
export interface CambioPagina {
  page: number;
  size: number;
}

/**
 * Directiva estructural para declarar la plantilla de celda de una columna.
 * El nombre pasado (`appCelda="clave"`) enlaza con `ColumnaTabla.clave`.
 */
@Directive({ selector: '[appCelda]' })
export class CeldaTablaDirective {
  /** Clave de la columna a la que aplica esta plantilla de celda. */
  @Input('appCelda') clave = '';

  constructor(readonly plantilla: TemplateRef<{ $implicit: unknown }>) {}
}

@Component({
  selector: 'app-data-table',
  imports: [MatTableModule, MatPaginatorModule, NgTemplateOutlet],
  templateUrl: './data-table.html',
  styleUrl: './data-table.scss',
  // Traduce el paginador al espanol de forma acotada: aplica a toda tabla que
  // use este componente sin repetir el provider en cada vista (Req 52, 57).
  providers: [{ provide: MatPaginatorIntl, useClass: PaginatorIntlEs }],
})
export class DataTable<T> implements AfterContentInit {
  /** Definicion de columnas a renderizar, en orden. */
  readonly columnas = input.required<ColumnaTabla[]>();
  /** Filas de la pagina actual. */
  readonly filas = input.required<readonly T[]>();
  /** Total de elementos en todas las paginas (PaginaResponse.totalElements). */
  readonly total = input(0);
  /** Numero de pagina actual (0-index). */
  readonly page = input(0);
  /** Tamano de pagina actual. */
  readonly size = input(20);
  /** Opciones de tamano de pagina ofrecidas al Usuario. */
  readonly opcionesTamano = input<number[]>([10, 20, 50, 100]);

  /** Emite cuando cambia la pagina o el tamano; la vista debe recargar datos. */
  readonly cambioPagina = output<CambioPagina>();

  /** Plantillas de celda proyectadas, indexadas por clave de columna. */
  @ContentChildren(CeldaTablaDirective) private plantillas!: QueryList<CeldaTablaDirective>;
  private mapaPlantillas = new Map<string, TemplateRef<{ $implicit: unknown }>>();

  /** Claves de columna en orden, derivadas de la definicion. */
  protected get claves(): string[] {
    return this.columnas().map((c) => c.clave);
  }

  ngAfterContentInit(): void {
    for (const c of this.plantillas) {
      this.mapaPlantillas.set(c.clave, c.plantilla);
    }
  }

  /** Devuelve la plantilla de celda para una columna, si se proporciono. */
  protected plantillaDe(clave: string): TemplateRef<{ $implicit: unknown }> | null {
    return this.mapaPlantillas.get(clave) ?? null;
  }

  /** Traduce el evento de MatPaginator al contrato de salida. */
  protected onPage(evento: PageEvent): void {
    this.cambioPagina.emit({ page: evento.pageIndex, size: evento.pageSize });
  }
}
