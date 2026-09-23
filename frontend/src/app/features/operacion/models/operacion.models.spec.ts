// =============================================================================
// Pruebas unitarias de la logica de dominio de operacion (Req 7, 19, 21)
// -----------------------------------------------------------------------------
// Verifican de forma determinista y sin zona:
//   - Maquina de estados de la Orden de Fabricacion (Req 7.5, 7.6).
//   - Maquina de estados de la OTI (Req 19.5).
//   - Derivacion del avance por sitio (fases cubiertas -> porcentaje) (Req 21.3).
// =============================================================================

import {
  SitioAvance,
  estadosDestinoOf,
  estadosDestinoOti,
  fasesCubiertas,
  porcentajeAvanceSitio,
} from './operacion.models';

describe('operacion.models — estados de orden de fabricacion (Req 7.5, 7.6)', () => {
  it('pendiente puede pasar a en_produccion o cancelada', () => {
    expect(estadosDestinoOf('pendiente')).toEqual(['en_produccion', 'cancelada']);
  });

  it('en_produccion puede pasar a terminada o cancelada', () => {
    expect(estadosDestinoOf('en_produccion')).toEqual(['terminada', 'cancelada']);
  });

  it('terminada y cancelada son terminales', () => {
    expect(estadosDestinoOf('terminada')).toEqual([]);
    expect(estadosDestinoOf('cancelada')).toEqual([]);
  });
});

describe('operacion.models — estados de OTI (Req 19.5)', () => {
  it('programada puede pasar a en_curso o cancelada', () => {
    expect(estadosDestinoOti('programada')).toEqual(['en_curso', 'cancelada']);
  });

  it('en_curso puede pasar a completada o cancelada', () => {
    expect(estadosDestinoOti('en_curso')).toEqual(['completada', 'cancelada']);
  });

  it('completada y cancelada son terminales', () => {
    expect(estadosDestinoOti('completada')).toEqual([]);
    expect(estadosDestinoOti('cancelada')).toEqual([]);
  });
});

describe('operacion.models — avance por sitio (Req 21.3, 21.4)', () => {
  /** Construye un SitioAvance con las cuatro banderas indicadas. */
  function avance(l: boolean, p: boolean, f: boolean, i: boolean): SitioAvance {
    return {
      sitio: {
        id: 's1',
        proyectoId: 'p1',
        nombre: 'Sitio',
        direccion: null,
        version: 0,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
      tieneLevantamientoCompletado: l,
      tienePermisoAprobado: p,
      tieneOrdenFabricacionTerminada: f,
      tieneInstalacionCompletada: i,
    };
  }

  it('cuenta las fases cubiertas en el rango 0..4', () => {
    expect(fasesCubiertas(avance(false, false, false, false))).toBe(0);
    expect(fasesCubiertas(avance(true, false, false, false))).toBe(1);
    expect(fasesCubiertas(avance(true, true, true, true))).toBe(4);
  });

  it('deriva el porcentaje de avance a partir de las fases cubiertas', () => {
    expect(porcentajeAvanceSitio(avance(false, false, false, false))).toBe(0);
    expect(porcentajeAvanceSitio(avance(true, false, false, false))).toBe(25);
    expect(porcentajeAvanceSitio(avance(true, true, false, false))).toBe(50);
    expect(porcentajeAvanceSitio(avance(true, true, true, true))).toBe(100);
  });
});
