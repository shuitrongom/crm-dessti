// =============================================================================
// Vista de detalle de Levantamiento de Sitio (Req 9.2, 9.5-9.8, 12)
// -----------------------------------------------------------------------------
// Vista ESPECIFICA de anuncios (ruta protegida por guardaGiro(GIRO_ANUNCIOS)).
// Muestra la informacion del Levantamiento con Sitio/Cotizacion/Orden por NOMBRE
// (via NombresOperacionService, nunca el UUID), el estado con etiqueta es-MX, y
// una galeria de fotos vinculadas. El flujo de adjuntar fotos (Req 12) se hace en
// un MODAL animado (LevantamientoFotosDialog), gobernado por el permiso
// levantamiento_sitio:cambiar_estado; al cerrarse recarga la galeria.
// =============================================================================

import { Component, OnInit, inject, signal, input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ChipEstado, VarianteChipEstado } from '../../../shared/components/chip-estado/chip-estado';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { LevantamientosService } from '../services/instalacion.service';
import { NombresOperacionService } from '../services/nombres-operacion.service';
import {
  ETIQUETA_ESTADO_LEVANTAMIENTO,
  EstadoLevantamiento,
  LevantamientoFoto,
  LevantamientoSitioDetalle,
} from '../models/operacion.models';
import {
  LevantamientoFotosDialog,
  LevantamientoFotosDialogData,
} from './levantamiento-fotos-dialog';

@Component({
  selector: 'app-operacion-levantamiento-detalle',
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    ChipEstado,
  ],
  templateUrl: './levantamiento-detalle.html',
  styleUrl: './levantamiento-detalle.scss',
})
export class OperacionLevantamientoDetalle implements OnInit {
  /** Identificador del Levantamiento tomado de la ruta (:id). */
  readonly id = input.required<string>();

  private readonly service = inject(LevantamientosService);
  protected readonly nombres = inject(NombresOperacionService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  /** Solo quien puede cambiar el estado del Levantamiento puede adjuntar fotos. */
  protected readonly puedeAgregarFotos = this.auth.tienePermiso(
    'levantamiento_sitio',
    'cambiar_estado',
  );

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly levantamiento = signal<LevantamientoSitioDetalle | null>(null);
  protected readonly fotos = signal<LevantamientoFoto[]>([]);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.mensajeError.set(undefined);
    // Carga los catalogos de nombres (Sitio/Cotizacion/OF) junto con el detalle
    // del Levantamiento; ambos deben estar listos antes de pintar la vista.
    forkJoin({
      nombres: this.nombres.cargar(),
      levantamiento: this.service.consultar(this.id()),
    }).subscribe({
      next: ({ levantamiento }) => {
        this.levantamiento.set(levantamiento);
        this.fotos.set(levantamiento.fotos);
        this.fase.set('ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Etiqueta legible es-MX del estado del Levantamiento. */
  protected etiquetaEstado(estado: EstadoLevantamiento): string {
    return ETIQUETA_ESTADO_LEVANTAMIENTO[estado] ?? estado;
  }

  /**
   * Variante semantica del Chip_Estado segun el estado del Levantamiento:
   * en_proceso -> advertencia (trabajo pendiente), completado -> exito.
   */
  protected varianteEstado(estado: EstadoLevantamiento): VarianteChipEstado {
    return estado === 'completado' ? 'exito' : 'advertencia';
  }

  /**
   * Abre el MODAL de gestion de fotos del Levantamiento (Req 12). Al cerrarse, si
   * se adjuntaron fotos, recarga la galeria de referencias.
   */
  abrirFotos(): void {
    const levantamiento = this.levantamiento();
    if (!levantamiento) {
      return;
    }
    const data: LevantamientoFotosDialogData = {
      levantamiento,
      puedeAgregar: this.puedeAgregarFotos,
    };
    const ref = this.dialog.open(LevantamientoFotosDialog, {
      width: 'min(680px, 96vw)',
      maxWidth: 'min(680px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((cambio?: boolean) => {
      if (cambio) {
        this.recargarFotos();
      }
    });
  }

  /** Recarga solo la galeria de fotos tras adjuntar en el modal. */
  private recargarFotos(): void {
    this.service.fotosDe(this.id()).subscribe({
      next: (fotos) => this.fotos.set(fotos),
    });
  }
}
