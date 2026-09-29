// =============================================================================
// Dialogo "Usuarios de la empresa" (super_admin) (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Lista las cuentas de Usuario de una Empresa para que el super_admin pueda
// recuperar el identificador de acceso (login) de un Usuario cuando su
// Administrador_Empresa lo olvida (caso de soporte). Consulta
// GET /empresas/{id}/usuarios (permiso empresa:leer). Nunca muestra la
// contrasena ni su hash (Req 11.3); solo login, nombre visible, estado y roles.
// Cada login tiene un boton para copiarlo al portapapeles. Accesible (labels,
// aria, foco) y responsive; solo tokens del Sistema de Diseno.
// =============================================================================

import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { EmpresasService } from '../services/empresas.service';
import { UsuarioEmpresa } from '../models/plataforma.models';
import { mensajeDeError } from '../../../core/services/error-mensajes';

/** Datos de entrada del dialogo: la Empresa cuyos usuarios se listan. */
export interface UsuariosEmpresaDialogData {
  empresaId: string;
  empresaNombre: string;
}

@Component({
  selector: 'app-usuarios-empresa-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './usuarios-empresa-dialog.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './usuarios-empresa-dialog.scss',
})
export class UsuariosEmpresaDialog {
  private readonly empresasService = inject(EmpresasService);
  protected readonly data = inject<UsuariosEmpresaDialogData>(MAT_DIALOG_DATA);

  /** `true` mientras se cargan las cuentas. */
  protected readonly cargando = signal(true);
  /** Mensaje de error de la carga, o `null` si fue exitosa. */
  protected readonly error = signal<string | null>(null);
  /** Cuentas de la Empresa; vacio mientras carga o si no hay. */
  protected readonly usuarios = signal<UsuarioEmpresa[]>([]);
  /** Login recien copiado al portapapeles (feedback temporal); `null` si ninguno. */
  protected readonly copiado = signal<string | null>(null);

  constructor() {
    this.cargar();
  }

  /** Carga las cuentas de la Empresa (GET /empresas/{id}/usuarios). */
  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.empresasService.listarUsuarios(this.data.empresaId).subscribe({
      next: (usuarios) => {
        this.usuarios.set(usuarios);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(e.status === 404 ? 'No se encontró la empresa.' : mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }

  /** Copia un identificador de acceso al portapapeles (feedback temporal). */
  protected async copiar(identificador: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(identificador);
      this.copiado.set(identificador);
      setTimeout(() => {
        if (this.copiado() === identificador) {
          this.copiado.set(null);
        }
      }, 2000);
    } catch {
      // Sin permiso de portapapeles: el Usuario puede copiar manualmente.
      this.copiado.set(null);
    }
  }
}
