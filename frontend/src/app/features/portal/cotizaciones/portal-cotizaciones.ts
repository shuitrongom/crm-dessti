// =============================================================================
// Portal del Cliente: Mis cotizaciones (Req 45.1)
// -----------------------------------------------------------------------------
// Listado paginado de las cotizaciones del Cliente autenticado (el backend acota
// por Cliente; la UI nunca envia clienteId). Solo lectura.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe, DatePipe } from '@angular/common';
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
import { Cotizacion } from '../models/portal.models';

@Component({
  selector: 'app-portal-cotizaciones',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './portal-cotizaciones.html',
})
export class PortalCotizaciones {
  private readonly service = inject(PortalService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);

  protected readonly humanizar = humanizarEstado;
  protected readonly tono = tonoDeEstado;

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'folio', encabezado: 'Cotizacion' },
    { clave: 'partidas', encabezado: 'Partidas', alineacion: 'centro' },
    { clave: 'total', encabezado: 'Total', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'fecha', encabezado: 'Fecha' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estado = signal<EstadoSolicitud<Cotizacion[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly procesando = signal(false);

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.misCotizaciones(this.page(), this.size()).subscribe({
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

  /** El cliente solo puede decidir una cotización que le fue enviada. */
  puedeDecidir(c: Cotizacion): boolean {
    return c.estado === 'enviada';
  }

  async aprobar(c: Cotizacion): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Aprobar cotización',
      mensaje: '¿Aprobar esta cotización? Esta decisión es definitiva y autoriza el trabajo.',
      textoConfirmar: 'Aprobar',
    });
    if (!ok) {
      return;
    }
    this.procesando.set(true);
    this.service.aprobarCotizacion(c.id).subscribe({
      next: () => {
        this.procesando.set(false);
        this.toast.exito('Cotización aprobada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  async rechazar(c: Cotizacion): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Rechazar cotización',
      mensaje: '¿Rechazar esta cotización? Esta decisión es definitiva.',
      textoConfirmar: 'Rechazar',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.procesando.set(true);
    this.service.rechazarCotizacion(c.id).subscribe({
      next: () => {
        this.procesando.set(false);
        this.toast.exito('Cotización rechazada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
