// =============================================================================
// Panel "Plan y Suscripcion" de una Empresa (super_admin)
// (plan-suscripcion-empresa-super-admin) (Req 5, 7, 8, 10, 11, 12)
// -----------------------------------------------------------------------------
// Como la vista de Empresas es un listado (no hay ruta de ficha/detalle), este
// panel se abre como DIALOGO desde la fila de una Empresa (igual que "Cambiar
// giro"). Recibe la `Empresa` y, al abrir, carga en paralelo:
//   - las suscripciones de la Empresa (SuscripcionesService.listarPorEmpresa),
//     para disponer del detalle completo (modulosHabilitados, monedaFacturacion)
//     y del `id` para las acciones;
//   - el catalogo de planes (PlanesService.listarPlanes(0,100)), para resolver
//     el detalle del plan vigente (moneda, maxUsuarios, total) y alimentar el
//     selector de asignar/cambiar plan;
//   - el catalogo de modulos (PlanesService.listarModulos), para etiquetar las
//     claves de modulos por su nombre humano (Req 5.7).
// Determina la SUSCRIPCION VIGENTE con la misma regla del backend (activa; si no,
// la de vigenciaInicio mas reciente; desempate por createdAt mas reciente) y
// muestra el plan vigente y la suscripcion actual en lenguaje claro, SIN exponer
// ningun UUID (Req 5.6). Concentra las acciones (asignar/cambiar plan, actualizar
// vigencia, activar/suspender) gated por permiso; en exito recarga el panel y
// marca que hubo cambio. Al cerrar devuelve un indicador booleano de cambio para
// que la vista de Empresas refresque el listado (Req 12.1). La accion Cancelar
// (destructiva/irreversible) la cablea la tarea 9.3.
// Accesible (headings, focus, aria en errores), responsive y solo con tokens del
// Sistema de Diseno; espanol es-MX.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { provideFechaIsoDatepicker } from '../../../shared/date/provide-fecha-iso';
import { PlanesService } from '../services/planes.service';
import { SuscripcionesService } from '../services/suscripciones.service';
import {
  Empresa,
  EstadoSuscripcion,
  Plan,
  Suscripcion,
  TipoInstrumento,
} from '../models/plataforma.models';
import { AsignarPlanDialog, AsignarPlanDialogData } from './asignar-plan-dialog';
import { VigenciaDialog, VigenciaDialogData } from './vigencia-dialog';

/** Datos de entrada del panel: la Empresa cuyo plan/suscripcion se administra. */
export interface PlanSuscripcionDialogData {
  empresa: Empresa;
}

/** Etiquetas en espanol de los estados de una Suscripcion (Req 5.3, 7.1). */
const ETIQUETAS_ESTADO: Record<EstadoSuscripcion, string> = {
  activa: 'Activa',
  en_prueba: 'En prueba',
  suspendida: 'Suspendida',
  cancelada: 'Cancelada',
  vencida: 'Vencida',
};

@Component({
  selector: 'app-plan-suscripcion-dialog',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatProgressSpinnerModule,
  ],
  providers: [provideFechaIsoDatepicker()],
  templateUrl: './plan-suscripcion-dialog.html',
  styleUrl: './plan-suscripcion-dialog.scss',
})
export class PlanSuscripcionDialog {
  private readonly fb = inject(FormBuilder);
  private readonly suscripcionesService = inject(SuscripcionesService);
  private readonly planesService = inject(PlanesService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly dialogRef = inject(MatDialogRef<PlanSuscripcionDialog, boolean>);
  protected readonly data = inject<PlanSuscripcionDialogData>(MAT_DIALOG_DATA);

  /** Estados de carga / error del panel (Req: carga controlada sin romper). */
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  /** `true` cuando una accion esta en curso (activar/suspender). */
  protected readonly procesando = signal(false);

  /** `true` mientras se muestra el capturador de nueva fecha para extender la prueba (Req 8.1). */
  protected readonly extendiendoPrueba = signal(false);

  /** Formulario para capturar la nueva `vigenciaFin` al extender el periodo de prueba. */
  protected readonly formularioExtender = this.fb.nonNullable.group({
    nuevaVigenciaFin: ['', [Validators.required]],
  });

  /** Catalogo de planes (para el selector y el detalle del plan vigente). */
  private readonly planes = signal<Plan[]>([]);
  /** Mapa clave -> nombre visible del modulo (Req 5.7). */
  private readonly etiquetasModulo = signal<Map<string, string>>(new Map());
  /** Suscripcion vigente resuelta con la regla de la feature (o null). */
  protected readonly vigente = signal<Suscripcion | null>(null);

  /** Indica si hubo algun cambio (para refrescar el listado al cerrar, Req 12.1). */
  private huboCambio = false;

  // --- Permisos (RBAC UI, Req 10) -------------------------------------------
  protected readonly puedeAsignar = this.auth.tienePermiso('suscripcion', 'crear');
  protected readonly puedeActualizarVigencia = this.auth.tienePermiso('suscripcion', 'actualizar');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('suscripcion', 'cambiar_estado');

  constructor() {
    this.cargar();
  }

  /**
   * Plan vigente completo resuelto del catalogo por el `planId` de la
   * suscripcion vigente, para mostrar moneda, maximo de usuarios y total.
   */
  protected readonly planVigente = computed<Plan | undefined>(() => {
    const sub = this.vigente();
    if (!sub) {
      return undefined;
    }
    return this.planes().find((p) => p.id === sub.planId);
  });

  /**
   * Nombre del plan vigente: preferentemente del catalogo; si no se resolvio,
   * el nombre que ya trae el `EmpresaDto` enriquecido (patron NO-UUID). Nunca UUID.
   */
  protected readonly nombrePlanVigente = computed<string>(() => {
    return (
      this.data.empresa.planVigente?.nombreInstrumento ??
      this.planVigente()?.nombre ??
      this.data.empresa.planVigente?.nombrePlan ??
      'Sin plan'
    );
  });

  /**
   * Tipo del instrumento vigente derivado del contrato (Req 1.4): 'plan' o
   * 'suscripcion'. Prefiere el dato del `EmpresaDto.planVigente` (fuente de
   * verdad del backend) y cae en el de la suscripcion vigente cargada.
   */
  protected readonly tipoInstrumento = computed<TipoInstrumento | null>(() => {
    return this.data.empresa.planVigente?.tipoInstrumento ?? this.vigente()?.tipoInstrumento ?? null;
  });

  /** Etiqueta humana del tipo de instrumento vigente ('Plan' | 'Suscripcion'). */
  protected readonly tipoInstrumentoEtiqueta = computed<string | null>(() => {
    const tipo = this.tipoInstrumento();
    if (!tipo) {
      return null;
    }
    return tipo === 'plan' ? 'Plan' : 'Suscripción';
  });

  /**
   * `true` cuando el contrato vigente esta dentro de su periodo de prueba
   * (estado EN_PRUEBA). Combina el dato del backend con el estado cargado.
   */
  protected readonly enPrueba = computed<boolean>(() => {
    if (this.data.empresa.planVigente?.enPrueba) {
      return true;
    }
    return this.vigente()?.estado === 'en_prueba';
  });

  /**
   * `true` cuando el contrato vigente esta por vencer (dentro del umbral de
   * aviso configurable calculado por el backend) (Req 8.3).
   */
  protected readonly porVencer = computed<boolean>(() => {
    return this.data.empresa.planVigente?.porVencer === true;
  });

  /**
   * Dias restantes hasta `vigenciaFin`. Prefiere el dato del backend
   * (`planVigente.diasRestantes`) y, si no esta disponible, lo calcula contra
   * la fecha de hoy a partir de la `vigenciaFin` de la suscripcion vigente.
   * `null` cuando no hay `vigenciaFin`.
   */
  protected readonly diasRestantes = computed<number | null>(() => {
    const backend = this.data.empresa.planVigente?.diasRestantes;
    if (backend !== undefined && backend !== null) {
      return backend;
    }
    const fin = this.vigente()?.vigenciaFin ?? null;
    if (!fin) {
      return null;
    }
    return this.diferenciaEnDias(this.hoyIso(), fin);
  });

  /**
   * Modulos habilitados de la suscripcion vigente por etiqueta humana; si la
   * suscripcion no los define (null), hereda los del plan vigente (Req 5.7).
   */
  protected readonly modulosSuscripcion = computed<string[]>(() => {
    const sub = this.vigente();
    if (!sub) {
      return [];
    }
    const claves = sub.modulosHabilitados ?? this.planVigente()?.modulosHabilitados ?? [];
    return claves.map((clave) => this.nombreModulo(clave));
  });

  /** `true` cuando los modulos mostrados se heredan del plan (la suscripcion no los define). */
  protected readonly modulosHeredadosDelPlan = computed<boolean>(() => {
    const sub = this.vigente();
    return !!sub && sub.modulosHabilitados === null;
  });

  /** Modulos del plan vigente por etiqueta humana (bloque de plan vigente). */
  protected readonly modulosPlan = computed<string[]>(() => {
    return (this.planVigente()?.modulosHabilitados ?? []).map((clave) => this.nombreModulo(clave));
  });

  /** Etiqueta en espanol del estado de la suscripcion vigente. */
  protected etiquetaEstado(estado: EstadoSuscripcion): string {
    return ETIQUETAS_ESTADO[estado];
  }

  /**
   * Estado DERIVADO que se muestra al usuario (Req 7.1, 7.2): refleja "En prueba"
   * cuando el contrato esta EN_PRUEBA y "Vencida" cuando esta vencido. La caducidad
   * se determina con corte ESTRICTO igual que el backend: `vigenciaFin < hoy` es
   * vencida (si `vigenciaFin === hoy` NO esta vencida). Para lo demas usa la
   * etiqueta normal del estado.
   */
  protected estadoMostrado(sub: Suscripcion): string {
    if (this.esVencida(sub)) {
      return ETIQUETAS_ESTADO.vencida;
    }
    if (sub.estado === 'en_prueba' || this.data.empresa.planVigente?.enPrueba) {
      return ETIQUETAS_ESTADO.en_prueba;
    }
    return ETIQUETAS_ESTADO[sub.estado];
  }

  /**
   * `true` cuando la suscripcion esta vencida. Prefiere el dato derivado del
   * backend (`planVigente.vencida`); si no, compara `vigenciaFin` con hoy con
   * corte estricto (`vigenciaFin < hoy`). Estados finales (cancelada) no se
   * consideran vencidos.
   */
  protected esVencida(sub: Suscripcion): boolean {
    if (sub.estado === 'cancelada') {
      return false;
    }
    if (sub.estado === 'vencida' || this.data.empresa.planVigente?.vencida) {
      return true;
    }
    const fin = sub.vigenciaFin;
    if (!fin) {
      return false;
    }
    return fin < this.hoyIso();
  }

  /** Fecha de hoy en formato ISO `YYYY-MM-DD` (hora local). */
  private hoyIso(): string {
    const hoy = new Date();
    const anio = hoy.getFullYear();
    const mes = String(hoy.getMonth() + 1).padStart(2, '0');
    const dia = String(hoy.getDate()).padStart(2, '0');
    return `${anio}-${mes}-${dia}`;
  }

  /** Dias entre dos fechas ISO `YYYY-MM-DD` (desde - hasta), positivo si `hasta` es futuro. */
  private diferenciaEnDias(desde: string, hasta: string): number {
    const msPorDia = 24 * 60 * 60 * 1000;
    const inicio = Date.parse(`${desde}T00:00:00`);
    const fin = Date.parse(`${hasta}T00:00:00`);
    return Math.round((fin - inicio) / msPorDia);
  }

  /** Resuelve la clave de un modulo a su nombre visible; si no esta, la propia clave (Req 5.7). */
  protected nombreModulo(clave: string): string {
    return this.etiquetasModulo().get(clave) ?? clave;
  }

  /**
   * Carga (o recarga) el panel: suscripciones de la Empresa + catalogo de planes
   * + catalogo de modulos, en paralelo. Ante fallo, muestra un mensaje sin romper.
   */
  private cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    forkJoin({
      suscripciones: this.suscripcionesService.listarPorEmpresa(this.data.empresa.id),
      planes: this.planesService.listarPlanes(0, 100),
      modulos: this.planesService.listarModulos(),
    }).subscribe({
      next: ({ suscripciones, planes, modulos }) => {
        this.planes.set(planes.content);
        this.etiquetasModulo.set(new Map(modulos.map((m) => [m.clave, m.nombreVisible])));
        this.vigente.set(this.suscripcionVigente(suscripciones));
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }

  /**
   * Determina la suscripcion vigente con la regla de la feature (Req 3, replicada
   * del backend): la `activa`; si no hay ninguna activa, la de `vigenciaInicio`
   * mas reciente; desempate por `createdAt` mas reciente. `null` si no hay ninguna.
   */
  private suscripcionVigente(subs: Suscripcion[]): Suscripcion | null {
    if (subs.length === 0) {
      return null;
    }
    const activa = subs.find((s) => s.estado === 'activa');
    if (activa) {
      return activa;
    }
    return subs.reduce((mejor, actual) => {
      if (actual.vigenciaInicio > mejor.vigenciaInicio) {
        return actual;
      }
      if (actual.vigenciaInicio === mejor.vigenciaInicio && actual.createdAt > mejor.createdAt) {
        return actual;
      }
      return mejor;
    });
  }

  /** Abre el dialogo de asignar/cambiar plan; al exito recarga y marca cambio (Req 6). */
  protected asignarPlan(): void {
    const data: AsignarPlanDialogData = {
      empresa: this.data.empresa,
      planes: this.planes(),
      planIdActual: this.vigente()?.planId ?? null,
    };
    const ref = this.dialog.open(AsignarPlanDialog, {
      width: 'min(560px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      data,
    });
    ref.afterClosed().subscribe((suscripcion?: Suscripcion) => {
      if (suscripcion) {
        this.toast.exito('Plan asignado correctamente.');
        this.marcarCambioYRecargar();
      }
    });
  }

  /** Abre el dialogo de actualizar vigencia; al exito recarga y marca cambio (Req 8). */
  protected actualizarVigencia(): void {
    const sub = this.vigente();
    if (!sub) {
      return;
    }
    const data: VigenciaDialogData = {
      suscripcionId: sub.id,
      vigenciaInicioActual: sub.vigenciaInicio,
      vigenciaFinActual: sub.vigenciaFin,
      empresaNombre: this.data.empresa.nombre,
    };
    const ref = this.dialog.open(VigenciaDialog, {
      width: 'min(520px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      data,
    });
    ref.afterClosed().subscribe((suscripcion?: Suscripcion) => {
      if (suscripcion) {
        this.toast.exito('Vigencia actualizada correctamente.');
        this.marcarCambioYRecargar();
      }
    });
  }

  /** Activa la suscripcion vigente (POST /suscripciones/{id}/activar) (Req 7.2). */
  protected activar(): void {
    const sub = this.vigente();
    if (!sub) {
      return;
    }
    this.procesando.set(true);
    this.suscripcionesService.activar(sub.id).subscribe({
      next: () => {
        this.procesando.set(false);
        this.toast.exito('Suscripcion activada.');
        this.marcarCambioYRecargar();
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /** Suspende la suscripcion vigente (POST /suscripciones/{id}/suspender) (Req 7.3). */
  protected suspender(): void {
    const sub = this.vigente();
    if (!sub) {
      return;
    }
    this.procesando.set(true);
    this.suscripcionesService.suspender(sub.id).subscribe({
      next: () => {
        this.procesando.set(false);
        this.toast.exito('Suscripcion suspendida.');
        this.marcarCambioYRecargar();
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /**
   * Cancela la suscripcion vigente (POST /suscripciones/{id}/cancelar). Es una
   * accion DESTRUCTIVA e IRREVERSIBLE (estado final): exige confirmacion explicita
   * que advierte que no podra reactivarse antes de invocar el backend (Req 7.4).
   */
  protected async cancelar(): Promise<void> {
    const sub = this.vigente();
    if (!sub) {
      return;
    }
    const ok = await this.confirm.confirmar({
      titulo: 'Cancelar suscripción',
      mensaje:
        'Cancelar la suscripción es IRREVERSIBLE (estado final): la empresa quedará sin plan vigente y no podrá reactivarse. ¿Deseas continuar?',
      textoConfirmar: 'Cancelar suscripción',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.procesando.set(true);
    this.suscripcionesService.cancelar(sub.id).subscribe({
      next: () => {
        this.procesando.set(false);
        this.toast.exito('Suscripción cancelada.');
        this.marcarCambioYRecargar();
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /**
   * Activa la FACTURACION de un contrato EN_PRUEBA (POST
   * /suscripciones/{id}/activar-facturacion), finalizando el periodo de prueba
   * (Req 8.1). Envia cuerpo vacio para que el backend aplique sus valores por
   * defecto. En exito recarga y marca cambio.
   */
  protected activarFacturacion(): void {
    const sub = this.vigente();
    if (!sub) {
      return;
    }
    this.procesando.set(true);
    this.suscripcionesService.activarFacturacion(sub.id, {}).subscribe({
      next: () => {
        this.procesando.set(false);
        this.toast.exito('Facturación activada.');
        this.marcarCambioYRecargar();
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /**
   * Abre el capturador inline de nueva fecha de fin para extender el periodo de
   * prueba (Req 8.1). Preselecciona la `vigenciaFin` vigente cuando existe. La
   * captura ocurre dentro del propio panel (datepicker ISO `YYYY-MM-DD`), sin
   * abrir el dialogo de vigencia (que persistiria un cambio distinto).
   */
  protected iniciarExtenderPrueba(): void {
    const sub = this.vigente();
    if (!sub) {
      return;
    }
    this.formularioExtender.reset({ nuevaVigenciaFin: sub.vigenciaFin ?? '' });
    this.extendiendoPrueba.set(true);
  }

  /** Cancela la captura de nueva fecha y oculta el capturador inline. */
  protected cancelarExtenderPrueba(): void {
    this.extendiendoPrueba.set(false);
  }

  /**
   * Confirma la extension del PERIODO DE PRUEBA (POST
   * /suscripciones/{id}/extender-prueba) con la nueva `vigenciaFin` capturada
   * (ISO `YYYY-MM-DD`). En exito recarga y marca cambio; el backend impone las
   * reglas (422/404), cuyo mensaje se muestra vía toast.
   */
  protected confirmarExtenderPrueba(): void {
    const sub = this.vigente();
    if (!sub) {
      return;
    }
    if (this.formularioExtender.invalid) {
      this.formularioExtender.markAllAsTouched();
      return;
    }
    const { nuevaVigenciaFin } = this.formularioExtender.getRawValue();
    this.procesando.set(true);
    this.suscripcionesService.extenderPrueba(sub.id, { nuevaVigenciaFin }).subscribe({
      next: () => {
        this.procesando.set(false);
        this.extendiendoPrueba.set(false);
        this.toast.exito('Periodo de prueba extendido.');
        this.marcarCambioYRecargar();
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /** Marca que hubo cambio (para refrescar el listado al cerrar) y recarga el panel. */
  private marcarCambioYRecargar(): void {
    this.huboCambio = true;
    this.cargar();
  }

  /** Reintenta la carga tras un error de carga inicial. */
  protected reintentar(): void {
    this.cargar();
  }

  /** Cierra el panel devolviendo si hubo algun cambio (Req 12.1). */
  protected cerrar(): void {
    this.dialogRef.close(this.huboCambio);
  }
}
