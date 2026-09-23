// =============================================================================
// Tablero de Estrategia empresarial (planeacion estrategica / OKR) (Req 58)
// -----------------------------------------------------------------------------
// Vista de negocio que presenta, de forma clara y accionable:
//   - La ESENCIA (mision, vision y valores) capturada, en tres paneles legibles,
//     con edicion in situ (expansion) que refresca lo mostrado tras guardar.
//   - Los OBJETIVOS estrategicos (OKR) como tarjetas: nombre, responsable,
//     periodo, meta, avance y estado derivado; expandibles a sus resultados
//     clave (valor objetivo/actual/peso) con acciones de alta y actualizacion.
//   - Un resumen de KPIs (total, avance promedio y conteo por estado).
//
// Buenas practicas OKR (ClearPoint/Atlassian/Smartsheet/Teamflect, reformulado):
// un objetivo cualitativo y ambicioso con 3-5 resultados clave medibles, avance
// 0-100% y revision con cadencia trimestral. Cada operacion se gobierna por
// permiso atomico (deny-by-default).
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ProgressBadge } from '../../../shared/components/progress-badge/progress-badge';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { EstrategiaVistasService } from '../services/estrategia-vistas.service';
import { EsenciaEmpresa, ObjetivoEstrategico } from '../models/estrategia.models';
import { ObjetivoDialog } from './objetivo-dialog';
import { ResultadoClaveDialog, DatosResultadoClaveDialog } from './resultado-clave-dialog';
import { StatCard } from '../../../shared/components/stat-card/stat-card';
import { OportunidadesService } from '../../comercial/services/oportunidades.service';
import { CotizacionesService } from '../../comercial/services/cotizaciones.service';
import { Cotizacion, Oportunidad } from '../../comercial/models/comercial.models';

/** Resumen de KPIs de la cartera de objetivos (OKR). */
interface ResumenObjetivos {
  total: number;
  avancePromedio: number;
  enRiesgo: number;
  enCurso: number;
  cumplido: number;
}

/** Etapas terminales del pipeline: una Oportunidad en ellas NO cuenta como abierta. */
const ETAPAS_CERRADAS: ReadonlySet<string> = new Set(['ganado', 'perdido']);

/** Tamano de pagina para calcular los indicadores comerciales en el cliente. */
const TAMANO_INDICADORES = 100;

@Component({
  selector: 'app-estrategia-vistas',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatExpansionModule,
    PageHeader,
    StateContainer,
    ProgressBadge,
    StatCard,
  ],
  templateUrl: './estrategia.html',
  styleUrl: '../estrategia.scss',
})
export class EstrategiaVistas {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(EstrategiaVistasService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly oportunidadesService = inject(OportunidadesService);
  private readonly cotizacionesService = inject(CotizacionesService);

  protected readonly puedeEditarEsencia = this.auth.tienePermiso(
    'planeacion_estrategica',
    'actualizar',
  );
  protected readonly puedeCrearObjetivo = this.auth.tienePermiso('objetivo_estrategico', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('objetivo_estrategico', 'actualizar');

  // --- Indicadores comerciales (Req 6.1) -------------------------------------
  // Gating por Modulo (deny-by-default): la seccion solo se muestra si la Empresa
  // tiene contratado el Modulo 'comercial' Y el Usuario puede listar el recurso.
  // Sin el Modulo/permiso NO se llama al endpoint (degradacion, evita 403/500).
  protected readonly puedeVerPipeline = computed(
    () => this.auth.tieneModulo('comercial') && this.auth.tienePermiso('oportunidad', 'listar'),
  );
  protected readonly puedeVerCotizaciones = computed(
    () => this.auth.tieneModulo('comercial') && this.auth.tienePermiso('cotizacion', 'listar'),
  );
  /** La seccion de indicadores comerciales aparece si hay al menos un indicador visible. */
  protected readonly mostrarComercial = computed(
    () => this.puedeVerPipeline() || this.puedeVerCotizaciones(),
  );

  // --- Esencia ---------------------------------------------------------------
  protected readonly esencia = signal<EstadoSolicitud<EsenciaEmpresa>>(cargando());
  protected readonly editandoEsencia = signal(false);
  protected readonly guardandoEsencia = signal(false);
  protected readonly formEsencia = this.fb.nonNullable.group({
    mision: [''],
    vision: [''],
    valores: [''],
  });

  // --- Objetivos -------------------------------------------------------------
  protected readonly objetivos = signal<EstadoSolicitud<ObjetivoEstrategico[]>>(cargando());

  // --- Indicadores comerciales (calculados en el cliente) --------------------
  // Se reutiliza el mismo enfoque que la Ficha 360 del Cliente: se cargan las
  // listas paginadas y los indicadores se COMPUTAN en el cliente (sin endpoint
  // de resumen). Estados propios de carga/ok/error para degradacion sin romper.
  protected readonly oportunidadesComercial = signal<EstadoSolicitud<Oportunidad[]>>(cargando());
  protected readonly cotizacionesComercial = signal<EstadoSolicitud<Cotizacion[]>>(cargando());

  /** Oportunidades abiertas: las que no estan en una etapa terminal (ganado/perdido). */
  private readonly oportunidadesAbiertas = computed(() =>
    (this.oportunidadesComercial().datos ?? []).filter((o) => !ETAPAS_CERRADAS.has(o.etapa)),
  );

  /** Numero de oportunidades abiertas del pipeline. */
  protected readonly numOportunidadesAbiertas = computed(
    () => this.oportunidadesAbiertas().length,
  );

  /** Valor en pipeline: suma del valor estimado de las oportunidades abiertas. */
  protected readonly valorPipeline = computed(() =>
    this.oportunidadesAbiertas().reduce((acc, o) => acc + (Number(o.valorEstimado) || 0), 0),
  );

  /** Valor en pipeline formateado como moneda es-MX/MXN para el indicador. */
  protected readonly valorPipelineTexto = computed(() =>
    this.valorPipeline().toLocaleString('es-MX', {
      style: 'currency',
      currency: 'MXN',
      maximumFractionDigits: 0,
    }),
  );

  /** Numero total de cotizaciones cargadas de la Empresa. */
  protected readonly numCotizaciones = computed(
    () => (this.cotizacionesComercial().datos ?? []).length,
  );

  /** Resumen de KPIs derivado de los objetivos cargados. */
  protected readonly resumen = computed<ResumenObjetivos>(() => {
    const lista = this.objetivos().datos ?? [];
    const total = lista.length;
    if (total === 0) {
      return { total: 0, avancePromedio: 0, enRiesgo: 0, enCurso: 0, cumplido: 0 };
    }
    const suma = lista.reduce((acc, o) => acc + (o.avance ?? 0), 0);
    return {
      total,
      avancePromedio: Math.round(suma / total),
      enRiesgo: lista.filter((o) => o.estadoDerivado === 'en_riesgo').length,
      enCurso: lista.filter((o) => o.estadoDerivado === 'en_curso').length,
      cumplido: lista.filter((o) => o.estadoDerivado === 'cumplido').length,
    };
  });

  constructor() {
    this.cargarEsencia();
    this.cargarObjetivos();
    this.cargarIndicadoresComerciales();
  }

  // ---------------------------------------------------------------------------
  // Indicadores comerciales (Req 6.1) — apoyo a los OKR
  // ---------------------------------------------------------------------------

  /**
   * Carga las listas comerciales para COMPUTAR los indicadores en el cliente
   * (valor en pipeline, oportunidades abiertas, cotizaciones), reutilizando el
   * enfoque de la Ficha 360. Gating por Modulo 'comercial' + permiso: si no esta
   * contratado/permitido NO se llama al endpoint (degradacion sin romper).
   */
  cargarIndicadoresComerciales(): void {
    if (this.puedeVerPipeline()) {
      this.oportunidadesComercial.set(cargando());
      this.oportunidadesService.listar({}, 0, TAMANO_INDICADORES).subscribe({
        next: (pagina) =>
          this.oportunidadesComercial.set(
            conDatos(pagina.content, pagina.content.length === 0),
          ),
        error: (e: HttpErrorResponse) =>
          this.oportunidadesComercial.set(conError(mensajeDeError(e))),
      });
    }
    if (this.puedeVerCotizaciones()) {
      this.cotizacionesComercial.set(cargando());
      this.cotizacionesService.listar({}, 0, TAMANO_INDICADORES).subscribe({
        next: (pagina) =>
          this.cotizacionesComercial.set(
            conDatos(pagina.content, pagina.content.length === 0),
          ),
        error: (e: HttpErrorResponse) =>
          this.cotizacionesComercial.set(conError(mensajeDeError(e))),
      });
    }
  }

  // ---------------------------------------------------------------------------
  // Esencia (mision / vision / valores)
  // ---------------------------------------------------------------------------

  cargarEsencia(): void {
    this.esencia.set(cargando());
    this.service.consultarEsencia().subscribe({
      next: (esencia) => this.esencia.set(conDatos(esencia)),
      error: (e: HttpErrorResponse) => {
        // 404: aun no hay esencia; se trata como estado vacio (se puede crear).
        if (e.status === 404) {
          this.esencia.set(conDatos<EsenciaEmpresa>(vaciaEsencia(), true));
          return;
        }
        this.esencia.set(conError(mensajeDeError(e)));
      },
    });
  }

  /** Abre el formulario de edicion precargando los valores actuales. */
  editarEsencia(): void {
    const datos = this.esencia().datos;
    this.formEsencia.patchValue({
      mision: datos?.mision ?? '',
      vision: datos?.vision ?? '',
      valores: datos?.valores ?? '',
    });
    this.editandoEsencia.set(true);
  }

  /** Cierra el formulario de edicion sin guardar. */
  cancelarEdicionEsencia(): void {
    this.editandoEsencia.set(false);
  }

  guardarEsencia(): void {
    const v = this.formEsencia.getRawValue();
    this.guardandoEsencia.set(true);
    this.service
      .guardarEsencia({
        mision: v.mision.trim() || null,
        vision: v.vision.trim() || null,
        valores: v.valores.trim() || null,
      })
      .subscribe({
        next: (esencia) => {
          this.guardandoEsencia.set(false);
          this.editandoEsencia.set(false);
          // Refresco optimista: lo mostrado refleja de inmediato lo guardado.
          this.esencia.set(conDatos(esencia));
          this.toast.exito('Esencia actualizada.');
        },
        error: (e: HttpErrorResponse) => {
          this.guardandoEsencia.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  // ---------------------------------------------------------------------------
  // Objetivos (OKR)
  // ---------------------------------------------------------------------------

  cargarObjetivos(): void {
    this.objetivos.set(cargando());
    this.service.listarObjetivos(null, 0, 100).subscribe({
      next: (pagina) => this.objetivos.set(conDatos(pagina.content, pagina.content.length === 0)),
      error: (e: HttpErrorResponse) => this.objetivos.set(conError(mensajeDeError(e))),
    });
  }

  /** Abre el dialogo de alta de objetivo y refresca la lista al crear. */
  abrirCrearObjetivo(): void {
    const ref = this.dialog.open(ObjetivoDialog, { autoFocus: true, restoreFocus: true });
    ref.afterClosed().subscribe((creado) => {
      if (creado) {
        this.toast.exito('Objetivo creado.');
        this.cargarObjetivos();
      }
    });
  }

  /** Abre el dialogo de alta de resultado clave para el objetivo indicado. */
  abrirAgregarResultado(objetivo: ObjetivoEstrategico): void {
    const datos: DatosResultadoClaveDialog = {
      objetivoId: objetivo.id,
      objetivoNombre: objetivo.nombre,
    };
    const ref = this.dialog.open(ResultadoClaveDialog, {
      data: datos,
      autoFocus: true,
      restoreFocus: true,
    });
    ref.afterClosed().subscribe((actualizado) => {
      if (actualizado) {
        this.toast.exito('Resultado clave agregado; avance recalculado.');
        this.reemplazarObjetivo(actualizado);
      }
    });
  }

  /** Actualiza el valor actual de un resultado clave (recalcula avance en el servidor). */
  actualizarValor(resultadoId: string, valor: string): void {
    const numero = Number(valor);
    if (!Number.isFinite(numero) || valor.trim() === '') {
      return;
    }
    this.service.actualizarValorResultadoClave(resultadoId, numero).subscribe({
      next: (actualizado) => {
        this.toast.exito('Valor actualizado; avance recalculado.');
        this.reemplazarObjetivo(actualizado);
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Reemplaza en la lista el objetivo por su version actualizada (sin recargar todo). */
  private reemplazarObjetivo(objetivo: ObjetivoEstrategico): void {
    const estado = this.objetivos();
    const lista = estado.datos ?? [];
    const nueva = lista.map((o) => (o.id === objetivo.id ? objetivo : o));
    this.objetivos.set(conDatos(nueva, nueva.length === 0));
  }
}

/** Esencia vacia por defecto cuando aun no se ha registrado. */
function vaciaEsencia(): EsenciaEmpresa {
  return {
    id: '',
    mision: null,
    vision: null,
    valores: null,
    version: 0,
    createdAt: '',
    updatedAt: '',
  };
}
