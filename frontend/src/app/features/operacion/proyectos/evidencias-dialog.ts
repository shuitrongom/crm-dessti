// =============================================================================
// Dialogo GALERIA de evidencias de avance de sitio (Req 3.2, enterprise)
// -----------------------------------------------------------------------------
// Lista las evidencias del Sitio con su estado de aprobacion, permite subir
// nuevas (abre EvidenciaSubirDialog), visualizar el archivo (imagen o PDF, via
// Blob del backend) y decidir (aprobar/rechazar con motivo) cuando el Usuario
// tiene el permiso evidencia_avance:aprobar. Clona los patrones existentes:
// visualizacion de PDF como en cotizacion-preview-dialog, decision como en
// permiso-detalle (ConfirmDialogService), estado con ChipEstado.
// =============================================================================

import { Component, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ChipEstado, VarianteChipEstado } from '../../../shared/components/chip-estado/chip-estado';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { EvidenciasService } from '../services/evidencias.service';
import {
  ETIQUETA_ESTADO_EVIDENCIA,
  EstadoEvidencia,
  EvidenciaAvance,
  FaseSitioGenerica,
  evidenciaEsImagen,
  evidenciaEsPdf,
} from '../models/operacion.models';
import { EvidenciaSubirDialog, EvidenciaSubirDialogData } from './evidencia-subir-dialog';

/** Datos de entrada: Proyecto, Sitio y su fase actual (para subir). */
export interface EvidenciasDialogData {
  proyectoId: string;
  sitioId: string;
  sitioNombre: string;
  fase: FaseSitioGenerica;
  puedeSubir: boolean;
}

@Component({
  selector: 'app-evidencias-dialog',
  imports: [
    DatePipe,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    StateContainer,
    ChipEstado,
  ],
  templateUrl: './evidencias-dialog.html',
  styleUrl: './evidencias-dialog.scss',
})
export class EvidenciasDialog implements OnDestroy {
  private readonly service = inject(EvidenciasService);
  private readonly dialog = inject(MatDialog);
  private readonly dialogRef = inject(MatDialogRef<EvidenciasDialog, boolean>);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly data = inject<EvidenciasDialogData>(MAT_DIALOG_DATA);

  protected readonly sitioNombre = this.data.sitioNombre;
  protected readonly puedeSubir = this.data.puedeSubir;
  protected readonly puedeDecidir = this.auth.tienePermiso('evidencia_avance', 'aprobar');
  private readonly mapaEstado = ETIQUETA_ESTADO_EVIDENCIA;

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly evidencias = signal<EvidenciaAvance[]>([]);
  /** true si a lo largo del modal cambio algo (subir/decidir): el origen debe recargar. */
  private huboCambios = false;

  /** Object URLs creados para previsualizar archivos; se revocan al cerrar. */
  private readonly objectUrls: string[] = [];

  constructor() {
    this.cargar();
  }

  ngOnDestroy(): void {
    for (const url of this.objectUrls) {
      URL.revokeObjectURL(url);
    }
  }

  /** Etiqueta legible del estado de una evidencia. */
  protected etiquetaEstado(estado: EstadoEvidencia): string {
    return this.mapaEstado[estado] ?? estado;
  }

  /** Variante del chip por estado: pendiente→info, aprobada→exito, rechazada→error. */
  protected varianteEstado(estado: EstadoEvidencia): VarianteChipEstado {
    const mapa: Record<EstadoEvidencia, VarianteChipEstado> = {
      pendiente: 'info',
      aprobada: 'exito',
      rechazada: 'error',
    };
    return mapa[estado] ?? 'neutro';
  }

  protected esImagen = evidenciaEsImagen;
  protected esPdf = evidenciaEsPdf;

  cargar(): void {
    this.fase.set('cargando');
    this.service.listar(this.data.proyectoId, this.data.sitioId).subscribe({
      next: (lista) => {
        this.evidencias.set(lista);
        this.fase.set(lista.length === 0 ? 'vacio' : 'ok');
      },
      error: () => this.fase.set('error'),
    });
  }

  /** Abre el modal de subida y, si sube, recarga la galeria. */
  subir(): void {
    const data: EvidenciaSubirDialogData = {
      proyectoId: this.data.proyectoId,
      sitioId: this.data.sitioId,
      sitioNombre: this.data.sitioNombre,
      fase: this.data.fase,
    };
    const ref = this.dialog.open(EvidenciaSubirDialog, {
      width: 'min(560px, 96vw)',
      maxWidth: 'min(560px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((creada?: EvidenciaAvance) => {
      if (creada) {
        this.huboCambios = true;
        this.toast.exito('Evidencia subida. Queda pendiente de aprobación.');
        this.cargar();
      }
    });
  }

  /** Abre el archivo de la evidencia en una pestaña nueva (imagen o PDF). */
  ver(evidencia: EvidenciaAvance): void {
    this.service.descargarArchivo(evidencia.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        this.objectUrls.push(url);
        window.open(url, '_blank');
        // Se revoca con margen para no cortar la carga de la nueva pestaña.
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Aprueba una evidencia con confirmación (Req 3.2). */
  async aprobar(evidencia: EvidenciaAvance): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Aprobar evidencia',
      mensaje: `Se aprobará “${evidencia.nombreOriginal}”. Con al menos una evidencia aprobada, la sucursal podrá entregarse.`,
      textoConfirmar: 'Aprobar',
    });
    if (!ok) {
      return;
    }
    this.service.aprobar(evidencia.id).subscribe({
      next: () => {
        this.huboCambios = true;
        this.toast.exito('Evidencia aprobada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Rechaza una evidencia pidiendo un motivo obligatorio (Req 3.2). */
  async rechazar(evidencia: EvidenciaAvance): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Rechazar evidencia',
      mensaje: `Se rechazará “${evidencia.nombreOriginal}”. Deberás indicar el motivo del rechazo.`,
      textoConfirmar: 'Rechazar',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    const motivo = (window.prompt('Motivo del rechazo:', '') ?? '').trim();
    if (!motivo) {
      this.toast.error('El motivo del rechazo es obligatorio.');
      return;
    }
    this.service.rechazar(evidencia.id, motivo).subscribe({
      next: () => {
        this.huboCambios = true;
        this.toast.exito('Evidencia rechazada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Cierra el modal indicando si hubo cambios (para que el detalle recargue). */
  protected cerrar(): void {
    this.dialogRef.close(this.huboCambios);
  }
}
