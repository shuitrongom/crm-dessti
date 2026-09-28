// =============================================================================
// Dialogo informativo de un Rol (admin_empresa) (Req 27, 28)
// -----------------------------------------------------------------------------
// Al hacer clic en una tarjeta de rol se abre este dialogo, que explica en
// lenguaje llano que hace el rol, a que modulo pertenece y una guia rapida de lo
// que suele poder (y no poder) hacer. Ayuda al administrador a decidir que rol
// asignar a cada persona. Solo-lectura.
// =============================================================================

import { Component, computed, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** Datos del rol que recibe el dialogo. */
export interface DatosRolInfo {
  nombre: string;
  modulo: string;
  esAdministracion: boolean;
  descripcion: string;
  /** Icono representativo (resuelto por la vista segun el modulo). */
  icono: string;
  /** Tono de color del modulo (para el acento del dialogo). */
  tono: string;
}

@Component({
  selector: 'app-rol-info-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './rol-info-dialog.html',
  styleUrl: './rol-info-dialog.scss',
})
export class RolInfoDialog {
  protected readonly datos = inject<DatosRolInfo>(MAT_DIALOG_DATA);

  /** Clase de tono para el acento del dialogo. */
  protected readonly claseTono = computed(() => `rid--${this.datos.tono}`);

  /**
   * Deriva una pista de "alcance" a partir de la descripcion: si menciona
   * "aprueba" tiene facultad de aprobacion; si dice "sin aprobar"/"lectura" es de
   * alcance operativo/consulta. Es una guia orientativa para el administrador.
   */
  protected readonly alcance = computed<string>(() => {
    const d = this.datos.descripcion.toLowerCase();
    if (d.includes('sin aprobar') || d.includes('lectura')) {
      return 'Alcance operativo o de consulta: puede trabajar en su área, pero las aprobaciones quedan reservadas a roles de mando.';
    }
    if (d.includes('aprueba') || d.includes('autoriza')) {
      return 'Rol con facultad de aprobación/autorización: puede validar operaciones clave de su área.';
    }
    if (this.datos.esAdministracion) {
      return 'Rol transversal de administración: gestiona configuración de la empresa (usuarios, roles, branding), no operación diaria.';
    }
    return 'Rol operativo del módulo: trabaja sobre las tareas propias de su área.';
  });
}
