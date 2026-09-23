// =============================================================================
// Dialogo de alta de Giro (super_admin) (Req 9)
// -----------------------------------------------------------------------------
// Formulario reactivo para crear un Giro (vertical de negocio). Solo es de alta:
// el backend no expone edicion de Giros, por lo que no hay modo edicion. La
// `clave` debe ser kebab-case (minusculas, numeros y guiones); se normaliza en
// la entrada (trim + minusculas) y se valida con un patron. Ante error se
// muestra el mensaje del backend (409 clave duplicada, 422 invalido).
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { ActualizarGiroRequest, GirosService } from '../services/giros.service';
import { Giro } from '../models/plataforma.models';
import { mensajeDeError } from '../../../core/services/error-mensajes';

/** Patron de clave kebab-case: minusculas/numeros separados por guiones. */
const PATRON_CLAVE = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

/** Datos de entrada del dialogo: el Giro a editar, o ausente para alta. */
export interface DatosGiroDialog {
  giro?: Giro | null;
}

@Component({
  selector: 'app-giro-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './giro-dialog.html',
  styleUrl: './giro-dialog.scss',
})
export class GiroDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(GirosService);
  private readonly dialogRef = inject(MatDialogRef<GiroDialog, Giro>);
  private readonly datos = inject<DatosGiroDialog>(MAT_DIALOG_DATA, { optional: true });

  /** Modo edicion cuando se recibio un Giro; en alta la clave es editable. */
  protected readonly esEdicion = !!this.datos?.giro;

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  /**
   * Giro recien creado: cuando toma valor, el dialogo cambia a un segundo estado
   * (panel informativo de completitud) en lugar de cerrarse en silencio, para
   * comunicar al super_admin que el giro nace "Base" (solo modulos base) y que
   * sus reglas de negocio especificas aun no estan programadas.
   */
  protected readonly creado = signal<Giro | null>(null);

  protected readonly formulario = this.fb.nonNullable.group({
    clave: [this.datos?.giro?.clave ?? '', [Validators.required, Validators.maxLength(60), Validators.pattern(PATRON_CLAVE)]],
    nombreVisible: [this.datos?.giro?.nombreVisible ?? '', [Validators.required, Validators.maxLength(150)]],
    descripcion: [this.datos?.giro?.descripcion ?? '', [Validators.maxLength(500)]],
  });

  /** Normaliza la clave al escribir: recorta espacios y pasa a minusculas. */
  protected normalizarClave(): void {
    const control = this.formulario.controls.clave;
    const normalizado = control.value.trim().toLowerCase();
    if (normalizado !== control.value) {
      control.setValue(normalizado, { emitEvent: false });
    }
  }

  /** Envia el alta del Giro. */
  protected guardar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const v = this.formulario.getRawValue();

    // Edicion: solo nombre visible y descripcion (la clave es inmutable). Al
    // exito cierra devolviendo el Giro actualizado (sin panel informativo).
    if (this.esEdicion && this.datos?.giro) {
      const cambios: ActualizarGiroRequest = {
        nombreVisible: v.nombreVisible,
        descripcion: v.descripcion ? v.descripcion : null,
      };
      this.service.actualizar(this.datos.giro.id, cambios).subscribe({
        next: (giro) => {
          this.guardando.set(false);
          this.dialogRef.close(giro);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
      return;
    }

    this.service
      .crear({
        clave: v.clave,
        nombreVisible: v.nombreVisible,
        descripcion: v.descripcion ? v.descripcion : null,
      })
      .subscribe({
        next: (giro) => {
          this.guardando.set(false);
          // No se cierra en silencio: se muestra el panel informativo de
          // completitud con el giro creado.
          this.creado.set(giro);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
  }

  /** Cierra el dialogo tras el panel informativo, devolviendo el giro creado. */
  protected cerrarConExito(): void {
    this.dialogRef.close(this.creado() ?? undefined);
  }

  /** Cancela el alta. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
