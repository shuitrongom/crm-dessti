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

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../../shared/components/state-container/state-container';
import { AuthService } from '../../../../core/auth/auth.service';
import { mensajeDeError } from '../../../../core/services/error-mensajes';
import { RolInfoDialog, type DatosRolInfo } from './rol-info-dialog';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../../shared/models/estado-solicitud';

import { UsuariosService, RolAsignable } from '../services/usuarios.service';

/** Rol asignable ya presentado (nombre humanizado + descripcion + icono). */
interface RolPresentado {
  id: string;
  nombre: string;
  descripcion: string;
  /** Icono representativo del rol (derivado de su nombre). */
  icono: string;
}

/** Grupo de roles asignables por modulo (Administracion o clave de modulo). */
interface GrupoRoles {
  /** Etiqueta visible del grupo (badge de modulo). */
  modulo: string;
  /** `true` cuando es el grupo transversal (modulo nulo). */
  esAdministracion: boolean;
  /** Tono de color del grupo (admin, m0..m5) para el acento de las tarjetas. */
  tono: string;
  /** Icono del grupo/modulo. */
  icono: string;
  roles: RolPresentado[];
}

/** Etiqueta del grupo transversal (roles con modulo nulo). */
const GRUPO_ADMINISTRACION = 'Administración';

/** Iconos por palabra clave del nombre del rol (para el chip de cada tarjeta). */
const ICONO_POR_ROL: { patron: RegExp; icono: string }[] = [
  { patron: /admin/, icono: 'admin_panel_settings' },
  { patron: /director/, icono: 'workspace_premium' },
  { patron: /gerente|gerencia/, icono: 'supervisor_account' },
  { patron: /supervisor/, icono: 'visibility' },
  { patron: /contad|contabil/, icono: 'account_balance' },
  { patron: /tesor/, icono: 'savings' },
  { patron: /venta|comercial/, icono: 'trending_up' },
  { patron: /compra|almacen/, icono: 'shopping_cart' },
  { patron: /calidad/, icono: 'verified' },
  { patron: /diseno|diseño/, icono: 'design_services' },
  { patron: /produccion|producción/, icono: 'precision_manufacturing' },
  { patron: /instalac/, icono: 'construction' },
  { patron: /marketing|social/, icono: 'campaign' },
  { patron: /rh|nomina|nómina/, icono: 'groups' },
];

/** Iconos por modulo (grupo). */
const ICONO_POR_MODULO: Record<string, string> = {
  'Activos fijos': 'savings',
  Comercial: 'storefront',
  Compras: 'shopping_cart',
  Contabilidad: 'account_balance',
  Operacion: 'engineering',
  Operación: 'engineering',
  'Redes sociales': 'campaign',
  'Rh nomina': 'groups',
  Tesoreria: 'account_balance_wallet',
  Tesorería: 'account_balance_wallet',
};

@Component({
  selector: 'app-admin-roles',
  imports: [MatChipsModule, MatIconModule, MatExpansionModule, PageHeader, StateContainer],
  templateUrl: './roles.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './roles.scss',
})
export class AdminRoles {
  private readonly service = inject(UsuariosService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  /** Icono representativo de un rol segun su nombre (o uno por defecto). */
  private iconoDeRol(nombre: string): string {
    const n = nombre.toLowerCase();
    for (const { patron, icono } of ICONO_POR_ROL) {
      if (patron.test(n)) {
        return icono;
      }
    }
    return 'badge';
  }

  /** Abre el dialogo informativo de un rol (Req 27). */
  protected abrirRol(rol: RolPresentado, grupo: GrupoRoles): void {
    const datos: DatosRolInfo = {
      nombre: rol.nombre,
      modulo: grupo.modulo,
      esAdministracion: grupo.esAdministracion,
      descripcion: rol.descripcion,
      icono: rol.icono,
      tono: grupo.tono,
    };
    this.dialog.open(RolInfoDialog, {
      data: datos,
      width: '30rem',
      maxWidth: '92vw',
      autoFocus: false,
    });
  }

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
      const nombre = this.humanizar(rol.nombre);
      lista.push({
        id: rol.id,
        nombre,
        descripcion: rol.descripcion?.trim() || 'Sin descripción.',
        icono: this.iconoDeRol(nombre),
      });
      mapa.set(clave, lista);
    }
    const grupos = Array.from(mapa.entries())
      .map(([clave, roles]) => {
        const esAdmin = clave === GRUPO_ADMINISTRACION;
        const modulo = esAdmin ? GRUPO_ADMINISTRACION : this.humanizar(clave);
        return {
          modulo,
          esAdministracion: esAdmin,
          tono: 'neutro',
          icono: esAdmin ? 'admin_panel_settings' : (ICONO_POR_MODULO[modulo] ?? 'extension'),
          roles: roles.sort((a, b) => a.nombre.localeCompare(b.nombre)),
        };
      })
      // Administracion (roles transversales) primero; el resto alfabetico.
      .sort((a, b) => {
        if (a.esAdministracion !== b.esAdministracion) {
          return a.esAdministracion ? -1 : 1;
        }
        return a.modulo.localeCompare(b.modulo);
      });
    // Asigna un tono ciclico por grupo (admin fijo; el resto m0..m5) para colorear.
    return grupos.map((g, i) => ({
      ...g,
      tono: g.esAdministracion ? 'admin' : `m${i % 6}`,
    }));
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
