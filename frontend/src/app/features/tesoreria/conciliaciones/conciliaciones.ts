// =============================================================================
// Vista de Conciliaciones bancarias (Req 43.6)
// -----------------------------------------------------------------------------
// Listado paginado de las conciliaciones bancarias del tenant, filtrable por
// cuenta y por estado (en proceso / completa). Cada fila resume el saldo bancario
// frente al saldo contable y la diferencia resultante; una conciliacion solo es
// "completa" cuando la diferencia es cero y no hay movimientos en excepcion
// (Property 18, garantizada por el backend). Vista de solo lectura: la ejecucion
// de la conciliacion vive en la vista de Cuentas bancarias.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';

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
import { ConciliacionBancaria, CuentaBancaria } from '../models/tesoreria.models';

@Component({
  selector: 'app-tesoreria-conciliaciones',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './conciliaciones.html',
  styleUrl: '../tesoreria.scss',
})
export class TesoreriaConciliaciones {
  private readonly service = inject(TesoreriaService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'fecha', encabezado: 'Fecha' },
    { clave: 'cuenta', encabezado: 'Cuenta' },
    { clave: 'saldoBancario', encabezado: 'Saldo banco', alineacion: 'fin' },
    { clave: 'saldoContable', encabezado: 'Saldo contable', alineacion: 'fin' },
    { clave: 'diferencia', encabezado: 'Diferencia', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado', alineacion: 'centro' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'en_proceso', etiqueta: 'En proceso' },
    { valor: 'completa', etiqueta: 'Completa' },
  ];

  protected readonly estado = signal<EstadoSolicitud<ConciliacionBancaria[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroCuenta = signal('');
  protected readonly filtroEstado = signal('');

  /** Cuentas para el selector de filtro (se cargan una vez). */
  protected readonly cuentas = signal<CuentaBancaria[]>([]);

  /** Resumen de la pagina visible: cuantas completas vs en proceso. */
  protected readonly resumen = computed(() => {
    const filas = this.estado().datos ?? [];
    const completas = filas.filter((c) => c.estado === 'completa').length;
    return { completas, enProceso: filas.length - completas, total: filas.length };
  });

  constructor() {
    this.cargarCuentas();
    this.cargar();
  }

  private cargarCuentas(): void {
    this.service.listarCuentas(null, 0, 100).subscribe({
      next: (pagina) => this.cuentas.set(pagina.content),
      // Silencioso: el filtro por cuenta es una comodidad, no un bloqueante.
      error: () => this.cuentas.set([]),
    });
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarConciliaciones(
        this.filtroCuenta() || null,
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

  filtrarPorCuenta(valor: string): void {
    this.filtroCuenta.set(valor);
    this.page.set(0);
    this.cargar();
  }

  filtrarPorEstado(valor: string): void {
    this.filtroEstado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  /** Nombre legible de la cuenta de una conciliacion (o su id si no esta cargada). */
  nombreCuenta(cuentaBancariaId: string): string {
    return this.cuentas().find((c) => c.id === cuentaBancariaId)?.nombre ?? '—';
  }

  /** La diferencia es cero cuando redondea a 0 centavos (evita ruido de coma flotante). */
  diferenciaEsCero(diferencia: number): boolean {
    return Math.round(diferencia * 100) === 0;
  }
}
