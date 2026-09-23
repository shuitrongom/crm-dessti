// =============================================================================
// Pruebas unitarias de la habilitacion de acciones de Nomina (Req 41)
// -----------------------------------------------------------------------------
// Verifican que la UI ofrece una unica accion de avance por estado, coherente con
// el flujo lineal borrador -> calculada -> autorizada -> timbrada -> pagada, y que
// el estado terminal (pagada) no ofrece ninguna accion. El backend valida la
// transicion; este helper solo evita mostrar botones invalidos.
// =============================================================================

import { accionDeNomina } from './nomina-estados';

describe('nomina-estados', () => {
  it('en borrador ofrece unicamente calcular', () => {
    const a = accionDeNomina('borrador');
    expect(a?.accion).toBe('calcular');
    expect(a?.etiqueta).toBe('Calcular');
  });

  it('en calculada ofrece unicamente autorizar', () => {
    expect(accionDeNomina('calculada')?.accion).toBe('autorizar');
  });

  it('en autorizada ofrece unicamente timbrar', () => {
    expect(accionDeNomina('autorizada')?.accion).toBe('timbrar');
  });

  it('en timbrada ofrece unicamente pagar', () => {
    expect(accionDeNomina('timbrada')?.accion).toBe('pagar');
  });

  it('en pagada (estado terminal) no ofrece ninguna accion', () => {
    expect(accionDeNomina('pagada')).toBeNull();
  });

  it('cada estado no terminal ofrece exactamente una accion de avance', () => {
    const estados = ['borrador', 'calculada', 'autorizada', 'timbrada'] as const;
    const acciones = estados.map((e) => accionDeNomina(e)?.accion);
    expect(acciones).toEqual(['calcular', 'autorizar', 'timbrar', 'pagar']);
  });
});
