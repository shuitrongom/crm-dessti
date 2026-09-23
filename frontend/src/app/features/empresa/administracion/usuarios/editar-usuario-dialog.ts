// =============================================================================
// Dialogo de edicion de cuenta de Usuario (admin_empresa) (Req 4.3)
// -----------------------------------------------------------------------------
// Edita el NOMBRE PARA MOSTRAR (PUT /usuarios/{id}) y reasigna los ROLES
// (PUT /usuarios/{id}/roles) de una cuenta existente. El formulario se prellena
// con los valores actuales recibidos por MAT_DIALOG_DATA y el selector de roles
// se alimenta de GET /roles/asignables (mismo componente visual que el alta).
//
// Al guardar solo se llaman los endpoints necesarios: si cambio el nombre, PUT
// del nombre; si cambiaron los roles, PUT de roles; si cambiaron ambos, ambos
// (en secuencia). El 422 de roles se mapea a un mensaje de plan/rol.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { Observable, of, switchMap } from 'rxjs';

import { UsuariosService, RolAsignable, Usuario } from '../services/usuarios.service';
import { mensajeDeError } from '../../../../core/services/error-mensajes';

/** Datos de entrada del dialogo: la cuenta a editar (para prellenar). */
export interface EditarUsuarioDialogData {
  usuario: Usuario;
}

@Component({
  selector: 'app-editar-usuario-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
  ],
  templateUrl: './editar-usuario-dialog.html',
  styleUrl: './usuario-dialog.scss',
})
export class EditarUsuarioDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(UsuariosService);
  private readonly dialogRef = inject(MatDialogRef<EditarUsuarioDialog, Usuario>);
  private readonly data = inject<EditarUsuarioDialogData>(MAT_DIALOG_DATA);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Roles que la empresa puede asignar (GET /roles/asignables). */
  protected readonly roles = signal<RolAsignable[]>([]);
  /** `true` cuando ya se resolvio la carga de roles y no hay ninguno. */
  protected readonly sinRoles = signal(false);
  /** Ids de rol seleccionados actualmente. */
  protected readonly seleccion = signal<Set<string>>(new Set());

  /** Identificador de acceso (solo lectura; contexto de la edicion). */
  protected readonly identificador = this.data.usuario.identificadorAcceso;
  /** Seleccion original de roles, para detectar cambios. */
  private readonly rolesOriginales = new Set(this.data.usuario.roles.map((r) => r.id));
  /** Nombre original, para detectar cambios. */
  private readonly nombreOriginal = this.data.usuario.nombreVisible ?? '';

  protected readonly formulario = this.fb.nonNullable.group({
    nombreVisible: [this.nombreOriginal, [Validators.maxLength(200)]],
  });

  constructor() {
    this.seleccion.set(new Set(this.rolesOriginales));
    this.service.rolesAsignables().subscribe({
      next: (roles) => {
        this.roles.set(roles);
        this.sinRoles.set(roles.length === 0);
      },
      error: () => {
        this.roles.set([]);
        this.sinRoles.set(true);
      },
    });
  }

  /** Indica si un rol esta seleccionado. */
  protected estaSeleccionado(id: string): boolean {
    return this.seleccion().has(id);
  }

  /** Alterna la seleccion de un rol. */
  protected alternar(id: string, seleccionado: boolean): void {
    const actual = new Set(this.seleccion());
    if (seleccionado) {
      actual.add(id);
    } else {
      actual.delete(id);
    }
    this.seleccion.set(actual);
  }

  /** `true` cuando el nombre para mostrar cambio respecto al original. */
  private nombreCambio(nombre: string): boolean {
    return nombre !== this.nombreOriginal;
  }

  /** `true` cuando la seleccion de roles difiere de la original. */
  private rolesCambiaron(): boolean {
    const actual = this.seleccion();
    if (actual.size !== this.rolesOriginales.size) {
      return true;
    }
    for (const id of actual) {
      if (!this.rolesOriginales.has(id)) {
        return true;
      }
    }
    return false;
  }

  /** Guarda los cambios: solo llama los endpoints necesarios. */
  protected guardar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    const nombre = this.formulario.getRawValue().nombreVisible.trim();
    const cambioNombre = this.nombreCambio(nombre);
    const cambioRoles = this.rolesCambiaron();

    if (!cambioNombre && !cambioRoles) {
      // Sin cambios: cierra sin efectos y sin peticiones.
      this.dialogRef.close(undefined);
      return;
    }

    this.guardando.set(true);
    const id = this.data.usuario.id;

    // Encadena solo las operaciones necesarias; la ultima define el resultado.
    const nombreOp: Observable<Usuario | null> = cambioNombre
      ? this.service.actualizarNombre(id, { nombreVisible: nombre ? nombre : null })
      : of(null);

    nombreOp
      .pipe(
        switchMap((resNombre) =>
          cambioRoles
            ? this.service.asignarRoles(id, { rolIds: Array.from(this.seleccion()) })
            : of(resNombre as Usuario),
        ),
      )
      .subscribe({
        next: (usuario) => {
          this.guardando.set(false);
          this.dialogRef.close(usuario ?? this.data.usuario);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          if (e.status === 422) {
            this.error.set('Alguno de los roles seleccionados no esta disponible en tu plan.');
          } else {
            this.error.set(mensajeDeError(e));
          }
        },
      });
  }

  /** Cancela la edicion. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
