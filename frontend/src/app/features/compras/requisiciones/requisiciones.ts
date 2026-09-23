// =============================================================================
// Vista de Requisiciones de compra (Req 30)
// -----------------------------------------------------------------------------
// Listado paginado con filtro por estado, alta de requisiciones con partidas
// (Material + cantidad) y acciones de cambio de estado gobernadas por la maquina
// de estados del backend (borrador -> enviada -> aprobada/rechazada; cancelada).
// La generacion de Orden_Compra desde una requisicion aprobada tambien se ofrece.
// Todas las acciones se gobiernan por permiso atomico (deny-by-default) y el
// backend reimpone la autorizacion y las transiciones validas.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
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
import { RequisicionCompra } from '../models/compras.models';

/** Transiciones ofrecidas en la UI por estado actual (el backend valida). */
const TRANSICIONES: Record<string, { estado: string; etiqueta: string; destructiva: boolean }[]> = {
  borrador: [
    { estado: 'enviada', etiqueta: 'Enviar a revision', destructiva: false },
    { estado: 'cancelada', etiqueta: 'Cancelar', destructiva: true },
  ],
  enviada: [
    { estado: 'aprobada', etiqueta: 'Aprobar', destructiva: false },
    { estado: 'rechazada', etiqueta: 'Rechazar', destructiva: true },
    { estado: 'cancelada', etiqueta: 'Cancelar', destructiva: true },
  ],
  aprobada: [{ estado: 'cancelada', etiqueta: 'Cancelar', destructiva: true }],
};

@Component({
  selector: 'app-compras-requisiciones',
  imports: [
    ReactiveFormsModule,
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
  templateUrl: './requisiciones.html',
  styleUrl: '../compras.scss',
})
export class ComprasRequisiciones {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ComprasService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly puedeCrear = this.auth.tienePermiso('requisicion_compra', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso(
    'requisicion_compra',
    'cambiar_estado',
  );

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'id', encabezado: 'Folio' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'partidas', encabezado: 'Partidas', alineacion: 'centro' },
    { clave: 'orden', encabezado: 'Orden de compra' },
    { clave: 'fecha', encabezado: 'Creada' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'borrador', etiqueta: 'Borrador' },
    { valor: 'enviada', etiqueta: 'Enviada' },
    { valor: 'aprobada', etiqueta: 'Aprobada' },
    { valor: 'rechazada', etiqueta: 'Rechazada' },
    { valor: 'cancelada', etiqueta: 'Cancelada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<RequisicionCompra[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  /** Formulario de alta con arreglo de partidas (al menos una). */
  protected readonly formAlta = this.fb.nonNullable.group({
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
    this.service.listarRequisiciones(this.filtroEstado() || null, this.page(), this.size()).subscribe({
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

  aplicarFiltro(valor: string): void {
    this.filtroEstado.set(valor);
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
    const partidas = this.partidas.getRawValue() as { materialId: string; cantidad: number }[];
    this.service.crearRequisicion({ partidas }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito('Requisicion creada en borrador.');
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
    req: RequisicionCompra,
    transicion: { estado: string; etiqueta: string; destructiva: boolean },
  ): Promise<void> {
    if (transicion.destructiva) {
      const ok = await this.confirm.confirmar({
        titulo: transicion.etiqueta,
        mensaje: `Confirmas "${transicion.etiqueta.toLowerCase()}" de la requisicion?`,
        textoConfirmar: transicion.etiqueta,
        destructiva: true,
      });
      if (!ok) {
        return;
      }
    }
    this.service.cambiarEstadoRequisicion(req.id, transicion.estado).subscribe({
      next: () => {
        this.toast.exito('Estado actualizado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
