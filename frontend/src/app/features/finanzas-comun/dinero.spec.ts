// =============================================================================
// Pruebas unitarias de las utilidades de dinero para previsualizacion (Req 62)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista, el calculo de la variacion real vs estimado
// (importe en centavos enteros y porcentaje) y la suma en centavos que evita los
// errores de coma flotante. El calculo oficial lo hace el servidor; estas son
// ayudas de previsualizacion, pero deben ser exactas.
// =============================================================================

import { aCentavos, sumaCentavos, aPesos, variacion } from './dinero';

describe('dinero (previsualizacion)', () => {
  describe('aCentavos', () => {
    it('convierte pesos a centavos enteros con redondeo HALF_UP', () => {
      expect(aCentavos(10.005)).toBe(1001);
      expect(aCentavos(10.004)).toBe(1000);
      expect(aCentavos(0)).toBe(0);
    });

    it('trata valores no finitos como cero', () => {
      expect(aCentavos(Number.NaN)).toBe(0);
      expect(aCentavos(Number.POSITIVE_INFINITY)).toBe(0);
    });
  });

  describe('sumaCentavos', () => {
    it('suma en centavos evitando el error clasico de float (0.1 + 0.2)', () => {
      // 0.1 + 0.2 en float da 0.30000000000000004; en centavos es exacto.
      expect(sumaCentavos([0.1, 0.2])).toBe(30);
      expect(aPesos(sumaCentavos([0.1, 0.2]))).toBe(0.3);
    });

    it('suma una lista de importes monetarios de forma exacta', () => {
      expect(sumaCentavos([1234.56, 65.44, 0.0])).toBe(130000);
      expect(aPesos(sumaCentavos([1234.56, 65.44]))).toBe(1300);
    });
  });

  describe('variacion (real vs estimado)', () => {
    it('calcula variacion positiva en importe y porcentaje', () => {
      const r = variacion(1200, 1000);
      expect(r.montoCentavos).toBe(20000); // +2000.00
      expect(r.porcentaje).toBe(20);
    });

    it('calcula variacion negativa (real por debajo del estimado)', () => {
      const r = variacion(800, 1000);
      expect(r.montoCentavos).toBe(-20000);
      expect(r.porcentaje).toBe(-20);
    });

    it('devuelve porcentaje null cuando el estimado es cero (indefinido)', () => {
      const r = variacion(500, 0);
      expect(r.montoCentavos).toBe(50000);
      expect(r.porcentaje).toBeNull();
    });

    it('redondea el porcentaje a 2 decimales', () => {
      const r = variacion(1015, 900); // 115/900 = 12.777...%
      expect(r.porcentaje).toBe(12.78);
    });
  });
});
