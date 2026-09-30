// =============================================================================
// Dialogo de alta/edicion de Proveedor (Compras) — modal premium animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de la vista de Proveedores por un MODAL
// (MatDialog) consistente con el resto de la plataforma (form shell ds-form). Sirve
// para ALTA (sin proveedor en los datos) y EDICION (con proveedor). Captura la
// identificacion (razon social, RFC), contacto (correo, telefono, persona de
// contacto), condiciones comerciales (regimen fiscal, dias de credito) y el
// domicilio fiscal (calle, ciudad, estado, CP). Al guardar hace POST/PUT via
// ComprasService y cierra devolviendo el Proveedor resultante.
//
// Reglas replicadas del backend (Req 29): nombre y RFC obligatorios; al menos un
// dato de contacto (correo o telefono); el backend reimpone formato (422) y la
// unicidad del RFC entre activos (409).
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError, erroresDeCampo } from '../../../core/services/error-mensajes';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';

import { ComprasService } from '../services/compras.service';
import { Proveedor } from '../models/compras.models';

/**
 * Datos de entrada del dialogo. `proveedor` presente => modo EDICION (prellena y
 * hace PUT); ausente => modo ALTA (form vacio y POST).
 */
export interface ProveedorFormDialogData {
  proveedor?: Proveedor;
}

@Component({
  selector: 'app-proveedor-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './proveedor-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './proveedor-form-dialog.scss',
})
export class ProveedorFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ComprasService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly toast = inject(NotificacionesService);
  private readonly dialogRef = inject(MatDialogRef<ProveedorFormDialog, Proveedor>);
  private readonly data = inject<ProveedorFormDialogData>(MAT_DIALOG_DATA);

  /** Proveedor en edicion (o `null` en alta). */
  private readonly proveedor = this.data?.proveedor ?? null;
  /** `true` cuando el dialogo edita un Proveedor existente. */
  protected readonly esEdicion = !!this.proveedor;

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly titulo = computed(() =>
    this.esEdicion ? 'Editar proveedor' : 'Nuevo proveedor',
  );
  protected readonly subtitulo = computed(() =>
    this.esEdicion
      ? 'Actualiza la identificación, el contacto y los datos fiscales del proveedor.'
      : 'Registra un proveedor con su razón social, RFC y al menos un medio de contacto.',
  );

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    rfc: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(13)]],
    email: ['', [Validators.email, Validators.maxLength(320)]],
    telefono: ['', [Validators.maxLength(15)]],
    personaContacto: ['', [Validators.maxLength(200)]],
    regimenFiscal: ['', [Validators.maxLength(10)]],
    diasCredito: [null as number | null, [Validators.min(0), Validators.max(365)]],
    domicilioCalle: ['', [Validators.maxLength(300)]],
    domicilioCiudad: ['', [Validators.maxLength(150)]],
    domicilioEstado: ['', [Validators.maxLength(150)]],
    codigoPostal: ['', [Validators.pattern(/^[0-9]{5}$/)]],
  });

  constructor() {
    if (this.proveedor) {
      const p = this.proveedor;
      this.form.reset({
        nombre: p.nombre,
        rfc: p.rfc,
        email: p.email ?? '',
        telefono: p.telefono ?? '',
        personaContacto: p.personaContacto ?? '',
        regimenFiscal: p.regimenFiscal ?? '',
        diasCredito: p.diasCredito,
        domicilioCalle: p.domicilioCalle ?? '',
        domicilioCiudad: p.domicilioCiudad ?? '',
        domicilioEstado: p.domicilioEstado ?? '',
        codigoPostal: p.codigoPostal ?? '',
      });
    }
  }

  /** Normaliza el RFC mientras se escribe: mayúsculas, solo [A-ZÑ&0-9], máx 13. */
  protected normalizarRfc(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const limpio = input.value
      .toUpperCase()
      .replace(/[^A-ZÑ&0-9]/g, '')
      .slice(0, 13);
    if (limpio !== input.value) {
      input.value = limpio;
    }
    this.form.controls.rfc.setValue(limpio);
  }

  /** Restringe a dígitos (hasta el máximo indicado) para teléfono y CP. */
  protected soloDigitos(evento: Event, control: 'telefono' | 'codigoPostal', max: number): void {
    const input = evento.target as HTMLInputElement;
    const limpio = input.value.replace(/\D/g, '').slice(0, max);
    if (limpio !== input.value) {
      input.value = limpio;
    }
    this.form.controls[control].setValue(limpio);
  }

  /** `null` cuando el texto queda en blanco tras recortar. */
  private opcional(valor: string | null): string | null {
    const limpio = (valor ?? '').trim();
    return limpio ? limpio : null;
  }

  /** Persiste el alta o la edición según el modo (Req 29). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const email = this.opcional(v.email);
    const telefono = this.opcional(v.telefono);
    // Regla del backend: al menos un dato de contacto (correo o teléfono).
    if (!email && !telefono) {
      this.form.controls.email.setErrors({ contactoRequerido: true });
      this.error.set('Captura al menos un dato de contacto: correo o teléfono.');
      return;
    }

    const request = {
      nombre: v.nombre.trim(),
      rfc: v.rfc.trim().toUpperCase(),
      email,
      telefono,
      personaContacto: this.opcional(v.personaContacto),
      regimenFiscal: this.opcional(v.regimenFiscal)?.toUpperCase() ?? null,
      diasCredito: v.diasCredito ?? null,
      domicilioCalle: this.opcional(v.domicilioCalle),
      domicilioCiudad: this.opcional(v.domicilioCiudad),
      domicilioEstado: this.opcional(v.domicilioEstado),
      codigoPostal: this.opcional(v.codigoPostal),
    };
    const id = this.proveedor?.id;
    this.guardando.set(true);
    const peticion = id
      ? this.service.actualizarProveedor(id, request)
      : this.service.crearProveedor(request);

    this.overlay
      .ejecutar(peticion, {
        tipo: id ? 'guardar' : 'crear',
        textoProceso: id ? 'Guardando proveedor…' : 'Creando proveedor…',
        textoExito: id ? 'Proveedor guardado' : 'Proveedor creado',
      })
      .subscribe({
        next: (proveedor) => {
          this.guardando.set(false);
          this.dialogRef.close(proveedor);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          if (e.status === 409) {
            this.form.controls.rfc.setErrors({ duplicado: true });
            this.error.set('Ya existe un proveedor activo con ese RFC.');
            return;
          }
          if (e.status === 422) {
            const campos = erroresDeCampo(e);
            this.error.set(
              campos.length ? campos.map((c) => c.mensaje).join(' ') : mensajeDeError(e),
            );
            return;
          }
          this.error.set(mensajeDeError(e));
        },
      });
  }

  /** Cancela sin guardar. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
