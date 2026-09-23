// =============================================================================
// Vista de Levantamientos de Sitio (Req 16) — listado + alta + completar
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por estado, alta con datos obligatorios
// y vinculos opcionales, y accion de completar (con confirmacion). Acciones
// gobernadas por permiso levantamiento_sitio:{...}.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ChipEstado, VarianteChipEstado } from '../../../shared/components/chip-estado/chip-estado';
import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { LevantamientosService } from '../services/instalacion.service';
import {
  ETIQUETA_ESTADO_LEVANTAMIENTO,
  EstadoLevantamiento,
  LevantamientoFoto,
  LevantamientoSitio,
} from '../models/operacion.models';

@Component({
  selector: 'app-operacion-levantamientos',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    ChipEstado,
  ],
  templateUrl: './levantamientos.html',
  styleUrl: './levantamientos.scss',
})
export class OperacionLevantamientos {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(LevantamientosService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('levantamiento_sitio', 'crear');
  protected readonly puedeCompletar = this.auth.tienePermiso('levantamiento_sitio', 'cambiar_estado');
  private readonly mapaEstado = ETIQUETA_ESTADO_LEVANTAMIENTO;
  protected readonly estados: EstadoLevantamiento[] = ['en_proceso', 'completado'];

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly levantamientos = signal<LevantamientoSitio[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly estado = signal<EstadoLevantamiento | ''>('');

  protected readonly guardando = signal(false);
  protected readonly formularioAbierto = signal(false);

  // --- Flujo de fotos (Req 12) ---------------------------------------------
  /** Levantamiento seleccionado para gestionar sus fotos; null si el panel esta cerrado. */
  protected readonly fotosLevantamiento = signal<LevantamientoSitio | null>(null);
  /** Fase de carga de la galeria del levantamiento seleccionado. */
  protected readonly fotosFase = signal<FaseSolicitud>('cargando');
  /** Fotos vinculadas al levantamiento seleccionado. */
  protected readonly fotos = signal<LevantamientoFoto[]>([]);
  /** Indica que hay una operacion de adjuntar en curso. */
  protected readonly adjuntando = signal(false);
  /** Formulario con una o mas referencias de foto a adjuntar. */
  protected readonly fotosForm = this.fb.group({
    referencias: this.fb.array<FormControl<string>>([this.nuevaReferencia()]),
  });

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'tipoSuperficie', encabezado: 'Superficie' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'completadoEn', encabezado: 'Completado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly form = this.fb.nonNullable.group({
    mediciones: ['', [Validators.required]],
    tipoSuperficie: ['', [Validators.required, Validators.maxLength(200)]],
    condicionesElectricas: ['', [Validators.required]],
    sitioId: [''],
    cotizacionId: [''],
    ordenFabricacionId: [''],
  });

  constructor() {
    this.cargar();
  }

  /** Etiqueta legible del estado del levantamiento; devuelve el valor crudo si no mapea. */
  protected etiquetaEstado(estado: string): string {
    return this.mapaEstado[estado as EstadoLevantamiento] ?? estado;
  }

  /**
   * Variante semantica del Chip_Estado segun el estado del Levantamiento:
   * en_proceso -> advertencia (trabajo pendiente), completado -> exito.
   */
  protected varianteEstado(estado: EstadoLevantamiento): VarianteChipEstado {
    return estado === 'completado' ? 'exito' : 'advertencia';
  }

  cargar(): void {
    this.fase.set('cargando');
    const estado = this.estado() || null;
    this.service.listar(estado, this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.levantamientos.set(pagina.content);
        this.total.set(pagina.totalElements);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  cambiarFiltro(valor: EstadoLevantamiento | ''): void {
    this.estado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  alternarFormulario(): void {
    this.formularioAbierto.update((v) => !v);
    if (this.formularioAbierto()) {
      this.form.reset({
        mediciones: '',
        tipoSuperficie: '',
        condicionesElectricas: '',
        sitioId: '',
        cotizacionId: '',
        ordenFabricacionId: '',
      });
    }
  }

  /** Crea un Levantamiento con datos obligatorios y vinculos opcionales (Req 16.1). */
  crear(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.service
      .crear({
        mediciones: v.mediciones.trim(),
        tipoSuperficie: v.tipoSuperficie.trim(),
        condicionesElectricas: v.condicionesElectricas.trim(),
        sitioId: v.sitioId.trim() || null,
        cotizacionId: v.cotizacionId.trim() || null,
        ordenFabricacionId: v.ordenFabricacionId.trim() || null,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Levantamiento creado.');
          this.formularioAbierto.set(false);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  /** Marca un Levantamiento como completado con confirmacion (Req 16.4). */
  async completar(levantamiento: LevantamientoSitio): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Completar levantamiento',
      mensaje: 'El levantamiento quedara marcado como completado. Deseas continuar?',
      textoConfirmar: 'Completar',
    });
    if (!ok) {
      return;
    }
    this.service.completar(levantamiento.id).subscribe({
      next: () => {
        this.toast.exito('Levantamiento completado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
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

  /** Abre el panel de fotos de un Levantamiento y carga su galeria. */
  abrirFotos(levantamiento: LevantamientoSitio): void {
    this.fotosLevantamiento.set(levantamiento);
    this.reiniciarFormularioFotos();
    this.cargarFotos(levantamiento.id);
  }

  /** Cierra el panel de fotos y limpia su estado. */
  cerrarFotos(): void {
    this.fotosLevantamiento.set(null);
    this.fotos.set([]);
    this.reiniciarFormularioFotos();
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

  /** Carga (o recarga) la galeria de fotos del levantamiento seleccionado (Req 12.1). */
  private cargarFotos(id: string): void {
    this.fotosFase.set('cargando');
    this.service.fotosDe(id).subscribe({
      next: (fotos) => {
        this.fotos.set(fotos);
        this.fotosFase.set(fotos.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.toast.error(mensajeDeError(e));
        this.fotosFase.set('error');
      },
    });
  }

  /** Reintenta la carga de la galeria del levantamiento seleccionado. */
  recargarFotos(): void {
    const seleccionado = this.fotosLevantamiento();
    if (seleccionado) {
      this.cargarFotos(seleccionado.id);
    }
  }

  private reiniciarFormularioFotos(): void {
    this.fotosForm.setControl('referencias', this.fb.array([this.nuevaReferencia()]));
  }

  /**
   * Adjunta las referencias capturadas al Levantamiento seleccionado (Req 12.1).
   * Sin referencias validas no se llama al backend y se muestra validacion es-MX
   * (Req 12.2); ante un fallo se muestra un mensaje es-MX conservando el estado
   * previo de la vista (Req 12.3).
   */
  agregarFotos(): void {
    const seleccionado = this.fotosLevantamiento();
    if (!seleccionado) {
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
    this.service.agregarFotos(seleccionado.id, referencias).subscribe({
      next: (fotos) => {
        this.adjuntando.set(false);
        this.fotos.set(fotos);
        this.fotosFase.set(fotos.length === 0 ? 'vacio' : 'ok');
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
