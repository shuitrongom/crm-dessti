// =============================================================================
// Vista de Tableros analiticos personalizados (Req 48.3)
// -----------------------------------------------------------------------------
// CRUD de tableros personalizados: listado paginado, alta/edicion (nombre,
// descripcion y definicion de widgets area+metrica+orden) y eliminacion. La
// gestion requiere el permiso inteligencia_negocio:gestionar; la lectura,
// inteligencia_negocio:leer.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
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

import { InteligenciaNegocioService } from '../services/inteligencia-negocio.service';
import {
  GuardarTableroPersonalizadoRequest,
  TableroPersonalizado,
  WidgetRequest,
} from '../models/reportes.models';

@Component({
  selector: 'app-tableros-personalizados',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './tableros-personalizados.html',
  styleUrl: './tableros-personalizados.scss',
})
export class TablerosPersonalizados {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(InteligenciaNegocioService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeGestionar = this.auth.tienePermiso('inteligencia_negocio', 'gestionar');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Tablero' },
    { clave: 'widgets', encabezado: 'Widgets', alineacion: 'centro' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estado = signal<EstadoSolicitud<TableroPersonalizado[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly guardando = signal(false);
  protected readonly mostrarForm = signal(false);
  /** Id del tablero en edicion; null si es alta. */
  protected readonly editandoId = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    descripcion: ['', [Validators.maxLength(500)]],
    widgets: this.fb.array<ReturnType<TablerosPersonalizados['crearWidget']>>([]),
  });

  constructor() {
    this.cargar();
  }

  /** Acceso tipado al FormArray de widgets. */
  get widgets(): FormArray {
    return this.form.get('widgets') as FormArray;
  }

  private crearWidget(area = '', metrica = '', orden = 0) {
    return this.fb.nonNullable.group({
      area: [area, [Validators.required, Validators.maxLength(40)]],
      metrica: [metrica, [Validators.required, Validators.maxLength(80)]],
      orden: [orden, [Validators.required, Validators.min(0)]],
    });
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarTableros(this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.total.set(pagina.totalElements);
        this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  nuevoTablero(): void {
    this.editandoId.set(null);
    this.form.reset({ nombre: '', descripcion: '' });
    this.widgets.clear();
    this.agregarWidget();
    this.mostrarForm.set(true);
  }

  editar(t: TableroPersonalizado): void {
    this.editandoId.set(t.id);
    this.form.reset({ nombre: t.nombre, descripcion: t.descripcion ?? '' });
    this.widgets.clear();
    for (const w of t.widgets) {
      this.widgets.push(this.crearWidget(w.area, w.metrica, w.orden));
    }
    if (this.widgets.length === 0) {
      this.agregarWidget();
    }
    this.mostrarForm.set(true);
  }

  cancelar(): void {
    this.mostrarForm.set(false);
    this.editandoId.set(null);
  }

  agregarWidget(): void {
    this.widgets.push(this.crearWidget('', '', this.widgets.length));
  }

  quitarWidget(indice: number): void {
    this.widgets.removeAt(indice);
  }

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const bruto = this.form.getRawValue();
    const widgets: WidgetRequest[] = (bruto.widgets as WidgetRequest[]).map((w, i) => ({
      area: w.area,
      metrica: w.metrica,
      orden: w.orden ?? i,
    }));
    const request: GuardarTableroPersonalizadoRequest = {
      nombre: bruto.nombre,
      descripcion: bruto.descripcion || null,
      widgets,
    };
    this.guardando.set(true);
    const id = this.editandoId();
    const peticion = id
      ? this.service.actualizarTablero(id, request)
      : this.service.crearTablero(request);
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito(id ? 'Tablero actualizado.' : 'Tablero creado.');
        this.mostrarForm.set(false);
        this.editandoId.set(null);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  async eliminar(t: TableroPersonalizado): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Eliminar tablero',
      mensaje: `Se eliminara el tablero "${t.nombre}" y sus widgets. Continuar?`,
      textoConfirmar: 'Eliminar',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.eliminarTablero(t.id).subscribe({
      next: () => {
        this.toast.exito('Tablero eliminado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
