// =============================================================================
// Dialogo de avance de fase de una sucursal (Operacion) — modal animado
// -----------------------------------------------------------------------------
// Confirma el avance de una sucursal a la fase destino y captura una NOTA
// opcional del avance. La EVIDENCIA (archivos reales) se gestiona aparte, en el
// modal de evidencias; aqui ya no se pide una URL. Si la transicion a "entregado"
// requiere evidencia aprobada y no la hay, el backend responde 422 y el mensaje se
// muestra en el propio dialogo (regla de negocio, Req 3.2). Al guardar hace
// PUT .../sitios/{id}/avance via ProyectosService y cierra con el Proyecto.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
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
import {
  ETIQUETA_FASE_SITIO,
  FaseSitioGenerica,
  Proyecto,
  SitioFase,
} from '../models/operacion.models';

/**
 * Datos de entrada del dialogo de avance:
 *  - `proyectoId`: Proyecto al que pertenece la sucursal.
 *  - `sitio`: sucursal que avanza (para mostrar su nombre y fase actual).
 *  - `destino`: fase a la que se avanza.
 *  - `icono`: icono contextual de la fase destino (coherente con el Kanban).
 *  - `requiereEvidencia`: si la transicion exige evidencia aprobada (entregar).
 */
export interface AvanceSitioDialogData {
  proyectoId: string;
  sitio: SitioFase;
  destino: FaseSitioGenerica;
  icono: string;
  requiereEvidencia?: boolean;
  /**
   * Modo del diálogo: `avance` (uso común, nota opcional, valida precondiciones en
   * el backend) o `correccion` (administrativa, motivo OBLIGATORIO, salta
   * precondiciones). Por defecto `avance`.
   */
  modo?: 'avance' | 'correccion';
  /**
   * Si la fase destino exige precondiciones de instalación (levantamiento
   * completado + permiso vigente), para mostrar un aviso informativo en el modo
   * avance. El backend es la fuente de verdad y responde 422 si no se cumplen.
   */
  requierePrecondicionesInstalacion?: boolean;
}

@Component({
  selector: 'app-avance-sitio-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './avance-sitio-dialog.html',
  styleUrl: './avance-sitio-dialog.scss',
})
export class AvanceSitioDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProyectosService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<AvanceSitioDialog, Proyecto>);
  private readonly data = inject<AvanceSitioDialogData>(MAT_DIALOG_DATA);

  protected readonly sitio = this.data.sitio;
  protected readonly destino = this.data.destino;
  protected readonly icono = this.data.icono;
  protected readonly requiereEvidencia = this.data.requiereEvidencia ?? false;
  protected readonly requierePrecondicionesInstalacion =
    this.data.requierePrecondicionesInstalacion ?? false;
  protected readonly esCorreccion = this.data.modo === 'correccion';
  protected readonly etiquetaFase = ETIQUETA_FASE_SITIO;

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly subtitulo = computed(() =>
    this.esCorreccion
      ? `Corrección administrativa: ${this.sitio.sitio.nombre} pasará a ` +
        `“${ETIQUETA_FASE_SITIO[this.destino]}”. Indica el motivo del ajuste.`
      : `${this.sitio.sitio.nombre} pasará de “${ETIQUETA_FASE_SITIO[this.sitio.fase]}” a ` +
        `“${ETIQUETA_FASE_SITIO[this.destino]}”.`,
  );

  // En corrección administrativa el motivo (nota) es OBLIGATORIO (coherente con la
  // guarda del backend, Req 3-bis.5); en el avance de uso común la nota es opcional.
  protected readonly form = this.fb.nonNullable.group({
    nota: [
      '',
      this.data.modo === 'correccion'
        ? [Validators.required, Validators.maxLength(500)]
        : [Validators.maxLength(500)],
    ],
  });

  /**
   * Confirma el cambio de fase. En modo avance envía el avance de uso común (nota
   * opcional, precondiciones validadas por el backend); en modo corrección envía la
   * corrección administrativa con el motivo obligatorio (Req 3-bis.5).
   */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const notaBruta = this.form.getRawValue().nota.trim();
    const nota = notaBruta || null;
    this.guardando.set(true);
    const peticion = this.esCorreccion
      ? this.service.corregirFaseSitio(this.data.proyectoId, this.sitio.sitio.id, {
          fase: this.destino,
          nota,
        })
      : this.service.actualizarAvanceSitio(this.data.proyectoId, this.sitio.sitio.id, {
          fase: this.destino,
          nota,
        });
    this.overlay
      .ejecutar(peticion, {
        tipo: 'procesar',
        textoProceso: this.esCorreccion ? 'Corrigiendo fase…' : 'Actualizando sitio…',
        textoExito: this.esCorreccion ? 'Fase corregida' : 'Sitio actualizado',
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
  }

  /** Cancela sin avanzar. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
