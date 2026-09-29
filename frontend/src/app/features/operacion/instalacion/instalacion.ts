// =============================================================================
// Vista de Ordenes de Trabajo de Instalacion / OTI (Req 19) — listado premium
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtros por estado, por Cliente (por NOMBRE
// via app-entity-select; Req 11.2) y por Cuadrilla. La programacion de una OTI y
// el registro de avance se hacen en MODALES animados (OtiFormDialog,
// OtiAvanceDialog). Las transiciones de estado validas se ofrecen en un menu. Los
// KPIs (programadas/en curso/completadas, con conteos reales del backend) abren el
// modal explicativo del indicador. Acciones gobernadas por permiso
// orden_trabajo_instalacion:{...}.
//
// NOTA SOBRE EL FILTRO DE CUADRILLA (Req 11.2): el backend NO expone un catalogo
// de Cuadrillas — `cuadrilla_id` es una referencia debil (sin tabla `cuadrilla`
// ni endpoint REST de listado; ver `NombresOperacionService.cuadrillas()`, que es
// una lista vacia por diseno). Por ello el filtro (y la captura en el modal) se
// ofrecen por identificador en lugar de un selector por nombre. NO se inventa un
// endpoint. Cuando el backend publique el catalogo, migrara a app-entity-select.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable, firstValueFrom, of } from 'rxjs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { ChipEstado, VarianteChipEstado } from '../../../shared/components/chip-estado/chip-estado';
import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import {
  IndicadorInfoDialog,
  type DatosIndicadorInfo,
} from '../../../shared/indicadores/indicador-info-dialog';
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
import { OtiFormDialog } from './oti-form-dialog';
import { OtiAvanceDialog, OtiAvanceDialogData } from './oti-avance-dialog';

@Component({
  selector: 'app-operacion-instalacion',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    KpiTile,
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
  private readonly dialog = inject(MatDialog);

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

  /** Conteos reales del backend para los indicadores (independientes del filtro). */
  protected readonly totalProgramadas = signal<number | null>(null);
  protected readonly totalEnCurso = signal<number | null>(null);
  protected readonly totalCompletadas = signal<number | null>(null);

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'cliente', encabezado: 'Cliente' },
    { clave: 'fechaProgramada', encabezado: 'Fecha programada' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  /**
   * Filtros del listado: Cliente por NOMBRE (app-entity-select expone el id
   * internamente) y Cuadrilla por identificador (sin catalogo backend; ver la
   * nota de cabecera).
   */
  protected readonly formFiltro = this.fb.nonNullable.group({
    clienteId: [''],
    cuadrillaId: [''],
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
    // render de la columna Cliente sin exponer UUIDs; luego el listado y conteos.
    this.nombres.cargar().subscribe({
      next: () => {
        this.cargar();
        this.cargarConteos();
      },
      error: () => {
        this.cargar();
        this.cargarConteos();
      },
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

  /**
   * Carga los conteos de los indicadores con consultas de tamano 1 (solo
   * totalElements), independientes del filtro de la tabla.
   */
  cargarConteos(): void {
    this.service.listar({ estado: 'programada' }, 0, 1).subscribe({
      next: (p) => this.totalProgramadas.set(p.totalElements),
      error: () => this.totalProgramadas.set(null),
    });
    this.service.listar({ estado: 'en_curso' }, 0, 1).subscribe({
      next: (p) => this.totalEnCurso.set(p.totalElements),
      error: () => this.totalEnCurso.set(null),
    });
    this.service.listar({ estado: 'completada' }, 0, 1).subscribe({
      next: (p) => this.totalCompletadas.set(p.totalElements),
      error: () => this.totalCompletadas.set(null),
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

  /** Abre el modal de programacion de OTI y recarga si se programó. */
  nuevo(): void {
    const ref = this.dialog.open(OtiFormDialog, {
      width: 'min(760px, 96vw)',
      maxWidth: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
    });
    ref.afterClosed().subscribe((creada?: OrdenTrabajoInstalacion) => {
      if (creada) {
        this.toast.exito('Orden de trabajo programada.');
        this.cargar();
        this.cargarConteos();
      }
    });
  }

  /** Abre el modal de registro de avance de una OTI. */
  abrirAvance(oti: OrdenTrabajoInstalacion): void {
    const data: OtiAvanceDialogData = { oti, nombreCliente: this.nombreCliente(oti.clienteId) };
    const ref = this.dialog.open(OtiAvanceDialog, {
      width: 'min(720px, 96vw)',
      maxWidth: 'min(720px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((registrado?: boolean) => {
      if (registrado) {
        this.toast.exito('Avance registrado.');
        this.cargar();
      }
    });
  }

  /**
   * Abre el dialogo explicativo de un indicador de OTIs. La clave debe coincidir
   * con una del catálogo de indicadores.
   */
  abrirInfoKpi(clave: string, etiqueta: string, valor: number, unidad: string): void {
    const datos: DatosIndicadorInfo = { clave, etiqueta, valor, unidad };
    this.dialog.open(IndicadorInfoDialog, {
      data: datos,
      width: '32rem',
      maxWidth: '92vw',
      autoFocus: false,
    });
  }

  /** Cambia el estado de una OTI con confirmacion (Req 19.5). */
  async cambiarEstado(oti: OrdenTrabajoInstalacion, estado: EstadoOti): Promise<void> {
    // Guarda preventiva del cierre (Req 19.6): una OTI no puede completarse sin al
    // menos una evidencia fotografica. Se verifica ANTES de confirmar consultando
    // el detalle (que trae pendientes y evidencias), para no lanzar una peticion
    // condenada al 422 y explicar al usuario que falta. El backend aplica la misma
    // regla como fuente de verdad; esto solo mejora la experiencia.
    if (estado === 'completada') {
      const puede = await this.validarCierrePreventivo(oti);
      if (!puede) {
        return;
      }
    }

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
        this.cargarConteos();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /**
   * Verifica preventivamente las guardas de cierre de una OTI (Req 19.6) antes de
   * intentar completarla: (1) al menos una evidencia fotografica adjunta, y (2) sin
   * pendientes por resolver. Devuelve `true` si el cierre puede proceder; si no,
   * muestra un mensaje explicativo (es-MX) y devuelve `false`. Ante un error de red
   * al consultar el detalle, deja que el flujo continue y sea el backend quien
   * decida (fuente de verdad), evitando bloquear por un fallo transitorio.
   */
  private async validarCierrePreventivo(oti: OrdenTrabajoInstalacion): Promise<boolean> {
    try {
      const detalle = await firstValueFrom(this.service.consultarDetalle(oti.id));
      if (detalle.evidencias.length === 0) {
        this.toast.error(
          'No se puede completar: la instalacion requiere al menos una evidencia fotografica. ' +
            'Registra evidencia desde "Avance" antes de completar.',
        );
        return false;
      }
      const sinResolver = detalle.pendientes.filter((p) => !p.resuelto);
      if (sinResolver.length > 0) {
        const descripciones = sinResolver.map((p) => `«${p.descripcion}»`).join(', ');
        this.toast.error(`No se puede completar: pendientes por resolver: [${descripciones}].`);
        return false;
      }
      return true;
    } catch {
      // Fallo al consultar el detalle: no bloqueamos localmente; el backend
      // aplicara la regla y devolvera 422 si corresponde.
      return true;
    }
  }
}
