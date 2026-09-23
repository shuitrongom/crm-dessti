// =============================================================================
// Vista de Permisos de Instalacion (Req 17) — listado + alta + aprobar/rechazar
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtros por tipo y estado, alta de permiso y
// decision (aprobar/rechazar) solo cuando esta solicitado. Acciones gobernadas
// por permiso permiso_instalacion:{...}.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  ChipEstado,
  VarianteChipEstado,
} from '../../../shared/components/chip-estado/chip-estado';
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

import { PermisosService } from '../services/instalacion.service';
import {
  ETIQUETA_ESTADO_PERMISO,
  ETIQUETA_TIPO_PERMISO,
  EstadoPermiso,
  PermisoInstalacion,
  TipoPermiso,
} from '../models/operacion.models';

@Component({
  selector: 'app-operacion-permisos',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    ChipEstado,
  ],
  templateUrl: './permisos.html',
  styleUrl: './permisos.scss',
})
export class OperacionPermisos {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PermisosService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('permiso_instalacion', 'crear');
  protected readonly puedeDecidir = this.auth.tienePermiso('permiso_instalacion', 'cambiar_estado');
  private readonly mapaTipo = ETIQUETA_TIPO_PERMISO;
  private readonly mapaEstado = ETIQUETA_ESTADO_PERMISO;
  protected readonly tipos: TipoPermiso[] = ['municipal', 'arrendador'];
  protected readonly estadosFiltro: EstadoPermiso[] = ['solicitado', 'aprobado', 'rechazado'];

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly permisos = signal<PermisoInstalacion[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly tipoFiltro = signal<TipoPermiso | ''>('');
  protected readonly estadoFiltro = signal<EstadoPermiso | ''>('');

  protected readonly guardando = signal(false);
  protected readonly formularioAbierto = signal(false);

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'tipo', encabezado: 'Tipo' },
    { clave: 'fechaVencimiento', encabezado: 'Vencimiento' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly form = this.fb.nonNullable.group({
    tipo: ['municipal' as TipoPermiso, [Validators.required]],
    fechaVencimiento: ['', [Validators.required]],
    sitioId: ['', [Validators.required]],
  });

  constructor() {
    this.cargar();
  }

  /** Etiqueta legible del tipo de permiso; devuelve el valor crudo si no mapea. */
  protected etiquetaTipo(tipo: string): string {
    return this.mapaTipo[tipo as TipoPermiso] ?? tipo;
  }

  /** Etiqueta legible del estado de permiso; devuelve el valor crudo si no mapea. */
  protected etiquetaEstado(estado: string): string {
    return this.mapaEstado[estado as EstadoPermiso] ?? estado;
  }

  /** Variante semantica del chip de estado: solicitado→info, aprobado→exito, rechazado→error. */
  protected varianteEstado(estado: EstadoPermiso): VarianteChipEstado {
    const mapa: Record<EstadoPermiso, VarianteChipEstado> = {
      solicitado: 'info',
      aprobado: 'exito',
      rechazado: 'error',
    };
    return mapa[estado] ?? 'neutro';
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service
      .listar(this.tipoFiltro() || null, this.estadoFiltro() || null, this.page(), this.size())
      .subscribe({
        next: (pagina) => {
          this.permisos.set(pagina.content);
          this.total.set(pagina.totalElements);
          this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
        },
        error: (e: HttpErrorResponse) => {
          this.mensajeError.set(mensajeDeError(e));
          this.fase.set('error');
        },
      });
  }

  cambiarTipo(valor: TipoPermiso | ''): void {
    this.tipoFiltro.set(valor);
    this.page.set(0);
    this.cargar();
  }

  cambiarEstadoFiltro(valor: EstadoPermiso | ''): void {
    this.estadoFiltro.set(valor);
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
      this.form.reset({ tipo: 'municipal', fechaVencimiento: '', sitioId: '' });
    }
  }

  /** Crea un Permiso de Instalacion en estado solicitado (Req 17.1). */
  crear(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.service
      .crear({ tipo: v.tipo, fechaVencimiento: v.fechaVencimiento, sitioId: v.sitioId.trim() })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Permiso registrado.');
          this.formularioAbierto.set(false);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  /** Aprueba o rechaza un Permiso solicitado con confirmacion (Req 17.2). */
  async decidir(permiso: PermisoInstalacion, accion: 'aprobar' | 'rechazar'): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: accion === 'aprobar' ? 'Aprobar permiso' : 'Rechazar permiso',
      mensaje: `El permiso quedara ${accion === 'aprobar' ? 'aprobado' : 'rechazado'}. Deseas continuar?`,
      textoConfirmar: accion === 'aprobar' ? 'Aprobar' : 'Rechazar',
      destructiva: accion === 'rechazar',
    });
    if (!ok) {
      return;
    }
    this.service.cambiarEstado(permiso.id, accion).subscribe({
      next: () => {
        this.toast.exito(accion === 'aprobar' ? 'Permiso aprobado.' : 'Permiso rechazado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
