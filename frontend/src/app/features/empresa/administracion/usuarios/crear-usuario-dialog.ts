// =============================================================================
// Dialogo de alta de cuenta de Usuario (admin_empresa) (Req 4.1)
// -----------------------------------------------------------------------------
// Formulario reactivo enterprise para dar de alta una cuenta de la empresa con
// campos claros (nombre para mostrar, correo/identificador, contrasena inicial)
// y un SELECTOR DE ROLES por casillas alimentado por GET /roles/asignables
// (nombre + descripcion), en lugar de un campo de texto con identificadores.
//
// Mapeo de errores de negocio del backend:
//   - 409 -> "Ya existe una cuenta con ese identificador" (campo + formulario).
//   - 422 -> "Alguno de los roles seleccionados no esta disponible en tu plan."
// Al crear, cierra devolviendo la cuenta creada (la vista refresca la tabla).
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';

import { UsuariosService, RolAsignable, Usuario } from '../services/usuarios.service';
import { mensajeDeError } from '../../../../core/services/error-mensajes';

@Component({
  selector: 'app-crear-usuario-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
  ],
  templateUrl: './crear-usuario-dialog.html',
  styleUrl: './usuario-dialog.scss',
})
export class CrearUsuarioDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(UsuariosService);
  private readonly dialogRef = inject(MatDialogRef<CrearUsuarioDialog, Usuario>);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Roles que la empresa puede asignar (GET /roles/asignables). */
  protected readonly roles = signal<RolAsignable[]>([]);
  /** `true` cuando ya se resolvio la carga de roles y no hay ninguno. */
  protected readonly sinRoles = signal(false);
  /** Ids de rol seleccionados por el admin. */
  protected readonly seleccion = signal<Set<string>>(new Set());

  protected readonly formulario = this.fb.nonNullable.group({
    nombreVisible: ['', [Validators.maxLength(200)]],
    identificadorAcceso: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(255)]],
  });

  constructor() {
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

  /** Normaliza un texto opcional: `null` cuando queda en blanco. */
  private opcional(valor: string): string | null {
    const limpio = valor.trim();
    return limpio ? limpio : null;
  }

  /** Envia el alta de la cuenta. */
  protected guardar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const v = this.formulario.getRawValue();
    this.service
      .crear({
        identificadorAcceso: v.identificadorAcceso.trim(),
        password: v.password,
        nombreVisible: this.opcional(v.nombreVisible),
        rolIds: Array.from(this.seleccion()),
      })
      .subscribe({
        next: (usuario) => {
          this.guardando.set(false);
          this.dialogRef.close(usuario);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          if (e.status === 409) {
            this.formulario.controls.identificadorAcceso.setErrors({ duplicado: true });
            this.error.set('Ya existe una cuenta con ese identificador.');
          } else if (e.status === 422) {
            this.error.set('Alguno de los roles seleccionados no esta disponible en tu plan.');
          } else {
            this.error.set(mensajeDeError(e));
          }
        },
      });
  }

  /** Cancela el alta. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
