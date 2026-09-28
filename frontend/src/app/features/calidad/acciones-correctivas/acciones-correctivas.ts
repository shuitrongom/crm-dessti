// =============================================================================
// Vista de Acciones correctivas del SGC (Req 70.2, clausula 10.2)
// -----------------------------------------------------------------------------
// Listado paginado con filtro por estado; apertura de una accion correctiva;
// avance por la maquina de estados (abierta -> en_analisis -> en_ejecucion ->
// verificacion); verificacion de eficacia; y cierre (el backend exige eficacia
// verificada, Property 43, 422 si no). Dashboard con scorecard. Acciones
// gobernadas por permiso atomico.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { humanizarEstado, tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { MetricChart, MetricPoint } from '../../../shared/components/metric-chart/metric-chart';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { DashboardSection } from '../../../shared/components/dashboard-section/dashboard-section';
import { CalidadService } from '../services/calidad.service';
import { AccionCorrectiva } from '../models/calidad.models';

/** Siguiente estado de avance (no cierre) desde el estado actual. */
const AVANCE_ACCION: Record<string, string | null> = {
  abierta: 'en_analisis',
  en_analisis: 'en_ejecucion',
  en_ejecucion: 'verificacion',
  verificacion: null,
  cerrada: null,
};

@Component({
  selector: 'app-calidad-acciones-correctivas',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
    MetricChart,
    KpiTile,
    DashboardSection,
  ],
  templateUrl: './acciones-correctivas.html',
  styleUrl: '../calidad.scss',
})
export class CalidadAccionesCorrectivas {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CalidadService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly puedeCrear = this.auth.tienePermiso('accion_correctiva', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('accion_correctiva', 'cambiar_estado');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'causa', encabezado: 'Causa raíz' },
    { clave: 'acciones_plan', encabezado: 'Acciones planificadas' },
    { clave: 'eficacia', encabezado: 'Eficacia' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'abierta', etiqueta: 'Abierta' },
    { valor: 'en_analisis', etiqueta: 'En análisis' },
    { valor: 'en_ejecucion', etiqueta: 'En ejecución' },
    { valor: 'verificacion', etiqueta: 'Verificación' },
    { valor: 'cerrada', etiqueta: 'Cerrada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<AccionCorrectiva[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  protected readonly formAlta = this.fb.nonNullable.group({
    responsableId: ['', [Validators.required]],
    noConformidadId: [''],
    causaRaiz: ['', [Validators.required]],
    accionesPlanificadas: ['', [Validators.required]],
  });

  constructor() {
    this.cargar();
  }

  /** Scorecard sobre la pagina cargada. */
  protected readonly resumen = computed(() => {
    const items = this.estado().datos ?? [];
    let abiertas = 0;
    let cerradas = 0;
    let porVerificar = 0;
    for (const a of items) {
      if (a.estado === 'cerrada') {
        cerradas += 1;
      } else {
        abiertas += 1;
        if (a.estado === 'verificacion' && !a.eficaciaVerificada) {
          porVerificar += 1;
        }
      }
    }
    return { abiertas, cerradas, porVerificar, total: items.length };
  });

  protected readonly composicionEstados = computed<MetricPoint[]>(() => {
    const r = this.resumen();
    return [
      { etiqueta: 'Abiertas', valor: r.abiertas },
      { etiqueta: 'Cerradas', valor: r.cerradas },
    ];
  });

  /** Etiquetas legibles de los 5 estados del ciclo de una accion correctiva. */
  private static readonly ETIQUETA_ESTADO: Record<string, string> = {
    abierta: 'Abierta',
    en_analisis: 'En análisis',
    en_ejecucion: 'En ejecución',
    verificacion: 'Verificación',
    cerrada: 'Cerrada',
  };

  /** Dona: composicion de acciones por su estado del ciclo (5 estados). */
  protected readonly composicionCiclo = computed<MetricPoint[]>(() => {
    const items = this.estado().datos ?? [];
    const cuenta = new Map<string, number>();
    for (const a of items) {
      cuenta.set(a.estado, (cuenta.get(a.estado) ?? 0) + 1);
    }
    return Object.keys(CalidadAccionesCorrectivas.ETIQUETA_ESTADO)
      .map((clave) => ({
        etiqueta: CalidadAccionesCorrectivas.ETIQUETA_ESTADO[clave],
        valor: cuenta.get(clave) ?? 0,
      }))
      .filter((p) => p.valor > 0);
  });

  /** Gauge: porcentaje de acciones cerradas sobre el total (0-100). */
  protected readonly porcentajeCerradas = computed<number>(() => {
    const r = this.resumen();
    if (r.total <= 0) {
      return 0;
    }
    return Math.max(0, Math.min(100, Math.round((r.cerradas / r.total) * 100)));
  });

  protected readonly hayDatos = computed(() => (this.estado().datos ?? []).length > 0);

  /** Siguiente estado de avance disponible (null si no aplica). */
  siguienteAvance(a: AccionCorrectiva): string | null {
    return this.puedeCambiarEstado ? (AVANCE_ACCION[a.estado] ?? null) : null;
  }

  /** Puede verificar eficacia cuando esta en verificacion y aun no verificada. */
  puedeVerificar(a: AccionCorrectiva): boolean {
    return this.puedeCambiarEstado && a.estado === 'verificacion' && !a.eficaciaVerificada;
  }

  /** Puede cerrar cuando la eficacia esta verificada y no esta cerrada. */
  puedeCerrar(a: AccionCorrectiva): boolean {
    return this.puedeCambiarEstado && a.eficaciaVerificada && a.estado !== 'cerrada';
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarAccionesCorrectivas(this.filtroEstado() || null, null, this.page(), this.size())
      .subscribe({
        next: (pagina) => {
          this.total.set(pagina.totalElements);
          this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
        },
        error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
      });
  }

  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  aplicarFiltro(valor: string): void {
    this.filtroEstado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  alternarAlta(): void {
    this.mostrarAlta.update((v) => !v);
  }

  crear(): void {
    if (this.formAlta.invalid) {
      this.formAlta.markAllAsTouched();
      return;
    }
    const v = this.formAlta.getRawValue();
    this.guardando.set(true);
    this.overlay
      .ejecutar(
        this.service.abrirAccionCorrectiva({
          responsableId: v.responsableId,
          noConformidadId: v.noConformidadId.trim() || null,
          causaRaiz: v.causaRaiz,
          accionesPlanificadas: v.accionesPlanificadas,
        }),
        { tipo: 'crear', textoProceso: 'Abriendo acción…', textoExito: 'Acción abierta' },
      )
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Acción correctiva abierta.');
          this.formAlta.reset({
            responsableId: '',
            noConformidadId: '',
            causaRaiz: '',
            accionesPlanificadas: '',
          });
          this.mostrarAlta.set(false);
          this.page.set(0);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  avanzar(a: AccionCorrectiva, destino: string): void {
    this.overlay
      .ejecutar(this.service.avanzarAccionCorrectiva(a.id, destino), {
        tipo: 'procesar',
        textoProceso: 'Actualizando acción…',
        textoExito: 'Acción actualizada',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Acción actualizada.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }

  verificar(a: AccionCorrectiva): void {
    this.overlay
      .ejecutar(this.service.verificarEficacia(a.id, null), {
        tipo: 'procesar',
        textoProceso: 'Verificando eficacia…',
        textoExito: 'Eficacia verificada',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Eficacia verificada.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }

  async cerrar(a: AccionCorrectiva): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Cerrar acción correctiva',
      mensaje: 'La acción se cerrará de forma definitiva. ¿Deseas continuar?',
      textoConfirmar: 'Cerrar acción',
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.service.cerrarAccionCorrectiva(a.id), {
        tipo: 'procesar',
        textoProceso: 'Cerrando acción…',
        textoExito: 'Acción cerrada',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Acción correctiva cerrada.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }
}
