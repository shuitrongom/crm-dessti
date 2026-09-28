// =============================================================================
// Vista de Estado de cuenta por cliente (Req 39.1) — solo lectura
// -----------------------------------------------------------------------------
// Muestra el detalle de las Cuentas_Por_Cobrar de un cliente en un periodo, con su
// total original, saldo pendiente, estado y fechas, más los grandes totales
// facturado y por cobrar. La UI elige cliente y periodo; el backend agrega los
// datos (no muta nada). Gobernada por reporte_financiero:leer (deny-by-default).
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { humanizarEstado, tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { ClientesService } from '../../comercial/services/clientes.service';
import { Cliente } from '../../comercial/models/comercial.models';
import { ContabilidadService } from '../services/contabilidad.service';
import { EstadoCuentaCliente } from '../models/contabilidad.models';

@Component({
  selector: 'app-contabilidad-estado-cuenta-cliente',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './estado-cuenta-cliente.html',
  styleUrl: '../contabilidad.scss',
})
export class ContabilidadEstadoCuentaCliente {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ContabilidadService);
  private readonly clientesService = inject(ClientesService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly clientes = signal<Cliente[]>([]);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly resultado = signal<EstadoCuentaCliente | null>(null);

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'fechaEmision', encabezado: 'Emisión' },
    { clave: 'factura', encabezado: 'Factura' },
    { clave: 'total', encabezado: 'Total', alineacion: 'fin' },
    { clave: 'saldo', encabezado: 'Saldo', alineacion: 'fin' },
    { clave: 'vencimiento', encabezado: 'Vence' },
    { clave: 'estado', encabezado: 'Estado', alineacion: 'centro' },
  ];

  protected readonly formulario = this.fb.nonNullable.group({
    clienteId: ['', [Validators.required]],
    desde: [''],
    hasta: [''],
  });

  /** Nombre del cliente seleccionado, para el encabezado del resultado. */
  protected readonly nombreClienteSeleccionado = computed(() => {
    const id = this.resultado()?.clienteId;
    return this.clientes().find((c) => c.id === id)?.nombre ?? '';
  });

  constructor() {
    this.cargarClientes();
  }

  private cargarClientes(): void {
    this.clientesService.listar(null, 0, 100).subscribe({
      next: (pagina) => this.clientes.set(pagina.content),
      error: () => this.clientes.set([]),
    });
  }

  consultar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    const v = this.formulario.getRawValue();
    this.cargando.set(true);
    this.error.set(null);
    this.service.estadoCuentaCliente(v.clienteId, v.desde || null, v.hasta || null).subscribe({
      next: (r) => {
        this.resultado.set(r);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }
}
