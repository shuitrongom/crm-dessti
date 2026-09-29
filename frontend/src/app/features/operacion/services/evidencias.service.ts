// =============================================================================
// Servicio de EVIDENCIAS de avance de sitio (Req 3.2, deber-ser enterprise)
// -----------------------------------------------------------------------------
// Integra el contrato REST del EvidenciaAvanceController:
//   - POST /proyectos/{pid}/sitios/{sid}/evidencias  (proyecto:actualizar) multipart
//   - GET  /proyectos/{pid}/sitios/{sid}/evidencias  (proyecto:leer)
//   - GET  /evidencias-avance/{id}/archivo           (proyecto:leer) -> Blob
//   - PUT  /evidencias-avance/{id}/decision?accion   (evidencia_avance:aprobar)
//
// PRIMER uso de multipart/FormData en el frontend: el archivo real se envia como
// FormData (NO se fija Content-Type a mano; el navegador pone el boundary). El
// binario para visualizar/descargar se obtiene como Blob (el JWT lo agrega el
// interceptor de HttpClient, por eso no se apunta un <img>/<iframe> a la URL).
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { EvidenciaAvance } from '../models/operacion.models';

@Injectable({ providedIn: 'root' })
export class EvidenciasService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /**
   * Sube un archivo de evidencia que respalda la fase actual del Sitio (Req 3.2).
   * El archivo viaja como parte multipart `archivo`. Devuelve la evidencia creada
   * (pendiente de aprobacion).
   */
  subir(proyectoId: string, sitioId: string, archivo: File): Observable<EvidenciaAvance> {
    const formData = new FormData();
    formData.append('archivo', archivo, archivo.name);
    return this.http.post<EvidenciaAvance>(
      this.api.url(`/proyectos/${proyectoId}/sitios/${sitioId}/evidencias`),
      formData,
    );
  }

  /** Lista las evidencias del Sitio, mas reciente primero (Req 3.2). */
  listar(proyectoId: string, sitioId: string): Observable<EvidenciaAvance[]> {
    return this.http.get<EvidenciaAvance[]>(
      this.api.url(`/proyectos/${proyectoId}/sitios/${sitioId}/evidencias`),
    );
  }

  /**
   * Descarga el binario de una evidencia como Blob para visualizarla o
   * descargarla (Req 3.2). El token lo agrega el interceptor de HttpClient.
   */
  descargarArchivo(evidenciaId: string): Observable<Blob> {
    return this.http.get(this.api.url(`/evidencias-avance/${evidenciaId}/archivo`), {
      responseType: 'blob',
    });
  }

  /** Aprueba una evidencia (Req 3.2). Requiere evidencia_avance:aprobar. */
  aprobar(evidenciaId: string): Observable<EvidenciaAvance> {
    return this.http.put<EvidenciaAvance>(
      this.api.url(`/evidencias-avance/${evidenciaId}/decision?accion=aprobar`),
      {},
    );
  }

  /** Rechaza una evidencia con un motivo obligatorio (Req 3.2). */
  rechazar(evidenciaId: string, motivo: string): Observable<EvidenciaAvance> {
    return this.http.put<EvidenciaAvance>(
      this.api.url(`/evidencias-avance/${evidenciaId}/decision?accion=rechazar`),
      { motivo },
    );
  }
}
