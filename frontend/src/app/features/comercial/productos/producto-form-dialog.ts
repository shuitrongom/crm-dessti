// =============================================================================
// Dialogo de alta/edicion de Producto (Comercial) — modal animado
// -----------------------------------------------------------------------------
// Reemplaza el formulario inline de la vista de Productos por un MODAL (MatDialog)
// consistente con el resto de la plataforma (mismo "form shell" ds-form* y el
// patron de Clientes). Sirve para ALTA (sin producto en los datos) y EDICION
// (con producto). Incluye la carga de foto con vista previa, los datos generales
// y comerciales, y —solo en edicion— el acceso a "Precios por lista". Al guardar
// hace POST/PUT via ProductosService y cierra devolviendo el Producto resultante.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { ProductosService } from '../services/catalogo.service';
import { Producto, ProductoRequest } from '../models/comercial.models';

/** Tamano maximo de la foto en bytes (1 MB, alineado con el limite del backend V61). */
const MAX_FOTO_BYTES = 1024 * 1024;

/** Tipos MIME de imagen admitidos para la foto del producto. */
const TIPOS_FOTO = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];

/**
 * Datos de entrada del dialogo. `producto` presente => modo EDICION (prellena y
 * hace PUT); ausente => modo ALTA (form vacio y POST).
 */
export interface ProductoFormDialogData {
  producto?: Producto;
}

@Component({
  selector: 'app-producto-form-dialog',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './producto-form-dialog.html',
  styleUrl: './producto-form-dialog.scss',
})
export class ProductoFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProductosService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);
  private readonly dialogRef = inject(MatDialogRef<ProductoFormDialog, Producto>);
  private readonly data = inject<ProductoFormDialogData>(MAT_DIALOG_DATA);

  /** Producto en edicion (o `null` en alta). */
  private readonly producto = this.data?.producto ?? null;
  /** `true` cuando el dialogo edita un Producto existente. */
  protected readonly esEdicion = !!this.producto;

  /** Habilita la seccion "Precios por lista" (solo edicion) segun permiso. */
  protected readonly puedeVerPrecios = this.auth.tienePermiso('lista_precios', 'listar');

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Data-URI o URL de la foto (vigente o recien seleccionada), o `null`. */
  protected readonly foto = signal<string | null>(this.producto?.foto ?? null);
  /** Mensaje de error de la carga de la foto (tipo/tamano invalido). */
  protected readonly fotoError = signal<string | null>(null);

  /** Titulo/subtitulo del dialogo segun el modo. */
  protected readonly titulo = computed(() => (this.esEdicion ? 'Editar producto' : 'Nuevo producto'));
  protected readonly subtitulo = computed(() =>
    this.esEdicion
      ? 'Actualiza los datos del producto o servicio de tu catálogo.'
      : 'Registra un producto o servicio en tu catálogo para cotizar y vender.',
  );

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    unidad: ['', [Validators.required, Validators.maxLength(50)]],
    descripcion: ['', [Validators.required, Validators.maxLength(2000)]],
    clienteMeta: [''],
    alianzas: [''],
    competencia: [''],
  });

  constructor() {
    if (this.producto) {
      this.form.reset({
        nombre: this.producto.nombre,
        unidad: this.producto.unidad,
        descripcion: this.producto.descripcion,
        clienteMeta: this.producto.clienteMeta ?? '',
        alianzas: this.producto.alianzas ?? '',
        competencia: this.producto.competencia ?? '',
      });
    }
  }

  /**
   * Carga la foto desde el input de archivo: valida tipo de imagen y tamano
   * (<= 1 MB) y la lee como data-URI (base64). Si falla, muestra un mensaje claro
   * y NO modifica la foto actual.
   */
  protected seleccionarFoto(evento: Event): void {
    this.fotoError.set(null);
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    if (!archivo) {
      return;
    }
    if (!(archivo.type.startsWith('image/') || TIPOS_FOTO.includes(archivo.type))) {
      this.fotoError.set('El archivo debe ser una imagen (PNG, JPG, WebP, etc.).');
      input.value = '';
      return;
    }
    if (archivo.size > MAX_FOTO_BYTES) {
      this.fotoError.set('La foto supera el tamaño máximo de 1 MB.');
      input.value = '';
      return;
    }
    const lector = new FileReader();
    lector.onload = () => this.foto.set(String(lector.result));
    lector.onerror = () => this.fotoError.set('No se pudo leer el archivo de la foto.');
    lector.readAsDataURL(archivo);
    input.value = '';
  }

  /** Quita la foto actual (se enviara `foto: null` al guardar). */
  protected quitarFoto(): void {
    this.foto.set(null);
    this.fotoError.set(null);
  }

  /** Persiste el alta o la edicion segun el modo (Req 59.1). */
  protected guardar(): void {
    this.error.set(null);
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
    const id = this.producto?.id;
    this.guardando.set(true);
    const peticion = id ? this.service.actualizar(id, request) : this.service.crear(request);
    this.overlay
      .ejecutar(peticion, {
        tipo: id ? 'guardar' : 'crear',
        textoProceso: id ? 'Guardando producto…' : 'Creando producto…',
        textoExito: id ? 'Producto guardado' : 'Producto creado',
      })
      .subscribe({
        next: (producto) => {
          this.guardando.set(false);
          this.dialogRef.close(producto);
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
  }

  /** Cancela sin guardar. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
