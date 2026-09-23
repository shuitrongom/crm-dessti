// =============================================================================
// Dialogo de alta de Empresa (super_admin) (Req 24.2, 11.3, 25.4)
// -----------------------------------------------------------------------------
// Formulario reactivo enterprise para dar de alta una Empresa con su primer
// admin_empresa. El formulario se organiza en secciones claramente etiquetadas
// (Identidad, Contacto, Direccion, Plan y modulos, Administrador inicial) con
// una rejilla responsive de 2 columnas (1 en movil) que evita el
// desbordamiento. La contrasena del admin es opcional: si se omite, el Sistema
// genera una temporal que se muestra UNA UNICA VEZ tras la creacion (Req 11.3).
// El Plan se elige de la lista existente; opcionalmente se habilita un
// SUBCONJUNTO de sus modulos (Req 25.4). El RFC se valida en el cliente con el
// mismo patron que el backend (UX; el backend sigue siendo la autoridad). El
// logo se carga como archivo de imagen (<= 256 KB) y se envia como data-URI.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatRadioModule } from '@angular/material/radio';

import { EmpresasService } from '../services/empresas.service';
import { PlanesService } from '../services/planes.service';
import { PaquetesSuscripcionService } from '../services/paquetes-suscripcion.service';
import { GirosService } from '../services/giros.service';
import {
  DependenciasModulos,
  ModuloCatalogo,
  Plan,
  PaqueteSuscripcion,
  Giro,
  EmpresaCreada,
} from '../models/plataforma.models';
import { GrupoModulos, agruparModulosPorGiro, humanizarGiro } from '../models/modulos-agrupados';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { rfcValidator } from '../../../shared/validators/rfc.validator';
import { telefonoValidator } from '../../../shared/validators/telefono.validator';
import {
  AddressAutocomplete,
  DireccionAutocompletada,
} from '../../../shared/components/address-autocomplete/address-autocomplete';

/** Tipos MIME de imagen admitidos para el logo del branding. */
const TIPOS_LOGO = ['image/png', 'image/jpeg', 'image/svg+xml', 'image/webp'];
/** Tamano maximo del archivo de logo en bytes (~256 KB). */
const MAX_LOGO_BYTES = 256 * 1024;

/**
 * Instrumento comercial excluyente elegido al alta (Req 4.1-4.4): un Plan de
 * largo plazo O un Paquete de Suscripcion de corto plazo, nunca ambos.
 */
type TipoInstrumento = 'plan' | 'suscripcion';

@Component({
  selector: 'app-crear-empresa-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatRadioModule,
    AddressAutocomplete,
  ],
  templateUrl: './crear-empresa-dialog.html',
  styleUrl: './crear-empresa-dialog.scss',
})
export class CrearEmpresaDialog {
  private readonly fb = inject(FormBuilder);
  private readonly empresasService = inject(EmpresasService);
  private readonly planesService = inject(PlanesService);
  private readonly paquetesService = inject(PaquetesSuscripcionService);
  private readonly girosService = inject(GirosService);
  private readonly dialogRef = inject(MatDialogRef<CrearEmpresaDialog, EmpresaCreada>);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly planes = signal<Plan[]>([]);
  /** Paquetes de Suscripcion disponibles para el alta (Req 4.2). */
  protected readonly paquetes = signal<PaqueteSuscripcion[]>([]);
  /** Giros ACTIVOS disponibles para el alta; solo estos pueden asignarse (Req 9). */
  protected readonly giros = signal<Giro[]>([]);
  /** `true` cuando ya se resolvio la carga de Giros y no hay ninguno activo. */
  protected readonly sinGirosActivos = signal(false);
  /** Resultado tras crear: si trae contrasena temporal, se muestra una unica vez (Req 11.3). */
  protected readonly creada = signal<EmpresaCreada | null>(null);

  /** Logo seleccionado como data-URI, o `null` si no se cargo ninguno. */
  protected readonly logo = signal<string | null>(null);
  /** Nombre del archivo de logo cargado (para la etiqueta accesible del preview). */
  protected readonly logoNombre = signal<string | null>(null);
  /** Mensaje de error de la carga del logo (tipo/tamano invalido). */
  protected readonly logoError = signal<string | null>(null);

  /** Etiquetas de modulo del catalogo (clave -> nombre visible) para las labels. */
  private readonly catalogoModulos = signal<ModuloCatalogo[]>([]);
  /** Instrumento comercial excluyente seleccionado (Plan por defecto) (Req 4.1-4.4). */
  protected readonly tipoInstrumentoSeleccionado = signal<TipoInstrumento>('plan');
  /** Plan seleccionado actualmente. */
  private readonly planSeleccionadoId = signal<string>('');
  /** Paquete de Suscripcion seleccionado actualmente. */
  protected readonly paqueteSeleccionadoId = signal<string>('');
  /** Giro seleccionado actualmente (para la nota informativa de giro "Base"). */
  private readonly giroIdSeleccionado = signal<string>('');
  /** Modulos del instrumento seleccionado deseleccionados por el admin (subconjunto). */
  protected readonly seleccion = signal<Set<string>>(new Set());
  /**
   * Mapa de dependencias entre modulos (GET /plataforma/dependencias-modulos):
   * `{ claveDependiente: [clavesRequeridas] }`. Fuente unica del aviso y del
   * bloqueo de deseleccion del override (Req 9.4, sin hardcode). Si su carga
   * falla, queda vacio (falla suave): no se activa aviso/bloqueo y el guardado
   * sigue disponible (la normalizacion del backend es la red de seguridad).
   */
  protected readonly dependencias = signal<DependenciasModulos>({});

  protected readonly formulario = this.fb.nonNullable.group({
    // Identidad
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    nombreComercial: ['', [Validators.maxLength(200)]],
    rfc: ['', [Validators.required, rfcValidator(), Validators.maxLength(13)]],
    giroId: ['', [Validators.required]],
    // Contacto. El correo es OBLIGATORIO: el backend lo exige (@NotBlank @Email)
    // porque se usa para el envio de la factura por correo (plataforma-multigiro).
    emailContacto: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    telefono: ['', [telefonoValidator()]],
    sitioWeb: ['', [Validators.maxLength(255)]],
    // Direccion
    direccionCalle: ['', [Validators.maxLength(255)]],
    direccionCiudad: ['', [Validators.maxLength(120)]],
    direccionEstado: ['', [Validators.maxLength(120)]],
    direccionCp: ['', [Validators.maxLength(10)]],
    direccionPais: ['', [Validators.maxLength(120)]],
    notas: ['', [Validators.maxLength(2000)]],
    // Contratacion (instrumento comercial excluyente). Por defecto es un Plan,
    // por lo que planId arranca como requerido y paqueteSuscripcionId sin validar
    // (cambiarTipoInstrumento alterna los validadores segun la eleccion).
    tipoInstrumento: ['plan' as TipoInstrumento, [Validators.required]],
    planId: ['', [Validators.required]],
    paqueteSuscripcionId: [''],
    otorgarPrueba: [false],
    // Administrador
    adminIdentificador: ['', [Validators.required, Validators.maxLength(255)]],
    adminPassword: ['', [Validators.minLength(8), Validators.maxLength(255)]],
  });

  /** Paquete de Suscripcion seleccionado actualmente (o `undefined`). */
  protected readonly paqueteSeleccionado = computed<PaqueteSuscripcion | undefined>(() =>
    this.paquetes().find((p) => p.id === this.paqueteSeleccionadoId()),
  );

  /** `true` cuando el Paquete seleccionado admite periodo de prueba (Req 4.5). */
  protected readonly paqueteAdmitePrueba = computed<boolean>(
    () => this.paqueteSeleccionado()?.admitePrueba === true,
  );

  /**
   * `true` cuando debe mostrarse el checkbox de prueba: solo con un Paquete de
   * Suscripcion que admita prueba (Req 4.5).
   */
  protected readonly mostrarOtorgarPrueba = computed<boolean>(
    () =>
      this.tipoInstrumentoSeleccionado() === 'suscripcion' && this.paqueteAdmitePrueba(),
  );

  /**
   * Claves de modulo del instrumento seleccionado (Plan o Paquete): fuente del
   * selector de subconjunto de modulos.
   */
  private readonly modulosDelInstrumento = computed<string[]>(() => {
    if (this.tipoInstrumentoSeleccionado() === 'suscripcion') {
      return this.paqueteSeleccionado()?.modulosHabilitados ?? [];
    }
    const plan = this.planes().find((p) => p.id === this.planSeleccionadoId());
    return plan?.modulosHabilitados ?? [];
  });

  /** Grupos de checkboxes (por Giro) restringidos a los modulos del instrumento. */
  protected readonly grupos = computed<GrupoModulos[]>(() => {
    const claves = new Set(this.modulosDelInstrumento());
    const delInstrumento = this.catalogoModulos().filter((m) => claves.has(m.clave));
    return agruparModulosPorGiro(delInstrumento);
  });

  /** Hay modulos que elegir para el instrumento seleccionado. */
  protected readonly hayModulos = computed(() => this.modulosDelInstrumento().length > 0);

  /**
   * Claves de modulo que NO pueden desmarcarse porque algun modulo seleccionado
   * las requiere (union de `dependencias[clave]` para toda clave seleccionada),
   * restringido a los modulos disponibles del instrumento (los unicos que se
   * pintan). Derivado del mapa de dependencias (Req 9.3, 9.4), sin lista fija.
   */
  protected readonly requeridosBloqueados = computed<Set<string>>(() => {
    const mapa = this.dependencias();
    const disponibles = new Set(this.modulosDelInstrumento());
    const bloqueados = new Set<string>();
    for (const clave of this.seleccion()) {
      for (const requerido of mapa[clave] ?? []) {
        if (disponibles.has(requerido)) {
          bloqueados.add(requerido);
        }
      }
    }
    return bloqueados;
  });

  /**
   * Aviso es-MX de dependencia cuando hay modulos seleccionados con requeridos:
   * "<Dependiente> depende de <Requeridos>. Se activara(n) tambien <Requeridos>."
   * El texto se deriva del mapa y de las etiquetas del catalogo (sin hardcode);
   * `null` cuando no aplica ninguna dependencia. (Req 9.1, 9.4)
   */
  protected readonly avisoDependencia = computed<string | null>(() => {
    const mapa = this.dependencias();
    const disponibles = new Set(this.modulosDelInstrumento());
    const frases: string[] = [];
    for (const clave of this.seleccion()) {
      const requeridos = (mapa[clave] ?? []).filter((r) => disponibles.has(r));
      if (requeridos.length === 0) {
        continue;
      }
      const etiqueta = this.etiquetaModulo(clave);
      const listaRequeridos = requeridos.map((r) => this.etiquetaModulo(r));
      const requeridosTexto = this.unir(listaRequeridos);
      const verbo = requeridos.length > 1 ? 'Se activaran tambien' : 'Se activara tambien';
      frases.push(`${etiqueta} depende de ${requeridosTexto}. ${verbo} ${requeridosTexto}.`);
    }
    return frases.length > 0 ? frases.join(' ') : null;
  });

  /** Giro actualmente seleccionado en el desplegable (o `undefined`). */
  private readonly giroSeleccionado = computed<Giro | undefined>(() =>
    this.giros().find((g) => g.id === this.giroIdSeleccionado()),
  );

  /**
   * `true` cuando el giro elegido es "Base" (sin reglas de negocio programadas):
   * en ese caso se muestra una nota informativa bajo el selector. No bloquea el
   * alta; solo informa al super_admin.
   */
  protected readonly giroBaseSeleccionado = computed<boolean>(() => {
    const giro = this.giroSeleccionado();
    return giro !== undefined && !giro.tieneReglasNegocio;
  });

  /**
   * Claves de Giro (distintas del Giro de la Empresa) cuyos modulos especificos
   * incluye el instrumento seleccionado. Se calcula cruzando los modulos del
   * instrumento con el catalogo (clave -> giro): un modulo con `giro` no nulo
   * distinto de la clave del Giro elegido para la Empresa indica una posible
   * incongruencia. El aviso es NO bloqueante (responsabilidad del super_admin).
   */
  protected readonly girosAjenosDelInstrumento = computed<string[]>(() => {
    const giroEmpresa = this.giroSeleccionado()?.clave ?? null;
    const claves = new Set(this.modulosDelInstrumento());
    const ajenos = new Set<string>();
    for (const modulo of this.catalogoModulos()) {
      if (
        claves.has(modulo.clave) &&
        modulo.giro !== null &&
        modulo.giro !== giroEmpresa
      ) {
        ajenos.add(modulo.giro);
      }
    }
    return Array.from(ajenos).map((clave) => this.etiquetaGiro(clave));
  });

  /** `true` cuando el instrumento incluye modulos especificos de otro Giro. */
  protected readonly hayGiroAjenoEnInstrumento = computed<boolean>(
    () => this.girosAjenosDelInstrumento().length > 0,
  );

  /** Etiqueta humana de una clave de Giro (nombre visible si esta cargado). */
  private etiquetaGiro(clave: string): string {
    const giro = this.giros().find((g) => g.clave === clave);
    return giro?.nombreVisible ?? humanizarGiro(clave);
  }

  constructor() {
    this.planesService.listarPlanes(0, 100).subscribe({
      next: (pagina) => this.planes.set(pagina.content),
      error: (e: HttpErrorResponse) => this.error.set(mensajeDeError(e)),
    });
    // Paquetes de Suscripcion disponibles como instrumento alternativo (Req 4.2).
    this.paquetesService.listarPaquetes(0, 100).subscribe({
      next: (pagina) => this.paquetes.set(pagina.content),
      error: (e: HttpErrorResponse) => this.error.set(mensajeDeError(e)),
    });
    // Solo los Giros ACTIVOS pueden asignarse a una nueva Empresa (Req 9); si no
    // hay ninguno, el alta se bloquea con un aviso (evita el 422 del backend).
    this.girosService.listar(true, 0, 100).subscribe({
      next: (pagina) => {
        this.giros.set(pagina.content);
        this.sinGirosActivos.set(pagina.content.length === 0);
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeDeError(e)),
    });
    // El catalogo solo aporta etiquetas/agrupacion; su ausencia no bloquea el alta.
    this.planesService.listarModulos().subscribe({
      next: (modulos) => this.catalogoModulos.set(modulos),
      error: () => this.catalogoModulos.set([]),
    });
    // Mapa de dependencias entre modulos (falla suave: si no carga, queda vacio
    // y el aviso/bloqueo no se activan; el guardado sigue disponible).
    this.planesService.listarDependenciasModulos().subscribe({
      next: (mapa) => this.dependencias.set(mapa),
      error: () => this.dependencias.set({}),
    });
  }

  /**
   * Reacciona al cambio de instrumento comercial excluyente (Req 4.1-4.4).
   * Alterna la obligatoriedad de `planId` / `paqueteSuscripcionId` de forma que
   * quede exactamente uno requerido, limpia el instrumento no elegido y su
   * seleccion de modulos, y fuerza `otorgarPrueba` a `false` fuera del caso de
   * un Paquete que admite prueba.
   */
  protected cambiarTipoInstrumento(tipo: TipoInstrumento): void {
    this.tipoInstrumentoSeleccionado.set(tipo);
    const planId = this.formulario.controls.planId;
    const paqueteId = this.formulario.controls.paqueteSuscripcionId;
    if (tipo === 'plan') {
      paqueteId.reset('');
      paqueteId.clearValidators();
      this.paqueteSeleccionadoId.set('');
      this.formulario.controls.otorgarPrueba.setValue(false);
      planId.setValidators([Validators.required]);
    } else {
      planId.reset('');
      planId.clearValidators();
      this.planSeleccionadoId.set('');
      paqueteId.setValidators([Validators.required]);
    }
    planId.updateValueAndValidity();
    paqueteId.updateValueAndValidity();
    // Al cambiar de instrumento no hay modulos elegibles hasta seleccionar uno.
    this.seleccion.set(new Set(this.modulosDelInstrumento()));
  }

  /** Reacciona al cambio de Plan: por defecto se habilitan TODOS sus modulos. */
  protected cambiarPlan(planId: string): void {
    this.planSeleccionadoId.set(planId);
    this.seleccion.set(new Set(this.modulosDelInstrumento()));
  }

  /**
   * Reacciona al cambio de Paquete de Suscripcion: habilita por defecto TODOS
   * sus modulos y, si el nuevo Paquete no admite prueba, fuerza `otorgarPrueba`
   * a `false` (Req 4.5).
   */
  protected cambiarPaquete(paqueteId: string): void {
    this.paqueteSeleccionadoId.set(paqueteId);
    if (!this.paqueteAdmitePrueba()) {
      this.formulario.controls.otorgarPrueba.setValue(false);
    }
    this.seleccion.set(new Set(this.modulosDelInstrumento()));
  }

  /** Reacciona al cambio de Giro para actualizar la nota de giro "Base". */
  protected cambiarGiro(giroId: string): void {
    this.giroIdSeleccionado.set(giroId);
  }

  /**
   * Restringe el campo de telefono a digitos: elimina cualquier caracter no
   * numerico y limita a 10 digitos mientras el Usuario escribe (UX; el patron
   * de 10 digitos lo exige `telefonoValidator`).
   */
  protected soloDigitos(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const limpio = input.value.replace(/\D/g, '').slice(0, 10);
    if (limpio !== input.value) {
      input.value = limpio;
    }
    this.formulario.controls.telefono.setValue(limpio);
  }

  /**
   * Normaliza el RFC mientras se escribe: mayusculas, solo [A-ZÑ&0-9] y tope de
   * 13 caracteres (persona fisica). Refuerza el `maxlength` y el patron del RFC.
   */
  protected normalizarRfc(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const limpio = input.value
      .toUpperCase()
      .replace(/[^A-ZÑ&0-9]/g, '')
      .slice(0, 13);
    if (limpio !== input.value) {
      input.value = limpio;
    }
    this.formulario.controls.rfc.setValue(limpio);
  }

  /**
   * Vuelca en el formulario la direccion elegida en el autocompletado (Photon).
   * Solo sobrescribe los campos que llegan con valor; los campos siguen siendo
   * editables tras el autollenado.
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
    this.formulario.patchValue(parche);
  }

  /** Indica si una clave de modulo esta seleccionada. */
  protected estaSeleccionado(clave: string): boolean {
    return this.seleccion().has(clave);
  }

  /**
   * Alterna la seleccion de un modulo del subconjunto (override). Al marcar,
   * por dependencia marca tambien los modulos requeridos disponibles en el
   * instrumento (Req 9.1, 9.2). RECHAZA desmarcar una clave requerida por algun
   * modulo aun seleccionado (bloqueo de deseleccion, Req 9.3). Todo derivado del
   * mapa de dependencias (Req 9.4), sin listas hardcodeadas.
   */
  protected alternar(clave: string, seleccionado: boolean): void {
    // Bloqueo de deseleccion: no permite desmarcar un modulo requerido por otro.
    if (!seleccionado && this.requeridosBloqueados().has(clave)) {
      return;
    }
    const actual = new Set(this.seleccion());
    if (seleccionado) {
      actual.add(clave);
      // Marca en cascada los modulos requeridos que ofrezca el instrumento.
      const disponibles = new Set(this.modulosDelInstrumento());
      for (const requerido of this.dependencias()[clave] ?? []) {
        if (disponibles.has(requerido)) {
          actual.add(requerido);
        }
      }
    } else {
      actual.delete(clave);
    }
    this.seleccion.set(actual);
  }

  /** Indica si una clave esta bloqueada (requerida por otro modulo seleccionado). */
  protected estaBloqueado(clave: string): boolean {
    return this.requeridosBloqueados().has(clave);
  }

  /**
   * Motivo es-MX del bloqueo de una clave requerida: nombra los modulos
   * dependientes seleccionados que la exigen (derivado del mapa, Req 9.3, 9.4).
   */
  protected motivoBloqueado(clave: string): string {
    const mapa = this.dependencias();
    const dependientes: string[] = [];
    for (const seleccionado of this.seleccion()) {
      if ((mapa[seleccionado] ?? []).includes(clave)) {
        dependientes.push(this.etiquetaModulo(seleccionado));
      }
    }
    const requerido = this.etiquetaModulo(clave);
    const dependienteTexto = this.unir(dependientes);
    return `No puedes desactivar ${requerido} mientras ${dependienteTexto} este activo, porque depende de el.`;
  }

  /** Etiqueta humana de una clave de modulo (nombre visible del catalogo o la clave). */
  private etiquetaModulo(clave: string): string {
    return this.catalogoModulos().find((m) => m.clave === clave)?.nombreVisible ?? clave;
  }

  /** Une una lista de etiquetas en es-MX ("A", "A y B", "A, B y C"). */
  private unir(etiquetas: readonly string[]): string {
    if (etiquetas.length === 0) {
      return '';
    }
    if (etiquetas.length === 1) {
      return etiquetas[0];
    }
    return `${etiquetas.slice(0, -1).join(', ')} y ${etiquetas[etiquetas.length - 1]}`;
  }

  /**
   * Carga el logo desde el input de archivo: valida tipo y tamano, y lo lee como
   * data-URI (base64). El limite de 256 KB deja margen holgado por debajo del
   * limite de 1 MiB de texto del backend tras codificar en base64.
   */
  protected seleccionarLogo(evento: Event): void {
    this.logoError.set(null);
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    if (!archivo) {
      return;
    }
    if (!TIPOS_LOGO.includes(archivo.type)) {
      this.logoError.set('Formato no admitido. Usa PNG, JPG, SVG o WebP.');
      input.value = '';
      return;
    }
    if (archivo.size > MAX_LOGO_BYTES) {
      this.logoError.set('El logo supera el tamano maximo de 256 KB.');
      input.value = '';
      return;
    }
    const lector = new FileReader();
    lector.onload = () => {
      this.logo.set(String(lector.result));
      this.logoNombre.set(archivo.name);
    };
    lector.onerror = () => this.logoError.set('No se pudo leer el archivo del logo.');
    lector.readAsDataURL(archivo);
    // Permite volver a elegir el mismo archivo tras quitarlo.
    input.value = '';
  }

  /** Quita el logo cargado. */
  protected quitarLogo(): void {
    this.logo.set(null);
    this.logoNombre.set(null);
    this.logoError.set(null);
  }

  /**
   * Resuelve el valor de `modulosHabilitados` a enviar: `null` cuando el admin
   * deja habilitados TODOS los modulos del instrumento (comportamiento por
   * defecto), o el subconjunto elegido en cualquier otro caso.
   */
  private modulosHabilitadosSolicitados(): string[] | null {
    const todos = this.modulosDelInstrumento();
    const elegidos = this.seleccion();
    const esTodoElPlan = todos.length === elegidos.size && todos.every((c) => elegidos.has(c));
    return esTodoElPlan ? null : Array.from(elegidos);
  }

  /** Normaliza un campo de texto opcional: `undefined` cuando queda en blanco. */
  private opcional(valor: string): string | undefined {
    const limpio = valor.trim();
    return limpio ? limpio : undefined;
  }

  /** Envia el alta de la Empresa. */
  protected guardar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const v = this.formulario.getRawValue();
    const esPlan = v.tipoInstrumento === 'plan';
    this.empresasService
      .crear({
        nombre: v.nombre.trim(),
        giroId: v.giroId,
        rfc: v.rfc.trim().toUpperCase(),
        // Instrumento comercial EXCLUYENTE: exactamente uno queda con valor y el
        // otro en null (Req 4.1-4.4). El backend valida el XOR (422).
        planId: esPlan ? v.planId : null,
        paqueteSuscripcionId: esPlan ? null : v.paqueteSuscripcionId,
        otorgarPrueba: !esPlan && this.paqueteAdmitePrueba() ? v.otorgarPrueba : false,
        adminIdentificador: v.adminIdentificador.trim(),
        adminPassword: v.adminPassword ? v.adminPassword : null,
        modulosHabilitados: this.modulosHabilitadosSolicitados(),
        nombreComercial: this.opcional(v.nombreComercial),
        emailContacto: this.opcional(v.emailContacto),
        telefono: this.opcional(v.telefono),
        sitioWeb: this.opcional(v.sitioWeb),
        direccionCalle: this.opcional(v.direccionCalle),
        direccionCiudad: this.opcional(v.direccionCiudad),
        direccionEstado: this.opcional(v.direccionEstado),
        direccionCp: this.opcional(v.direccionCp),
        direccionPais: this.opcional(v.direccionPais),
        notas: this.opcional(v.notas),
        logo: this.logo() ?? undefined,
      })
      .subscribe({
        next: (creada) => {
          this.guardando.set(false);
          if (creada.adminPasswordTemporal) {
            // Se muestra la contrasena temporal una unica vez (Req 11.3).
            this.creada.set(creada);
          } else {
            this.dialogRef.close(creada);
          }
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e));
        },
      });
  }

  /** Cierra el dialogo tras mostrar la contrasena temporal. */
  protected cerrarConExito(): void {
    this.dialogRef.close(this.creada() ?? undefined);
  }

  /** Cancela el alta. */
  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
