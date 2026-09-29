// =============================================================================
// Dialogo de alta/edicion de Lista de precios (Comercial) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline por un MODAL (MatDialog) consistente con el
// resto de la plataforma (form shell ds-form*). Sirve para ALTA (sin lista en
// los datos) y EDICION (con lista). Captura nombre, prioridad, segmento y la
// ventana de vigencia (inicio obligatorio, fin opcional). Al guardar hace
// POST/PUT via ListasPreciosService y cierra devolviendo la Lista resultante.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { ListasPreciosService } from '../services/catalogo.service';
import { ListaPrecios, ListaPreciosRequest } from '../models/comercial.models';

/**
 * Datos de entrada del dialogo. `lista` presente => modo EDICION (prellena y hace
 * PUT); ausente => modo ALTA (form vacio y POST).
 */
export interface ListaFormDialogData {
  lista?: ListaPrecios;
}

/** Convierte un valor de fecha (Date del datepicker o ISO string) a ISO YYYY-MM-DD. */
function aIsoFecha(valor: unknown): string | null {
  if (!valor) {
    return null;
  }
  if (valor instanceof Date) {
    // Fecha local a YYYY-MM-DD sin desfase de zona horaria.
    const y = valor.getFullYear();
    const m = String(valor.getMonth() + 1).padStart(2, '0');
    const d = String(valor.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
  const s = String(valor).trim();
  return s.length > 0 ? s.slice(0, 10) : null;
}

@Component({
  selector: 'app-lista-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './lista-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './lista-form-dialog.scss',
})
export class ListaFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ListasPreciosService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<ListaFormDialog, ListaPrecios>);
  private readonly data = inject<ListaFormDialogData>(MAT_DIALOG_DATA);

  /** Lista en edicion (o `null` en alta). */
  private readonly lista = this.data?.lista ?? null;
  /** `true` cuando el dialogo edita una Lista existente. */
  protected readonly esEdicion = !!this.lista;

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly titulo = computed(() =>
    this.esEdicion ? 'Editar lista de precios' : 'Nueva lista de precios',
  );
  protected readonly subtitulo = computed(() =>
    this.esEdicion
      ? 'Actualiza la vigencia, prioridad y segmento de la lista.'
      : 'Define una lista de precios con su vigencia, prioridad y segmento.',
  );

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    prioridad: [1, [Validators.required, Validators.min(0)]],
    segmento: [''],
    vigenciaInicio: ['' as string | Date, [Validators.required]],
    vigenciaFin: ['' as string | Date],
  });

  constructor() {
    if (this.lista) {
      this.form.reset({
        nombre: this.lista.nombre,
        prioridad: this.lista.prioridad,
        segmento: this.lista.segmento ?? '',
        vigenciaInicio: this.lista.vigenciaInicio,
        vigenciaFin: this.lista.vigenciaFin ?? '',
      });
    }
  }

  /** Persiste el alta o la edicion segun el modo (Req 59.3). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const inicio = aIsoFecha(v.vigenciaInicio);
    if (!inicio) {
      this.form.controls.vigenciaInicio.setErrors({ required: true });
      return;
    }
    const request: ListaPreciosRequest = {
      nombre: v.nombre.trim(),
      prioridad: Number(v.prioridad),
      segmento: v.segmento.trim() || null,
      vigenciaInicio: inicio,
      vigenciaFin: aIsoFecha(v.vigenciaFin),
    };
    const id = this.lista?.id;
    this.guardando.set(true);
    const peticion = id ? this.service.actualizar(id, request) : this.service.definir(request);
    this.overlay
      .ejecutar(peticion, {
        tipo: id ? 'guardar' : 'crear',
        textoProceso: id ? 'Guardando lista…' : 'Creando lista…',
        textoExito: id ? 'Lista guardada' : 'Lista creada',
      })
      .subscribe({
        next: (lista) => {
          this.guardando.set(false);
          this.dialogRef.close(lista);
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
