// =============================================================================
// Utilidades de dinero para PREVISUALIZACION en el cliente (Req 39, 62)
// -----------------------------------------------------------------------------
// El calculo oficial de importes SIEMPRE lo realiza el servidor (BigDecimal
// escala 2, redondeo HALF_UP). Estas utilidades existen unicamente para mostrar
// previsualizaciones en formularios (p. ej. el balance tentativo de una poliza o
// la variacion de un presupuesto). Para evitar los errores de coma flotante de
// IEEE-754, las sumas se hacen en CENTAVOS ENTEROS y luego se dividen entre 100.
// Nunca deben sustituir el valor calculado por el backend.
// =============================================================================

/** Convierte un importe en pesos (decimal) a centavos enteros con redondeo. */
export function aCentavos(importe: number): number {
  return Math.round((Number.isFinite(importe) ? importe : 0) * 100);
}

/** Suma una lista de importes en pesos y devuelve el total en CENTAVOS enteros. */
export function sumaCentavos(importes: number[]): number {
  return importes.reduce((acumulado, importe) => acumulado + aCentavos(importe), 0);
}

/** Convierte centavos enteros de vuelta a pesos (decimal con 2 posiciones). */
export function aPesos(centavos: number): number {
  return centavos / 100;
}

/**
 * Calcula la variacion (real - estimado) y su porcentaje respecto al estimado,
 * en CENTAVOS para la parte entera. El porcentaje se devuelve como numero con
 * hasta 2 decimales; si el estimado es 0 devuelve {@code null} (indefinido).
 * Es una ayuda de previsualizacion; el comparativo oficial lo da el backend.
 */
export function variacion(
  real: number,
  estimado: number,
): { montoCentavos: number; porcentaje: number | null } {
  const montoCentavos = aCentavos(real) - aCentavos(estimado);
  const estimadoCentavos = aCentavos(estimado);
  const porcentaje =
    estimadoCentavos === 0 ? null : Math.round((montoCentavos / estimadoCentavos) * 10000) / 100;
  return { montoCentavos, porcentaje };
}
