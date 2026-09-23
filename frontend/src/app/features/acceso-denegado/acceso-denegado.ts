// =============================================================================
// Vista de acceso denegado (403) (Req 3.2)
// -----------------------------------------------------------------------------
// Se muestra cuando un Usuario autenticado intenta acceder a un ambito o vista
// para el que no esta autorizado (deny-by-default). Ofrece volver al inicio de
// su propio ambito segun su rol.
// =============================================================================

import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-acceso-denegado',
  imports: [MatButtonModule, MatIconModule],
  template: `
    <main id="main-content" class="acceso-denegado">
      <mat-icon class="acceso-denegado__icono" aria-hidden="true">lock</mat-icon>
      <h1 class="acceso-denegado__titulo">Acceso denegado</h1>
      <p class="acceso-denegado__texto">
        No tienes permiso para ver esta seccion. Si crees que es un error,
        contacta al administrador de tu empresa.
      </p>
      <button matButton="filled" type="button" (click)="irAInicio()">
        <mat-icon aria-hidden="true">home</mat-icon>
        Ir al inicio
      </button>
    </main>
  `,
  styles: [
    `
      .acceso-denegado {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: var(--ds-space-3);
        min-height: 100dvh;
        padding: var(--ds-space-5);
        text-align: center;
      }
      .acceso-denegado__icono {
        font-size: 3rem;
        width: 3rem;
        height: 3rem;
        color: var(--ds-color-error);
      }
      .acceso-denegado__titulo {
        margin: 0;
      }
      .acceso-denegado__texto {
        margin: 0;
        max-width: 46ch;
        color: var(--ds-color-text-muted);
      }
    `,
  ],
})
export class AccesoDenegado {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Ruta de inicio del ambito del Usuario. */
  private readonly inicio = computed(() => {
    switch (this.auth.ambito()) {
      case 'plataforma':
        return '/plataforma';
      case 'portal':
        return '/portal';
      default:
        return '/empresa';
    }
  });

  protected irAInicio(): void {
    void this.router.navigateByUrl(this.auth.isAuthenticated() ? this.inicio() : '/login');
  }
}
