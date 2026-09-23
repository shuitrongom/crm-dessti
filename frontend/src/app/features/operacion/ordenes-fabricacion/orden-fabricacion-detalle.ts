// =============================================================================
// Vista de detalle de Orden de Fabricacion (Req 9.1, 9.5-9.8, 10.2, 10.3)
// -----------------------------------------------------------------------------
// Vista GENERICA (sin guardaGiro): muestra la informacion de una OF con el
// Cliente por NOMBRE (via NombresOperacionService, nunca el UUID), el estado con
// etiqueta es-MX, el origen ("directa" / "desde cotizacion") y la tabla de
// partidas (Material por nombre + cantidad). Las acciones de estado se ofrecen
// CONTEXTUALIZADAS al estado actual y solo si el Usuario tiene el permiso
// orden_fabricacion:cambiar_estado:
//   - pendiente     -> Iniciar produccion (en_produccion) / Cancelar
//   - en_produccion -> Terminar (terminada) / Cancelar
//   - terminada/cancelada -> sin acciones (estados finales)
// Sigue el patron de proyecto-detalle: input.required<string>() del :id, signal
// de fase cargando|ok|error|vacio + mensajeError, StateContainer, PageHeader,
// es-MX, solo design tokens y WCAG AA.
// =============================================================================

import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ChipEstado } from '../../../shared/components/chip-estado/chip-estado';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ProduccionService } from '../services/produccion.service';
import { NombresOperacionService } from '../services/nombres-operacion.service';
import {
  ETIQUETA_ESTADO_OF,
  EstadoOrdenFabricacion,
  OrdenFabricacionDetalle,
  estadosDestinoOf,
} from '../models/operacion.models';

/** Accion de estado contextual ofrecida en el detalle de la OF. */
interface AccionEstado {
  readonly destino: EstadoOrdenFabricacion;
  readonly etiqueta: string;
  readonly destructiva: boolean;
}

@Component({
  selector: 'app-operacion-orden-fabricacion-detalle',
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    PageHeader,
    StateContainer,
    ChipEstado,
  ],
  templateUrl: './orden-fabricacion-detalle.html',
  styleUrl: './orden-fabricacion-detalle.scss',
})
export class OperacionOrdenFabricacionDetalle implements OnInit {
  /** Identificador de la Orden de Fabricacion tomado de la ruta (:id). */
  readonly id = input.required<string>();

  private readonly service = inject(ProduccionService);
  protected readonly nombres = inject(NombresOperacionService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCambiarEstado = this.auth.tienePermiso(
    'orden_fabricacion',
    'cambiar_estado',
  );

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly orden = signal<OrdenFabricacionDetalle | null>(null);

  /** Columnas de la tabla de partidas (Material por nombre + cantidad). */
  protected readonly columnasPartidas = ['material', 'cantidad'] as const;

  /**
   * Acciones de estado contextualizadas al estado actual: solo las transiciones
   * validas de la maquina de estados, con etiqueta es-MX orientada a la accion.
   * Vacio para estados finales (terminada/cancelada) o sin permiso.
   */
  protected readonly acciones = computed<AccionEstado[]>(() => {
    const o = this.orden();
    if (!o || !this.puedeCambiarEstado) {
      return [];
    }
    return estadosDestinoOf(o.estado).map((destino) => ({
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
    // Carga los catalogos de nombres (para Cliente y Material) junto con el
    // detalle de la OF; ambos deben estar disponibles antes de pintar la vista.
    forkJoin({
      nombres: this.nombres.cargar(),
      orden: this.service.consultar(this.id()),
    }).subscribe({
      next: ({ orden }) => {
        this.orden.set(orden);
        this.fase.set('ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Etiqueta legible es-MX del estado de la OF. */
  protected etiquetaEstado(estado: EstadoOrdenFabricacion): string {
    return ETIQUETA_ESTADO_OF[estado] ?? estado;
  }

  /**
   * Mapea el estado de la OF a la variante semantica del Chip_Estado:
   * pendiente -> info, en_produccion -> advertencia, terminada -> exito,
   * cancelada -> neutro (por sobriedad, no es un error del sistema).
   */
  protected varianteEstado(
    estado: EstadoOrdenFabricacion,
  ): 'exito' | 'advertencia' | 'error' | 'info' | 'neutro' {
    switch (estado) {
      case 'pendiente':
        return 'info';
      case 'en_produccion':
        return 'advertencia';
      case 'terminada':
        return 'exito';
      case 'cancelada':
        return 'neutro';
      default:
        return 'neutro';
    }
  }

  /** Origen legible de la OF: "Desde cotizacion" si tiene folio; si no, "Directa". */
  protected origen(orden: OrdenFabricacionDetalle): string {
    return orden.cotizacionId
      ? `Desde cotizacion (${this.nombres.nombreCotizacion(orden.cotizacionId)})`
      : 'Directa';
  }

  /** Nombre legible del Cliente (nunca el UUID). */
  protected nombreCliente(clienteId: string): string {
    return this.nombres.nombreCliente(clienteId);
  }

  /** Nombre legible del Material de una partida (nunca el UUID). */
  protected nombreMaterial(materialId: string): string {
    return this.nombres.nombreMaterial(materialId);
  }

  /** Etiqueta de accion orientada al verbo para cada transicion valida. */
  private etiquetaAccion(destino: EstadoOrdenFabricacion): string {
    switch (destino) {
      case 'en_produccion':
        return 'Iniciar produccion';
      case 'terminada':
        return 'Terminar';
      case 'cancelada':
        return 'Cancelar';
      default:
        return ETIQUETA_ESTADO_OF[destino] ?? destino;
    }
  }

  /** Cambia el estado de la OF con confirmacion previa (Req 9.5). */
  async cambiarEstado(accion: AccionEstado): Promise<void> {
    const orden = this.orden();
    if (!orden) {
      return;
    }
    const ok = await this.confirm.confirmar({
      titulo: 'Cambiar estado de la orden',
      mensaje: `La orden pasara a "${this.etiquetaEstado(accion.destino)}". Deseas continuar?`,
      textoConfirmar: accion.etiqueta,
      destructiva: accion.destructiva,
    });
    if (!ok) {
      return;
    }
    this.service.cambiarEstado(orden.id, accion.destino).subscribe({
      next: () => {
        this.toast.exito('Estado actualizado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
