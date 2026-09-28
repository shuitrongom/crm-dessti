// =============================================================================
// Vista de Clientes (Req 5) — listado paginado + alta/edicion (modal) + baja
// -----------------------------------------------------------------------------
// Listado paginado (DataTable) con filtro por nombre/RFC e indicadores KPI. El
// alta y la edicion se realizan en un MODAL animado (ClienteFormDialog), no en
// la propia pantalla, para una experiencia mas fluida y consistente con el resto
// de la plataforma. La baja es logica y con confirmacion. Todas las acciones se
// gobiernan por permiso atomico (deny-by-default) y el listado gestiona sus
// estados con StateContainer.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatDialog } from '@angular/material/dialog';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { KpiTile } from '../../../shared/components/kpi-tile/kpi-tile';
import {
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import {
  IndicadorInfoDialog,
  type DatosIndicadorInfo,
} from '../../../shared/indicadores/indicador-info-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { ClientesService } from '../services/clientes.service';
import { Cliente, ETIQUETA_TIPO_PERSONA, TipoPersona } from '../models/comercial.models';
import { ClienteFormDialog, ClienteFormDialogData } from './cliente-form-dialog';

@Component({
  selector: 'app-comercial-clientes',
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    KpiTile,
    DataTable,
    CeldaTablaDirective,
  ],
  templateUrl: './clientes.html',
  styleUrl: './clientes.scss',
})
export class ComercialClientes {
  private readonly service = inject(ClientesService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  // Permisos (deny-by-default) que gobiernan las acciones en la vista.
  protected readonly puedeCrear = this.auth.tienePermiso('cliente', 'crear');
  protected readonly puedeActualizar = this.auth.tienePermiso('cliente', 'actualizar');
  protected readonly puedeEliminar = this.auth.tienePermiso('cliente', 'eliminar');

  // Estado del listado.
  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly clientes = signal<Cliente[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly filtro = signal('');

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'nombre', encabezado: 'Nombre' },
    { clave: 'tipoPersona', encabezado: 'Tipo' },
    { clave: 'rfc', encabezado: 'RFC' },
    { clave: 'email', encabezado: 'Correo' },
    { clave: 'telefono', encabezado: 'Teléfono' },
    { clave: 'acciones', encabezado: 'Acciones', alineacion: 'fin' },
  ];

  // ---------------------------------------------------------------------------
  // Indicadores (KPIs) — patron enterprise: outcome-first. Se calculan en el
  // cliente. El total usa el conteo global paginado (`total()`); los desgloses
  // (activos, morales/fisicas) se calculan sobre la pagina cargada.
  // ---------------------------------------------------------------------------

  /** Numero de Clientes activos en la pagina cargada. */
  protected readonly clientesActivos = computed<number>(
    () => this.clientes().filter((c) => c.activo).length,
  );

  /** Numero de personas morales en la pagina cargada. */
  protected readonly clientesMorales = computed<number>(
    () => this.clientes().filter((c) => c.tipoPersona === 'moral').length,
  );

  /** Numero de personas fisicas en la pagina cargada. */
  protected readonly clientesFisicas = computed<number>(
    () => this.clientes().filter((c) => c.tipoPersona === 'fisica').length,
  );

  constructor() {
    this.cargar();
  }

  /** Carga la pagina actual de Clientes desde la API. */
  cargar(): void {
    this.fase.set('cargando');
    this.service.listar(this.filtro(), this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.clientes.set(pagina.content);
        this.total.set(pagina.totalElements);
        this.fase.set(pagina.content.length === 0 ? 'vacio' : 'ok');
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Aplica el filtro de busqueda reiniciando a la primera pagina. */
  aplicarFiltro(valor: string): void {
    this.filtro.set(valor);
    this.page.set(0);
    this.cargar();
  }

  /** Cambia de pagina o tamano y recarga (contrato PaginaResponse). */
  onPagina(evento: { page: number; size: number }): void {
    this.page.set(evento.page);
    this.size.set(evento.size);
    this.cargar();
  }

  /** Etiqueta legible del tipo de persona para el listado. */
  protected etiquetaTipoPersona(tipo: TipoPersona | null): string {
    return tipo ? ETIQUETA_TIPO_PERSONA[tipo] : '—';
  }

  /**
   * Abre el dialogo explicativo de un indicador de la cartera (¿qué es? / ¿cómo
   * se calcula? / ¿por qué importa?). La clave debe coincidir con una del
   * catalogo central de indicadores para mostrar la explicacion correcta.
   */
  abrirInfoKpi(clave: string, etiqueta: string, valor: number, unidad: string): void {
    const datos: DatosIndicadorInfo = { clave, etiqueta, valor, unidad };
    this.dialog.open(IndicadorInfoDialog, {
      data: datos,
      width: '32rem',
      maxWidth: '92vw',
      autoFocus: false,
    });
  }

  /** Abre el modal de alta de Cliente y recarga si se creo (Req 5.1). */
  nuevo(): void {
    this.abrirFormulario();
  }

  /** Abre el modal de edicion con los datos del Cliente y recarga si cambio (Req 5.4). */
  editar(cliente: Cliente): void {
    this.abrirFormulario(cliente);
  }

  /**
   * Abre el modal de formulario de Cliente (alta si no se pasa `cliente`, edicion
   * si se pasa) y recarga el listado cuando el dialogo confirma una operacion.
   */
  private abrirFormulario(cliente?: Cliente): void {
    const data: ClienteFormDialogData = { cliente };
    const ref = this.dialog.open(ClienteFormDialog, {
      width: 'min(920px, 96vw)',
      maxWidth: 'min(920px, 96vw)',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
      panelClass: 'ds-dialog-panel',
      data,
    });
    ref.afterClosed().subscribe((guardado?: Cliente) => {
      if (guardado) {
        this.toast.exito(cliente ? 'Cliente actualizado.' : 'Cliente creado.');
        this.cargar();
      }
    });
  }

  /**
   * Abre la Ficha 360 del Cliente (datos + actividad comercial conectada, Req 1).
   * Navega por el id de ruta; el Usuario nunca teclea el identificador.
   */
  ver(cliente: Cliente): void {
    this.router.navigate(['/empresa/comercial/clientes', cliente.id]);
  }

  /** Da de baja logica un Cliente con confirmacion (Req 5.9, 54). */
  async eliminar(cliente: Cliente): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Dar de baja cliente',
      mensaje: `El cliente "${cliente.nombre}" quedara inactivo y se conservara su historico. Deseas continuar?`,
      textoConfirmar: 'Dar de baja',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.eliminar(cliente.id).subscribe({
      next: () => {
        this.toast.exito('Cliente dado de baja.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
