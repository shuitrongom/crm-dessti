// =============================================================================
// Portal del Cliente: Mi perfil (Req 45.1) — solo lectura
// -----------------------------------------------------------------------------
// Muestra los datos del Cliente autenticado (identificación, contacto y dirección).
// El backend acota al Cliente del usuario del portal; la UI nunca envía clienteId.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { PortalService } from '../services/portal.service';
import { PerfilCliente } from '../models/portal.models';

@Component({
  selector: 'app-portal-perfil',
  imports: [MatCardModule, PageHeader, StateContainer],
  templateUrl: './portal-perfil.html',
})
export class PortalPerfil {
  private readonly service = inject(PortalService);

  protected readonly estado = signal<EstadoSolicitud<PerfilCliente>>(cargando());

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.miPerfil().subscribe({
      next: (perfil) => this.estado.set(conDatos(perfil)),
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }
}
