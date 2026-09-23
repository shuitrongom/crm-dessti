// =============================================================================
// Landing del Portal del Cliente (cliente_portal) (Req 45)
// -----------------------------------------------------------------------------
// En esta fase de fundacion, el portal del cliente ofrece el shell (guarda +
// navegacion + layout) y una vista de bienvenida. Las vistas de negocio del
// portal (consulta de proyectos, aprobaciones, facturas, etc.) se implementan en
// el bloque 51.3 anadiendo rutas hijas en portal.routes.ts, sin reestructurar el
// shell.
// =============================================================================

import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-portal-home',
  imports: [MatCardModule, MatIconModule, PageHeader],
  template: `
    <app-page-header
      titulo="Portal del cliente"
      [subtitulo]="'Bienvenido, ' + (identificador() ?? 'cliente') + '.'"
    />
    <mat-card appearance="outlined">
      <mat-card-content>
        <p class="admin-sin-datos">
          <mat-icon aria-hidden="true">info</mat-icon>
          Desde aqui podras consultar la informacion de tus proyectos y solicitudes.
          Las secciones del portal se habilitaran proximamente.
        </p>
      </mat-card-content>
    </mat-card>
  `,
})
export class PortalHome {
  private readonly auth = inject(AuthService);
  protected readonly identificador = this.auth.identificador;
}
