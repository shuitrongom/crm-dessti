// =============================================================================
// Dialogo de SUBIDA de evidencia de avance de sitio (Req 3.2, enterprise)
// -----------------------------------------------------------------------------
// Permite subir un ARCHIVO REAL (foto/PDF) desde el dispositivo — camara o
// galeria en movil, explorador en escritorio — con arrastrar y soltar, vista
// previa (imagen o icono de PDF) y validacion de tipo/tamano en el cliente. Al
// confirmar, envia el File por multipart via EvidenciasService y cierra
// devolviendo la evidencia creada (en estado pendiente de aprobacion).
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { EvidenciasService } from '../services/evidencias.service';
import { EvidenciaAvance, ETIQUETA_FASE_SITIO, FaseSitioGenerica } from '../models/operacion.models';

/** Tipos MIME admitidos para la evidencia (alineado con el backend). */
const TIPOS_PERMITIDOS = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];
/** Tamano maximo por archivo: 10 MB (alineado con crm.evidencias.storage.max-tamano-mb). */
const MAX_BYTES = 10 * 1024 * 1024;

/** Datos de entrada: Proyecto, Sitio y fase que la evidencia respaldará. */
export interface EvidenciaSubirDialogData {
  proyectoId: string;
  sitioId: string;
  sitioNombre: string;
  fase: FaseSitioGenerica;
}

@Component({
  selector: 'app-evidencia-subir-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './evidencia-subir-dialog.html',
  styleUrl: './evidencia-subir-dialog.scss',
})
export class EvidenciaSubirDialog {
  private readonly service = inject(EvidenciasService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly dialogRef = inject(MatDialogRef<EvidenciaSubirDialog, EvidenciaAvance>);
  private readonly data = inject<EvidenciaSubirDialogData>(MAT_DIALOG_DATA);

  protected readonly sitioNombre = this.data.sitioNombre;
  protected readonly etiquetaFase = ETIQUETA_FASE_SITIO[this.data.fase];

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Archivo seleccionado (o null). */
  protected readonly archivo = signal<File | null>(null);
  /** Data-URL de vista previa cuando el archivo es imagen; null para PDF/otros. */
  protected readonly preview = signal<string | null>(null);
  /** true cuando se arrastra un archivo sobre la zona de soltar. */
  protected readonly arrastrando = signal(false);

  protected readonly esImagen = computed(() => this.archivo()?.type.startsWith('image/') ?? false);
  protected readonly esPdf = computed(() => this.archivo()?.type === 'application/pdf');
  protected readonly nombreArchivo = computed(() => this.archivo()?.name ?? '');
  protected readonly tamanoLegible = computed(() => {
    const bytes = this.archivo()?.size ?? 0;
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  });

  /** Procesa el archivo del input file. */
  protected seleccionar(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    this.tomarArchivo(archivo);
    input.value = '';
  }

  /** Procesa el archivo soltado (drag & drop). */
  protected soltar(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastrando.set(false);
    this.tomarArchivo(evento.dataTransfer?.files?.[0]);
  }

  protected arrastrarSobre(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastrando.set(true);
  }

  protected arrastrarFuera(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastrando.set(false);
  }

  /** Valida y adopta un archivo; genera vista previa si es imagen. */
  private tomarArchivo(archivo: File | undefined): void {
    this.error.set(null);
    if (!archivo) {
      return;
    }
    if (!TIPOS_PERMITIDOS.includes(archivo.type)) {
      this.error.set('Formato no admitido. Usa una imagen (JPG, PNG, WebP) o un PDF.');
      return;
    }
    if (archivo.size > MAX_BYTES) {
      this.error.set('El archivo supera el tamaño máximo de 10 MB.');
      return;
    }
    this.archivo.set(archivo);
    if (archivo.type.startsWith('image/')) {
      const lector = new FileReader();
      lector.onload = () => this.preview.set(String(lector.result));
      lector.onerror = () => this.preview.set(null);
      lector.readAsDataURL(archivo);
    } else {
      this.preview.set(null);
    }
  }

  /** Quita el archivo seleccionado. */
  protected quitar(): void {
    this.archivo.set(null);
    this.preview.set(null);
    this.error.set(null);
  }

  /** Sube la evidencia (Req 3.2). */
  protected subir(): void {
    const archivo = this.archivo();
    if (!archivo) {
      this.error.set('Selecciona un archivo de evidencia.');
      return;
    }
    this.guardando.set(true);
    this.overlay
      .ejecutar(this.service.subir(this.data.proyectoId, this.data.sitioId, archivo), {
        tipo: 'crear',
        textoProceso: 'Subiendo evidencia…',
        textoExito: 'Evidencia subida',
      })
      .subscribe({
        next: (evidencia) => {
          this.guardando.set(false);
          this.dialogRef.close(evidencia);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
  }

  /** Cancela sin subir. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
