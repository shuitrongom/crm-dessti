// =============================================================================
// Vista de Cierre de periodo contable (candado contable) — enterprise
// -----------------------------------------------------------------------------
// Muestra los 12 meses de un año con su estado (abierto/cerrado) y permite:
//   - Cerrar un mes abierto (el backend valida que la balanza cuadre; 422 si no).
//   - Reabrir un mes cerrado indicando un motivo obligatorio (reapertura auditada).
// El gating por módulo 'contabilidad' y el permiso periodo_contable gobiernan el
// acceso. es-MX, WCAG 2.1 AA.
// =============================================================================

import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { CierrePeriodoService } from '../services/cierre-periodo.service';
import { PeriodoContable } from '../models/cierre-periodo.models';

@Component({
  selector: 'app-cierre-periodo',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
  ],
  templateUrl: './cierre-periodo.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../contabilidad.scss',
})
export class ContabilidadCierrePeriodo {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CierrePeriodoService);
  private readonly overlay = inject(OperacionOverlayService);

  /** Nombres de los meses (es-MX), indexados por su número 1..12. */
  protected readonly nombresMes = [
    '',
    'Enero',
    'Febrero',
    'Marzo',
    'Abril',
    'Mayo',
    'Junio',
    'Julio',
    'Agosto',
    'Septiembre',
    'Octubre',
    'Noviembre',
    'Diciembre',
  ];

  private readonly hoy = new Date();

  protected readonly formAnio = this.fb.nonNullable.group({
    anio: [
      this.hoy.getFullYear(),
      [Validators.required, Validators.min(2000), Validators.max(2100)],
    ],
  });

  /** Formulario de reapertura (motivo) del mes en edición. */
  protected readonly formReapertura = this.fb.nonNullable.group({
    motivo: ['', [Validators.required, Validators.maxLength(500)]],
  });

  protected readonly periodos = signal<PeriodoContable[]>([]);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  /** Mes (1..12) cuya reapertura se está capturando, o null si ninguno. */
  protected readonly reabriendoMes = signal<number | null>(null);
  /** Acción en curso identificada por "cerrar-<mes>" o "reabrir-<mes>". */
  protected readonly accionEnCurso = signal<string | null>(null);

  constructor() {
    this.consultar();
  }

  /** Consulta los 12 meses del año seleccionado. */
  consultar(): void {
    if (this.formAnio.invalid) {
      this.formAnio.markAllAsTouched();
      return;
    }
    const anio = this.formAnio.getRawValue().anio;
    this.cargando.set(true);
    this.error.set(null);
    this.aviso.set(null);
    this.reabriendoMes.set(null);
    this.service.listar(anio).subscribe({
      next: (lista) => {
        this.periodos.set(lista);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => this.fallar(e),
    });
  }

  /** Cierra el mes indicado (el backend valida el cuadre de la balanza). */
  cerrar(mes: number): void {
    const anio = this.formAnio.getRawValue().anio;
    this.accionEnCurso.set(`cerrar-${mes}`);
    this.error.set(null);
    this.aviso.set(null);
    this.overlay
      .ejecutar(this.service.cerrar(anio, mes), {
        tipo: 'procesar',
        textoProceso: 'Cerrando periodo…',
        textoExito: 'Periodo cerrado',
      })
      .subscribe({
        next: (p) => {
          this.reemplazar(p);
          this.accionEnCurso.set(null);
          this.aviso.set(`Periodo ${this.etiqueta(anio, mes)} cerrado correctamente.`);
        },
        error: (e: HttpErrorResponse) => this.fallar(e),
      });
  }

  /** Abre el formulario de reapertura para el mes indicado. */
  iniciarReapertura(mes: number): void {
    this.formReapertura.reset({ motivo: '' });
    this.error.set(null);
    this.aviso.set(null);
    this.reabriendoMes.set(mes);
  }

  /** Cancela la captura de reapertura. */
  cancelarReapertura(): void {
    this.reabriendoMes.set(null);
    this.formReapertura.reset({ motivo: '' });
  }

  /** Confirma la reapertura del mes en edición con el motivo capturado. */
  confirmarReapertura(mes: number): void {
    if (this.formReapertura.invalid) {
      this.formReapertura.markAllAsTouched();
      return;
    }
    const anio = this.formAnio.getRawValue().anio;
    const motivo = this.formReapertura.getRawValue().motivo.trim();
    this.accionEnCurso.set(`reabrir-${mes}`);
    this.error.set(null);
    this.aviso.set(null);
    this.overlay
      .ejecutar(this.service.reabrir(anio, mes, motivo), {
        tipo: 'procesar',
        textoProceso: 'Reabriendo periodo…',
        textoExito: 'Periodo reabierto',
      })
      .subscribe({
        next: (p) => {
          this.reemplazar(p);
          this.accionEnCurso.set(null);
          this.reabriendoMes.set(null);
          this.formReapertura.reset({ motivo: '' });
          this.aviso.set(`Periodo ${this.etiqueta(anio, mes)} reabierto correctamente.`);
        },
        error: (e: HttpErrorResponse) => this.fallar(e),
      });
  }

  protected estaCerrado(p: PeriodoContable): boolean {
    return p.estado === 'cerrado';
  }

  protected accion(tipo: string, mes: number): boolean {
    return this.accionEnCurso() === `${tipo}-${mes}`;
  }

  protected nombreMes(mes: number): string {
    return this.nombresMes[mes] ?? String(mes);
  }

  // --- Interno ---------------------------------------------------------------

  private etiqueta(anio: number, mes: number): string {
    return `${anio}-${String(mes).padStart(2, '0')}`;
  }

  private reemplazar(actualizado: PeriodoContable): void {
    this.periodos.update((lista) =>
      lista.map((p) => (p.mes === actualizado.mes ? actualizado : p)),
    );
  }

  private fallar(e: HttpErrorResponse): void {
    this.cargando.set(false);
    this.accionEnCurso.set(null);
    this.error.set(mensajeDeError(e));
  }
}
