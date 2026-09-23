// =============================================================================
// Vista de Canales de venta (Req 63) — CRUD del catalogo
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por nombre, alta/edicion y baja logica.
// Acciones gobernadas por permiso canal_venta:{...}.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
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
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { CanalesVentaService } from '../services/catalogo.service';
import { CanalVenta } from '../models/comercial.models';

@Component({
  selector: 'app-comercial-canales-venta',
  imports: [
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
  templateUrl: './canales-venta.html',
  styleUrl: './canales-venta.scss',
})
export class ComercialCanalesVenta {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CanalesVentaService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('canal_venta', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('canal_venta', 'actualizar');
  protected readonly puedeEliminar = this.auth.tienePermiso('canal_venta', 'eliminar');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly canales = signal<CanalVenta[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtro = signal('');

  protected readonly guardando = signal(false);
  protected readonly editandoId = signal<string | null>(null);
  protected readonly formularioAbierto = signal(false);
  protected readonly tituloFormulario = computed(() =>
    this.editandoId() ? 'Editar canal de venta' : 'Nuevo canal de venta',
  );

  /** Numero de Canales activos en la pagina cargada (indicador enterprise). */
  protected readonly canalesActivos = computed<number>(
    () => this.canales().filter((c) => c.activo).length,
  );

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Nombre' },
    { clave: 'descripcion', encabezado: 'Descripción' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    descripcion: ['', [Validators.maxLength(500)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service.listar(this.filtro(), this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.canales.set(pagina.content);
        this.total.set(pagina.totalElements);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  aplicarFiltro(valor: string): void {
    this.filtro.set(valor);
    this.page.set(0);
    this.cargar();
  }

  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  nuevo(): void {
    this.editandoId.set(null);
    this.form.reset({ nombre: '', descripcion: '' });
    this.formularioAbierto.set(true);
  }

  editar(canal: CanalVenta): void {
    this.editandoId.set(canal.id);
    this.form.reset({ nombre: canal.nombre, descripcion: canal.descripcion ?? '' });
    this.formularioAbierto.set(true);
  }

  cancelar(): void {
    this.formularioAbierto.set(false);
    this.editandoId.set(null);
  }

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request = { nombre: v.nombre.trim(), descripcion: v.descripcion.trim() || null };
    this.guardando.set(true);
    const id = this.editandoId();
    const peticion = id ? this.service.actualizar(id, request) : this.service.crear(request);
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito(id ? 'Canal actualizado.' : 'Canal creado.');
        this.formularioAbierto.set(false);
        this.editandoId.set(null);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  async eliminar(canal: CanalVenta): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja canal de venta',
      mensaje: `El canal "${canal.nombre}" quedara inactivo. Deseas continuar?`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.eliminar(canal.id).subscribe({
      next: () => {
        this.toast.exito('Canal dado de baja.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
