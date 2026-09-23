// =============================================================================
// Dialogo de alta/edicion de Plan (super_admin) (Req 25.1, plataforma-multigiro)
// -----------------------------------------------------------------------------
// Formulario reactivo enterprise para crear o actualizar un Plan segun el nuevo
// modelo multigiro: un Plan pertenece a un GIRO, fija una MONEDA y captura un
// PRECIO POR MODULO. El total del Plan es la suma de los precios de los modulos
// habilitados.
//
// Los modulos se organizan en dos grupos, siempre en funcion del Giro elegido:
//   - "Nucleo comun": modulos con giro null; SIEMPRE disponibles.
//   - Grupo del Giro seleccionado: SOLO los modulos especificos de ese Giro.
// Nunca se muestran modulos de otros Giros. Al cambiar de Giro se descartan las
// selecciones especificas del Giro anterior (se conserva el Nucleo) y se informa
// de forma discreta.
//
// Cada modulo expone una casilla (habilita/deshabilita el modulo en el Plan) y un
// campo de PRECIO (>= 0) que se habilita al marcar la casilla y se limpia al
// desmarcarla. Un total en vivo suma los precios de los modulos marcados y se
// formatea con la moneda elegida (locale es-MX). Al guardar se arma
// `preciosModulos` = { clave: precio } SOLO con los modulos marcados y se envia
// { nombre, maxUsuarios, giroId, monedaCodigo, preciosModulos }.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { GuardarPlanRequest, PlanesService } from '../services/planes.service';
import { GirosService } from '../services/giros.service';
import { DependenciasModulos, Giro, Moneda, ModuloCatalogo, Plan } from '../models/plataforma.models';
import { GrupoModulos, TITULO_NUCLEO, humanizarGiro } from '../models/modulos-agrupados';
import { mensajeDeError } from '../../../core/services/error-mensajes';

/** Datos de entrada del dialogo: el Plan a editar o `null` para crear. */
export interface DatosPlanDialog {
  plan: Plan | null;
}

/** Moneda por defecto cuando el catalogo la incluye. */
const MONEDA_POR_DEFECTO = 'MXN';

/**
 * Orden canonico de los bloques (modulos) tal como aparecen en el documento de
 * requisitos, para presentarlos SIEMPRE en ese orden (no alfabetico) y poder
 * abordarlos bloque a bloque. Las claves no listadas aqui se ordenan despues,
 * por su etiqueta. Nucleo y especificos comparten esta escala de orden.
 */
const ORDEN_BLOQUES: readonly string[] = [
  'estrategia',           // 1. Planeacion estrategica y objetivos
  'comercial',            // 2. Comercial y CRM (incluye catalogo de productos y listas de precios)
  'redes-sociales',       // 4. Redes sociales y mensajeria omnicanal
  'operacion',            // 5. Operacion y produccion (incluye proyectos multi-sitio)
  'inventario-avanzado',  // 6. Inventario avanzado
  'mantenimiento',        // 7. Mantenimiento y post-venta
  'compras',              // 9. Compras y proveedores
  'facturacion',          // 10. Facturacion electronica (CFDI 4.0)
  'contabilidad',         // 11. Contabilidad y finanzas
  'portal-cliente',       // 12. Portal de autoservicio del cliente
  'reportes-bi',          // 14. Reportes, tablero e inteligencia de negocio
  'calidad',              // 16. Cumplimiento y calidad (ISO 9001:2026)
  'presupuestos',         // 17. Presupuestos
  'tesoreria',            // 18. Tesoreria
  'activos-fijos',        // 19. Activos fijos
  'rh-nomina',            // 20. RH y nomina
];

/**
 * Ordena una lista de modulos por el orden canonico de {@link ORDEN_BLOQUES};
 * las claves fuera de la lista van al final, ordenadas por su etiqueta.
 */
function ordenarPorBloques(modulos: readonly ModuloCatalogo[]): ModuloCatalogo[] {
  const indice = (clave: string): number => {
    const i = ORDEN_BLOQUES.indexOf(clave);
    return i === -1 ? Number.MAX_SAFE_INTEGER : i;
  };
  return [...modulos].sort((a, b) => {
    const da = indice(a.clave);
    const db = indice(b.clave);
    if (da !== db) {
      return da - db;
    }
    return a.nombreVisible.localeCompare(b.nombreVisible, 'es');
  });
}

@Component({
  selector: 'app-plan-dialog',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './plan-dialog.html',
  styleUrl: './plan-dialog.scss',
})
export class PlanDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PlanesService);
  private readonly girosService = inject(GirosService);
  private readonly datos = inject<DatosPlanDialog>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<PlanDialog, Plan>);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly esEdicion = this.datos.plan !== null;

  /** Carga del catalogo/giros/monedas: deshabilita el guardado mientras carga. */
  protected readonly cargando = signal(true);
  /** Catalogo plano de modulos recibido del backend. */
  private readonly catalogo = signal<ModuloCatalogo[]>([]);
  /** Giros ACTIVOS que pueden asignarse al Plan (Req 9). */
  protected readonly giros = signal<Giro[]>([]);
  /** Monedas ACTIVAS del catalogo de la plataforma. */
  protected readonly monedas = signal<Moneda[]>([]);

  /** Precios por modulo capturados: `clave -> texto del input` (permite vacio). */
  private readonly precios = signal<Map<string, string>>(new Map());
  /** Conjunto de claves de modulo marcadas (habilitadas en el Plan). */
  protected readonly seleccion = signal<Set<string>>(new Set());
  /** `true` cuando se descartaron modulos especificos al cambiar de Giro. */
  protected readonly avisoGiroCambiado = signal(false);
  /**
   * Mapa de dependencias entre modulos (GET /plataforma/dependencias-modulos):
   * `{ claveDependiente: [clavesRequeridas] }`. Fuente unica del aviso y del
   * bloqueo de deseleccion (Req 9.4, sin hardcode). Si su carga falla, queda
   * vacio (falla suave): no se activa aviso/bloqueo y el guardado sigue disponible.
   */
  protected readonly dependencias = signal<DependenciasModulos>({});
  /**
   * Id del Giro seleccionado como signal (fuente reactiva del filtro de modulos).
   * Se mantiene en sincronia con el control `giroId` del formulario reactivo.
   */
  private readonly giroIdSeleccionado = signal<string>(this.datos.plan?.giroId ?? '');
  /**
   * Codigo de moneda seleccionado como signal (fuente reactiva de las etiquetas
   * de precio y del formato del total). Se mantiene en sincronia con el control
   * `monedaCodigo`; un `computed` sobre el control no reacciona a sus cambios.
   */
  private readonly monedaSeleccionada = signal<string>(this.datos.plan?.monedaCodigo ?? '');

  protected readonly formulario = this.fb.nonNullable.group({
    nombre: [this.datos.plan?.nombre ?? '', [Validators.required, Validators.maxLength(120)]],
    maxUsuarios: [this.datos.plan?.maxUsuarios ?? 0, [Validators.required, Validators.min(0)]],
    // Duracion del contrato en dias; debe ser > 365 (min 366) para un Plan.
    // El backend exige @Min(366); el valor por defecto (730) coincide con la migracion.
    duracionDias: [this.datos.plan?.duracionDias ?? 730, [Validators.required, Validators.min(366)]],
    giroId: [this.datos.plan?.giroId ?? '', [Validators.required]],
    monedaCodigo: [this.datos.plan?.monedaCodigo ?? '', [Validators.required]],
  });

  /** Codigo de la moneda seleccionada (para formatear importes); por defecto MXN. */
  protected readonly moneda = computed(
    () => this.monedaSeleccionada() || MONEDA_POR_DEFECTO,
  );

  /** Reacciona al cambio de moneda del select: sincroniza el signal reactivo. */
  protected cambiarMoneda(codigo: string): void {
    this.formulario.controls.monedaCodigo.setValue(codigo);
    this.monedaSeleccionada.set(codigo);
  }

  /** Clave del Giro seleccionado, o `null` si aun no se elige (para filtrar). */
  private readonly giroClaveSeleccionada = computed<string | null>(() => {
    const id = this.giroIdSeleccionado();
    return this.giros().find((g) => g.id === id)?.clave ?? null;
  });

  /** `true` cuando ya se eligio un Giro (habilita el grupo especifico). */
  protected readonly hayGiro = computed(() => this.giroClaveSeleccionada() !== null);

  /**
   * Grupos de modulos a mostrar: SIEMPRE el "Nucleo comun" (giro null) y, si hay
   * un Giro elegido, SOLO los modulos especificos de ese Giro. Nunca se muestran
   * modulos de otros Giros. El backend ya devuelve el catalogo ordenado; aqui
   * solo se filtra y agrupa preservando ese orden.
   */
  protected readonly grupos = computed<GrupoModulos[]>(() => {
    const catalogo = this.catalogo();
    const giroClave = this.giroClaveSeleccionada();
    const grupos: GrupoModulos[] = [];

    const nucleo = ordenarPorBloques(catalogo.filter((m) => m.giro === null));
    if (nucleo.length > 0) {
      grupos.push({ giro: null, titulo: TITULO_NUCLEO, modulos: nucleo });
    }
    if (giroClave !== null) {
      const especificos = ordenarPorBloques(catalogo.filter((m) => m.giro === giroClave));
      if (especificos.length > 0) {
        grupos.push({ giro: giroClave, titulo: this.etiquetaGiro(giroClave), modulos: especificos });
      }
    }
    return grupos;
  });

  /** Numero de modulos marcados (para el resumen del total en vivo). */
  protected readonly totalSeleccionados = computed(() => this.seleccion().size);

  /** Total en vivo: suma de los precios de los modulos MARCADOS (>= 0). */
  protected readonly total = computed(() => {
    const precios = this.precios();
    let suma = 0;
    for (const clave of this.seleccion()) {
      suma += this.aNumero(precios.get(clave));
    }
    return suma;
  });

  /**
   * Claves de modulo que NO pueden desmarcarse porque algun modulo seleccionado
   * las requiere (union de `dependencias[clave]` para toda clave seleccionada).
   * Derivado del mapa de dependencias (Req 9.3, 9.4), nunca de una lista fija.
   */
  protected readonly requeridosBloqueados = computed<Set<string>>(() => {
    const mapa = this.dependencias();
    const bloqueados = new Set<string>();
    for (const clave of this.seleccion()) {
      for (const requerido of mapa[clave] ?? []) {
        bloqueados.add(requerido);
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
    const seleccion = this.seleccion();
    const frases: string[] = [];
    for (const clave of seleccion) {
      const requeridos = mapa[clave] ?? [];
      if (requeridos.length === 0) {
        continue;
      }
      const etiqueta = this.etiquetaModulo(clave);
      const listaRequeridos = requeridos.map((r) => this.etiquetaModulo(r));
      const requeridosTexto = this.unir(listaRequeridos);
      const verbo = requeridos.length > 1 ? 'Se activarán también' : 'Se activará también';
      frases.push(`${etiqueta} depende de ${requeridosTexto}. ${verbo} ${requeridosTexto}.`);
    }
    return frases.length > 0 ? frases.join(' ') : null;
  });

  constructor() {
    // Precarga de precios/seleccion en modo edicion desde el Plan.
    if (this.datos.plan) {
      const precios = new Map<string, string>();
      const seleccion = new Set<string>();
      for (const [clave, precio] of Object.entries(this.datos.plan.preciosModulos ?? {})) {
        seleccion.add(clave);
        precios.set(clave, String(precio));
      }
      this.precios.set(precios);
      this.seleccion.set(seleccion);
    }

    // Carga en paralelo del catalogo de modulos, giros activos y monedas activas.
    this.girosService.listar(true, 0, 100).subscribe({
      next: (pagina) => this.giros.set(pagina.content),
      error: (e: HttpErrorResponse) => this.error.set(mensajeDeError(e)),
    });
    this.service.listarMonedas().subscribe({
      next: (monedas) => {
        const activas = monedas.filter((m) => m.activo);
        this.monedas.set(activas);
        // En alta, si no hay moneda elegida y existe MXN activa, la usa por defecto.
        if (!this.esEdicion && this.formulario.controls.monedaCodigo.value === '') {
          const porDefecto = activas.find((m) => m.codigo === MONEDA_POR_DEFECTO);
          if (porDefecto) {
            this.formulario.controls.monedaCodigo.setValue(porDefecto.codigo);
            this.monedaSeleccionada.set(porDefecto.codigo);
          }
        }
      },
      error: (e: HttpErrorResponse) => this.error.set(mensajeDeError(e)),
    });
    this.service.listarModulos().subscribe({
      next: (modulos: ModuloCatalogo[]) => {
        this.catalogo.set(modulos);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
    // Mapa de dependencias entre modulos (falla suave: si no carga, queda vacio
    // y el aviso/bloqueo no se activan; el guardado sigue disponible).
    this.service.listarDependenciasModulos().subscribe({
      next: (mapa) => this.dependencias.set(mapa),
      error: () => this.dependencias.set({}),
    });
  }

  /** Etiqueta humana de una clave de Giro (nombre visible si esta cargado). */
  private etiquetaGiro(clave: string): string {
    return this.giros().find((g) => g.clave === clave)?.nombreVisible ?? humanizarGiro(clave);
  }

  /**
   * Reacciona al cambio de Giro: descarta las selecciones especificas del Giro
   * anterior (las que no son de Nucleo ni del nuevo Giro) y conserva el Nucleo.
   * Informa de forma discreta cuando efectivamente se descarto algo.
   */
  protected cambiarGiro(giroId: string): void {
    this.formulario.controls.giroId.setValue(giroId);
    this.giroIdSeleccionado.set(giroId);
    const giroClave = this.giros().find((g) => g.id === giroId)?.clave ?? null;
    // Claves permitidas tras el cambio: Nucleo (giro null) + especificas del Giro.
    const permitidas = new Set(
      this.catalogo()
        .filter((m) => m.giro === null || m.giro === giroClave)
        .map((m) => m.clave),
    );

    const seleccion = new Set<string>();
    const precios = new Map(this.precios());
    let descarto = false;
    for (const clave of this.seleccion()) {
      if (permitidas.has(clave)) {
        seleccion.add(clave);
      } else {
        precios.delete(clave);
        descarto = true;
      }
    }
    this.seleccion.set(seleccion);
    this.precios.set(precios);
    this.avisoGiroCambiado.set(descarto);
  }

  /** Indica si una clave de modulo esta marcada (habilitada en el Plan). */
  protected estaSeleccionado(clave: string): boolean {
    return this.seleccion().has(clave);
  }

  /** Valor de texto del precio de un modulo (vacio si no se ha capturado). */
  protected precioDe(clave: string): string {
    return this.precios().get(clave) ?? '';
  }

  /**
   * Alterna un modulo: al marcar habilita su precio y, por dependencia, marca
   * tambien los modulos requeridos (con precio 0.00 por defecto si no tenian);
   * al desmarcar lo limpia. RECHAZA desmarcar una clave requerida por algun
   * modulo aun seleccionado (bloqueo de deseleccion, Req 9.3). Todo derivado del
   * mapa de dependencias (Req 9.4), sin listas hardcodeadas.
   */
  protected alternar(clave: string, seleccionado: boolean): void {
    // Bloqueo de deseleccion: no permite desmarcar un modulo requerido por otro.
    if (!seleccionado && this.requeridosBloqueados().has(clave)) {
      return;
    }
    const seleccion = new Set(this.seleccion());
    const precios = new Map(this.precios());
    if (seleccionado) {
      seleccion.add(clave);
      // Marca en cascada los modulos requeridos por la clave (Req 9.1, 9.2).
      for (const requerido of this.dependencias()[clave] ?? []) {
        if (!seleccion.has(requerido)) {
          seleccion.add(requerido);
          // Habilita su precio con 0.00 por defecto si no tenia valor capturado.
          if (!precios.has(requerido)) {
            precios.set(requerido, '0.00');
          }
        }
      }
    } else {
      seleccion.delete(clave);
      precios.delete(clave);
    }
    this.seleccion.set(seleccion);
    this.precios.set(precios);
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
    return this.catalogo().find((m) => m.clave === clave)?.nombreVisible ?? clave;
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

  /** Registra el precio capturado para un modulo. */
  protected fijarPrecio(clave: string, valor: string): void {
    const precios = new Map(this.precios());
    precios.set(clave, valor);
    this.precios.set(precios);
  }

  /** Convierte el texto de un precio a numero no negativo (0 si es invalido). */
  private aNumero(texto: string | undefined): number {
    if (texto === undefined || texto.trim() === '') {
      return 0;
    }
    const n = Number(texto);
    return Number.isFinite(n) && n >= 0 ? n : 0;
  }

  protected guardar(): void {
    this.error.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const v = this.formulario.getRawValue();

    // preciosModulos SOLO con los modulos marcados; su precio (>= 0), 0 si vacio.
    const preciosModulos: Record<string, number> = {};
    const precios = this.precios();
    for (const clave of this.seleccion()) {
      preciosModulos[clave] = Math.round(this.aNumero(precios.get(clave)) * 100) / 100;
    }

    const request: GuardarPlanRequest = {
      nombre: v.nombre.trim(),
      maxUsuarios: Number(v.maxUsuarios),
      duracionDias: Number(v.duracionDias),
      giroId: v.giroId,
      monedaCodigo: v.monedaCodigo,
      preciosModulos,
    };
    const peticion = this.esEdicion
      ? this.service.actualizarPlan(this.datos.plan!.id, request)
      : this.service.crearPlan(request);

    peticion.subscribe({
      next: (plan) => {
        this.guardando.set(false);
        this.dialogRef.close(plan);
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  protected cancelar(): void {
    this.dialogRef.close(undefined);
  }
}
