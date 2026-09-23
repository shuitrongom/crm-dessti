// =============================================================================
// Portal del Cliente: Mis facturas (Req 45.1)
// -----------------------------------------------------------------------------
// Listado paginado de las facturas CFDI del Cliente autenticado, con folio
// fiscal, total, estado y fecha de timbrado. Solo lectura.
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
import { Factura } from '../models/portal.models';

@Component({
  selector: 'app-portal-facturas',
  imports: [
    CurrencyPipe,
    DatePipe,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './portal-facturas.html',
})
export class PortalFacturas {
  private readonly service = inject(PortalService);

  protected readonly humanizar = humanizarEstado;
  protected readonly tono = tonoDeEstado;

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'folio', encabezado: 'Folio fiscal' },
    { clave: 'total', encabezado: 'Total', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'timbrado', encabezado: 'Timbrado' },
  ];

  protected readonly estado = signal<EstadoSolicitud<Factura[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.misFacturas(this.page(), this.size()).subscribe({
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
