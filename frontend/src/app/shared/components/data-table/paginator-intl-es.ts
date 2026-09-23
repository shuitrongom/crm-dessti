// =============================================================================
// MatPaginatorIntl en espanol (Req 52, 57)
// -----------------------------------------------------------------------------
// Angular Material trae las etiquetas del paginador en ingles ("Items per page",
// "1 - 1 of 1"). Esta clase las traduce al espanol para que las tablas de la
// plataforma sean coherentes con el idioma de la aplicacion (es-MX).
//
// Se provee de forma ACOTADA en los `providers` del componente DataTable
// (unico envoltorio de MatPaginator del proyecto): asi la traduccion aplica
// automaticamente a toda tabla que use DataTable, sin repetir el provider en
// cada vista ni depender de un registro global, manteniendo la responsabilidad
// junto al componente que posee el paginador.
// =============================================================================

import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

/** Etiquetas del paginador de Angular Material traducidas al espanol. */
@Injectable()
export class PaginatorIntlEs extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Elementos por pagina:';
  override nextPageLabel = 'Pagina siguiente';
  override previousPageLabel = 'Pagina anterior';
  override firstPageLabel = 'Primera pagina';
  override lastPageLabel = 'Ultima pagina';

  /** Construye la etiqueta de rango "X - Y de Z" (o "0 de Z" sin resultados). */
  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0 || pageSize === 0) {
      return `0 de ${length}`;
    }
    const total = Math.max(length, 0);
    const inicio = page * pageSize;
    // Evita rangos que excedan el total cuando la ultima pagina esta incompleta.
    const fin = Math.min(inicio + pageSize, total);
    return `${inicio + 1} - ${fin} de ${total}`;
  };
}
