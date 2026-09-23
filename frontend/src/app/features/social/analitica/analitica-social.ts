// =============================================================================
// Vista de Analitica social (Req 66)
// -----------------------------------------------------------------------------
// Metricas de solo lectura por Canal_Social y periodo (filtros desde/hasta y
// canal): alcance, interacciones, mensajes recibidos/enviados, tiempo de
// respuesta promedio y conversiones. Ofrece la accion de exportar (mismo resumen
// del backend). No modifica datos de origen (Req 66.1).
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

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

import { AnaliticaSocialService, FiltroAnalitica } from '../services/analitica-social.service';
import { MetricasSociales, ResumenAnaliticaSocial } from '../models/social.models';
import { ETIQUETA_CANAL, ICONO_CANAL, OPCIONES_CANAL } from '../social-etiquetas';

@Component({
  selector: 'app-analitica-social',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
  ],
  templateUrl: './analitica-social.html',
  styleUrl: './analitica-social.scss',
})
export class AnaliticaSocial {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(AnaliticaSocialService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeExportar = this.auth.tienePermiso('analitica_social', 'leer');

  // Filtro por canal derivado del origen unico (los cinco canales) mas "todos".
  protected readonly canales = [
    { valor: '', etiqueta: 'Todos los canales' },
    ...OPCIONES_CANAL,
  ];

  protected readonly estado = signal<EstadoSolicitud<ResumenAnaliticaSocial>>(cargando());
  protected readonly exportando = signal(false);

  protected readonly formFiltro = this.fb.nonNullable.group({
    desde: [''],
    hasta: [''],
    canal: [''],
  });

  /** Totales agregados de todos los canales del resumen actual. */
  protected readonly totales = computed(() => {
    const datos = this.estado().datos;
    const canales = datos?.canales ?? [];
    return canales.reduce(
      (acc, c) => ({
        alcance: acc.alcance + c.alcance,
        interacciones: acc.interacciones + c.interacciones,
        mensajesRecibidos: acc.mensajesRecibidos + c.mensajesRecibidos,
        mensajesEnviados: acc.mensajesEnviados + c.mensajesEnviados,
        conversiones: acc.conversiones + c.conversiones,
      }),
      { alcance: 0, interacciones: 0, mensajesRecibidos: 0, mensajesEnviados: 0, conversiones: 0 },
    );
  });

  constructor() {
    this.consultar();
  }

  etiquetaCanal(valor: string): string {
    return ETIQUETA_CANAL[valor as keyof typeof ETIQUETA_CANAL] ?? valor;
  }

  iconoCanal(valor: string): string {
    return ICONO_CANAL[valor as keyof typeof ICONO_CANAL] ?? 'public';
  }

  /** Formatea segundos como "m min s s" para el tiempo de respuesta promedio. */
  tiempoRespuesta(segundos: number): string {
    if (segundos <= 0) {
      return 'Sin datos';
    }
    const min = Math.floor(segundos / 60);
    const s = Math.round(segundos % 60);
    return min > 0 ? `${min} min ${s} s` : `${s} s`;
  }

  private filtroActual(): FiltroAnalitica {
    const v = this.formFiltro.getRawValue();
    return {
      desde: v.desde || null,
      hasta: v.hasta || null,
      canal: v.canal || null,
    };
  }

  consultar(): void {
    this.estado.set(cargando());
    this.service.metricas(this.filtroActual()).subscribe({
      next: (resumen) =>
        this.estado.set(conDatos(resumen, (resumen.canales?.length ?? 0) === 0)),
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  exportar(): void {
    this.exportando.set(true);
    this.service.exportar(this.filtroActual()).subscribe({
      next: (resumen) => {
        this.exportando.set(false);
        this.descargar(resumen);
        this.toast.exito('Analitica exportada.');
      },
      error: (e: HttpErrorResponse) => {
        this.exportando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /** Descarga el resumen como archivo JSON en el navegador. */
  private descargar(resumen: ResumenAnaliticaSocial): void {
    const blob = new Blob([JSON.stringify(resumen, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = 'analitica-social.json';
    enlace.click();
    URL.revokeObjectURL(url);
  }

  /** Acceso tipado a la lista de canales del resumen (para la plantilla). */
  canalesDe(resumen: ResumenAnaliticaSocial): MetricasSociales[] {
    return resumen.canales ?? [];
  }
}
