// =============================================================================
// Pruebas del validador telefonoValidator
// -----------------------------------------------------------------------------
// Verifican que el telefono sea exactamente 10 digitos numericos: acepta un
// numero de 10 digitos, rechaza numeros cortos, largos y con letras. El control
// vacio no se valida (se delega en Validators.required).
// =============================================================================

import { FormControl } from '@angular/forms';

import { telefonoValidator } from './telefono.validator';

describe('telefonoValidator', () => {
  const validar = (valor: string | null) => telefonoValidator()(new FormControl(valor));

  it('acepta un telefono de 10 digitos', () => {
    expect(validar('5551234567')).toBeNull();
  });

  it('rechaza un telefono demasiado corto', () => {
    expect(validar('123')).toEqual({ telefono: true });
  });

  it('rechaza un telefono demasiado largo (11 digitos)', () => {
    expect(validar('55512345678')).toEqual({ telefono: true });
  });

  it('rechaza un telefono con letras', () => {
    expect(validar('abcdefghij')).toEqual({ telefono: true });
  });

  it('no valida cuando el control esta vacio', () => {
    expect(validar('')).toBeNull();
    expect(validar(null)).toBeNull();
  });
});
