// =============================================================================
// Dialogo de alta/edicion de Actividad de seguimiento (Comercial) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza los formularios inline del timeline (alta y edicion en linea) por un
// MODAL (MatDialog) consistente con el resto de la plataforma (form shell
// ds-form*). Sirve para ALTA (sin actividad en los datos: captura tipo, asunto,
// fecha programada, vencimiento y descripcion) y para EDICION (con actividad: el
// backend solo permite editar asunto y descripcion, asi que el tipo y las fechas
// se muestran como contexto de solo lectura). Al guardar hace POST/PUT via
// ActividadesService y cierra devolviendo la Actividad resultante.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { ActividadesService } from '../services/actividades.service';
import {
  Actividad,
  ActividadRequest,
  EditarActividadRequest,
  ETIQUETA_TIPO_ACTIVIDAD,
  TIPOS_ACTIVIDAD,
  TipoActividad,
} from '../models/comercial.models';

/**
 * Datos de entrada del dialogo.
 *  - `actividad` presente => modo EDICION (prellena y hace PUT de asunto/descripcion).
 *  - `actividad` ausente => modo ALTA (form vacio y POST). Requiere `clienteId`;
 *    `oportunidadId` es opcional para vincular la actividad a una oportunidad.
 */
export interface ActividadFormDialogData {
  actividad?: Actividad;
  clienteId?: string;
  oportunidadId?: string | null;
}

@Component({
  selector: 'app-actividad-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './actividad-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './actividad-form-dialog.scss',
})
export class ActividadFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ActividadesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<ActividadFormDialog, Actividad>);
  private readonly data = inject<ActividadFormDialogData>(MAT_DIALOG_DATA);

  /** Actividad en edicion (o `null` en alta). */
  private readonly actividad = this.data?.actividad ?? null;
  /** `true` cuando el dialogo edita una Actividad existente. */
  protected readonly esEdicion = !!this.actividad;

  protected readonly tiposActividad = TIPOS_ACTIVIDAD;

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly titulo = computed(() =>
    this.esEdicion ? 'Editar actividad' : 'Nueva actividad',
  );
  protected readonly subtitulo = computed(() =>
    this.esEdicion
      ? 'Actualiza el asunto y la descripción del seguimiento. El tipo y las fechas no se modifican aquí.'
      : 'Registra una interacción o tarea de seguimiento con el cliente (llamada, correo, reunión, tarea o nota).',
  );

  /**
   * Texto legible del tipo de la actividad en edicion, para mostrarlo como
   * contexto de solo lectura (el backend no permite cambiar el tipo al editar).
   */
  protected readonly tipoLegible = computed(() =>
    this.actividad ? ETIQUETA_TIPO_ACTIVIDAD[this.actividad.tipo] : '',
  );

  /** Fecha programada legible (contexto de solo lectura en edicion). */
  protected readonly fechaProgramadaLegible = computed(() =>
    this.actividad ? this.formatearFecha(this.actividad.fechaProgramada) : '',
  );

  protected readonly form = this.fb.nonNullable.group({
    tipo: ['llamada' as TipoActividad, Validators.required],
    asunto: ['', [Validators.required, Validators.maxLength(200)]],
    descripcion: ['', Validators.maxLength(4000)],
    fechaProgramada: [this.ahoraLocalInput(), Validators.required],
    vencimiento: [''],
  });

  constructor() {
    if (this.actividad) {
      // En edicion solo se editan asunto y descripcion; tipo y fechas quedan como
      // referencia (se muestran en el HTML) y no se envian al backend.
      this.form.reset({
        tipo: this.actividad.tipo,
        asunto: this.actividad.asunto,
        descripcion: this.actividad.descripcion ?? '',
        fechaProgramada: this.aInputLocal(this.actividad.fechaProgramada),
        vencimiento: this.actividad.vencimiento ? this.aInputLocal(this.actividad.vencimiento) : '',
      });
    }
  }

  /** Persiste el alta o la edicion segun el modo (Req: actividades V79). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);

    if (this.actividad) {
      // EDICION: el backend solo acepta asunto y descripcion.
      const request: EditarActividadRequest = {
        asunto: v.asunto.trim(),
        descripcion: v.descripcion.trim() || null,
      };
      this.overlay
        .ejecutar(this.service.editar(this.actividad.id, request), {
          tipo: 'guardar',
          textoProceso: 'Guardando actividad…',
          textoExito: 'Actividad actualizada',
        })
        .subscribe({
          next: (actividad) => {
            this.guardando.set(false);
            this.dialogRef.close(actividad);
          },
          error: (e: HttpErrorResponse) => {
            this.guardando.set(false);
            this.error.set(mensajeDeError(e));
          },
        });
      return;
    }

    // ALTA: cuerpo completo. El clienteId proviene del contexto del timeline.
    const request: ActividadRequest = {
      clienteId: this.data?.clienteId ?? '',
      oportunidadId: this.data?.oportunidadId ?? null,
      tipo: v.tipo,
      asunto: v.asunto.trim(),
      descripcion: v.descripcion.trim() || null,
      fechaProgramada: this.aIso(v.fechaProgramada),
      vencimiento: v.vencimiento ? this.aIso(v.vencimiento) : null,
    };
    this.overlay
      .ejecutar(this.service.crear(request), {
        tipo: 'crear',
        textoProceso: 'Registrando actividad…',
        textoExito: 'Actividad registrada',
      })
      .subscribe({
        next: (actividad) => {
          this.guardando.set(false);
          this.dialogRef.close(actividad);
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

  // --- Utilidades de fecha ---------------------------------------------------

  /** Convierte un valor de <input type="datetime-local"> a ISO-8601 (UTC). */
  private aIso(valorLocal: string): string {
    return new Date(valorLocal).toISOString();
  }

  /** Convierte un instante ISO-8601 a 'YYYY-MM-DDTHH:mm' en hora local (para el input). */
  private aInputLocal(iso: string): string {
    const fecha = new Date(iso);
    const offset = fecha.getTimezoneOffset() * 60000;
    return new Date(fecha.getTime() - offset).toISOString().slice(0, 16);
  }

  /** Valor 'YYYY-MM-DDTHH:mm' del instante actual en hora local para el input. */
  private ahoraLocalInput(): string {
    const ahora = new Date();
    const offset = ahora.getTimezoneOffset() * 60000;
    return new Date(ahora.getTime() - offset).toISOString().slice(0, 16);
  }

  /** Formatea un instante ISO a texto legible es-MX para el contexto de edicion. */
  private formatearFecha(iso: string): string {
    return new Date(iso).toLocaleString('es-MX', {
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  }
}
