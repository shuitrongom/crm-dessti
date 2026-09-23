// =============================================================================
// Vista de Notas de credito / CFDI de egreso (Req 37)
// -----------------------------------------------------------------------------
// Listado paginado con filtro por estado; emision de una Nota de Credito que
// referencia una Factura timbrada (el backend valida que el monto no exceda el
// saldo); y timbrado ante el PAC. El estado y el Folio_Fiscal se muestran en la
// tabla. Cada accion se gobierna por permiso atomico (deny-by-default).
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
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
import { FacturacionService } from '../services/facturacion.service';
import { NotaCredito } from '../models/facturacion.models';

@Component({
  selector: 'app-facturacion-notas-credito',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './notas-credito.html',
  styleUrl: '../facturacion.scss',
})
export class FacturacionNotasCredito {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(FacturacionService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly puedeCrear = this.auth.tienePermiso('nota_credito', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('nota_credito', 'cambiar_estado');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'factura', encabezado: 'Factura' },
    { clave: 'monto', encabezado: 'Monto', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'folio', encabezado: 'Folio fiscal' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'borrador', etiqueta: 'Borrador' },
    { valor: 'timbrada', etiqueta: 'Timbrada' },
    { valor: 'cancelada', etiqueta: 'Cancelada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<NotaCredito[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  protected readonly formAlta = this.fb.nonNullable.group({
    facturaId: ['', [Validators.required]],
    monto: [0.01, [Validators.required, Validators.min(0.01)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarNotasCredito(null, this.filtroEstado() || null, this.page(), this.size()).subscribe({
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
    this.service.emitirNotaCredito({ facturaId: v.facturaId, monto: v.monto }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito('Nota de credito emitida en borrador.');
        this.formAlta.reset({ facturaId: '', monto: 0.01 });
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

  puedeTimbrar(n: NotaCredito): boolean {
    return this.puedeCambiarEstado && n.estado === 'borrador';
  }

  timbrar(n: NotaCredito): void {
    this.service.timbrarNotaCredito(n.id).subscribe({
      next: () => {
        this.toast.exito('Nota de credito timbrada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
