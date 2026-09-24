// =============================================================================
// Servicio del submódulo Contabilidad Electrónica SAT (Anexo 24)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend
// (com.dessti.crm.contabilidad.electronica.*), base relativa /api/v1:
//   Preview   GET /contabilidad/contabilidad-electronica/{catalogo|balanza|polizas}/preview
//   XML       GET /contabilidad/contabilidad-electronica/{catalogo|balanza|polizas}/xml?anio=&mes=
//   Amarre    GET   /contabilidad/codigos-agrupadores-sat?q=
//             PATCH /contabilidad/cuentas-contables/{id}/codigo-agrupador
// Las descargas se obtienen como Blob observando la respuesta completa para leer
// el nombre de archivo del header Content-Disposition.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import {
  CodigoAgrupadorSat,
  VistaPreviaBalanza,
  VistaPreviaCatalogo,
  VistaPreviaPolizas,
} from '../models/contabilidad-electronica.models';

/** Archivo XML descargado: nombre sugerido por el servidor y su contenido. */
export interface ArchivoDescargado {
  readonly nombreArchivo: string;
  readonly blob: Blob;
}

@Injectable({ providedIn: 'root' })
export class ContabilidadElectronicaService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  private readonly base = '/contabilidad/contabilidad-electronica';

  // --- Vistas previa ---------------------------------------------------------

  vistaPreviaCatalogo(): Observable<VistaPreviaCatalogo> {
    return this.http.get<VistaPreviaCatalogo>(this.api.url(`${this.base}/catalogo/preview`));
  }

  vistaPreviaBalanza(anio: number, mes: number): Observable<VistaPreviaBalanza> {
    const params = new HttpParams().set('anio', anio).set('mes', mes);
    return this.http.get<VistaPreviaBalanza>(this.api.url(`${this.base}/balanza/preview`), {
      params,
    });
  }

  vistaPreviaPolizas(anio: number, mes: number): Observable<VistaPreviaPolizas> {
    const params = new HttpParams().set('anio', anio).set('mes', mes);
    return this.http.get<VistaPreviaPolizas>(this.api.url(`${this.base}/polizas/preview`), {
      params,
    });
  }

  // --- Descargas de XML ------------------------------------------------------

  descargarCatalogo(anio: number, mes: number): Observable<ArchivoDescargado> {
    return this.descargarXml(`${this.base}/catalogo/xml`, anio, mes, 'CT');
  }

  descargarBalanza(anio: number, mes: number): Observable<ArchivoDescargado> {
    return this.descargarXml(`${this.base}/balanza/xml`, anio, mes, 'BN');
  }

  descargarPolizas(anio: number, mes: number): Observable<ArchivoDescargado> {
    return this.descargarXml(`${this.base}/polizas/xml`, anio, mes, 'PL');
  }

  // --- Catálogo de códigos agrupadores del SAT -------------------------------

  buscarCodigosAgrupadores(q: string): Observable<CodigoAgrupadorSat[]> {
    let params = new HttpParams();
    if (q) {
      params = params.set('q', q);
    }
    return this.http.get<CodigoAgrupadorSat[]>(
      this.api.url('/contabilidad/codigos-agrupadores-sat'),
      { params },
    );
  }

  // --- Amarre del código agrupador a una cuenta ------------------------------

  amarrarCodigoAgrupador(cuentaId: string, codigoAgrupadorSat: string): Observable<unknown> {
    return this.http.patch(
      this.api.url(`/contabilidad/cuentas-contables/${cuentaId}/codigo-agrupador`),
      { codigoAgrupadorSat },
    );
  }

  // --- Interno ---------------------------------------------------------------

  private descargarXml(
    path: string,
    anio: number,
    mes: number,
    sufijoFallback: string,
  ): Observable<ArchivoDescargado> {
    const params = new HttpParams().set('anio', anio).set('mes', mes);
    return new Observable<ArchivoDescargado>((observer) => {
      const sub = this.http
        .get(this.api.url(path), { params, observe: 'response', responseType: 'blob' })
        .subscribe({
          next: (respuesta: HttpResponse<Blob>) => {
            const nombre = this.nombreDesdeCabecera(
              respuesta.headers.get('Content-Disposition'),
              `${anio}${String(mes).padStart(2, '0')}${sufijoFallback}.xml`,
            );
            observer.next({ nombreArchivo: nombre, blob: respuesta.body ?? new Blob() });
            observer.complete();
          },
          error: (e) => observer.error(e),
        });
      return () => sub.unsubscribe();
    });
  }

  private nombreDesdeCabecera(contentDisposition: string | null, fallback: string): string {
    if (!contentDisposition) {
      return fallback;
    }
    const coincidencia = /filename="?([^"]+)"?/i.exec(contentDisposition);
    return coincidencia?.[1] ?? fallback;
  }
}
