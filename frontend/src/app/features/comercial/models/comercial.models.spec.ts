// =============================================================================
// Pruebas unitarias de la logica de dominio comercial (Req 6, 14)
// -----------------------------------------------------------------------------
// Verifican de forma determinista y sin zona:
//   - Calculo de subtotal/total de partidas de una Cotizacion (Req 6.3, 6.5).
//   - Maquina de estados del pipeline de Oportunidades (Req 14.3, 14.4): solo se
//     ofrecen transiciones validas; las etapas terminales no tienen salida.
//   - Maquina de estados de la Cotizacion (Req 6.6, 6.7).
// =============================================================================

import {
  estadosDestinoCotizacion,
  etapasDestino,
  subtotalPartida,
  totalPartidas,
} from './comercial.models';

describe('comercial.models — calculo de partidas (Req 6.5)', () => {
  it('subtotalPartida redondea a 2 decimales half-up', () => {
    expect(subtotalPartida(3, 10.005)).toBe(30.02);
    expect(subtotalPartida(2, 12.5)).toBe(25);
    expect(subtotalPartida(0, 99)).toBe(0);
  });

  it('subtotalPartida tolera valores no numericos como 0', () => {
    expect(subtotalPartida(NaN as unknown as number, 10)).toBe(0);
    expect(subtotalPartida(5, NaN as unknown as number)).toBe(0);
  });

  it('totalPartidas suma los subtotales y redondea a 2 decimales', () => {
    const partidas = [
      { cantidad: 2, precioUnitario: 10 },
      { cantidad: 3, precioUnitario: 5.5 },
      { cantidad: 1, precioUnitario: 0.01 },
    ];
    // 20 + 16.5 + 0.01 = 36.51
    expect(totalPartidas(partidas)).toBe(36.51);
  });

  it('totalPartidas trata un precio nulo/ausente como 0', () => {
    const partidas = [
      { cantidad: 4, precioUnitario: null },
      { cantidad: 2, precioUnitario: 15 },
    ];
    expect(totalPartidas(partidas)).toBe(30);
  });
});

describe('comercial.models — pipeline de oportunidades (Req 14.3, 14.4)', () => {
  it('ofrece las transiciones validas desde cada etapa no terminal', () => {
    expect(etapasDestino('nuevo')).toEqual(['calificado', 'perdido']);
    expect(etapasDestino('calificado')).toEqual(['propuesta', 'perdido']);
    expect(etapasDestino('propuesta')).toEqual(['negociacion', 'perdido']);
    expect(etapasDestino('negociacion')).toEqual(['ganado', 'perdido']);
  });

  it('las etapas terminales (ganado/perdido) no ofrecen transiciones', () => {
    expect(etapasDestino('ganado')).toEqual([]);
    expect(etapasDestino('perdido')).toEqual([]);
  });

  it('nunca ofrece una transicion de regreso (el embudo no retrocede)', () => {
    // Property-like: ninguna etapa de destino debe apuntar a "nuevo".
    const etapas = ['nuevo', 'calificado', 'propuesta', 'negociacion', 'ganado', 'perdido'] as const;
    for (const etapa of etapas) {
      expect(etapasDestino(etapa)).not.toContain('nuevo');
    }
  });
});

describe('comercial.models — estados de cotizacion (Req 6.6, 6.7)', () => {
  it('borrador puede enviarse; enviada puede aprobarse o rechazarse', () => {
    expect(estadosDestinoCotizacion('borrador')).toEqual(['enviada']);
    expect(estadosDestinoCotizacion('enviada')).toEqual(['aprobada', 'rechazada']);
  });

  it('aprobada y rechazada son terminales', () => {
    expect(estadosDestinoCotizacion('aprobada')).toEqual([]);
    expect(estadosDestinoCotizacion('rechazada')).toEqual([]);
  });
});
