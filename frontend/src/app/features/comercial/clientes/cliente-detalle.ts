// =============================================================================
// Vista Ficha 360 del Cliente (Req 1, 7) — datos + actividad comercial conectada
// -----------------------------------------------------------------------------
// Reune, ademas de los datos del Cliente (solo lectura), su actividad comercial:
// una fila de indicadores (oportunidades abiertas, valor en pipeline, num. de
// cotizaciones y ultima cotizacion) y dos listas resumidas (Oportunidades y
// Cotizaciones) navegables a su detalle. Reutiliza los servicios existentes con
// el filtro `clienteId` (sin endpoints nuevos): los indicadores se CALCULAN en el
// cliente a partir de las listas cargadas. Cada seccion se OCULTA si el Usuario
// no tiene el permiso de listar el recurso (deny-by-default). No se exponen
// UUIDs: las entidades se muestran por nombre/titulo/folio. El backend NO expone
// un listado de Contactos por Cliente, por lo que esa seccion muestra una nota y
// no inventa un endpoint inexistente.
// =============================================================================

import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { StatCard } from '../../../shared/components/stat-card/stat-card';
import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ClientesService } from '../services/clientes.service';
import { OportunidadesService } from '../services/oportunidades.service';
import { CotizacionesService } from '../services/cotizaciones.service';
import {
  Cliente,
  Cotizacion,
  ETIQUETA_ESTADO_COTIZACION,
  ETIQUETA_ETAPA,
  ETIQUETA_TIPO_PERSONA,
  Oportunidad,
} from '../models/comercial.models';

/** Etapas terminales del pipeline: una Oportunidad en ellas NO cuenta como abierta. */
const ETAPAS_CERRADAS: ReadonlySet<string> = new Set(['ganado', 'perdido']);

/** Tamano de pagina para las listas resumidas de la ficha (resumen, no paginacion). */
const TAMANO_RESUMEN = 20;

@Component({
  selector: 'app-comercial-cliente-detalle',
  imports: [
    RouterLink,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    StatCard,
    EstadoChip,
  ],
  templateUrl: './cliente-detalle.html',
  styleUrl: './cliente-detalle.scss',
})
export class ComercialClienteDetalle implements OnInit {
  /** Identificador del Cliente tomado de la ruta (:id). Nunca se muestra al Usuario. */
  readonly id = input.required<string>();

  private readonly clientesService = inject(ClientesService);
  private readonly oportunidadesService = inject(OportunidadesService);
  private readonly cotizacionesService = inject(CotizacionesService);
  private readonly auth = inject(AuthService);

  // Gating por permiso (deny-by-default): cada seccion relacionada solo se
  // renderiza si el Usuario puede listar el recurso correspondiente (Req 1.4).
  protected readonly puedeListarOportunidades = this.auth.tienePermiso('oportunidad', 'listar');
  protected readonly puedeListarCotizaciones = this.auth.tienePermiso('cotizacion', 'listar');

  protected readonly etiquetaEtapa = ETIQUETA_ETAPA;
  protected readonly etiquetaEstadoCotizacion = ETIQUETA_ESTADO_COTIZACION;
  protected readonly tonoEstado = tonoDeEstado;

  // Estado de la cabecera (datos del Cliente).
  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly cliente = signal<Cliente | null>(null);

  // Estado de la seccion de Oportunidades.
  protected readonly faseOportunidades = signal<FaseSolicitud>('cargando');
  protected readonly errorOportunidades = signal<string | undefined>(undefined);
  protected readonly oportunidades = signal<Oportunidad[]>([]);

  // Estado de la seccion de Cotizaciones.
  protected readonly faseCotizaciones = signal<FaseSolicitud>('cargando');
  protected readonly errorCotizaciones = signal<string | undefined>(undefined);
  protected readonly cotizaciones = signal<Cotizacion[]>([]);

  /** Titulo del encabezado con el nombre del Cliente (o generico mientras carga). */
  protected readonly titulo = computed(() => {
    const c = this.cliente();
    return c ? c.nombre : 'Ficha del cliente';
  });

  /** Etiqueta legible del tipo de persona para la cabecera de datos. */
  protected readonly tipoPersonaEtiqueta = computed(() => {
    const tipo = this.cliente()?.tipoPersona;
    return tipo ? ETIQUETA_TIPO_PERSONA[tipo] : null;
  });

  /** Direccion del Cliente unida en una linea legible (o null si esta vacia). */
  protected readonly direccion = computed(() => {
    const c = this.cliente();
    if (!c) {
      return null;
    }
    const partes = [
      c.direccionCalle,
      c.direccionCiudad,
      c.direccionEstado,
      c.direccionCp,
      c.direccionPais,
    ].filter((p): p is string => !!p && p.trim().length > 0);
    return partes.length > 0 ? partes.join(', ') : null;
  });

  // ---------------------------------------------------------------------------
  // Indicadores (Req 1.3) — se calculan en el cliente a partir de las listas.
  // ---------------------------------------------------------------------------

  /** Oportunidades abiertas: las que no estan en una etapa terminal. */
  protected readonly oportunidadesAbiertas = computed(() =>
    this.oportunidades().filter((o) => !ETAPAS_CERRADAS.has(o.etapa)),
  );

  /** Numero de oportunidades abiertas. */
  protected readonly numOportunidadesAbiertas = computed(
    () => this.oportunidadesAbiertas().length,
  );

  /** Valor en pipeline: suma del valor estimado de las oportunidades abiertas. */
  protected readonly valorPipeline = computed(() =>
    this.oportunidadesAbiertas().reduce((acc, o) => acc + (Number(o.valorEstimado) || 0), 0),
  );

  /** Valor en pipeline ya formateado como moneda es-MX/MXN para el indicador. */
  protected readonly valorPipelineTexto = computed(() =>
    this.valorPipeline().toLocaleString('es-MX', {
      style: 'currency',
      currency: 'MXN',
      maximumFractionDigits: 0,
    }),
  );

  /** Numero total de cotizaciones cargadas del cliente. */
  protected readonly numCotizaciones = computed(() => this.cotizaciones().length);

  /**
   * Ultima cotizacion del cliente: la mas reciente por fecha de emision (o de
   * creacion como respaldo). Se usa para el indicador "Ultima cotizacion".
   */
  protected readonly ultimaCotizacion = computed<Cotizacion | null>(() => {
    const lista = this.cotizaciones();
    if (lista.length === 0) {
      return null;
    }
    return [...lista].sort((a, b) => this.fechaOrden(b) - this.fechaOrden(a))[0];
  });

  /** Texto del indicador "Ultima cotizacion": folio (o guion si no hay datos). */
  protected readonly ultimaCotizacionFolio = computed(() => {
    const c = this.ultimaCotizacion();
    return c?.folio ?? (c ? 'Sin folio' : '—');
  });

  ngOnInit(): void {
    this.cargar();
  }

  /** Carga los datos del Cliente y dispara la carga de sus secciones relacionadas. */
  cargar(): void {
    this.fase.set('cargando');
    this.clientesService.consultar(this.id()).subscribe({
      next: (cliente) => {
        this.cliente.set(cliente);
        this.fase.set('ok');
        this.cargarOportunidades();
        this.cargarCotizaciones();
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Carga las Oportunidades del Cliente (solo si el permiso lo permite, Req 1.4). */
  cargarOportunidades(): void {
    if (!this.puedeListarOportunidades) {
      return;
    }
    this.faseOportunidades.set('cargando');
    this.oportunidadesService.listar({ clienteId: this.id() }, 0, TAMANO_RESUMEN).subscribe({
      next: (pagina) => {
        this.oportunidades.set(pagina.content);
        this.faseOportunidades.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.errorOportunidades.set(mensajeDeError(e));
        this.faseOportunidades.set('error');
      },
    });
  }

  /** Carga las Cotizaciones del Cliente (solo si el permiso lo permite, Req 1.4). */
  cargarCotizaciones(): void {
    if (!this.puedeListarCotizaciones) {
      return;
    }
    this.faseCotizaciones.set('cargando');
    this.cotizacionesService.listar({ clienteId: this.id() }, 0, TAMANO_RESUMEN).subscribe({
      next: (pagina) => {
        this.cotizaciones.set(pagina.content);
        this.faseCotizaciones.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.errorCotizaciones.set(mensajeDeError(e));
        this.faseCotizaciones.set('error');
      },
    });
  }

  /** Clave temporal de una Cotizacion para ordenar (emision preferida; creacion de respaldo). */
  private fechaOrden(cotizacion: Cotizacion): number {
    const referencia = cotizacion.fechaEmision ?? cotizacion.createdAt;
    const tiempo = referencia ? new Date(referencia).getTime() : 0;
    return Number.isNaN(tiempo) ? 0 : tiempo;
  }
}
