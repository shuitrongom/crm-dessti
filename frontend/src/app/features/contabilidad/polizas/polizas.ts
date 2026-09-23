// =============================================================================
// Vista de Polizas contables (Req 38)
// -----------------------------------------------------------------------------
// Listado paginado filtrable por rango de fechas; alta de una poliza balanceada
// (cargos == abonos, lo valida el backend) y reverso de una poliza existente.
// La suma de cargos/abonos del formulario se muestra SOLO como previsualizacion
// (etiquetada), calculada con centavos enteros para evitar errores de float; el
// balance definitivo y los totales los calcula el servidor.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

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
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { ContabilidadService } from '../services/contabilidad.service';
import { PolizaContable, RenglonPolizaRequest } from '../models/contabilidad.models';
import { sumaCentavos } from '../../finanzas-comun/dinero';

@Component({
  selector: 'app-contabilidad-polizas',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './polizas.html',
  styleUrl: '../contabilidad.scss',
})
export class ContabilidadPolizas {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ContabilidadService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('poliza_contable', 'crear');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'fecha', encabezado: 'Fecha' },
    { clave: 'tipo', encabezado: 'Tipo' },
    { clave: 'concepto', encabezado: 'Concepto' },
    { clave: 'cargos', encabezado: 'Cargos', alineacion: 'fin' },
    { clave: 'abonos', encabezado: 'Abonos', alineacion: 'fin' },
    { clave: 'balance', encabezado: 'Balance' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estado = signal<EstadoSolicitud<PolizaContable[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly desde = signal('');
  protected readonly hasta = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  /** Recalculo del preview de balance ante cada cambio de los renglones. */
  private readonly cambioRenglones = signal(0);

  protected readonly formAlta = this.fb.nonNullable.group({
    fecha: ['', [Validators.required]],
    tipo: ['diario', [Validators.required]],
    concepto: ['', [Validators.required]],
    renglones: this.fb.array([this.crearRenglon(), this.crearRenglon()]),
  });

  protected get renglones(): FormArray {
    return this.formAlta.get('renglones') as FormArray;
  }

  /**
   * Previsualizacion del balance (cargos - abonos) en centavos enteros. Es solo
   * una ayuda visual; el balance oficial lo valida el servidor.
   */
  protected readonly balancePreview = computed(() => {
    this.cambioRenglones();
    const filas = this.renglones.getRawValue() as { cargo: number; abono: number }[];
    const cargos = sumaCentavos(filas.map((f) => f.cargo ?? 0));
    const abonos = sumaCentavos(filas.map((f) => f.abono ?? 0));
    return { cargos: cargos / 100, abonos: abonos / 100, balanceado: cargos === abonos };
  });

  constructor() {
    this.cargar();
  }

  private crearRenglon() {
    return this.fb.nonNullable.group({
      cuentaContableId: ['', [Validators.required]],
      cargo: [0, [Validators.min(0)]],
      abono: [0, [Validators.min(0)]],
    });
  }

  agregarRenglon(): void {
    this.renglones.push(this.crearRenglon());
    this.cambioRenglones.update((v) => v + 1);
  }

  quitarRenglon(indice: number): void {
    if (this.renglones.length > 2) {
      this.renglones.removeAt(indice);
      this.cambioRenglones.update((v) => v + 1);
    }
  }

  recalcular(): void {
    this.cambioRenglones.update((v) => v + 1);
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarPolizas(this.desde() || null, this.hasta() || null, null, this.page(), this.size())
      .subscribe({
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

  aplicarRango(desde: string, hasta: string): void {
    this.desde.set(desde);
    this.hasta.set(hasta);
    this.page.set(0);
    this.cargar();
  }

  alternarAlta(): void {
    this.mostrarAlta.update((v) => !v);
  }

  esBalanceada(p: PolizaContable): boolean {
    return Math.round(p.totalCargos * 100) === Math.round(p.totalAbonos * 100);
  }

  crear(): void {
    if (this.formAlta.invalid) {
      this.formAlta.markAllAsTouched();
      return;
    }
    const v = this.formAlta.getRawValue();
    const renglones = v.renglones as RenglonPolizaRequest[];
    this.guardando.set(true);
    this.service
      .registrarPoliza({
        fecha: v.fecha,
        tipo: v.tipo,
        concepto: v.concepto,
        origen: null,
        origenId: null,
        renglones,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Poliza registrada.');
          this.formAlta.reset({ fecha: '', tipo: 'diario', concepto: '' });
          this.formAlta.setControl('renglones', this.fb.array([this.crearRenglon(), this.crearRenglon()]));
          this.recalcular();
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

  async reversar(p: PolizaContable): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Reversar poliza',
      mensaje: 'Se creara una poliza de reverso; la original se conserva. Continuar?',
      textoConfirmar: 'Reversar',
    });
    if (!ok) {
      return;
    }
    this.service.reversarPoliza(p.id, null).subscribe({
      next: () => {
        this.toast.exito('Poliza de reverso creada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
