// =============================================================================
// Vista de detalle de Cotizacion (Req 6, 15) — partidas, estado y pruebas de diseno
// -----------------------------------------------------------------------------
// Muestra la Cotizacion con sus partidas y totales, permite agregar partidas
// mientras esta en borrador (Req 6.3), cambiar de estado segun la maquina de
// estados (solo se ofrecen transiciones validas, Req 6.6/6.7) y gestionar las
// Pruebas de Diseno asociadas (generar/aprobar/rechazar, Req 15). Las acciones se
// gobiernan por permiso atomico (deny-by-default).
// =============================================================================

import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { Observable } from 'rxjs';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { EstadoChip } from '../../finanzas-comun/estado-chip/estado-chip';
import { tonoDeEstado } from '../../finanzas-comun/tono-estado';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/services/error-mensajes';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { FaseSolicitud } from '../../../shared/models/estado-solicitud';

import { CotizacionesService } from '../services/cotizaciones.service';
import { CanalesVentaService, ProductosService } from '../services/catalogo.service';
import { EnviarCorreoDialogService } from './enviar-correo-dialog';
import {
  CanalVenta,
  Cotizacion,
  ETIQUETA_ESTADO_COTIZACION,
  ETIQUETA_ESTADO_PRUEBA,
  EstadoCotizacion,
  MONEDA_POR_DEFECTO,
  Producto,
  PruebaDiseno,
  estadosDestinoCotizacion,
} from '../models/comercial.models';

@Component({
  selector: 'app-comercial-cotizacion-detalle',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
    EntitySelect,
    EstadoChip,
  ],
  templateUrl: './cotizacion-detalle.html',
  styleUrl: './cotizacion-detalle.scss',
})
export class ComercialCotizacionDetalle implements OnInit {
  /** Identificador de la Cotizacion tomado de la ruta (:id). */
  readonly id = input.required<string>();

  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CotizacionesService);
  private readonly productos = inject(ProductosService);
  private readonly canales = inject(CanalesVentaService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly correoDialog = inject(EnviarCorreoDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeActualizar = this.auth.tienePermiso('cotizacion', 'actualizar');
  protected readonly puedeLeer = this.auth.tienePermiso('cotizacion', 'leer');
  protected readonly puedeCambiarEstado = this.auth.tienePermiso('cotizacion', 'cambiar_estado');
  protected readonly puedeGenerarPrueba = this.auth.tienePermiso('prueba_diseno', 'crear');
  protected readonly puedeListarPruebas = this.auth.tienePermiso('prueba_diseno', 'listar');
  protected readonly puedeDecidirPrueba = this.auth.tienePermiso('prueba_diseno', 'cambiar_estado');
  /** El enlace al Cliente solo se ofrece si el Usuario puede abrir su Ficha 360 (Req 3.1, 7.2). */
  protected readonly puedeVerCliente = this.auth.tienePermiso('cliente', 'leer');
  /** El enlace a la Oportunidad de origen solo se ofrece si el Usuario puede leerla (Req 3.1, 7.2). */
  protected readonly puedeVerOportunidad = this.auth.tienePermiso('oportunidad', 'leer');
  /** El mismo permiso `cotizacion:actualizar` gobierna la asignacion de canal (Req 3.3). */
  protected readonly puedeAsignarCanal = this.puedeActualizar;

  protected readonly etiquetaEstado = ETIQUETA_ESTADO_COTIZACION;
  protected readonly etiquetaEstadoPrueba = ETIQUETA_ESTADO_PRUEBA;
  protected readonly tonoEstado = tonoDeEstado;
  protected readonly columnasPartidas = ['descripcion', 'cantidad', 'precioUnitario', 'subtotal'];

  protected readonly fase = signal<FaseSolicitud>('cargando');
  protected readonly mensajeError = signal<string | undefined>(undefined);
  protected readonly cotizacion = signal<Cotizacion | null>(null);
  protected readonly pruebas = signal<PruebaDiseno[]>([]);
  protected readonly guardando = signal(false);
  protected readonly descargando = signal(false);
  protected readonly enviando = signal(false);
  protected readonly formPartidaAbierto = signal(false);

  /** Canales de venta disponibles para el selector de asignacion (cargados una vez). */
  protected readonly canalesDisponibles = signal<CanalVenta[]>([]);
  /** Mapa id -> nombre de canal para mostrar el canal actual sin exponer el UUID (Req 3.3). */
  private readonly nombreCanalPorId = computed<Map<string, string>>(
    () => new Map(this.canalesDisponibles().map((c) => [c.id, c.nombre])),
  );
  /** `true` mientras el dialogo de asignacion de canal esta abierto. */
  protected readonly asignandoCanal = signal(false);

  /** Nombre del canal de venta actual de la Cotizacion, o null si no tiene canal. */
  protected readonly nombreCanal = computed<string | null>(() => {
    const canalVentaId = this.cotizacion()?.canalVentaId;
    return canalVentaId ? (this.nombreCanalPorId().get(canalVentaId) ?? null) : null;
  });

  /** Codigo de moneda efectivo de la cotizacion (MXN por defecto). */
  protected readonly moneda = computed(
    () => this.cotizacion()?.moneda || MONEDA_POR_DEFECTO,
  );

  /** Titulo con folio para el encabezado ("Cotizacion COT-2026-0001"). */
  protected readonly titulo = computed(() => {
    const folio = this.cotizacion()?.folio;
    return folio ? `Cotizacion ${folio}` : 'Detalle de cotizacion';
  });

  /** Transiciones de estado validas desde el estado actual (deny-by-default). */
  protected readonly transiciones = computed<readonly EstadoCotizacion[]>(() => {
    const c = this.cotizacion();
    return c ? estadosDestinoCotizacion(c.estado) : [];
  });

  /** Solo se pueden agregar partidas mientras la Cotizacion esta en borrador (Req 6.3). */
  protected readonly esBorrador = computed(() => this.cotizacion()?.estado === 'borrador');

  /**
   * `true` cuando la Empresa emisora tiene datos fiscales incompletos (Req 3):
   * activa un aviso no intrusivo que invita a completar RFC y direccion en
   * "Mi empresa". Se alimenta del flag `emisorIncompleto` del DTO, presente
   * tanto en la consulta (GET) como en la respuesta de envio por correo (POST).
   */
  protected readonly emisorIncompleto = computed(
    () => this.cotizacion()?.emisorIncompleto === true,
  );

  protected readonly formPartida = this.fb.nonNullable.group({
    productoId: [''],
    descripcion: ['', [Validators.required, Validators.maxLength(500)]],
    cantidad: [1, [Validators.required, Validators.min(1), Validators.max(999999)]],
    precioUnitario: [null as number | null, [Validators.min(0.01), Validators.max(999999999.99)]],
  });

  /** Formulario del dialogo de asignacion de canal (canalId vacio = quitar canal). */
  protected readonly formCanal = this.fb.nonNullable.group({
    canalId: [''],
  });

  /** Busca Productos por nombre para el selector de partida (opcional, sin UUID). */
  protected readonly buscarProducto = (filtro: string): Observable<PaginaResponse<Producto>> =>
    this.productos.listar(filtro, 0, 20);

  /** Etiqueta principal de un Producto en el selector. */
  protected readonly etiquetaProducto = (producto: Producto): string => producto.nombre;

  /** Detalle secundario (unidad) de un Producto en el selector. */
  protected readonly detalleProducto = (producto: Producto): string | null =>
    producto.unidad || null;

  /** Carga inicial: el input de ruta (:id) ya esta enlazado en ngOnInit. */
  ngOnInit(): void {
    this.cargar();
    this.cargarCanales();
  }

  /**
   * Carga los canales de venta una sola vez para resolver el nombre del canal
   * actual (sin exponer el UUID) y alimentar el selector de asignacion (Req 3.3).
   * Sin canales, el selector queda vacio y la vista degrada sin romper.
   */
  cargarCanales(): void {
    this.canales.listar(null, 0, 100).subscribe({
      next: (pagina) => this.canalesDisponibles.set(pagina.content),
      error: () => this.canalesDisponibles.set([]),
    });
  }

  /** Carga la Cotizacion y, si esta permitido, sus Pruebas de Diseno. */
  cargar(): void {
    this.fase.set('cargando');
    this.service.consultar(this.id()).subscribe({
      next: (cotizacion) => {
        this.cotizacion.set(cotizacion);
        this.fase.set('ok');
        this.cargarPruebas();
      },
      error: (e: HttpErrorResponse) => {
        this.mensajeError.set(mensajeDeError(e));
        this.fase.set('error');
      },
    });
  }

  /** Carga las Pruebas de Diseno de la Cotizacion (Req 15.6). */
  cargarPruebas(): void {
    if (!this.puedeListarPruebas) {
      return;
    }
    this.service.listarPruebas(this.id(), 0, 50).subscribe({
      next: (pagina) => this.pruebas.set(pagina.content),
      error: () => this.pruebas.set([]),
    });
  }

  /**
   * Descarga el PDF de la cotizacion y dispara la descarga en el navegador
   * (crea un object URL temporal y lo revoca al terminar). El nombre de archivo
   * es cotizacion-<folio>.pdf (o el id si no hay folio).
   */
  descargarPdf(): void {
    const c = this.cotizacion();
    if (!c || this.descargando()) {
      return;
    }
    this.descargando.set(true);
    this.service.descargarPdf(this.id()).subscribe({
      next: (blob) => {
        this.descargando.set(false);
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `cotizacion-${c.folio ?? c.id}.pdf`;
        document.body.appendChild(enlace);
        enlace.click();
        enlace.remove();
        URL.revokeObjectURL(url);
      },
      error: (e: HttpErrorResponse) => {
        this.descargando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /**
   * Abre el dialogo de envio por correo (prellenado con el correo del cliente),
   * y al confirmar envia la cotizacion; en exito refresca el detalle porque el
   * estado puede pasar a "enviada" y se fija enviadaEn (Req 6).
   */
  async enviarCorreo(): Promise<void> {
    const c = this.cotizacion();
    if (!c || this.enviando()) {
      return;
    }
    const correo = await this.correoDialog.pedir({
      folio: c.folio,
      correoCliente: c.clienteEmail ?? null,
    });
    if (!correo) {
      return;
    }
    this.enviando.set(true);
    this.service.enviarCorreo(this.id(), { email: correo }).subscribe({
      next: (cotizacion) => {
        this.enviando.set(false);
        this.cotizacion.set(cotizacion);
        this.toast.exito(`Cotizacion enviada a ${correo}.`);
        // Aviso no intrusivo si la Empresa emisora tiene datos fiscales
        // incompletos: el envio no se bloquea, solo se invita a completarlos (Req 3).
        if (cotizacion.emisorIncompleto === true) {
          this.toast.info(
            'Completa los datos fiscales de tu empresa (RFC y direccion) en Mi empresa para que tus cotizaciones salgan completas.',
          );
        }
      },
      error: (e: HttpErrorResponse) => {
        this.enviando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /**
   * Comparte la cotizacion por WhatsApp abriendo wa.me con un mensaje prellenado
   * (folio, total, moneda y validez). No adjunta el PDF: WhatsApp no admite
   * adjuntos por URL, por lo que se avisa al Usuario que puede descargar el PDF y
   * adjuntarlo manualmente. Si el DTO trae telefono, se usa el numero (LADA MX 52).
   */
  compartirWhatsApp(): void {
    const c = this.cotizacion();
    if (!c) {
      return;
    }
    const cliente = c.clienteNombre ? ` ${c.clienteNombre}` : '';
    const total = c.total.toLocaleString('es-MX', {
      style: 'currency',
      currency: this.moneda(),
    });
    const validez = c.validoHasta
      ? ` Validez: ${new Date(c.validoHasta).toLocaleDateString('es-MX')}.`
      : '';
    const mensaje =
      `Hola${cliente}, te compartimos la cotizacion ${c.folio ?? ''} ` +
      `por un total de ${total}.${validez} Quedamos atentos.`;
    const url = `https://wa.me/?text=${encodeURIComponent(mensaje.trim())}`;
    window.open(url, '_blank', 'noopener');
    this.toast.info('Recuerda: el PDF puede descargarse y adjuntarse manualmente en WhatsApp.');
  }

  /** Abre/cierra el formulario de agregar partida. */
  alternarFormPartida(): void {
    this.formPartidaAbierto.update((v) => !v);
    if (this.formPartidaAbierto()) {
      this.formPartida.reset({ productoId: '', descripcion: '', cantidad: 1, precioUnitario: null });
    }
  }

  /** Agrega una partida a la Cotizacion en borrador (Req 6.3). */
  agregarPartida(): void {
    if (this.formPartida.invalid) {
      this.formPartida.markAllAsTouched();
      return;
    }
    const v = this.formPartida.getRawValue();
    this.guardando.set(true);
    this.service
      .agregarPartida(this.id(), {
        productoId: v.productoId.trim() || null,
        descripcion: v.descripcion.trim(),
        cantidad: Number(v.cantidad),
        precioUnitario: v.precioUnitario == null ? null : Number(v.precioUnitario),
      })
      .subscribe({
        next: (cotizacion) => {
          this.guardando.set(false);
          this.cotizacion.set(cotizacion);
          this.formPartidaAbierto.set(false);
          this.toast.exito('Partida agregada.');
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  /** Cambia el estado de la Cotizacion con confirmacion (Req 6.6). */
  async cambiarEstado(estado: EstadoCotizacion): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Cambiar estado',
      mensaje: `La cotizacion pasara a "${ETIQUETA_ESTADO_COTIZACION[estado]}". Deseas continuar?`,
      textoConfirmar: 'Cambiar estado',
      destructiva: estado === 'rechazada',
    });
    if (!ok) {
      return;
    }
    this.service.cambiarEstado(this.id(), estado).subscribe({
      next: (cotizacion) => {
        this.cotizacion.set(cotizacion);
        this.toast.exito('Estado actualizado.');
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Abre el dialogo para asignar/cambiar el canal de venta de la Cotizacion (Req 3.3). */
  abrirAsignarCanal(): void {
    this.formCanal.reset({ canalId: this.cotizacion()?.canalVentaId ?? '' });
    this.asignandoCanal.set(true);
  }

  /** Cierra el dialogo de asignacion de canal sin guardar. */
  cerrarAsignarCanal(): void {
    this.asignandoCanal.set(false);
  }

  /**
   * Guarda el canal elegido invocando el endpoint existente (canalId vacio limpia
   * el canal), actualiza la Cotizacion en memoria y cierra el dialogo (Req 3.3, 63.1).
   */
  guardarCanal(): void {
    const c = this.cotizacion();
    if (!c || this.guardando()) {
      return;
    }
    const canalId = this.formCanal.getRawValue().canalId || null;
    this.guardando.set(true);
    this.service.asignarCanalVenta(c.id, canalId).subscribe({
      next: (cotizacion) => {
        this.guardando.set(false);
        this.cotizacion.set(cotizacion);
        this.asignandoCanal.set(false);
        this.toast.exito(canalId ? 'Canal de venta asignado.' : 'Canal de venta retirado.');
      },
      error: (e: HttpErrorResponse) => {
        this.guardando.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /** Genera la Prueba de Diseno inicial (Req 15.1). */
  generarPrueba(): void {
    this.service.generarPrueba(this.id()).subscribe({
      next: () => {
        this.toast.exito('Prueba de diseno generada.');
        this.cargarPruebas();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Aprueba una Prueba de Diseno pendiente (Req 15.2). */
  aprobarPrueba(prueba: PruebaDiseno): void {
    this.service.aprobarPrueba(prueba.id).subscribe({
      next: () => {
        this.toast.exito(`Prueba v${prueba.numeroVersion} aprobada.`);
        this.cargarPruebas();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Rechaza una Prueba de Diseno pendiente y genera la nueva version (Req 15.3). */
  async rechazarPrueba(prueba: PruebaDiseno): Promise<void> {
    const ok = await this.confirm.confirmar({
      titulo: 'Rechazar prueba de diseno',
      mensaje: `Se rechazara la version ${prueba.numeroVersion} y se generara una nueva version pendiente. Deseas continuar?`,
      textoConfirmar: 'Rechazar',
      destructiva: true,
    });
    if (!ok) {
      return;
    }
    this.service.rechazarPrueba(prueba.id).subscribe({
      next: () => {
        this.toast.exito('Prueba rechazada; nueva version generada.');
        this.cargarPruebas();
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }
}
