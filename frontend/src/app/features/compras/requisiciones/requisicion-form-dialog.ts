// =============================================================================
// Dialogo de alta de Requisición de compra (Compras) — modal premium animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de alta por un MODAL (MatDialog) consistente con
// el resto de la plataforma. Captura una o más partidas (Material + cantidad); el
// Material se elige por NOMBRE con EntitySelect (nunca se teclea el UUID) y se
// puede precargar la lista al enfocar. Al guardar hace POST vía ComprasService y
// cierra devolviendo la Requisición creada.
//
// Reglas replicadas del backend (Req 30.1): al menos una partida; cantidad entera
// 1..999999; el backend valida que el Material exista y esté activo (404 si no).
// =============================================================================

import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
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

import { ComprasService } from '../services/compras.service';
import { MaterialesService } from '../../operacion/services/inventario.service';
import { Material } from '../../operacion/models/operacion.models';
import { RequisicionCompra } from '../models/compras.models';

@Component({
  selector: 'app-requisicion-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    EntitySelect,
  ],
  templateUrl: './requisicion-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './requisicion-form-dialog.scss',
})
export class RequisicionFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ComprasService);
  private readonly materiales = inject(MaterialesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<RequisicionFormDialog, RequisicionCompra>);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Busca Materiales activos por nombre para el selector de partida (sin UUID). */
  protected readonly buscarMaterial = (filtro: string): Observable<PaginaResponse<Material>> =>
    this.materiales.listar(filtro, 'activo', false, 0, 20);
  protected readonly etiquetaMaterial = (m: Material): string => m.nombre;
  protected readonly detalleMaterial = (m: Material): string | null => m.unidadMedida || null;

  protected readonly form = this.fb.nonNullable.group({
    partidas: this.fb.array([this.crearPartida()]),
  });

  protected get partidas(): FormArray {
    return this.form.get('partidas') as FormArray;
  }

  private crearPartida() {
    return this.fb.nonNullable.group({
      materialId: ['', [Validators.required]],
      cantidad: [1, [Validators.required, Validators.min(1), Validators.max(999999)]],
    });
  }

  protected agregarPartida(): void {
    this.partidas.push(this.crearPartida());
  }

  protected quitarPartida(indice: number): void {
    if (this.partidas.length > 1) {
      this.partidas.removeAt(indice);
    }
  }

  /** Persiste el alta de la requisición (Req 30.1). */
  protected guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const partidas = this.partidas.getRawValue() as { materialId: string; cantidad: number }[];
    this.guardando.set(true);
    this.overlay
      .ejecutar(this.service.crearRequisicion({ partidas }), {
        tipo: 'crear',
        textoProceso: 'Creando requisición…',
        textoExito: 'Requisición creada',
      })
      .subscribe({
        next: (requisicion) => {
          this.guardando.set(false);
          this.dialogRef.close(requisicion);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
  }

  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
