// =============================================================================
// Vista de administracion de Usuarios (admin_empresa) (Req 4)
// -----------------------------------------------------------------------------
// Tablero enterprise de las cuentas de la empresa: tabla paginada con busqueda
// de texto con debounce, alta de cuenta (dialogo), edicion de nombre y roles
// (dialogo) y desactivacion con confirmacion (accion sensible). Las cuentas se
// operan por SELECCION DE FILA: el identificador tecnico no se muestra ni se
// captura en ningun punto. Estados de carga/vacio/error consistentes.
//
// La busqueda usa un signal enlazado a un Observable (toObservable) con
// debounceTime(300ms) para no lanzar una peticion por cada tecla; al cambiar el
// texto se reinicia a la pagina 0. Las acciones se gobiernan por el permiso
// atomico correspondiente (deny-by-default).
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { debounceTime, distinctUntilChanged, skip } from 'rxjs/operators';

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
import { AuthService } from '../../../../core/auth/auth.service';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../../shared/models/estado-solicitud';

import { UsuariosService, Usuario } from '../services/usuarios.service';
import { CrearUsuarioDialog } from './crear-usuario-dialog';
import { EditarUsuarioDialog, EditarUsuarioDialogData } from './editar-usuario-dialog';

@Component({
  selector: 'app-admin-usuarios',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.scss',
})
export class AdminUsuarios {
  private readonly service = inject(UsuariosService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  /** Columnas de la tabla de cuentas. */
  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Nombre' },
    { clave: 'identificador', encabezado: 'Identificador' },
    // "Roles" es la columna menos critica en pantallas estrechas: se oculta por
    // debajo de `md` para que Nombre/Identificador/Estado/Acciones quepan sin
    // scroll horizontal (Req 52). El detalle sigue disponible en la edicion.
    { clave: 'roles', encabezado: 'Roles', ocultarEnMovil: true },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  /** Estado de la pagina de cuentas. */
  protected readonly estado = signal<EstadoSolicitud<Usuario[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  /** Texto de busqueda escrito por el Usuario (sin debounce). */
  protected readonly busqueda = signal('');
  /** Texto de busqueda ya aplicado a la ultima carga (para el estado vacio). */
  private readonly busquedaAplicada = signal('');

  /** Mensaje del estado vacio: refleja la busqueda cuando la hay. */
  protected readonly mensajeVacio = computed(() => {
    const q = this.busquedaAplicada().trim();
    return q
      ? `No se encontraron usuarios para «${q}».`
      : 'Aun no hay usuarios registrados. Crea el primero con «Crear usuario».';
  });

  protected readonly puedeCrear = this.auth.tienePermiso('usuario', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('usuario', 'actualizar');

  constructor() {
    // Debounce de la busqueda: reinicia a la pagina 0 y recarga. Se omite (skip)
    // la emision inicial del signal para no duplicar la carga del constructor.
    toObservable(this.busqueda)
      .pipe(skip(1), debounceTime(300), distinctUntilChanged())
      .subscribe(() => {
        this.page.set(0);
        this.cargar();
      });
    this.cargar();
  }

  /** Carga la pagina actual de cuentas (respeta busqueda + paginacion). */
  cargar(): void {
    this.estado.set(cargando());
    const q = this.busqueda().trim();
    this.busquedaAplicada.set(q);
    this.service.listar(this.page(), this.size(), q).subscribe({
      next: (pagina) => {
        this.total.set(pagina.totalElements);
        this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  /** Actualiza el texto de busqueda (el debounce dispara la recarga). */
  cambiarBusqueda(texto: string): void {
    this.busqueda.set(texto);
  }

  /** Limpia la busqueda; el debounce recargara sin filtro de texto. */
  limpiarBusqueda(): void {
    this.busqueda.set('');
  }

  /** Reacciona al cambio de pagina. */
  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Nombre para mostrar de una cuenta, o un guion cuando no se capturo. */
  protected nombreDe(usuario: Usuario): string {
    const nombre = usuario.nombreVisible?.trim();
    return nombre ? nombre : '—';
  }

  /** Abre el dialogo de alta de cuenta (Req 4.1). */
  crear(): void {
    const ref = this.dialog.open(CrearUsuarioDialog, {
      width: 'min(680px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
    });
    ref.afterClosed().subscribe((creado: Usuario | undefined) => {
      if (creado) {
        this.toast.exito(`Usuario "${creado.identificadorAcceso}" creado.`);
        this.page.set(0);
        this.cargar();
      }
    });
  }

  /** Abre el dialogo de edicion de nombre y roles (Req 4.3). */
  editar(usuario: Usuario): void {
    const data: EditarUsuarioDialogData = { usuario };
    const ref = this.dialog.open(EditarUsuarioDialog, {
      width: 'min(680px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((actualizado: Usuario | undefined) => {
      if (actualizado) {
        this.toast.exito('Usuario actualizado correctamente.');
        this.cargar();
      }
    });
  }

  /** Desactiva una cuenta con confirmacion (accion sensible, Req 4.2, 54). */
  async desactivar(usuario: Usuario): Promise<void> {
    const etiqueta = this.nombreDe(usuario) !== '—' ? this.nombreDe(usuario) : usuario.identificadorAcceso;
    const ok = await this.confirm.confirmar({
      titulo: 'Desactivar usuario',
      mensaje: `La cuenta "${etiqueta}" no podra iniciar sesion. Deseas continuar?`,
      textoConfirmar: 'Desactivar',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.desactivar(usuario.id).subscribe({
      next: () => {
        this.toast.exito(`Usuario "${etiqueta}" desactivado.`);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
