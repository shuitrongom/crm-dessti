// =============================================================================
// Vista de detalle de Orden de Trabajo de Instalacion / OTI (Req 9.4, 9.5-9.8, 8)
// -----------------------------------------------------------------------------
// Vista ESPECIFICA de anuncios (ruta protegida por guardaGiro(GIRO_ANUNCIOS)).
// Muestra la informacion de la OTI con Cliente/Sitio/Orden/Cuadrilla por NOMBRE
// (via NombresOperacionService, nunca el UUID), el estado con etiqueta es-MX, la
// lista de pendientes (con accion "Resolver" por pendiente) y la galeria de
// evidencias. Ofrece acciones de estado CONTEXTUALIZADAS a las transiciones
// validas (Req 19.5). Al intentar completar con pendientes no resueltos, el
// backend responde 422 con un mensaje que enumera las descripciones (§C2, tarea
// 6.3); esa vista muestra ese mensaje informativo tal cual. Las acciones se
// gobiernan por el permiso orden_trabajo_instalacion:cambiar_estado. Sigue el
// patron de proyecto-detalle: input.required<string>() del :id, signal de fase
// cargando|ok|error|vacio + mensajeError, StateContainer, PageHeader, es-MX,
// solo design tokens y WCAG AA.
// =============================================================================

import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ChipEstado, VarianteChipEstado } from '../../../shared/components/chip-estado/chip-estado';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { OtisService } from '../services/instalacion.service';
import { NombresOperacionService } from '../services/nombres-operacion.service';
import {
  ETIQUETA_ESTADO_OTI,
  EstadoOti,
  OrdenTrabajoInstalacionDetalle,
  PendienteInstalacion,
  estadosDestinoOti,
} from '../models/operacion.models';

/** Accion de estado contextual ofrecida en el detalle de la OTI. */
interface AccionEstado {
  readonly destino: EstadoOti;
  readonly etiqueta: string;
  readonly destructiva: boolean;
}

@Component({
  selector: 'app-operacion-oti-detalle',
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    PageHeader,
    StateContainer,
    ChipEstado,
  ],
  templateUrl: './oti-detalle.html',
  styleUrl: './oti-detalle.scss',
})
export class OperacionOtiDetalle implements OnInit {
  /** Identificador de la OTI tomado de la ruta (:id). */
  readonly id = input.required<string>();

  private readonly service = inject(OtisService);
  protected readonly nombres = inject(NombresOperacionService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeGestionar = this.auth.tienePermiso(
    'orden_trabajo_instalacion',
    'cambiar_estado',
  );

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly oti = signal<OrdenTrabajoInstalacionDetalle | null>(null);

  /**
   * Acciones de estado contextualizadas al estado actual: solo las transiciones
   * validas de la maquina de estados (Req 19.5). Vacio para estados terminales
   * (completada/cancelada) o sin permiso.
   */
  protected readonly acciones = computed<AccionEstado[]>(() => {
    const o = this.oti();
    if (!o || !this.puedeGestionar) {
      return [];
    }
    return estadosDestinoOti(o.estado).map((destino) => ({
      destino,
      etiqueta: this.etiquetaAccion(destino),
      destructiva: destino === 'cancelada',
    }));
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.mensajeError.set(undefined);
    // Carga los catalogos de nombres (Cliente/Sitio/OF) junto con el detalle de
    // la OTI (pendientes + evidencias); ambos deben estar listos antes de pintar.
    forkJoin({
      nombres: this.nombres.cargar(),
      oti: this.service.consultarDetalle(this.id()),
    }).subscribe({
      next: ({ oti }) => {
        this.oti.set(oti);
        this.fase.set('ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Etiqueta legible es-MX del estado de la OTI. */
  protected etiquetaEstado(estado: EstadoOti): string {
    return ETIQUETA_ESTADO_OTI[estado] ?? estado;
  }

  /** Variante semantica del chip de estado para cada estado de la OTI (Req 7.5). */
  protected varianteEstado(estado: EstadoOti): VarianteChipEstado {
    switch (estado) {
      case 'programada':
        return 'info';
      case 'en_curso':
        return 'advertencia';
      case 'completada':
        return 'exito';
      case 'cancelada':
        return 'neutro';
      default:
        return 'neutro';
    }
  }

  /** Etiqueta de accion orientada al verbo para cada transicion valida. */
  private etiquetaAccion(destino: EstadoOti): string {
    switch (destino) {
      case 'en_curso':
        return 'Iniciar';
      case 'completada':
        return 'Completar';
      case 'cancelada':
        return 'Cancelar';
      default:
        return ETIQUETA_ESTADO_OTI[destino] ?? destino;
    }
  }

  /**
   * Cambia el estado de la OTI con confirmacion previa (Req 19.5). Si el destino
   * es `completada` y hay pendientes no resueltos, el backend responde 422 con un
   * mensaje que enumera las descripciones; se muestra tal cual (§C2).
   */
  async cambiarEstado(accion: AccionEstado): Promise<void> {
    const oti = this.oti();
    if (!oti) {
      return;
    }
    const ok = await this.confirm.confirmar({
      titulo: 'Cambiar estado de la OTI',
      mensaje: `La orden de trabajo pasara a "${this.etiquetaEstado(accion.destino)}". Deseas continuar?`,
      textoConfirmar: accion.etiqueta,
      destructiva: accion.destructiva,
    });
    if (!ok) {
      return;
    }
    this.service.cambiarEstado(oti.id, accion.destino).subscribe({
      next: () => {
        this.toast.exito('Estado actualizado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Marca un pendiente como resuelto con confirmacion previa (Req 8.4). */
  async resolver(pendiente: PendienteInstalacion): Promise<void> {
    const oti = this.oti();
    if (!oti || pendiente.resuelto) {
      return;
    }
    const ok = await this.confirm.confirmar({
      titulo: 'Resolver pendiente',
      mensaje: `El pendiente "${pendiente.descripcion}" quedara marcado como resuelto. Deseas continuar?`,
      textoConfirmar: 'Resolver',
    });
    if (!ok) {
      return;
    }
    this.service.registrarAvance(oti.id, { pendientesResueltos: [pendiente.id] }).subscribe({
      next: () => {
        this.toast.exito('Pendiente resuelto.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
