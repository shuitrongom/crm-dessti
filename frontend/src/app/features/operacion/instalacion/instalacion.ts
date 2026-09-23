// =============================================================================
// Vista de Ordenes de Trabajo de Instalacion / OTI (Req 19)
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtros por estado, por Cliente (por NOMBRE
// via app-entity-select; Req 11.2) y por Cuadrilla, programacion de una OTI a
// partir de una Orden de Fabricacion terminada (con Cuadrilla y fecha), registro
// de avance (pendientes/evidencias/resoluciones) y transiciones de estado
// validas segun la maquina de estados. Acciones gobernadas por permiso
// orden_trabajo_instalacion:{...}.
//
// NOTA SOBRE EL FILTRO DE CUADRILLA (Req 11.2): el backend NO expone un catalogo
// de Cuadrillas — `cuadrilla_id` es una referencia debil (sin tabla `cuadrilla`
// ni endpoint REST de listado; ver `NombresOperacionService.cuadrillas()`, que es
// una lista vacia por diseno). Por ello, y siguiendo el mismo patron que el resto
// del modulo (p. ej. el formulario de programacion, que captura la Cuadrilla por
// identificador), el filtro de Cuadrilla se ofrece por identificador con seleccion
// asistida en lugar de un selector por nombre. NO se inventa un endpoint. Cuando
// el backend publique el catalogo de Cuadrillas, este filtro migrara a
// app-entity-select igual que el de Cliente.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ChipEstado, VarianteChipEstado } from '../../../shared/components/chip-estado/chip-estado';
import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
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
import { PaginaResponse } from '../../../core/models/pagina-response';
import { Cliente } from '../../comercial/models/comercial.models';

import { OtisService } from '../services/instalacion.service';
import { NombresOperacionService } from '../services/nombres-operacion.service';
import {
  ETIQUETA_ESTADO_OTI,
  EstadoOti,
  OrdenTrabajoInstalacion,
  estadosDestinoOti,
} from '../models/operacion.models';

@Component({
  selector: 'app-operacion-instalacion',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatSelectModule,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    ChipEstado,
    EntitySelect,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './instalacion.html',
  styleUrl: './instalacion.scss',
})
export class OperacionInstalacion {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(OtisService);
  protected readonly nombres = inject(NombresOperacionService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('orden_trabajo_instalacion', 'crear');
  protected readonly puedeGestionar = this.auth.tienePermiso(
    'orden_trabajo_instalacion',
    'cambiar_estado',
  );
  private readonly mapaEstado = ETIQUETA_ESTADO_OTI;
  protected readonly estados: EstadoOti[] = ['programada', 'en_curso', 'completada', 'cancelada'];

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly otis = signal<OrdenTrabajoInstalacion[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly estado = signal<EstadoOti | ''>('');
  /** Cliente seleccionado en el filtro (id interno; nunca visible). */
  protected readonly clienteId = signal<string>('');
  /** Cuadrilla seleccionada en el filtro (identificador; sin catalogo backend). */
  protected readonly cuadrillaId = signal<string>('');

  protected readonly guardando = signal(false);
  protected readonly formularioAbierto = signal(false);
  /** OTI sobre la que se registra avance (o null). */
  protected readonly avanceDe = signal<OrdenTrabajoInstalacion | null>(null);

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'cliente', encabezado: 'Cliente' },
    { clave: 'fechaProgramada', encabezado: 'Fecha programada' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly form = this.fb.nonNullable.group({
    ordenFabricacionId: ['', [Validators.required]],
    sitioId: ['', [Validators.required]],
    cuadrillaId: ['', [Validators.required]],
    fechaProgramada: ['', [Validators.required]],
  });

  /**
   * Filtros del listado: Cliente por NOMBRE (app-entity-select expone el id
   * internamente) y Cuadrilla por identificador (sin catalogo backend; ver la
   * nota de cabecera).
   */
  protected readonly formFiltro = this.fb.nonNullable.group({
    clienteId: [''],
    cuadrillaId: [''],
  });

  protected readonly formAvance = this.fb.nonNullable.group({
    nuevosPendientes: [''],
    evidencias: [''],
  });

  /**
   * Busca Clientes por nombre sobre el catalogo ya cargado en memoria (sin red
   * por pulsacion), alimentando el app-entity-select del filtro (Req 11.2, 11.5).
   */
  protected readonly buscarCliente = (filtro: string): Observable<PaginaResponse<Cliente>> => {
    const termino = filtro.trim().toLowerCase();
    const lista = this.nombres.clientes();
    const content = termino
      ? lista.filter((c) => c.nombre.toLowerCase().includes(termino))
      : lista.slice(0, 20);
    return of({
      content: content.slice(0, 20),
      page: 0,
      size: 20,
      totalElements: content.length,
      totalPages: 1,
    });
  };

  protected readonly etiquetaCliente = (c: Cliente): string => c.nombre;

  constructor() {
    // Carga los catalogos de nombres (Cliente) para el selector por nombre y el
    // render de la columna Cliente sin exponer UUIDs; luego el listado.
    this.nombres.cargar().subscribe({
      next: () => this.cargar(),
      error: () => this.cargar(),
    });
    // Aplica el filtro por Cliente al elegir/limpiar una opcion del selector.
    this.formFiltro.controls.clienteId.valueChanges.subscribe((valor) => {
      this.cambiarCliente(valor ?? '');
    });
  }

  /** Etiqueta legible del estado de una OTI; devuelve el valor crudo si no mapea. */
  protected etiquetaEstado(estado: string): string {
    return this.mapaEstado[estado as EstadoOti] ?? estado;
  }

  /** Variante semantica del chip de estado para cada estado de la OTI (Req 7.5). */
  protected varianteEstado(estado: EstadoOti): VarianteChipEstado {
    switch (estado) {
      case 'programada':
        return 'info';
      case 'en_curso':
        return 'advertencia';
      case 'completada':
        return 'exito';
      case 'cancelada':
        return 'neutro';
      default:
        return 'neutro';
    }
  }

  /** Nombre legible del Cliente de una OTI (nunca el UUID). */
  protected nombreCliente(clienteId: string | null | undefined): string {
    return this.nombres.nombreCliente(clienteId);
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service
      .listar(
        {
          estado: this.estado() || null,
          clienteId: this.clienteId() || null,
          cuadrillaId: this.cuadrillaId() || null,
        },
        this.page(),
        this.size(),
      )
      .subscribe({
        next: (pagina) => {
          this.otis.set(pagina.content);
          this.total.set(pagina.totalElements);
          this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
        },
        error: (e: HttpErrorResponse) => {
          this.mensajeError.set(mensajeDeError(e));
          this.fase.set('error');
        },
      });
  }

  cambiarFiltro(valor: EstadoOti | ''): void {
    this.estado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  /** Aplica el filtro por Cliente derivado de la seleccion por nombre (Req 11.3). */
  cambiarCliente(clienteId: string): void {
    if (clienteId === this.clienteId()) {
      return;
    }
    this.clienteId.set(clienteId);
    this.page.set(0);
    this.cargar();
  }

  /** Aplica el filtro por Cuadrilla (por identificador; sin catalogo backend). */
  aplicarFiltroCuadrilla(): void {
    const valor = this.formFiltro.getRawValue().cuadrillaId.trim();
    if (valor === this.cuadrillaId()) {
      return;
    }
    this.cuadrillaId.set(valor);
    this.page.set(0);
    this.cargar();
  }

  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Transiciones de estado validas para una OTI dada. */
  transicionesDe(oti: OrdenTrabajoInstalacion): readonly EstadoOti[] {
    return estadosDestinoOti(oti.estado);
  }

  alternarFormulario(): void {
    this.formularioAbierto.update((v) => !v);
    this.avanceDe.set(null);
    if (this.formularioAbierto()) {
      this.form.reset({ ordenFabricacionId: '', sitioId: '', cuadrillaId: '', fechaProgramada: '' });
    }
  }

  /** Programa una OTI a partir de una Orden de Fabricacion terminada (Req 19.1). */
  programar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.service
      .programar({
        ordenFabricacionId: v.ordenFabricacionId.trim(),
        sitioId: v.sitioId.trim(),
        cuadrillaId: v.cuadrillaId.trim(),
        fechaProgramada: v.fechaProgramada,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Orden de trabajo programada.');
          this.formularioAbierto.set(false);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  /** Cambia el estado de una OTI con confirmacion (Req 19.5). */
  async cambiarEstado(oti: OrdenTrabajoInstalacion, estado: EstadoOti): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Cambiar estado de la OTI',
      mensaje: `La orden de trabajo pasara a "${ETIQUETA_ESTADO_OTI[estado]}". Deseas continuar?`,
      textoConfirmar: 'Cambiar estado',
      destructiva: estado === 'cancelada',
    });
    if (!ok) {
      return;
    }
    this.service.cambiarEstado(oti.id, estado).subscribe({
      next: () => {
        this.toast.exito('Estado actualizado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Abre/cierra el panel de registro de avance de una OTI. */
  abrirAvance(oti: OrdenTrabajoInstalacion): void {
    this.avanceDe.set(oti);
    this.formularioAbierto.set(false);
    this.formAvance.reset({ nuevosPendientes: '', evidencias: '' });
  }

  cerrarAvance(): void {
    this.avanceDe.set(null);
  }

  /** Registra el avance (pendientes/evidencias) de la OTI seleccionada (Req 19.4). */
  registrarAvance(): void {
    const oti = this.avanceDe();
    if (!oti) {
      return;
    }
    const v = this.formAvance.getRawValue();
    const nuevosPendientes = this.aLista(v.nuevosPendientes);
    const evidencias = this.aLista(v.evidencias);
    if (nuevosPendientes.length === 0 && evidencias.length === 0) {
      this.toast.info('Agrega al menos un pendiente o una evidencia.');
      return;
    }
    this.guardando.set(true);
    this.service.registrarAvance(oti.id, { nuevosPendientes, evidencias }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito('Avance registrado.');
        this.avanceDe.set(null);
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /** Convierte texto por lineas o comas en una lista sin vacios. */
  private aLista(texto: string): string[] {
    return texto
      .split(/[\n,]/)
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }
}
