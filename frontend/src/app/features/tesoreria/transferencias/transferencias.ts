// =============================================================================
// Vista de Transferencias entre cuentas bancarias (Req 43)
// -----------------------------------------------------------------------------
// Registra traspasos internos de fondos entre dos Cuentas_Bancarias de la Empresa
// y lista el histórico. El alta usa un formulario ds-form con selector de cuenta
// origen y destino (deben ser distintas y compartir moneda; el backend reimpone
// ambas reglas), monto, fecha y concepto, con el overlay animado de operación.
// Gobernada por transferencia_bancaria:crear / :listar (deny-by-default).
// =============================================================================

import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
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
import { mensajeDeError, erroresDeCampo } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { TesoreriaService } from '../services/tesoreria.service';
import { CuentaBancaria, TransferenciaBancaria } from '../models/tesoreria.models';

@Component({
  selector: 'app-tesoreria-transferencias',
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
  ],
  templateUrl: './transferencias.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../tesoreria.scss',
})
export class TesoreriaTransferencias {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(TesoreriaService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('transferencia_bancaria', 'crear');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'fecha', encabezado: 'Fecha' },
    { clave: 'origen', encabezado: 'Origen' },
    { clave: 'destino', encabezado: 'Destino' },
    { clave: 'concepto', encabezado: 'Concepto' },
    { clave: 'monto', encabezado: 'Monto', alineacion: 'fin' },
  ];

  protected readonly estado = signal<EstadoSolicitud<TransferenciaBancaria[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly guardando = signal(false);
  protected readonly mostrarForm = signal(false);
  protected readonly cuentas = signal<CuentaBancaria[]>([]);

  protected readonly formulario = this.fb.nonNullable.group({
    cuentaOrigenId: ['', [Validators.required]],
    cuentaDestinoId: ['', [Validators.required]],
    monto: [0.01, [Validators.required, Validators.min(0.01)]],
    fecha: ['', [Validators.required]],
    concepto: [''],
  });

  constructor() {
    this.cargarCuentas();
    this.cargar();
  }

  private cargarCuentas(): void {
    // Solo cuentas activas son candidatas a transferir.
    this.service.listarCuentas(true, 0, 100).subscribe({
      next: (pagina) => this.cuentas.set(pagina.content),
      error: () => this.cuentas.set([]),
    });
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarTransferencias(null, this.page(), this.size()).subscribe({
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

  alternarForm(): void {
    this.mostrarForm.update((v) => !v);
    if (this.mostrarForm()) {
      this.formulario.reset({
        cuentaOrigenId: '',
        cuentaDestinoId: '',
        monto: 0.01,
        fecha: '',
        concepto: '',
      });
    }
  }

  registrar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    const v = this.formulario.getRawValue();
    // Regla del backend, validada también en cliente para feedback inmediato.
    if (v.cuentaOrigenId === v.cuentaDestinoId) {
      this.formulario.controls.cuentaDestinoId.setErrors({ mismaCuenta: true });
      this.toast.error('La cuenta de origen y la de destino deben ser distintas.');
      return;
    }
    this.guardando.set(true);
    this.overlay
      .ejecutar(
        this.service.registrarTransferencia({
          cuentaOrigenId: v.cuentaOrigenId,
          cuentaDestinoId: v.cuentaDestinoId,
          monto: v.monto,
          fecha: v.fecha,
          concepto: v.concepto.trim() || null,
        }),
        {
          tipo: 'traspasar',
          textoProceso: 'Registrando transferencia…',
          textoExito: 'Transferencia registrada',
        },
      )
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Transferencia registrada.');
          this.mostrarForm.set(false);
          this.page.set(0);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          if (e.status === 422) {
            const campos = erroresDeCampo(e);
            this.toast.error(
              campos.length ? campos.map((c) => c.mensaje).join(' ') : mensajeDeError(e),
            );
            return;
          }
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  /** Nombre legible de una cuenta por su id (o un guion si no está cargada). */
  nombreCuenta(id: string): string {
    return this.cuentas().find((c) => c.id === id)?.nombre ?? '—';
  }
}
