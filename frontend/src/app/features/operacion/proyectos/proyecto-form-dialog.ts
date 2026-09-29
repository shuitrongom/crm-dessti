// =============================================================================
// Dialogo de alta/edicion de Proyecto (Operacion) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de la vista de Proyectos por un MODAL (MatDialog)
// consistente con el resto de la plataforma (form shell ds-form*). En ALTA pide
// el Cliente (selector por nombre/RFC, nunca el UUID) y el nombre; en EDICION solo
// permite cambiar el nombre (el Cliente es inmutable). Al guardar hace POST/PUT via
// ProyectosService y cierra devolviendo el Proyecto resultante.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Observable } from 'rxjs';

import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { PaginaResponse } from '../../../core/models/pagina-response';

import { ProyectosService } from '../services/proyectos.service';
import { Proyecto } from '../models/operacion.models';
import { Cliente } from '../../comercial/models/comercial.models';
import { ClientesService } from '../../comercial/services/clientes.service';

/**
 * Datos de entrada del dialogo. `proyecto` presente => modo EDICION (prellena el
 * nombre y hace PUT, Cliente inmutable); ausente => modo ALTA (selector de Cliente
 * + nombre y POST).
 */
export interface ProyectoFormDialogData {
  proyecto?: Proyecto;
}

@Component({
  selector: 'app-proyecto-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    EntitySelect,
  ],
  templateUrl: './proyecto-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './proyecto-form-dialog.scss',
})
export class ProyectoFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProyectosService);
  private readonly clientes = inject(ClientesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<ProyectoFormDialog, Proyecto>);
  private readonly data = inject<ProyectoFormDialogData>(MAT_DIALOG_DATA);

  /** Proyecto en edicion (o `null` en alta). */
  private readonly proyecto = this.data?.proyecto ?? null;
  /** `true` cuando el dialogo edita un Proyecto existente. */
  protected readonly esEdicion = !!this.proyecto;

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly titulo = computed(() =>
    this.esEdicion ? 'Editar proyecto' : 'Nuevo proyecto',
  );
  protected readonly subtitulo = computed(() =>
    this.esEdicion
      ? 'Actualiza el nombre del proyecto. El cliente asociado no se modifica.'
      : 'Registra un proyecto asociado a un cliente. Luego podrás agregarle sitios.',
  );

  protected readonly form = this.fb.nonNullable.group({
    clienteId: ['', [Validators.required]],
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
  });

  /** Busca Clientes por nombre/RFC para el selector (nunca se teclea el UUID). */
  protected readonly buscarCliente = (filtro: string): Observable<PaginaResponse<Cliente>> =>
    this.clientes.listar(filtro, 0, 20);

  /** Etiqueta principal de un Cliente en el selector. */
  protected readonly etiquetaCliente = (cliente: Cliente): string => cliente.nombre;

  /** Detalle secundario (RFC) de un Cliente en el selector. */
  protected readonly detalleCliente = (cliente: Cliente): string | null => cliente.rfc || null;

  constructor() {
    if (this.proyecto) {
      // En edicion el Cliente es inmutable: se quita la validacion del control (no
      // se muestra el selector) y se prellena el nombre.
      this.form.controls.clienteId.clearValidators();
      this.form.controls.clienteId.updateValueAndValidity();
      this.form.reset({ clienteId: this.proyecto.clienteId, nombre: this.proyecto.nombre });
    }
  }

  /** Persiste el alta o la edicion segun el modo (Req 21.1). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    const peticion = this.proyecto
      ? this.service.editar(this.proyecto.id, { nombre: v.nombre.trim() })
      : this.service.crear({ clienteId: v.clienteId.trim(), nombre: v.nombre.trim() });
    this.overlay
      .ejecutar(peticion, {
        tipo: this.proyecto ? 'guardar' : 'crear',
        textoProceso: this.proyecto ? 'Guardando proyecto…' : 'Creando proyecto…',
        textoExito: this.proyecto ? 'Proyecto guardado' : 'Proyecto creado',
      })
      .subscribe({
        next: (proyecto) => {
          this.guardando.set(false);
          this.dialogRef.close(proyecto);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(
            e.status === 404 && !this.proyecto
              ? 'El cliente seleccionado ya no existe.'
              : mensajeDeError(e),
          );
        },
      });
  }

  /** Cancela sin guardar. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
