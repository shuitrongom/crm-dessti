// =============================================================================
// Vista de Ordenes de compra (Req 31)
// -----------------------------------------------------------------------------
// Segunda via del proceso de compra. Listado paginado con filtros por proveedor
// y estado, alta directa (proveedor + partidas con precio unitario) y acciones de
// cambio de estado. El total lo calcula y devuelve el servidor (escala 2); la UI
// solo lo formatea con el pipe currency es-MX. Las recepciones se registran en la
// vista de Recepciones enlazando la Orden_Compra.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { humanizarEstado, tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { ComprasService } from '../services/compras.service';
import { OrdenCompra } from '../models/compras.models';

/** Transiciones ofrecidas por estado actual (el backend valida la maquina). */
const TRANSICIONES: Record<string, { estado: string; etiqueta: string; destructiva: boolean }[]> = {
  abierta: [{ estado: 'cancelada', etiqueta: 'Cancelar', destructiva: true }],
  recibida_parcial: [{ estado: 'cancelada', etiqueta: 'Cancelar', destructiva: true }],
  recibida_total: [{ estado: 'cerrada', etiqueta: 'Cerrar', destructiva: false }],
};

@Component({
  selector: 'app-compras-ordenes-compra',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './ordenes-compra.html',
  styleUrl: '../compras.scss',
})
export class ComprasOrdenesCompra {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ComprasService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly puedeCrear = this.auth.tienePermiso('orden_compra', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('orden_compra', 'cambiar_estado');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'id', encabezado: 'Folio' },
    { clave: 'proveedor', encabezado: 'Proveedor' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'total', encabezado: 'Total', alineacion: 'fin' },
    { clave: 'fecha', encabezado: 'Creada' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'abierta', etiqueta: 'Abierta' },
    { valor: 'recibida_parcial', etiqueta: 'Recibida parcial' },
    { valor: 'recibida_total', etiqueta: 'Recibida total' },
    { valor: 'cerrada', etiqueta: 'Cerrada' },
    { valor: 'cancelada', etiqueta: 'Cancelada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<OrdenCompra[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly filtroProveedor = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  protected readonly formAlta = this.fb.nonNullable.group({
    proveedorId: ['', [Validators.required]],
    partidas: this.fb.array([this.crearPartida()]),
  });

  protected get partidas(): FormArray {
    return this.formAlta.get('partidas') as FormArray;
  }

  constructor() {
    this.cargar();
  }

  private crearPartida() {
    return this.fb.nonNullable.group({
      materialId: ['', [Validators.required]],
      cantidad: [1, [Validators.required, Validators.min(1), Validators.max(999999)]],
      precioUnitario: [0.01, [Validators.required, Validators.min(0.01)]],
    });
  }

  agregarPartida(): void {
    this.partidas.push(this.crearPartida());
  }

  quitarPartida(indice: number): void {
    if (this.partidas.length > 1) {
      this.partidas.removeAt(indice);
    }
  }

  transicionesDe(estado: string): { estado: string; etiqueta: string; destructiva: boolean }[] {
    return TRANSICIONES[estado] ?? [];
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarOrdenesCompra(
        this.filtroProveedor() || null,
        this.filtroEstado() || null,
        this.page(),
        this.size(),
      )
      .subscribe({
        next: (pagina) => {
          this.total.set(pagina.totalElements);
          this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
        },
        error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
      });
  }

  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  aplicarFiltroEstado(valor: string): void {
    this.filtroEstado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  aplicarFiltroProveedor(valor: string): void {
    this.filtroProveedor.set(valor.trim());
    this.page.set(0);
    this.cargar();
  }

  alternarAlta(): void {
    this.mostrarAlta.update((v) => !v);
  }

  crear(): void {
    if (this.formAlta.invalid) {
      this.formAlta.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const v = this.formAlta.getRawValue();
    this.service
      .crearOrdenCompra({
        proveedorId: v.proveedorId,
        partidas: v.partidas as { materialId: string; cantidad: number; precioUnitario: number }[],
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Orden de compra creada.');
          this.formAlta.reset({ proveedorId: '' });
          this.formAlta.setControl('partidas', this.fb.array([this.crearPartida()]));
          this.mostrarAlta.set(false);
          this.page.set(0);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  async cambiarEstado(
    orden: OrdenCompra,
    transicion: { estado: string; etiqueta: string; destructiva: boolean },
  ): Promise<void> {
    if (transicion.destructiva) {
      const ok = await this.confirm.confirmar({
        titulo: transicion.etiqueta,
        mensaje: `Confirmas "${transicion.etiqueta.toLowerCase()}" de la orden de compra?`,
        textoConfirmar: transicion.etiqueta,
        destructiva: true,
      });
      if (!ok) {
        return;
      }
    }
    this.service.cambiarEstadoOrdenCompra(orden.id, transicion.estado).subscribe({
      next: () => {
        this.toast.exito('Estado actualizado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
