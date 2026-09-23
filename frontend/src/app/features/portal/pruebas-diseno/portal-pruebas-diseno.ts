// =============================================================================
// Portal del Cliente: Mis pruebas de diseno (Req 45.1, 45.2)
// -----------------------------------------------------------------------------
// Listado paginado de las pruebas de diseno del Cliente autenticado con acciones
// de APROBAR y RECHAZAR sobre las pruebas pendientes (Req 45.2). El rechazo genera
// automaticamente la siguiente version pendiente (Req 15.3). El backend acota por
// Cliente y responde 404 si la prueba no es propia; 409 si ya esta decidida.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
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
import { PruebaDiseno } from '../models/portal.models';

@Component({
  selector: 'app-portal-pruebas-diseno',
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './portal-pruebas-diseno.html',
})
export class PortalPruebasDiseno {
  private readonly service = inject(PortalService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);

  protected readonly humanizar = humanizarEstado;
  protected readonly tono = tonoDeEstado;

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'version', encabezado: 'Version', alineacion: 'centro' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'decidida', encabezado: 'Decidida' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estado = signal<EstadoSolicitud<PruebaDiseno[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly procesando = signal(false);

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.misPruebasDiseno(this.page(), this.size()).subscribe({
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

  async aprobar(p: PruebaDiseno): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Aprobar diseno',
      mensaje: `Aprobar la version ${p.numeroVersion} de la prueba de diseno? Esta decision es definitiva.`,
      textoConfirmar: 'Aprobar',
    });
    if (!ok) {
      return;
    }
    this.procesando.set(true);
    this.service.aprobarPrueba(p.id).subscribe({
      next: () => {
        this.procesando.set(false);
        this.toast.exito('Prueba de diseno aprobada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  async rechazar(p: PruebaDiseno): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Rechazar diseno',
      mensaje: `Rechazar la version ${p.numeroVersion}? Se generara una nueva version para su revision.`,
      textoConfirmar: 'Rechazar',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.procesando.set(true);
    this.service.rechazarPrueba(p.id).subscribe({
      next: (resultado) => {
        this.procesando.set(false);
        this.toast.exito(
          `Prueba rechazada. Nueva version ${resultado.nuevaVersion.numeroVersion} pendiente.`,
        );
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
