// =============================================================================
// Vista de Clientes (Req 5) — listado paginado + alta/edicion + baja
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por nombre/RFC, alta y edicion via
// formulario reactivo accesible y baja logica con confirmacion. El formulario
// se organiza en secciones enterprise (Identificacion, Contacto, Direccion,
// Notas) con una rejilla responsive y VALIDACION EN EL CLIENTE que espeja las
// reglas del backend (RFC mexicano, telefonos de 10 digitos, al menos un
// contacto), de modo que el Usuario recibe retroalimentacion inmediata en lugar
// del 422 generico. El backend sigue siendo la autoridad. Todas las acciones se
// gobiernan por permiso atomico (deny-by-default) y el listado gestiona sus
// estados con StateContainer.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  AddressAutocomplete,
  DireccionAutocompletada,
} from '../../../shared/components/address-autocomplete/address-autocomplete';
import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { rfcValidator } from '../../../shared/validators/rfc.validator';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ClientesService } from '../services/clientes.service';
import {
  Cliente,
  ClienteRequest,
  ETIQUETA_TIPO_PERSONA,
  TIPOS_PERSONA,
  TipoPersona,
} from '../models/comercial.models';

/** Longitud maxima del campo de notas (coincide con la cota del backend). */
const MAX_NOTAS = 1000;

/**
 * Validador de nivel formulario (Req 5): exige al menos un dato de contacto
 * (correo o telefono). Cuando ambos estan vacios devuelve `{ contacto: true }`;
 * espeja la regla de negocio del backend (422) para retroalimentacion inmediata.
 */
function alMenosUnContacto(control: AbstractControl): ValidationErrors | null {
  const email = String(control.get('email')?.value ?? '').trim();
  const telefono = String(control.get('telefono')?.value ?? '').trim();
  return email.length > 0 || telefono.length > 0 ? null : { contacto: true };
}

@Component({
  selector: 'app-comercial-clientes',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    AddressAutocomplete,
  ],
  templateUrl: './clientes.html',
  styleUrl: './clientes.scss',
})
export class ComercialClientes {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ClientesService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  // Permisos (deny-by-default) que gobiernan las acciones en la vista.
  protected readonly puedeCrear = this.auth.tienePermiso('cliente', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('cliente', 'actualizar');
  protected readonly puedeEliminar = this.auth.tienePermiso('cliente', 'eliminar');

  // Estado del listado.
  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly clientes = signal<Cliente[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtro = signal('');

  // Estado del formulario de alta/edicion/consulta.
  protected readonly guardando = signal(false);
  protected readonly editandoId = signal<string | null>(null);
  protected readonly formularioAbierto = signal(false);

  /** Longitud maxima del campo de notas (expuesta al contador de la plantilla). */
  protected readonly maxNotas = MAX_NOTAS;
  /** Opciones del selector de tipo de persona. */
  protected readonly tiposPersona = TIPOS_PERSONA;

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Nombre' },
    { clave: 'tipoPersona', encabezado: 'Tipo' },
    { clave: 'rfc', encabezado: 'RFC' },
    { clave: 'email', encabezado: 'Correo' },
    { clave: 'telefono', encabezado: 'Teléfono' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly tituloFormulario = computed(() =>
    this.editandoId() ? 'Editar cliente' : 'Nuevo cliente',
  );

  // ---------------------------------------------------------------------------
  // Indicadores (KPIs) — patron enterprise: outcome-first. Se calculan en el
  // cliente. El total usa el conteo global paginado (`total()`); los desgloses
  // (activos, morales/fisicas) se calculan sobre la pagina cargada.
  // ---------------------------------------------------------------------------

  /** Numero de Clientes activos en la pagina cargada. */
  protected readonly clientesActivos = computed<number>(
    () => this.clientes().filter((c) => c.activo).length,
  );

  /** Numero de personas morales en la pagina cargada. */
  protected readonly clientesMorales = computed<number>(
    () => this.clientes().filter((c) => c.tipoPersona === 'moral').length,
  );

  /** Numero de personas fisicas en la pagina cargada. */
  protected readonly clientesFisicas = computed<number>(
    () => this.clientes().filter((c) => c.tipoPersona === 'fisica').length,
  );

  // Formulario reactivo con validacion en el cliente. Los telefonos exigen
  // EXACTAMENTE 10 digitos (regla de UX; 10 esta dentro del rango 10..15 que
  // acepta el backend). El RFC reutiliza el validador compartido y se limita a
  // 13 caracteres. Al menos un contacto se valida a nivel de formulario.
  protected readonly form = this.fb.nonNullable.group(
    {
      nombre: ['', [Validators.required, Validators.maxLength(200)]],
      nombreComercial: ['', [Validators.maxLength(200)]],
      tipoPersona: ['' as TipoPersona | '', []],
      rfc: ['', [Validators.required, Validators.maxLength(13), rfcValidator()]],
      email: ['', [Validators.maxLength(254), Validators.email]],
      telefono: ['', [Validators.pattern(/^\d{10}$/)]],
      telefonoAdicional: ['', [Validators.pattern(/^\d{10}$/)]],
      direccionCalle: ['', [Validators.maxLength(200)]],
      direccionCiudad: ['', [Validators.maxLength(120)]],
      direccionEstado: ['', [Validators.maxLength(120)]],
      direccionCp: ['', [Validators.maxLength(10), Validators.pattern(/^\d*$/)]],
      direccionPais: ['', [Validators.maxLength(80)]],
      notas: ['', [Validators.maxLength(MAX_NOTAS)]],
    },
    { validators: alMenosUnContacto },
  );

  /** Longitud actual de las notas para el contador accesible de la plantilla. */
  protected readonly longitudNotas = computed(() => this.notasValor().length);
  private readonly notasValor = signal('');

  constructor() {
    this.form.controls.notas.valueChanges.subscribe((v) => this.notasValor.set(v ?? ''));
    this.cargar();
  }

  /** Carga la pagina actual de Clientes desde la API. */
  cargar(): void {
    this.fase.set('cargando');
    this.service.listar(this.filtro(), this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.clientes.set(pagina.content);
        this.total.set(pagina.totalElements);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Aplica el filtro de busqueda reiniciando a la primera pagina. */
  aplicarFiltro(valor: string): void {
    this.filtro.set(valor);
    this.page.set(0);
    this.cargar();
  }

  /** Cambia de pagina o tamano y recarga (contrato PaginaResponse). */
  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /**
   * Vuelca en el formulario la direccion elegida en el autocompletado (Photon).
   * Solo sobrescribe los campos que llegan con valor, para no borrar datos
   * capturados manualmente; los campos siguen siendo editables tras el autollenado.
   */
  protected onDireccion(direccion: DireccionAutocompletada): void {
    const parche: Partial<{
      direccionCalle: string;
      direccionCiudad: string;
      direccionEstado: string;
      direccionCp: string;
      direccionPais: string;
    }> = {};
    if (direccion.calle) parche.direccionCalle = direccion.calle;
    if (direccion.ciudad) parche.direccionCiudad = direccion.ciudad;
    if (direccion.estado) parche.direccionEstado = direccion.estado;
    if (direccion.cp) parche.direccionCp = direccion.cp;
    if (direccion.pais) parche.direccionPais = direccion.pais;
    this.form.patchValue(parche);
  }

  /** Etiqueta legible del tipo de persona para el listado. */
  protected etiquetaTipoPersona(tipo: TipoPersona | null): string {
    return tipo ? ETIQUETA_TIPO_PERSONA[tipo] : '—';
  }

  /** Abre el formulario en modo alta. */
  nuevo(): void {
    this.editandoId.set(null);
    this.form.enable();
    this.form.reset(this.valoresVacios());
    this.formularioAbierto.set(true);
  }

  /** Abre el formulario en modo edicion con los datos del Cliente. */
  editar(cliente: Cliente): void {
    this.editandoId.set(cliente.id);
    this.form.enable();
    this.form.reset(this.valoresDe(cliente));
    this.formularioAbierto.set(true);
  }

  /**
   * Abre la Ficha 360 del Cliente (datos + actividad comercial conectada, Req 1).
   * Navega por el id de ruta; el Usuario nunca teclea el identificador.
   */
  ver(cliente: Cliente): void {
    this.router.navigate(['/empresa/comercial/clientes', cliente.id]);
  }

  /** Cierra el formulario sin guardar y restablece su estado editable. */
  cancelar(): void {
    this.formularioAbierto.set(false);
    this.editandoId.set(null);
    this.form.enable();
  }

  /** Persiste el alta o la edicion segun el modo actual (Req 5.1, 5.4). */
  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const request = this.construirRequest();
    this.guardando.set(true);
    const id = this.editandoId();
    const peticion = id ? this.service.actualizar(id, request) : this.service.crear(request);
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito(id ? 'Cliente actualizado.' : 'Cliente creado.');
        this.cerrarFormulario();
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.manejarError(e);
      },
    });
  }

  /** Da de baja logica un Cliente con confirmacion (Req 5.9, 54). */
  async eliminar(cliente: Cliente): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja cliente',
      mensaje: `El cliente "${cliente.nombre}" quedara inactivo y se conservara su historico. Deseas continuar?`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.eliminar(cliente.id).subscribe({
      next: () => {
        this.toast.exito('Cliente dado de baja.');
        // La fila desaparece del listado: cualquier formulario abierto (consulta
        // o edicion) queda obsoleto, asi que lo cerramos y restablecemos su estado.
        this.cerrarFormulario();
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Cierra el formulario y restablece su estado (editable y vacio de modo). */
  private cerrarFormulario(): void {
    this.formularioAbierto.set(false);
    this.editandoId.set(null);
    this.form.enable();
  }

  /** Proyecta un Cliente a los valores del formulario (edicion y consulta). */
  private valoresDe(cliente: Cliente): ReturnType<ComercialClientes['form']['getRawValue']> {
    return {
      nombre: cliente.nombre,
      nombreComercial: cliente.nombreComercial ?? '',
      tipoPersona: cliente.tipoPersona ?? '',
      rfc: cliente.rfc,
      email: cliente.email ?? '',
      telefono: cliente.telefono ?? '',
      telefonoAdicional: cliente.telefonoAdicional ?? '',
      direccionCalle: cliente.direccionCalle ?? '',
      direccionCiudad: cliente.direccionCiudad ?? '',
      direccionEstado: cliente.direccionEstado ?? '',
      direccionCp: cliente.direccionCp ?? '',
      direccionPais: cliente.direccionPais ?? '',
      notas: cliente.notas ?? '',
    };
  }

  /** Valores por defecto (vacios) del formulario. */
  private valoresVacios(): ReturnType<ComercialClientes['form']['getRawValue']> {
    return {
      nombre: '',
      nombreComercial: '',
      tipoPersona: '',
      rfc: '',
      email: '',
      telefono: '',
      telefonoAdicional: '',
      direccionCalle: '',
      direccionCiudad: '',
      direccionEstado: '',
      direccionCp: '',
      direccionPais: '',
      notas: '',
    };
  }

  /** Normaliza un texto opcional: `null` cuando queda en blanco. */
  private opcional(valor: string): string | null {
    const limpio = valor.trim();
    return limpio.length > 0 ? limpio : null;
  }

  /** Construye el cuerpo de la peticion a partir del formulario. */
  private construirRequest(): ClienteRequest {
    const v = this.form.getRawValue();
    return {
      nombre: v.nombre.trim(),
      rfc: v.rfc.trim().toUpperCase(),
      email: this.opcional(v.email),
      telefono: this.opcional(v.telefono),
      nombreComercial: this.opcional(v.nombreComercial),
      tipoPersona: v.tipoPersona ? (v.tipoPersona as TipoPersona) : null,
      telefonoAdicional: this.opcional(v.telefonoAdicional),
      direccionCalle: this.opcional(v.direccionCalle),
      direccionCiudad: this.opcional(v.direccionCiudad),
      direccionEstado: this.opcional(v.direccionEstado),
      direccionCp: this.opcional(v.direccionCp),
      direccionPais: this.opcional(v.direccionPais),
      notas: this.opcional(v.notas),
    };
  }

  /**
   * Traduce el error de guardado a un mensaje amigable. El 409 de RFC duplicado
   * se coloca como error inline en el campo RFC; el resto se muestra como toast.
   */
  private manejarError(e: HttpErrorResponse): void {
    if (e.status === 409) {
      this.form.controls.rfc.setErrors({ duplicado: true });
      this.form.controls.rfc.markAsTouched();
      this.toast.error('Ya existe un cliente con ese RFC.');
      return;
    }
    this.toast.error(mensajeDeError(e));
  }
}
