// =============================================================================
// Dialogo de restablecimiento de contrasena del administrador de una Empresa
// (super_admin) (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Permite al super_admin reiniciar la contrasena del administrador de una
// Empresa en dos modos, elegibles con un control de radio:
//   - "generar" (por defecto): el servidor genera una contrasena TEMPORAL que se
//     muestra UNA UNICA VEZ con un boton de copiado y un aviso claro.
//   - "explicita": el super_admin fija una contrasena (8..255) con confirmacion
//     y validador cruzado de coincidencia.
// Al confirmar hace POST /empresas/{id}/admin/reset-password. Mapea 404 (admin
// no encontrado) y 422 (contrasena invalida) a mensajes en espanol. Accesible
// (labels, foco, aria) y responsive; solo tokens del Sistema de Diseno.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatRadioModule } from '@angular/material/radio';

import { EmpresasService } from '../services/empresas.service';
import { ResetPasswordAdmin } from '../models/plataforma.models';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';

/** Datos de entrada del dialogo: la Empresa cuyo admin se restablece. */
export interface ResetPasswordDialogData {
  empresaId: string;
  empresaNombre: string;
}

/** Modo de restablecimiento: generar temporal o establecer explicita. */
type ModoReset = 'generar' | 'explicita';

/**
 * Validador cruzado de coincidencia para el modo "explicita". Solo aplica cuando
 * el modo es explicito y ambos campos tienen contenido; marca `noCoincide` en el
 * campo de confirmacion para mostrar el error junto a el.
 */
function coincidenPassword(grupo: AbstractControl): ValidationErrors | null {
  const modo = grupo.get('modo')?.value as ModoReset | undefined;
  const confirmacion = grupo.get('confirmarPassword');
  if (modo !== 'explicita' || !confirmacion) {
    confirmacion?.hasError('noCoincide') && limpiarNoCoincide(confirmacion);
    return null;
  }
  const nueva = grupo.get('password')?.value ?? '';
  if (confirmacion.value === '') {
    return null;
  }
  if (nueva !== confirmacion.value) {
    confirmacion.setErrors({ ...(confirmacion.errors ?? {}), noCoincide: true });
    return { noCoincide: true };
  }
  limpiarNoCoincide(confirmacion);
  return null;
}

/** Quita el error `noCoincide` de un control preservando el resto. */
function limpiarNoCoincide(control: AbstractControl): void {
  if (!control.hasError('noCoincide')) {
    return;
  }
  const { noCoincide, ...resto } = control.errors ?? {};
  void noCoincide;
  control.setErrors(Object.keys(resto).length ? resto : null);
}

@Component({
  selector: 'app-reset-password-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatRadioModule,
  ],
  templateUrl: './reset-password-dialog.html',
  styleUrl: './reset-password-dialog.scss',
})
export class ResetPasswordDialog {
  private readonly fb = inject(FormBuilder);
  private readonly empresasService = inject(EmpresasService);
  private readonly toast = inject(NotificacionesService);
  private readonly dialogRef = inject(MatDialogRef<ResetPasswordDialog, boolean>);
  protected readonly data = inject<ResetPasswordDialogData>(MAT_DIALOG_DATA);

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Resultado tras el reinicio: si trae `passwordTemporal`, se muestra una vez. */
  protected readonly resultado = signal<ResetPasswordAdmin | null>(null);
  /** `true` tras copiar la contrasena temporal al portapapeles (feedback). */
  protected readonly copiado = signal(false);

  protected readonly formulario = this.fb.nonNullable.group(
    {
      modo: ['generar' as ModoReset],
      password: ['', [Validators.minLength(8), Validators.maxLength(255)]],
      confirmarPassword: [''],
    },
    { validators: coincidenPassword },
  );

  /**
   * Respaldo reactivo del modo elegido: un `computed` sobre el FormControl no
   * reacciona a sus cambios, asi que se actualiza desde el handler `cambiarModo`.
   */
  private readonly modoSeleccionado = signal<ModoReset>('generar');

  /** `true` cuando el modo elegido es establecer una contrasena explicita. */
  protected readonly modoExplicito = computed(() => this.modoSeleccionado() === 'explicita');

  /** `true` cuando el resultado incluye una contrasena temporal generada. */
  protected readonly hayTemporal = computed(() => !!this.resultado()?.passwordTemporal);

  /** Reacciona al cambio de modo: ajusta los validadores de contrasena explicita. */
  protected cambiarModo(modo: ModoReset): void {
    this.modoSeleccionado.set(modo);
    const password = this.formulario.controls.password;
    const confirmar = this.formulario.controls.confirmarPassword;
    if (modo === 'explicita') {
      password.addValidators(Validators.required);
      confirmar.addValidators(Validators.required);
    } else {
      password.removeValidators(Validators.required);
      confirmar.removeValidators(Validators.required);
      password.setValue('');
      confirmar.setValue('');
    }
    password.updateValueAndValidity();
    confirmar.updateValueAndValidity();
  }

  /** Confirma el restablecimiento contra el backend. */
  protected confirmar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    const modo = this.formulario.controls.modo.value;
    const body =
      modo === 'explicita'
        ? { password: this.formulario.controls.password.value }
        : undefined;

    this.enviando.set(true);
    this.empresasService.restablecerPasswordAdmin(this.data.empresaId, body).subscribe({
      next: (resultado) => {
        this.enviando.set(false);
        if (resultado.passwordTemporal) {
          // Se muestra una unica vez en el propio dialogo (con copiado).
          this.resultado.set(resultado);
        } else {
          // Contrasena explicita: no hay temporal que mostrar; cierra con exito.
          this.toast.exito(`Contraseña actualizada para ${resultado.identificador}.`);
          this.dialogRef.close(true);
        }
      },
      error: (e: HttpErrorResponse) => {
        this.enviando.set(false);
        if (e.status === 404) {
          this.error.set('No se encontró un administrador para esta empresa.');
        } else if (e.status === 422) {
          this.error.set('La contraseña indicada no es válida. Debe tener entre 8 y 255 caracteres.');
        } else {
          this.error.set(mensajeDeError(e));
        }
      },
    });
  }

  /** Copia la contrasena temporal al portapapeles (feedback temporal). */
  protected async copiar(): Promise<void> {
    const temporal = this.resultado()?.passwordTemporal;
    if (!temporal) {
      return;
    }
    try {
      await navigator.clipboard.writeText(temporal);
      this.copiado.set(true);
      setTimeout(() => this.copiado.set(false), 2000);
    } catch {
      // Sin permiso de portapapeles: el Usuario puede copiar manualmente.
      this.copiado.set(false);
    }
  }

  /** Cierra el dialogo tras mostrar la contrasena temporal (recarga la lista). */
  protected cerrarConExito(): void {
    this.dialogRef.close(true);
  }

  /** Cancela sin cambios. */
  protected cancelar(): void {
    this.dialogRef.close(false);
  }
}
