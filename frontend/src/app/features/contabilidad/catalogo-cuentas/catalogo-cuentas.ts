// =============================================================================
// Vista de Catálogo de cuentas — con amarre al código agrupador del SAT
// -----------------------------------------------------------------------------
// Lista las cuentas contables del tenant y permite amarrar cada una a un código
// agrupador del SAT (Anexo 24) mediante un autocompletar contra el catálogo
// oficial. El amarre exige el permiso cuenta_contable:actualizar; la lectura,
// cuenta_contable:listar. Reutiliza el catálogo de cuentas ya existente.
// =============================================================================

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { debounceTime, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import {
  CambioPagina,
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { EstadoSolicitud, cargando, conDatos, conError } from '../../../shared/models/estado-solicitud';

import { ContabilidadService } from '../services/contabilidad.service';
import { ContabilidadElectronicaService } from '../services/contabilidad-electronica.service';
import { CuentaContable } from '../models/contabilidad.models';
import { CodigoAgrupadorSat } from '../models/contabilidad-electronica.models';

@Component({
  selector: 'app-contabilidad-catalogo-cuentas',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './catalogo-cuentas.html',
  styleUrl: '../contabilidad.scss',
})
export class ContabilidadCatalogoCuentas {
  private readonly service = inject(ContabilidadService);
  private readonly ceService = inject(ContabilidadElectronicaService);

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'codigo', encabezado: 'Código' },
    { clave: 'nombre', encabezado: 'Nombre' },
    { clave: 'tipo', encabezado: 'Tipo' },
    { clave: 'agrupador', encabezado: 'Código SAT' },
    { clave: 'acciones', encabezado: 'Amarre' },
  ];

  protected readonly cuentas = signal<EstadoSolicitud<CuentaContable[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);

  // Fila en edición de amarre y su control de autocompletar.
  protected readonly editando = signal<string | null>(null);
  protected readonly opciones = signal<CodigoAgrupadorSat[]>([]);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly controlAgrupador = new FormControl<string>('', { nonNullable: true });

  constructor() {
    this.cargar();
    this.controlAgrupador.valueChanges
      .pipe(
        debounceTime(250),
        switchMap((q) => (q && q.length >= 1 ? this.ceService.buscarCodigosAgrupadores(q) : of([]))),
      )
      .subscribe((lista) => this.opciones.set(lista));
  }

  cargar(): void {
    this.cuentas.set(cargando());
    this.service.listarCuentasContables(null, this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.total.set(pagina.totalElements);
        this.cuentas.set(conDatos(pagina.content, pagina.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.cuentas.set(conError(mensajeDeError(e))),
    });
  }

  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  iniciarAmarre(cuenta: CuentaContable): void {
    this.editando.set(cuenta.id);
    this.controlAgrupador.setValue(cuenta.codigoAgrupadorSat ?? '');
    this.opciones.set([]);
    this.error.set(null);
  }

  cancelarAmarre(): void {
    this.editando.set(null);
    this.opciones.set([]);
  }

  confirmarAmarre(cuenta: CuentaContable): void {
    const codigo = this.controlAgrupador.value?.trim();
    if (!codigo) {
      this.error.set('Selecciona un código agrupador del SAT.');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    this.ceService.amarrarCodigoAgrupador(cuenta.id, codigo).subscribe({
      next: () => {
        this.guardando.set(false);
        this.editando.set(null);
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }
}
