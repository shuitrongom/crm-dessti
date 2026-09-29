// =============================================================================
// Vista de detalle de Proyecto (Req 21) — avance consolidado por sitio
// -----------------------------------------------------------------------------
// Muestra el estado consolidado del Proyecto y, por cada Sitio, su avance en las
// cuatro fases (levantamiento, permiso, fabricacion, instalacion) con una barra
// de progreso accesible (ProgressBadge). Permite agregar Sitios (Req 21.2). Las
// acciones se gobiernan por permiso proyecto:{leer,actualizar}.
// =============================================================================

import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ProgressBadge } from '../../../shared/components/progress-badge/progress-badge';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import {
  IndicadorInfoDialog,
  type DatosIndicadorInfo,
} from '../../../shared/indicadores/indicador-info-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';
import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { humanizarEstado, tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { MetricChart, MetricPoint } from '../../../shared/components/metric-chart/metric-chart';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { DashboardSection } from '../../../shared/components/dashboard-section/dashboard-section';

import { ProyectosService } from '../services/proyectos.service';
import { SitioFormDialog, SitioFormDialogData } from './sitio-form-dialog';
import { AvanceSitioDialog, AvanceSitioDialogData } from './avance-sitio-dialog';
import { EvidenciasDialog, EvidenciasDialogData } from './evidencias-dialog';
import {
  ETIQUETA_FASE_SITIO,
  FaseSitioGenerica,
  ORDEN_FASE_SITIO,
  Proyecto,
  SitioAvance,
  SitioFase,
  porcentajeAvanceSitio,
  porcentajeFaseSitio,
  siguienteFaseSitio,
} from '../models/operacion.models';

@Component({
  selector: 'app-operacion-proyecto-detalle',
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    PageHeader,
    StateContainer,
    ProgressBadge,
    EstadoChip,
    MetricChart,
    KpiTile,
    DashboardSection,
  ],
  templateUrl: './proyecto-detalle.html',
  styleUrl: './proyecto-detalle.scss',
})
export class OperacionProyectoDetalle implements OnInit {
  /** Identificador del Proyecto tomado de la ruta (:id). */
  readonly id = input.required<string>();

  private readonly service = inject(ProyectosService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly puedeAgregarSitio = this.auth.tienePermiso('proyecto', 'actualizar');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly proyecto = signal<Proyecto | null>(null);

  ngOnInit(): void {
    // La carga inicial va en ngOnInit (no en el constructor): los signal inputs
    // como `id` solo tienen valor DESPUES de la construccion. Leer this.id() en el
    // constructor lanza NG0950 ("Input id is required but no value is available
    // yet") y deja la vista en blanco.
    this.cargar();
  }

  cargar(): void {
    this.fase.set('cargando');
    this.service.consultar(this.id()).subscribe({
      next: (proyecto) => {
        this.proyecto.set(proyecto);
        this.fase.set('ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Porcentaje de avance de un Sitio (0..100) segun las fases cubiertas. */
  avanceDe(sitio: SitioAvance): number {
    return porcentajeAvanceSitio(sitio);
  }

  /**
   * Estado derivado del avance del Sitio para colorear ProgressBadge sin ser el
   * color el unico portador de significado: 100% cumplido, >=50% en_curso, resto
   * en_riesgo.
   */
  estadoDe(sitio: SitioAvance): 'cumplido' | 'en_curso' | 'en_riesgo' {
    const avance = this.avanceDe(sitio);
    if (avance >= 100) {
      return 'cumplido';
    }
    return avance >= 50 ? 'en_curso' : 'en_riesgo';
  }

  // ------------------------------------------------------------------
  // Multi-sitio (giros genericos): fase editable por Sitio (Req 3.2)
  // ------------------------------------------------------------------

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;
  protected readonly etiquetaFase = ETIQUETA_FASE_SITIO;
  protected readonly fasesTodas = ORDEN_FASE_SITIO;

  /** Correccion/retroceso de fase: operacion administrativa (proyecto:cambiar_estado). */
  protected readonly puedeCorregir = this.auth.tienePermiso('proyecto', 'cambiar_estado');

  /** Indica si el Proyecto usa el pipeline multi-sitio generico (no anuncios). */
  protected readonly esMultisitio = computed(() => this.proyecto()?.estadoMultisitio != null);

  /** Scorecard de sucursales por fase sobre el Proyecto multi-sitio cargado. */
  protected readonly resumenMultisitio = computed(() => {
    const sitios = this.proyecto()?.sitiosMultisitio ?? [];
    let pendientes = 0;
    let enCurso = 0;
    let entregados = 0;
    for (const s of sitios) {
      if (s.fase === 'entregado') {
        entregados += 1;
      } else if (s.fase === 'pendiente') {
        pendientes += 1;
      } else {
        enCurso += 1;
      }
    }
    return { total: sitios.length, pendientes, enCurso, entregados };
  });

  /**
   * Gauge: avance global del proyecto multi-sitio (0-100). Promedia el porcentaje de
   * avance de cada sucursal segun su fase (pendiente=0 … entregado=100).
   */
  protected readonly avanceGlobal = computed<number>(() => {
    const sitios = this.proyecto()?.sitiosMultisitio ?? [];
    if (sitios.length === 0) {
      return 0;
    }
    const suma = sitios.reduce((acc, s) => acc + porcentajeFaseSitio(s.fase), 0);
    return Math.max(0, Math.min(100, Math.round(suma / sitios.length)));
  });

  /** Dona: distribucion de sucursales por fase de despliegue. */
  protected readonly composicionFases = computed<MetricPoint[]>(() => {
    const sitios = this.proyecto()?.sitiosMultisitio ?? [];
    const cuenta = new Map<FaseSitioGenerica, number>();
    for (const s of sitios) {
      cuenta.set(s.fase, (cuenta.get(s.fase) ?? 0) + 1);
    }
    return (Object.keys(ETIQUETA_FASE_SITIO) as FaseSitioGenerica[]).map((f) => ({
      etiqueta: ETIQUETA_FASE_SITIO[f],
      valor: cuenta.get(f) ?? 0,
    }));
  });

  /** Porcentaje de avance de una sucursal segun su fase. */
  porcentajeSitio(sitio: SitioFase): number {
    return porcentajeFaseSitio(sitio.fase);
  }

  // ------------------------------------------------------------------
  // Tablero Kanban de sucursales por fase (Req 3.2) — vista premium
  // ------------------------------------------------------------------

  /** Icono contextual por fase para las tarjetas del tablero. */
  protected readonly iconoFase: Record<FaseSitioGenerica, string> = {
    pendiente: 'inventory_2',
    en_preparacion: 'precision_manufacturing',
    en_instalacion: 'local_shipping',
    entregado: 'task_alt',
  };

  /**
   * Sucursal que acaba de cambiar de fase, para reproducir la animacion de
   * "llegada" a su nueva columna. Se limpia sola tras la transicion.
   */
  protected readonly sitioAnimado = signal<string | null>(null);

  /**
   * Columnas del tablero Kanban: las 4 fases en orden, cada una con sus
   * sucursales. Se recomputa automaticamente cuando cambia el Proyecto (al
   * avanzar/corregir una fase, la tarjeta aparece en su nueva columna).
   */
  protected readonly columnasKanban = computed(() => {
    const sitios = this.proyecto()?.sitiosMultisitio ?? [];
    return ORDEN_FASE_SITIO.map((fase) => ({
      fase,
      etiqueta: ETIQUETA_FASE_SITIO[fase],
      icono: this.iconoFase[fase],
      sitios: sitios.filter((s) => s.fase === fase),
    }));
  });

  /** Siguiente fase disponible para una sucursal (null si ya esta entregada). */
  siguienteFase(sitio: SitioFase): FaseSitioGenerica | null {
    return this.puedeAgregarSitio ? siguienteFaseSitio(sitio.fase) : null;
  }

  /**
   * Avanza la fase de una sucursal a la fase destino (Req 3.2) mediante un MODAL
   * (AvanceSitioDialog) que confirma el avance y captura una evidencia opcional.
   * Al cerrarse con el Proyecto actualizado, la tarjeta salta animada a su nueva
   * columna del tablero.
   */
  avanzarSitio(sitio: SitioFase, destino: FaseSitioGenerica): void {
    // Entregar (desde en_instalacion) exige evidencia aprobada (Req 3.2): se avisa
    // en el modal; el backend aplica la guarda y responde 422 si falta.
    const requiereEvidencia =
      destino === 'entregado' && sitio.fase === 'en_instalacion';
    // Pasar a instalación exige levantamiento completado + permiso vigente
    // (Req 3-bis.2): se avisa en el modal; el backend valida y responde 422 si falta.
    const requierePrecondicionesInstalacion = destino === 'en_instalacion';
    const data: AvanceSitioDialogData = {
      proyectoId: this.id(),
      sitio,
      destino,
      icono: this.iconoFase[destino],
      requiereEvidencia,
      requierePrecondicionesInstalacion,
      modo: 'avance',
    };
    const ref = this.dialog.open(AvanceSitioDialog, {
      width: 'min(560px, 96vw)',
      maxWidth: 'min(560px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((proyecto?: Proyecto) => {
      if (proyecto) {
        this.proyecto.set(proyecto);
        this.animarLlegada(sitio.sitio.id);
        this.toast.exito('Avance del sitio actualizado.');
      }
    });
  }

  /**
   * Abre la GALERIA de evidencias de una sucursal (Req 3.2): ver/subir archivos y,
   * con permiso, aprobar/rechazar. El acceso a subir se gobierna por
   * proyecto:actualizar (puedeAgregarSitio). No requiere recargar el proyecto salvo
   * que cambie algo relevante para el tablero (no altera la fase).
   */
  abrirEvidencias(sitio: SitioFase): void {
    const data: EvidenciasDialogData = {
      proyectoId: this.id(),
      sitioId: sitio.sitio.id,
      sitioNombre: sitio.sitio.nombre,
      fase: sitio.fase,
      puedeSubir: this.puedeAgregarSitio,
    };
    this.dialog.open(EvidenciasDialog, {
      width: 'min(720px, 96vw)',
      maxWidth: 'min(720px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
  }

  /**
   * Corrige (incluido retroceso) la fase de una sucursal a cualquier fase. Operacion
   * administrativa; solo visible con permiso proyecto:cambiar_estado (Req 3.2).
   */
  corregirFase(sitio: SitioFase, destino: FaseSitioGenerica): void {
    if (destino === sitio.fase) {
      return;
    }
    // La corrección exige un MOTIVO obligatorio (Req 3-bis.5): se captura en el
    // modal (modo corrección) en lugar de llamar directo al backend, que de otro
    // modo rechazaría con 422 por falta de motivo. El modal envía la corrección.
    const data: AvanceSitioDialogData = {
      proyectoId: this.id(),
      sitio,
      destino,
      icono: this.iconoFase[destino],
      modo: 'correccion',
    };
    const ref = this.dialog.open(AvanceSitioDialog, {
      width: 'min(560px, 96vw)',
      maxWidth: 'min(560px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((proyecto?: Proyecto) => {
      if (proyecto) {
        this.proyecto.set(proyecto);
        this.animarLlegada(sitio.sitio.id);
        this.toast.exito('Fase de la sucursal corregida.');
      }
    });
  }

  /**
   * Marca una sucursal como recien llegada a su nueva columna para disparar la
   * animacion de entrada; la limpia sola pasado el tiempo de la transicion.
   */
  private animarLlegada(sitioId: string): void {
    this.sitioAnimado.set(sitioId);
    setTimeout(() => {
      if (this.sitioAnimado() === sitioId) {
        this.sitioAnimado.set(null);
      }
    }, 700);
  }

  /** Abre el modal para agregar un Sitio al Proyecto (Req 21.2). */
  nuevoSitio(): void {
    this.abrirSitio();
  }

  /** Abre el modal para editar el nombre/direccion de un Sitio (Req 21.2). */
  editarSitio(sitio: SitioFase): void {
    this.abrirSitio(sitio);
  }

  /**
   * Abre el modal de formulario de Sitio (alta si no se pasa `sitio`, edición si se
   * pasa). En edición el backend devuelve el Proyecto detallado y se aplica directo;
   * en alta se recarga el detalle.
   */
  private abrirSitio(sitio?: SitioFase): void {
    const data: SitioFormDialogData = { proyectoId: this.id(), sitio };
    const ref = this.dialog.open(SitioFormDialog, {
      width: 'min(620px, 96vw)',
      maxWidth: 'min(620px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((resultado?: Proyecto | true) => {
      if (!resultado) {
        return;
      }
      if (resultado === true) {
        this.toast.exito('Sitio agregado.');
        this.cargar();
      } else {
        // Edición: el diálogo devolvió el Proyecto detallado ya actualizado.
        this.proyecto.set(resultado);
        this.toast.exito('Sitio actualizado.');
      }
    });
  }

  /**
   * Abre el dialogo explicativo de un indicador de sucursales del proyecto (¿qué es? /
   * ¿cómo se calcula? / ¿por qué importa?). La clave debe coincidir con una del catálogo.
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
}
