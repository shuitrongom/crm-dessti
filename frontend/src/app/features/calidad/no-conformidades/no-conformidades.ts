// =============================================================================
// Vista de No conformidades del SGC (Req 70.2, clausula 10.2)
// -----------------------------------------------------------------------------
// Listado paginado con filtro por origen/estado; registro de una no conformidad;
// y avance de estado (abierta -> en_tratamiento -> cerrada). Dashboard con
// scorecard por estado. Acciones gobernadas por permiso atomico.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
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
import { NoConformidad, ORIGENES_NO_CONFORMIDAD } from '../models/calidad.models';

/** Transiciones de estado permitidas desde el estado actual de una No_Conformidad. */
const TRANSICIONES_NC: Record<string, string[]> = {
  abierta: ['en_tratamiento', 'cerrada'],
  en_tratamiento: ['cerrada'],
  cerrada: [],
};

@Component({
  selector: 'app-calidad-no-conformidades',
  imports: [
    ReactiveFormsModule,
    DatePipe,
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
  templateUrl: './no-conformidades.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../calidad.scss',
})
export class CalidadNoConformidades {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CalidadService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;
  protected readonly origenes = ORIGENES_NO_CONFORMIDAD;

  protected readonly puedeCrear = this.auth.tienePermiso('no_conformidad', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso(
    'no_conformidad',
    'cambiar_estado',
  );

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'descripcion', encabezado: 'No conformidad' },
    { clave: 'origen', encabezado: 'Origen' },
    { clave: 'proceso', encabezado: 'Proceso afectado' },
    { clave: 'detectada', encabezado: 'Detectada' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'abierta', etiqueta: 'Abierta' },
    { valor: 'en_tratamiento', etiqueta: 'En tratamiento' },
    { valor: 'cerrada', etiqueta: 'Cerrada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<NoConformidad[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  protected readonly formAlta = this.fb.nonNullable.group({
    origen: ['proceso', [Validators.required]],
    descripcion: ['', [Validators.required]],
    procesoAfectado: ['', [Validators.required]],
  });

  constructor() {
    this.cargar();
  }

  /** Scorecard por estado sobre la pagina cargada. */
  protected readonly resumen = computed(() => {
    const items = this.estado().datos ?? [];
    let abiertas = 0;
    let enTratamiento = 0;
    let cerradas = 0;
    for (const nc of items) {
      if (nc.estado === 'abierta') {
        abiertas += 1;
      } else if (nc.estado === 'en_tratamiento') {
        enTratamiento += 1;
      } else {
        cerradas += 1;
      }
    }
    return { abiertas, enTratamiento, cerradas, total: items.length };
  });

  protected readonly composicionEstados = computed<MetricPoint[]>(() => {
    const r = this.resumen();
    return [
      { etiqueta: 'Abiertas', valor: r.abiertas },
      { etiqueta: 'En tratamiento', valor: r.enTratamiento },
      { etiqueta: 'Cerradas', valor: r.cerradas },
    ];
  });

  /** Dona: composicion de no conformidades por origen. */
  protected readonly composicionOrigen = computed<MetricPoint[]>(() => {
    const items = this.estado().datos ?? [];
    const cuenta = new Map<string, number>();
    for (const nc of items) {
      cuenta.set(nc.origen, (cuenta.get(nc.origen) ?? 0) + 1);
    }
    return ORIGENES_NO_CONFORMIDAD.map((o) => ({
      etiqueta: o.etiqueta,
      valor: cuenta.get(o.valor) ?? 0,
    })).filter((p) => p.valor > 0);
  });

  /** Gauge: porcentaje de no conformidades cerradas sobre el total (0-100). */
  protected readonly porcentajeCerradas = computed<number>(() => {
    const r = this.resumen();
    if (r.total <= 0) {
      return 0;
    }
    return Math.max(0, Math.min(100, Math.round((r.cerradas / r.total) * 100)));
  });

  protected readonly hayDatos = computed(() => (this.estado().datos ?? []).length > 0);

  transiciones(nc: NoConformidad): string[] {
    return this.puedeCambiarEstado ? (TRANSICIONES_NC[nc.estado] ?? []) : [];
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarNoConformidades(null, this.filtroEstado() || null, this.page(), this.size())
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
        this.service.registrarNoConformidad({
          origen: v.origen as NoConformidad['origen'],
          descripcion: v.descripcion,
          procesoAfectado: v.procesoAfectado,
        }),
        { tipo: 'crear', textoProceso: 'Registrando…', textoExito: 'No conformidad registrada' },
      )
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('No conformidad registrada.');
          this.formAlta.reset({ origen: 'proceso', descripcion: '', procesoAfectado: '' });
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

  cambiarEstado(nc: NoConformidad, destino: string): void {
    this.overlay
      .ejecutar(this.service.cambiarEstadoNoConformidad(nc.id, destino), {
        tipo: 'procesar',
        textoProceso: 'Actualizando…',
        textoExito: 'No conformidad actualizada',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Estado actualizado.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }
}
