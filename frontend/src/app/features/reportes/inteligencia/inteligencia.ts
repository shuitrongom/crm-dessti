// =============================================================================
// Vista de Inteligencia de Negocio consolidada (Req 48)
// -----------------------------------------------------------------------------
// Analisis consolidado de solo lectura por area con comparativos de periodo:
// muestra el valor actual, el valor del periodo anterior, la variacion absoluta
// y la variacion porcentual con una tendencia accesible (icono + texto, no solo
// color). Filtros por periodo, area y dimension. Exportacion si hay permiso.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { InteligenciaNegocioService } from '../services/inteligencia-negocio.service';
import { Indicador, InteligenciaNegocio } from '../models/reportes.models';
import { Comparativo, comparativoDe } from '../comparativo';
import { humanizarArea } from '../areas-etiquetas';

@Component({
  selector: 'app-inteligencia',
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    PageHeader,
    StateContainer,
  ],
  templateUrl: './inteligencia.html',
  styleUrl: './inteligencia.scss',
})
export class Inteligencia {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(InteligenciaNegocioService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly humanizarArea = humanizarArea;
  protected readonly puedeExportar = this.auth.tienePermiso('inteligencia_negocio', 'exportar');

  protected readonly estado = signal<EstadoSolicitud<InteligenciaNegocio>>(cargando());
  protected readonly exportando = signal(false);

  protected readonly formFiltro = this.fb.nonNullable.group({
    desde: [''],
    hasta: [''],
    area: [''],
    dimension: [''],
  });

  constructor() {
    this.consultar();
  }

  /** Comparativo derivado (tendencia, porcentaje, icono, texto) de un indicador. */
  comparativo(indicador: Indicador): Comparativo {
    return comparativoDe(indicador);
  }

  /** Clase de tono de la tendencia para el estilo (acompana siempre al texto). */
  claseTendencia(indicador: Indicador): string {
    return `inteligencia-delta--${comparativoDe(indicador).tendencia}`;
  }

  private filtroActual() {
    const v = this.formFiltro.getRawValue();
    return {
      desde: v.desde || null,
      hasta: v.hasta || null,
      area: v.area || null,
      dimension: v.dimension || null,
    };
  }

  consultar(): void {
    this.estado.set(cargando());
    this.service.consolidado(this.filtroActual()).subscribe({
      next: (datos) => this.estado.set(conDatos(datos, (datos.areas?.length ?? 0) === 0)),
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  exportar(): void {
    this.exportando.set(true);
    this.service.exportarConsolidado(this.filtroActual()).subscribe({
      next: (datos) => {
        this.exportando.set(false);
        const blob = new Blob([JSON.stringify(datos, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = 'inteligencia-negocio.json';
        enlace.click();
        URL.revokeObjectURL(url);
        this.toast.exito('Analisis exportado.');
      },
      error: (e: HttpErrorResponse) => {
        this.exportando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
