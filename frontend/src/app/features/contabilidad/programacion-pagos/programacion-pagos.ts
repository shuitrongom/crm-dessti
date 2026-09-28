// =============================================================================
// Vista de Programación de pagos (CxP) (Req 42.2, 42.6)
// -----------------------------------------------------------------------------
// Calendario de pagos a proveedores: programa una fecha y un monto sobre una
// Cuenta_Por_Pagar con saldo y consulta el histórico de programaciones (aplicadas
// o pendientes de aplicar). Ayuda a la gestión del flujo de caja: qué se paga y
// cuándo. El alta usa el overlay animado de operación; el backend valida el monto
// positivo y la existencia de la CxP. Gobernada por permisos atómicos
// (programacion_pago:crear / :listar), reimpuestos por el backend.
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
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { ContabilidadService } from '../services/contabilidad.service';
import { CuentaPorPagar, ProgramacionPago } from '../models/contabilidad.models';

@Component({
  selector: 'app-contabilidad-programacion-pagos',
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
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './programacion-pagos.html',
  styleUrl: '../contabilidad.scss',
})
export class ContabilidadProgramacionPagos {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ContabilidadService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('programacion_pago', 'crear');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'fechaProgramada', encabezado: 'Fecha programada' },
    { clave: 'cuenta', encabezado: 'Cuenta por pagar' },
    { clave: 'monto', encabezado: 'Monto', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado', alineacion: 'centro' },
  ];

  protected readonly estado = signal<EstadoSolicitud<ProgramacionPago[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly guardando = signal(false);
  protected readonly mostrarForm = signal(false);

  /** CxP con saldo pendiente, para el selector del formulario. */
  protected readonly cxpPendientes = signal<CuentaPorPagar[]>([]);

  /** Resumen de la página: monto total programado y cuántas pendientes de aplicar. */
  protected readonly resumen = computed(() => {
    const filas = this.estado().datos ?? [];
    const pendientes = filas.filter((p) => !p.aplicada);
    const montoPendiente = pendientes.reduce((acc, p) => acc + p.monto, 0);
    return { pendientes: pendientes.length, montoPendiente };
  });

  protected readonly formulario = this.fb.nonNullable.group({
    cuentaPorPagarId: ['', [Validators.required]],
    fechaProgramada: ['', [Validators.required]],
    monto: [0.01, [Validators.required, Validators.min(0.01)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarProgramacionesPago(null, this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.total.set(pagina.totalElements);
        this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  private cargarCxpPendientes(): void {
    // Solo CxP con saldo (pendiente/parcial) son candidatas a programar un pago.
    this.service.listarCuentasPorPagar(null, 'pendiente', 0, 100).subscribe({
      next: (pagina) => this.cxpPendientes.set(pagina.content),
      error: () => this.cxpPendientes.set([]),
    });
  }

  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  abrirForm(): void {
    this.cargarCxpPendientes();
    this.formulario.reset({ cuentaPorPagarId: '', fechaProgramada: '', monto: 0.01 });
    this.mostrarForm.set(true);
  }

  cancelar(): void {
    this.mostrarForm.set(false);
  }

  /** Al elegir una CxP, sugiere su saldo como monto por defecto. */
  alSeleccionarCxp(cuentaPorPagarId: string): void {
    const cxp = this.cxpPendientes().find((c) => c.id === cuentaPorPagarId);
    if (cxp) {
      this.formulario.controls.monto.setValue(cxp.saldo);
    }
  }

  guardar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    const v = this.formulario.getRawValue();
    this.guardando.set(true);
    this.overlay
      .ejecutar(
        this.service.crearProgramacionPago({
          cuentaPorPagarId: v.cuentaPorPagarId,
          fechaProgramada: v.fechaProgramada,
          monto: v.monto,
        }),
        { tipo: 'crear', textoProceso: 'Programando pago…', textoExito: 'Pago programado' },
      )
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Pago programado.');
          this.mostrarForm.set(false);
          this.page.set(0);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  /** Etiqueta legible de la CxP (folio de factura de proveedor o su id). */
  etiquetaCxp(cuentaPorPagarId: string): string {
    const cxp = this.cxpPendientes().find((c) => c.id === cuentaPorPagarId);
    if (!cxp) {
      return cuentaPorPagarId.slice(0, 8);
    }
    return cxp.facturaProveedorId.slice(0, 8);
  }
}
