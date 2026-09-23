// =============================================================================
// Vista de detalle de Levantamiento de Sitio (Req 9.2, 9.5-9.8, 12)
// -----------------------------------------------------------------------------
// Vista ESPECIFICA de anuncios (ruta protegida por guardaGiro(GIRO_ANUNCIOS)).
// Muestra la informacion del Levantamiento con Sitio/Cotizacion/Orden por NOMBRE
// (via NombresOperacionService, nunca el UUID), el estado con etiqueta es-MX, y
// una galeria de fotos vinculadas. Ofrece el flujo de adjuntar fotos (Req 12),
// gobernado por el permiso levantamiento_sitio:cambiar_estado, reutilizando
// LevantamientosService.agregarFotos/fotosDe (no se duplica el contrato).
// Sigue el patron de proyecto-detalle: input.required<string>() del :id, signal
// de fase cargando|ok|error|vacio + mensajeError, StateContainer, PageHeader,
// es-MX, solo design tokens y WCAG AA.
// =============================================================================

import { Component, OnInit, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { RouterLink } from '@angular/router';
import { FormArray, FormControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ChipEstado, VarianteChipEstado } from '../../../shared/components/chip-estado/chip-estado';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { LevantamientosService } from '../services/instalacion.service';
import { NombresOperacionService } from '../services/nombres-operacion.service';
import {
  ETIQUETA_ESTADO_LEVANTAMIENTO,
  EstadoLevantamiento,
  LevantamientoFoto,
  LevantamientoSitioDetalle,
} from '../models/operacion.models';

@Component({
  selector: 'app-operacion-levantamiento-detalle',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    ChipEstado,
  ],
  templateUrl: './levantamiento-detalle.html',
  styleUrl: './levantamiento-detalle.scss',
})
export class OperacionLevantamientoDetalle implements OnInit {
  /** Identificador del Levantamiento tomado de la ruta (:id). */
  readonly id = input.required<string>();

  private readonly fb = inject(FormBuilder);
  private readonly service = inject(LevantamientosService);
  protected readonly nombres = inject(NombresOperacionService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  /** Solo quien puede cambiar el estado del Levantamiento puede adjuntar fotos. */
  protected readonly puedeAgregarFotos = this.auth.tienePermiso(
    'levantamiento_sitio',
    'cambiar_estado',
  );

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly levantamiento = signal<LevantamientoSitioDetalle | null>(null);
  protected readonly fotos = signal<LevantamientoFoto[]>([]);

  /** Indica que hay una operacion de adjuntar en curso. */
  protected readonly adjuntando = signal(false);
  /** Formulario con una o mas referencias de foto a adjuntar (Req 12.1). */
  protected readonly fotosForm = this.fb.group({
    referencias: this.fb.array<FormControl<string>>([this.nuevaReferencia()]),
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.mensajeError.set(undefined);
    // Carga los catalogos de nombres (Sitio/Cotizacion/OF) junto con el detalle
    // del Levantamiento; ambos deben estar listos antes de pintar la vista.
    forkJoin({
      nombres: this.nombres.cargar(),
      levantamiento: this.service.consultar(this.id()),
    }).subscribe({
      next: ({ levantamiento }) => {
        this.levantamiento.set(levantamiento);
        this.fotos.set(levantamiento.fotos);
        this.fase.set('ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Etiqueta legible es-MX del estado del Levantamiento. */
  protected etiquetaEstado(estado: EstadoLevantamiento): string {
    return ETIQUETA_ESTADO_LEVANTAMIENTO[estado] ?? estado;
  }

  /**
   * Variante semantica del Chip_Estado segun el estado del Levantamiento:
   * en_proceso -> advertencia (trabajo pendiente), completado -> exito.
   */
  protected varianteEstado(estado: EstadoLevantamiento): VarianteChipEstado {
    return estado === 'completado' ? 'exito' : 'advertencia';
  }

  // --- Flujo de fotos (Req 12) ---------------------------------------------

  /** Crea un control de referencia de foto (obligatorio, sin espacios). */
  private nuevaReferencia(): FormControl<string> {
    return this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(500)]);
  }

  /** Acceso tipado al arreglo de controles de referencia. */
  protected get referencias(): FormArray<FormControl<string>> {
    return this.fotosForm.controls.referencias;
  }

  /** Agrega un nuevo campo de referencia para adjuntar varias fotos a la vez. */
  agregarCampoReferencia(): void {
    this.referencias.push(this.nuevaReferencia());
  }

  /** Elimina un campo de referencia; conserva al menos uno. */
  quitarCampoReferencia(indice: number): void {
    if (this.referencias.length > 1) {
      this.referencias.removeAt(indice);
    }
  }

  private reiniciarFormularioFotos(): void {
    this.fotosForm.setControl('referencias', this.fb.array([this.nuevaReferencia()]));
  }

  /**
   * Adjunta las referencias capturadas al Levantamiento (Req 12.1). Sin
   * referencias validas no se llama al backend y se muestra validacion es-MX
   * (Req 12.2); ante un fallo se muestra un mensaje es-MX conservando el estado
   * previo de la vista (Req 12.3).
   */
  agregarFotos(): void {
    const levantamiento = this.levantamiento();
    if (!levantamiento) {
      return;
    }
    const referencias = this.referencias.controls
      .map((c) => c.value.trim())
      .filter((r) => r.length > 0);
    if (referencias.length === 0) {
      this.referencias.markAllAsTouched();
      this.toast.error('Agrega al menos una referencia de foto.');
      return;
    }
    this.adjuntando.set(true);
    this.service.agregarFotos(levantamiento.id, referencias).subscribe({
      next: (fotos) => {
        this.adjuntando.set(false);
        this.fotos.set(fotos);
        this.reiniciarFormularioFotos();
        this.toast.exito('Fotos adjuntadas.');
      },
      error: (e: HttpErrorResponse) => {
        this.adjuntando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
