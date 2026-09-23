// =============================================================================
// Pruebas unitarias de los comparativos de Inteligencia de Negocio (Req 48.1)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista, la derivacion de la tendencia, la variacion
// porcentual (con el borde de comparativo cero) y el comparativo de presentacion
// completo (icono + texto accesible) a partir de un indicador.
// =============================================================================

import {
  comparativoDe,
  porcentajeVariacion,
  tendenciaDe,
  textoTendencia,
} from './comparativo';
import { Indicador } from './models/reportes.models';

function indicador(valor: number, comparativo: number | null, variacion?: number | null): Indicador {
  return {
    clave: 'ventas',
    etiqueta: 'Ventas',
    valor,
    unidad: 'MXN',
    comparativo,
    variacion: variacion === undefined ? null : variacion,
  };
}

describe('tendenciaDe', () => {
  it('devuelve subida cuando la variacion es positiva', () => {
    expect(tendenciaDe(15)).toBe('subida');
  });

  it('devuelve bajada cuando la variacion es negativa', () => {
    expect(tendenciaDe(-3)).toBe('bajada');
  });

  it('devuelve neutra cuando la variacion es cero', () => {
    expect(tendenciaDe(0)).toBe('neutra');
  });

  it('devuelve ninguna cuando no hay variacion (null)', () => {
    expect(tendenciaDe(null)).toBe('ninguna');
  });
});

describe('porcentajeVariacion', () => {
  it('calcula el crecimiento porcentual respecto del comparativo', () => {
    // (1200 - 1000) / 1000 = 20 %.
    expect(porcentajeVariacion(1200, 1000)).toBe(20);
  });

  it('calcula la caida porcentual', () => {
    // (800 - 1000) / 1000 = -20 %.
    expect(porcentajeVariacion(800, 1000)).toBe(-20);
  });

  it('redondea a 2 decimales', () => {
    // (1015 - 900) / 900 = 12.777... -> 12.78 %.
    expect(porcentajeVariacion(1015, 900)).toBe(12.78);
  });

  it('devuelve null cuando el comparativo es cero (indefinido)', () => {
    expect(porcentajeVariacion(500, 0)).toBeNull();
  });

  it('devuelve null cuando no hay comparativo', () => {
    expect(porcentajeVariacion(500, null)).toBeNull();
  });

  it('usa el valor absoluto del comparativo negativo para el signo del cambio', () => {
    // (-50 - (-100)) / |−100| = 50 / 100 = 50 %.
    expect(porcentajeVariacion(-50, -100)).toBe(50);
  });
});

describe('comparativoDe', () => {
  it('deriva tendencia, porcentaje, icono y texto para una subida', () => {
    const c = comparativoDe(indicador(1200, 1000, 200));
    expect(c.tendencia).toBe('subida');
    expect(c.variacion).toBe(200);
    expect(c.porcentaje).toBe(20);
    expect(c.icono).toBe('trending_up');
    expect(c.texto).toBe('al alza');
  });

  it('deriva la variacion del backend cuando no viene explicita', () => {
    // Sin variacion en el DTO: se deriva de valor - comparativo.
    const c = comparativoDe(indicador(800, 1000, null));
    expect(c.variacion).toBe(-200);
    expect(c.tendencia).toBe('bajada');
    expect(c.icono).toBe('trending_down');
  });

  it('marca sin comparativo cuando el comparativo es null', () => {
    const c = comparativoDe(indicador(500, null, null));
    expect(c.tendencia).toBe('ninguna');
    expect(c.variacion).toBeNull();
    expect(c.porcentaje).toBeNull();
    expect(c.icono).toBe('trending_flat');
    expect(c.texto).toBe('sin comparativo');
  });

  it('trata una variacion cero como neutra con texto accesible', () => {
    const c = comparativoDe(indicador(1000, 1000, 0));
    expect(c.tendencia).toBe('neutra');
    expect(textoTendencia(c.tendencia)).toBe('sin cambio');
  });
});
