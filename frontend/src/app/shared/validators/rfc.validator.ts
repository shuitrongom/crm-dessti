// =============================================================================
// Validador reactivo de RFC mexicano (Req 24.2)
// -----------------------------------------------------------------------------
// Reproduce en el cliente la validacion que el backend aplica al RFC para dar
// retroalimentacion inmediata (UX); el backend sigue siendo la autoridad y
// devuelve 422 ante un RFC invalido. Acepta RFC de persona moral (12 caracteres)
// y de persona fisica (13 caracteres). La comparacion es en mayusculas, por lo
// que un RFC en minusculas se considera valido (se normaliza antes de probar).
// =============================================================================

import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Patron del RFC mexicano: 3 o 4 letras iniciales (incluye Ñ y &), 6 digitos de
 * fecha (AAMMDD) y 3 caracteres de homoclave (letras o digitos).
 */
const PATRON_RFC = /^[A-ZÑ&]{3,4}\d{6}[A-Z\d]{3}$/;

/**
 * Validador que exige un RFC mexicano bien formado. Normaliza el valor a
 * mayusculas antes de probar el patron; devuelve `{ rfc: true }` cuando el valor
 * no cumple. No valida cuando el control esta vacio (delegar en `required`).
 */
export function rfcValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const valor = control.value;
    if (valor === null || valor === undefined || valor === '') {
      return null;
    }
    const normalizado = String(valor).trim().toUpperCase();
    return PATRON_RFC.test(normalizado) ? null : { rfc: true };
  };
}
