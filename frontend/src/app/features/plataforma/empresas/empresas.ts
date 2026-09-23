// =============================================================================
// Vista de administracion de Empresas (super_admin) (Req 24)
// -----------------------------------------------------------------------------
// Tablero de Empresas: fila de indicadores KPI (totales por estado), lista
// paginada con filtro por estado y busqueda de texto con debounce, alta de
// Empresa (dialogo) y cambios de estado (activar/suspender) con confirmacion
// para las acciones sensibles (Req 24.4, 54). Estados de carga/vacio/error
// consistentes.
//
// Los conteos globales de los KPI se calculan sin depender de un endpoint nuevo:
// se consultan en paralelo (forkJoin) las paginas de tamano 1 por estado y se lee
// `totalElements` de cada respuesta. Asi el conteo es global (no de la pagina
// filtrada ni del texto buscado) y la tabla principal mantiene su propio
// filtro/paginacion/busqueda.
//
// La busqueda usa un signal enlazado a un Observable (toObservable) con
// debounceTime(300ms) para no lanzar una peticion por cada tecla; al cambiar el
// texto se reinicia a la pagina 0.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { forkJoin } from 'rxjs';
import { debounceTime, distinctUntilChanged, skip } from 'rxjs/operators';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { StatCard } from '../../../shared/components/stat-card/stat-card';
import {
  ColumnaTabla,
  DataTable,
  CeldaTablaDirective,
  CambioPagina,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { AuthService } from '../../../core/auth/auth.service';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { EmpresasService } from '../services/empresas.service';
import { GirosService } from '../services/giros.service';
import {
  Empresa,
  EstadoEmpresa,
  EstadoSuscripcion,
  Giro,
  TipoInstrumento,
} from '../models/plataforma.models';
import { CrearEmpresaDialog } from './crear-empresa-dialog';
import { EditarEmpresaDialog, EditarEmpresaDialogData } from './editar-empresa-dialog';
import { ResetPasswordDialog, ResetPasswordDialogData } from './reset-password-dialog';
import { CambiarGiroDialog, CambiarGiroDialogData } from './cambiar-giro-dialog';
import { PlanSuscripcionDialog, PlanSuscripcionDialogData } from './plan-suscripcion-dialog';

/** Conteos globales de Empresas por estado para los indicadores KPI. */
interface ConteosEmpresas {
  total: number;
  activas: number;
  suspendidas: number;
  canceladas: number;
}

@Component({
  selector: 'app-plataforma-empresas',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    StatCard,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './empresas.html',
  styleUrl: './empresas.scss',
})
export class PlataformaEmpresas {
  private readonly service = inject(EmpresasService);
  private readonly girosService = inject(GirosService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  /**
   * Columnas de la tabla de Empresas. La celda de nombre concentra el avatar
   * (logo o iniciales), la razon social, el RFC y el nombre comercial; la de
   * contacto muestra correo/telefono.
   *
   * Es un `computed` porque la columna "Plan" solo debe existir cuando el
   * usuario tiene permiso de lectura de suscripciones (Req 10.4). Al derivar el
   * arreglo de `puedeVerPlan` (que lee la senal de permisos del `AuthService`),
   * la tabla oculta por completo la columna cuando falta el permiso, en lugar de
   * mostrarla vacia.
   */
  protected readonly columnas = computed<ColumnaTabla[]>(() => {
    const cols: ColumnaTabla[] = [
      { clave: 'nombre', encabezado: 'Empresa' },
      // "Giro" (vertical de negocio) por su nombre, nunca el UUID. Se oculta por
      // debajo de `md` (ocultarEnMovil) para que Empresa/Estado/Acciones quepan
      // sin scroll horizontal en pantallas estrechas (Req 52).
      { clave: 'giro', encabezado: 'Giro', ocultarEnMovil: true },
    ];
    // "Plan" (plan vigente por su NOMBRE, nunca el UUID) alimentada por el dato
    // enriquecido del EmpresaDto (sin peticiones por fila). Se oculta por debajo
    // de `md` (ocultarEnMovil) con el mismo criterio responsivo que "Giro", para
    // preservar la legibilidad sin scroll horizontal en pantallas estrechas
    // (Req 1.7). Solo se agrega con permiso de lectura de suscripciones (Req 10.4).
    if (this.puedeVerPlan) {
      cols.push({ clave: 'plan', encabezado: 'Plan', ocultarEnMovil: true });
    }
    cols.push(
      // "Contacto" es la columna menos critica: se oculta por debajo de `md`
      // (ocultarEnMovil) para que Empresa/Estado/Acciones quepan sin scroll
      // horizontal en pantallas estrechas (Req 52). El dato sigue disponible en
      // el alta/edicion de la Empresa.
      { clave: 'contacto', encabezado: 'Contacto', ocultarEnMovil: true },
      { clave: 'estado', encabezado: 'Estado' },
      { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
    );
    return cols;
  });

  /** Estado de la pagina de Empresas. */
  protected readonly estado = signal<EstadoSolicitud<Empresa[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal<EstadoEmpresa | null>(null);
  /** Texto de busqueda escrito por el Usuario (sin debounce). */
  protected readonly busqueda = signal('');
  /** Texto de busqueda ya aplicado a la ultima carga (para el estado vacio). */
  private readonly busquedaAplicada = signal('');

  /** Conteos globales por estado para los KPI; `null` mientras se resuelven. */
  protected readonly conteos = signal<ConteosEmpresas | null>(null);
  /** `true` mientras se calculan los conteos KPI (estado de carga sutil). */
  protected readonly cargandoConteos = signal(true);

  /**
   * Catalogo de giros ACTIVOS (para el selector de "Cambiar giro"). Se carga una
   * unica vez al iniciar la vista.
   */
  private readonly girosActivos = signal<Giro[]>([]);
  /** Mapa giroId -> nombreVisible para resolver el nombre sin exponer el UUID. */
  private readonly mapaGiros = signal<Map<string, string>>(new Map());

  /** Valor mostrado en cada KPI: un guion mientras carga o si no hay dato. */
  protected readonly kpiTotal = computed(() => this.valorKpi(this.conteos()?.total));
  protected readonly kpiActivas = computed(() => this.valorKpi(this.conteos()?.activas));
  protected readonly kpiSuspendidas = computed(() => this.valorKpi(this.conteos()?.suspendidas));
  protected readonly kpiCanceladas = computed(() => this.valorKpi(this.conteos()?.canceladas));

  /**
   * Mensaje del estado vacio: refleja la busqueda cuando la hay; en caso
   * contrario, el mensaje generico del filtro por estado.
   */
  protected readonly mensajeVacio = computed(() => {
    const q = this.busquedaAplicada().trim();
    return q
      ? `No se encontraron empresas para Â«${q}Â».`
      : 'No hay empresas registradas para el filtro seleccionado.';
  });

  /** Permiso para cambiar el estado de una Empresa (deny-by-default). */
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('empresa', 'cambiar_estado');
  protected readonly puedeCrear = this.auth.tienePermiso('empresa', 'crear');
  /** Permiso para editar los datos descriptivos/fiscales de una Empresa. */
  protected readonly puedeEditar = this.auth.tienePermiso('empresa', 'actualizar');
  /**
   * Permiso para restablecer la contrasena del administrador de una Empresa. El
   * backend lo concede al super_admin junto con el alta de Empresas, por lo que
   * se reutiliza `empresa:crear` como predicado (deny-by-default).
   */
  protected readonly puedeRestablecer = this.auth.tienePermiso('empresa', 'crear');
  /**
   * Permiso de lectura de suscripciones. Gobierna la visibilidad de la columna
   * "Plan" del listado (y, mas adelante, la accion del panel "Plan y
   * suscripcion"). Se concede con `suscripcion:leer` o `suscripcion:listar`
   * (deny-by-default), Req 10.4.
   */
  protected readonly puedeVerPlan = this.auth.tieneAlgunPermiso(
    'suscripcion:leer',
    'suscripcion:listar',
  );

  constructor() {
    // Debounce de la busqueda: reinicia a la pagina 0 y recarga (Req 24.5). Se
    // omite (skip) la emision inicial del signal para no duplicar la carga que
    // ya dispara el `cargar()` del constructor.
    toObservable(this.busqueda)
      .pipe(skip(1), debounceTime(300), distinctUntilChanged())
      .subscribe(() => {
        this.page.set(0);
        this.cargar();
      });
    this.cargar();
    this.cargarConteos();
    this.cargarGiros();
  }

  /**
   * Carga una unica vez el catalogo de giros ACTIVOS y arma el mapa
   * giroId -> nombreVisible que resuelve el nombre a mostrar en el listado y el
   * selector de "Cambiar giro". Ante un error no rompe la vista: el listado cae
   * al marcador neutro y el selector queda vacio.
   */
  private cargarGiros(): void {
    this.girosService.listar(true, 0, 100).subscribe({
      next: (pagina) => {
        this.girosActivos.set(pagina.content);
        this.mapaGiros.set(new Map(pagina.content.map((g) => [g.id, g.nombreVisible])));
      },
      error: () => {
        this.girosActivos.set([]);
        this.mapaGiros.set(new Map());
      },
    });
  }

  /**
   * Nombre visible del giro de una Empresa. Devuelve el nombre del catalogo o un
   * marcador neutro cuando el id no se resuelve; NUNCA expone el UUID (Req 1.4).
   */
  protected nombreGiro(id: string | null | undefined): string {
    if (!id) {
      return '(sin giro)';
    }
    return this.mapaGiros().get(id) ?? '(sin giro)';
  }

  /**
   * Nombre del plan vigente de una Empresa para la columna "Plan". El nombre lo
   * resuelve el BACKEND dentro del `EmpresaDto` enriquecido (`planVigente`), por
   * lo que NO se hace ninguna peticion por fila para obtenerlo (Req 1.2). Cuando
   * la Empresa no tiene plan vigente devuelve el marcador neutro "Sin plan"
   * (Req 1.3, 1.4, 1.5). NUNCA expone un UUID (Req 1.6).
   */
  protected nombrePlan(empresa: Empresa): string {
    return (
      empresa.planVigente?.nombreInstrumento ?? empresa.planVigente?.nombrePlan ?? 'Sin plan'
    );
  }

  /**
   * Etiqueta humana del TIPO de instrumento vigente ("Plan" | "Suscripción")
   * para acompanar al nombre en la columna. Devuelve `null` cuando la Empresa no
   * tiene un contrato vigente (`planVigente` ausente). NUNCA expone el UUID.
   */
  protected tipoInstrumentoEtiqueta(empresa: Empresa): string | null {
    const tipo = empresa.planVigente?.tipoInstrumento;
    if (!tipo) {
      return null;
    }
    const etiquetas: Record<TipoInstrumento, string> = {
      plan: 'Plan',
      suscripcion: 'Suscripción',
    };
    return etiquetas[tipo] ?? tipo;
  }

  /**
   * Etiqueta humana en espanol del estado de la suscripcion vigente, para
   * acompanar al nombre del plan como una pildora accesible. No depende de las
   * mayusculas del backend: mapea la clave canonica a su texto en espanol.
   */
  protected etiquetaEstadoSuscripcion(estado: EstadoSuscripcion): string {
    const etiquetas: Record<EstadoSuscripcion, string> = {
      activa: 'Activa',
      en_prueba: 'En prueba',
      suspendida: 'Suspendida',
      cancelada: 'Cancelada',
      vencida: 'Vencida',
    };
    return etiquetas[estado] ?? estado;
  }

  /**
   * Estado MOSTRADO del contrato vigente en el listado, priorizando los estados
   * derivados por el backend: si esta `vencida` muestra "Vencida"; si esta
   * `enPrueba` muestra "En prueba"; en otro caso, la etiqueta del `estado`
   * persistido. Asi el usuario ve el estado real aunque el backend conserve
   * `estado='activa'` mientras la vigencia sigue corriendo.
   */
  protected estadoMostradoEmpresa(empresa: Empresa): string {
    const pv = empresa.planVigente;
    if (!pv) {
      return '';
    }
    if (pv.vencida) {
      return 'Vencida';
    }
    if (pv.enPrueba) {
      return 'En prueba';
    }
    return this.etiquetaEstadoSuscripcion(pv.estado);
  }

  /**
   * Clave de estado usada para construir el modificador CSS de la pildora
   * (`empresas__estado--<clave>`). Refleja los estados derivados (`vencida`,
   * `en_prueba`) para que el color semantico acompane al texto; en otro caso
   * usa el `estado` persistido.
   */
  protected estadoClaseEmpresa(empresa: Empresa): string {
    const pv = empresa.planVigente;
    if (!pv) {
      return '';
    }
    if (pv.vencida) {
      return 'vencida';
    }
    if (pv.enPrueba) {
      return 'en_prueba';
    }
    return pv.estado;
  }

  /**
   * Dias restantes hasta la vigencia del contrato vigente, o `null` cuando no
   * hay `vigenciaFin` (contrato sin fecha de corte) o no hay contrato.
   */
  protected diasRestantesEmpresa(empresa: Empresa): number | null {
    return empresa.planVigente?.diasRestantes ?? null;
  }

  /** Carga la pagina actual de Empresas (respeta filtro + busqueda + paginacion). */
  cargar(): void {
    this.estado.set(cargando());
    const q = this.busqueda().trim();
    this.busquedaAplicada.set(q);
    this.service.listar(this.filtroEstado(), q, this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.total.set(pagina.totalElements);
        this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  /**
   * Calcula los conteos globales por estado en paralelo, leyendo `totalElements`
   * de paginas de tamano 1 (suficiente para el conteo). Los conteos son globales
   * y NO se ven afectados por el texto de busqueda. Ante un error se muestran
   * guiones en lugar de romper la vista.
   */
  cargarConteos(): void {
    this.cargandoConteos.set(true);
    forkJoin({
      total: this.service.listar(null, null, 0, 1),
      activas: this.service.listar('activa', null, 0, 1),
      suspendidas: this.service.listar('suspendida', null, 0, 1),
      canceladas: this.service.listar('cancelada', null, 0, 1),
    }).subscribe({
      next: (r) => {
        this.conteos.set({
          total: r.total.totalElements,
          activas: r.activas.totalElements,
          suspendidas: r.suspendidas.totalElements,
          canceladas: r.canceladas.totalElements,
        });
        this.cargandoConteos.set(false);
      },
      error: () => {
        this.conteos.set(null);
        this.cargandoConteos.set(false);
      },
    });
  }

  /** Valor a mostrar en un KPI: un guion mientras carga o si no hay dato. */
  private valorKpi(valor: number | undefined): string {
    if (this.cargandoConteos() || valor === undefined) {
      return 'â€”';
    }
    return String(valor);
  }

  /**
   * Iniciales de una Empresa para el avatar (1 o 2 letras en mayusculas).
   * Toma la inicial de las dos primeras palabras del nombre; si es una sola
   * palabra, toma sus dos primeras letras.
   */
  protected iniciales(nombre: string): string {
    const palabras = nombre.trim().split(/\s+/).filter(Boolean);
    if (palabras.length === 0) {
      return '?';
    }
    if (palabras.length === 1) {
      return palabras[0].slice(0, 2).toUpperCase();
    }
    return (palabras[0][0] + palabras[1][0]).toUpperCase();
  }

  /**
   * Etiqueta humana capitalizada del estado. No depende de las mayusculas del
   * backend: mapea la clave canonica a su texto en espanol.
   */
  protected etiquetaEstado(estado: EstadoEmpresa): string {
    const etiquetas: Record<EstadoEmpresa, string> = {
      activa: 'Activa',
      suspendida: 'Suspendida',
      cancelada: 'Cancelada',
    };
    return etiquetas[estado] ?? estado;
  }

  /** Reacciona al cambio de filtro por estado. */
  cambiarFiltro(estado: EstadoEmpresa | null): void {
    this.filtroEstado.set(estado);
    this.page.set(0);
    this.cargar();
  }

  /** Actualiza el texto de busqueda (el debounce dispara la recarga). */
  cambiarBusqueda(texto: string): void {
    this.busqueda.set(texto);
  }

  /** Limpia la busqueda; el debounce recargara sin filtro de texto. */
  limpiarBusqueda(): void {
    this.busqueda.set('');
  }

  /** Reacciona al cambio de pagina. */
  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Abre el dialogo de alta de Empresa (Req 24.2). */
  crear(): void {
    const ref = this.dialog.open(CrearEmpresaDialog, {
      width: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
    });
    ref.afterClosed().subscribe((creada) => {
      if (creada) {
        this.toast.exito('Empresa creada correctamente.');
        this.cargar();
        this.cargarConteos();
      }
    });
  }

  /**
   * Abre el dialogo de edicion de los datos descriptivos/fiscales de una Empresa
   * (PUT /empresas/{id}). Al confirmar, refresca la lista y los conteos.
   */
  editar(empresa: Empresa): void {
    const data: EditarEmpresaDialogData = { empresa };
    const ref = this.dialog.open(EditarEmpresaDialog, {
      width: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((actualizada) => {
      if (actualizada) {
        this.toast.exito('Empresa actualizada correctamente.');
        this.cargar();
        this.cargarConteos();
      }
    });
  }

  /**
   * Abre el dialogo para restablecer la contrasena del administrador de una
   * Empresa (genera una temporal o establece una explicita). No requiere recargar
   * la lista, pero se refresca por consistencia si el dialogo confirma.
   */
  restablecerPassword(empresa: Empresa): void {
    const data: ResetPasswordDialogData = {
      empresaId: empresa.id,
      empresaNombre: empresa.nombre,
    };
    this.dialog.open(ResetPasswordDialog, {
      width: 'min(560px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
  }

  /** Activa una Empresa (Req 24.1). */
  activar(empresa: Empresa): void {
    this.service.activar(empresa.id).subscribe({
      next: () => {
        this.toast.exito(`Empresa "${empresa.nombre}" activada.`);
        this.cargar();
        this.cargarConteos();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Suspende una Empresa con confirmacion (accion sensible, Req 24.4, 54). */
  async suspender(empresa: Empresa): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Suspender empresa',
      mensaje: `Suspender "${empresa.nombre}" impedira el inicio de sesion de sus usuarios. Deseas continuar?`,
      textoConfirmar: 'Suspender',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.suspender(empresa.id).subscribe({
      next: () => {
        this.toast.exito(`Empresa "${empresa.nombre}" suspendida.`);
        this.cargar();
        this.cargarConteos();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /**
   * Abre el dialogo para reasignar el Giro (vertical) de una Empresa. El dialogo
   * ofrece los giros ACTIVOS por nombre (sin UUID) e invoca
   * EmpresasService.cambiarGiro; en exito notifica y refresca la lista + los
   * conteos. Los errores 422 (giro invalido / datos del vertical) se muestran
   * dentro del propio dialogo. Reutiliza el permiso `empresa:cambiar_estado`.
   */
  cambiarGiro(empresa: Empresa): void {
    const data: CambiarGiroDialogData = {
      empresa,
      girosActivos: this.girosActivos(),
    };
    const ref = this.dialog.open(CambiarGiroDialog, {
      width: 'min(560px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((actualizada) => {
      if (actualizada) {
        this.toast.exito(`Giro de "${empresa.nombre}" actualizado.`);
        this.cargar();
        this.cargarConteos();
      }
    });
  }

  /**
   * Abre el panel "Plan y suscripcion" de una Empresa (Req 5.1). El panel muestra
   * el plan vigente y la suscripcion actual y concentra las acciones
   * (asignar/cambiar plan, activar/suspender/cancelar, actualizar vigencia), cada
   * una notificada por el propio panel con errores traducidos al espanol (404/409/
   * 422/403) via `mensajeDeError` (Req 10.5, 11.1). Aqui NO se emite toast: solo se
   * refresca el listado y los conteos cuando el panel devuelve que hubo un cambio,
   * para que la columna "Plan" refleje el nuevo estado vigente (Req 12.1). La
   * visibilidad de la accion se gobierna por `puedeVerPlan` en la plantilla
   * (Req 10.4).
   */
  planSuscripcion(empresa: Empresa): void {
    const data: PlanSuscripcionDialogData = { empresa };
    const ref = this.dialog.open(PlanSuscripcionDialog, {
      width: 'min(720px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((huboCambio) => {
      if (huboCambio) {
        this.cargar();
        this.cargarConteos();
      }
    });
  }
}

