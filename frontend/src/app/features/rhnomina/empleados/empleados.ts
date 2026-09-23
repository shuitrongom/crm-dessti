// =============================================================================
// Vista de Empleados, contratos e incidencias (Req 40)
// -----------------------------------------------------------------------------
// Listado paginado filtrable por nombre; alta de empleado con su primer contrato;
// baja logica; y, al seleccionar un empleado, registro y consulta de incidencias e
// historico de contratos. Cada accion se gobierna por permiso atomico
// (deny-by-default). El backend valida RFC/CURP/NSS y devuelve 409/422 mapeados a
// mensajes en espanol.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
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
import { MatTabsModule } from '@angular/material/tabs';

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
import { RhNominaService } from '../services/rhnomina.service';
import { ContratoLaboral, Empleado, Incidencia } from '../models/rhnomina.models';

@Component({
  selector: 'app-rhnomina-empleados',
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
    MatTabsModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './empleados.html',
  styleUrl: '../rhnomina.scss',
})
export class RhNominaEmpleados {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(RhNominaService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('empleado', 'crear');
  protected readonly puedeEliminar = this.auth.tienePermiso('empleado', 'eliminar');
  protected readonly puedeIncidencia = this.auth.tienePermiso('incidencia', 'crear');
  protected readonly puedeVerIncidencias = this.auth.tienePermiso('incidencia', 'listar');
  protected readonly puedeVerContratos = this.auth.tienePermiso('contrato_laboral', 'listar');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Empleado' },
    { clave: 'rfc', encabezado: 'RFC' },
    { clave: 'ingreso', encabezado: 'Ingreso' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estado = signal<EstadoSolicitud<Empleado[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroNombre = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);
  protected readonly seleccionado = signal<Empleado | null>(null);
  protected readonly incidencias = signal<Incidencia[]>([]);
  protected readonly contratos = signal<ContratoLaboral[]>([]);

  protected readonly formAlta = this.fb.nonNullable.group({
    nombre: ['', [Validators.required]],
    rfc: ['', [Validators.required, Validators.minLength(13), Validators.maxLength(13)]],
    curp: ['', [Validators.required, Validators.minLength(18), Validators.maxLength(18)]],
    nss: ['', [Validators.required, Validators.minLength(11), Validators.maxLength(11)]],
    fechaIngreso: ['', [Validators.required]],
    tipoContrato: ['indeterminado', [Validators.required]],
    salarioDiario: [1, [Validators.required, Validators.min(0.01)]],
    periodicidad: ['quincenal', [Validators.required]],
    fechaInicio: ['', [Validators.required]],
  });

  protected readonly formIncidencia = this.fb.nonNullable.group({
    periodoNomina: ['', [Validators.required]],
    tipo: ['falta', [Validators.required]],
    cantidad: [null as number | null],
    descripcion: [''],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarEmpleados(this.filtroNombre() || null, null, this.page(), this.size()).subscribe({
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
    this.filtroNombre.set(valor.trim());
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
    this.service
      .altaEmpleado({
        nombre: v.nombre,
        rfc: v.rfc,
        curp: v.curp,
        nss: v.nss,
        fechaIngreso: v.fechaIngreso,
        tipoContrato: v.tipoContrato,
        salarioDiario: v.salarioDiario,
        periodicidad: v.periodicidad,
        fechaInicio: v.fechaInicio,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Empleado dado de alta.');
          this.formAlta.reset({
            nombre: '',
            rfc: '',
            curp: '',
            nss: '',
            fechaIngreso: '',
            tipoContrato: 'indeterminado',
            salarioDiario: 1,
            periodicidad: 'quincenal',
            fechaInicio: '',
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

  async darDeBaja(empleado: Empleado): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja',
      mensaje: `Dar de baja al empleado "${empleado.nombre}"? Se conserva su historico.`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.darDeBajaEmpleado(empleado.id).subscribe({
      next: () => {
        this.toast.exito('Empleado dado de baja.');
        if (this.seleccionado()?.id === empleado.id) {
          this.seleccionado.set(null);
        }
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  seleccionar(empleado: Empleado): void {
    this.seleccionado.set(empleado);
    this.incidencias.set([]);
    this.contratos.set([]);
    if (this.puedeVerIncidencias) {
      this.service.listarIncidencias(empleado.id).subscribe({
        next: (p) => this.incidencias.set(p.content),
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
    }
    if (this.puedeVerContratos) {
      this.service.listarContratos(empleado.id).subscribe({
        next: (p) => this.contratos.set(p.content),
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
    }
  }

  cerrarDetalle(): void {
    this.seleccionado.set(null);
  }

  registrarIncidencia(): void {
    const empleado = this.seleccionado();
    if (!empleado || this.formIncidencia.invalid) {
      this.formIncidencia.markAllAsTouched();
      return;
    }
    const v = this.formIncidencia.getRawValue();
    this.service
      .registrarIncidencia(empleado.id, {
        periodoNomina: v.periodoNomina,
        tipo: v.tipo,
        cantidad: v.cantidad,
        descripcion: v.descripcion || null,
      })
      .subscribe({
        next: () => {
          this.toast.exito('Incidencia registrada.');
          this.formIncidencia.reset({ periodoNomina: '', tipo: 'falta', cantidad: null, descripcion: '' });
          this.seleccionar(empleado);
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }
}
