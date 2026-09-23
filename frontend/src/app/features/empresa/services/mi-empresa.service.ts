// =============================================================================
// Servicio de datos de la propia Empresa (admin_empresa) (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend para que el administrador de una Empresa
// consulte y edite los datos de SU propia Empresa (el tenant se deriva del
// contexto autenticado en el servidor; nunca se envia):
//   - GET /empresas/mi-empresa  (rol admin_empresa) -> Empresa
//   - PUT /empresas/mi-empresa  (rol admin_empresa) -> Empresa
//
// A diferencia del ambito plataforma (EmpresasService, super_admin), este
// servicio es de AMBITO EMPRESA: el admin edita solo un subconjunto de datos
// descriptivos (nombre, contacto, direccion, logo). NO puede cambiar
// rfc/giro/plan/estado (no forman parte del contrato). Un super_admin llamando
// a estos endpoints recibe 403 (comportamiento correcto del backend).
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { Empresa } from '../../plataforma/models/plataforma.models';

/**
 * Cuerpo de PUT /empresas/mi-empresa (ActualizarMiEmpresaRequest). `nombre` y
 * `emailContacto` son obligatorios (backend); el resto es opcional. NO incluye
 * rfc/giro/plan/estado: el admin de empresa no puede modificarlos.
 */
export interface ActualizarMiEmpresaRequest {
  nombre: string;
  emailContacto: string;
  telefono?: string | null;
  direccionCalle?: string | null;
  direccionCiudad?: string | null;
  direccionEstado?: string | null;
  direccionCp?: string | null;
  direccionPais?: string | null;
  /** Logo del branding como data-URI (base64); backend limita a 1 MiB. */
  logo?: string | null;
}

@Injectable({ providedIn: 'root' })
export class MiEmpresaService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Consulta los datos de la Empresa del Usuario autenticado (GET /empresas/mi-empresa). */
  consultarMiEmpresa(): Observable<Empresa> {
    return this.http.get<Empresa>(this.api.url('/empresas/mi-empresa'));
  }

  /**
   * Actualiza los datos descriptivos de la propia Empresa (PUT /empresas/mi-empresa).
   * Errores: 404 (el tenant no tiene empresa), 400 (correo/nombre invalidos).
   */
  actualizarMiEmpresa(request: ActualizarMiEmpresaRequest): Observable<Empresa> {
    return this.http.put<Empresa>(this.api.url('/empresas/mi-empresa'), request);
  }
}
