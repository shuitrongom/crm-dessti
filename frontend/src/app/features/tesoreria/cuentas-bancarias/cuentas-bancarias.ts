// =============================================================================
// Vista de Cuentas bancarias y conciliacion (Req 43)
// -----------------------------------------------------------------------------
// Listado paginado de cuentas bancarias; alta de cuenta; importacion de un estado
// de cuenta con sus movimientos; y conciliacion bancaria (el backend empareja los
// movimientos y solo marca "completa" cuando la diferencia es cero y no hay
// excepciones). Tras conciliar se muestra la diferencia resultante como indicador.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
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
import { TesoreriaService } from '../services/tesoreria.service';
import { ConciliacionBancaria, CuentaBancaria } from '../models/tesoreria.models';

@Component({
  selector: 'app-tesoreria-cuentas-bancarias',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
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
  templateUrl: './cuentas-bancarias.html',
  styleUrl: '../tesoreria.scss',
})
export class TesoreriaCuentasBancarias {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(TesoreriaService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('cuenta_bancaria', 'crear');
  protected readonly puedeConciliar = this.auth.tienePermiso('conciliacion_bancaria', 'crear');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Cuenta' },
    { clave: 'banco', encabezado: 'Banco' },
    { clave: 'clabe', encabezado: 'CLABE' },
    { clave: 'moneda', encabezado: 'Moneda', alineacion: 'centro' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estado = signal<EstadoSolicitud<CuentaBancaria[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);
  protected readonly cuentaImportar = signal<CuentaBancaria | null>(null);
  protected readonly ultimaConciliacion = signal<ConciliacionBancaria | null>(null);

  protected readonly formCuenta = this.fb.nonNullable.group({
    nombre: ['', [Validators.required]],
    banco: ['', [Validators.required]],
    clabe: [''],
    moneda: ['MXN'],
  });

  protected readonly formImport = this.fb.nonNullable.group({
    referenciaArchivo: [''],
    periodoInicio: ['', [Validators.required]],
    periodoFin: ['', [Validators.required]],
    movimientos: this.fb.array([this.crearMovimiento()]),
  });

  protected get movimientos(): FormArray {
    return this.formImport.get('movimientos') as FormArray;
  }

  constructor() {
    this.cargar();
  }

  private crearMovimiento() {
    return this.fb.nonNullable.group({
      fecha: ['', [Validators.required]],
      monto: [0, [Validators.required]],
      referencia: [''],
      descripcion: [''],
    });
  }

  agregarMovimiento(): void {
    this.movimientos.push(this.crearMovimiento());
  }

  quitarMovimiento(indice: number): void {
    if (this.movimientos.length > 1) {
      this.movimientos.removeAt(indice);
    }
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarCuentas(null, this.page(), this.size()).subscribe({
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

  alternarAlta(): void {
    this.mostrarAlta.update((v) => !v);
  }

  crearCuenta(): void {
    if (this.formCuenta.invalid) {
      this.formCuenta.markAllAsTouched();
      return;
    }
    const v = this.formCuenta.getRawValue();
    this.guardando.set(true);
    this.service
      .crearCuenta({ nombre: v.nombre, banco: v.banco, clabe: v.clabe || null, moneda: v.moneda || null })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Cuenta bancaria creada.');
          this.formCuenta.reset({ moneda: 'MXN' });
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

  abrirImportar(cuenta: CuentaBancaria): void {
    this.cuentaImportar.set(cuenta);
    this.ultimaConciliacion.set(null);
    this.formImport.reset({ referenciaArchivo: '', periodoInicio: '', periodoFin: '' });
    this.formImport.setControl('movimientos', this.fb.array([this.crearMovimiento()]));
  }

  cancelarImportar(): void {
    this.cuentaImportar.set(null);
  }

  importarYConciliar(): void {
    const cuenta = this.cuentaImportar();
    if (!cuenta || this.formImport.invalid) {
      this.formImport.markAllAsTouched();
      return;
    }
    const v = this.formImport.getRawValue();
    const movimientos = (v.movimientos as {
      fecha: string;
      monto: number;
      referencia: string;
      descripcion: string;
    }[]).map((m) => ({
      fecha: m.fecha,
      monto: m.monto,
      referencia: m.referencia || null,
      descripcion: m.descripcion || null,
    }));
    this.guardando.set(true);
    this.service
      .importarEstadoCuenta(cuenta.id, {
        referenciaArchivo: v.referenciaArchivo || null,
        periodoInicio: v.periodoInicio,
        periodoFin: v.periodoFin,
        movimientos,
      })
      .subscribe({
        next: (estadoCuenta) => {
          this.service.conciliar(estadoCuenta.id).subscribe({
            next: (conciliacion) => {
              this.guardando.set(false);
              this.ultimaConciliacion.set(conciliacion);
              if (conciliacion.estado === 'completa') {
                this.toast.exito('Conciliacion completa: diferencia cero.');
              } else {
                this.toast.info('Conciliacion en proceso: revisa la diferencia y las excepciones.');
              }
            },
            error: (e: HttpErrorResponse) => {
              this.guardando.set(false);
              this.toast.error(mensajeDeError(e));
            },
          });
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  diferenciaEsCero(c: ConciliacionBancaria): boolean {
    return Math.round(c.diferencia * 100) === 0;
  }
}
