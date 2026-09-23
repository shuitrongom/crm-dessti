// =============================================================================
// Vista de Materiales / inventario base (Req 18) — listado + alta + movimientos
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por nombre y por condicion de stock
// bajo (indicador visible sin depender solo del color), alta de Material,
// registro de movimientos (entrada/salida/ajuste) y baja logica. Acciones
// gobernadas por permiso material:{...} y movimiento_inventario:crear.
//
// NOTA: el historial cronologico (Kardex) de un Material se consulta en la vista
// de inventario avanzado por Almacen; el inventario base no expone un listado de
// movimientos.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
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

import { MaterialesService } from '../services/inventario.service';
import { ETIQUETA_TIPO_MOVIMIENTO, Material, TipoMovimiento } from '../models/operacion.models';

@Component({
  selector: 'app-operacion-materiales',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './materiales.html',
  styleUrl: './materiales.scss',
})
export class OperacionMateriales {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MaterialesService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('material', 'crear');
  protected readonly puedeMovimiento = this.auth.tienePermiso('movimiento_inventario', 'crear');
  protected readonly puedeEliminar = this.auth.tienePermiso('material', 'eliminar');
  protected readonly etiquetaTipo = ETIQUETA_TIPO_MOVIMIENTO;
  protected readonly tipos: TipoMovimiento[] = ['entrada', 'salida', 'ajuste'];

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly materiales = signal<Material[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly nombre = signal('');
  protected readonly soloStockBajo = signal(false);

  protected readonly guardando = signal(false);
  protected readonly formularioAbierto = signal(false);
  /** Material sobre el que se registra un movimiento (o null). */
  protected readonly movimientoDe = signal<Material | null>(null);

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Material' },
    { clave: 'unidadMedida', encabezado: 'Unidad' },
    { clave: 'existencias', encabezado: 'Existencias', alineacion: 'fin' },
    { clave: 'stockMinimo', encabezado: 'Stock minimo', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    unidadMedida: ['', [Validators.required, Validators.maxLength(50)]],
    stockMinimo: [0, [Validators.required, Validators.min(0)]],
  });

  protected readonly formMovimiento = this.fb.nonNullable.group({
    tipo: ['entrada' as TipoMovimiento, [Validators.required]],
    cantidad: [1, [Validators.required]],
    motivo: ['', [Validators.maxLength(500)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service.listar(this.nombre(), this.soloStockBajo(), this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.materiales.set(pagina.content);
        this.total.set(pagina.totalElements);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  filtrarNombre(valor: string): void {
    this.nombre.set(valor);
    this.page.set(0);
    this.cargar();
  }

  alternarStockBajo(valor: boolean): void {
    this.soloStockBajo.set(valor);
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
    this.movimientoDe.set(null);
    if (this.formularioAbierto()) {
      this.form.reset({ nombre: '', unidadMedida: '', stockMinimo: 0 });
    }
  }

  /** Da de alta un Material con existencias iniciales 0 (Req 18.1). */
  crear(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.service
      .crear({ nombre: v.nombre.trim(), unidadMedida: v.unidadMedida.trim(), stockMinimo: Number(v.stockMinimo) })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Material creado.');
          this.formularioAbierto.set(false);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  /** Abre el panel de registro de movimiento para un Material. */
  abrirMovimiento(material: Material): void {
    this.movimientoDe.set(material);
    this.formularioAbierto.set(false);
    this.formMovimiento.reset({ tipo: 'entrada', cantidad: 1, motivo: '' });
  }

  cerrarMovimiento(): void {
    this.movimientoDe.set(null);
  }

  /** Registra un movimiento de inventario sobre el Material seleccionado (Req 18.2). */
  registrarMovimiento(): void {
    const material = this.movimientoDe();
    if (!material || this.formMovimiento.invalid) {
      this.formMovimiento.markAllAsTouched();
      return;
    }
    const v = this.formMovimiento.getRawValue();
    this.guardando.set(true);
    this.service
      .registrarMovimiento(material.id, {
        tipo: v.tipo,
        cantidad: Number(v.cantidad),
        motivo: v.motivo.trim() || null,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Movimiento registrado.');
          this.movimientoDe.set(null);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  /** Baja logica de un Material con confirmacion (Req 18). */
  async eliminar(material: Material): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja material',
      mensaje: `El material "${material.nombre}" quedara inactivo. Deseas continuar?`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.eliminar(material.id).subscribe({
      next: () => {
        this.toast.exito('Material dado de baja.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
