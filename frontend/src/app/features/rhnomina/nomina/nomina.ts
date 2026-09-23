// =============================================================================
// Vista de Nomina y recibos (Req 41)
// -----------------------------------------------------------------------------
// Listado paginado filtrable por estado; creacion de nomina de un periodo; y las
// acciones de la maquina de estados (calcular -> autorizar -> timbrar -> pagar),
// gobernadas por el estado actual (nomina-estados) y por el permiso atomico
// nomina:cambiar_estado. Al seleccionar una nomina se listan sus recibos con
// percepciones/deducciones/neto y el Folio_Fiscal del CFDI de nomina. Los importes
// los calcula el servidor; la UI solo los formatea (currency es-MX).
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
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
import { humanizarEstado, tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { RhNominaService } from '../services/rhnomina.service';
import { Nomina, ReciboNomina } from '../models/rhnomina.models';
import { AccionNomina, accionDeNomina } from '../nomina-estados';

@Component({
  selector: 'app-rhnomina-nomina',
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
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './nomina.html',
  styleUrl: '../rhnomina.scss',
})
export class RhNominaNomina {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(RhNominaService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly tono = tonoDeEstado;
  protected readonly humanizar = humanizarEstado;
  protected readonly accionDe = accionDeNomina;

  protected readonly puedeCrear = this.auth.tienePermiso('nomina', 'crear');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('nomina', 'cambiar_estado');
  protected readonly puedeVerRecibos = this.auth.tienePermiso('recibo_nomina', 'leer');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'periodo', encabezado: 'Periodo' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'percepciones', encabezado: 'Percepciones', alineacion: 'fin' },
    { clave: 'deducciones', encabezado: 'Deducciones', alineacion: 'fin' },
    { clave: 'neto', encabezado: 'Neto', alineacion: 'fin' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly recibosColumnas: ColumnaTabla[] = [
    { clave: 'empleado', encabezado: 'Empleado' },
    { clave: 'percepciones', encabezado: 'Percepciones', alineacion: 'fin' },
    { clave: 'deducciones', encabezado: 'Deducciones', alineacion: 'fin' },
    { clave: 'neto', encabezado: 'Neto', alineacion: 'fin' },
    { clave: 'estado', encabezado: 'Estado' },
    { clave: 'folio', encabezado: 'Folio fiscal' },
  ];

  protected readonly estadosFiltro = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'borrador', etiqueta: 'Borrador' },
    { valor: 'calculada', etiqueta: 'Calculada' },
    { valor: 'autorizada', etiqueta: 'Autorizada' },
    { valor: 'timbrada', etiqueta: 'Timbrada' },
    { valor: 'pagada', etiqueta: 'Pagada' },
  ];

  protected readonly estado = signal<EstadoSolicitud<Nomina[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroEstado = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);
  protected readonly seleccionada = signal<Nomina | null>(null);
  protected readonly recibos = signal<ReciboNomina[]>([]);

  protected readonly formAlta = this.fb.nonNullable.group({
    periodoNomina: ['', [Validators.required, Validators.maxLength(7)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarNominas(null, this.filtroEstado() || null, this.page(), this.size()).subscribe({
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
    this.filtroEstado.set(valor);
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
    this.guardando.set(true);
    this.service.crearNomina({ periodoNomina: this.formAlta.getRawValue().periodoNomina }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito('Nomina creada en borrador.');
        this.formAlta.reset({ periodoNomina: '' });
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

  async ejecutarAccion(nomina: Nomina, accion: AccionNomina): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: accion.etiqueta,
      mensaje: `Confirmas "${accion.etiqueta.toLowerCase()}" de la nomina del periodo ${nomina.periodoNomina}?`,
      textoConfirmar: accion.etiqueta,
    });
    if (!ok) {
      return;
    }
    const peticion$ =
      accion.accion === 'calcular'
        ? this.service.calcularNomina(nomina.id, { aguinaldo: null, ptu: null, tasaInfonavit: null })
        : accion.accion === 'autorizar'
          ? this.service.autorizarNomina(nomina.id)
          : accion.accion === 'timbrar'
            ? this.service.timbrarNomina(nomina.id)
            : this.service.pagarNomina(nomina.id);
    peticion$.subscribe({
      next: () => {
        this.toast.exito('Nomina actualizada.');
        this.cargar();
        if (this.seleccionada()?.id === nomina.id) {
          this.verRecibos(nomina);
        }
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  verRecibos(nomina: Nomina): void {
    this.seleccionada.set(nomina);
    this.recibos.set([]);
    if (!this.puedeVerRecibos) {
      return;
    }
    this.service.listarRecibos(nomina.id).subscribe({
      next: (p) => this.recibos.set(p.content),
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  cerrarRecibos(): void {
    this.seleccionada.set(null);
  }
}
