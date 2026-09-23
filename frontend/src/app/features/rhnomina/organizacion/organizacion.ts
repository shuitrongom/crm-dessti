// =============================================================================
// Vista de Organizacion de personal (Req 61)
// -----------------------------------------------------------------------------
// Reune, en pestanas, la gestion de Puestos (listado + alta), el organigrama
// derivado (arbol de solo lectura) y las Evaluaciones de desempeno (listado +
// registro). Cada operacion se gobierna por su permiso atomico (deny-by-default)
// y el backend valida jerarquia/escala. El organigrama se renderiza como un arbol
// accesible con desangrado por nivel.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
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
import { EvaluacionDesempeno, OrganigramaNodo, Puesto } from '../models/rhnomina.models';
import { OrganigramaArbol } from './organigrama-arbol';

@Component({
  selector: 'app-rhnomina-organizacion',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTabsModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
    OrganigramaArbol,
  ],
  templateUrl: './organizacion.html',
  styleUrl: '../rhnomina.scss',
})
export class RhNominaOrganizacion {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(RhNominaService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrearPuesto = this.auth.tienePermiso('puesto', 'crear');
  protected readonly puedeVerOrganigrama = this.auth.tienePermiso('organigrama', 'leer');
  protected readonly puedeCrearEvaluacion = this.auth.tienePermiso('evaluacion_desempeno', 'crear');
  protected readonly puedeVerEvaluaciones = this.auth.tienePermiso('evaluacion_desempeno', 'listar');

  // --- Puestos ---------------------------------------------------------------
  protected readonly puestosColumnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Puesto' },
    { clave: 'descripcion', encabezado: 'Descripción' },
    { clave: 'superior', encabezado: 'Superior' },
    { clave: 'estado', encabezado: 'Estado' },
  ];
  protected readonly puestos = signal<EstadoSolicitud<Puesto[]>>(cargando());
  protected readonly puestosTotal = signal(0);
  protected readonly puestosPage = signal(0);
  protected readonly puestosSize = signal(20);
  protected readonly guardandoPuesto = signal(false);

  protected readonly formPuesto = this.fb.nonNullable.group({
    nombre: ['', [Validators.required]],
    descripcion: [''],
    puestoSuperiorId: [''],
  });

  // --- Organigrama -----------------------------------------------------------
  protected readonly organigrama = signal<OrganigramaNodo[]>([]);
  protected readonly organigramaFase = signal<'cargando' | 'ok' | 'vacio' | 'error'>('cargando');
  protected readonly organigramaError = signal<string | undefined>(undefined);

  // --- Evaluaciones ----------------------------------------------------------
  protected readonly evalColumnas: ColumnaTabla[] = [
    { clave: 'empleado', encabezado: 'Empleado' },
    { clave: 'periodo', encabezado: 'Periodo' },
    { clave: 'calificacion', encabezado: 'Calificacion', alineacion: 'centro' },
    { clave: 'evaluada', encabezado: 'Evaluada' },
  ];
  protected readonly evaluaciones = signal<EstadoSolicitud<EvaluacionDesempeno[]>>(cargando());
  protected readonly evalTotal = signal(0);
  protected readonly evalPage = signal(0);
  protected readonly evalSize = signal(20);
  protected readonly guardandoEval = signal(false);

  protected readonly formEval = this.fb.nonNullable.group({
    empleadoId: ['', [Validators.required]],
    periodo: ['', [Validators.required, Validators.maxLength(7)]],
    calificacion: [3, [Validators.required, Validators.min(1), Validators.max(5)]],
    comentarios: [''],
  });

  constructor() {
    this.cargarPuestos();
    if (this.puedeVerOrganigrama) {
      this.cargarOrganigrama();
    }
    if (this.puedeVerEvaluaciones) {
      this.cargarEvaluaciones();
    }
  }

  // --- Puestos ---------------------------------------------------------------
  cargarPuestos(): void {
    this.puestos.set(cargando());
    this.service.listarPuestos(null, null, this.puestosPage(), this.puestosSize()).subscribe({
      next: (p) => {
        this.puestosTotal.set(p.totalElements);
        this.puestos.set(conDatos(p.content, p.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.puestos.set(conError(mensajeDeError(e))),
    });
  }

  cambiarPaginaPuestos(evento: CambioPagina): void {
    this.puestosPage.set(evento.page);
    this.puestosSize.set(evento.size);
    this.cargarPuestos();
  }

  crearPuesto(): void {
    if (this.formPuesto.invalid) {
      this.formPuesto.markAllAsTouched();
      return;
    }
    const v = this.formPuesto.getRawValue();
    this.guardandoPuesto.set(true);
    this.service
      .crearPuesto({
        nombre: v.nombre,
        descripcion: v.descripcion || null,
        puestoSuperiorId: v.puestoSuperiorId || null,
      })
      .subscribe({
        next: () => {
          this.guardandoPuesto.set(false);
          this.toast.exito('Puesto creado.');
          this.formPuesto.reset({ nombre: '', descripcion: '', puestoSuperiorId: '' });
          this.puestosPage.set(0);
          this.cargarPuestos();
          if (this.puedeVerOrganigrama) {
            this.cargarOrganigrama();
          }
        },
        error: (e: HttpErrorResponse) => {
          this.guardandoPuesto.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  // --- Organigrama -----------------------------------------------------------
  cargarOrganigrama(): void {
    this.organigramaFase.set('cargando');
    this.service.consultarOrganigrama().subscribe({
      next: (nodos) => {
        this.organigrama.set(nodos);
        this.organigramaFase.set(nodos.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.organigramaError.set(mensajeDeError(e));
        this.organigramaFase.set('error');
      },
    });
  }

  // --- Evaluaciones ----------------------------------------------------------
  cargarEvaluaciones(): void {
    this.evaluaciones.set(cargando());
    this.service.listarEvaluaciones(null, null, this.evalPage(), this.evalSize()).subscribe({
      next: (p) => {
        this.evalTotal.set(p.totalElements);
        this.evaluaciones.set(conDatos(p.content, p.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.evaluaciones.set(conError(mensajeDeError(e))),
    });
  }

  cambiarPaginaEval(evento: CambioPagina): void {
    this.evalPage.set(evento.page);
    this.evalSize.set(evento.size);
    this.cargarEvaluaciones();
  }

  registrarEvaluacion(): void {
    if (this.formEval.invalid) {
      this.formEval.markAllAsTouched();
      return;
    }
    const v = this.formEval.getRawValue();
    this.guardandoEval.set(true);
    this.service
      .registrarEvaluacion({
        empleadoId: v.empleadoId,
        periodo: v.periodo,
        calificacion: v.calificacion,
        comentarios: v.comentarios || null,
      })
      .subscribe({
        next: () => {
          this.guardandoEval.set(false);
          this.toast.exito('Evaluacion registrada.');
          this.formEval.reset({ empleadoId: '', periodo: '', calificacion: 3, comentarios: '' });
          this.evalPage.set(0);
          this.cargarEvaluaciones();
        },
        error: (e: HttpErrorResponse) => {
          this.guardandoEval.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }
}
