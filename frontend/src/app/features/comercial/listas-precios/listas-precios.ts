// =============================================================================
// Vista de Listas de precios (Req 59) — CRUD + asignacion de precios
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por nombre, alta/edicion (vigencia,
// prioridad, segmento), baja logica y asignacion del precio de un Producto en la
// lista. Acciones gobernadas por permiso lista_precios:{...}.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { Observable } from 'rxjs';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
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
import { PaginaResponse } from '../../../core/models/pagina-response';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ListasPreciosService, ProductosService } from '../services/catalogo.service';
import { ListaPrecios, PrecioLista, Producto } from '../models/comercial.models';

@Component({
  selector: 'app-comercial-listas-precios',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EntitySelect,
  ],
  templateUrl: './listas-precios.html',
  styleUrl: './listas-precios.scss',
})
export class ComercialListasPrecios {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ListasPreciosService);
  private readonly productos = inject(ProductosService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('lista_precios', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('lista_precios', 'actualizar');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly listas = signal<ListaPrecios[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtro = signal('');

  protected readonly guardando = signal(false);
  protected readonly editandoId = signal<string | null>(null);
  protected readonly formularioAbierto = signal(false);
  /** Lista sobre la que se esta asignando un precio (o null). */
  protected readonly asignandoPrecioA = signal<ListaPrecios | null>(null);
  /** Precios ya asignados en la lista abierta (para confirmar visualmente el guardado). */
  protected readonly preciosDeLista = signal<PrecioLista[]>([]);
  protected readonly cargandoPrecios = signal(false);
  protected readonly tituloFormulario = computed(() =>
    this.editandoId() ? 'Editar lista de precios' : 'Nueva lista de precios',
  );

  /**
   * Numero de Listas VIGENTES hoy en la pagina cargada: activas y cuya ventana de
   * vigencia (inicio..fin, fin abierto si es null) incluye la fecha actual. Las
   * fechas del DTO son ISO (YYYY-MM-DD), comparables lexicograficamente contra hoy.
   */
  protected readonly listasVigentes = computed<number>(() => {
    const hoy = new Date().toISOString().slice(0, 10);
    return this.listas().filter(
      (l) =>
        l.activo &&
        l.vigenciaInicio <= hoy &&
        (l.vigenciaFin == null || l.vigenciaFin >= hoy),
    ).length;
  });

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Nombre' },
    { clave: 'prioridad', encabezado: 'Prioridad', alineacion: 'centro' },
    { clave: 'segmento', encabezado: 'Segmento' },
    { clave: 'vigencia', encabezado: 'Vigencia' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    prioridad: [0, [Validators.required]],
    segmento: [''],
    vigenciaInicio: ['', [Validators.required]],
    vigenciaFin: [''],
  });

  protected readonly formPrecio = this.fb.nonNullable.group({
    productoId: ['', [Validators.required]],
    precio: [0, [Validators.required, Validators.min(0.01), Validators.max(999999999.99)]],
  });

  /** Busca Productos por nombre para el selector de asignacion (nunca UUID a mano). */
  protected readonly buscarProducto = (filtro: string): Observable<PaginaResponse<Producto>> =>
    this.productos.listar(filtro, 0, 20);

  /** Etiqueta principal de un Producto en el selector. */
  protected readonly etiquetaProducto = (producto: Producto): string => producto.nombre;

  /** Detalle secundario (unidad) de un Producto en el selector. */
  protected readonly detalleProducto = (producto: Producto): string | null =>
    producto.unidad || null;

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service.listar(this.filtro(), this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.listas.set(pagina.content);
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
    this.form.reset({ nombre: '', prioridad: 0, segmento: '', vigenciaInicio: '', vigenciaFin: '' });
    this.formularioAbierto.set(true);
    this.asignandoPrecioA.set(null);
  }

  editar(lista: ListaPrecios): void {
    this.editandoId.set(lista.id);
    this.form.reset({
      nombre: lista.nombre,
      prioridad: lista.prioridad,
      segmento: lista.segmento ?? '',
      vigenciaInicio: lista.vigenciaInicio,
      vigenciaFin: lista.vigenciaFin ?? '',
    });
    this.formularioAbierto.set(true);
    this.asignandoPrecioA.set(null);
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
    const request = {
      nombre: v.nombre.trim(),
      prioridad: Number(v.prioridad),
      segmento: v.segmento.trim() || null,
      vigenciaInicio: v.vigenciaInicio,
      vigenciaFin: v.vigenciaFin || null,
    };
    this.guardando.set(true);
    const id = this.editandoId();
    const peticion = id ? this.service.actualizar(id, request) : this.service.definir(request);
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito(id ? 'Lista actualizada.' : 'Lista creada.');
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

  async eliminar(lista: ListaPrecios): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja lista de precios',
      mensaje: `La lista "${lista.nombre}" quedara inactiva. Deseas continuar?`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.eliminar(lista.id).subscribe({
      next: () => {
        this.toast.exito('Lista dada de baja.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Abre el panel de asignacion de precio para una lista. */
  abrirAsignarPrecio(lista: ListaPrecios): void {
    this.asignandoPrecioA.set(lista);
    this.formPrecio.reset({ productoId: '', precio: 0 });
    this.formularioAbierto.set(false);
    this.cargarPreciosDeLista(lista.id);
  }

  /** Carga los precios ya asignados en la lista para mostrarlos bajo el formulario. */
  private cargarPreciosDeLista(listaId: string): void {
    this.cargandoPrecios.set(true);
    this.service.listarPrecios(listaId).subscribe({
      next: (precios) => {
        this.preciosDeLista.set(precios);
        this.cargandoPrecios.set(false);
      },
      error: () => {
        this.preciosDeLista.set([]);
        this.cargandoPrecios.set(false);
      },
    });
  }

  cerrarAsignarPrecio(): void {
    this.asignandoPrecioA.set(null);
  }

  /** Asigna el precio de un Producto en la lista seleccionada (Req 59.3, 59.10). */
  asignarPrecio(): void {
    const lista = this.asignandoPrecioA();
    if (!lista || this.formPrecio.invalid) {
      this.formPrecio.markAllAsTouched();
      return;
    }
    const v = this.formPrecio.getRawValue();
    this.guardando.set(true);
    this.service.asignarPrecio(lista.id, { productoId: v.productoId.trim(), precio: Number(v.precio) }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito('Precio asignado.');
        // Mantener el panel abierto y refrescar los precios de la lista para que
        // el Usuario vea el precio recien guardado reflejado (bugfix #8).
        this.formPrecio.reset({ productoId: '', precio: 0 });
        this.cargarPreciosDeLista(lista.id);
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
