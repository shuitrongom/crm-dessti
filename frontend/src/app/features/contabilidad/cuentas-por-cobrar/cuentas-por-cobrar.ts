// =============================================================================
// Vista de Cuentas por cobrar (CxC) y registro de pagos (Req 36)
// -----------------------------------------------------------------------------
// Listado paginado de CxC con saldo y vencimiento, filtrable por estado. Permite
// registrar un pago de cliente y aplicarlo a una CxC concreta (aplicacion 1 a 1
// desde la fila). El backend valida que el monto no exceda el saldo y, si es
// parcialidad, timbra el complemento de pago. Los importes los calcula el
// servidor; la UI solo los formatea (currency es-MX).
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
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
import { ContabilidadService } from '../services/contabilidad.service';
import { CuentaPorCobrar } from '../models/contabilidad.models';

@Component({
  selector: 'app-contabilidad-cxc',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './cuentas-por-cobrar.html',
  styleUrl: '../contabilidad.scss',
})
export class ContabilidadCuentasPorCobrar {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ContabilidadService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly puedeCobrar = this.auth.tienePermiso('pago_cliente', 'crear');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'factura', encabezado: 'Factura' },
    { clave: 'total', encabezado: 'Total', alineacion: 'fin' },
    { clave: 'saldo', encabezado: 'Saldo', alineacion: 'fin' },
    { clave: 'vencimiento', encabezado: 'Vence' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'pendiente', etiqueta: 'Pendiente' },
    { valor: 'parcial', etiqueta: 'Parcial' },
    { valor: 'pagada', etiqueta: 'Pagada' },
    { valor: 'cancelada', etiqueta: 'Cancelada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<CuentaPorCobrar[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly guardando = signal(false);
  protected readonly cxcSeleccionada = signal<CuentaPorCobrar | null>(null);

  protected readonly formPago = this.fb.nonNullable.group({
    monto: [0.01, [Validators.required, Validators.min(0.01)]],
    formaPago: [''],
    esParcialidad: [false],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarCuentasPorCobrar(null, this.filtroEstado() || null, this.page(), this.size())
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

  aplicarFiltro(valor: string): void {
    this.filtroEstado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  abrirPago(cxc: CuentaPorCobrar): void {
    this.cxcSeleccionada.set(cxc);
    this.formPago.reset({ monto: cxc.saldo, formaPago: '', esParcialidad: false });
  }

  cancelarPago(): void {
    this.cxcSeleccionada.set(null);
  }

  registrarPago(): void {
    const cxc = this.cxcSeleccionada();
    if (!cxc || this.formPago.invalid) {
      this.formPago.markAllAsTouched();
      return;
    }
    const v = this.formPago.getRawValue();
    this.guardando.set(true);
    this.service
      .registrarPago({
        clienteId: cxc.clienteId,
        monto: v.monto,
        formaPago: v.formaPago || null,
        esParcialidad: v.esParcialidad,
        aplicaciones: [{ facturaId: cxc.facturaId, monto: v.monto }],
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Pago registrado y aplicado.');
          this.cxcSeleccionada.set(null);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }
}
