// =============================================================================
// Vista de Recepciones de mercancia (Req 32)
// -----------------------------------------------------------------------------
// Tercera via del proceso de compra. Listado paginado con filtro por Orden_Compra
// y registro de recepciones: el usuario indica la Orden_Compra, la vista carga sus
// partidas y permite capturar la cantidad recibida por renglon. El backend valida
// que la Orden admita recepciones y que el acumulado no exceda lo ordenado, y
// deriva el estado de la Orden (recibida_parcial / recibida_total).
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
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

import { ComprasService } from '../services/compras.service';
import { OrdenCompra, RecepcionMercancia } from '../models/compras.models';

@Component({
  selector: 'app-compras-recepciones',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './recepciones.html',
  styleUrl: '../compras.scss',
})
export class ComprasRecepciones {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ComprasService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('recepcion_mercancia', 'crear');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'id', encabezado: 'Folio' },
    { clave: 'orden', encabezado: 'Orden de compra' },
    { clave: 'renglones', encabezado: 'Renglones', alineacion: 'centro' },
    { clave: 'fecha', encabezado: 'Recibida' },
  ];

  protected readonly estado = signal<EstadoSolicitud<RecepcionMercancia[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtroOrden = signal('');
  protected readonly guardando = signal(false);
  protected readonly ordenSeleccionada = signal<OrdenCompra | null>(null);
  protected readonly cargandoOrden = signal(false);

  /** Formulario de registro: OC + renglones a recibir. */
  protected readonly formRegistro = this.fb.nonNullable.group({
    ordenCompraId: ['', [Validators.required]],
    partidas: this.fb.array<ReturnType<ComprasRecepciones['crearRenglon']>>([]),
  });

  protected get partidas(): FormArray {
    return this.formRegistro.get('partidas') as FormArray;
  }

  constructor() {
    this.cargar();
  }

  private crearRenglon(partidaOrdenCompraId: string) {
    return this.fb.nonNullable.group({
      partidaOrdenCompraId: [partidaOrdenCompraId, [Validators.required]],
      cantidadRecibida: [0, [Validators.required, Validators.min(0)]],
    });
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listarRecepciones(this.filtroOrden() || null, this.page(), this.size()).subscribe({
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
    this.filtroOrden.set(valor.trim());
    this.page.set(0);
    this.cargar();
  }

  /** Carga la Orden_Compra indicada y genera un renglon por cada partida. */
  cargarOrden(): void {
    const id = this.formRegistro.controls.ordenCompraId.value.trim();
    if (!id) {
      this.formRegistro.controls.ordenCompraId.markAsTouched();
      return;
    }
    this.cargandoOrden.set(true);
    this.service.consultarOrdenCompra(id).subscribe({
      next: (orden) => {
        this.cargandoOrden.set(false);
        this.ordenSeleccionada.set(orden);
        const arreglo = this.fb.array(orden.partidas.map((p) => this.crearRenglon(p.id)));
        this.formRegistro.setControl('partidas', arreglo);
      },
      error: (e: HttpErrorResponse) => {
        this.cargandoOrden.set(false);
        this.ordenSeleccionada.set(null);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  materialDeRenglon(indice: number): string {
    const orden = this.ordenSeleccionada();
    return orden?.partidas[indice]?.materialId ?? '';
  }

  cantidadOrdenada(indice: number): number {
    const orden = this.ordenSeleccionada();
    return orden?.partidas[indice]?.cantidad ?? 0;
  }

  registrar(): void {
    if (this.formRegistro.invalid || this.partidas.length === 0) {
      this.formRegistro.markAllAsTouched();
      return;
    }
    const v = this.formRegistro.getRawValue();
    const partidas = (v.partidas as { partidaOrdenCompraId: string; cantidadRecibida: number }[]).filter(
      (p) => p.cantidadRecibida > 0,
    );
    if (partidas.length === 0) {
      this.toast.info('Captura al menos una cantidad recibida.');
      return;
    }
    this.guardando.set(true);
    this.service.registrarRecepcion({ ordenCompraId: v.ordenCompraId, partidas }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.toast.exito('Recepcion registrada.');
        this.ordenSeleccionada.set(null);
        this.formRegistro.reset({ ordenCompraId: '' });
        this.formRegistro.setControl(
          'partidas',
          this.fb.array<ReturnType<ComprasRecepciones['crearRenglon']>>([]),
        );
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
