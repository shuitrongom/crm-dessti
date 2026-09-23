// =============================================================================
// Vista de detalle de Permiso de Instalacion (Req 9.3, 9.5-9.8, 17)
// -----------------------------------------------------------------------------
// Vista ESPECIFICA de anuncios (ruta protegida por guardaGiro(GIRO_ANUNCIOS)).
// Muestra la informacion del Permiso con el Sitio por NOMBRE (via
// NombresOperacionService, nunca el UUID), el tipo y el estado con etiquetas
// es-MX. Las acciones aprobar/rechazar se ofrecen CONTEXTUALIZADAS: solo cuando
// el estado es `solicitado` y el Usuario tiene el permiso
// permiso_instalacion:cambiar_estado (un Permiso ya decidido no muestra
// acciones). Sigue el patron de proyecto-detalle: input.required<string>() del
// :id, signal de fase cargando|ok|error|vacio + mensajeError, StateContainer,
// PageHeader, es-MX, solo design tokens y WCAG AA.
// =============================================================================

import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  ChipEstado,
  VarianteChipEstado,
} from '../../../shared/components/chip-estado/chip-estado';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { PermisosService } from '../services/instalacion.service';
import { NombresOperacionService } from '../services/nombres-operacion.service';
import {
  ETIQUETA_ESTADO_PERMISO,
  ETIQUETA_TIPO_PERMISO,
  EstadoPermiso,
  PermisoInstalacion,
  TipoPermiso,
} from '../models/operacion.models';

@Component({
  selector: 'app-operacion-permiso-detalle',
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    ChipEstado,
  ],
  templateUrl: './permiso-detalle.html',
  styleUrl: './permiso-detalle.scss',
})
export class OperacionPermisoDetalle implements OnInit {
  /** Identificador del Permiso tomado de la ruta (:id). */
  readonly id = input.required<string>();

  private readonly service = inject(PermisosService);
  protected readonly nombres = inject(NombresOperacionService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeDecidir = this.auth.tienePermiso('permiso_instalacion', 'cambiar_estado');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly permiso = signal<PermisoInstalacion | null>(null);

  /**
   * Las acciones de decision (aprobar/rechazar) solo estan disponibles cuando el
   * Permiso esta `solicitado` y el Usuario tiene el permiso de cambio de estado.
   * Un Permiso ya decidido (aprobado/rechazado) es terminal: sin acciones.
   */
  protected readonly puedeAccionar = computed(() => {
    const p = this.permiso();
    return !!p && p.estado === 'solicitado' && this.puedeDecidir;
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.mensajeError.set(undefined);
    // Carga los catalogos de nombres (para el Sitio) junto con el detalle del
    // Permiso; ambos deben estar disponibles antes de pintar la vista.
    forkJoin({
      nombres: this.nombres.cargar(),
      permiso: this.service.consultar(this.id()),
    }).subscribe({
      next: ({ permiso }) => {
        this.permiso.set(permiso);
        this.fase.set('ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Etiqueta legible es-MX del tipo de Permiso. */
  protected etiquetaTipo(tipo: TipoPermiso): string {
    return ETIQUETA_TIPO_PERMISO[tipo] ?? tipo;
  }

  /** Etiqueta legible es-MX del estado de Permiso. */
  protected etiquetaEstado(estado: EstadoPermiso): string {
    return ETIQUETA_ESTADO_PERMISO[estado] ?? estado;
  }

  /** Variante semantica del chip de estado: solicitado→info, aprobado→exito, rechazado→error. */
  protected varianteEstado(estado: EstadoPermiso): VarianteChipEstado {
    const mapa: Record<EstadoPermiso, VarianteChipEstado> = {
      solicitado: 'info',
      aprobado: 'exito',
      rechazado: 'error',
    };
    return mapa[estado] ?? 'neutro';
  }

  /** Aprueba o rechaza el Permiso solicitado con confirmacion previa (Req 17.2). */
  async decidir(accion: 'aprobar' | 'rechazar'): Promise<void> {
    const permiso = this.permiso();
    if (!permiso || permiso.estado !== 'solicitado') {
      return;
    }
    const ok = await this.confirm.confirmar({
      titulo: accion === 'aprobar' ? 'Aprobar permiso' : 'Rechazar permiso',
      mensaje: `El permiso quedara ${accion === 'aprobar' ? 'aprobado' : 'rechazado'}. Deseas continuar?`,
      textoConfirmar: accion === 'aprobar' ? 'Aprobar' : 'Rechazar',
      destructiva: accion === 'rechazar',
    });
    if (!ok) {
      return;
    }
    this.service.cambiarEstado(permiso.id, accion).subscribe({
      next: () => {
        this.toast.exito(accion === 'aprobar' ? 'Permiso aprobado.' : 'Permiso rechazado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
