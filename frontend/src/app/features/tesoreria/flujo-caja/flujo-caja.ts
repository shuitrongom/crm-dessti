// =============================================================================
// Vista de Flujo de caja / posicion de liquidez (Req 43, suite Tesoreria)
// -----------------------------------------------------------------------------
// Dashboard ejecutivo de tesoreria: saldo bancario acumulado, entradas/salidas y
// flujo neto del periodo, cuentas activas y partidas por conciliar, mas una
// grafica de columnas del flujo mensual (entradas vs salidas). Solo lectura.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { MetricChart, type MetricPoint } from '../../../shared/components/metric-chart/metric-chart';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { DashboardSection } from '../../../shared/components/dashboard-section/dashboard-section';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { TesoreriaService } from '../services/tesoreria.service';
import { FlujoCaja } from '../models/tesoreria.models';

@Component({
  selector: 'app-tesoreria-flujo-caja',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    PageHeader,
    StateContainer,
    MetricChart,
    KpiTile,
    DashboardSection,
  ],
  templateUrl: './flujo-caja.html',
  styleUrl: '../tesoreria.scss',
})
export class TesoreriaFlujoCaja {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(TesoreriaService);

  protected readonly estado = signal<EstadoSolicitud<FlujoCaja>>(cargando());

  protected readonly formFiltro = this.fb.nonNullable.group({
    desde: [''],
    hasta: [''],
  });

  /** Puntos de la grafica: flujo mensual (entradas como valor, salidas como comparativo). */
  protected readonly puntos = computed<MetricPoint[]>(() => {
    const datos = this.estado().datos;
    if (!datos) {
      return [];
    }
    return datos.meses.map((m) => ({
      etiqueta: m.periodo,
      valor: m.entradas,
      comparativo: m.salidas,
      unidad: 'MXN',
    }));
  });

  /** Area/linea: flujo neto por mes (positivo = superavit). */
  protected readonly netoMensual = computed<MetricPoint[]>(() => {
    const datos = this.estado().datos;
    if (!datos) {
      return [];
    }
    return datos.meses.map((m) => ({ etiqueta: m.periodo, valor: m.neto, unidad: 'MXN' }));
  });

  /**
   * Gauge de "salud de liquidez": porcentaje que representa el flujo neto respecto a
   * las entradas del periodo (0-100). 100% = todo lo que entro se conservo; 0% o
   * negativo = se gasto todo o mas de lo que entro (se acota a 0).
   */
  protected readonly saludLiquidez = computed<number>(() => {
    const d = this.estado().datos;
    if (!d || d.entradasPeriodo <= 0) {
      return 0;
    }
    const pct = (d.flujoNetoPeriodo / d.entradasPeriodo) * 100;
    return Math.max(0, Math.min(100, Math.round(pct)));
  });

  constructor() {
    this.consultar();
  }

  private filtroActual() {
    const v = this.formFiltro.getRawValue();
    return { desde: v.desde || null, hasta: v.hasta || null };
  }

  consultar(): void {
    this.estado.set(cargando());
    const f = this.filtroActual();
    this.service.flujoCaja(f.desde, f.hasta).subscribe({
      next: (datos) => this.estado.set(conDatos(datos, (datos.meses?.length ?? 0) === 0)),
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }
}
