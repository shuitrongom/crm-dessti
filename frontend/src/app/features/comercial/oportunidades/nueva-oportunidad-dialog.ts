// =============================================================================
// Dialogo de alta de Oportunidad (Comercial) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de alta del pipeline por un MODAL (MatDialog)
// consistente con el resto de la plataforma (mismo "form shell" ds-form*). Busca
// el Cliente por nombre/RFC (entity-select, sin teclear el UUID), captura titulo
// y valor estimado, y da de alta la Oportunidad en etapa inicial (Req 14.1). Al
// guardar hace POST /oportunidades via OportunidadesService y cierra devolviendo
// la Oportunidad creada, para que el pipeline recargue.
// =============================================================================

import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Observable } from 'rxjs';

import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { PaginaResponse } from '../../../core/models/pagina-response';

import { OportunidadesService } from '../services/oportunidades.service';
import { ClientesService } from '../services/clientes.service';
import { Cliente, Oportunidad } from '../models/comercial.models';

@Component({
  selector: 'app-nueva-oportunidad-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    EntitySelect,
  ],
  templateUrl: './nueva-oportunidad-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './nueva-oportunidad-dialog.scss',
})
export class NuevaOportunidadDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(OportunidadesService);
  private readonly clientes = inject(ClientesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<NuevaOportunidadDialog, Oportunidad>);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    clienteId: ['', [Validators.required]],
    titulo: ['', [Validators.required, Validators.maxLength(200)]],
    valorEstimado: [0, [Validators.required, Validators.min(0.01), Validators.max(999999999.99)]],
  });

  /** Busca Clientes por nombre/RFC para el selector (nunca se teclea el UUID). */
  protected readonly buscarCliente = (filtro: string): Observable<PaginaResponse<Cliente>> =>
    this.clientes.listar(filtro, 0, 20);

  /** Etiqueta principal de un Cliente en el selector. */
  protected readonly etiquetaCliente = (cliente: Cliente): string => cliente.nombre;

  /** Detalle secundario (RFC) de un Cliente en el selector. */
  protected readonly detalleCliente = (cliente: Cliente): string | null => cliente.rfc || null;

  /** Da de alta la Oportunidad y cierra el dialogo con la creada (Req 14.1). */
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
          clienteId: v.clienteId.trim(),
          titulo: v.titulo.trim(),
          valorEstimado: v.valorEstimado,
        }),
        { tipo: 'crear', textoProceso: 'Creando oportunidad…', textoExito: 'Oportunidad creada' },
      )
      .subscribe({
        next: (oportunidad) => {
          this.guardando.set(false);
          this.dialogRef.close(oportunidad);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          // El cliente se elige de una lista existente; un 404 aqui significa que
          // dejo de existir (baja concurrente) desde que se cargo el selector.
          this.error.set(
            e.status === 404 ? 'El cliente seleccionado ya no existe.' : mensajeDeError(e),
          );
        },
      });
  }

  /** Cancela el alta sin guardar. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
