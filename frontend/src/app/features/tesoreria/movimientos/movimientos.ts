// =============================================================================
// Vista de Movimientos bancarios (Req 43.6)
// -----------------------------------------------------------------------------
// Listado paginado de movimientos bancarios filtrable por estado de conciliacion.
// Muestra el monto con signo y el estado de conciliacion (pendiente / conciliado /
// excepcion) como insignia. Solo lectura; la conciliacion se ejecuta desde la
// vista de cuentas bancarias.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';

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
import { TesoreriaService } from '../services/tesoreria.service';
import { MovimientoBancario } from '../models/tesoreria.models';

@Component({
  selector: 'app-tesoreria-movimientos',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatFormFieldModule,
    MatSelectModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './movimientos.html',
  styleUrl: '../tesoreria.scss',
})
export class TesoreriaMovimientos {
  private readonly service = inject(TesoreriaService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'fecha', encabezado: 'Fecha' },
    { clave: 'descripcion', encabezado: 'Concepto' },
    { clave: 'referencia', encabezado: 'Referencia' },
    { clave: 'monto', encabezado: 'Monto', alineacion: 'fin' },
    { clave: 'conciliacion', encabezado: 'Conciliacion' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos' },
    { valor: 'pendiente', etiqueta: 'Pendiente' },
    { valor: 'conciliado', etiqueta: 'Conciliado' },
    { valor: 'excepcion', etiqueta: 'Excepcion' },
  ];

  protected readonly estado = signal<EstadoSolicitud<MovimientoBancario[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarMovimientos(null, this.filtroEstado() || null, this.page(), this.size()).subscribe({
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
}
