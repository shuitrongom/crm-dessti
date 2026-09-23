// =============================================================================
// Dialogo de edicion de Empresa (super_admin) (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Formulario reactivo enterprise para editar los datos DESCRIPTIVOS y FISCALES
// de una Empresa existente. Reutiliza el mismo layout seccionado del alta
// (Identidad, Contacto, Direccion, Logo) y los mismos validadores (RFC, email,
// logo <= 256 KB) para mantener el codigo DRY y la experiencia consistente.
//
// A diferencia del alta, NO gestiona giro/plan/estado ni el administrador
// inicial: esos flujos viven en otras pantallas (activar/suspender, planes y
// suscripciones). El backend expone PUT /empresas/{id} que edita SOLO estos
// campos; por eso el formulario no ofrece esos selectores.
//
// El formulario se prellena con los valores actuales de la Empresa recibida por
// MAT_DIALOG_DATA. Al enviar hace PUT /empresas/{id} y mapea:
//   - 200 -> cierra devolviendo la Empresa actualizada (la vista refresca).
//   - 409 -> error a nivel de campo RFC ("Ya existe una empresa con ese RFC").
//   - 400 -> validacion (mensaje generico de negocio).
//   - 404 -> error de no encontrada.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { EmpresasService } from '../services/empresas.service';
import { Empresa } from '../models/plataforma.models';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { rfcValidator } from '../../../shared/validators/rfc.validator';
import { telefonoValidator } from '../../../shared/validators/telefono.validator';
import {
  AddressAutocomplete,
  DireccionAutocompletada,
} from '../../../shared/components/address-autocomplete/address-autocomplete';

/** Datos de entrada del dialogo: la Empresa a editar (para prellenar). */
export interface EditarEmpresaDialogData {
  empresa: Empresa;
}

/** Tipos MIME de imagen admitidos para el logo del branding. */
const TIPOS_LOGO = ['image/png', 'image/jpeg', 'image/svg+xml', 'image/webp'];
/** Tamano maximo del archivo de logo en bytes (~256 KB). */
const MAX_LOGO_BYTES = 256 * 1024;

@Component({
  selector: 'app-editar-empresa-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    AddressAutocomplete,
  ],
  templateUrl: './editar-empresa-dialog.html',
  styleUrl: './editar-empresa-dialog.scss',
})
export class EditarEmpresaDialog {
  private readonly fb = inject(FormBuilder);
  private readonly empresasService = inject(EmpresasService);
  private readonly dialogRef = inject(MatDialogRef<EditarEmpresaDialog, Empresa>);
  private readonly data = inject<EditarEmpresaDialogData>(MAT_DIALOG_DATA);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Logo vigente/seleccionado como data-URI (o `null` si se quito). */
  protected readonly logo = signal<string | null>(null);
  /** Nombre del archivo de logo recien cargado (para la etiqueta accesible). */
  protected readonly logoNombre = signal<string | null>(null);
  /** Mensaje de error de la carga del logo (tipo/tamano invalido). */
  protected readonly logoError = signal<string | null>(null);

  protected readonly formulario = this.fb.nonNullable.group({
    // Identidad
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    nombreComercial: ['', [Validators.maxLength(200)]],
    rfc: ['', [Validators.required, rfcValidator(), Validators.maxLength(13)]],
    // Contacto (el correo es OBLIGATORIO: lo exige el backend).
    emailContacto: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    telefono: ['', [telefonoValidator()]],
    sitioWeb: ['', [Validators.maxLength(255)]],
    // Direccion
    direccionCalle: ['', [Validators.maxLength(255)]],
    direccionCiudad: ['', [Validators.maxLength(120)]],
    direccionEstado: ['', [Validators.maxLength(120)]],
    direccionCp: ['', [Validators.maxLength(10)]],
    direccionPais: ['', [Validators.maxLength(120)]],
    notas: ['', [Validators.maxLength(2000)]],
  });

  constructor() {
    this.prellenar(this.data.empresa);
  }

  /** Prellena el formulario y el logo con los valores actuales de la Empresa. */
  private prellenar(empresa: Empresa): void {
    this.formulario.patchValue({
      nombre: empresa.nombre ?? '',
      nombreComercial: empresa.nombreComercial ?? '',
      rfc: empresa.rfc ?? '',
      emailContacto: empresa.emailContacto ?? '',
      telefono: empresa.telefono ?? '',
      sitioWeb: empresa.sitioWeb ?? '',
      direccionCalle: empresa.direccion?.calle ?? '',
      direccionCiudad: empresa.direccion?.ciudad ?? '',
      direccionEstado: empresa.direccion?.estado ?? '',
      direccionCp: empresa.direccion?.cp ?? '',
      direccionPais: empresa.direccion?.pais ?? '',
      notas: empresa.notas ?? '',
    });
    this.logo.set(empresa.brandingLogo ?? null);
  }

  /**
   * Carga el logo desde el input de archivo: valida tipo y tamano, y lo lee como
   * data-URI (base64). El limite de 256 KB deja margen holgado por debajo del
   * limite de 1 MiB de texto del backend tras codificar en base64.
   */
  protected seleccionarLogo(evento: Event): void {
    this.logoError.set(null);
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    if (!archivo) {
      return;
    }
    if (!TIPOS_LOGO.includes(archivo.type)) {
      this.logoError.set('Formato no admitido. Usa PNG, JPG, SVG o WebP.');
      input.value = '';
      return;
    }
    if (archivo.size > MAX_LOGO_BYTES) {
      this.logoError.set('El logo supera el tamano maximo de 256 KB.');
      input.value = '';
      return;
    }
    const lector = new FileReader();
    lector.onload = () => {
      this.logo.set(String(lector.result));
      this.logoNombre.set(archivo.name);
    };
    lector.onerror = () => this.logoError.set('No se pudo leer el archivo del logo.');
    lector.readAsDataURL(archivo);
    input.value = '';
  }

  /** Quita el logo cargado. */
  protected quitarLogo(): void {
    this.logo.set(null);
    this.logoNombre.set(null);
    this.logoError.set(null);
  }

  /**
   * Restringe el campo de telefono a digitos: elimina cualquier caracter no
   * numerico y limita a 10 digitos mientras el Usuario escribe (UX; el patron
   * de 10 digitos lo exige `telefonoValidator`).
   */
  protected soloDigitos(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const limpio = input.value.replace(/\D/g, '').slice(0, 10);
    if (limpio !== input.value) {
      input.value = limpio;
    }
    this.formulario.controls.telefono.setValue(limpio);
  }

  /**
   * Normaliza el RFC mientras se escribe: mayusculas, solo [A-ZÑ&0-9] y tope de
   * 13 caracteres (persona fisica). Refuerza el `maxlength` y el patron del RFC.
   */
  protected normalizarRfc(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const limpio = input.value
      .toUpperCase()
      .replace(/[^A-ZÑ&0-9]/g, '')
      .slice(0, 13);
    if (limpio !== input.value) {
      input.value = limpio;
    }
    this.formulario.controls.rfc.setValue(limpio);
  }

  /**
   * Vuelca en el formulario la direccion elegida en el autocompletado (Photon).
   * Solo sobrescribe los campos que llegan con valor; los campos siguen siendo
   * editables tras el autollenado.
   */
  protected onDireccion(direccion: DireccionAutocompletada): void {
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
    this.formulario.patchValue(parche);
  }

  /** Normaliza un campo de texto opcional: `null` cuando queda en blanco. */
  private opcional(valor: string): string | null {
    const limpio = valor.trim();
    return limpio ? limpio : null;
  }

  /** Envia la actualizacion de la Empresa (PUT /empresas/{id}). */
  protected guardar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const v = this.formulario.getRawValue();
    this.empresasService
      .actualizarEmpresa(this.data.empresa.id, {
        nombre: v.nombre.trim(),
        rfc: v.rfc.trim().toUpperCase(),
        emailContacto: v.emailContacto.trim(),
        nombreComercial: this.opcional(v.nombreComercial),
        telefono: this.opcional(v.telefono),
        sitioWeb: this.opcional(v.sitioWeb),
        direccionCalle: this.opcional(v.direccionCalle),
        direccionCiudad: this.opcional(v.direccionCiudad),
        direccionEstado: this.opcional(v.direccionEstado),
        direccionCp: this.opcional(v.direccionCp),
        direccionPais: this.opcional(v.direccionPais),
        notas: this.opcional(v.notas),
        logo: this.logo(),
      })
      .subscribe({
        next: (empresa) => {
          this.guardando.set(false);
          this.dialogRef.close(empresa);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          if (e.status === 409) {
            // RFC duplicado de otra empresa: se marca el campo y el formulario.
            this.formulario.controls.rfc.setErrors({ duplicado: true });
            this.error.set('Ya existe una empresa con ese RFC.');
          } else {
            this.error.set(mensajeDeError(e));
          }
        },
      });
  }

  /** Cancela la edicion. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
