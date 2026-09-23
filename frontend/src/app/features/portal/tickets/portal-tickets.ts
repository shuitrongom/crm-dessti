// =============================================================================
// Portal del Cliente: Mis tickets de servicio (Req 45.1)
// -----------------------------------------------------------------------------
// Listado paginado de los tickets del Cliente autenticado, con estado, origen y
// fechas de apertura/resolucion. Solo lectura.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';
import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { humanizarEstado, tonoDeEstado } from '../../finanzas-comun/tono-estado';

import { PortalService } from '../services/portal.service';
import { TicketServicio } from '../models/portal.models';

@Component({
  selector: 'app-portal-tickets',
  imports: [
    DatePipe,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './portal-tickets.html',
})
export class PortalTickets {
  private readonly service = inject(PortalService);

  protected readonly humanizar = humanizarEstado;
  protected readonly tono = tonoDeEstado;

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'ticket', encabezado: 'Ticket' },
    { clave: 'origen', encabezado: 'Origen' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'abierto', encabezado: 'Abierto' },
    { clave: 'resuelto', encabezado: 'Resuelto' },
  ];

  protected readonly estado = signal<EstadoSolicitud<TicketServicio[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.misTickets(this.page(), this.size()).subscribe({
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
}
