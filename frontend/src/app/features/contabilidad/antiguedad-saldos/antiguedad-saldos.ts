// =============================================================================
// Vista de Antigüedad de saldos (aging) CxC y CxP (Req 36.5, 42.5) — solo lectura
// -----------------------------------------------------------------------------
// Presenta en dos pestañas la antigüedad de saldos por cobrar (por cliente) y por
// pagar (por proveedor), clasificada en los rangos estándar 0-30, 31-60, 61-90 y
// más de 90 días. Un panel de KPIs resalta el saldo total y la cartera vencida
// (61-90 + más de 90 días), el indicador clave de riesgo de cobranza/pago. Todos
// los importes son agregaciones del servidor; la UI solo los formatea (MXN, es-MX).
// Gobernada por cuenta_por_cobrar:leer / cuenta_por_pagar:leer (deny-by-default).
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { ContabilidadService } from '../services/contabilidad.service';
import { RenglonAgingCliente, RenglonAgingProveedor } from '../models/contabilidad.models';

@Component({
  selector: 'app-contabilidad-antiguedad-saldos',
  imports: [
    CurrencyPipe,
    MatCardModule,
    MatIconModule,
    MatTabsModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './antiguedad-saldos.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../contabilidad.scss',
})
export class ContabilidadAntiguedadSaldos {
  private readonly service = inject(ContabilidadService);
  private readonly auth = inject(AuthService);

  protected readonly puedeVerCxC = this.auth.tienePermiso('cuenta_por_cobrar', 'leer');
  protected readonly puedeVerCxP = this.auth.tienePermiso('cuenta_por_pagar', 'leer');

  protected readonly columnasCxC: ColumnaTabla[] = [
    { clave: 'entidad', encabezado: 'Cliente' },
    { clave: 'rango0a30', encabezado: '0-30 días', alineacion: 'fin' },
    { clave: 'rango31a60', encabezado: '31-60 días', alineacion: 'fin' },
    { clave: 'rango61a90', encabezado: '61-90 días', alineacion: 'fin' },
    { clave: 'rangoMas90', encabezado: '+90 días', alineacion: 'fin' },
    { clave: 'saldoTotal', encabezado: 'Saldo total', alineacion: 'fin' },
  ];

  protected readonly columnasCxP: ColumnaTabla[] = [
    { clave: 'entidad', encabezado: 'Proveedor' },
    { clave: 'rango0a30', encabezado: '0-30 días', alineacion: 'fin' },
    { clave: 'rango31a60', encabezado: '31-60 días', alineacion: 'fin' },
    { clave: 'rango61a90', encabezado: '61-90 días', alineacion: 'fin' },
    { clave: 'rangoMas90', encabezado: '+90 días', alineacion: 'fin' },
    { clave: 'saldoTotal', encabezado: 'Saldo total', alineacion: 'fin' },
  ];

  protected readonly cxc = signal<EstadoSolicitud<RenglonAgingCliente[]>>(cargando());
  protected readonly cxp = signal<EstadoSolicitud<RenglonAgingProveedor[]>>(cargando());

  /** KPIs de CxC: saldo total y cartera vencida (61-90 + más de 90 días). */
  protected readonly kpisCxC = computed(() => this.kpis(this.cxc().datos ?? []));
  /** KPIs de CxP: saldo total y saldo vencido (61-90 + más de 90 días). */
  protected readonly kpisCxP = computed(() => this.kpis(this.cxp().datos ?? []));

  constructor() {
    if (this.puedeVerCxC) {
      this.cargarCxC();
    }
    if (this.puedeVerCxP) {
      this.cargarCxP();
    }
  }

  private kpis(filas: (RenglonAgingCliente | RenglonAgingProveedor)[]): {
    total: number;
    vencido: number;
  } {
    const total = filas.reduce((acc, f) => acc + f.saldoTotal, 0);
    const vencido = filas.reduce((acc, f) => acc + f.rango61a90 + f.rangoMas90, 0);
    return { total, vencido };
  }

  cargarCxC(): void {
    this.cxc.set(cargando());
    this.service.agingCxC(null).subscribe({
      next: (r) => this.cxc.set(conDatos(r.clientes, r.clientes.length === 0)),
      error: (e: HttpErrorResponse) => this.cxc.set(conError(mensajeDeError(e))),
    });
  }

  cargarCxP(): void {
    this.cxp.set(cargando());
    this.service.agingCxP(null).subscribe({
      next: (r) => this.cxp.set(conDatos(r.proveedores, r.proveedores.length === 0)),
      error: (e: HttpErrorResponse) => this.cxp.set(conError(mensajeDeError(e))),
    });
  }

  /** Etiqueta corta de la entidad (cliente/proveedor) por su id. */
  etiqueta(id: string): string {
    return id.slice(0, 8);
  }
}
