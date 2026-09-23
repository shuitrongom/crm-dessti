// =============================================================================
// Vista de Roles y permisos (admin_empresa) (Req 27, 28)
// -----------------------------------------------------------------------------
// Presenta el catalogo de roles que la empresa PUEDE asignar segun su plan
// (GET /roles/asignables): solo los roles contratados/asignables, filtrados por
// los modulos contratados. Los roles se agrupan por modulo (Administracion —los
// roles transversales con modulo nulo— primero, luego los roles de modulo) y se
// muestran como tarjetas enterprise (nombre humanizado, descripcion y badge de
// modulo). Debajo, un panel expandible y colapsado por defecto muestra los roles
// del Usuario autenticado como referencia secundaria (derivados de los claims,
// AuthService), en lugar del volcado crudo del catalogo de permisos.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';

import { PageHeader } from '../../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../../shared/components/state-container/state-container';
import { AuthService } from '../../../../core/auth/auth.service';
import { mensajeDeError } from '../../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../../shared/models/estado-solicitud';

import { UsuariosService, RolAsignable } from '../services/usuarios.service';

/** Rol asignable ya presentado (nombre humanizado + descripcion). */
interface RolPresentado {
  id: string;
  nombre: string;
  descripcion: string;
}

/** Grupo de roles asignables por modulo (Administracion o clave de modulo). */
interface GrupoRoles {
  /** Etiqueta visible del grupo (badge de modulo). */
  modulo: string;
  /** `true` cuando es el grupo transversal (modulo nulo). */
  esAdministracion: boolean;
  roles: RolPresentado[];
}

/** Etiqueta del grupo transversal (roles con modulo nulo). */
const GRUPO_ADMINISTRACION = 'Administración';

@Component({
  selector: 'app-admin-roles',
  imports: [
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatExpansionModule,
    PageHeader,
    StateContainer,
  ],
  templateUrl: './roles.html',
  styleUrl: './roles.scss',
})
export class AdminRoles {
  private readonly service = inject(UsuariosService);
  private readonly auth = inject(AuthService);

  /** Estado del catalogo de roles asignables. */
  protected readonly estado = signal<EstadoSolicitud<RolAsignable[]>>(cargando());

  /** Roles del Usuario autenticado (referencia secundaria). */
  protected readonly rolesUsuario = computed(() =>
    this.auth.roles().map((rol) => this.humanizar(rol)),
  );

  /** Roles asignables agrupados por modulo (Administracion primero). */
  protected readonly grupos = computed<GrupoRoles[]>(() => {
    const datos = this.estado().datos ?? [];
    const mapa = new Map<string, RolPresentado[]>();
    for (const rol of datos) {
      const clave = rol.modulo ?? GRUPO_ADMINISTRACION;
      const lista = mapa.get(clave) ?? [];
      lista.push({
        id: rol.id,
        nombre: this.humanizar(rol.nombre),
        descripcion: rol.descripcion?.trim() || 'Sin descripción.',
      });
      mapa.set(clave, lista);
    }
    return Array.from(mapa.entries())
      .map(([clave, roles]) => ({
        modulo: clave === GRUPO_ADMINISTRACION ? GRUPO_ADMINISTRACION : this.humanizar(clave),
        esAdministracion: clave === GRUPO_ADMINISTRACION,
        roles: roles.sort((a, b) => a.nombre.localeCompare(b.nombre)),
      }))
      // Administracion (roles transversales) primero; el resto alfabetico.
      .sort((a, b) => {
        if (a.esAdministracion !== b.esAdministracion) {
          return a.esAdministracion ? -1 : 1;
        }
        return a.modulo.localeCompare(b.modulo);
      });
  });

  constructor() {
    this.cargar();
  }

  /** Carga el catalogo de roles asignables (GET /roles/asignables). */
  cargar(): void {
    this.estado.set(cargando());
    this.service.rolesAsignables().subscribe({
      next: (roles) => this.estado.set(conDatos(roles, roles.length === 0)),
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  /**
   * Humaniza una clave tecnica (snake_case) a un texto legible en espanol:
   * `admin_empresa` -> "Admin empresa"; `contexto_organizacion` -> "Contexto
   * organizacion". No traduce; solo mejora la presentacion.
   */
  protected humanizar(clave: string): string {
    const limpio = clave.replace(/_/g, ' ').trim();
    if (!limpio) {
      return clave;
    }
    return limpio.charAt(0).toUpperCase() + limpio.slice(1);
  }
}
