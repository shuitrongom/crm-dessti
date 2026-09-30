// =============================================================================
// Vista de Proveedores (Req 29)
// -----------------------------------------------------------------------------
// Catálogo de proveedores del módulo Compras: listado paginado con filtro por
// nombre/RFC, alta y edición con formulario premium (ds-form two-pane) y baja
// lógica con confirmación. Todas las acciones de escritura muestran el overlay
// animado de operación. Gobernadas por permiso atómico (deny-by-default); el
// backend reimpone la autorización, la unicidad del RFC (409) y el formato.
// =============================================================================

import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

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
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError, erroresDeCampo } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { ComprasService } from '../services/compras.service';
import { Proveedor } from '../models/compras.models';

@Component({
  selector: 'app-compras-proveedores',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    PageHeader,
    StateContainer,
    DataTable,
    CeldaTablaDirective,
    EstadoChip,
  ],
  templateUrl: './proveedores.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../compras.scss',
})
export class ComprasProveedores {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ComprasService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly overlay = inject(OperacionOverlayService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('proveedor', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('proveedor', 'actualizar');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Proveedor' },
    { clave: 'rfc', encabezado: 'RFC' },
    { clave: 'contacto', encabezado: 'Contacto' },
    { clave: 'estado', encabezado: 'Estado', alineacion: 'centro' },
    { clave: 'alta', encabezado: 'Alta' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  protected readonly estado = signal<EstadoSolicitud<Proveedor[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtro = signal('');
  /** Filtro de estado del listado: activos (por defecto), inactivos o todos. */
  protected readonly estadoFiltro = signal<'activo' | 'inactivo' | 'todos'>('activo');
  protected readonly guardando = signal(false);
  protected readonly mostrarForm = signal(false);
  /** Id del proveedor en edición; null en alta. */
  protected readonly editandoId = signal<string | null>(null);

  protected readonly tituloFormulario = computed(() =>
    this.editandoId() ? 'Editar proveedor' : 'Nuevo proveedor',
  );

  /** Formulario de alta/edición: nombre + RFC obligatorios; al menos un contacto. */
  protected readonly formulario = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(200)]],
    rfc: ['', [Validators.required, Validators.maxLength(20)]],
    email: ['', [Validators.email, Validators.maxLength(320)]],
    telefono: ['', [Validators.maxLength(20)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service
      .listarProveedores(this.filtro() || null, this.page(), this.size(), this.estadoFiltro())
      .subscribe({
        next: (pagina) => {
          this.total.set(pagina.totalElements);
          this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
        },
        error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
      });
  }

  /** Cambia el filtro de estado (activo/inactivo/todos) y recarga desde la pagina 0. */
  cambiarEstadoFiltro(estado: 'activo' | 'inactivo' | 'todos'): void {
    this.estadoFiltro.set(estado);
    this.page.set(0);
    this.cargar();
  }

  cambiarPagina(evento: CambioPagina): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  aplicarFiltro(valor: string): void {
    this.filtro.set(valor.trim());
    this.page.set(0);
    this.cargar();
  }

  /** Abre el formulario en modo alta (vacío). */
  nuevo(): void {
    this.editandoId.set(null);
    this.formulario.reset({ nombre: '', rfc: '', email: '', telefono: '' });
    this.mostrarForm.set(true);
  }

  /** Abre el formulario en modo edición prellenado. */
  editar(proveedor: Proveedor): void {
    this.editandoId.set(proveedor.id);
    this.formulario.reset({
      nombre: proveedor.nombre,
      rfc: proveedor.rfc,
      email: proveedor.email ?? '',
      telefono: proveedor.telefono ?? '',
    });
    this.mostrarForm.set(true);
  }

  /** Cierra el formulario sin guardar. */
  cancelar(): void {
    this.mostrarForm.set(false);
    this.editandoId.set(null);
  }

  /** Normaliza el RFC mientras se escribe: mayúsculas, solo [A-ZÑ&0-9], máx 13. */
  normalizarRfc(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const limpio = input.value
      .toUpperCase()
      .replace(/[^A-ZÑ&0-9]/g, '')
      .slice(0, 13);
    if (limpio !== input.value) {
      input.value = limpio;
    }
    this.formulario.controls.rfc.setValue(limpio);
  }

  /** Restringe el teléfono a dígitos (hasta 10). */
  soloDigitos(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const limpio = input.value.replace(/\D/g, '').slice(0, 10);
    if (limpio !== input.value) {
      input.value = limpio;
    }
    this.formulario.controls.telefono.setValue(limpio);
  }

  /** `null` cuando el texto queda en blanco tras recortar. */
  private opcional(valor: string): string | null {
    const limpio = valor.trim();
    return limpio ? limpio : null;
  }

  /** Guarda el alta o la edición del proveedor. */
  guardar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    const v = this.formulario.getRawValue();
    const email = this.opcional(v.email);
    const telefono = this.opcional(v.telefono);
    // Regla del backend: al menos un dato de contacto (email o teléfono).
    if (!email && !telefono) {
      this.formulario.controls.email.setErrors({ contactoRequerido: true });
      this.toast.error('Captura al menos un dato de contacto (correo o teléfono).');
      return;
    }

    const request = { nombre: v.nombre.trim(), rfc: v.rfc.trim().toUpperCase(), email, telefono };
    this.guardando.set(true);
    const id = this.editandoId();
    const peticion = id
      ? this.service.actualizarProveedor(id, request)
      : this.service.crearProveedor(request);

    this.overlay
      .ejecutar(peticion, {
        tipo: id ? 'guardar' : 'crear',
        textoProceso: id ? 'Guardando proveedor…' : 'Creando proveedor…',
        textoExito: id ? 'Proveedor guardado' : 'Proveedor creado',
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito(id ? 'Proveedor actualizado.' : 'Proveedor creado.');
          this.mostrarForm.set(false);
          this.editandoId.set(null);
          this.page.set(0);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          if (e.status === 409) {
            this.formulario.controls.rfc.setErrors({ duplicado: true });
            this.toast.error('Ya existe un proveedor activo con ese RFC.');
            return;
          }
          if (e.status === 422) {
            const campos = erroresDeCampo(e);
            this.toast.error(
              campos.length ? campos.map((c) => c.mensaje).join(' ') : mensajeDeError(e),
            );
            return;
          }
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  /** Da de baja lógica al proveedor (conserva histórico), con confirmación. */
  async desactivar(proveedor: Proveedor): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja proveedor',
      mensaje: `El proveedor "${proveedor.nombre}" quedará inactivo y no podrá usarse en nuevas órdenes. Su RFC se libera. ¿Continuar?`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.service.desactivarProveedor(proveedor.id), {
        tipo: 'eliminar',
        textoProceso: 'Dando de baja…',
        textoExito: 'Proveedor dado de baja',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Proveedor dado de baja.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
      });
  }

  /** Reactiva un proveedor dado de baja (Req 29.5), con confirmación. */
  async reactivar(proveedor: Proveedor): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Reactivar proveedor',
      mensaje: `El proveedor "${proveedor.nombre}" volverá a estar activo y disponible para nuevas órdenes. ¿Continuar?`,
      textoConfirmar: 'Reactivar',
    });
    if (!ok) {
      return;
    }
    this.overlay
      .ejecutar(this.service.reactivarProveedor(proveedor.id), {
        tipo: 'guardar',
        textoProceso: 'Reactivando…',
        textoExito: 'Proveedor reactivado',
      })
      .subscribe({
        next: () => {
          this.toast.exito('Proveedor reactivado.');
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          if (e.status === 409) {
            this.toast.error('Ya existe un proveedor activo con ese RFC. Edítalo antes de reactivar.');
            return;
          }
          this.toast.error(mensajeDeError(e));
        },
      });
  }
}
