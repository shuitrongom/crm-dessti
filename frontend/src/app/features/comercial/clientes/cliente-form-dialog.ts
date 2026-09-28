// =============================================================================
// Dialogo de alta/edicion de Cliente (Comercial) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de la vista de Clientes por un MODAL (MatDialog)
// consistente con el resto de la plataforma (mismo "form shell" ds-form* y el
// patron de dialogo de plataforma/empresas). Sirve tanto para ALTA (sin cliente
// en los datos) como para EDICION (con cliente). Reutiliza el FormGroup, las
// validaciones (RFC mexicano, telefonos de 10 digitos, al menos un contacto) y
// el autocompletado de direccion. El backend sigue siendo la autoridad: el 409
// de RFC duplicado se marca inline en el campo RFC.
//
// Al guardar hace POST/PUT via ClientesService y cierra el dialogo devolviendo
// el Cliente resultante (o `undefined` al cancelar), para que la vista recargue.
// Accesible (labels, foco, aria) y responsive; solo tokens del Sistema de Diseno.
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
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import {
  AddressAutocomplete,
  DireccionAutocompletada,
} from '../../../shared/components/address-autocomplete/address-autocomplete';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { rfcValidator } from '../../../shared/validators/rfc.validator';

import { ClientesService } from '../services/clientes.service';
import {
  Cliente,
  ClienteRequest,
  TIPOS_PERSONA,
  TipoPersona,
} from '../models/comercial.models';

/** Longitud maxima del campo de notas (coincide con la cota del backend). */
const MAX_NOTAS = 1000;

/**
 * Datos de entrada del dialogo. `cliente` presente => modo EDICION (prellena y
 * hace PUT); ausente => modo ALTA (form vacio y POST).
 */
export interface ClienteFormDialogData {
  cliente?: Cliente;
}

/**
 * Validador de nivel formulario (Req 5): exige al menos un dato de contacto
 * (correo o telefono). Cuando ambos estan vacios devuelve `{ contacto: true }`;
 * espeja la regla del backend (422) para retroalimentacion inmediata.
 */
function alMenosUnContacto(control: AbstractControl): ValidationErrors | null {
  const email = String(control.get('email')?.value ?? '').trim();
  const telefono = String(control.get('telefono')?.value ?? '').trim();
  return email.length > 0 || telefono.length > 0 ? null : { contacto: true };
}

@Component({
  selector: 'app-cliente-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    AddressAutocomplete,
  ],
  templateUrl: './cliente-form-dialog.html',
  styleUrl: './cliente-form-dialog.scss',
})
export class ClienteFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ClientesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<ClienteFormDialog, Cliente>);
  private readonly data = inject<ClienteFormDialogData>(MAT_DIALOG_DATA);

  /** Cliente en edicion (o `null` en alta). */
  private readonly cliente = this.data?.cliente ?? null;
  /** `true` cuando el dialogo edita un Cliente existente. */
  protected readonly esEdicion = !!this.cliente;

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Longitud maxima del campo de notas (expuesta al contador de la plantilla). */
  protected readonly maxNotas = MAX_NOTAS;
  /** Opciones del selector de tipo de persona. */
  protected readonly tiposPersona = TIPOS_PERSONA;

  /** Titulo del dialogo segun el modo. */
  protected readonly titulo = computed(() => (this.esEdicion ? 'Editar cliente' : 'Nuevo cliente'));
  /** Subtitulo contextual del dialogo segun el modo. */
  protected readonly subtitulo = computed(() =>
    this.esEdicion
      ? 'Actualiza los datos del cliente y su información de contacto.'
      : 'Registra un nuevo cliente en tu cartera con sus datos de contacto.',
  );

  protected readonly form = this.fb.nonNullable.group(
    {
      nombre: ['', [Validators.required, Validators.maxLength(200)]],
      nombreComercial: ['', [Validators.maxLength(200)]],
      tipoPersona: ['' as TipoPersona | '', []],
      rfc: ['', [Validators.required, Validators.maxLength(13), rfcValidator()]],
      email: ['', [Validators.maxLength(254), Validators.email]],
      telefono: ['', [Validators.pattern(/^\d{10}$/)]],
      telefonoAdicional: ['', [Validators.pattern(/^\d{10}$/)]],
      direccionCalle: ['', [Validators.maxLength(200)]],
      direccionCiudad: ['', [Validators.maxLength(120)]],
      direccionEstado: ['', [Validators.maxLength(120)]],
      direccionCp: ['', [Validators.maxLength(10), Validators.pattern(/^\d*$/)]],
      direccionPais: ['', [Validators.maxLength(80)]],
      notas: ['', [Validators.maxLength(MAX_NOTAS)]],
    },
    { validators: alMenosUnContacto },
  );

  /** Longitud actual de las notas para el contador accesible de la plantilla. */
  protected readonly longitudNotas = computed(() => this.notasValor().length);
  private readonly notasValor = signal('');

  constructor() {
    this.form.controls.notas.valueChanges.subscribe((v) => this.notasValor.set(v ?? ''));
    if (this.cliente) {
      this.form.reset(this.valoresDe(this.cliente));
    }
  }

  /**
   * Vuelca en el formulario la direccion elegida en el autocompletado (Photon).
   * Solo sobrescribe los campos que llegan con valor, para no borrar datos
   * capturados manualmente; los campos siguen siendo editables tras el autollenado.
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
    this.form.patchValue(parche);
  }

  /** Persiste el alta o la edicion segun el modo (Req 5.1, 5.4). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const request = this.construirRequest();
    const id = this.cliente?.id;
    this.guardando.set(true);
    const peticion = id ? this.service.actualizar(id, request) : this.service.crear(request);
    this.overlay
      .ejecutar(peticion, {
        tipo: 'guardar',
        textoProceso: id ? 'Guardando cambios…' : 'Creando cliente…',
        textoExito: id ? 'Cliente actualizado' : 'Cliente creado',
      })
      .subscribe({
        next: (cliente) => {
          this.guardando.set(false);
          this.dialogRef.close(cliente);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.manejarError(e);
        },
      });
  }

  /** Cancela sin guardar. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }

  /** Proyecta un Cliente a los valores del formulario (edicion). */
  private valoresDe(cliente: Cliente): ReturnType<ClienteFormDialog['form']['getRawValue']> {
    return {
      nombre: cliente.nombre,
      nombreComercial: cliente.nombreComercial ?? '',
      tipoPersona: cliente.tipoPersona ?? '',
      rfc: cliente.rfc,
      email: cliente.email ?? '',
      telefono: cliente.telefono ?? '',
      telefonoAdicional: cliente.telefonoAdicional ?? '',
      direccionCalle: cliente.direccionCalle ?? '',
      direccionCiudad: cliente.direccionCiudad ?? '',
      direccionEstado: cliente.direccionEstado ?? '',
      direccionCp: cliente.direccionCp ?? '',
      direccionPais: cliente.direccionPais ?? '',
      notas: cliente.notas ?? '',
    };
  }

  /** Normaliza un texto opcional: `null` cuando queda en blanco. */
  private opcional(valor: string): string | null {
    const limpio = valor.trim();
    return limpio.length > 0 ? limpio : null;
  }

  /** Construye el cuerpo de la peticion a partir del formulario. */
  private construirRequest(): ClienteRequest {
    const v = this.form.getRawValue();
    return {
      nombre: v.nombre.trim(),
      rfc: v.rfc.trim().toUpperCase(),
      email: this.opcional(v.email),
      telefono: this.opcional(v.telefono),
      nombreComercial: this.opcional(v.nombreComercial),
      tipoPersona: v.tipoPersona ? (v.tipoPersona as TipoPersona) : null,
      telefonoAdicional: this.opcional(v.telefonoAdicional),
      direccionCalle: this.opcional(v.direccionCalle),
      direccionCiudad: this.opcional(v.direccionCiudad),
      direccionEstado: this.opcional(v.direccionEstado),
      direccionCp: this.opcional(v.direccionCp),
      direccionPais: this.opcional(v.direccionPais),
      notas: this.opcional(v.notas),
    };
  }

  /**
   * Traduce el error de guardado. El 409 de RFC duplicado se coloca como error
   * inline en el campo RFC; el resto se muestra como nota en el propio dialogo.
   */
  private manejarError(e: HttpErrorResponse): void {
    if (e.status === 409) {
      this.form.controls.rfc.setErrors({ duplicado: true });
      this.form.controls.rfc.markAsTouched();
      this.error.set('Ya existe un cliente con ese RFC.');
      return;
    }
    this.error.set(mensajeDeError(e));
  }
}
