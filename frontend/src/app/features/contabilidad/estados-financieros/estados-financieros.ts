// =============================================================================
// Vista de Estados Financieros (Req 47) — solo lectura
// -----------------------------------------------------------------------------
// Presenta los tres estados financieros del periodo en pestañas: Balance General
// (con la ecuación contable activo = pasivo + capital), Estado de Resultados
// (ingresos - gastos = utilidad) y Balanza de Comprobación (detalle por cuenta con
// gran total de cargos y abonos). Todos son agregaciones del servidor (Property 17);
// la UI solo elige el periodo y formatea los importes (MXN, es-MX). Cada estado
// muestra una insignia de "cuadra / no cuadra" para señalar la integridad contable.
// El permiso estado_financiero:leer gobierna el acceso (deny-by-default).
// =============================================================================

import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { ContabilidadService } from '../services/contabilidad.service';
import {
  BalanceGeneral,
  BalanzaComprobacion,
  EstadoResultados,
} from '../models/contabilidad.models';

@Component({
  selector: 'app-contabilidad-estados-financieros',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    MatTabsModule,
    PageHeader,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './estados-financieros.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../contabilidad.scss',
})
export class ContabilidadEstadosFinancieros {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ContabilidadService);

  protected readonly formPeriodo = this.fb.nonNullable.group({
    desde: [''],
    hasta: [''],
  });

  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly balance = signal<BalanceGeneral | null>(null);
  protected readonly resultados = signal<EstadoResultados | null>(null);
  protected readonly balanza = signal<BalanzaComprobacion | null>(null);

  protected readonly columnasBalanza: ColumnaTabla[] = [
    { clave: 'codigo', encabezado: 'Código' },
    { clave: 'nombre', encabezado: 'Cuenta' },
    { clave: 'cargos', encabezado: 'Cargos', alineacion: 'fin' },
    { clave: 'abonos', encabezado: 'Abonos', alineacion: 'fin' },
  ];

  constructor() {
    this.consultar();
  }

  consultar(): void {
    const { desde, hasta } = this.formPeriodo.getRawValue();
    const d = desde || null;
    const h = hasta || null;
    this.cargando.set(true);
    this.error.set(null);

    this.service.balanceGeneral(d, h).subscribe({
      next: (r) => this.balance.set(r),
      error: (e: HttpErrorResponse) => this.fallo(e),
    });
    this.service.estadoResultados(d, h).subscribe({
      next: (r) => this.resultados.set(r),
      error: (e: HttpErrorResponse) => this.fallo(e),
    });
    this.service.balanzaComprobacion(d, h).subscribe({
      next: (r) => {
        this.balanza.set(r);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => this.fallo(e),
    });
  }

  private fallo(e: HttpErrorResponse): void {
    this.cargando.set(false);
    this.error.set(mensajeDeError(e));
  }
}
