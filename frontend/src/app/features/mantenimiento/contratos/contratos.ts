// =============================================================================
// Vista de Contratos de mantenimiento (Req 20.1, 20.7)
// -----------------------------------------------------------------------------
// Listado paginado y alta de contratos de mantenimiento con sus tiempos de SLA
// (respuesta y resolucion en horas). Cada operacion se gobierna por permiso
// atomico (deny-by-default). El backend valida los datos.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
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
import { humanizarEstado } from '../../finanzas-comun/tono-estado';
import { MantenimientoService } from '../services/mantenimiento.service';
import { ContratoMantenimiento } from '../models/mantenimiento.models';

@Component({
  selector: 'app-mantenimiento-contratos',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './contratos.html',
  styleUrl: '../mantenimiento.scss',
})
export class MantenimientoContratos {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MantenimientoService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly humanizar = humanizarEstado;
  protected readonly puedeCrear = this.auth.tienePermiso('contrato_mantenimiento', 'crear');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'cliente', encabezado: 'Cliente' },
    { clave: 'tipo', encabezado: 'Tipo' },
    { clave: 'respuesta', encabezado: 'SLA respuesta', alineacion: 'centro' },
    { clave: 'resolucion', encabezado: 'SLA resolucion', alineacion: 'centro' },
    { clave: 'estado', encabezado: 'Estado' },
  ];

  protected readonly estado = signal<EstadoSolicitud<ContratoMantenimiento[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  protected readonly formAlta = this.fb.nonNullable.group({
    clienteId: ['', [Validators.required]],
    tipo: ['preventivo', [Validators.required]],
    slaRespuestaHoras: [4, [Validators.required, Validators.min(1)]],
    slaResolucionHoras: [24, [Validators.required, Validators.min(1)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarContratos(null, this.page(), this.size()).subscribe({
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

  crear(): void {
    if (this.formAlta.invalid) {
      this.formAlta.markAllAsTouched();
      return;
    }
    const v = this.formAlta.getRawValue();
    this.guardando.set(true);
    this.service.crearContrato(v).subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito('Contrato de mantenimiento creado.');
        this.formAlta.reset({ clienteId: '', tipo: 'preventivo', slaRespuestaHoras: 4, slaResolucionHoras: 24 });
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
}
