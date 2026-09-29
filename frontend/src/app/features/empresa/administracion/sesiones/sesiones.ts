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

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
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
import { Router } from '@angular/router';

import { ConfirmDialogService } from '../../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../../shared/services/notificaciones.service';
import { OperacionOverlayService } from '../../../../shared/components/operacion-overlay/operacion-overlay';
import { AuthService } from '../../../../core/auth/auth.service';
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
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './sesiones.scss',
})
export class AdminSesiones {
  private readonly service = inject(SesionesService);
  private readonly usuarios = inject(UsuariosService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** `true` cuando la cuenta seleccionada es la del propio Usuario autenticado. */
  protected readonly esMiCuenta = computed(() => this.usuarioId() === this.auth.identificador());

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'sesion', encabezado: 'Sesión' },
    { clave: 'emitidoEn', encabezado: 'Iniciada' },
    { clave: 'expiraEn', encabezado: 'Expira' },
    { clave: 'estado', encabezado: 'Estado', alineacion: 'fin' },
  ];

  /**
   * Dias restantes (redondeados) hasta que expire una sesion. Sirve para mostrar
   * "Expira en N dias" y para resaltar las que estan por vencer.
   */
  protected diasRestantes(expiraEn: string): number {
    const ms = new Date(expiraEn).getTime() - Date.now();
    return Math.max(0, Math.ceil(ms / (1000 * 60 * 60 * 24)));
  }

  /** Fragmento corto y legible del jti (las sesiones no exponen mas identidad). */
  protected jtiCorto(jti: string): string {
    return jti.length > 13 ? `${jti.slice(0, 8)}…${jti.slice(-4)}` : jti;
  }

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

  /**
   * Revoca todas las sesiones vigentes de la cuenta con confirmacion (Req 68.2, 54).
   *
   * Detalle de seguridad: revocar invalida el Token_Refresco (la "sesion"); el
   * corte efectivo del acceso ocurre en la proxima renovacion del token. Si la
   * cuenta revocada es la del PROPIO Usuario, se cierra su sesion de inmediato
   * (logout local + redirige al login), para que la accion tenga el efecto que
   * espera el administrador ("me saco a mi mismo").
   */
  async revocar(): Promise<void> {
    const id = this.usuarioId();
    if (!id) {
      return;
    }
    const propia = this.esMiCuenta();
    const ok = await this.confirm.confirmar({
      titulo: 'Revocar sesiones',
      mensaje: propia
        ? 'Se cerrarán todas tus sesiones activas y saldrás de la aplicación. Deberás iniciar sesión de nuevo. ¿Deseas continuar?'
        : 'Se cerrarán todas las sesiones vigentes de la cuenta. El acceso se corta en su próxima renovación de token. ¿Deseas continuar?',
      textoConfirmar: 'Revocar sesiones',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.service.revocar(id), {
        tipo: 'eliminar',
        textoProceso: 'Revocando sesiones…',
        textoExito: 'Sesiones revocadas',
      })
      .subscribe({
        next: () => {
          if (propia) {
            // Es mi propia cuenta: cierro sesion de inmediato y voy al login.
            this.toast.info('Tus sesiones fueron revocadas. Vuelve a iniciar sesión.');
            this.auth.logout().subscribe({
              next: () => this.router.navigateByUrl('/login'),
              error: () => this.router.navigateByUrl('/login'),
            });
            return;
          }
          this.toast.exito('Sesiones revocadas.');
          this.consultar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }
}
