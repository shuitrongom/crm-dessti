// =============================================================================
// Vista del Tablero de indicadores por area (Req 22)
// -----------------------------------------------------------------------------
// Foto de solo lectura de los indicadores agrupados por area, con filtro por
// periodo (desde/hasta). Reutiliza IndicatorCard (que ya muestra valor, unidad y
// comparativo/tendencia accesible). Estados de carga/vacio/error via
// StateContainer. La exportacion se ofrece si el Usuario tiene el permiso.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { IndicatorCard } from '../../../shared/components/indicator-card/indicator-card';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { TableroService } from '../services/tablero.service';
import { Tablero as TableroModel } from '../models/reportes.models';
import { humanizarArea } from '../areas-etiquetas';

@Component({
  selector: 'app-tablero',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    PageHeader,
    StateContainer,
    IndicatorCard,
  ],
  templateUrl: './tablero.html',
  styleUrl: './tablero.scss',
})
export class Tablero {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(TableroService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly humanizarArea = humanizarArea;
  protected readonly puedeExportar = this.auth.tienePermiso('reporte', 'exportar');

  protected readonly estado = signal<EstadoSolicitud<TableroModel>>(cargando());
  protected readonly exportando = signal(false);

  protected readonly formFiltro = this.fb.nonNullable.group({
    desde: [''],
    hasta: [''],
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
    this.service.consultar(this.filtroActual()).subscribe({
      next: (tablero) => this.estado.set(conDatos(tablero, (tablero.areas?.length ?? 0) === 0)),
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  exportar(): void {
    this.exportando.set(true);
    this.service.exportar(this.filtroActual()).subscribe({
      next: (tablero) => {
        this.exportando.set(false);
        const blob = new Blob([JSON.stringify(tablero, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = 'tablero-indicadores.json';
        enlace.click();
        URL.revokeObjectURL(url);
        this.toast.exito('Tablero exportado.');
      },
      error: (e: HttpErrorResponse) => {
        this.exportando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
