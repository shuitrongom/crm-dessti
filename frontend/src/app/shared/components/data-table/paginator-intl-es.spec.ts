// =============================================================================
// Pruebas de PaginatorIntlEs: etiquetas del paginador en espanol (Req 52, 57)
// =============================================================================

import { PaginatorIntlEs } from './paginator-intl-es';

describe('PaginatorIntlEs', () => {
  let intl: PaginatorIntlEs;

  beforeEach(() => {
    intl = new PaginatorIntlEs();
  });

  it('traduce las etiquetas fijas al espanol', () => {
    expect(intl.itemsPerPageLabel).toBe('Elementos por pagina:');
    expect(intl.nextPageLabel).toBe('Pagina siguiente');
    expect(intl.previousPageLabel).toBe('Pagina anterior');
    expect(intl.firstPageLabel).toBe('Primera pagina');
    expect(intl.lastPageLabel).toBe('Ultima pagina');
  });

  it('formatea el rango como "X - Y de Z"', () => {
    expect(intl.getRangeLabel(0, 20, 40)).toBe('1 - 20 de 40');
    expect(intl.getRangeLabel(1, 20, 40)).toBe('21 - 40 de 40');
  });

  it('acota el fin del rango en la ultima pagina incompleta', () => {
    expect(intl.getRangeLabel(1, 20, 25)).toBe('21 - 25 de 25');
  });

  it('devuelve "0 de Z" cuando no hay resultados', () => {
    expect(intl.getRangeLabel(0, 20, 0)).toBe('0 de 0');
  });
});
