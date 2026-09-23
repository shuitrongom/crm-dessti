// =============================================================================
// Vista de Facturas de proveedor / conciliacion 3 vias (Req 33)
// -----------------------------------------------------------------------------
// Cierra el proceso de compra. Listado paginado con filtros; alta de facturas
// asociadas a una Orden_Compra; conciliacion de tres vias (orden - recepcion -
// factura) que marca la factura conciliada o con discrepancia; y autorizacion de
// pago solo desde el estado conciliada. El indicador de conciliacion se muestra
// como insignia (conciliada/discrepancia). El backend valida cada transicion.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

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
import { ComprasService } from '../services/compras.service';
import { FacturaProveedor } from '../models/compras.models';

@Component({
  selector: 'app-compras-facturas-proveedor',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './facturas-proveedor.html',
  styleUrl: '../compras.scss',
})
export class ComprasFacturasProveedor {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ComprasService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly puedeCrear = this.auth.tienePermiso('factura_proveedor', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('factura_proveedor', 'cambiar_estado');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'folio', encabezado: 'Folio proveedor' },
    { clave: 'orden', encabezado: 'Orden de compra' },
    { clave: 'monto', encabezado: 'Monto', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Conciliacion' },
    { clave: 'fecha', encabezado: 'Registrada' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'registrada', etiqueta: 'Registrada' },
    { valor: 'conciliada', etiqueta: 'Conciliada' },
    { valor: 'discrepancia', etiqueta: 'Discrepancia' },
    { valor: 'pagada', etiqueta: 'Pagada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<FacturaProveedor[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  protected readonly formAlta = this.fb.nonNullable.group({
    ordenCompraId: ['', [Validators.required]],
    folioProveedor: ['', [Validators.required, Validators.maxLength(100)]],
    monto: [0.01, [Validators.required, Validators.min(0.01)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarFacturasProveedor(null, null, this.filtroEstado() || null, this.page(), this.size())
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

  alternarAlta(): void {
    this.mostrarAlta.update((v) => !v);
  }

  puedeConciliar(f: FacturaProveedor): boolean {
    return this.puedeCambiarEstado && f.estado === 'registrada';
  }

  puedeAutorizar(f: FacturaProveedor): boolean {
    return this.puedeCambiarEstado && f.estado === 'conciliada';
  }

  crear(): void {
    if (this.formAlta.invalid) {
      this.formAlta.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const v = this.formAlta.getRawValue();
    this.service
      .registrarFacturaProveedor({
        ordenCompraId: v.ordenCompraId,
        folioProveedor: v.folioProveedor,
        monto: v.monto,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Factura de proveedor registrada.');
          this.formAlta.reset({ ordenCompraId: '', folioProveedor: '', monto: 0.01 });
          this.mostrarAlta.set(false);
          this.page.set(0);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  conciliar(f: FacturaProveedor): void {
    this.service.conciliarFactura(f.id).subscribe({
      next: (actualizada) => {
        if (actualizada.estado === 'discrepancia') {
          this.toast.info('Conciliacion con discrepancia: revisa orden, recepcion y factura.');
        } else {
          this.toast.exito('Factura conciliada.');
        }
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  async autorizarPago(f: FacturaProveedor): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Autorizar pago',
      mensaje: `Autorizar el pago de la factura ${f.folioProveedor}?`,
      textoConfirmar: 'Autorizar',
    });
    if (!ok) {
      return;
    }
    this.service.autorizarPagoFactura(f.id).subscribe({
      next: () => {
        this.toast.exito('Pago autorizado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
