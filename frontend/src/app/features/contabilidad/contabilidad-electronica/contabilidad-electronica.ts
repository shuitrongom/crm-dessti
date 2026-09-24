// =============================================================================
// Vista de Contabilidad Electrónica SAT (Anexo 24) — enterprise
// -----------------------------------------------------------------------------
// Permite seleccionar el periodo (mes/año) y, para cada uno de los tres XML del
// SAT (Catálogo de cuentas, Balanza de comprobación y Pólizas del periodo),
// consultar una vista previa con advertencias y descargar el XML oficial.
// Reutiliza el catálogo de cuentas y las pólizas del backend; el gating por
// módulo 'contabilidad' y el permiso contabilidad_electronica gobiernan el acceso.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { mensajeDeError } from '../../../core/services/error-mensajes';

import { ContabilidadElectronicaService, ArchivoDescargado } from '../services/contabilidad-electronica.service';
import {
  VistaPreviaBalanza,
  VistaPreviaCatalogo,
  VistaPreviaPolizas,
} from '../models/contabilidad-electronica.models';

@Component({
  selector: 'app-contabilidad-electronica',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
  ],
  templateUrl: './contabilidad-electronica.html',
  styleUrl: '../contabilidad.scss',
})
export class ContabilidadElectronica {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ContabilidadElectronicaService);

  /** Meses del año para el selector (es-MX). */
  protected readonly meses = [
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

  private readonly hoy = new Date();

  protected readonly formPeriodo = this.fb.nonNullable.group({
    anio: [this.hoy.getFullYear(), [Validators.required, Validators.min(2000), Validators.max(2999)]],
    mes: [this.hoy.getMonth() + 1, [Validators.required, Validators.min(1), Validators.max(12)]],
  });

  // Estado de cada tarjeta (vista previa, carga, error).
  protected readonly previaCatalogo = signal<VistaPreviaCatalogo | null>(null);
  protected readonly previaBalanza = signal<VistaPreviaBalanza | null>(null);
  protected readonly previaPolizas = signal<VistaPreviaPolizas | null>(null);

  protected readonly cargando = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  // --- Vistas previa ---------------------------------------------------------

  previewCatalogo(): void {
    this.iniciar('preview-catalogo');
    this.service.vistaPreviaCatalogo().subscribe({
      next: (r) => {
        this.previaCatalogo.set(r);
        this.terminar();
      },
      error: (e: HttpErrorResponse) => this.fallar(e),
    });
  }

  previewBalanza(): void {
    if (!this.periodoValido()) return;
    const { anio, mes } = this.formPeriodo.getRawValue();
    this.iniciar('preview-balanza');
    this.service.vistaPreviaBalanza(anio, mes).subscribe({
      next: (r) => {
        this.previaBalanza.set(r);
        this.terminar();
      },
      error: (e: HttpErrorResponse) => this.fallar(e),
    });
  }

  previewPolizas(): void {
    if (!this.periodoValido()) return;
    const { anio, mes } = this.formPeriodo.getRawValue();
    this.iniciar('preview-polizas');
    this.service.vistaPreviaPolizas(anio, mes).subscribe({
      next: (r) => {
        this.previaPolizas.set(r);
        this.terminar();
      },
      error: (e: HttpErrorResponse) => this.fallar(e),
    });
  }

  // --- Descargas -------------------------------------------------------------

  descargarCatalogo(): void {
    if (!this.periodoValido()) return;
    const { anio, mes } = this.formPeriodo.getRawValue();
    this.iniciar('xml-catalogo');
    this.service.descargarCatalogo(anio, mes).subscribe({
      next: (a) => this.guardar(a),
      error: (e: HttpErrorResponse) => this.fallar(e),
    });
  }

  descargarBalanza(): void {
    if (!this.periodoValido()) return;
    const { anio, mes } = this.formPeriodo.getRawValue();
    this.iniciar('xml-balanza');
    this.service.descargarBalanza(anio, mes).subscribe({
      next: (a) => this.guardar(a),
      error: (e: HttpErrorResponse) => this.fallar(e),
    });
  }

  descargarPolizas(): void {
    if (!this.periodoValido()) return;
    const { anio, mes } = this.formPeriodo.getRawValue();
    this.iniciar('xml-polizas');
    this.service.descargarPolizas(anio, mes).subscribe({
      next: (a) => this.guardar(a),
      error: (e: HttpErrorResponse) => this.fallar(e),
    });
  }

  protected estaCargando(accion: string): boolean {
    return this.cargando() === accion;
  }

  // --- Interno ---------------------------------------------------------------

  private periodoValido(): boolean {
    if (this.formPeriodo.invalid) {
      this.formPeriodo.markAllAsTouched();
      return false;
    }
    return true;
  }

  private iniciar(accion: string): void {
    this.cargando.set(accion);
    this.error.set(null);
  }

  private terminar(): void {
    this.cargando.set(null);
  }

  private fallar(e: HttpErrorResponse): void {
    this.cargando.set(null);
    this.error.set(mensajeDeError(e));
  }

  /** Dispara la descarga del archivo en el navegador. */
  private guardar(archivo: ArchivoDescargado): void {
    const url = URL.createObjectURL(archivo.blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = archivo.nombreArchivo;
    document.body.appendChild(enlace);
    enlace.click();
    document.body.removeChild(enlace);
    URL.revokeObjectURL(url);
    this.terminar();
  }
}
