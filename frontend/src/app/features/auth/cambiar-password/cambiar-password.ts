// =============================================================================
// Cambio de contrasena forzado (V69)
// -----------------------------------------------------------------------------
// Pantalla a la que se redirige tras iniciar sesion con una contrasena TEMPORAL
// (marcada por el backend con debeCambiarPassword). Obliga a definir una nueva
// contrasena antes de operar. Reutiliza PUT /auth/perfil/password: la contrasena
// ACTUAL (la temporal recien usada) llega por el estado de navegacion desde el
// login, por lo que no se pide de nuevo ni viaja en la URL. Al exito redirige al
// ambito del Usuario. Accesible y solo con tokens del Sistema de Diseno.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../../../core/auth/auth.service';
import { PerfilService } from '../../../core/auth/perfil.service';
import { ModulosEmpresaService } from '../../../core/auth/modulos-empresa.service';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';

/** Validador cruzado: passwordNueva y confirmar deben coincidir. */
function coinciden(grupo: AbstractControl): ValidationErrors | null {
  const nueva = grupo.get('passwordNueva')?.value ?? '';
  const confirmar = grupo.get('confirmarPassword');
  if (!confirmar || confirmar.value === '') {
    return null;
  }
  if (nueva !== confirmar.value) {
    confirmar.setErrors({ ...(confirmar.errors ?? {}), noCoincide: true });
    return { noCoincide: true };
  }
  if (confirmar.hasError('noCoincide')) {
    const { noCoincide, ...resto } = confirmar.errors ?? {};
    void noCoincide;
    confirmar.setErrors(Object.keys(resto).length ? resto : null);
  }
  return null;
}

@Component({
  selector: 'app-cambiar-password',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './cambiar-password.html',
  styleUrl: './cambiar-password.scss',
})
export class CambiarPassword {
  private readonly fb = inject(FormBuilder);
  private readonly perfil = inject(PerfilService);
  private readonly auth = inject(AuthService);
  private readonly modulosEmpresa = inject(ModulosEmpresaService);
  private readonly toast = inject(NotificacionesService);
  private readonly router = inject(Router);

  /** Contrasena actual (temporal) recibida por estado de navegacion desde el login. */
  private readonly passwordActual: string;

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly mostrar = signal(false);
  protected readonly anio = new Date().getFullYear();

  protected readonly formulario = this.fb.nonNullable.group(
    {
      passwordNueva: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(255)]],
      confirmarPassword: ['', [Validators.required]],
    },
    { validators: coinciden },
  );

  protected readonly etiquetaMostrar = computed(() =>
    this.mostrar() ? 'Ocultar contrasena' : 'Mostrar contrasena',
  );

  constructor() {
    const estado = this.router.getCurrentNavigation()?.extras.state
      ?? (typeof history !== 'undefined' ? history.state : undefined);
    this.passwordActual = (estado && typeof estado['passwordActual'] === 'string')
      ? estado['passwordActual']
      : '';
    // Sin la contrasena actual no se puede completar el cambio (acceso directo
    // a la ruta sin pasar por el login): se regresa al login.
    if (!this.passwordActual) {
      void this.router.navigateByUrl('/login');
    }
  }

  protected enviar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    const passwordNueva = this.formulario.controls.passwordNueva.value;
    this.perfil.cambiarPassword({ passwordActual: this.passwordActual, passwordNueva }).subscribe({
      next: () => {
        this.enviando.set(false);
        this.toast.exito('Tu contrasena se actualizo. Bienvenido.');
        this.modulosEmpresa.refrescar();
        this.redirigir();
      },
      error: (e: HttpErrorResponse) => {
        this.enviando.set(false);
        if (e.status === 422) {
          this.error.set('La contrasena temporal no coincide. Vuelve a iniciar sesion e intentalo de nuevo.');
        } else {
          this.error.set(mensajeDeError(e));
        }
      },
    });
  }

  private redirigir(): void {
    const destino =
      this.auth.ambito() === 'plataforma'
        ? '/plataforma'
        : this.auth.ambito() === 'portal'
          ? '/portal'
          : '/empresa';
    void this.router.navigateByUrl(destino);
  }
}
