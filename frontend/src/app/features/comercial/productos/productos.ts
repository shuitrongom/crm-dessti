// =============================================================================
// Vista de Productos (Req 59, V61) — catalogo con alta/edicion/baja
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por nombre y miniatura de la foto del
// producto, y formulario reactivo de alta/edicion seccionado con carga de FOTO
// (archivo de imagen leido como data-URI, con vista previa y opcion de quitar);
// baja logica con confirmacion. Acciones gobernadas por permiso. La carga de
// foto reutiliza el patron de Branding: valida tipo (image/*) y tamano (<= 1 MB)
// en el cliente y guarda el data-URI en el control `foto`.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
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
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ProductosService } from '../services/catalogo.service';
import { Producto, ProductoRequest } from '../models/comercial.models';

/** Tamano maximo de la foto en bytes (1 MB, alineado con el limite del backend V61). */
const MAX_FOTO_BYTES = 1024 * 1024;

@Component({
  selector: 'app-comercial-productos',
  imports: [
    ReactiveFormsModule,
    RouterLink,
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
  templateUrl: './productos.html',
  styleUrl: './productos.scss',
})
export class ComercialProductos {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProductosService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('producto', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('producto', 'actualizar');
  protected readonly puedeEliminar = this.auth.tienePermiso('producto', 'eliminar');
  /**
   * Habilita la seccion "Precios por lista" que enlaza a Listas de precios, donde
   * se gestionan los precios por producto. El backend no expone (aun) un endpoint
   * para leer los precios de un producto en cada lista, por lo que se ofrece un
   * acceso directo honesto en vez de inventar datos (Req 4.1, 4.3).
   */
  protected readonly puedeVerPrecios = this.auth.tienePermiso('lista_precios', 'listar');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly productos = signal<Producto[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtro = signal('');

  protected readonly guardando = signal(false);
  protected readonly editandoId = signal<string | null>(null);
  protected readonly formularioAbierto = signal(false);
  protected readonly tituloFormulario = computed(() =>
    this.editandoId() ? 'Editar producto' : 'Nuevo producto',
  );

  /** Numero de Productos activos en la pagina cargada (indicador enterprise). */
  protected readonly productosActivos = computed<number>(
    () => this.productos().filter((p) => p.activo).length,
  );

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'foto', encabezado: 'Foto' },
    { clave: 'nombre', encabezado: 'Nombre' },
    { clave: 'unidad', encabezado: 'Unidad', ocultarEnMovil: true },
    { clave: 'descripcion', encabezado: 'Descripción', ocultarEnMovil: true },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  /** Data-URI o URL de la foto (vigente o recien seleccionada), o `null` si no hay. */
  protected readonly foto = signal<string | null>(null);
  /** Mensaje de error de la carga de la foto (tipo/tamano invalido). */
  protected readonly fotoError = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    unidad: ['', [Validators.required, Validators.maxLength(50)]],
    descripcion: ['', [Validators.required, Validators.maxLength(2000)]],
    clienteMeta: [''],
    alianzas: [''],
    competencia: [''],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service.listar(this.filtro(), this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.productos.set(pagina.content);
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
    this.form.reset({ nombre: '', unidad: '', descripcion: '', clienteMeta: '', alianzas: '', competencia: '' });
    this.foto.set(null);
    this.fotoError.set(null);
    this.formularioAbierto.set(true);
  }

  editar(producto: Producto): void {
    this.editandoId.set(producto.id);
    this.form.reset({
      nombre: producto.nombre,
      unidad: producto.unidad,
      descripcion: producto.descripcion,
      clienteMeta: producto.clienteMeta ?? '',
      alianzas: producto.alianzas ?? '',
      competencia: producto.competencia ?? '',
    });
    this.foto.set(producto.foto ?? null);
    this.fotoError.set(null);
    this.formularioAbierto.set(true);
  }

  cancelar(): void {
    this.formularioAbierto.set(false);
    this.editandoId.set(null);
    this.foto.set(null);
    this.fotoError.set(null);
  }

  /**
   * Carga la foto desde el input de archivo: valida que sea una imagen
   * (type empieza por `image/`) y que no supere 1 MB, y la lee como data-URI
   * (base64). Si la validacion falla, muestra un mensaje claro en espanol y NO
   * modifica la foto actual. Reutiliza el patron de la carga de logo de Branding.
   */
  seleccionarFoto(evento: Event): void {
    this.fotoError.set(null);
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    if (!archivo) {
      return;
    }
    if (!archivo.type.startsWith('image/')) {
      this.fotoError.set('El archivo debe ser una imagen (PNG, JPG, WebP, etc.).');
      input.value = '';
      return;
    }
    if (archivo.size > MAX_FOTO_BYTES) {
      this.fotoError.set('La foto supera el tamano maximo de 1 MB.');
      input.value = '';
      return;
    }
    const lector = new FileReader();
    lector.onload = () => this.foto.set(String(lector.result));
    lector.onerror = () => this.fotoError.set('No se pudo leer el archivo de la foto.');
    lector.readAsDataURL(archivo);
    // Permite volver a elegir el mismo archivo tras quitarlo.
    input.value = '';
  }

  /** Quita la foto actual (se enviara `foto: null` al guardar). */
  quitarFoto(): void {
    this.foto.set(null);
    this.fotoError.set(null);
  }

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: ProductoRequest = {
      nombre: v.nombre.trim(),
      unidad: v.unidad.trim(),
      descripcion: v.descripcion.trim(),
      clienteMeta: v.clienteMeta.trim() || null,
      alianzas: v.alianzas.trim() || null,
      competencia: v.competencia.trim() || null,
      foto: this.foto(),
    };
    this.guardando.set(true);
    const id = this.editandoId();
    const peticion = id ? this.service.actualizar(id, request) : this.service.crear(request);
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito(id ? 'Producto actualizado.' : 'Producto creado.');
        this.formularioAbierto.set(false);
        this.editandoId.set(null);
        this.foto.set(null);
        this.fotoError.set(null);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  async eliminar(producto: Producto): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja producto',
      mensaje: `El producto "${producto.nombre}" quedara inactivo. Deseas continuar?`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.eliminar(producto.id).subscribe({
      next: () => {
        this.toast.exito('Producto dado de baja.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
