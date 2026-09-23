// =============================================================================
// Validador reactivo de telefono (10 digitos)
// -----------------------------------------------------------------------------
// Estandariza en el cliente la validacion de telefonos del sistema: cuando el
// campo trae valor, debe ser exactamente 10 digitos numericos. El backend sigue
// siendo la autoridad; esta validacion solo mejora la retroalimentacion (UX).
// No valida cuando el control esta vacio (se delega en `required` cuando aplica),
// por lo que un campo opcional vacio se considera valido.
// =============================================================================

import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Patron de telefono: exactamente 10 digitos numericos.
 */
const PATRON_TELEFONO = /^\d{10}$/;

/**
 * Validador que exige un telefono de exactamente 10 digitos. Recorta espacios
 * antes de probar el patron; devuelve `{ telefono: true }` cuando el valor no
 * cumple. No valida cuando el control esta vacio (delegar en `required`).
 */
export function telefonoValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const valor = control.value;
    if (valor === null || valor === undefined || valor === '') {
      return null;
    }
    const normalizado = String(valor).trim();
    if (normalizado === '') {
      return null;
    }
    return PATRON_TELEFONO.test(normalizado) ? null : { telefono: true };
  };
}
