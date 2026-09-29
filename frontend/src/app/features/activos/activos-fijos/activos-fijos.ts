// =============================================================================
// Vista de Activos fijos y depreciacion (Req 44)
// -----------------------------------------------------------------------------
// Listado paginado filtrable por estado; alta de un activo fijo (metodo linea
// recta o saldos decrecientes); corrida de depreciacion por periodo mensual; y
// baja del bien. Se muestra la depreciacion acumulada y el valor en libros
// (costo - acumulada), ambos calculados por el servidor (la resta de despliegue
// se hace en centavos enteros para evitar errores de float). Cada accion se
// gobierna por permiso atomico (deny-by-default).
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { humanizarEstado, tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { aPesos, aCentavos } from '../../finanzas-comun/dinero';
import {
  MetricChart,
  type MetricPoint,
} from '../../../shared/components/metric-chart/metric-chart';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import { DashboardSection } from '../../../shared/components/dashboard-section/dashboard-section';
import { ActivosService } from '../services/activos.service';
import { ActivoFijo } from '../models/activos.models';

@Component({
  selector: 'app-activos-fijos',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
    MetricChart,
    KpiTile,
    DashboardSection,
  ],
  templateUrl: './activos-fijos.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './activos-fijos.scss',
})
export class ActivosFijos {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ActivosService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;

  protected readonly puedeCrear = this.auth.tienePermiso('activo_fijo', 'crear');
  protected readonly puedeDepreciar = this.auth.tienePermiso('depreciacion', 'crear');
  protected readonly puedeBaja = this.auth.tienePermiso('activo_fijo', 'cambiar_estado');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Activo' },
    { clave: 'costo', encabezado: 'Costo', alineacion: 'fin' },
    { clave: 'metodo', encabezado: 'Metodo' },
    { clave: 'acumulada', encabezado: 'Dep. acumulada', alineacion: 'fin' },
    { clave: 'libros', encabezado: 'Valor en libros', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos' },
    { valor: 'activo', etiqueta: 'Activo' },
    { valor: 'baja', etiqueta: 'Baja' },
  ];

  protected readonly estado = signal<EstadoSolicitud<ActivoFijo[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  protected readonly formAlta = this.fb.nonNullable.group({
    nombre: ['', [Validators.required]],
    costo: [0.01, [Validators.required, Validators.min(0.01)]],
    fechaAdquisicion: ['', [Validators.required]],
    vidaUtilMeses: [12, [Validators.required, Validators.min(1)]],
    metodoDepreciacion: ['linea_recta', [Validators.required]],
    valorResidual: [0, [Validators.min(0)]],
  });

  constructor() {
    this.cargar();
  }

  /**
   * Resumen (scorecard) de los activos de la pagina cargada: costo total,
   * depreciacion acumulada, valor neto en libros y conteos por estado. Se calcula en
   * centavos para evitar errores de coma flotante y se expresa de vuelta en pesos.
   */
  protected readonly resumen = computed(() => {
    const activos = this.estado().datos ?? [];
    let costoC = 0;
    let deprecC = 0;
    let vigentes = 0;
    let bajas = 0;
    for (const a of activos) {
      costoC += aCentavos(a.costo);
      deprecC += aCentavos(a.depreciacionAcumulada);
      if (a.estado === 'baja') {
        bajas += 1;
      } else {
        vigentes += 1;
      }
    }
    return {
      costo: aPesos(costoC),
      depreciacion: aPesos(deprecC),
      valorNeto: aPesos(costoC - deprecC),
      vigentes,
      bajas,
    };
  });

  /** Puntos de la dona: composicion del valor en libros por metodo de depreciacion. */
  protected readonly composicion = computed<MetricPoint[]>(() => {
    const activos = this.estado().datos ?? [];
    let recta = 0;
    let decreciente = 0;
    for (const a of activos) {
      const libros = aCentavos(a.costo) - aCentavos(a.depreciacionAcumulada);
      if (a.metodoDepreciacion === 'saldos_decrecientes') {
        decreciente += libros;
      } else {
        recta += libros;
      }
    }
    return [
      { etiqueta: 'Línea recta', valor: aPesos(recta), unidad: 'MXN' },
      { etiqueta: 'Saldos decrecientes', valor: aPesos(decreciente), unidad: 'MXN' },
    ];
  });

  /**
   * Columnas comparativas por metodo de depreciacion: costo original (valor) vs
   * valor neto en libros (comparativo). Muestra cuanto se ha depreciado en cada
   * metodo. Todo en pesos, agregando en centavos para evitar errores de float.
   */
  protected readonly costoVsNeto = computed<MetricPoint[]>(() => {
    const activos = this.estado().datos ?? [];
    let costoRectaC = 0;
    let netoRectaC = 0;
    let costoDecrC = 0;
    let netoDecrC = 0;
    for (const a of activos) {
      const costoC = aCentavos(a.costo);
      const netoC = costoC - aCentavos(a.depreciacionAcumulada);
      if (a.metodoDepreciacion === 'saldos_decrecientes') {
        costoDecrC += costoC;
        netoDecrC += netoC;
      } else {
        costoRectaC += costoC;
        netoRectaC += netoC;
      }
    }
    return [
      {
        etiqueta: 'Línea recta',
        valor: aPesos(costoRectaC),
        comparativo: aPesos(netoRectaC),
        unidad: 'MXN',
      },
      {
        etiqueta: 'Saldos decrecientes',
        valor: aPesos(costoDecrC),
        comparativo: aPesos(netoDecrC),
        unidad: 'MXN',
      },
    ];
  });

  /**
   * Porcentaje del valor neto en libros respecto al costo total (0-100). Es una
   * medida de "vida util remanente" del patrimonio: 100% recien adquirido, 0%
   * totalmente depreciado. Alimenta el gauge del tablero.
   */
  protected readonly porcentajeValorNeto = computed<number>(() => {
    const r = this.resumen();
    if (r.costo <= 0) {
      return 0;
    }
    const pct = (r.valorNeto / r.costo) * 100;
    return Math.max(0, Math.min(100, Math.round(pct)));
  });

  /** Valor en libros de despliegue: costo - depreciacion acumulada (en centavos). */
  valorEnLibros(a: ActivoFijo): number {
    return aPesos(aCentavos(a.costo) - aCentavos(a.depreciacionAcumulada));
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listar(this.filtroEstado() || null, this.page(), this.size()).subscribe({
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

  aplicarFiltro(valor: string): void {
    this.filtroEstado.set(valor);
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
    this.overlay
      .ejecutar(
        this.service.crear({
          nombre: v.nombre,
          costo: v.costo,
          fechaAdquisicion: v.fechaAdquisicion,
          vidaUtilMeses: v.vidaUtilMeses,
          metodoDepreciacion: v.metodoDepreciacion as 'linea_recta' | 'saldos_decrecientes',
          valorResidual: v.valorResidual ?? 0,
        }),
        { tipo: 'crear', textoProceso: 'Registrando activo…', textoExito: 'Activo registrado' },
      )
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Activo fijo registrado.');
          this.formAlta.reset({
            nombre: '',
            costo: 0.01,
            fechaAdquisicion: '',
            vidaUtilMeses: 12,
            metodoDepreciacion: 'linea_recta',
            valorResidual: 0,
          });
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

  /** Deriva el periodo mensual actual 'AAAA-MM' para la corrida por defecto. */
  private periodoActual(): string {
    const hoy = new Date();
    const mes = `${hoy.getMonth() + 1}`.padStart(2, '0');
    return `${hoy.getFullYear()}-${mes}`;
  }

  async depreciar(a: ActivoFijo): Promise<void> {
    const periodo = this.periodoActual();
    const ok = await this.confirm.confirmar({
      titulo: 'Correr depreciacion',
      mensaje: `Correr la depreciacion del periodo ${periodo} para "${a.nombre}"?`,
      textoConfirmar: 'Correr depreciacion',
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.service.depreciar(a.id, periodo), {
        tipo: 'procesar',
        textoProceso: 'Corriendo depreciación…',
        textoExito: 'Depreciación aplicada',
      })
      .subscribe({
        next: (d) => {
          this.toast.exito(`Depreciacion aplicada del periodo ${d.periodo}.`);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }

  async darDeBaja(a: ActivoFijo): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja',
      mensaje: `Dar de baja el activo "${a.nombre}"? Se conserva su historico.`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.service.darDeBaja(a.id), {
        tipo: 'eliminar',
        textoProceso: 'Dando de baja…',
        textoExito: 'Activo dado de baja',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Activo dado de baja.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }
}
