// =============================================================================
// Vista de Conexiones (gestion de Cuentas de Canal_Social) (Req 2, redes-sociales)
// -----------------------------------------------------------------------------
// Punto de entrada del modulo social: lista las cuentas conectadas del tenant y
// permite registrar nuevas (canal + nombre + identificador externo + referencia
// de credencial). NUNCA pide ni muestra la credencial en claro, solo su
// referencia; nunca expone UUIDs. Estados carga/vacio/error via componentes
// compartidos, en espanol, responsivo y WCAG AA. Reutiliza POST/GET
// /social/cuentas-canal (no crea endpoints nuevos).
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
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
  CeldaTablaDirective,
  ColumnaTabla,
  DataTable,
} from '../../../shared/components/data-table/data-table';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError, erroresDeCampo } from '../../../core/services/error-mensajes';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../shared/models/estado-solicitud';

import { CuentasCanalService } from '../services/cuentas-canal.service';
import { CanalSocial, CuentaCanalSocial } from '../models/social.models';
import { ETIQUETA_CANAL, ICONO_CANAL, OPCIONES_CANAL } from '../social-etiquetas';

/** Ayudas por canal para el campo "identificador externo". */
const AYUDA_IDENTIFICADOR: Record<CanalSocial, string> = {
  whatsapp: 'Numero de WhatsApp Business (con lada), p. ej. +52 55 1234 5678.',
  facebook: 'ID o nombre de usuario de la pagina de Facebook.',
  instagram: 'Usuario del perfil de Instagram, p. ej. @miempresa.',
  messenger: 'ID de la pagina asociada a Messenger.',
  tiktok: 'Usuario del perfil de TikTok, p. ej. @miempresa.',
};

@Component({
  selector: 'app-conexiones',
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
  ],
  templateUrl: './conexiones.html',
  styleUrl: './conexiones.scss',
})
export class Conexiones {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CuentasCanalService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);

  protected readonly puedeCrear = this.auth.tienePermiso('cuenta_canal_social', 'crear');

  /** Opciones de canal derivadas del origen unico (los cinco canales). */
  protected readonly canales = OPCIONES_CANAL;

  protected readonly columnas: ColumnaTabla[] = [
    { clave: 'canal', encabezado: 'Canal' },
    { clave: 'nombre', encabezado: 'Nombre' },
    { clave: 'identificador', encabezado: 'Identificador' },
    { clave: 'estado', encabezado: 'Estado' },
  ];

  protected readonly estado = signal<EstadoSolicitud<CuentaCanalSocial[]>>(cargando());
  protected readonly total = signal(0);
  protected readonly guardando = signal(false);
  protected readonly mostrarAlta = signal(false);
  /** Mensaje de conflicto (409) mostrado junto al formulario, si aplica. */
  protected readonly mensajeConflicto = signal<string | null>(null);

  protected readonly formAlta = this.fb.nonNullable.group({
    canal: ['' as '' | CanalSocial, [Validators.required]],
    nombre: ['', [Validators.required]],
    identificadorExterno: ['', [Validators.required]],
    credencialesRef: ['', [Validators.required]],
  });

  /** Ayuda contextual del identificador segun el canal elegido. */
  protected readonly ayudaIdentificador = computed(() => {
    const canal = this.canalSeleccionado();
    return canal
      ? AYUDA_IDENTIFICADOR[canal]
      : 'Numero de WhatsApp, ID de pagina, usuario de perfil...';
  });

  private readonly canalSeleccionado = signal<CanalSocial | ''>('');

  constructor() {
    this.formAlta.controls.canal.valueChanges.subscribe((v) =>
      this.canalSeleccionado.set(v ?? ''),
    );
    this.cargar();
  }

  etiquetaCanal(valor: string): string {
    return ETIQUETA_CANAL[valor as CanalSocial] ?? valor;
  }

  iconoCanal(valor: string): string {
    return ICONO_CANAL[valor as CanalSocial] ?? 'public';
  }

  cargar(): void {
    this.estado.set(cargando());
    this.service.listar(null, 0, 100).subscribe({
      next: (pagina) => {
        this.total.set(pagina.totalElements);
        this.estado.set(conDatos(pagina.content, pagina.content.length === 0));
      },
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  alternarAlta(): void {
    this.mensajeConflicto.set(null);
    this.mostrarAlta.update((v) => !v);
  }

  crear(): void {
    this.mensajeConflicto.set(null);
    if (this.formAlta.invalid) {
      this.formAlta.markAllAsTouched();
      return;
    }
    const v = this.formAlta.getRawValue();
    this.guardando.set(true);
    this.service
      .crear({
        canal: v.canal as CanalSocial,
        nombre: v.nombre,
        identificadorExterno: v.identificadorExterno,
        credencialesRef: v.credencialesRef,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.exito('Cuenta conectada.');
          this.reiniciarFormulario();
          this.mostrarAlta.set(false);
          this.cargar();
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          if (e.status === 409) {
            this.mensajeConflicto.set(
              'Ya existe una cuenta de ese canal con ese identificador.',
            );
            return;
          }
          if (e.status === 422) {
            const campos = erroresDeCampo(e);
            const detalle = campos.length
              ? campos.map((c) => c.mensaje).join(' ')
              : mensajeDeError(e);
            this.mensajeConflicto.set(detalle);
            return;
          }
          this.toast.error(mensajeDeError(e));
        },
      });
  }

  private reiniciarFormulario(): void {
    this.formAlta.reset({
      canal: '',
      nombre: '',
      identificadorExterno: '',
      credencialesRef: '',
    });
    this.canalSeleccionado.set('');
  }
}
