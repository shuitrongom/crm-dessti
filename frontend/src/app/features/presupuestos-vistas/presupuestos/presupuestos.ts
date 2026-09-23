// =============================================================================
// Vista de negocio de Presupuestos (Req 62)
// -----------------------------------------------------------------------------
// Listado paginado filtrable por area y periodo; alta de presupuesto; y consulta
// de la variacion real vs estimado (importe y porcentaje) de solo lectura, que el
// servidor calcula (VariacionPresupuestoDto) e indica favorable/desfavorable y si
// supera el umbral (se destaca la desviacion). Los importes los calcula el
// servidor; la UI solo los formatea (currency es-MX). Cada operacion se gobierna
// por permiso atomico (deny-by-default).
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { PresupuestosVistasService } from '../services/presupuestos-vistas.service';
import { Presupuesto, VariacionPresupuesto } from '../models/presupuestos.models';

@Component({
  selector: 'app-presupuestos-vistas',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DecimalPipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './presupuestos.html',
  styleUrl: '../presupuestos.scss',
})
export class PresupuestosVistas {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PresupuestosVistasService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('presupuesto', 'crear');
  protected readonly puedeLeer = this.auth.tienePermiso('presupuesto', 'leer');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'area', encabezado: 'Area' },
    { clave: 'periodo', encabezado: 'Periodo' },
    { clave: 'ingresos', encabezado: 'Ingresos estimados', alineacion: 'fin' },
    { clave: 'egresos', encabezado: 'Egresos estimados', alineacion: 'fin' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estado = signal<EstadoSolicitud<Presupuesto[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroArea = signal('');
  protected readonly filtroPeriodo = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);
  protected readonly variacion = signal<VariacionPresupuesto | null>(null);
  protected readonly cargandoVariacion = signal(false);

  protected readonly formAlta = this.fb.nonNullable.group({
    area: ['', [Validators.required]],
    periodo: ['', [Validators.required, Validators.maxLength(7)]],
    ingresosEstimados: [0, [Validators.required, Validators.min(0)]],
    egresosEstimados: [0, [Validators.required, Validators.min(0)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listar(this.filtroArea() || null, this.filtroPeriodo() || null, this.page(), this.size())
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

  aplicarFiltroArea(valor: string): void {
    this.filtroArea.set(valor.trim());
    this.page.set(0);
    this.cargar();
  }

  aplicarFiltroPeriodo(valor: string): void {
    this.filtroPeriodo.set(valor.trim());
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
    this.service.crear(v).subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito('Presupuesto creado.');
        this.formAlta.reset({ area: '', periodo: '', ingresosEstimados: 0, egresosEstimados: 0 });
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

  verVariacion(presupuesto: Presupuesto): void {
    this.variacion.set(null);
    this.cargandoVariacion.set(true);
    this.service.consultarVariacion(presupuesto.id).subscribe({
      next: (v) => {
        this.cargandoVariacion.set(false);
        this.variacion.set(v);
      },
      error: (e: HttpErrorResponse) => {
        this.cargandoVariacion.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  cerrarVariacion(): void {
    this.variacion.set(null);
  }
}
