// =============================================================================
// Portal del Cliente: Mis quejas / soporte (Req 45.2, 70.1)
// -----------------------------------------------------------------------------
// Listado paginado de las quejas que el Cliente ha levantado desde el portal, con
// un formulario para registrar una nueva (origen 'portal'). El backend acota por
// Cliente (nunca se envía clienteId) y registra la queja en estado 'registrada'.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
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
import { QuejaPortal } from '../models/portal.models';

@Component({
  selector: 'app-portal-quejas',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './portal-quejas.html',
})
export class PortalQuejas {
  private readonly service = inject(PortalService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(NotificacionesService);

  protected readonly humanizar = humanizarEstado;
  protected readonly tono = tonoDeEstado;

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'descripcion', encabezado: 'Descripción' },
    { clave: 'estado', encabezado: 'Estado', alineacion: 'centro' },
    { clave: 'fecha', encabezado: 'Registrada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<QuejaPortal[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly enviando = signal(false);
  protected readonly mostrarForm = signal(false);

  protected readonly formulario = this.fb.nonNullable.group({
    descripcion: ['', [Validators.required, Validators.maxLength(2000)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.misQuejas(this.page(), this.size()).subscribe({
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
      this.formulario.reset({ descripcion: '' });
    }
  }

  enviar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.service.registrarQueja({ descripcion: this.formulario.getRawValue().descripcion.trim() }).subscribe({
      next: () => {
        this.enviando.set(false);
        this.toast.exito('Queja registrada. Le daremos seguimiento.');
        this.mostrarForm.set(false);
        this.page.set(0);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.enviando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
