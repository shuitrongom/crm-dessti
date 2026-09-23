// =============================================================================
// Dialogo "Enviar cotizacion por correo" (Req 6)
// -----------------------------------------------------------------------------
// Pide (o confirma) el correo destino de la cotizacion antes de enviarla. Se
// abre con EnviarCorreoDialogService.pedir(datos) y resuelve con el correo a
// usar (string) o null si el Usuario cancela. Prellena con el correo del cliente
// cuando existe; el campo es editable para corregirlo o indicar otro.
// =============================================================================

import { Component, Injectable, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';

/** Datos de apertura del dialogo de envio por correo. */
export interface DatosEnviarCorreo {
  /** Folio de la cotizacion para contextualizar el mensaje. */
  folio: string | null;
  /** Correo del cliente para prellenar el campo (puede faltar). */
  correoCliente: string | null;
}

@Component({
  selector: 'app-enviar-correo-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title id="enviar-correo-titulo">Enviar cotizacion por correo</h2>
    <mat-dialog-content>
      <p class="enviar-correo__intro">
        Se enviara la cotizacion @if (datos.folio) {
          <strong>{{ datos.folio }}</strong>
        } al correo indicado.
      </p>
      <form [formGroup]="form" (ngSubmit)="enviar()" novalidate>
        <mat-form-field appearance="outline" class="enviar-correo__campo">
          <mat-label>Correo destino</mat-label>
          <input
            matInput
            type="email"
            formControlName="email"
            autocomplete="email"
            placeholder="cliente@empresa.com"
            required
          />
          @if (form.controls.email.hasError('required') && form.controls.email.touched) {
            <mat-error>Indica un correo para enviar la cotizacion.</mat-error>
          }
          @if (form.controls.email.hasError('email') && form.controls.email.touched) {
            <mat-error>El correo no tiene un formato valido.</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton type="button" (click)="cancelar()">Cancelar</button>
      <button matButton="filled" type="button" cdkFocusInitial (click)="enviar()">Enviar</button>
    </mat-dialog-actions>
  `,
  styles: `
    .enviar-correo__intro {
      margin: 0 0 var(--ds-space-3);
      color: var(--ds-color-text-muted);
    }
    .enviar-correo__campo {
      width: 100%;
    }
  `,
})
export class EnviarCorreoDialog {
  protected readonly datos = inject<DatosEnviarCorreo>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<EnviarCorreoDialog, string | null>);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    email: [this.datos.correoCliente ?? '', [Validators.required, Validators.email]],
  });

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.dialogRef.close(this.form.controls.email.value.trim());
  }

  protected cancelar(): void {
    this.dialogRef.close(null);
  }
}

/** Servicio para abrir el dialogo de envio por correo y esperar el correo elegido. */
@Injectable({ providedIn: 'root' })
export class EnviarCorreoDialogService {
  private readonly dialog = inject(MatDialog);

  /**
   * Abre el dialogo y resuelve con el correo a usar (string) o null si el Usuario
   * cancela o cierra el dialogo.
   */
  async pedir(datos: DatosEnviarCorreo): Promise<string | null> {
    const ref = this.dialog.open<EnviarCorreoDialog, DatosEnviarCorreo, string | null>(
      EnviarCorreoDialog,
      {
        data: datos,
        width: 'min(480px, 92vw)',
        autoFocus: true,
        restoreFocus: true,
        ariaLabelledBy: 'enviar-correo-titulo',
      },
    );
    return (await firstValueFrom(ref.afterClosed())) ?? null;
  }
}
