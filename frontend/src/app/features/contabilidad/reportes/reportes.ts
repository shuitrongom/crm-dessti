// =============================================================================
// Vista de Reportes financieros (Req 39) — solo lectura
// -----------------------------------------------------------------------------
// Consulta de ingresos por periodo, IVA trasladado/retenido y libro de polizas
// paginado. Todos los importes son agregaciones del servidor (solo lectura); la
// UI unicamente selecciona el periodo y muestra los resultados con formato MXN.
// El permiso reporte_financiero:leer gobierna el acceso (deny-by-default).
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { ContabilidadService } from '../services/contabilidad.service';
import { IngresosPeriodo, IvaPeriodo, PolizaContable } from '../models/contabilidad.models';

@Component({
  selector: 'app-contabilidad-reportes',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatTabsModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './reportes.html',
  styleUrl: '../contabilidad.scss',
})
export class ContabilidadReportes {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ContabilidadService);

  protected readonly formPeriodo = this.fb.nonNullable.group({
    desde: ['', [Validators.required]],
    hasta: ['', [Validators.required]],
  });

  protected readonly ingresos = signal<IngresosPeriodo | null>(null);
  protected readonly iva = signal<IvaPeriodo | null>(null);
  protected readonly cargandoReportes = signal(false);
  protected readonly errorReportes = signal<string | null>(null);

  // Libro de polizas
  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'fecha', encabezado: 'Fecha' },
    { clave: 'tipo', encabezado: 'Tipo' },
    { clave: 'concepto', encabezado: 'Concepto' },
    { clave: 'cargos', encabezado: 'Cargos', alineacion: 'fin' },
    { clave: 'abonos', encabezado: 'Abonos', alineacion: 'fin' },
  ];
  protected readonly libro = signal<EstadoSolicitud<PolizaContable[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  constructor() {
    this.cargarLibro();
  }

  consultar(): void {
    if (this.formPeriodo.invalid) {
      this.formPeriodo.markAllAsTouched();
      return;
    }
    const { desde, hasta } = this.formPeriodo.getRawValue();
    this.cargandoReportes.set(true);
    this.errorReportes.set(null);

    this.service.reporteIngresos(desde, hasta, null).subscribe({
      next: (r) => this.ingresos.set(r),
      error: (e: HttpErrorResponse) => {
        this.cargandoReportes.set(false);
        this.errorReportes.set(mensajeDeError(e));
      },
    });
    this.service.reporteIva(desde, hasta, null).subscribe({
      next: (r) => {
        this.iva.set(r);
        this.cargandoReportes.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargandoReportes.set(false);
        this.errorReportes.set(mensajeDeError(e));
      },
    });
    this.cargarLibro();
  }

  cargarLibro(): void {
    this.libro.set(cargando());
    const { desde, hasta } = this.formPeriodo.getRawValue();
    this.service.libroPolizas(desde || null, hasta || null, null, this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.total.set(pagina.totalElements);
        this.libro.set(conDatos(pagina.content, pagina.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.libro.set(conError(mensajeDeError(e))),
    });
  }

  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargarLibro();
  }
}
