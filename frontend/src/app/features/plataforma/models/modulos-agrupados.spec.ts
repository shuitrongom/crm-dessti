// =============================================================================
// Pruebas de agruparModulosPorGiro y humanizarGiro (Req 25.1)
// =============================================================================

import { agruparModulosPorGiro, humanizarGiro, TITULO_NUCLEO } from './modulos-agrupados';
import { ModuloCatalogo } from './plataforma.models';

describe('agruparModulosPorGiro', () => {
  const catalogo: ModuloCatalogo[] = [
    {
      clave: 'comercial',
      nombreVisible: 'Comercial (CRM)',
      giro: null,
      catalogoModuloId: 'c1',
      precio: 1500,
      monedaCodigo: 'MXN',
    },
    {
      clave: 'operacion',
      nombreVisible: 'Operacion y produccion',
      giro: 'anuncios-luminosos',
      catalogoModuloId: 'c2',
      precio: 800,
      monedaCodigo: 'MXN',
    },
    {
      clave: 'produccion-industrial',
      nombreVisible: 'Produccion industrial',
      giro: 'manufactura',
      catalogoModuloId: null,
      precio: null,
      monedaCodigo: 'MXN',
    },
  ];

  it('coloca el grupo de Nucleo primero', () => {
    const grupos = agruparModulosPorGiro(catalogo);
    expect(grupos[0].giro).toBeNull();
    expect(grupos[0].titulo).toBe(TITULO_NUCLEO);
    expect(grupos[0].modulos.map((m) => m.clave)).toEqual(['comercial']);
  });

  it('agrupa por Giro en orden de aparicion', () => {
    const grupos = agruparModulosPorGiro(catalogo);
    expect(grupos.map((g) => g.giro)).toEqual([null, 'anuncios-luminosos', 'manufactura']);
  });

  it('omite el grupo de Nucleo cuando no hay modulos transversales', () => {
    const grupos = agruparModulosPorGiro([catalogo[1]]);
    expect(grupos).toHaveLength(1);
    expect(grupos[0].giro).toBe('anuncios-luminosos');
  });

  it('usa la etiqueta de Giro provista y, si falta, humaniza la clave', () => {
    const etiquetas = new Map([['anuncios-luminosos', 'Anuncios luminosos']]);
    const grupos = agruparModulosPorGiro(catalogo, etiquetas);
    expect(grupos[1].titulo).toBe('Anuncios luminosos');
    expect(grupos[2].titulo).toBe('Manufactura'); // sin etiqueta -> humanizada
  });
});

describe('humanizarGiro', () => {
  it('reemplaza guiones y capitaliza', () => {
    expect(humanizarGiro('anuncios-luminosos')).toBe('Anuncios luminosos');
    expect(humanizarGiro('rh_nomina')).toBe('Rh nomina');
  });
});
