// =============================================================================
// Pruebas del validador rfcValidator (Req 24.2)
// -----------------------------------------------------------------------------
// Verifican el patron del RFC mexicano: persona moral (12) y fisica (13),
// normalizacion a mayusculas y rechazo de valores mal formados. El control
// vacio no se valida (se delega en Validators.required).
// =============================================================================

import { FormControl } from '@angular/forms';

import { rfcValidator } from './rfc.validator';

describe('rfcValidator', () => {
  const validar = (valor: string | null) => rfcValidator()(new FormControl(valor));

  it('acepta un RFC de persona moral (12 caracteres)', () => {
    expect(validar('ABC010101AB1')).toBeNull();
  });

  it('acepta un RFC de persona fisica (13 caracteres)', () => {
    expect(validar('ABCD901231XYZ')).toBeNull();
  });

  it('acepta letras iniciales con Ñ y &', () => {
    expect(validar('ÑÑ&010101AB1')).toBeNull();
  });

  it('normaliza a mayusculas: acepta un RFC en minusculas', () => {
    expect(validar('abc010101ab1')).toBeNull();
  });

  it('rechaza un RFC demasiado corto', () => {
    expect(validar('ABC01')).toEqual({ rfc: true });
  });

  it('rechaza un RFC con caracteres invalidos en la fecha', () => {
    expect(validar('ABCXX0101AB1')).toEqual({ rfc: true });
  });

  it('no valida cuando el control esta vacio', () => {
    expect(validar('')).toBeNull();
    expect(validar(null)).toBeNull();
  });
});
