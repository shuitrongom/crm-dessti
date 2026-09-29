// =============================================================================
// Dialogo de alta/edicion de Sitio de un Proyecto (Operacion) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de "Nuevo sitio" del detalle de Proyecto por un
// MODAL (MatDialog) consistente con el resto de la plataforma (form shell
// ds-form*). Sirve para ALTA (sin sitio en los datos: POST agregar) y EDICION
// (con sitio: PUT editar). Captura nombre y direccion opcional. Al cerrar devuelve
// el Proyecto detallado (en edicion) o `true` (en alta) para que el detalle
// refresque.
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
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { ProyectosService } from '../services/proyectos.service';
import { Proyecto, SitioFase } from '../models/operacion.models';

/** Resultado del dialogo: el Proyecto detallado (edicion) o `true` (alta). */
export type SitioFormDialogResult = Proyecto | true;

/**
 * Datos de entrada. `proyectoId` obligatorio. `sitio` presente => modo EDICION
 * (prellena y hace PUT); ausente => modo ALTA (form vacio y POST).
 */
export interface SitioFormDialogData {
  proyectoId: string;
  sitio?: SitioFase;
}

@Component({
  selector: 'app-sitio-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './sitio-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './sitio-form-dialog.scss',
})
export class SitioFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProyectosService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<SitioFormDialog, SitioFormDialogResult>);
  private readonly data = inject<SitioFormDialogData>(MAT_DIALOG_DATA);

  /** Sitio en edicion (o `null` en alta). */
  private readonly sitio = this.data?.sitio ?? null;
  /** `true` cuando el dialogo edita un Sitio existente. */
  protected readonly esEdicion = !!this.sitio;

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly titulo = computed(() => (this.esEdicion ? 'Editar sitio' : 'Nuevo sitio'));
  protected readonly subtitulo = computed(() =>
    this.esEdicion
      ? 'Actualiza el nombre y la dirección de la sucursal/ubicación.'
      : 'Agrega una sucursal o ubicación al proyecto. Su fase inicia en pendiente.',
  );

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    direccion: ['', [Validators.maxLength(500)]],
  });

  constructor() {
    if (this.sitio) {
      this.form.reset({
        nombre: this.sitio.sitio.nombre,
        direccion: this.sitio.sitio.direccion ?? '',
      });
    }
  }

  /** Persiste el alta o la edicion segun el modo (Req 21.2). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request = { nombre: v.nombre.trim(), direccion: v.direccion.trim() || null };
    this.guardando.set(true);

    if (this.sitio) {
      // EDICION: el backend devuelve el Proyecto detallado tras el cambio.
      this.overlay
        .ejecutar(this.service.editarSitio(this.data.proyectoId, this.sitio.sitio.id, request), {
          tipo: 'guardar',
          textoProceso: 'Guardando sitio…',
          textoExito: 'Sitio actualizado',
        })
        .subscribe({
          next: (proyecto) => {
            this.guardando.set(false);
            this.dialogRef.close(proyecto);
          },
          error: (e: HttpErrorResponse) => {
            this.guardando.set(false);
            this.error.set(mensajeDeError(e));
          },
        });
      return;
    }

    // ALTA: el backend devuelve el Sitio creado; devolvemos `true` para recargar.
    this.overlay
      .ejecutar(this.service.agregarSitio(this.data.proyectoId, request), {
        tipo: 'crear',
        textoProceso: 'Agregando sitio…',
        textoExito: 'Sitio agregado',
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.dialogRef.close(true);
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
