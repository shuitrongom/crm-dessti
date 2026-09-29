// =============================================================================
// Dialogo de vista previa imprimible del PDF de una Cotizacion (Req 6)
// -----------------------------------------------------------------------------
// Muestra el PDF de la cotizacion dentro de un modal (iframe) para revisarlo sin
// salir de la lista, con acciones de Imprimir y Descargar. El PDF exige JWT, que
// solo agrega el interceptor de HttpClient; por eso NO se puede usar un iframe
// apuntando directo a la URL: se trae el documento como blob con el servicio, se
// crea un object URL temporal y se sanitiza para bindearlo al iframe. El object
// URL se revoca al cerrar el dialogo para no filtrar memoria.
// =============================================================================

import { Component, OnDestroy, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { mensajeDeError } from '../../../core/services/error-mensajes';
import { CotizacionesService } from '../services/cotizaciones.service';

/** Datos de entrada: la Cotizacion cuyo PDF se previsualiza. */
export interface CotizacionPreviewDialogData {
  id: string;
  folio: string | null;
}

@Component({
  selector: 'app-cotizacion-preview-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './cotizacion-preview-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './cotizacion-preview-dialog.scss',
})
export class CotizacionPreviewDialog implements OnDestroy {
  private readonly service = inject(CotizacionesService);
  private readonly sanitizer = inject(DomSanitizer);
  protected readonly data = inject<CotizacionPreviewDialogData>(MAT_DIALOG_DATA);

  /** `true` mientras se descarga el PDF del backend. */
  protected readonly cargando = signal(true);
  /** Mensaje de error de la descarga, o `null` si fue exitosa. */
  protected readonly error = signal<string | null>(null);
  /** URL segura (object URL) del PDF para el iframe; `null` mientras carga/falla. */
  protected readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  /** `true` mientras se descarga el archivo (boton Descargar). */
  protected readonly descargando = signal(false);

  /** Object URL crudo del blob (para revocarlo al cerrar). */
  private objectUrl: string | null = null;
  /** Blob del PDF ya en memoria (se reutiliza para Descargar sin repetir la peticion). */
  private blobPdf: Blob | null = null;

  constructor() {
    this.cargar();
  }

  /** Descarga el PDF como blob y lo prepara para el iframe. */
  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.service.descargarPdf(this.data.id).subscribe({
      next: (blob) => {
        this.blobPdf = blob;
        this.objectUrl = URL.createObjectURL(blob);
        // El fragmento #toolbar controla la barra nativa del visor del navegador.
        this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl));
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }

  /** Envia el PDF a la impresora del navegador (dialogo nativo de impresion). */
  protected imprimir(): void {
    const iframe = document.querySelector<HTMLIFrameElement>('.cot-preview__iframe');
    const ventana = iframe?.contentWindow;
    if (ventana) {
      ventana.focus();
      ventana.print();
    }
  }

  /** Descarga el PDF al equipo del Usuario (reutiliza el blob ya cargado). */
  protected descargar(): void {
    if (!this.blobPdf) {
      return;
    }
    this.descargando.set(true);
    const url = URL.createObjectURL(this.blobPdf);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = `cotizacion-${this.data.folio ?? this.data.id}.pdf`;
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    URL.revokeObjectURL(url);
    this.descargando.set(false);
  }

  ngOnDestroy(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
  }
}
