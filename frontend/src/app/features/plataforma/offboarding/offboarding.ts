// =============================================================================
// Vista de Offboarding de plataforma (super_admin) (Req 69)
// -----------------------------------------------------------------------------
// Permite, sobre una Empresa SELECCIONADA por nombre/RFC (nunca por UUID a mano),
// tres operaciones del ciclo de baja con confirmacion para las acciones
// sensibles (Req 54):
//   - Exportar datos de negocio (Req 69.1): descarga un JSON estructurado.
//   - Cancelar e iniciar Periodo_Gracia (Req 69.2).
//   - Eliminar definitivamente tras el Periodo_Gracia (Req 69.3, 69.4).
//
// La Empresa se elige con un autocompletado que busca por nombre o RFC en el
// backend (buscador `q`, con debounce); el identificador (UUID) se usa
// internamente y jamas se pide teclear.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged, switchMap, of, catchError } from 'rxjs';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { EmpresasService } from '../services/empresas.service';
import { Empresa, EstadoEmpresa } from '../models/plataforma.models';

@Component({
  selector: 'app-plataforma-offboarding',
  imports: [
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    PageHeader,
  ],
  templateUrl: './offboarding.html',
  styleUrl: './offboarding.scss',
})
export class PlataformaOffboarding {
  private readonly service = inject(EmpresasService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly procesando = signal(false);

  protected readonly puedeExportar = this.auth.tienePermiso('offboarding', 'exportar');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('offboarding', 'cambiar_estado');

  /** Texto de busqueda (nombre o RFC). */
  protected readonly busqueda = signal('');
  /** Empresa seleccionada sobre la que operar; su id se usa internamente. */
  protected readonly seleccionada = signal<Empresa | null>(null);
  /** Indica una busqueda en curso (para el estado de carga del autocompletado). */
  protected readonly buscando = signal(false);

  /** Milisegundos en un dia (para derivar los dias restantes de gracia). */
  private static readonly MS_POR_DIA = 24 * 60 * 60 * 1000;

  /**
   * Indica si la Empresa seleccionada esta cancelada y tiene definido el fin de
   * su periodo de gracia (unica situacion en que se muestra el panel informativo).
   */
  protected readonly enPeriodoGracia = computed(() => {
    const empresa = this.seleccionada();
    return empresa?.estado === 'cancelada' && empresa.finPeriodoGracia !== null;
  });

  /**
   * Dias restantes del periodo de gracia, DERIVADOS del instante `finPeriodoGracia`
   * que envia el backend frente al momento actual (nunca se fija una duracion en
   * el frontend). Se redondea hacia arriba (Math.ceil) para contar el dia en curso:
   *   > 0  → aun dentro del periodo (faltan N dias para poder eliminar).
   *   <= 0 → el periodo vencio (ya se puede eliminar definitivamente).
   * Devuelve `null` cuando no aplica (Empresa no cancelada o sin fin definido).
   */
  protected readonly diasRestantesGracia = computed<number | null>(() => {
    const empresa = this.seleccionada();
    if (empresa?.estado !== 'cancelada' || !empresa.finPeriodoGracia) {
      return null;
    }
    const fin = new Date(empresa.finPeriodoGracia).getTime();
    if (Number.isNaN(fin)) {
      return null;
    }
    return Math.ceil((fin - Date.now()) / PlataformaOffboarding.MS_POR_DIA);
  });

  /**
   * `true` cuando el periodo de gracia ya vencio (dias restantes <= 0). Es la
   * condicion que, junto al estado `cancelada`, habilita la eliminacion definitiva.
   */
  protected readonly graciaVencida = computed(() => {
    const dias = this.diasRestantesGracia();
    return dias !== null && dias <= 0;
  });

  /** Habilita "Eliminar definitivamente": solo si esta cancelada y vencio la gracia. */
  protected readonly puedeEliminar = computed(() => {
    const empresa = this.seleccionada();
    return empresa?.estado === 'cancelada' && this.graciaVencida();
  });

  /** `true` cuando la Empresa ya esta cancelada (la cancelacion es idempotente en UX). */
  protected readonly yaCancelada = computed(() => this.seleccionada()?.estado === 'cancelada');

  /**
   * Motivo por el que "Eliminar definitivamente" esta deshabilitado, para el
   * tooltip/ayuda accesible. Cadena vacia cuando el boton esta habilitado.
   */
  protected readonly motivoEliminarBloqueado = computed(() => {
    const empresa = this.seleccionada();
    if (!empresa) {
      return 'Selecciona una empresa.';
    }
    if (empresa.estado !== 'cancelada') {
      return 'Primero cancela la empresa para iniciar el periodo de gracia.';
    }
    if (!this.graciaVencida()) {
      return 'Disponible cuando venza el periodo de gracia.';
    }
    return '';
  });

  /**
   * Resultados del autocompletado: reacciona al texto de busqueda con debounce y
   * consulta el buscador del backend (nombre/RFC). Se ignoran textos muy cortos
   * para no listar todo con una sola letra.
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
        return this.service.listar(null, termino, 0, 10).pipe(
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

  /** Fija la Empresa elegida del autocompletado. */
  protected alSeleccionar(evento: MatAutocompleteSelectedEvent): void {
    const empresa = evento.option.value as Empresa;
    this.seleccionada.set(empresa);
    this.busqueda.set('');
  }

  /** Limpia la Empresa seleccionada para elegir otra. */
  protected limpiarSeleccion(): void {
    this.seleccionada.set(null);
    this.busqueda.set('');
  }

  /** Etiqueta legible del estado de la Empresa. */
  protected etiquetaEstado(estado: EstadoEmpresa): string {
    switch (estado) {
      case 'activa':
        return 'Activa';
      case 'suspendida':
        return 'Suspendida';
      case 'cancelada':
        return 'Cancelada';
    }
  }

  /** Iniciales para el avatar cuando la Empresa no tiene logo. */
  protected iniciales(nombre: string): string {
    return nombre
      .split(/\s+/)
      .filter((p) => p.length > 0)
      .slice(0, 2)
      .map((p) => p.charAt(0).toUpperCase())
      .join('');
  }

  /** Exporta los datos de negocio de la Empresa (Req 69.1) y ofrece la descarga. */
  exportar(): void {
    const empresa = this.seleccionada();
    if (!empresa) {
      return;
    }
    this.procesando.set(true);
    this.service.exportar(empresa.id).subscribe({
      next: (datos) => {
        this.procesando.set(false);
        this.descargarJson(datos, `exportacion-${empresa.rfc}.json`);
        this.toast.exito('Exportacion generada. Se inicio la descarga del archivo.');
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /** Cancela la Empresa e inicia el Periodo_Gracia con confirmacion (Req 69.2). */
  async cancelar(): Promise<void> {
    const empresa = this.seleccionada();
    if (!empresa) {
      return;
    }
    const ok = await this.confirm.confirmar({
      titulo: 'Cancelar empresa',
      mensaje:
        `Se cancelara "${empresa.nombre}" e iniciara su periodo de gracia. Durante ese periodo se puede revertir. Deseas continuar?`,
      textoConfirmar: 'Cancelar empresa',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.procesando.set(true);
    this.service.cancelar(empresa.id).subscribe({
      next: (actualizada) => {
        this.procesando.set(false);
        this.seleccionada.set(actualizada);
        this.toast.exito('Empresa cancelada. Se inicio el periodo de gracia.');
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /** Elimina definitivamente los datos tras el Periodo_Gracia con doble aviso (Req 69.3). */
  async eliminar(): Promise<void> {
    const empresa = this.seleccionada();
    if (!empresa) {
      return;
    }
    const ok = await this.confirm.confirmar({
      titulo: 'Eliminar datos definitivamente',
      mensaje:
        `Esta accion elimina o anonimiza los datos de negocio de "${empresa.nombre}" de forma irreversible (se preservan los comprobantes fiscales). Solo procede tras vencer el periodo de gracia. Deseas continuar?`,
      textoConfirmar: 'Eliminar definitivamente',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.procesando.set(true);
    this.service.eliminarDefinitivamente(empresa.id).subscribe({
      next: () => {
        this.procesando.set(false);
        this.toast.exito('Datos de negocio eliminados definitivamente.');
      },
      error: (e: HttpErrorResponse) => {
        this.procesando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /** Dispara la descarga en el navegador de un objeto JSON como archivo. */
  private descargarJson(datos: unknown, nombreArchivo: string): void {
    const blob = new Blob([JSON.stringify(datos, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombreArchivo;
    enlace.click();
    URL.revokeObjectURL(url);
  }
}
