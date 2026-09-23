// =============================================================================
// Pruebas unitarias de la Ventana de Servicio (Req 64.6, 64.7)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y con reloj inyectado, el calculo del estado
// de la ventana (dentro / por_expirar / expirada) a partir del ultimo entrante y
// del instante "ahora". Es la logica pura que gobierna el compositor de mensajes:
// dentro de las 24 h se admite texto libre; fuera de ellas solo plantillas.
// =============================================================================

import {
  calcularVentanaServicio,
  etiquetaVentana,
  textoRestante,
  tonoVentana,
} from './ventana-servicio';

const AHORA = new Date('2026-01-10T12:00:00Z');

describe('calcularVentanaServicio', () => {
  it('esta DENTRO de la ventana cuando el entrante es reciente (2 h)', () => {
    const hace2h = new Date('2026-01-10T10:00:00Z');
    const v = calcularVentanaServicio(hace2h, AHORA, 24);
    expect(v.estado).toBe('dentro');
    expect(v.permiteTextoLibre).toBe(true);
    // Quedan 22 h -> 1320 min.
    expect(v.restanteMinutos).toBe(22 * 60);
  });

  it('esta EXPIRADA cuando han pasado mas de 24 h desde el entrante', () => {
    const hace25h = new Date('2026-01-09T11:00:00Z');
    const v = calcularVentanaServicio(hace25h, AHORA, 24);
    expect(v.estado).toBe('expirada');
    expect(v.permiteTextoLibre).toBe(false);
    expect(v.restanteMs).toBe(0);
  });

  it('marca POR_EXPIRAR cuando quedan menos que el umbral (60 min)', () => {
    // 23 h 30 min transcurridos -> quedan 30 min.
    const hace23h30 = new Date('2026-01-09T12:30:00Z');
    const v = calcularVentanaServicio(hace23h30, AHORA, 24, 60);
    expect(v.estado).toBe('por_expirar');
    expect(v.permiteTextoLibre).toBe(true);
    expect(v.restanteMinutos).toBe(30);
  });

  it('trata el borde exacto de 24 h como DENTRO (<=)', () => {
    const hace24hExactas = new Date('2026-01-09T12:00:00Z');
    const v = calcularVentanaServicio(hace24hExactas, AHORA, 24, 60);
    // Restante 0 min: sigue dentro (por_expirar), texto libre aun permitido.
    expect(v.permiteTextoLibre).toBe(true);
    expect(v.estado).toBe('por_expirar');
    expect(v.restanteMinutos).toBe(0);
  });

  it('sin ningun entrante (null) se considera EXPIRADA (se exige plantilla)', () => {
    const v = calcularVentanaServicio(null, AHORA, 24);
    expect(v.estado).toBe('expirada');
    expect(v.permiteTextoLibre).toBe(false);
  });

  it('un ahora anterior al entrante (transcurrido negativo) sigue DENTRO', () => {
    const futuro = new Date('2026-01-10T13:00:00Z');
    const v = calcularVentanaServicio(futuro, AHORA, 24, 60);
    expect(v.permiteTextoLibre).toBe(true);
    expect(v.estado).toBe('dentro');
  });

  it('acepta instantes en formato ISO string igual que Date', () => {
    const v = calcularVentanaServicio('2026-01-10T10:00:00Z', '2026-01-10T12:00:00Z', 24);
    expect(v.estado).toBe('dentro');
    expect(v.restanteMinutos).toBe(22 * 60);
  });

  it('devuelve EXPIRADA ante entradas invalidas (fecha no parseable)', () => {
    const v = calcularVentanaServicio('no-es-fecha', AHORA, 24);
    expect(v.estado).toBe('expirada');
  });
});

describe('presentacion de la ventana', () => {
  it('etiquetaVentana traduce cada estado a espanol', () => {
    expect(etiquetaVentana('dentro')).toBe('Dentro de ventana');
    expect(etiquetaVentana('por_expirar')).toBe('Por expirar');
    expect(etiquetaVentana('expirada')).toBe('Ventana expirada');
  });

  it('tonoVentana mapea a semaforo verde/ambar/rojo', () => {
    expect(tonoVentana('dentro')).toBe('exito');
    expect(tonoVentana('por_expirar')).toBe('advertencia');
    expect(tonoVentana('expirada')).toBe('error');
  });

  it('textoRestante describe el tiempo restante de forma accesible', () => {
    const v = calcularVentanaServicio('2026-01-10T09:40:00Z', AHORA, 24);
    // Quedan 21 h 40 min.
    expect(textoRestante(v)).toBe('Quedan 21 h 40 min');
  });

  it('textoRestante indica ventana cerrada cuando expiro', () => {
    const v = calcularVentanaServicio(null, AHORA, 24);
    expect(textoRestante(v)).toBe('Ventana cerrada: solo plantillas aprobadas');
  });
});
