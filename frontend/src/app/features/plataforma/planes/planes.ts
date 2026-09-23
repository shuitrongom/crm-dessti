// =============================================================================
// Vista de Planes y Suscripciones (super_admin) (Req 25, plataforma-multigiro;
// Req 10 plan-vs-suscripcion-contratacion)
// -----------------------------------------------------------------------------
// Dos secciones dentro de un mat-tab-group accesible:
//   - Pestana "Planes": catalogo de Planes (contratos de largo plazo, > 1 ano).
//   - Pestana "Suscripciones": catalogo de Paquetes de Suscripcion (contratos de
//     un ano o menos, con opcion de periodo de prueba).
// Cada pestana tiene su propia rejilla responsive de tarjetas, su boton "Nuevo"
// y su paginador de servidor. Cada tarjeta muestra el nombre, el Giro como chip,
// la moneda y el TOTAL (que calcula el backend) de forma destacada, el limite de
// usuarios como metrica y los modulos habilitados como chips (etiquetas humanas
// del catalogo). Las tarjetas de Suscripcion agregan la vigencia en dias y, si
// aplica, el periodo de prueba. Alta/edicion via dialogo.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTabsModule } from '@angular/material/tabs';
import { MatPaginatorModule, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { PaginatorIntlEs } from '../../../shared/components/data-table/paginator-intl-es';
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

import { PlanesService } from '../services/planes.service';
import { PaquetesSuscripcionService } from '../services/paquetes-suscripcion.service';
import { GirosService } from '../services/giros.service';
import { Plan, PaqueteSuscripcion } from '../models/plataforma.models';
import { humanizarGiro } from '../models/modulos-agrupados';
import { PlanDialog } from './plan-dialog';
import { PaqueteSuscripcionDialog } from './paquete-suscripcion-dialog';

/** Modulos visibles en la tarjeta antes de resumir el resto en "+N mas". */
const MAX_CHIPS_VISIBLES = 6;

/** Chips de modulos de un Plan o Paquete: los visibles y el resumen del excedente. */
interface ChipsModulos {
  visibles: string[];
  restantes: number;
}

@Component({
  selector: 'app-plataforma-planes',
  imports: [
    CurrencyPipe,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTabsModule,
    MatPaginatorModule,
    PageHeader,
    StateContainer,
  ],
  templateUrl: './planes.html',
  styleUrl: './planes.scss',
  // Traduce el paginador al espanol de forma acotada (mismo intl que DataTable).
  providers: [{ provide: MatPaginatorIntl, useClass: PaginatorIntlEs }],
})
export class PlataformaPlanes {
  private readonly service = inject(PlanesService);
  private readonly paquetesService = inject(PaquetesSuscripcionService);
  private readonly girosService = inject(GirosService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  // --- Estado de la pestana "Planes" ---------------------------------------
  protected readonly estado = signal<EstadoSolicitud<Plan[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly opcionesTamano = [10, 20, 50, 100];

  protected readonly puedeCrear = this.auth.tienePermiso('plan', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('plan', 'actualizar');
  protected readonly puedeEliminar = this.auth.tienePermiso('plan', 'eliminar');

  // --- Estado de la pestana "Suscripciones" (Paquetes) ---------------------
  // Los endpoints de Paquete estan gated por 'suscripcion:*' (backend); el
  // borrado usa 'suscripcion:cambiar_estado' (PaqueteSuscripcionController).
  protected readonly estadoPaquetes = signal<EstadoSolicitud<PaqueteSuscripcion[]>>(cargando());
  protected readonly totalPaquetes = signal(0);
  protected readonly pagePaquetes = signal(0);
  protected readonly sizePaquetes = signal(20);

  protected readonly puedeCrearPaquete = this.auth.tienePermiso('suscripcion', 'crear');
  protected readonly puedeActualizarPaquete = this.auth.tienePermiso('suscripcion', 'actualizar');
  protected readonly puedeEliminarPaquete = this.auth.tienePermiso('suscripcion', 'cambiar_estado');

  /** Etiquetas de modulo (clave -> nombre visible) del catalogo, para los chips. */
  private readonly etiquetasModulo = signal<Map<string, string>>(new Map());
  /** Etiquetas de Giro (id -> nombre visible) para el chip de giro de la tarjeta. */
  private readonly etiquetasGiro = signal<Map<string, string>>(new Map());

  constructor() {
    this.cargar();
    this.cargarPaquetes();
    // El catalogo aporta etiquetas de modulo; si falla, se humaniza la clave.
    this.service.listarModulos().subscribe({
      next: (modulos) => {
        this.etiquetasModulo.set(new Map(modulos.map((m) => [m.clave, m.nombreVisible])));
      },
      error: () => this.etiquetasModulo.set(new Map()),
    });
    // Los Giros aportan el nombre visible para el chip; si falla, se muestra la
    // clave/humanizacion o nada segun disponibilidad.
    this.girosService.listar(null, 0, 100).subscribe({
      next: (pagina) => {
        this.etiquetasGiro.set(new Map(pagina.content.map((g) => [g.id, g.nombreVisible])));
      },
      error: () => this.etiquetasGiro.set(new Map()),
    });
  }

  /** Etiqueta del Giro de un Plan: nombre visible resoluble, o `null` si no. */
  protected etiquetaGiro(plan: Plan): string | null {
    if (plan.giroId === null) {
      return null;
    }
    return this.etiquetasGiro().get(plan.giroId) ?? humanizarGiro(plan.giroId);
  }

  /** Etiqueta del Giro de un Paquete: nombre visible resoluble, o `null` si no. */
  protected etiquetaGiroPaquete(paquete: PaqueteSuscripcion): string | null {
    if (paquete.giroId === null) {
      return null;
    }
    return this.etiquetasGiro().get(paquete.giroId) ?? humanizarGiro(paquete.giroId);
  }

  /**
   * Construye los chips de modulos de un Plan: hasta {@link MAX_CHIPS_VISIBLES}
   * etiquetas humanas y el conteo del resto para el chip "+N mas".
   */
  protected chipsModulos(plan: Plan): ChipsModulos {
    return this.construirChips(plan.modulosHabilitados);
  }

  /** Chips de modulos de un Paquete (espejo de {@link chipsModulos}). */
  protected chipsModulosPaquete(paquete: PaqueteSuscripcion): ChipsModulos {
    return this.construirChips(paquete.modulosHabilitados);
  }

  /** Resuelve las claves de modulo a etiquetas humanas y arma el resumen "+N mas". */
  private construirChips(claves: string[]): ChipsModulos {
    const etiquetas = this.etiquetasModulo();
    const nombres = claves.map((clave) => etiquetas.get(clave) ?? humanizarGiro(clave));
    return {
      visibles: nombres.slice(0, MAX_CHIPS_VISIBLES),
      restantes: Math.max(nombres.length - MAX_CHIPS_VISIBLES, 0),
    };
  }

  // --- Planes ---------------------------------------------------------------

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarPlanes(this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.total.set(pagina.totalElements);
        this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  /** Reacciona al cambio de pagina del paginador de Planes. */
  cambiarPagina(evento: PageEvent): void {
    this.page.set(evento.pageIndex);
    this.size.set(evento.pageSize);
    this.cargar();
  }

  crear(): void {
    const ref = this.dialog.open(PlanDialog, {
      width: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      data: { plan: null },
    });
    ref.afterClosed().subscribe((guardado) => {
      if (guardado) {
        this.toast.exito('Plan creado correctamente.');
        this.cargar();
      }
    });
  }

  editar(plan: Plan): void {
    const ref = this.dialog.open(PlanDialog, {
      width: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      data: { plan },
    });
    ref.afterClosed().subscribe((guardado) => {
      if (guardado) {
        this.toast.exito('Plan actualizado correctamente.');
        this.cargar();
      }
    });
  }

  /**
   * Elimina un Plan con confirmacion (accion destructiva e irreversible). El
   * backend responde 422 si alguna Empresa tiene el Plan asignado ("Primero
   * cambia el plan de esas empresas."); ese mensaje se muestra tal cual.
   */
  async eliminar(plan: Plan): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Eliminar plan',
      mensaje: `Se eliminara el plan "${plan.nombre}". Esta accion no se puede deshacer. Continuar?`,
      textoConfirmar: 'Eliminar',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.eliminarPlan(plan.id).subscribe({
      next: () => {
        this.toast.exito(`Plan "${plan.nombre}" eliminado.`);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  // --- Suscripciones (Paquetes) --------------------------------------------

  cargarPaquetes(): void {
    this.estadoPaquetes.set(cargando());
    this.paquetesService.listarPaquetes(this.pagePaquetes(), this.sizePaquetes()).subscribe({
      next: (pagina) => {
        this.totalPaquetes.set(pagina.totalElements);
        this.estadoPaquetes.set(conDatos(pagina.content, pagina.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.estadoPaquetes.set(conError(mensajeDeError(e))),
    });
  }

  /** Reacciona al cambio de pagina del paginador de Suscripciones. */
  cambiarPaginaPaquetes(evento: PageEvent): void {
    this.pagePaquetes.set(evento.pageIndex);
    this.sizePaquetes.set(evento.pageSize);
    this.cargarPaquetes();
  }

  crearPaquete(): void {
    const ref = this.dialog.open(PaqueteSuscripcionDialog, {
      width: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      data: { paquete: null },
    });
    ref.afterClosed().subscribe((guardado) => {
      if (guardado) {
        this.toast.exito('Suscripcion creada correctamente.');
        this.cargarPaquetes();
      }
    });
  }

  editarPaquete(paquete: PaqueteSuscripcion): void {
    const ref = this.dialog.open(PaqueteSuscripcionDialog, {
      width: 'min(760px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      data: { paquete },
    });
    ref.afterClosed().subscribe((guardado) => {
      if (guardado) {
        this.toast.exito('Suscripcion actualizada correctamente.');
        this.cargarPaquetes();
      }
    });
  }

  /**
   * Elimina un Paquete de Suscripcion con confirmacion (accion destructiva e
   * irreversible). El backend responde 422 si algun Contrato (Suscripcion) lo
   * referencia; ese mensaje se muestra tal cual.
   */
  async eliminarPaquete(paquete: PaqueteSuscripcion): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Eliminar suscripcion',
      mensaje: `Se eliminara la suscripcion "${paquete.nombre}". Esta accion no se puede deshacer. Continuar?`,
      textoConfirmar: 'Eliminar',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.paquetesService.eliminarPaquete(paquete.id).subscribe({
      next: () => {
        this.toast.exito(`Suscripcion "${paquete.nombre}" eliminada.`);
        this.cargarPaquetes();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
