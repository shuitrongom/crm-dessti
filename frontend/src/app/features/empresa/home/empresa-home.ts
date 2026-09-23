// =============================================================================
// Pagina principal por empresa (Req 22, 26, 58)
// -----------------------------------------------------------------------------
// Compone la vista de inicio del ambito empresa:
//   - Branding de la empresa (nombre/logo) — Req 26.
//   - Esencia: mision/vision/valores — Req 58.1.
//   - Resumen de objetivos estrategicos con avance y estado derivado — Req 58.10.
//   - Tablero de indicadores por area — Req 22.
//
// Cada bloque tiene su propio estado (cargando/ok/vacio/error) y se consulta solo
// si el Usuario tiene el permiso correspondiente (deny-by-default, Req 3); sin el
// permiso, el bloque no se solicita ni se renderiza.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';
import { RouterLink } from '@angular/router';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { ProgressBadge } from '../../../shared/components/progress-badge/progress-badge';
import { IndicatorCard } from '../../../shared/components/indicator-card/indicator-card';
import { StatCard } from '../../../shared/components/stat-card/stat-card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { BrandingService } from '../services/branding.service';
import { MiEmpresaService } from '../services/mi-empresa.service';
import { EstrategiaVistasService } from '../../estrategia-vistas/services/estrategia-vistas.service';
import { EsenciaEmpresa, ObjetivoEstrategico } from '../../estrategia-vistas/models/estrategia.models';
import { TableroService } from '../../reportes/services/tablero.service';
import { Tablero } from '../../reportes/models/reportes.models';
import { OportunidadesService } from '../../comercial/services/oportunidades.service';
import { CotizacionesService } from '../../comercial/services/cotizaciones.service';
import { Oportunidad } from '../../comercial/models/comercial.models';
import { Branding } from './home.models';

/** Nombre neutro por defecto cuando no se puede resolver el nombre de la Empresa. */
const NOMBRE_EMPRESA_POR_DEFECTO = 'Mi empresa';

/** Saludo neutro cuando no hay un nombre legible (token antiguo/sin claim). */
const SALUDO_NEUTRO = 'Hola, bienvenido.';

/**
 * Detecta un UUID v1-v5 canonico (8-4-4-4-12 hex). Se usa para NUNCA mostrar el
 * identificador tecnico (`sub`) en el saludo cuando el token es antiguo/cacheado
 * y el claim legible `identificador` esta ausente.
 */
const PATRON_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Etapas terminales del pipeline: una Oportunidad en ellas NO cuenta como abierta. */
const ETAPAS_CERRADAS: ReadonlySet<string> = new Set(['ganado', 'perdido']);

/** Tamano de pagina para calcular el resumen comercial en el cliente. */
const TAMANO_RESUMEN_COMERCIAL = 100;

/** Resumen comercial calculado en el cliente a partir de las listas. */
interface ResumenComercial {
  valorPipeline: number;
  oportunidadesAbiertas: number;
  cotizaciones: number;
}

/** Resumen comercial vacio por defecto (degradacion sin datos). */
function resumenComercialVacio(): ResumenComercial {
  return { valorPipeline: 0, oportunidadesAbiertas: 0, cotizaciones: 0 };
}

@Component({
  selector: 'app-empresa-home',
  imports: [
    RouterLink,
    PageHeader,
    StateContainer,
    ProgressBadge,
    IndicatorCard,
    StatCard,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './empresa-home.html',
  styleUrl: './empresa-home.scss',
})
export class EmpresaHome {
  private readonly auth = inject(AuthService);
  private readonly brandingService = inject(BrandingService);
  private readonly miEmpresaService = inject(MiEmpresaService);
  private readonly estrategiaService = inject(EstrategiaVistasService);
  private readonly tableroService = inject(TableroService);
  private readonly oportunidadesService = inject(OportunidadesService);
  private readonly cotizacionesService = inject(CotizacionesService);

  /**
   * Nombre legible del Usuario para el saludo. Prefiere el claim legible
   * (`nombreMostrado`, p. ej. "admin@demofactura.com") y NUNCA muestra el UUID
   * tecnico (`sub`) ni una marca de terceros.
   */
  protected readonly nombreUsuario = this.auth.nombreMostrado;

  /**
   * Saludo mostrado en el encabezado. Usa el nombre legible del Usuario, pero es
   * robusto ante tokens antiguos/cacheados: si `nombreMostrado()` es nulo o tiene
   * forma de UUID (el claim legible faltaba y recayo en el `sub`), degrada a un
   * saludo neutro en lugar de exponer el identificador tecnico.
   */
  protected readonly saludo = computed(() => {
    const nombre = this.nombreUsuario();
    if (!nombre || PATRON_UUID.test(nombre)) {
      return SALUDO_NEUTRO;
    }
    return `Hola, ${nombre}. Este es el resumen de tu empresa.`;
  });

  /**
   * Nombre de la Empresa que encabeza la pagina (Req 26). Se resuelve desde
   * GET /empresas/mi-empresa: prefiere `brandingNombreVisible` y, en su ausencia,
   * la razon social `nombre`. Degrada a un nombre neutro si la consulta falla;
   * nunca muestra una marca de terceros ni el UUID.
   */
  protected readonly nombreEmpresa = signal<string>(NOMBRE_EMPRESA_POR_DEFECTO);

  /** Estado del branding de la empresa (Req 26). */
  protected readonly branding = signal<EstadoSolicitud<Branding>>(cargando());
  /** Estado de la esencia mision/vision/valores (Req 58.1). */
  protected readonly esencia = signal<EstadoSolicitud<EsenciaEmpresa>>(cargando());
  /** Estado del resumen de objetivos estrategicos (Req 58.10). */
  protected readonly objetivos = signal<EstadoSolicitud<ObjetivoEstrategico[]>>(cargando());
  /** Estado del Tablero de indicadores por area (Req 22). */
  protected readonly tablero = signal<EstadoSolicitud<Tablero>>(cargando());
  /** Estado del resumen comercial (Req 6.3): pipeline y cotizaciones. */
  protected readonly resumenComercial = signal<EstadoSolicitud<ResumenComercial>>(cargando());

  /** Valor en pipeline del resumen comercial formateado como moneda es-MX/MXN. */
  protected readonly valorPipelineTexto = computed(() =>
    (this.resumenComercial().datos?.valorPipeline ?? 0).toLocaleString('es-MX', {
      style: 'currency',
      currency: 'MXN',
      maximumFractionDigits: 0,
    }),
  );

  /**
   * Muestra el bloque de estrategia (esencia + objetivos) solo si la Empresa
   * tiene contratado el Modulo 'estrategia' Y el Usuario tiene permiso. Se oculta
   * por completo cuando el Modulo no esta contratado (degradacion sin clutter).
   */
  protected readonly mostrarEstrategia = computed(
    () =>
      this.auth.tieneModulo('estrategia') &&
      this.auth.tieneAlgunPermiso('planeacion_estrategica:leer', 'objetivo_estrategico:listar'),
  );

  /**
   * Muestra el Tablero solo si la Empresa tiene contratado el Modulo 'reportes-bi'
   * Y el Usuario tiene permiso; se oculta por completo en caso contrario.
   */
  protected readonly mostrarTablero = computed(
    () => this.auth.tieneModulo('reportes-bi') && this.auth.tienePermiso('tablero', 'leer'),
  );

  /** Permiso para ver el indicador de pipeline (oportunidades). */
  protected readonly puedeVerPipeline = computed(
    () => this.auth.tieneModulo('comercial') && this.auth.tienePermiso('oportunidad', 'listar'),
  );
  /** Permiso para ver el indicador de cotizaciones. */
  protected readonly puedeVerCotizaciones = computed(
    () => this.auth.tieneModulo('comercial') && this.auth.tienePermiso('cotizacion', 'listar'),
  );

  /**
   * Muestra el bloque de resumen comercial solo si la Empresa tiene contratado
   * el Modulo 'comercial' Y el Usuario puede listar al menos uno de los recursos;
   * se oculta por completo en caso contrario (degradacion sin clutter).
   */
  protected readonly mostrarComercial = computed(
    () => this.puedeVerPipeline() || this.puedeVerCotizaciones(),
  );

  constructor() {
    this.cargarMiEmpresa();
    this.cargarBranding();
    this.cargarEsencia();
    this.cargarObjetivos();
    this.cargarTablero();
    this.cargarResumenComercial();
  }

  /**
   * Resuelve el nombre de la Empresa para el encabezado (Req 26). Ante cualquier
   * fallo se conserva el nombre neutro por defecto (degradacion elegante); jamas
   * se expone una marca de terceros ni el UUID del Usuario.
   */
  cargarMiEmpresa(): void {
    this.miEmpresaService.consultarMiEmpresa().subscribe({
      next: (empresa) => {
        const nombre = empresa.brandingNombreVisible?.trim() || empresa.nombre?.trim();
        this.nombreEmpresa.set(nombre || NOMBRE_EMPRESA_POR_DEFECTO);
      },
      error: () => this.nombreEmpresa.set(NOMBRE_EMPRESA_POR_DEFECTO),
    });
  }

  // ---------------------------------------------------------------------------
  // Carga de cada bloque (deny-by-default por permiso)
  // ---------------------------------------------------------------------------

  cargarBranding(): void {
    if (!this.auth.tienePermiso('branding', 'leer')) {
      // Sin permiso de branding: se omite el bloque (deny-by-default, Req 3).
      this.branding.set(conDatos<Branding>({ nombreVisible: null, logo: null, colorPrimario: null }));
      return;
    }
    this.branding.set(cargando());
    this.brandingService.consultar().subscribe({
      next: (b) => this.branding.set(conDatos(b)),
      error: (e: HttpErrorResponse) => this.branding.set(conError(mensajeDeError(e))),
    });
  }

  cargarEsencia(): void {
    // Gating por Modulo (deny-by-default): si la Empresa no tiene contratado el
    // Modulo 'estrategia', se omite el bloque SIN llamar al endpoint (evita el
    // 403/500 por Modulo no contratado). Requiere ademas el permiso.
    if (!this.auth.tieneModulo('estrategia') || !this.auth.tienePermiso('planeacion_estrategica', 'leer')) {
      this.esencia.set(conDatos<EsenciaEmpresa>(vaciaEsencia(), true));
      return;
    }
    this.esencia.set(cargando());
    this.estrategiaService.consultarEsencia().subscribe({
      next: (e) => this.esencia.set(conDatos(e)),
      error: (e: HttpErrorResponse) => {
        // 404 = aun no se ha registrado la esencia: se trata como estado vacio.
        if (e.status === 404) {
          this.esencia.set(conDatos<EsenciaEmpresa>(vaciaEsencia(), true));
        } else {
          this.esencia.set(conError(mensajeDeError(e)));
        }
      },
    });
  }

  cargarObjetivos(): void {
    // Gating por Modulo 'estrategia' (deny-by-default) ademas del permiso: sin el
    // Modulo contratado se omite el bloque sin llamar al endpoint.
    if (!this.auth.tieneModulo('estrategia') || !this.auth.tienePermiso('objetivo_estrategico', 'listar')) {
      this.objetivos.set(conDatos<ObjetivoEstrategico[]>([], true));
      return;
    }
    this.objetivos.set(cargando());
    this.estrategiaService.listarObjetivos(null, 0, 5).subscribe({
      next: (pagina) => this.objetivos.set(conDatos(pagina.content, pagina.content.length === 0)),
      error: (e: HttpErrorResponse) => this.objetivos.set(conError(mensajeDeError(e))),
    });
  }

  cargarTablero(): void {
    // Gating por Modulo 'reportes-bi' (deny-by-default) ademas del permiso: sin el
    // Modulo contratado se omite el Tablero sin llamar al endpoint (evita el
    // 403/500 por Modulo no contratado).
    if (!this.auth.tieneModulo('reportes-bi') || !this.auth.tienePermiso('tablero', 'leer')) {
      this.tablero.set(conDatos<Tablero>(vacioTablero(), true));
      return;
    }
    this.tablero.set(cargando());
    this.tableroService.consultar({}).subscribe({
      next: (t) => {
        const sinIndicadores = t.areas.every((a) => a.indicadores.length === 0);
        this.tablero.set(conDatos(t, sinIndicadores));
      },
      error: (e: HttpErrorResponse) => this.tablero.set(conError(mensajeDeError(e))),
    });
  }

  /**
   * Carga el resumen comercial (Req 6.3). Gating por Modulo 'comercial'
   * (deny-by-default) ademas del permiso: sin el Modulo contratado se omite el
   * bloque SIN llamar a los endpoints (evita el 403/500 por Modulo no
   * contratado). Los indicadores se CALCULAN en el cliente a partir de las
   * listas paginadas, igual que la Ficha 360 (no hay endpoint de resumen).
   */
  cargarResumenComercial(): void {
    if (!this.puedeVerPipeline() && !this.puedeVerCotizaciones()) {
      this.resumenComercial.set(conDatos<ResumenComercial>(resumenComercialVacio(), true));
      return;
    }
    this.resumenComercial.set(cargando());

    // Solo se consultan los recursos permitidos; el resto queda en cero.
    const pipeline$ = this.puedeVerPipeline()
      ? this.oportunidadesService.listar({}, 0, TAMANO_RESUMEN_COMERCIAL)
      : of(null);
    const cotizaciones$ = this.puedeVerCotizaciones()
      ? this.cotizacionesService.listar({}, 0, TAMANO_RESUMEN_COMERCIAL)
      : of(null);

    forkJoin({ pipeline: pipeline$, cotizaciones: cotizaciones$ }).subscribe({
      next: ({ pipeline, cotizaciones }) => {
        const abiertas = (pipeline?.content ?? []).filter(
          (o: Oportunidad) => !ETAPAS_CERRADAS.has(o.etapa),
        );
        const resumen: ResumenComercial = {
          valorPipeline: abiertas.reduce((acc, o) => acc + (Number(o.valorEstimado) || 0), 0),
          oportunidadesAbiertas: abiertas.length,
          cotizaciones: (cotizaciones?.content ?? []).length,
        };
        const vacio =
          resumen.oportunidadesAbiertas === 0 && resumen.cotizaciones === 0;
        this.resumenComercial.set(conDatos(resumen, vacio));
      },
      error: (e: HttpErrorResponse) =>
        this.resumenComercial.set(conError(mensajeDeError(e))),
    });
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

/** Tablero vacio por defecto. */
function vacioTablero(): Tablero {
  return { generadoEn: '', desde: null, hasta: null, clienteId: null, areas: [] };
}
