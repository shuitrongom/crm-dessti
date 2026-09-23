// =============================================================================
// Portal del Cliente: Mis cotizaciones (Req 45.1)
// -----------------------------------------------------------------------------
// Listado paginado de las cotizaciones del Cliente autenticado (el backend acota
// por Cliente; la UI nunca envia clienteId). Solo lectura.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe, DatePipe } from '@angular/common';

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
import { Cotizacion } from '../models/portal.models';

@Component({
  selector: 'app-portal-cotizaciones',
  imports: [
    CurrencyPipe,
    DatePipe,
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

  protected readonly humanizar = humanizarEstado;
  protected readonly tono = tonoDeEstado;

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'folio', encabezado: 'Cotizacion' },
    { clave: 'partidas', encabezado: 'Partidas', alineacion: 'centro' },
    { clave: 'total', encabezado: 'Total', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'fecha', encabezado: 'Fecha' },
  ];

  protected readonly estado = signal<EstadoSolicitud<Cotizacion[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);

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
}
