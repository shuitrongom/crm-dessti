// =============================================================================
// Vista de Publicaciones sociales (Req 65.1-65.6, 65.10, 65.11)
// -----------------------------------------------------------------------------
// Listado paginado filtrable por canal y estado; alta de una Publicacion_Social
// (borrador) por una Cuenta_Canal_Social con fecha programada; programacion
// (borrador -> programada) y publicacion con reintentos (programada ->
// publicada|fallida). Cada accion se gobierna por permiso atomico.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';
import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { tonoDeEstado } from '../../finanzas-comun/tono-estado';

import { PublicacionesService } from '../services/publicaciones.service';
import { CuentasCanalService } from '../services/cuentas-canal.service';
import { CuentaCanalSocial, PublicacionSocial } from '../models/social.models';
import { ETIQUETA_CANAL, ETIQUETA_ESTADO_PUBLICACION, OPCIONES_CANAL } from '../social-etiquetas';

@Component({
  selector: 'app-publicaciones',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './publicaciones.html',
  styleUrl: './publicaciones.scss',
})
export class Publicaciones {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PublicacionesService);
  private readonly cuentasService = inject(CuentasCanalService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;

  protected readonly puedeCrear = this.auth.tienePermiso('publicacion_social', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('publicacion_social', 'cambiar_estado');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'contenido', encabezado: 'Publicacion' },
    { clave: 'canal', encabezado: 'Canal' },
    { clave: 'programada', encabezado: 'Programada' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  // Filtro por canal derivado del origen unico (los cinco canales) mas "todos".
  protected readonly canales = [
    { valor: '', etiqueta: 'Todos los canales' },
    ...OPCIONES_CANAL,
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'borrador', etiqueta: 'Borrador' },
    { valor: 'programada', etiqueta: 'Programada' },
    { valor: 'publicada', etiqueta: 'Publicada' },
    { valor: 'fallida', etiqueta: 'Fallida' },
  ];

  protected readonly estado = signal<EstadoSolicitud<PublicacionSocial[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroCanal = signal('');
  protected readonly filtroEstado = signal('');
  protected readonly cuentas = signal<CuentaCanalSocial[]>([]);
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  /** Solo hay alta posible si existe al menos una cuenta de canal conectada. */
  protected readonly hayCuentas = computed(() => this.cuentas().length > 0);

  protected readonly formAlta = this.fb.nonNullable.group({
    cuentaCanalSocialId: ['', [Validators.required]],
    contenido: ['', [Validators.required]],
    fechaProgramada: ['', [Validators.required]],
  });

  constructor() {
    this.cargar();
    this.cargarCuentas();
  }

  etiquetaCanal(valor: string): string {
    return ETIQUETA_CANAL[valor as keyof typeof ETIQUETA_CANAL] ?? valor;
  }

  etiquetaEstado(valor: string): string {
    return ETIQUETA_ESTADO_PUBLICACION[valor as keyof typeof ETIQUETA_ESTADO_PUBLICACION] ?? valor;
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listar({ canal: this.filtroCanal() || null, estado: this.filtroEstado() || null }, this.page(), this.size())
      .subscribe({
        next: (pagina) => {
          this.total.set(pagina.totalElements);
          this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
        },
        error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
      });
  }

  private cargarCuentas(): void {
    this.cuentasService.listar(null, 0, 100).subscribe({
      next: (pagina) => this.cuentas.set(pagina.content.filter((c) => c.activa)),
      error: () => this.cuentas.set([]),
    });
  }

  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  aplicarCanal(valor: string): void {
    this.filtroCanal.set(valor);
    this.page.set(0);
    this.cargar();
  }

  aplicarEstado(valor: string): void {
    this.filtroEstado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  alternarAlta(): void {
    this.mostrarAlta.update((v) => !v);
  }

  crear(): void {
    if (this.formAlta.invalid) {
      this.formAlta.markAllAsTouched();
      return;
    }
    const v = this.formAlta.getRawValue();
    this.guardando.set(true);
    this.service
      .crear({
        cuentaCanalSocialId: v.cuentaCanalSocialId,
        contenido: v.contenido,
        // Convierte el datetime-local a instante UTC ISO-8601.
        fechaProgramada: new Date(v.fechaProgramada).toISOString(),
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Publicacion creada en borrador.');
          this.formAlta.reset({ cuentaCanalSocialId: '', contenido: '', fechaProgramada: '' });
          this.mostrarAlta.set(false);
          this.page.set(0);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  async programar(p: PublicacionSocial): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Programar publicacion',
      mensaje: 'Se programara la publicacion para su fecha. Continuar?',
      textoConfirmar: 'Programar',
    });
    if (!ok) {
      return;
    }
    this.service.cambiarEstado(p.id, 'programada').subscribe({
      next: () => {
        this.toast.exito('Publicacion programada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  async publicar(p: PublicacionSocial): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Publicar ahora',
      mensaje: 'Se publicara en el canal con reintentos automaticos. Continuar?',
      textoConfirmar: 'Publicar',
    });
    if (!ok) {
      return;
    }
    this.service.publicar(p.id).subscribe({
      next: (resultado) => {
        if (resultado.estado === 'publicada') {
          this.toast.exito('Publicacion publicada.');
        } else {
          this.toast.error(resultado.motivoFallo ?? 'La publicacion no pudo completarse.');
        }
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
