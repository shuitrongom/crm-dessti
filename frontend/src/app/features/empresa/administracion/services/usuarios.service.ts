// =============================================================================
// Servicio de gestion de Usuarios (admin_empresa) (Req 4)
// -----------------------------------------------------------------------------
// Integra el contrato REST del backend (platform.security.usuarios), acotado al
// tenant de la sesion:
//   - GET  /usuarios?page&size&q            (usuario:listar)
//   - GET  /usuarios/{id}                   (usuario:leer)
//   - POST /usuarios                        (usuario:crear)
//   - PUT  /usuarios/{id}                   (usuario:actualizar)  -> nombreVisible
//   - PUT  /usuarios/{id}/roles             (usuario:actualizar)  -> rolIds
//   - POST /usuarios/{id}/desactivar        (usuario:actualizar)
//   - GET  /roles/asignables                (rol:listar)
//
// El listado reutiliza el contrato de paginacion PaginaResponse comun a todos
// los listados del servidor. Las cuentas se operan por seleccion de fila; el
// identificador tecnico NO se expone en la interfaz.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../../core/services/api-config.service';
import { PaginaResponse } from '../../../../core/models/pagina-response';

/** Rol asignado a una cuenta (vista minima del backend). */
export interface RolAsignado {
  id: string;
  nombre: string;
}

/** Cuenta de Usuario (UsuarioDto del backend). */
export interface Usuario {
  id: string;
  identificadorAcceso: string;
  /** Nombre para mostrar; puede ser nulo si no se capturo. */
  nombreVisible: string | null;
  activo: boolean;
  roles: RolAsignado[];
}

/**
 * Rol que la empresa puede asignar (GET /roles/asignables). Solo incluye los
 * roles contratables por el tenant; el `modulo` puede ser nulo (rol transversal).
 */
export interface RolAsignable {
  id: string;
  nombre: string;
  modulo: string | null;
  descripcion?: string;
}

/** Cuerpo de POST /usuarios (Req 4.1). */
export interface CrearUsuarioRequest {
  identificadorAcceso: string;
  password: string;
  /** Opcional: nombre para mostrar (<= 200). */
  nombreVisible?: string | null;
  rolIds: string[];
}

/** Cuerpo de PUT /usuarios/{id} (edicion de datos descriptivos). */
export interface ActualizarUsuarioRequest {
  nombreVisible?: string | null;
}

/** Cuerpo de PUT /usuarios/{id}/roles (Req 4.3). */
export interface AsignarRolesRequest {
  rolIds: string[];
}

@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /**
   * Lista las cuentas del tenant, paginadas y filtrables por texto libre `q`
   * (busqueda case-insensitive sobre identificador/nombre). `q` solo se envia
   * cuando trae contenido.
   */
  listar(page = 0, size = 20, q: string | null = null): Observable<PaginaResponse<Usuario>> {
    let params = new HttpParams().set('page', page).set('size', size);
    const termino = q?.trim();
    if (termino) {
      params = params.set('q', termino);
    }
    return this.http.get<PaginaResponse<Usuario>>(this.api.url('/usuarios'), { params });
  }

  /** Consulta una cuenta por su identificador. */
  consultar(id: string): Observable<Usuario> {
    return this.http.get<Usuario>(this.api.url(`/usuarios/${id}`));
  }

  /** Crea una cuenta de Usuario (Req 4.1). */
  crear(request: CrearUsuarioRequest): Observable<Usuario> {
    return this.http.post<Usuario>(this.api.url('/usuarios'), request);
  }

  /** Actualiza el nombre para mostrar de una cuenta (PUT /usuarios/{id}). */
  actualizarNombre(id: string, request: ActualizarUsuarioRequest): Observable<Usuario> {
    return this.http.put<Usuario>(this.api.url(`/usuarios/${id}`), request);
  }

  /** Reemplaza los roles de una cuenta (Req 4.3). */
  asignarRoles(id: string, request: AsignarRolesRequest): Observable<Usuario> {
    return this.http.put<Usuario>(this.api.url(`/usuarios/${id}/roles`), request);
  }

  /** Desactiva una cuenta para impedir el inicio de sesion (Req 4.2). */
  desactivar(id: string): Observable<Usuario> {
    return this.http.post<Usuario>(this.api.url(`/usuarios/${id}/desactivar`), {});
  }

  /** Lista los roles que la empresa puede asignar (GET /roles/asignables). */
  rolesAsignables(): Observable<RolAsignable[]> {
    return this.http.get<RolAsignable[]>(this.api.url('/roles/asignables'));
  }
}
