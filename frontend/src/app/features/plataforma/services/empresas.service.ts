// =============================================================================
// Servicio de administracion de plataforma: Empresas y Offboarding (Req 24, 69)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (super_admin):
//   - GET   /empresas?estado&q&page&size         (empresa:listar)
//   - GET   /empresas/{id}                        (empresa:leer)
//   - POST  /empresas                             (empresa:crear)
//   - POST  /empresas/{id}/activar|suspender      (empresa:cambiar_estado)
//   - POST  /empresas/{id}/offboarding/exportar   (offboarding:exportar)
//   - POST  /empresas/{id}/offboarding/cancelar   (offboarding:cambiar_estado)
//   - POST  /empresas/{id}/offboarding/eliminar   (offboarding:cambiar_estado)
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  Empresa,
  EmpresaCreada,
  EstadoEmpresa,
  ExportacionTenant,
  ResetPasswordAdmin,
  ResultadoEliminacionTenant,
} from '../models/plataforma.models';

/**
 * Cuerpo OPCIONAL de POST /empresas/{id}/admin/reset-password. Si se omite
 * `password`, el servidor genera una contrasena temporal y la devuelve una unica
 * vez. `usuarioId` permite dirigir el reinicio a un administrador concreto.
 */
export interface ResetPasswordAdminRequest {
  /** Contrasena explicita (8..255). Si se omite, el servidor genera una temporal. */
  password?: string;
  /** Administrador destino; opcional si la Empresa tiene un unico administrador. */
  usuarioId?: string;
}

/**
 * Cuerpo de POST /empresas (Req 24.2, 4.1-4.5). El instrumento comercial es
 * EXCLUYENTE: se envia `planId` (Plan de largo plazo) O `paqueteSuscripcionId`
 * (Paquete de Suscripcion de corto plazo), nunca ambos ni ninguno. El backend
 * valida el XOR (422 cuando faltan ambos o llegan los dos). Cuando el Paquete
 * admite prueba, `otorgarPrueba` indica si el Super_Admin concede el periodo de
 * prueba al alta (Contrato en estado `EN_PRUEBA`).
 */
export interface CrearEmpresaRequest {
  nombre: string;
  /** Giro (vertical de negocio) activo al que pertenece la Empresa (Req 9, 24.2). */
  giroId: string;
  rfc: string;
  /** Plan de largo plazo (excluyente con `paqueteSuscripcionId`); `null` si se contrata un Paquete (Req 4.1, 4.4). */
  planId?: string | null;
  /** Paquete de Suscripcion de corto plazo (excluyente con `planId`); `null` si se contrata un Plan (Req 4.2, 4.4). */
  paqueteSuscripcionId?: string | null;
  /** Otorga el periodo de prueba cuando el Paquete lo admite (Req 4.5); ignorado para Planes. */
  otorgarPrueba?: boolean;
  adminIdentificador: string;
  /** Opcional: si se omite, el Sistema genera una contrasena temporal (Req 11.3). */
  adminPassword?: string | null;
  /** Opcional: subconjunto de modulos del instrumento contratado (Req 25.4, 11.1). */
  modulosHabilitados?: string[] | null;
  /** Opcional: nombre comercial (marca) distinto de la razon social. */
  nombreComercial?: string | null;
  /** Opcional: correo de contacto principal (validado como @Email en backend). */
  emailContacto?: string | null;
  /** Opcional: telefono de contacto. */
  telefono?: string | null;
  /** Opcional: sitio web corporativo. */
  sitioWeb?: string | null;
  /** Opcional: calle y numero de la direccion postal. */
  direccionCalle?: string | null;
  /** Opcional: ciudad de la direccion postal. */
  direccionCiudad?: string | null;
  /** Opcional: estado/provincia de la direccion postal. */
  direccionEstado?: string | null;
  /** Opcional: codigo postal. */
  direccionCp?: string | null;
  /** Opcional: pais de la direccion postal. */
  direccionPais?: string | null;
  /** Opcional: notas internas sobre la Empresa. */
  notas?: string | null;
  /** Opcional: logo del branding como data-URI (base64); backend limita a 1 MiB. */
  logo?: string | null;
}

/**
 * Cuerpo de PUT /empresas/{id} (super_admin). Edita SOLO los datos descriptivos
 * y fiscales de la Empresa; NO reasigna giro/plan/estado (cada uno tiene su
 * propio flujo). `nombre`, `rfc` y `emailContacto` son obligatorios (backend).
 */
export interface ActualizarEmpresaRequest {
  nombre: string;
  rfc: string;
  emailContacto: string;
  /** Opcional: nombre comercial (marca) distinto de la razon social. */
  nombreComercial?: string | null;
  /** Opcional: telefono de contacto. */
  telefono?: string | null;
  /** Opcional: sitio web corporativo. */
  sitioWeb?: string | null;
  /** Opcional: calle y numero de la direccion postal. */
  direccionCalle?: string | null;
  /** Opcional: ciudad de la direccion postal. */
  direccionCiudad?: string | null;
  /** Opcional: estado/provincia de la direccion postal. */
  direccionEstado?: string | null;
  /** Opcional: codigo postal. */
  direccionCp?: string | null;
  /** Opcional: pais de la direccion postal. */
  direccionPais?: string | null;
  /** Opcional: notas internas sobre la Empresa. */
  notas?: string | null;
  /** Opcional: logo del branding como data-URI (base64); backend limita a 1 MiB. */
  logo?: string | null;
}

@Injectable({ providedIn: 'root' })
export class EmpresasService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /**
   * Lista Empresas paginadas, filtrable por estado y por texto libre (Req 24.5).
   * El parametro `q` realiza una busqueda case-insensitive sobre nombre, RFC y
   * nombre comercial; solo se envia cuando trae contenido (se ignora en blanco).
   * El filtro por estado y el texto son combinables.
   */
  listar(
    estado: EstadoEmpresa | null,
    q: string | null = null,
    page = 0,
    size = 20,
  ): Observable<PaginaResponse<Empresa>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) {
      params = params.set('estado', estado);
    }
    const termino = q?.trim();
    if (termino) {
      params = params.set('q', termino);
    }
    return this.http.get<PaginaResponse<Empresa>>(this.api.url('/empresas'), { params });
  }

  /** Consulta una Empresa por su identificador (Req 24.1). */
  consultar(id: string): Observable<Empresa> {
    return this.http.get<Empresa>(this.api.url(`/empresas/${id}`));
  }

  /** Da de alta una Empresa con su primer admin y suscripcion inicial (Req 24.2). */
  crear(request: CrearEmpresaRequest): Observable<EmpresaCreada> {
    return this.http.post<EmpresaCreada>(this.api.url('/empresas'), request);
  }

  /**
   * Actualiza los datos descriptivos/fiscales de una Empresa (PUT /empresas/{id}).
   * NO reasigna giro/plan/estado. Errores: 404 (empresa no encontrada), 409 (RFC
   * duplicado de otra empresa), 400 (correo/RFC invalidos o ausentes).
   */
  actualizarEmpresa(id: string, request: ActualizarEmpresaRequest): Observable<Empresa> {
    return this.http.put<Empresa>(this.api.url(`/empresas/${id}`), request);
  }

  /** Activa una Empresa (Req 24.1). */
  activar(id: string): Observable<Empresa> {
    return this.http.post<Empresa>(this.api.url(`/empresas/${id}/activar`), {});
  }

  /** Suspende una Empresa; impide el login de sus Usuarios (Req 24.4). */
  suspender(id: string): Observable<Empresa> {
    return this.http.post<Empresa>(this.api.url(`/empresas/${id}/suspender`), {});
  }

  /**
   * Reasigna el Giro (vertical de negocio) de una Empresa (PUT
   * /empresas/{id}/giro). El backend impone todas las reglas: el giro debe estar
   * activo y la Empresa no debe tener datos del vertical actual; ante un giro
   * invalido/inactivo o datos del vertical devuelve 422 (mensaje propagado), y
   * 404 si la Empresa no existe. Requiere el permiso `empresa:cambiar_estado`.
   */
  cambiarGiro(id: string, giroId: string): Observable<Empresa> {
    return this.http.put<Empresa>(this.api.url(`/empresas/${id}/giro`), { giroId });
  }

  /**
   * Restablece la contrasena del administrador de una Empresa. Sin cuerpo (o sin
   * `password`) el servidor genera una contrasena temporal y la devuelve una
   * unica vez en `passwordTemporal`; con una contrasena explicita `passwordTemporal`
   * llega nula. Errores: 404 (empresa/admin no encontrado), 422 (contrasena invalida).
   */
  restablecerPasswordAdmin(
    empresaId: string,
    body?: ResetPasswordAdminRequest,
  ): Observable<ResetPasswordAdmin> {
    return this.http.post<ResetPasswordAdmin>(
      this.api.url(`/empresas/${empresaId}/admin/reset-password`),
      body ?? {},
    );
  }

  // --- Offboarding (Req 69) ---

  /** Exporta los datos de negocio de la Empresa (Req 69.1). */
  exportar(id: string): Observable<ExportacionTenant> {
    return this.http.post<ExportacionTenant>(
      this.api.url(`/empresas/${id}/offboarding/exportar`),
      {},
    );
  }

  /** Cancela la Empresa e inicia el Periodo_Gracia (Req 69.2). */
  cancelar(id: string): Observable<Empresa> {
    return this.http.post<Empresa>(this.api.url(`/empresas/${id}/offboarding/cancelar`), {});
  }

  /** Elimina/anonimiza los datos tras el Periodo_Gracia (Req 69.3, 69.4). */
  eliminarDefinitivamente(id: string): Observable<ResultadoEliminacionTenant> {
    return this.http.post<ResultadoEliminacionTenant>(
      this.api.url(`/empresas/${id}/offboarding/eliminar`),
      {},
    );
  }
}
