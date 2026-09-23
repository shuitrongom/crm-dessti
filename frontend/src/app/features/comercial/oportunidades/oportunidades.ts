// =============================================================================
// Vista de Oportunidades / pipeline (Req 14, 63) — tablero por etapa (kanban)
// -----------------------------------------------------------------------------
// Presenta las Oportunidades agrupadas por etapa del embudo (nuevo -> ... ->
// ganado/perdido). Cada tarjeta ofrece SOLO las transiciones validas segun la
// maquina de estados (una transicion invalida no se muestra; el backend la
// rechazaria con 409). Permite el alta y la conversion de una Oportunidad ganada
// en Cotizacion (Req 14.5). Las acciones se gobiernan por permiso atomico.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';

import { Observable, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { OportunidadesService } from '../services/oportunidades.service';
import { ClientesService } from '../services/clientes.service';
import { CanalesVentaService } from '../services/catalogo.service';
import {
  CanalVenta,
  Cliente,
  ETAPAS_PIPELINE,
  ETIQUETA_ETAPA,
  EtapaOportunidad,
  Oportunidad,
  etapasDestino,
} from '../models/comercial.models';

/** Columna del tablero: una etapa con sus Oportunidades y su valor agregado. */
interface ColumnaPipeline {
  etapa: EtapaOportunidad;
  etiqueta: string;
  oportunidades: Oportunidad[];
  /** Suma del valor estimado de las Oportunidades de la columna (MXN). */
  valor: number;
}

/** Etapas terminales del embudo: NO cuentan como pipeline abierto. */
const ETAPAS_TERMINALES: ReadonlySet<EtapaOportunidad> = new Set<EtapaOportunidad>([
  'ganado',
  'perdido',
]);

@Component({
  selector: 'app-comercial-oportunidades',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    EntitySelect,
  ],
  templateUrl: './oportunidades.html',
  styleUrl: './oportunidades.scss',
})
export class ComercialOportunidades {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(OportunidadesService);
  private readonly clientes = inject(ClientesService);
  private readonly canales = inject(CanalesVentaService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly puedeCrear = this.auth.tienePermiso('oportunidad', 'crear');
  protected readonly puedeCambiarEtapa = this.auth.tienePermiso('oportunidad', 'cambiar_estado');
  protected readonly puedeConvertir = this.auth.tienePermiso('oportunidad', 'actualizar');
  /** El mismo permiso `actualizar` gobierna la asignacion de canal (Req 2.1). */
  protected readonly puedeAsignarCanal = this.auth.tienePermiso('oportunidad', 'actualizar');
  /** Permite guiar a crear un canal cuando no hay ninguno (bugfix #7). */
  protected readonly puedeCrearCanal = this.auth.tienePermiso('canal_venta', 'crear');
  /** El enlace al Cliente solo se ofrece si el Usuario puede abrir su ficha (Req 2.4, 7.2). */
  protected readonly puedeVerCliente = this.auth.tienePermiso('cliente', 'leer');
  /** El enlace "Ver cotizacion" solo se ofrece si el Usuario puede leerla (Req 2.3, 7.2). */
  protected readonly puedeVerCotizacion = this.auth.tienePermiso('cotizacion', 'leer');

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly oportunidades = signal<Oportunidad[]>([]);
  protected readonly guardando = signal(false);
  protected readonly formularioAbierto = signal(false);
  protected readonly etiquetaEtapa = ETIQUETA_ETAPA;

  /** Filtro por canal de venta seleccionado (id, o '' para todos). */
  protected readonly filtroCanalId = signal('');
  /** Canales de venta disponibles para el filtro y los selectores (cargados una vez). */
  protected readonly canalesDisponibles = signal<CanalVenta[]>([]);
  /** Mapa id -> nombre de canal para mostrar el canal actual sin exponer el UUID. */
  private readonly nombreCanalPorId = computed<Map<string, string>>(
    () => new Map(this.canalesDisponibles().map((c) => [c.id, c.nombre])),
  );
  /** Mapa id -> nombre de cliente para mostrar/enlazar al cliente sin exponer el UUID. */
  protected readonly nombreClientePorId = signal<Map<string, string>>(new Map());

  /** Oportunidad cuyo canal se esta asignando (o null cuando no hay dialogo abierto). */
  protected readonly canalObjetivo = signal<Oportunidad | null>(null);

  /** Agrupa las Oportunidades por etapa, respetando el orden del embudo, y
   * calcula el valor agregado de cada columna para el tablero (kanban premium). */
  protected readonly columnas = computed<ColumnaPipeline[]>(() => {
    const items = this.oportunidades();
    return ETAPAS_PIPELINE.map((etapa) => {
      const oportunidades = items.filter((o) => o.etapa === etapa);
      const valor = oportunidades.reduce((acc, o) => acc + (Number(o.valorEstimado) || 0), 0);
      return { etapa, etiqueta: ETIQUETA_ETAPA[etapa], oportunidades, valor };
    });
  });

  // ---------------------------------------------------------------------------
  // Indicadores del embudo (KPIs) — patron enterprise: outcome-first arriba a la
  // izquierda. Se calculan en el cliente a partir de las Oportunidades cargadas.
  // ---------------------------------------------------------------------------

  /** Oportunidades ABIERTAS (no terminales): las que siguen vivas en el embudo. */
  private readonly abiertas = computed<Oportunidad[]>(() =>
    this.oportunidades().filter((o) => !ETAPAS_TERMINALES.has(o.etapa)),
  );

  /** Numero de Oportunidades abiertas. */
  protected readonly totalAbiertas = computed<number>(() => this.abiertas().length);

  /** Valor total del pipeline ABIERTO (MXN): suma del valor estimado de las abiertas. */
  protected readonly valorPipeline = computed<number>(() =>
    this.abiertas().reduce((acc, o) => acc + (Number(o.valorEstimado) || 0), 0),
  );

  /** Ticket promedio del pipeline abierto (MXN); 0 si no hay abiertas. */
  protected readonly ticketPromedio = computed<number>(() => {
    const n = this.totalAbiertas();
    return n === 0 ? 0 : this.valorPipeline() / n;
  });

  /** Numero de Oportunidades ganadas (etapa terminal 'ganado'). */
  protected readonly totalGanados = computed<number>(
    () => this.oportunidades().filter((o) => o.etapa === 'ganado').length,
  );

  /** Tasa de conversion a ganado sobre el total (0-100); 0 si no hay Oportunidades. */
  protected readonly tasaGanados = computed<number>(() => {
    const total = this.oportunidades().length;
    return total === 0 ? 0 : Math.round((this.totalGanados() / total) * 100);
  });

  protected readonly form = this.fb.nonNullable.group({
    clienteId: ['', [Validators.required]],
    titulo: ['', [Validators.required, Validators.maxLength(200)]],
    valorEstimado: [0, [Validators.required, Validators.min(0.01), Validators.max(999999999.99)]],
  });

  /** Formulario del dialogo de asignacion de canal (canalId vacio = quitar canal). */
  protected readonly formCanal = this.fb.nonNullable.group({
    canalId: [''],
  });

  /** Busca Clientes por nombre/RFC para el selector (nunca se teclea el UUID). */
  protected readonly buscarCliente = (filtro: string): Observable<PaginaResponse<Cliente>> =>
    this.clientes.listar(filtro, 0, 20);

  /** Etiqueta principal de un Cliente en el selector. */
  protected readonly etiquetaCliente = (cliente: Cliente): string => cliente.nombre;

  /** Detalle secundario (RFC) de un Cliente en el selector. */
  protected readonly detalleCliente = (cliente: Cliente): string | null => cliente.rfc || null;

  constructor() {
    this.cargarCanales();
    this.cargar();
  }

  /** Carga los canales de venta una sola vez para el filtro y los selectores (Req 63). */
  cargarCanales(): void {
    this.canales.listar(null, 0, 100).subscribe({
      next: (pagina) => this.canalesDisponibles.set(pagina.content),
      // Sin canales el filtro/selector quedan vacios; la vista degrada sin romper.
      error: () => this.canalesDisponibles.set([]),
    });
  }

  /** Nombre del canal de una Oportunidad, o null si no tiene canal asignado. */
  nombreCanal(oportunidad: Oportunidad): string | null {
    return oportunidad.canalVentaId
      ? (this.nombreCanalPorId().get(oportunidad.canalVentaId) ?? null)
      : null;
  }

  /** Nombre del cliente de una Oportunidad, o null si aun no se resolvio. */
  nombreCliente(oportunidad: Oportunidad): string | null {
    return this.nombreClientePorId().get(oportunidad.clienteId) ?? null;
  }

  /** Carga las Oportunidades (hasta 100, el maximo permitido por pagina) con el filtro por canal. */
  cargar(): void {
    this.fase.set('cargando');
    this.service.listar({ canalVentaId: this.filtroCanalId() || null }, 0, 100).subscribe({
      next: (pagina) => {
        this.oportunidades.set(pagina.content);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
        this.resolverNombresCliente(pagina.content);
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Aplica el filtro por canal de venta y recarga el pipeline (Req 2.2). */
  aplicarFiltroCanal(canalId: string): void {
    this.filtroCanalId.set(canalId ?? '');
    this.cargar();
  }

  /**
   * Resuelve los nombres de los Clientes referidos por las Oportunidades visibles
   * para poder mostrarlos como enlace navegable sin exponer el UUID (Req 2.4, 7.1).
   * Consulta solo los ids que aun no estan en el mapa; si el Usuario no puede
   * abrir la ficha del cliente, se omite la resolucion (degradacion sin romper).
   */
  private resolverNombresCliente(items: Oportunidad[]): void {
    if (!this.puedeVerCliente) {
      return;
    }
    const mapa = this.nombreClientePorId();
    const pendientes = [...new Set(items.map((o) => o.clienteId))].filter((id) => !mapa.has(id));
    if (pendientes.length === 0) {
      return;
    }
    forkJoin(
      pendientes.map((id) =>
        this.clientes.consultar(id).pipe(catchError(() => of(null as Cliente | null))),
      ),
    ).subscribe((clientes) => {
      const actualizado = new Map(this.nombreClientePorId());
      clientes.forEach((cliente, indice) => {
        if (cliente) {
          actualizado.set(pendientes[indice], cliente.nombre);
        }
      });
      this.nombreClientePorId.set(actualizado);
    });
  }

  /** Transiciones de etapa validas desde la etapa actual de una Oportunidad. */
  transicionesDe(oportunidad: Oportunidad): readonly EtapaOportunidad[] {
    return etapasDestino(oportunidad.etapa);
  }

  /** Abre/cierra el formulario de alta. */
  alternarFormulario(): void {
    this.formularioAbierto.update((v) => !v);
    if (this.formularioAbierto()) {
      this.form.reset({ clienteId: '', titulo: '', valorEstimado: 0 });
    }
  }

  /** Da de alta una Oportunidad en etapa nuevo (Req 14.1). */
  crear(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.service
      .crear({ clienteId: v.clienteId.trim(), titulo: v.titulo.trim(), valorEstimado: v.valorEstimado })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Oportunidad creada.');
          this.formularioAbierto.set(false);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          // El cliente se elige de una lista existente; un 404 aqui significa que
          // dejo de existir (baja concurrente) desde que se cargo el selector.
          const mensaje =
            e.status === 404 ? 'El cliente seleccionado no existe.' : mensajeDeError(e);
          this.toast.error(mensaje);
        },
      });
  }

  /** Cambia la etapa de una Oportunidad segun la maquina de estados (Req 14.3). */
  cambiarEtapa(oportunidad: Oportunidad, etapa: EtapaOportunidad): void {
    this.service.cambiarEtapa(oportunidad.id, etapa).subscribe({
      next: () => {
        this.toast.exito(`Oportunidad movida a "${ETIQUETA_ETAPA[etapa]}".`);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Convierte una Oportunidad ganada en Cotizacion y navega a su detalle (Req 14.5). */
  async convertir(oportunidad: Oportunidad): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Convertir en cotizacion',
      mensaje: `Se generara una cotizacion a partir de "${oportunidad.titulo}". Deseas continuar?`,
      textoConfirmar: 'Convertir',
    });
    if (!ok) {
      return;
    }
    this.service.convertir(oportunidad.id).subscribe({
      next: (resultado) => {
        this.toast.exito('Cotizacion generada.');
        this.router.navigate(['/empresa/comercial/cotizaciones', resultado.cotizacionId]);
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Abre el dialogo para asignar/cambiar el canal de venta de una Oportunidad (Req 2.1). */
  abrirAsignarCanal(oportunidad: Oportunidad): void {
    this.canalObjetivo.set(oportunidad);
    this.formCanal.reset({ canalId: oportunidad.canalVentaId ?? '' });
  }

  /** Cierra el dialogo de asignacion de canal sin guardar. */
  cerrarAsignarCanal(): void {
    this.canalObjetivo.set(null);
  }

  /**
   * Guarda el canal elegido en el dialogo invocando el endpoint existente
   * (canalId vacio limpia el canal). Actualiza la Oportunidad en memoria sin
   * recargar todo el pipeline (Req 2.1, 63.1).
   */
  guardarCanal(): void {
    const objetivo = this.canalObjetivo();
    if (!objetivo || this.guardando()) {
      return;
    }
    const canalId = this.formCanal.getRawValue().canalId || null;
    this.guardando.set(true);
    this.service.asignarCanalVenta(objetivo.id, canalId).subscribe({
      next: (actualizada) => {
        this.guardando.set(false);
        this.oportunidades.update((items) =>
          items.map((o) => (o.id === actualizada.id ? actualizada : o)),
        );
        this.canalObjetivo.set(null);
        this.toast.exito(canalId ? 'Canal de venta asignado.' : 'Canal de venta retirado.');
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
