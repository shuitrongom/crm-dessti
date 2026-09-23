// =============================================================================
// Dialogo para asignar / cambiar el Plan de una Empresa (super_admin)
// (plan-suscripcion-empresa-super-admin) (Req 6)
// -----------------------------------------------------------------------------
// Permite al super_admin asignar o cambiar el Plan de una Empresa creando una
// Suscripcion (POST /suscripciones). Presenta un `mat-select` de Planes por
// NOMBRE (NUNCA el UUID; Req 6.2, 6.8) con el plan vigente preseleccionado, y
// dos datepickers ISO opcionales para la vigencia (inicio y fin; Req 6.4). Al
// confirmar hace POST /suscripciones con { tenantId, planId, vigenciaInicio,
// vigenciaFin } y:
//   - 200/201 -> cierra devolviendo la Suscripcion creada (el panel refresca;
//                Req 6.3, 6.7).
//   - 404 -> Empresa o Plan inexistente: muestra el mensaje del backend SIN
//            cerrar el dialogo para su lectura (Req 6.5).
//   - 422 -> vigencia invalida: muestra el mensaje del backend sin cerrar el
//            dialogo (Req 6.6).
// El catalogo de planes se recibe por MAT_DIALOG_DATA (ya cargado por el panel)
// para no repetir peticiones. Accesible (labels, foco, aria), responsive y solo
// con tokens del Sistema de Diseno.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { SuscripcionesService } from '../services/suscripciones.service';
import { Empresa, Plan, Suscripcion } from '../models/plataforma.models';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { provideFechaIsoDatepicker } from '../../../shared/date/provide-fecha-iso';

/**
 * Datos de entrada del dialogo: la Empresa a la que se asigna el plan, el
 * catalogo de Planes (ya cargado por el panel, para no repetir la peticion) y,
 * opcionalmente, el `planId` del plan vigente para preseleccionarlo.
 */
export interface AsignarPlanDialogData {
  empresa: Empresa;
  planes: Plan[];
  planIdActual?: string | null;
}

@Component({
  selector: 'app-asignar-plan-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
  ],
  providers: [provideFechaIsoDatepicker()],
  templateUrl: './asignar-plan-dialog.html',
  styleUrl: './asignar-plan-dialog.scss',
})
export class AsignarPlanDialog {
  private readonly fb = inject(FormBuilder);
  private readonly suscripcionesService = inject(SuscripcionesService);
  private readonly dialogRef = inject(MatDialogRef<AsignarPlanDialog, Suscripcion>);
  protected readonly data = inject<AsignarPlanDialogData>(MAT_DIALOG_DATA);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Planes disponibles para asignar (por nombre, sin UUID). */
  protected readonly planes = this.data.planes;

  protected readonly formulario = this.fb.nonNullable.group({
    planId: [this.data.planIdActual ?? '', [Validators.required]],
    vigenciaInicio: [''],
    vigenciaFin: [''],
  });

  /**
   * Plan seleccionado en el formulario (o `undefined` si aun no se elige),
   * usado para mostrar detalles de ayuda (moneda, maximo de usuarios, total).
   */
  protected readonly planSeleccionado = computed(() => {
    const id = this.formulario.controls.planId.value;
    return this.planes.find((p) => p.id === id);
  });

  /** Confirma la asignacion creando la Suscripcion (POST /suscripciones). */
  protected guardar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    const { planId, vigenciaInicio, vigenciaFin } = this.formulario.getRawValue();
    this.guardando.set(true);
    this.suscripcionesService
      .crear({
        tenantId: this.data.empresa.id,
        planId,
        vigenciaInicio: vigenciaInicio || null,
        vigenciaFin: vigenciaFin || null,
      })
      .subscribe({
        next: (suscripcion) => {
          this.guardando.set(false);
          this.dialogRef.close(suscripcion);
        },
        error: (e: HttpErrorResponse) => {
          // El backend impone las reglas (empresa/plan existentes, vigencia
          // valida): se muestra su mensaje y el dialogo permanece abierto para
          // que se lea (Req 6.5 para 404, 6.6 para 422).
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
  }

  /** Cancela la asignacion de plan. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
