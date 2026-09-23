// =============================================================================
// Vista de inicio de sesion (Req 1, 3)
// -----------------------------------------------------------------------------
// Formulario reactivo accesible (identificador + password) con validacion en
// espanol. Ante credenciales invalidas el backend responde 401 con mensaje
// generico; la vista muestra un mensaje sin filtrar detalle (Req 1.3). Tras un
// login exitoso redirige al ambito que corresponde al rol (super_admin ->
// /plataforma, cliente_portal -> /portal, resto -> /empresa), o a la URL de
// retorno si la habia (deep-link protegido por guarda).
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../../../core/auth/auth.service';
import { ModulosEmpresaService } from '../../../core/auth/modulos-empresa.service';
import { ThemeService } from '../../../core/services/theme.service';
import { TematizacionService } from '../../../core/services/tematizacion.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly modulosEmpresa = inject(ModulosEmpresaService);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);
  protected readonly theme = inject(ThemeService);
  private readonly tematizacion = inject(TematizacionService);

  constructor() {
    // La pantalla de login siempre usa el Tema_Corporativo: se limpia cualquier
    // color de marca que hubiera quedado de una sesion de empresa previa, de
    // modo que el branding por empresa NUNCA se filtra al login (aislamiento por
    // ambito). El color solo vive dentro del ambito empresa.
    this.tematizacion.limpiar();
  }

  /** Indica si hay un envio en curso (deshabilita el formulario). */
  protected readonly enviando = signal(false);
  /** Mensaje de error generico a mostrar (Req 1.3, 56). */
  protected readonly error = signal<string | null>(null);
  /** Alterna la visibilidad de la contrasena. */
  protected readonly mostrarPassword = signal(false);

  /** Anio actual para el pie de pagina de marca. */
  protected readonly anio = new Date().getFullYear();

  /** Formulario reactivo del login. */
  protected readonly formulario = this.fb.nonNullable.group({
    identificador: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  /** Etiqueta accesible del boton de mostrar/ocultar contrasena. */
  protected readonly etiquetaPassword = computed(() =>
    this.mostrarPassword() ? 'Ocultar contrasena' : 'Mostrar contrasena',
  );

  /** Envia las credenciales y navega al ambito correspondiente al rol. */
  protected enviar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    const { identificador, password } = this.formulario.getRawValue();

    this.auth.login({ identificador, password }).subscribe({
      next: (respuesta) => {
        this.enviando.set(false);
        // Contrasena temporal (V69): la cuenta debe cambiarla antes de operar.
        // Se redirige a la pantalla de cambio forzado, pasando la contrasena
        // recien usada como "actual" (por estado de navegacion, no en la URL).
        if (respuesta.debeCambiarPassword) {
          void this.router.navigate(['/cambiar-password'], {
            state: { passwordActual: password },
          });
          return;
        }
        // Sesion recien iniciada: si es una empresa, carga los Modulos VIVOS del
        // tenant para que el menu refleje el plan vigente sin depender del claim
        // congelado del token. Fuera de empresa (plataforma/portal) el servicio
        // ignora el refresco (evita 403).
        this.modulosEmpresa.refrescar();
        this.redirigir();
      },
      error: (err: unknown) => {
        this.enviando.set(false);
        // Mensaje generico: no se revela si el usuario existe o esta bloqueado (Req 1.3).
        this.error.set(mensajeDeError(err));
      },
    });
  }

  /** Redirige tras un login exitoso segun el ambito del Usuario o el retorno. */
  private redirigir(): void {
    const retorno = this.ruta.snapshot.queryParamMap.get('retorno');
    if (retorno) {
      void this.router.navigateByUrl(retorno);
      return;
    }
    const destino =
      this.auth.ambito() === 'plataforma'
        ? '/plataforma'
        : this.auth.ambito() === 'portal'
          ? '/portal'
          : '/empresa';
    void this.router.navigateByUrl(destino);
  }
}

