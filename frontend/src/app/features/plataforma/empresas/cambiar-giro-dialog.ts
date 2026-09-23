// =============================================================================
// Dialogo de cambio de Giro de una Empresa (super_admin) (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Permite al super_admin reasignar el Giro (vertical de negocio) de una Empresa.
// Muestra el giro actual (por su nombre, NUNCA el UUID) y un `mat-select` de los
// giros ACTIVOS por nombre visible, con el actual preseleccionado. Al confirmar
// hace PUT /empresas/{id}/giro con { giroId } y:
//   - 200 -> cierra devolviendo la Empresa actualizada (la vista refresca).
//   - 422 -> giro invalido/inactivo o datos del vertical actual: muestra el
//            mensaje del backend SIN cerrar el dialogo (el Usuario lo lee).
//   - 404 -> Empresa inexistente: muestra el mensaje del backend.
// La lista de giros activos se recibe por MAT_DIALOG_DATA (ya cargada por la
// vista) para no duplicar peticiones. Accesible (labels, foco, aria) y
// responsive; solo tokens del Sistema de Diseno.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { EmpresasService } from '../services/empresas.service';
import { Empresa, Giro } from '../models/plataforma.models';
import { mensajeDeError } from '../../../core/services/error-mensajes';

/**
 * Datos de entrada del dialogo: la Empresa cuyo giro se cambia y el catalogo de
 * giros ACTIVOS (ya cargado por la vista, para no repetir la peticion).
 */
export interface CambiarGiroDialogData {
  empresa: Empresa;
  girosActivos: Giro[];
}

@Component({
  selector: 'app-cambiar-giro-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './cambiar-giro-dialog.html',
  styleUrl: './cambiar-giro-dialog.scss',
})
export class CambiarGiroDialog {
  private readonly fb = inject(FormBuilder);
  private readonly empresasService = inject(EmpresasService);
  private readonly dialogRef = inject(MatDialogRef<CambiarGiroDialog, Empresa>);
  protected readonly data = inject<CambiarGiroDialogData>(MAT_DIALOG_DATA);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Giros activos disponibles para asignar (por nombre, sin UUID). */
  protected readonly girosActivos = this.data.girosActivos;

  protected readonly formulario = this.fb.nonNullable.group({
    giroId: [this.data.empresa.giroId ?? '', [Validators.required]],
  });

  /**
   * Nombre visible del giro ACTUAL de la Empresa, o un marcador neutro cuando el
   * giro no se encuentra en el catalogo activo (nunca se muestra el UUID).
   */
  protected readonly giroActual = computed(() => {
    const actual = this.girosActivos.find((g) => g.id === this.data.empresa.giroId);
    return actual?.nombreVisible ?? '(sin giro)';
  });

  /**
   * Respaldo reactivo del giro elegido: un `computed` sobre el FormControl no
   * reacciona a sus cambios, asi que se actualiza desde el handler `cambiarGiro`.
   */
  private readonly giroIdSeleccionado = signal<string>(this.data.empresa.giroId ?? '');

  /** `true` cuando no se selecciono un giro distinto del actual. */
  protected readonly sinCambio = computed(
    () => this.giroIdSeleccionado() === (this.data.empresa.giroId ?? ''),
  );

  /** Reacciona a la seleccion de un giro en el `mat-select`. */
  protected cambiarGiro(id: string): void {
    this.formulario.controls.giroId.setValue(id);
    this.giroIdSeleccionado.set(id);
  }

  /** Confirma el cambio de giro contra el backend (PUT /empresas/{id}/giro). */
  protected guardar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    const giroId = this.formulario.controls.giroId.value;
    this.guardando.set(true);
    this.empresasService.cambiarGiro(this.data.empresa.id, giroId).subscribe({
      next: (empresa) => {
        this.guardando.set(false);
        this.dialogRef.close(empresa);
      },
      error: (e: HttpErrorResponse) => {
        // El backend impone las reglas (giro activo, datos del vertical): se
        // muestra su mensaje y el dialogo permanece abierto para que se lea.
        this.guardando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  /** Cancela el cambio de giro. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
