// =============================================================================
// Vista "Mi perfil" del super_admin (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Pantalla de perfil propio para el ambito de plataforma. Dos tarjetas:
//   1. Datos de la cuenta (solo lectura): identificador legible, roles como
//      chips y ambito ("Plataforma" cuando el tenant es nulo). Se carga de
//      GET /auth/perfil.
//   2. Cambio de contrasena: formulario reactivo (passwordActual, passwordNueva,
//      confirmarPassword) con validaciones en espanol y validador cruzado de
//      coincidencia. Al enviar hace PUT /auth/perfil/password:
//        - 204: toast de exito y reinicio del formulario.
//        - 422: error de contrasena actual incorrecta (a nivel de campo/formulario).
//        - 400 / otros: mensaje generico de validacion.
//
// Enterprise, responsive (mobile-first) y accesible (labels, aria-busy, foco,
// errores inline). Solo tokens del Sistema de Diseno.
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
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  AddressAutocomplete,
  DireccionAutocompletada,
} from '../../../shared/components/address-autocomplete/address-autocomplete';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { PerfilService } from '../../../core/auth/perfil.service';
import { telefonoValidator } from '../../../shared/validators/telefono.validator';
import { AuthService } from '../../../core/auth/auth.service';
import { Perfil, ROL_ADMIN_EMPRESA } from '../../../core/auth/auth.models';
import {
  MiEmpresaService,
  ActualizarMiEmpresaRequest,
} from '../../empresa/services/mi-empresa.service';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

/**
 * Validador cruzado: exige que `confirmarPassword` coincida con `passwordNueva`.
 * Marca el error `noCoincide` en el propio control de confirmacion (no en el
 * grupo) para poder mostrarlo junto al campo. No valida mientras el campo de
 * confirmacion esta vacio (delegar en `required`).
 */
function coincidenPassword(grupo: AbstractControl): ValidationErrors | null {
  const nueva = grupo.get('passwordNueva')?.value ?? '';
  const confirmacion = grupo.get('confirmarPassword');
  if (!confirmacion || confirmacion.value === '') {
    return null;
  }
  if (nueva !== confirmacion.value) {
    confirmacion.setErrors({ ...(confirmacion.errors ?? {}), noCoincide: true });
    return { noCoincide: true };
  }
  // Limpia el error de coincidencia preservando otros posibles errores.
  if (confirmacion.hasError('noCoincide')) {
    const { noCoincide, ...resto } = confirmacion.errors ?? {};
    void noCoincide;
    confirmacion.setErrors(Object.keys(resto).length ? resto : null);
  }
  return null;
}

@Component({
  selector: 'app-plataforma-perfil',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    PageHeader,
    StateContainer,
    AddressAutocomplete,
  ],
  templateUrl: './perfil.html',
  styleUrl: './perfil.scss',
})
export class PlataformaPerfil {
  private readonly fb = inject(FormBuilder);
  private readonly perfilService = inject(PerfilService);
  private readonly miEmpresaService = inject(MiEmpresaService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(NotificacionesService);

  /** Estado de carga del perfil (GET /auth/perfil). */
  protected readonly estado = signal<EstadoSolicitud<Perfil>>(cargando());

  /** `true` mientras el cambio de contrasena esta en curso. */
  protected readonly enviando = signal(false);
  /** Mensaje de error del cambio de contrasena a nivel de formulario. */
  protected readonly errorPassword = signal<string | null>(null);

  /**
   * `true` cuando el Usuario es administrador de una Empresa (tiene el rol
   * `admin_empresa` o un tenant asociado). Solo en ese caso se muestra la
   * seccion "Datos de mi empresa"; el super_admin (sin tenant) no la ve.
   */
  protected readonly esAdminEmpresa = this.auth.tieneRol(ROL_ADMIN_EMPRESA) || this.auth.tenantId() !== null;

  /** Estado de carga de los datos de la propia Empresa (GET /empresas/mi-empresa). */
  protected readonly estadoEmpresa = signal<EstadoSolicitud<null>>(cargando());
  /** `true` mientras se guardan los datos de la Empresa. */
  protected readonly guardandoEmpresa = signal(false);
  /** Logo vigente/seleccionado de la Empresa como data-URI (o `null`). */
  protected readonly logoEmpresa = signal<string | null>(null);
  /** Nombre del archivo de logo recien cargado (para la etiqueta accesible). */
  protected readonly logoEmpresaNombre = signal<string | null>(null);
  /** Mensaje de error de la carga del logo (tipo/tamano invalido). */
  protected readonly logoEmpresaError = signal<string | null>(null);

  /** Ambito legible del Usuario segun su tenant (Plataforma cuando es nulo). */
  protected readonly ambitoLegible = computed<string>(() => {
    const perfil = this.estado().datos;
    return perfil && perfil.tenantId ? 'Empresa' : 'Plataforma';
  });

  protected readonly formulario = this.fb.nonNullable.group(
    {
      passwordActual: ['', [Validators.required]],
      passwordNueva: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(255)]],
      confirmarPassword: ['', [Validators.required]],
    },
    { validators: coincidenPassword },
  );

  /** Formulario de datos de la propia Empresa (solo para admin_empresa). */
  protected readonly formularioEmpresa = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    emailContacto: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    telefono: ['', [telefonoValidator()]],
    direccionCalle: ['', [Validators.maxLength(255)]],
    direccionCiudad: ['', [Validators.maxLength(120)]],
    direccionEstado: ['', [Validators.maxLength(120)]],
    direccionCp: ['', [Validators.maxLength(10)]],
    direccionPais: ['', [Validators.maxLength(120)]],
  });

  constructor() {
    this.cargar();
    if (this.esAdminEmpresa) {
      this.cargarEmpresa();
    }
  }

  /** Carga el perfil del Usuario autenticado. */
  cargar(): void {
    this.estado.set(cargando());
    this.perfilService.obtenerPerfil().subscribe({
      next: (perfil) => this.estado.set(conDatos(perfil)),
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  /** Etiqueta humana de un rol (reemplaza guiones bajos por espacios). */
  protected etiquetaRol(rol: string): string {
    return rol.replace(/_/g, ' ');
  }

  /** Envia el cambio de contrasena propia. */
  protected cambiarPassword(): void {
    this.errorPassword.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    const v = this.formulario.getRawValue();
    this.perfilService
      .cambiarPassword({ passwordActual: v.passwordActual, passwordNueva: v.passwordNueva })
      .subscribe({
        next: () => {
          this.enviando.set(false);
          this.toast.exito('Contraseña actualizada');
          this.formulario.reset();
        },
        error: (e: HttpErrorResponse) => {
          this.enviando.set(false);
          if (e.status === 422) {
            // Contrasena actual incorrecta: se marca el campo y el formulario.
            const mensaje = 'La contraseña actual no es correcta.';
            this.formulario.controls.passwordActual.setErrors({ incorrecta: true });
            this.errorPassword.set(mensaje);
          } else if (e.status === 400) {
            this.errorPassword.set(
              'La nueva contraseña no cumple los requisitos. Debe tener al menos 8 caracteres.',
            );
          } else {
            this.errorPassword.set(mensajeDeError(e));
          }
        },
      });
  }

  // ---------------------------------------------------------------------------
  // Datos de mi empresa (admin_empresa) — GET/PUT /empresas/mi-empresa
  // ---------------------------------------------------------------------------

  /** Carga y prellena los datos de la propia Empresa (GET /empresas/mi-empresa). */
  cargarEmpresa(): void {
    this.estadoEmpresa.set(cargando());
    this.miEmpresaService.consultarMiEmpresa().subscribe({
      next: (empresa) => {
        this.formularioEmpresa.patchValue({
          nombre: empresa.nombre ?? '',
          emailContacto: empresa.emailContacto ?? '',
          telefono: empresa.telefono ?? '',
          direccionCalle: empresa.direccion?.calle ?? '',
          direccionCiudad: empresa.direccion?.ciudad ?? '',
          direccionEstado: empresa.direccion?.estado ?? '',
          direccionCp: empresa.direccion?.cp ?? '',
          direccionPais: empresa.direccion?.pais ?? '',
        });
        this.logoEmpresa.set(empresa.brandingLogo ?? null);
        this.estadoEmpresa.set(conDatos(null));
      },
      error: (e: HttpErrorResponse) => this.estadoEmpresa.set(conError(mensajeDeError(e))),
    });
  }

  /**
   * Carga el logo desde el input de archivo: valida tipo y tamano y lo lee como
   * data-URI (base64). Limite de 256 KB, holgado bajo el limite del backend.
   */
  protected seleccionarLogoEmpresa(evento: Event): void {
    this.logoEmpresaError.set(null);
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    if (!archivo) {
      return;
    }
    if (!TIPOS_LOGO_EMPRESA.includes(archivo.type)) {
      this.logoEmpresaError.set('Formato no admitido. Usa PNG, JPG, SVG o WebP.');
      input.value = '';
      return;
    }
    if (archivo.size > MAX_LOGO_EMPRESA_BYTES) {
      this.logoEmpresaError.set('El logo supera el tamano maximo de 256 KB.');
      input.value = '';
      return;
    }
    const lector = new FileReader();
    lector.onload = () => {
      this.logoEmpresa.set(String(lector.result));
      this.logoEmpresaNombre.set(archivo.name);
    };
    lector.onerror = () => this.logoEmpresaError.set('No se pudo leer el archivo del logo.');
    lector.readAsDataURL(archivo);
    input.value = '';
  }

  /**
   * Restringe el campo de telefono de la Empresa a digitos: elimina cualquier
   * caracter no numerico y limita a 10 digitos mientras el Usuario escribe (UX;
   * el patron de 10 digitos lo exige `telefonoValidator`).
   */
  protected soloDigitos(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const limpio = input.value.replace(/\D/g, '').slice(0, 10);
    if (limpio !== input.value) {
      input.value = limpio;
    }
    this.formularioEmpresa.controls.telefono.setValue(limpio);
  }

  /** Quita el logo cargado de la Empresa. */
  protected quitarLogoEmpresa(): void {
    this.logoEmpresa.set(null);
    this.logoEmpresaNombre.set(null);
    this.logoEmpresaError.set(null);
  }

  /**
   * Vuelca en el formulario de empresa la direccion elegida en el autocompletado
   * (Photon). Solo sobrescribe los campos que llegan con valor; los campos siguen
   * siendo editables tras el autollenado.
   */
  protected onDireccionEmpresa(direccion: DireccionAutocompletada): void {
    const parche: Partial<{
      direccionCalle: string;
      direccionCiudad: string;
      direccionEstado: string;
      direccionCp: string;
      direccionPais: string;
    }> = {};
    if (direccion.calle) parche.direccionCalle = direccion.calle;
    if (direccion.ciudad) parche.direccionCiudad = direccion.ciudad;
    if (direccion.estado) parche.direccionEstado = direccion.estado;
    if (direccion.cp) parche.direccionCp = direccion.cp;
    if (direccion.pais) parche.direccionPais = direccion.pais;
    this.formularioEmpresa.patchValue(parche);
  }

  /** Normaliza un campo de texto opcional: `null` cuando queda en blanco. */
  private opcional(valor: string): string | null {
    const limpio = valor.trim();
    return limpio ? limpio : null;
  }

  /** Envia la actualizacion de los datos de la propia Empresa (PUT /empresas/mi-empresa). */
  protected guardarEmpresa(): void {
    if (this.formularioEmpresa.invalid) {
      this.formularioEmpresa.markAllAsTouched();
      return;
    }
    this.guardandoEmpresa.set(true);
    const v = this.formularioEmpresa.getRawValue();
    const request: ActualizarMiEmpresaRequest = {
      nombre: v.nombre.trim(),
      emailContacto: v.emailContacto.trim(),
      telefono: this.opcional(v.telefono),
      direccionCalle: this.opcional(v.direccionCalle),
      direccionCiudad: this.opcional(v.direccionCiudad),
      direccionEstado: this.opcional(v.direccionEstado),
      direccionCp: this.opcional(v.direccionCp),
      direccionPais: this.opcional(v.direccionPais),
      logo: this.logoEmpresa(),
    };
    this.miEmpresaService.actualizarMiEmpresa(request).subscribe({
      next: (empresa) => {
        this.guardandoEmpresa.set(false);
        this.logoEmpresa.set(empresa.brandingLogo ?? this.logoEmpresa());
        this.toast.exito('Datos de empresa actualizados');
      },
      error: (e: HttpErrorResponse) => {
        this.guardandoEmpresa.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}

/** Tipos MIME de imagen admitidos para el logo de la Empresa. */
const TIPOS_LOGO_EMPRESA = ['image/png', 'image/jpeg', 'image/svg+xml', 'image/webp'];
/** Tamano maximo del archivo de logo en bytes (~256 KB). */
const MAX_LOGO_EMPRESA_BYTES = 256 * 1024;
