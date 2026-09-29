// =============================================================================
// Dialogo de alta de Permiso de Instalacion (Operacion) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de la vista de Permisos por un MODAL (MatDialog)
// consistente con el resto de la plataforma (form shell ds-form*). Captura el
// tipo (municipal/arrendador), la fecha de vencimiento (datepicker es-MX que
// mantiene el valor como cadena ISO YYYY-MM-DD) y el sitio. Al guardar hace POST
// via PermisosService y cierra devolviendo el Permiso creado.
// =============================================================================

import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { provideFechaIsoDatepicker } from '../../../shared/date/provide-fecha-iso';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { PermisosService } from '../services/instalacion.service';
import { ETIQUETA_TIPO_PERMISO, PermisoInstalacion, TipoPermiso } from '../models/operacion.models';

@Component({
  selector: 'app-permiso-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  providers: [provideFechaIsoDatepicker()],
  templateUrl: './permiso-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './permiso-form-dialog.scss',
})
export class PermisoFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PermisosService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<PermisoFormDialog, PermisoInstalacion>);

  protected readonly etiquetaTipo = ETIQUETA_TIPO_PERMISO;
  protected readonly tipos: TipoPermiso[] = ['municipal', 'arrendador'];

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    tipo: ['municipal' as TipoPermiso, [Validators.required]],
    fechaVencimiento: ['', [Validators.required]],
    sitioId: ['', [Validators.required]],
  });

  /** Crea un Permiso de Instalacion en estado solicitado (Req 17.1). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.overlay
      .ejecutar(
        this.service.crear({
          tipo: v.tipo,
          fechaVencimiento: v.fechaVencimiento,
          sitioId: v.sitioId.trim(),
        }),
        {
          tipo: 'crear',
          textoProceso: 'Registrando permiso…',
          textoExito: 'Permiso registrado',
        },
      )
      .subscribe({
        next: (permiso) => {
          this.guardando.set(false);
          this.dialogRef.close(permiso);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
  }

  /** Cancela sin guardar. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
