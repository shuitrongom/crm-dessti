// =============================================================================
// Servicio de notificaciones toast (Req 56)
// -----------------------------------------------------------------------------
// Envoltura sobre MatSnackBar con mensajes de negocio en espanol y sin detalle
// tecnico. Estandariza duracion, posicion y estilos (exito / error / info) para
// toda la aplicacion.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { MatSnackBar, MatSnackBarConfig } from '@angular/material/snack-bar';

@Injectable({ providedIn: 'root' })
export class NotificacionesService {
  private readonly snackBar = inject(MatSnackBar);

  private config(clasePanel: string, duracionMs: number): MatSnackBarConfig {
    return {
      duration: duracionMs,
      horizontalPosition: 'center',
      verticalPosition: 'bottom',
      panelClass: ['ds-toast', clasePanel],
    };
  }

  /** Muestra una notificacion de exito (operacion completada). */
  exito(mensaje: string): void {
    this.snackBar.open(mensaje, 'Cerrar', this.config('ds-toast--exito', 4000));
  }

  /** Muestra una notificacion de error de negocio (Req 56). */
  error(mensaje: string): void {
    this.snackBar.open(mensaje, 'Cerrar', this.config('ds-toast--error', 7000));
  }

  /** Muestra una notificacion informativa. */
  info(mensaje: string): void {
    this.snackBar.open(mensaje, 'Cerrar', this.config('ds-toast--info', 5000));
  }
}
