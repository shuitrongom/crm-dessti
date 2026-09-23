// =============================================================================
// Vista de Campanas publicitarias (Req 65.7-65.11)
// -----------------------------------------------------------------------------
// Listado paginado filtrable por canal; alta de una Campana_Publicitaria
// (presupuesto MXN + periodo) con validacion en el servidor; y consulta del
// estado externo de SOLO LECTURA desde la Marketing API de Meta (Req 65.9).
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
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

import { CampanasService } from '../services/campanas.service';
import { CampanaPublicitaria, EstadoCampanaExterno } from '../models/social.models';
import { ETIQUETA_CANAL, OPCIONES_CANAL } from '../social-etiquetas';

@Component({
  selector: 'app-campanas',
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
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './campanas.html',
  styleUrl: './campanas.scss',
})
export class Campanas {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CampanasService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('campana_publicitaria', 'crear');
  protected readonly puedeLeer = this.auth.tienePermiso('campana_publicitaria', 'leer');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Campana' },
    { clave: 'canal', encabezado: 'Canal' },
    { clave: 'presupuesto', encabezado: 'Presupuesto', alineacion: 'fin' },
    { clave: 'periodo', encabezado: 'Periodo' },
    { clave: 'estadoExterno', encabezado: 'Estado (Meta)' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  // Filtro por canal derivado del origen unico (los cinco canales) mas "todos".
  protected readonly canales = [
    { valor: '', etiqueta: 'Todos los canales' },
    ...OPCIONES_CANAL,
  ];

  // Opciones del alta: los cinco canales mas "sin canal" (campana no ligada).
  protected readonly canalesAlta = [{ valor: '', etiqueta: 'Sin canal' }, ...OPCIONES_CANAL];

  protected readonly estado = signal<EstadoSolicitud<CampanaPublicitaria[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroCanal = signal('');
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);

  /** Instantaneas de estado externo consultadas a demanda, por campana. */
  protected readonly estadosExternos = signal<Record<string, EstadoCampanaExterno>>({});

  protected readonly formAlta = this.fb.nonNullable.group({
    nombre: ['', [Validators.required]],
    canal: [''],
    presupuesto: [0.01, [Validators.required, Validators.min(0.01)]],
    fechaInicio: ['', [Validators.required]],
    fechaFin: ['', [Validators.required]],
  });

  constructor() {
    this.cargar();
  }

  etiquetaCanal(valor: string | null): string {
    if (!valor) {
      return 'Sin canal';
    }
    return ETIQUETA_CANAL[valor as keyof typeof ETIQUETA_CANAL] ?? valor;
  }

  /** Estado externo cacheado de una campana, si ya se consulto. */
  estadoExternoDe(id: string): EstadoCampanaExterno | null {
    return this.estadosExternos()[id] ?? null;
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listar(this.filtroCanal() || null, this.page(), this.size()).subscribe({
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

  aplicarCanal(valor: string): void {
    this.filtroCanal.set(valor);
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
      .crear({
        nombre: v.nombre,
        canal: (v.canal || null) as CampanaPublicitaria['canal'],
        presupuesto: v.presupuesto,
        fechaInicio: v.fechaInicio,
        fechaFin: v.fechaFin,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Campana creada.');
          this.formAlta.reset({
            nombre: '',
            canal: '',
            presupuesto: 0.01,
            fechaInicio: '',
            fechaFin: '',
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

  /** Consulta el estado externo de SOLO LECTURA de una campana (Req 65.9). */
  consultarEstadoExterno(c: CampanaPublicitaria): void {
    this.service.consultarEstadoExterno(c.id).subscribe({
      next: (externo) => {
        this.estadosExternos.update((mapa) => ({ ...mapa, [c.id]: externo }));
        this.toast.info('Estado externo actualizado desde Meta.');
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
