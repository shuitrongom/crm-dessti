// =============================================================================
// Vista de Facturas CFDI (Req 34, 35)
// -----------------------------------------------------------------------------
// Listado paginado con filtro por estado; emision de una Factura desde una
// Cotizacion o una Orden_Fabricacion; timbrado ante el PAC (muestra Folio_Fiscal
// y estado); y cancelacion con un motivo del catalogo del SAT. Los importes los
// calcula el servidor; la UI solo los formatea (currency es-MX). Cada accion se
// gobierna por permiso atomico (deny-by-default) y el backend valida la maquina
// de estados (borrador -> timbrada -> cancelada).
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
import { FacturacionService } from '../services/facturacion.service';
import { Factura, MOTIVOS_CANCELACION_SAT } from '../models/facturacion.models';

@Component({
  selector: 'app-facturacion-facturas',
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
  templateUrl: './facturas.html',
  styleUrl: '../facturacion.scss',
})
export class FacturacionFacturas {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(FacturacionService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;
  protected readonly motivosSat = MOTIVOS_CANCELACION_SAT;

  protected readonly puedeCrear = this.auth.tienePermiso('factura', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('factura', 'cambiar_estado');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'receptor', encabezado: 'Receptor' },
    { clave: 'folio', encabezado: 'Folio fiscal' },
    { clave: 'total', encabezado: 'Total', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'timbrado', encabezado: 'Timbrado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'borrador', etiqueta: 'Borrador' },
    { valor: 'timbrada', etiqueta: 'Timbrada' },
    { valor: 'cancelacion_en_proceso', etiqueta: 'Cancelación en proceso' },
    { valor: 'cancelada', etiqueta: 'Cancelada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<Factura[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  protected readonly formAlta = this.fb.nonNullable.group({
    origen: ['cotizacion', [Validators.required]],
    origenId: ['', [Validators.required]],
    receptorRfc: ['', [Validators.required]],
    receptorNombre: ['', [Validators.required]],
    receptorCp: ['', [Validators.required]],
    receptorRegimenFiscal: ['', [Validators.required]],
    usoCfdi: ['', [Validators.required]],
    tasaRetencion: [0, [Validators.min(0), Validators.max(1)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarFacturas(null, this.filtroEstado() || null, this.page(), this.size()).subscribe({
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

  emitir(): void {
    if (this.formAlta.invalid) {
      this.formAlta.markAllAsTouched();
      return;
    }
    const v = this.formAlta.getRawValue();
    this.guardando.set(true);
    this.service
      .emitirFactura({
        cotizacionId: v.origen === 'cotizacion' ? v.origenId : null,
        ordenFabricacionId: v.origen === 'orden_fabricacion' ? v.origenId : null,
        receptorRfc: v.receptorRfc,
        receptorNombre: v.receptorNombre,
        receptorCp: v.receptorCp,
        receptorRegimenFiscal: v.receptorRegimenFiscal,
        usoCfdi: v.usoCfdi,
        tasaRetencion: v.tasaRetencion ?? null,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Factura emitida en borrador.');
          this.formAlta.reset({ origen: 'cotizacion', origenId: '', tasaRetencion: 0 });
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

  puedeTimbrar(f: Factura): boolean {
    return this.puedeCambiarEstado && f.estado === 'borrador';
  }

  puedeCancelar(f: Factura): boolean {
    return this.puedeCambiarEstado && f.estado === 'timbrada';
  }

  async timbrar(f: Factura): Promise<void> {
    // El timbrado emite un CFDI oficial ante el PAC/SAT y no es reversible sin
    // una cancelacion posterior; se confirma como accion sensible (Req 54.2).
    const ok = await this.confirm.confirmar({
      titulo: 'Timbrar factura',
      mensaje:
        'Se emitira el CFDI ante el PAC y quedara timbrado oficialmente. Esta accion no se puede deshacer, solo cancelar. Deseas continuar?',
      textoConfirmar: 'Timbrar factura',
    });
    if (!ok) {
      return;
    }
    this.service.timbrarFactura(f.id).subscribe({
      next: () => {
        this.toast.exito('Factura timbrada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  async cancelar(f: Factura, motivoSat: string): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Cancelar factura',
      mensaje: `Cancelar el CFDI con motivo SAT ${motivoSat}? Esta accion se reporta al SAT.`,
      textoConfirmar: 'Cancelar CFDI',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.cancelarFactura(f.id, motivoSat).subscribe({
      next: () => {
        this.toast.exito('Cancelación solicitada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
