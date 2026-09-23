// =============================================================================
// Vista de Facturacion de renta de modulos (super_admin) (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Sobre una Empresa SELECCIONADA por nombre/RFC (autocompletado, nunca por UUID
// a mano), permite:
//   - Listar sus facturas de renta ya emitidas (mas recientes primero).
//   - Ver el PDF premium de una factura en una pestana nueva (vista inline).
//   - Descargar ese PDF como `factura-{periodo}.pdf`.
//   - Previsualizar la factura de un periodo (total + lineas, sin persistir) y
//     luego emitirla (persiste).
//
// El PDF exige autenticacion: se descarga como Blob via HttpClient (el
// interceptor adjunta el token) y se abre/descarga con un object URL local; un
// <a href> plano devolveria 401. Los importes los calcula el backend; la UI solo
// los formatea (CurrencyPipe es-MX con la moneda de la factura; DatePipe es-MX).
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { StatCard } from '../../../shared/components/stat-card/stat-card';
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

import { EmpresasService } from '../services/empresas.service';
import { FacturacionService } from '../services/facturacion.service';
import { Empresa, FacturaRenta } from '../models/plataforma.models';

/** Opcion de mes para el selector de periodo (valor 1-12 + etiqueta en espanol). */
interface OpcionMes {
  valor: number;
  etiqueta: string;
}

@Component({
  selector: 'app-plataforma-facturacion',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageHeader,
    StateContainer,
    StatCard,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './facturacion.html',
  styleUrl: './facturacion.scss',
})
export class PlataformaFacturacion {
  private readonly empresas = inject(EmpresasService);
  private readonly service = inject(FacturacionService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  /** Permisos atomicos (deny-by-default). */
  protected readonly puedeLeer = this.auth.tienePermiso('factura_renta', 'leer');
  protected readonly puedeEmitir = this.auth.tienePermiso('factura_renta', 'crear');

  /** Columnas de la tabla de facturas emitidas. */
  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'periodo', encabezado: 'Periodo' },
    { clave: 'moneda', encabezado: 'Moneda', ocultarEnMovil: true },
    { clave: 'total', encabezado: 'Total', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado', ocultarEnMovil: true },
    { clave: 'emitida', encabezado: 'Emitida', ocultarEnMovil: true },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  // --- Seleccion de Empresa (autocompletado por nombre/RFC) ------------------

  /** Texto de busqueda (nombre o RFC). */
  protected readonly busqueda = signal('');
  /** Empresa seleccionada sobre la que operar; su id se usa internamente. */
  protected readonly seleccionada = signal<Empresa | null>(null);
  /** Indica una busqueda en curso (estado de carga del autocompletado). */
  protected readonly buscando = signal(false);

  // --- Listado de facturas de la Empresa seleccionada ------------------------

  protected readonly estado = signal<EstadoSolicitud<FacturaRenta[]>>(conDatos([], true));

  // Paginacion en cliente: el backend devuelve el arreglo completo (no paginado);
  // se corta localmente para que el paginador de la DataTable sea consistente.
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  /** Total de facturas cargadas (para el paginador). */
  protected readonly total = computed(() => this.estado().datos?.length ?? 0);

  /** Pagina actual del listado, cortada del arreglo completo. */
  protected readonly facturasPagina = computed<FacturaRenta[]>(() => {
    const facturas = this.estado().datos ?? [];
    const inicio = this.page() * this.size();
    return facturas.slice(inicio, inicio + this.size());
  });

  /** KPI: numero de facturas emitidas de la Empresa seleccionada. */
  protected readonly kpiConteo = computed(() => {
    const facturas = this.estado().datos;
    return facturas ? facturas.length : 0;
  });

  /**
   * KPI: total facturado. Solo tiene sentido si todas las facturas comparten
   * moneda; se devuelve la suma y su moneda cuando es homogenea, o `null` para
   * ocultar el KPI si conviven varias monedas (no se suman peras con manzanas).
   */
  protected readonly kpiTotal = computed<{ monto: number; moneda: string } | null>(() => {
    const facturas = this.estado().datos ?? [];
    if (facturas.length === 0) {
      return null;
    }
    const monedas = new Set(facturas.map((f) => f.monedaCodigo));
    if (monedas.size !== 1) {
      return null;
    }
    const monto = facturas.reduce((suma, f) => suma + f.total, 0);
    return { monto, moneda: facturas[0].monedaCodigo };
  });

  // --- Emision / previsualizacion --------------------------------------------

  /** Meses del anio para el selector de periodo. */
  protected readonly meses: OpcionMes[] = [
    { valor: 1, etiqueta: 'Enero' },
    { valor: 2, etiqueta: 'Febrero' },
    { valor: 3, etiqueta: 'Marzo' },
    { valor: 4, etiqueta: 'Abril' },
    { valor: 5, etiqueta: 'Mayo' },
    { valor: 6, etiqueta: 'Junio' },
    { valor: 7, etiqueta: 'Julio' },
    { valor: 8, etiqueta: 'Agosto' },
    { valor: 9, etiqueta: 'Septiembre' },
    { valor: 10, etiqueta: 'Octubre' },
    { valor: 11, etiqueta: 'Noviembre' },
    { valor: 12, etiqueta: 'Diciembre' },
  ];

  /** Anios ofrecidos: el actual y los cuatro anteriores. */
  protected readonly anios: number[] = Array.from(
    { length: 5 },
    (_, i) => new Date().getFullYear() - i,
  );

  /** Mes y anio elegidos para el periodo a emitir (por defecto, el mes actual). */
  protected readonly mesElegido = signal(new Date().getMonth() + 1);
  protected readonly anioElegido = signal(new Date().getFullYear());

  /** Periodo derivado en formato `YYYY-MM-01` que consume el backend. */
  protected readonly periodo = computed(() => {
    const mes = String(this.mesElegido()).padStart(2, '0');
    return `${this.anioElegido()}-${mes}-01`;
  });

  /** Previsualizacion actual (total + lineas sin persistir), o `null`. */
  protected readonly previsualizacion = signal<FacturaRenta | null>(null);
  /** `true` mientras se previsualiza o emite (bloquea los botones). */
  protected readonly procesando = signal(false);

  /**
   * Resultados del autocompletado: reacciona al texto con debounce y consulta el
   * buscador del backend (nombre/RFC). Ignora textos muy cortos para no listar
   * todo con una sola letra.
   */
  protected readonly resultados = toSignal(
    toObservable(this.busqueda).pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap((q) => {
        const termino = q.trim();
        if (termino.length < 2) {
          this.buscando.set(false);
          return of([] as Empresa[]);
        }
        this.buscando.set(true);
        return this.empresas.listar(null, termino, 0, 10).pipe(
          switchMap((pagina) => {
            this.buscando.set(false);
            return of(pagina.content);
          }),
          catchError(() => {
            this.buscando.set(false);
            return of([] as Empresa[]);
          }),
        );
      }),
    ),
    { initialValue: [] as Empresa[] },
  );

  /** Actualiza el texto de busqueda desde el input. */
  protected alEscribir(valor: string): void {
    this.busqueda.set(valor);
    // Si el usuario edita el texto, se invalida la seleccion previa.
    if (this.seleccionada()) {
      this.seleccionada.set(null);
    }
  }

  /** Fija la Empresa elegida del autocompletado y carga sus facturas. */
  protected alSeleccionar(evento: MatAutocompleteSelectedEvent): void {
    const empresa = evento.option.value as Empresa;
    this.seleccionada.set(empresa);
    this.busqueda.set('');
    this.previsualizacion.set(null);
    this.cargar();
  }

  /** Limpia la Empresa seleccionada para elegir otra. */
  protected limpiarSeleccion(): void {
    this.seleccionada.set(null);
    this.busqueda.set('');
    this.previsualizacion.set(null);
    this.estado.set(conDatos([], true));
  }

  /** Iniciales para el avatar cuando la Empresa no tiene logo. */
  protected iniciales(nombre: string): string {
    const palabras = nombre.trim().split(/\s+/).filter(Boolean);
    if (palabras.length === 0) {
      return '?';
    }
    if (palabras.length === 1) {
      return palabras[0].slice(0, 2).toUpperCase();
    }
    return (palabras[0][0] + palabras[1][0]).toUpperCase();
  }

  /** Carga las facturas emitidas de la Empresa seleccionada. */
  cargar(): void {
    const empresa = this.seleccionada();
    if (!empresa) {
      return;
    }
    this.page.set(0);
    this.estado.set(cargando());
    this.service.listarPorEmpresa(empresa.id).subscribe({
      next: (facturas) => this.estado.set(conDatos(facturas, facturas.length === 0)),
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  /** Reacciona al cambio de pagina del listado (paginacion en cliente). */
  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
  }

  /**
   * Previsualiza la factura del periodo elegido (sin persistir). Traduce los
   * errores de negocio del backend (404 sin suscripcion, 422 sin modulos o sin
   * moneda) a un toast con el mensaje del servidor.
   */
  previsualizar(): void {
    const empresa = this.seleccionada();
    if (!empresa || !this.puedeLeer) {
      return;
    }
    this.procesando.set(true);
    this.service.previsualizar(empresa.id, this.periodo()).subscribe({
      next: (factura) => {
        this.procesando.set(false);
        this.previsualizacion.set(factura);
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.previsualizacion.set(null);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /** Descarta la previsualizacion activa. */
  cancelarPrevisualizacion(): void {
    this.previsualizacion.set(null);
  }

  /**
   * Emite (persiste) la factura del periodo elegido. Al exito, muestra un toast,
   * limpia la previsualizacion y recarga el listado. Traduce 409 (ya emitida),
   * 422 y 404 al mensaje de negocio del backend.
   */
  emitir(): void {
    const empresa = this.seleccionada();
    if (!empresa || !this.puedeEmitir) {
      return;
    }
    this.procesando.set(true);
    this.service.emitir(empresa.id, this.periodo()).subscribe({
      next: () => {
        this.procesando.set(false);
        this.previsualizacion.set(null);
        this.toast.exito('Factura de renta emitida correctamente.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /**
   * Abre el PDF premium de una factura en una pestana nueva (vista inline). Se
   * obtiene como Blob (token via interceptor) y se abre con un object URL local;
   * este se revoca tras un margen para permitir que la pestana lo cargue.
   */
  verPdf(factura: FacturaRenta): void {
    if (!this.puedeLeer) {
      return;
    }
    this.procesando.set(true);
    this.service.descargarPdf(factura.id).subscribe({
      next: (blob) => {
        this.procesando.set(false);
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        // Se revoca con margen para no cortar la carga de la nueva pestana.
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /**
   * Descarga el PDF premium de una factura como `factura-{periodo}.pdf` (token
   * via interceptor). Revoca el object URL inmediatamente tras disparar la
   * descarga.
   */
  descargarPdf(factura: FacturaRenta): void {
    if (!this.puedeLeer) {
      return;
    }
    this.procesando.set(true);
    this.service.descargarPdf(factura.id).subscribe({
      next: (blob) => {
        this.procesando.set(false);
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `factura-${factura.periodo}.pdf`;
        enlace.click();
        URL.revokeObjectURL(url);
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }
}
