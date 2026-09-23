// =============================================================================
// Portal del Cliente: Mis proyectos y avance por sitio (Req 45.1)
// -----------------------------------------------------------------------------
// Listado paginado de los proyectos del Cliente y, al expandir uno, el avance
// consolidado por sitio (cuatro fases: levantamiento, permiso, fabricacion,
// instalacion). El avance por sitio se resume en un porcentaje 0..100 y un estado
// derivado que se muestra con ProgressBadge (barra + insignia accesible).
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ProgressBadge } from '../../../shared/components/progress-badge/progress-badge';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';
import { humanizarEstado } from '../../finanzas-comun/tono-estado';

import { PortalService } from '../services/portal.service';
import { Proyecto, SitioAvance } from '../models/portal.models';

@Component({
  selector: 'app-portal-proyectos',
  imports: [MatCardModule, MatButtonModule, MatIconModule, PageHeader, StateContainer, ProgressBadge],
  templateUrl: './portal-proyectos.html',
})
export class PortalProyectos {
  private readonly service = inject(PortalService);

  protected readonly humanizar = humanizarEstado;

  protected readonly estado = signal<EstadoSolicitud<Proyecto[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  /** Proyecto expandido con su detalle de avance por sitio. */
  protected readonly detalle = signal<Proyecto | null>(null);
  protected readonly detalleEstado = signal<EstadoSolicitud<Proyecto>>(cargando());

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.misProyectos(this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.total.set(pagina.totalElements);
        this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  verAvance(p: Proyecto): void {
    if (this.detalle()?.id === p.id) {
      this.detalle.set(null);
      return;
    }
    this.detalle.set(p);
    this.detalleEstado.set(cargando());
    this.service.avanceDeProyecto(p.id).subscribe({
      next: (proyecto) => this.detalleEstado.set(conDatos(proyecto)),
      error: (e: HttpErrorResponse) => this.detalleEstado.set(conError(mensajeDeError(e))),
    });
  }

  /** Numero de fases cubiertas de un sitio (0..4). */
  private fasesCubiertas(s: SitioAvance): number {
    return (
      (s.tieneLevantamientoCompletado ? 1 : 0) +
      (s.tienePermisoAprobado ? 1 : 0) +
      (s.tieneOrdenFabricacionTerminada ? 1 : 0) +
      (s.tieneInstalacionCompletada ? 1 : 0)
    );
  }

  /** Avance porcentual (0..100) de un sitio segun sus 4 fases. */
  avanceSitio(s: SitioAvance): number {
    return Math.round((this.fasesCubiertas(s) / 4) * 100);
  }

  /** Estado derivado del avance de un sitio para ProgressBadge. */
  estadoSitio(s: SitioAvance): 'en_riesgo' | 'en_curso' | 'cumplido' {
    const fases = this.fasesCubiertas(s);
    if (fases >= 4) {
      return 'cumplido';
    }
    if (fases === 0) {
      return 'en_riesgo';
    }
    return 'en_curso';
  }
}
