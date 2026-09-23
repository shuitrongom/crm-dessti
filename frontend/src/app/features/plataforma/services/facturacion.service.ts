// =============================================================================
// Servicio de Facturacion de renta de modulos (super_admin) (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (permisos de super_admin):
//   - GET  /empresas/{tenantId}/facturas-renta          (factura_renta:listar)
//   - GET  /empresas/{tenantId}/renta?periodo=YYYY-MM-01 (factura_renta:leer)   PREVIEW (no persiste)
//   - POST /empresas/{tenantId}/renta?periodo=YYYY-MM-01 (factura_renta:crear)  EMITE (persiste, 201)
//   - GET  /facturas-renta/{id}                          (factura_renta:leer)
//   - GET  /facturas-renta/{id}/pdf                       (factura_renta:leer)  application/pdf inline
//
// El PDF requiere autenticacion (JWT). Por eso NO se abre con un <a href> plano:
// se descarga como Blob a traves de HttpClient (el interceptor adjunta el token)
// y se abre/descarga mediante un object URL local.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { FacturaRenta } from '../models/plataforma.models';

@Injectable({ providedIn: 'root' })
export class FacturacionService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /**
   * Lista las facturas de renta ya emitidas de una Empresa (mas recientes
   * primero, segun el backend). Permiso `factura_renta:listar`.
   */
  listarPorEmpresa(tenantId: string): Observable<FacturaRenta[]> {
    return this.http.get<FacturaRenta[]>(this.api.url(`/empresas/${tenantId}/facturas-renta`));
  }

  /**
   * Previsualiza (sin persistir) la factura de renta de un periodo. Permiso
   * `factura_renta:leer`. El backend responde 422 si no hay modulos facturables
   * o no hay moneda de facturacion, y 404 si no hay suscripcion activa.
   *
   * @param periodo primer dia del mes en formato `YYYY-MM-01`.
   */
  previsualizar(tenantId: string, periodo: string): Observable<FacturaRenta> {
    const params = new HttpParams().set('periodo', periodo);
    return this.http.get<FacturaRenta>(this.api.url(`/empresas/${tenantId}/renta`), { params });
  }

  /**
   * Emite (persiste) la factura de renta de un periodo. Permiso
   * `factura_renta:crear`. Responde 201 con la factura emitida; 409 si ya existe
   * una factura para ese periodo + moneda.
   *
   * @param periodo primer dia del mes en formato `YYYY-MM-01`.
   */
  emitir(tenantId: string, periodo: string): Observable<FacturaRenta> {
    const params = new HttpParams().set('periodo', periodo);
    return this.http.post<FacturaRenta>(this.api.url(`/empresas/${tenantId}/renta`), null, {
      params,
    });
  }

  /** URL absoluta del PDF premium de una factura (`/facturas-renta/{id}/pdf`). */
  pdfUrl(facturaId: string): string {
    return this.api.url(`/facturas-renta/${facturaId}/pdf`);
  }

  /**
   * Descarga el PDF premium de la factura como Blob a traves de HttpClient, de
   * modo que el interceptor adjunte el token (el endpoint exige autenticacion;
   * un <a href> plano devolveria 401). Permiso `factura_renta:leer`.
   */
  descargarPdf(facturaId: string): Observable<Blob> {
    return this.http.get(this.pdfUrl(facturaId), { responseType: 'blob' });
  }
}
