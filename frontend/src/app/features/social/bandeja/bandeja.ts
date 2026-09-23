// =============================================================================
// Vista Bandeja unificada omnicanal (Req 64)
// -----------------------------------------------------------------------------
// Lista las Conversaciones consolidadas de todos los canales (WhatsApp,
// Messenger, Instagram) con un indicador de VENTANA DE SERVICIO por conversacion
// (24 h desde el ultimo entrante). Al abrir una conversacion muestra el historial
// de mensajes y un compositor: dentro de la ventana admite texto libre; fuera de
// ella solo plantillas aprobadas (Req 64.6, 64.7). Permite handover (asignar) y
// cierre. El refresco es a demanda (no hay realtime del backend).
//
// El "ahora" para el calculo de la ventana se congela por render (signal) y se
// puede refrescar; la logica de ventana es pura y esta cubierta por pruebas.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonToggleModule } from '@angular/material/button-toggle';

import { PageHeader } from '../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../shared/components/state-container/state-container';
import { EntitySelect } from '../../../shared/components/entity-select/entity-select';
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

import { BandejaService, FiltroBandeja } from '../services/bandeja.service';
import { PlantillasService } from '../services/plantillas.service';
import { ClientesService } from '../../comercial/services/clientes.service';
import { Cliente } from '../../comercial/models/comercial.models';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  Conversacion,
  MensajeSocial,
  PlantillaMensaje,
} from '../models/social.models';
import { ETIQUETA_CANAL, ETIQUETA_TIPO_MENSAJE, ICONO_CANAL } from '../social-etiquetas';
import {
  EstadoVentana,
  VENTANA_HORAS_DEFECTO,
  VentanaServicio,
  calcularVentanaServicio,
  etiquetaVentana,
  textoRestante,
  tonoVentana,
} from '../ventana-servicio';

@Component({
  selector: 'app-bandeja',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatMenuModule,
    MatButtonToggleModule,
    RouterLink,
    PageHeader,
    StateContainer,
    EntitySelect,
    EstadoChip,
  ],
  templateUrl: './bandeja.html',
  styleUrl: './bandeja.scss',
})
export class Bandeja {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(BandejaService);
  private readonly plantillasService = inject(PlantillasService);
  private readonly clientes = inject(ClientesService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly humanizar = humanizarEstado;
  protected readonly tono = tonoDeEstado;

  protected readonly puedeEnviar = this.auth.tienePermiso('conversacion', 'enviar');
  protected readonly puedeActualizar = this.auth.tienePermiso('conversacion', 'actualizar');
  /** El enlace al Cliente vinculado solo se ofrece si el Usuario puede abrir su Ficha 360 (Req 5.5, 7.2). */
  protected readonly puedeVerCliente = this.auth.tienePermiso('cliente', 'leer');

  protected readonly canales = [
    { valor: '', etiqueta: 'Todos los canales' },
    { valor: 'whatsapp', etiqueta: 'WhatsApp' },
    { valor: 'messenger', etiqueta: 'Messenger' },
    { valor: 'instagram', etiqueta: 'Instagram' },
  ];

  protected readonly estados = [
    { valor: '', etiqueta: 'Todos los estados' },
    { valor: 'abierta', etiqueta: 'Abierta' },
    { valor: 'asignada', etiqueta: 'Asignada' },
    { valor: 'cerrada', etiqueta: 'Cerrada' },
  ];

  // --- Listado de conversaciones ---
  protected readonly estado = signal<EstadoSolicitud<Conversacion[]>>(cargando());
  protected readonly filtroCanal = signal('');
  protected readonly filtroEstado = signal('');

  // --- Instante de referencia para la Ventana_Servicio (congelado por render) ---
  protected readonly ahora = signal<Date>(new Date());

  // --- Conversacion seleccionada + historial ---
  protected readonly seleccionada = signal<Conversacion | null>(null);
  protected readonly mensajes = signal<EstadoSolicitud<MensajeSocial[]>>(cargando());
  protected readonly plantillas = signal<PlantillaMensaje[]>([]);
  protected readonly enviando = signal(false);

  // --- Vinculacion a Cliente (lead social, Req 5.1, 5.2) ---
  /** Nombre del Cliente vinculado a la conversacion seleccionada (sin exponer el UUID). */
  protected readonly nombreClienteVinculado = signal<string | null>(null);
  /** Indica que el panel para elegir un Cliente esta abierto. */
  protected readonly vinculando = signal(false);
  /** Indica un guardado de vinculacion en curso. */
  protected readonly guardandoVinculo = signal(false);
  /** Formulario del selector de Cliente (expone solo el id; nunca se teclea el UUID). */
  protected readonly formVinculo = this.fb.nonNullable.group({
    clienteId: ['', [Validators.required]],
  });

  /** Ventana de servicio de la conversacion seleccionada. */
  protected readonly ventanaSeleccionada = computed<VentanaServicio | null>(() => {
    const c = this.seleccionada();
    if (!c) {
      return null;
    }
    return calcularVentanaServicio(c.ultimoEntranteUtc, this.ahora(), VENTANA_HORAS_DEFECTO);
  });

  /** Solo se admite texto libre dentro/por-expirar de la ventana y si la conversacion no esta cerrada. */
  protected readonly admiteTextoLibre = computed<boolean>(() => {
    const c = this.seleccionada();
    const v = this.ventanaSeleccionada();
    return !!c && c.estado !== 'cerrada' && !!v && v.permiteTextoLibre;
  });

  protected readonly formMensaje = this.fb.nonNullable.group({
    tipo: ['texto', [Validators.required]],
    contenido: ['', [Validators.required]],
    plantillaId: [''],
    esMarketing: [false],
  });

  constructor() {
    this.cargar();
  }

  // ---------------------------------------------------------------------------
  // Etiquetas (metodos para no indexar Record<Enum,string> en plantilla estricta)
  // ---------------------------------------------------------------------------
  etiquetaCanal(valor: string): string {
    return ETIQUETA_CANAL[valor as keyof typeof ETIQUETA_CANAL] ?? valor;
  }

  iconoCanal(valor: string): string {
    return ICONO_CANAL[valor as keyof typeof ICONO_CANAL] ?? 'public';
  }

  etiquetaTipoMensaje(valor: string): string {
    return ETIQUETA_TIPO_MENSAJE[valor as keyof typeof ETIQUETA_TIPO_MENSAJE] ?? valor;
  }

  // ---------------------------------------------------------------------------
  // Ventana de servicio (indicadores por conversacion)
  // ---------------------------------------------------------------------------
  ventanaDe(c: Conversacion): VentanaServicio {
    return calcularVentanaServicio(c.ultimoEntranteUtc, this.ahora(), VENTANA_HORAS_DEFECTO);
  }

  etiquetaVentana(estado: EstadoVentana): string {
    return etiquetaVentana(estado);
  }

  tonoVentana(estado: EstadoVentana): 'exito' | 'advertencia' | 'error' {
    return tonoVentana(estado);
  }

  textoRestante(v: VentanaServicio): string {
    return textoRestante(v);
  }

  // ---------------------------------------------------------------------------
  // Carga de datos
  // ---------------------------------------------------------------------------
  cargar(): void {
    this.ahora.set(new Date());
    this.estado.set(cargando());
    const filtro: FiltroBandeja = {
      canal: this.filtroCanal() || null,
      estado: this.filtroEstado() || null,
    };
    // Bandeja de conversaciones activas: tamano amplio, orden del servidor.
    this.service.listar(filtro, 0, 50).subscribe({
      next: (pagina) => this.estado.set(conDatos(pagina.content, pagina.content.length === 0)),
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  aplicarCanal(valor: string): void {
    this.filtroCanal.set(valor);
    this.cargar();
  }

  aplicarEstado(valor: string): void {
    this.filtroEstado.set(valor);
    this.cargar();
  }

  seleccionar(c: Conversacion): void {
    this.ahora.set(new Date());
    this.seleccionada.set(c);
    this.reiniciarCompositor();
    this.cerrarVinculacion();
    this.cargarMensajes(c);
    this.cargarPlantillas(c);
    this.resolverClienteVinculado(c);
  }

  cerrarDetalle(): void {
    this.seleccionada.set(null);
    this.mensajes.set(cargando());
    this.plantillas.set([]);
    this.cerrarVinculacion();
    this.nombreClienteVinculado.set(null);
  }

  private cargarMensajes(c: Conversacion): void {
    this.mensajes.set(cargando());
    this.service.listarMensajes(c.id, 0, 100).subscribe({
      next: (pagina) => this.mensajes.set(conDatos(pagina.content, pagina.content.length === 0)),
      error: (e: HttpErrorResponse) => this.mensajes.set(conError(mensajeDeError(e))),
    });
  }

  private cargarPlantillas(c: Conversacion): void {
    this.plantillasService.listar(c.canal, 0, 100).subscribe({
      next: (pagina) => this.plantillas.set(pagina.content.filter((p) => p.aprobada)),
      error: () => this.plantillas.set([]),
    });
  }

  // ---------------------------------------------------------------------------
  // Compositor de mensajes
  // ---------------------------------------------------------------------------
  private reiniciarCompositor(): void {
    const tipoInicial = this.admiteTextoLibre() ? 'texto' : 'plantilla';
    this.formMensaje.reset({
      tipo: tipoInicial,
      contenido: '',
      plantillaId: '',
      esMarketing: false,
    });
  }

  cambiarTipo(tipo: string): void {
    this.formMensaje.patchValue({ tipo, contenido: '', plantillaId: '' });
  }

  usarPlantilla(id: string): void {
    const plantilla = this.plantillas().find((p) => p.id === id);
    this.formMensaje.patchValue({
      plantillaId: id,
      contenido: plantilla ? plantilla.contenido : '',
    });
  }

  enviar(): void {
    const c = this.seleccionada();
    if (!c || this.formMensaje.invalid) {
      this.formMensaje.markAllAsTouched();
      return;
    }
    const v = this.formMensaje.getRawValue();
    // Guarda de UI coherente con el backend: texto libre solo dentro de la ventana.
    if (v.tipo === 'texto' && !this.admiteTextoLibre()) {
      this.toast.error(
        'La ventana de servicio expiro; solo se permiten plantillas aprobadas fuera de las 24 h.',
      );
      return;
    }
    this.enviando.set(true);
    this.service
      .enviarMensaje(c.id, {
        tipo: v.tipo,
        contenido: v.contenido,
        esMarketing: v.esMarketing,
      })
      .subscribe({
        next: () => {
          this.enviando.set(false);
          this.toast.exito('Mensaje enviado.');
          this.reiniciarCompositor();
          this.cargarMensajes(c);
        },
        error: (e: HttpErrorResponse) => {
          this.enviando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  // ---------------------------------------------------------------------------
  // Vinculacion a Cliente (lead social, Req 5.1, 5.2)
  // ---------------------------------------------------------------------------

  /** Busca Clientes por nombre/RFC para el selector (nunca se teclea el UUID). */
  protected readonly buscarCliente = (filtro: string): Observable<PaginaResponse<Cliente>> =>
    this.clientes.listar(filtro, 0, 20);

  /** Etiqueta principal de un Cliente en el selector. */
  protected readonly etiquetaCliente = (cliente: Cliente): string => cliente.nombre;

  /** Detalle secundario (RFC) de un Cliente en el selector. */
  protected readonly detalleCliente = (cliente: Cliente): string | null => cliente.rfc || null;

  /** Abre el panel para elegir un Cliente al que vincular la conversacion. */
  abrirVinculacion(): void {
    this.formVinculo.reset({ clienteId: '' });
    this.vinculando.set(true);
  }

  /** Cierra el panel de vinculacion sin guardar. */
  cerrarVinculacion(): void {
    this.vinculando.set(false);
    this.guardandoVinculo.set(false);
    this.formVinculo.reset({ clienteId: '' });
  }

  /** Vincula la conversacion seleccionada al Cliente elegido (PUT vinculacion). */
  vincular(): void {
    const c = this.seleccionada();
    if (!c || this.formVinculo.invalid) {
      this.formVinculo.markAllAsTouched();
      return;
    }
    const clienteId = this.formVinculo.getRawValue().clienteId;
    this.guardandoVinculo.set(true);
    this.service.vincular(c.id, clienteId).subscribe({
      next: (actualizada) => {
        this.guardandoVinculo.set(false);
        this.vinculando.set(false);
        this.toast.exito('Conversacion vinculada al cliente.');
        this.actualizarEnLista(actualizada);
        this.resolverClienteVinculado(actualizada);
      },
      error: (e: HttpErrorResponse) => {
        this.guardandoVinculo.set(false);
        this.toast.error(mensajeDeError(e));
      },
    });
  }

  /**
   * Resuelve el nombre del Cliente vinculado para mostrarlo como enlace a su Ficha
   * 360 (nunca el UUID). Solo consulta si el Usuario puede leer Clientes (Req 7.2).
   */
  private resolverClienteVinculado(c: Conversacion): void {
    this.nombreClienteVinculado.set(null);
    if (!c.clienteId || !this.puedeVerCliente) {
      return;
    }
    this.clientes.consultar(c.clienteId).subscribe({
      next: (cliente) => this.nombreClienteVinculado.set(cliente.nombre),
      // Si no se puede resolver el nombre, se degrada sin romper (no se muestra enlace).
      error: () => this.nombreClienteVinculado.set(null),
    });
  }

  // ---------------------------------------------------------------------------
  // Handover y cierre
  // ---------------------------------------------------------------------------
  async asignarme(): Promise<void> {
    const c = this.seleccionada();
    const usuarioId = this.auth.identificador();
    if (!c || !usuarioId) {
      return;
    }
    this.service.asignar(c.id, usuarioId).subscribe({
      next: (actualizada) => {
        this.toast.exito('Conversacion asignada.');
        this.actualizarEnLista(actualizada);
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  async cerrarConversacion(): Promise<void> {
    const c = this.seleccionada();
    if (!c) {
      return;
    }
    const ok = await this.confirm.confirmar({
      titulo: 'Cerrar conversacion',
      mensaje: 'Se dara por concluida la atencion. Se conserva el historial. Continuar?',
      textoConfirmar: 'Cerrar conversacion',
    });
    if (!ok) {
      return;
    }
    this.service.cerrar(c.id).subscribe({
      next: (actualizada) => {
        this.toast.exito('Conversacion cerrada.');
        this.actualizarEnLista(actualizada);
      },
      error: (e: HttpErrorResponse) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Refleja el cambio de una conversacion en la lista y el detalle. */
  private actualizarEnLista(actualizada: Conversacion): void {
    this.seleccionada.set(actualizada);
    const actual = this.estado().datos;
    if (actual) {
      const nuevas = actual.map((c) => (c.id === actualizada.id ? actualizada : c));
      this.estado.set(conDatos(nuevas, nuevas.length === 0));
    }
  }
}
