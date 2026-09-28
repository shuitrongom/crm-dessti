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
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
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
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError, erroresDeCampo } from '../../../core/services/error-mensajes';
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
    MatSelectModule,
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
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ContabilidadService);
  private readonly ceService = inject(ContabilidadElectronicaService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('cuenta_contable', 'crear');

  protected readonly tipos = [
    { valor: 'activo', etiqueta: 'Activo' },
    { valor: 'pasivo', etiqueta: 'Pasivo' },
    { valor: 'capital', etiqueta: 'Capital' },
    { valor: 'ingreso', etiqueta: 'Ingreso' },
    { valor: 'gasto', etiqueta: 'Gasto' },
  ];

  protected readonly naturalezas = [
    { valor: 'deudora', etiqueta: 'Deudora' },
    { valor: 'acreedora', etiqueta: 'Acreedora' },
  ];

  protected readonly mostrarAlta = signal(false);
  protected readonly guardandoAlta = signal(false);

  protected readonly formAlta = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(40)]],
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    tipo: ['activo', [Validators.required]],
    naturaleza: ['deudora', [Validators.required]],
  });

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

  // --- Alta de cuenta contable (Req 38.1) ------------------------------------

  alternarAlta(): void {
    this.mostrarAlta.update((v) => !v);
    if (this.mostrarAlta()) {
      this.formAlta.reset({ codigo: '', nombre: '', tipo: 'activo', naturaleza: 'deudora' });
    }
  }

  crearCuenta(): void {
    if (this.formAlta.invalid) {
      this.formAlta.markAllAsTouched();
      return;
    }
    const v = this.formAlta.getRawValue();
    this.guardandoAlta.set(true);
    this.overlay
      .ejecutar(
        this.service.crearCuentaContable({
          codigo: v.codigo.trim(),
          nombre: v.nombre.trim(),
          tipo: v.tipo,
          naturaleza: v.naturaleza,
        }),
        { tipo: 'crear', textoProceso: 'Creando cuenta…', textoExito: 'Cuenta creada' },
      )
      .subscribe({
        next: () => {
          this.guardandoAlta.set(false);
          this.toast.exito('Cuenta contable creada.');
          this.mostrarAlta.set(false);
          this.page.set(0);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardandoAlta.set(false);
          if (e.status === 409) {
            this.formAlta.controls.codigo.setErrors({ duplicado: true });
            this.toast.error('Ya existe una cuenta con ese código.');
            return;
          }
          if (e.status === 422) {
            const campos = erroresDeCampo(e);
            this.toast.error(campos.length ? campos.map((c) => c.mensaje).join(' ') : mensajeDeError(e));
            return;
          }
          this.toast.error(mensajeDeError(e));
        },
      });
  }
}
