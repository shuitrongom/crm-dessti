// =============================================================================
// Vista de gestion de Sesiones (admin_empresa) (Req 68)
// -----------------------------------------------------------------------------
// Permite consultar las sesiones (Token_Refresco) activas de una cuenta de la
// empresa y revocarlas todas con confirmacion (accion sensible). La cuenta se
// elige mediante un SELECTOR de usuarios alimentado por GET /usuarios (nunca se
// escribe un identificador tecnico): el valor de cada opcion es el id de la
// cuenta y la etiqueta muestra "nombre — correo" (o solo el correo cuando no
// hay nombre). Al seleccionar una cuenta se cargan sus sesiones en una tabla.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../../shared/components/state-container/state-container';
import {
  ColumnaTabla,
  DataTable,
  CeldaTablaDirective,
  CambioPagina,
} from '../../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../../shared/services/notificaciones.service';
import { mensajeDeError } from '../../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../../shared/models/estado-solicitud';

import { SesionesService, SesionActiva } from '../services/sesiones.service';
import { UsuariosService, Usuario } from '../services/usuarios.service';

@Component({
  selector: 'app-admin-sesiones',
  imports: [
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './sesiones.html',
  styleUrl: './sesiones.scss',
})
export class AdminSesiones {
  private readonly service = inject(SesionesService);
  private readonly usuarios = inject(UsuariosService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'jti', encabezado: 'Sesion (jti)' },
    { clave: 'emitidoEn', encabezado: 'Emitida' },
    { clave: 'expiraEn', encabezado: 'Expira' },
  ];

  /** Cuentas disponibles para el selector (GET /usuarios). */
  protected readonly cuentas = signal<Usuario[]>([]);
  /** Id de la cuenta seleccionada; `null` mientras no se elige ninguna. */
  protected readonly usuarioId = signal<string | null>(null);

  /** Estado del listado de sesiones; `null` antes de la primera consulta. */
  protected readonly estado = signal<EstadoSolicitud<SesionActiva[]> | null>(null);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  /** `true` cuando hay una cuenta seleccionada (habilita "Revocar sesiones"). */
  protected readonly haySeleccion = computed(() => this.usuarioId() !== null);

  constructor() {
    // Carga la primera pagina de cuentas (tamano generoso para el selector).
    this.usuarios.listar(0, 100).subscribe({
      next: (pagina) => this.cuentas.set(pagina.content),
      error: () => this.cuentas.set([]),
    });
  }

  /** Etiqueta de una cuenta: "nombre — correo" o solo el correo si no hay nombre. */
  protected etiquetaCuenta(usuario: Usuario): string {
    const nombre = usuario.nombreVisible?.trim();
    return nombre ? `${nombre} — ${usuario.identificadorAcceso}` : usuario.identificadorAcceso;
  }

  /** Reacciona a la seleccion de una cuenta en el selector: carga sus sesiones. */
  seleccionar(id: string): void {
    this.usuarioId.set(id);
    this.page.set(0);
    this.consultar();
  }

  /** Consulta las sesiones activas de la cuenta seleccionada (Req 68.5). */
  consultar(): void {
    const id = this.usuarioId();
    if (!id) {
      return;
    }
    this.estado.set(cargando());
    this.service.listar(id, this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.total.set(pagina.totalElements);
        this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.consultar();
  }

  /** Revoca todas las sesiones vigentes de la cuenta con confirmacion (Req 68.2, 54). */
  async revocar(): Promise<void> {
    const id = this.usuarioId();
    if (!id) {
      return;
    }
    const ok = await this.confirm.confirmar({
      titulo: 'Revocar sesiones',
      mensaje: 'Se cerraran todas las sesiones vigentes de la cuenta. Deseas continuar?',
      textoConfirmar: 'Revocar sesiones',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.revocar(id).subscribe({
      next: () => {
        this.toast.exito('Sesiones revocadas.');
        this.consultar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
