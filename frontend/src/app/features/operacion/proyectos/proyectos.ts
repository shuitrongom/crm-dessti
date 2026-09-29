// =============================================================================
// Vista de Proyectos (Req 21) — listado paginado premium
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por cliente; el alta/edicion de un
// proyecto se hace en un MODAL animado (ProyectoFormDialog). Enlaza al detalle con
// el avance consolidado por sitio. Acciones gobernadas por permiso proyecto:{...}.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ProyectosService } from '../services/proyectos.service';
import { NombresOperacionService } from '../services/nombres-operacion.service';
import { Proyecto } from '../models/operacion.models';
import { ProyectoFormDialog, ProyectoFormDialogData } from './proyecto-form-dialog';

@Component({
  selector: 'app-operacion-proyectos',
  imports: [
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './proyectos.html',
  styleUrl: './proyectos.scss',
})
export class OperacionProyectos {
  private readonly service = inject(ProyectosService);
  protected readonly nombres = inject(NombresOperacionService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeCrear = this.auth.tienePermiso('proyecto', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('proyecto', 'actualizar');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly proyectos = signal<Proyecto[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly clienteId = signal('');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Proyecto' },
    { clave: 'clienteId', encabezado: 'Cliente' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  constructor() {
    // Carga los catalogos de nombres (Cliente) para resolver la columna Cliente
    // sin exponer UUIDs (Req 10.2); luego el listado.
    this.nombres.cargar().subscribe({
      next: () => this.cargar(),
      error: () => this.cargar(),
    });
  }

  /** Nombre legible del Cliente de un proyecto (nunca el UUID). */
  protected nombreCliente(clienteId: string | null | undefined): string {
    return this.nombres.nombreCliente(clienteId);
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service.listar(this.clienteId().trim() || null, this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.proyectos.set(pagina.content);
        this.total.set(pagina.totalElements);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  filtrarPorCliente(valor: string): void {
    this.clienteId.set(valor);
    this.page.set(0);
    this.cargar();
  }

  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Abre el modal de alta de Proyecto y recarga si se creó. */
  nuevo(): void {
    this.abrirFormulario();
  }

  /** Abre el modal de edición con los datos del Proyecto y recarga si cambió. */
  editar(proyecto: Proyecto): void {
    this.abrirFormulario(proyecto);
  }

  /**
   * Abre el modal de formulario de Proyecto (alta si no se pasa `proyecto`,
   * edición si se pasa) y recarga el listado cuando el diálogo confirma.
   */
  private abrirFormulario(proyecto?: Proyecto): void {
    const data: ProyectoFormDialogData = { proyecto };
    const ref = this.dialog.open(ProyectoFormDialog, {
      width: 'min(680px, 96vw)',
      maxWidth: 'min(680px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((guardado?: Proyecto) => {
      if (guardado) {
        this.toast.exito(proyecto ? 'Proyecto actualizado.' : 'Proyecto creado.');
        this.cargar();
      }
    });
  }
}
