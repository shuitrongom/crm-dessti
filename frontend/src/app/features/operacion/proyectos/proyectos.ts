// =============================================================================
// Vista de Proyectos (Req 21) — listado paginado + alta
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por cliente y alta de proyecto; enlaza
// al detalle con el avance consolidado por sitio. Acciones gobernadas por permiso
// proyecto:{...}.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ProyectosService } from '../services/proyectos.service';
import { NombresOperacionService } from '../services/nombres-operacion.service';
import { Proyecto } from '../models/operacion.models';

@Component({
  selector: 'app-operacion-proyectos',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './proyectos.html',
  styleUrl: './proyectos.scss',
})
export class OperacionProyectos {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProyectosService);
  protected readonly nombres = inject(NombresOperacionService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('proyecto', 'crear');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly proyectos = signal<Proyecto[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly clienteId = signal('');

  protected readonly guardando = signal(false);
  protected readonly formularioAbierto = signal(false);

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Proyecto' },
    { clave: 'clienteId', encabezado: 'Cliente' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly form = this.fb.nonNullable.group({
    clienteId: ['', [Validators.required]],
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
  });

  constructor() {
    // Carga los catalogos de nombres (Cliente) para resolver la columna Cliente
    // sin exponer UUIDs (Req 10.2); luego el listado.
    this.nombres.cargar().subscribe({
      next: () => this.cargar(),
      error: () => this.cargar(),
    });
  }

  /** Nombre legible del Cliente de un proyecto (nunca el UUID). */
  protected nombreCliente(clienteId: string | null | undefined): string {
    return this.nombres.nombreCliente(clienteId);
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service.listar(this.clienteId().trim() || null, this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.proyectos.set(pagina.content);
        this.total.set(pagina.totalElements);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  filtrarPorCliente(valor: string): void {
    this.clienteId.set(valor);
    this.page.set(0);
    this.cargar();
  }

  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  alternarFormulario(): void {
    this.formularioAbierto.update((v) => !v);
    if (this.formularioAbierto()) {
      this.form.reset({ clienteId: '', nombre: '' });
    }
  }

  /** Crea un Proyecto asociado a un Cliente (Req 21.1). */
  crear(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.service.crear({ clienteId: v.clienteId.trim(), nombre: v.nombre.trim() }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito('Proyecto creado.');
        this.formularioAbierto.set(false);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
