// =============================================================================
// Componente y servicio de dialogo de confirmacion (Req 54)
// -----------------------------------------------------------------------------
// Modal de confirmacion reutilizable para acciones sensibles (cambiar estado,
// offboarding, revocar sesiones, etc.). Se abre mediante ConfirmDialogService,
// que devuelve una Promesa<boolean> con la decision del Usuario.
// =============================================================================

import { Component, Injectable, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';

/** Datos de configuracion del dialogo de confirmacion. */
export interface DatosConfirmacion {
  titulo: string;
  mensaje: string;
  /** Texto del boton de confirmacion (por defecto "Confirmar"). */
  textoConfirmar?: string;
  /** Texto del boton de cancelacion (por defecto "Cancelar"). */
  textoCancelar?: string;
  /** Marca la accion como destructiva para resaltarla visualmente (Req 54.2). */
  destructiva?: boolean;
}

@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title id="confirm-dialog-titulo">{{ datos.titulo }}</h2>
    <mat-dialog-content>
      <p>{{ datos.mensaje }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton type="button" (click)="cerrar(false)">
        {{ datos.textoCancelar ?? 'Cancelar' }}
      </button>
      <button
        [matButton]="datos.destructiva ? 'filled' : 'tonal'"
        type="button"
        [class.ds-boton-destructivo]="datos.destructiva"
        cdkFocusInitial
        (click)="cerrar(true)"
      >
        {{ datos.textoConfirmar ?? 'Confirmar' }}
      </button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialog {
  protected readonly datos = inject<DatosConfirmacion>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ConfirmDialog, boolean>);

  protected cerrar(resultado: boolean): void {
    this.dialogRef.close(resultado);
  }
}

/** Servicio para abrir el dialogo de confirmacion y esperar la decision. */
@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  private readonly dialog = inject(MatDialog);

  /**
   * Abre el dialogo de confirmacion y resuelve con `true` si el Usuario confirma,
   * `false` en caso contrario (cancelar o cerrar).
   */
  async confirmar(datos: DatosConfirmacion): Promise<boolean> {
    const ref = this.dialog.open<ConfirmDialog, DatosConfirmacion, boolean>(ConfirmDialog, {
      data: datos,
      width: 'min(480px, 92vw)',
      autoFocus: true,
      restoreFocus: true,
      ariaLabelledBy: 'confirm-dialog-titulo',
    });
    return (await firstValueFrom(ref.afterClosed())) ?? false;
  }
}
