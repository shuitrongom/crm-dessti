// =============================================================================
// Vista de Cotizaciones (Req 6) — listado paginado con filtros
// -----------------------------------------------------------------------------
// Lista paginada (DataTable) con filtro por estado; enlaza al detalle de cada
// Cotizacion y a la creacion de una nueva. Gestiona estados con StateContainer.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { CotizacionesService } from '../services/cotizaciones.service';
import {
  Cotizacion,
  ETIQUETA_ESTADO_COTIZACION,
  EstadoCotizacion,
  MONEDA_POR_DEFECTO,
} from '../models/comercial.models';

@Component({
  selector: 'app-comercial-cotizaciones',
  imports: [
    RouterLink,
    FormsModule,
    CurrencyPipe,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    DatePipe,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './cotizaciones.html',
  styleUrl: './cotizaciones.scss',
})
export class ComercialCotizaciones {
  private readonly service = inject(CotizacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('cotizacion', 'crear');
  protected readonly tonoEstado = tonoDeEstado;
  protected readonly monedaPorDefecto = MONEDA_POR_DEFECTO;
  private readonly mapaEstado = ETIQUETA_ESTADO_COTIZACION;
  protected readonly estados: EstadoCotizacion[] = ['borrador', 'enviada', 'aprobada', 'rechazada'];

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly cotizaciones = signal<Cotizacion[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly estado = signal<EstadoCotizacion | ''>('');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'folio', encabezado: 'Folio' },
    { clave: 'cliente', encabezado: 'Cliente' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'fecha', encabezado: 'Emision' },
    { clave: 'total', encabezado: 'Total', alineacion: 'fin' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  // ---------------------------------------------------------------------------
  // Indicadores (KPIs) — patron enterprise: outcome-first. El conteo usa el total
  // paginado (`total()`); el valor y los desgloses por estado se calculan sobre
  // la pagina cargada (el backend pagina el listado), con etiquetas honestas.
  // ---------------------------------------------------------------------------

  /** Valor total de las Cotizaciones de la pagina cargada (campo `total` del DTO). */
  protected readonly valorPagina = computed<number>(() =>
    this.cotizaciones().reduce((acc, c) => acc + (Number(c.total) || 0), 0),
  );

  /** Numero de Cotizaciones en borrador en la pagina cargada. */
  protected readonly cotizacionesBorrador = computed<number>(
    () => this.cotizaciones().filter((c) => c.estado === 'borrador').length,
  );

  /** Numero de Cotizaciones enviadas en la pagina cargada. */
  protected readonly cotizacionesEnviadas = computed<number>(
    () => this.cotizaciones().filter((c) => c.estado === 'enviada').length,
  );

  constructor() {
    this.cargar();
  }

  /** Etiqueta legible del estado de la cotizacion; devuelve el valor crudo si no mapea. */
  protected etiquetaEstado(estado: string): string {
    return this.mapaEstado[estado as EstadoCotizacion] ?? estado;
  }

  /** Carga la pagina actual de Cotizaciones aplicando el filtro por estado. */
  cargar(): void {
    this.fase.set('cargando');
    const estado = this.estado() || null;
    this.service.listar({ estado }, this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.cotizaciones.set(pagina.content);
        this.total.set(pagina.totalElements);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Cambia el filtro de estado reiniciando a la primera pagina. */
  cambiarEstado(valor: EstadoCotizacion | ''): void {
    this.estado.set(valor);
    this.page.set(0);
    this.cargar();
  }

  /** Cambia de pagina/tamano y recarga. */
  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }
}
