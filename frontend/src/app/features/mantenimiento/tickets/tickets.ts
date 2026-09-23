// =============================================================================
// Vista de Tickets de servicio con SLA (Req 20.2 - 20.7)
// -----------------------------------------------------------------------------
// Listado paginado filtrable por estado y por vencimiento del SLA; generacion de
// tickets; asignacion a tecnico/cuadrilla; y avance por la maquina de estados
// (abierto -> asignado -> en_proceso -> resuelto -> cerrado). Se muestra el
// indicador de cumplimiento del SLA (respuesta/resolucion). El backend valida las
// transiciones y registra el cumplimiento del SLA al resolver.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

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
import { MantenimientoService } from '../services/mantenimiento.service';
import { TicketServicio } from '../models/mantenimiento.models';
import { AccionTicket, avanceDeTicket } from '../ticket-estados';

@Component({
  selector: 'app-mantenimiento-tickets',
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
    EstadoChip,
  ],
  templateUrl: './tickets.html',
  styleUrl: '../mantenimiento.scss',
})
export class MantenimientoTickets {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MantenimientoService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;
  protected readonly avanceDe = avanceDeTicket;

  protected readonly puedeCrear = this.auth.tienePermiso('ticket_servicio', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('ticket_servicio', 'cambiar_estado');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'id', encabezado: 'Folio' },
    { clave: 'cliente', encabezado: 'Cliente' },
    { clave: 'origen', encabezado: 'Origen' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'sla', encabezado: 'SLA' },
    { clave: 'abierto', encabezado: 'Abierto' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'abierto', etiqueta: 'Abierto' },
    { valor: 'asignado', etiqueta: 'Asignado' },
    { valor: 'en_proceso', etiqueta: 'En proceso' },
    { valor: 'resuelto', etiqueta: 'Resuelto' },
    { valor: 'cerrado', etiqueta: 'Cerrado' },
  ];

  protected readonly estado = signal<EstadoSolicitud<TicketServicio[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly filtroSlaVencido = signal<boolean | null>(null);
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);
  protected readonly asignando = signal<TicketServicio | null>(null);

  protected readonly formAlta = this.fb.nonNullable.group({
    contratoMantenimientoId: [''],
    clienteId: ['', [Validators.required]],
    origen: ['manual', [Validators.required]],
  });

  protected readonly formAsignar = this.fb.nonNullable.group({
    asignadoTipo: ['tecnico', [Validators.required]],
    asignadoId: ['', [Validators.required]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarTickets(this.filtroEstado() || null, this.filtroSlaVencido(), this.page(), this.size())
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

  aplicarFiltroSla(valor: string): void {
    this.filtroSlaVencido.set(valor === '' ? null : valor === 'true');
    this.page.set(0);
    this.cargar();
  }

  alternarAlta(): void {
    this.mostrarAlta.update((v) => !v);
  }

  /** Etiqueta del cumplimiento global del SLA para la insignia. */
  etiquetaSla(t: TicketServicio): string {
    if (t.slaResolucionCumplido === null && t.slaRespuestaCumplido === null) {
      return 'En curso';
    }
    const cumplido = t.slaResolucionCumplido !== false && t.slaRespuestaCumplido !== false;
    return cumplido ? 'SLA cumplido' : 'SLA incumplido';
  }

  tonoSla(t: TicketServicio): 'neutro' | 'exito' | 'error' {
    if (t.slaResolucionCumplido === null && t.slaRespuestaCumplido === null) {
      return 'neutro';
    }
    return t.slaResolucionCumplido !== false && t.slaRespuestaCumplido !== false ? 'exito' : 'error';
  }

  generar(): void {
    if (this.formAlta.invalid) {
      this.formAlta.markAllAsTouched();
      return;
    }
    const v = this.formAlta.getRawValue();
    this.guardando.set(true);
    this.service
      .generarTicket({
        contratoMantenimientoId: v.contratoMantenimientoId || null,
        clienteId: v.clienteId,
        origen: v.origen,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Ticket generado.');
          this.formAlta.reset({ contratoMantenimientoId: '', clienteId: '', origen: 'manual' });
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

  abrirAsignacion(ticket: TicketServicio): void {
    this.asignando.set(ticket);
    this.formAsignar.reset({ asignadoTipo: 'tecnico', asignadoId: '' });
  }

  cancelarAsignacion(): void {
    this.asignando.set(null);
  }

  asignar(): void {
    const ticket = this.asignando();
    if (!ticket || this.formAsignar.invalid) {
      this.formAsignar.markAllAsTouched();
      return;
    }
    const v = this.formAsignar.getRawValue();
    this.service.asignarTicket(ticket.id, { asignadoTipo: v.asignadoTipo, asignadoId: v.asignadoId }).subscribe({
      next: () => {
        this.toast.exito('Ticket asignado.');
        this.asignando.set(null);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  async avanzar(ticket: TicketServicio, accion: AccionTicket): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: accion.etiqueta,
      mensaje: `Confirmas "${accion.etiqueta.toLowerCase()}" del ticket?`,
      textoConfirmar: accion.etiqueta,
    });
    if (!ok) {
      return;
    }
    this.service.cambiarEstadoTicket(ticket.id, accion.estado).subscribe({
      next: () => {
        this.toast.exito('Estado actualizado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
