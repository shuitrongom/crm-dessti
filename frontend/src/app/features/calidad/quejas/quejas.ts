// =============================================================================
// Vista de Quejas de cliente del SGC (Req 70.1, clausula 10.2)
// -----------------------------------------------------------------------------
// Listado paginado con filtro por origen/estado; registro de una queja; vinculo
// a una accion correctiva; y atencion (cierre). Dashboard con scorecard por
// estado. Acciones gobernadas por permiso atomico (queja_cliente:*).
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
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
import { ORIGENES_QUEJA, Queja } from '../models/calidad.models';

@Component({
  selector: 'app-calidad-quejas',
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
  templateUrl: './quejas.html',
  styleUrl: '../calidad.scss',
})
export class CalidadQuejas {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CalidadService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;
  protected readonly origenes = ORIGENES_QUEJA;

  protected readonly puedeCrear = this.auth.tienePermiso('queja_cliente', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('queja_cliente', 'cambiar_estado');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'descripcion', encabezado: 'Queja' },
    { clave: 'origen', encabezado: 'Origen' },
    { clave: 'registrada', encabezado: 'Registrada' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'registrada', etiqueta: 'Registrada' },
    { valor: 'vinculada', etiqueta: 'Vinculada' },
    { valor: 'atendida', etiqueta: 'Atendida' },
  ];

  protected readonly estado = signal<EstadoSolicitud<Queja[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  protected readonly formAlta = this.fb.nonNullable.group({
    clienteId: ['', [Validators.required]],
    origen: ['correo', [Validators.required]],
    descripcion: ['', [Validators.required]],
  });

  constructor() {
    this.cargar();
  }

  protected readonly resumen = computed(() => {
    const items = this.estado().datos ?? [];
    let registradas = 0;
    let vinculadas = 0;
    let atendidas = 0;
    for (const q of items) {
      if (q.estado === 'registrada') {
        registradas += 1;
      } else if (q.estado === 'vinculada') {
        vinculadas += 1;
      } else {
        atendidas += 1;
      }
    }
    return { registradas, vinculadas, atendidas, total: items.length };
  });

  protected readonly composicionEstados = computed<MetricPoint[]>(() => {
    const r = this.resumen();
    return [
      { etiqueta: 'Registradas', valor: r.registradas },
      { etiqueta: 'Vinculadas', valor: r.vinculadas },
      { etiqueta: 'Atendidas', valor: r.atendidas },
    ];
  });

  /** Dona: composicion de quejas por canal de origen. */
  protected readonly composicionOrigen = computed<MetricPoint[]>(() => {
    const items = this.estado().datos ?? [];
    const cuenta = new Map<string, number>();
    for (const q of items) {
      cuenta.set(q.origen, (cuenta.get(q.origen) ?? 0) + 1);
    }
    return ORIGENES_QUEJA
      .map((o) => ({ etiqueta: o.etiqueta, valor: cuenta.get(o.valor) ?? 0 }))
      .filter((p) => p.valor > 0);
  });

  /** Gauge: porcentaje de quejas atendidas sobre el total (0-100). */
  protected readonly porcentajeAtendidas = computed<number>(() => {
    const r = this.resumen();
    if (r.total <= 0) {
      return 0;
    }
    return Math.max(0, Math.min(100, Math.round((r.atendidas / r.total) * 100)));
  });

  protected readonly hayDatos = computed(() => (this.estado().datos ?? []).length > 0);

  puedeAtender(q: Queja): boolean {
    return this.puedeCambiarEstado && q.estado !== 'atendida';
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarQuejas(null, this.filtroEstado() || null, this.page(), this.size())
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
        this.service.registrarQueja({
          clienteId: v.clienteId,
          origen: v.origen as Queja['origen'],
          canalSocialId: null,
          descripcion: v.descripcion,
        }),
        { tipo: 'crear', textoProceso: 'Registrando queja…', textoExito: 'Queja registrada' },
      )
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Queja registrada.');
          this.formAlta.reset({ clienteId: '', origen: 'correo', descripcion: '' });
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

  atender(q: Queja): void {
    this.overlay
      .ejecutar(this.service.atenderQueja(q.id), {
        tipo: 'procesar',
        textoProceso: 'Atendiendo queja…',
        textoExito: 'Queja atendida',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Queja atendida.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }
}
