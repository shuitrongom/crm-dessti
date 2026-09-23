// =============================================================================
// Vista de configuracion de Branding (admin_empresa) (Req 26)
// -----------------------------------------------------------------------------
// Consulta y actualiza el nombre visible y el logotipo de la empresa. El logo se
// carga como ARCHIVO de imagen (no como URL/data-URI en texto): se valida tipo y
// tamano en el cliente y se lee como data-URI (base64) para enviarlo al backend
// (PUT /empresa/branding). Incluye previsualizacion en vivo del logotipo vigente
// o del recien seleccionado, y un boton para quitarlo.
// =============================================================================

import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PageHeader } from '../../../../shared/components/page-header/page-header';
import { StateContainer } from '../../../../shared/components/state-container/state-container';
import { NotificacionesService } from '../../../../shared/services/notificaciones.service';
import { AuthService } from '../../../../core/auth/auth.service';
import { mensajeDeError } from '../../../../core/services/error-mensajes';
import { TematizacionService } from '../../../../core/services/tematizacion.service';
import { esHexValido } from '../../../../core/theming/color-utils';
import {
  EstadoSolicitud,
  cargando,
  conDatos,
  conError,
} from '../../../../shared/models/estado-solicitud';

import { BrandingService } from '../../services/branding.service';
import { Branding } from '../../home/home.models';

/** Tipos MIME de imagen admitidos para el logo del branding. */
const TIPOS_LOGO = ['image/png', 'image/jpeg', 'image/svg+xml', 'image/webp'];
/** Tamano maximo del archivo de logo en bytes (1 MB, alineado con el limite del backend). */
const MAX_LOGO_BYTES = 1024 * 1024;

/** Preset de color de marca ofrecido como muestra rapida (Req 1.1, 1.2). */
interface PresetColor {
  /** Nombre legible del color, usado en la etiqueta accesible. */
  readonly nombre: string;
  /** Valor hexadecimal `#RRGGBB` del preset. */
  readonly hex: string;
}

/**
 * Conjunto de Preset_Color ofrecidos en la vista (Req 1.1). Cada muestra fija
 * el Color_Primario_Marca a su valor hexadecimal al hacer clic (Req 1.2).
 */
const PRESETS_COLOR: ReadonlyArray<PresetColor> = [
  { nombre: 'Azul corporativo', hex: '#35507a' },
  { nombre: 'Azul brillante', hex: '#2563eb' },
  { nombre: 'Indigo', hex: '#4f46e5' },
  { nombre: 'Violeta', hex: '#7c3aed' },
  { nombre: 'Magenta', hex: '#9d174d' },
  { nombre: 'Rosa', hex: '#db2777' },
  { nombre: 'Rojo', hex: '#dc2626' },
  { nombre: 'Vino', hex: '#8a1f3d' },
  { nombre: 'Naranja', hex: '#ea580c' },
  { nombre: 'Ambar', hex: '#b45309' },
  { nombre: 'Verde', hex: '#2e7d4f' },
  { nombre: 'Esmeralda', hex: '#059669' },
  { nombre: 'Teal', hex: '#0f766e' },
  { nombre: 'Cian', hex: '#0e7490' },
  { nombre: 'Grafito', hex: '#374151' },
  { nombre: 'Pizarra', hex: '#475569' },
];

@Component({
  selector: 'app-admin-branding',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    StateContainer,
  ],
  templateUrl: './branding.html',
  styleUrl: './branding.scss',
})
export class AdminBranding {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(BrandingService);
  private readonly toast = inject(NotificacionesService);
  private readonly auth = inject(AuthService);
  private readonly tematizacion = inject(TematizacionService);

  protected readonly estado = signal<EstadoSolicitud<Branding>>(cargando());
  protected readonly guardando = signal(false);
  protected readonly puedeActualizar = this.auth.tienePermiso('branding', 'actualizar');

  /** Lista de Preset_Color ofrecidos como muestras rapidas (Req 1.1, 1.2). */
  protected readonly presets = PRESETS_COLOR;

  /** Data-URI del logotipo (vigente o recien seleccionado), o `null` si no hay. */
  protected readonly logo = signal<string | null>(null);
  /** Nombre del archivo recien cargado, para la etiqueta accesible del preview. */
  protected readonly logoNombre = signal<string | null>(null);
  /** Mensaje de error de la carga del logo (tipo/tamano invalido). */
  protected readonly logoError = signal<string | null>(null);

  protected readonly formulario = this.fb.nonNullable.group({
    nombreVisible: [''],
    /** Color primario de marca `#RRGGBB` o cadena vacia si no hay color. */
    colorPrimario: [''],
  });

  /** Control tipado del color primario, para uso interno. */
  private readonly controlColor = this.formulario.controls.colorPrimario;

  /**
   * Valor vigente del control de color (reactivo). Se sincroniza con los cambios
   * del formulario para derivar la validez y la previsualizacion en vivo.
   */
  private readonly colorValor = toSignal(this.controlColor.valueChanges, {
    initialValue: this.controlColor.value,
  });

  /** Indica si el valor de color actual esta vacio (sin color de marca). */
  protected readonly colorVacio = computed(() => (this.colorValor() ?? '').trim() === '');

  /**
   * Indica si el valor de color actual es invalido: no vacio y sin cumplir
   * `#RRGGBB` (Req 1.4). Un valor vacio se considera valido (usar Tema_Corporativo).
   */
  protected readonly colorInvalido = computed(() => {
    const valor = (this.colorValor() ?? '').trim();
    return valor !== '' && !esHexValido(valor);
  });

  /**
   * Valor a mostrar en el selector `<input type="color">`, que solo admite un
   * `#RRGGBB` valido. Ante valor vacio o invalido cae a un neutro por defecto
   * para no romper el control nativo (el texto hex es la fuente de verdad).
   */
  protected readonly colorSelector = computed(() => {
    const valor = (this.colorValor() ?? '').trim();
    return esHexValido(valor) ? valor.toLowerCase() : '#374151';
  });

  constructor() {
    this.cargar();
    // Previsualizacion en vivo (Req 1.5): al cambiar el color por selector o
    // preset, si es un hex valido se previsualiza; si es invalido no se toca la
    // paleta vigente (Req 1.4); si se vacia se restaura el Tema_Corporativo.
    this.controlColor.valueChanges.subscribe((valor) => {
      const hex = (valor ?? '').trim();
      if (hex === '') {
        this.tematizacion.limpiar();
      } else if (esHexValido(hex)) {
        this.tematizacion.previsualizar(hex);
      }
    });
  }

  /** Fija el Color_Primario_Marca al valor de un Preset_Color (Req 1.2). */
  seleccionarPreset(hex: string): void {
    if (!this.puedeActualizar) {
      return;
    }
    this.controlColor.setValue(hex);
  }

  /**
   * Limpia el color de marca: vacia el control y restaura el Tema_Corporativo
   * (Req 1.7). El proximo guardado enviara `colorPrimario: null`.
   */
  usarColorPorDefecto(): void {
    if (!this.puedeActualizar) {
      return;
    }
    this.controlColor.setValue('');
  }

  /** Carga el branding vigente (Req 26.2). */
  cargar(): void {
    this.estado.set(cargando());
    this.service.consultar().subscribe({
      next: (b) => {
        this.estado.set(conDatos(b));
        const color = b.colorPrimario ?? '';
        this.formulario.setValue({ nombreVisible: b.nombreVisible ?? '', colorPrimario: color });
        this.logo.set(b.logo ?? null);
        this.logoNombre.set(null);
        this.logoError.set(null);
        // Sincroniza la previsualizacion con el color persistido (Req 1.5):
        // aplica el color guardado o restaura el Tema_Corporativo si es nulo.
        if (esHexValido(color)) {
          this.tematizacion.aplicar(color);
        } else {
          this.tematizacion.limpiar();
        }
      },
      error: (e: HttpErrorResponse) => this.estado.set(conError(mensajeDeError(e))),
    });
  }

  /**
   * Carga el logo desde el input de archivo: valida que sea una imagen admitida
   * y que no supere 1 MB, y lo lee como data-URI (base64). Si la validacion
   * falla, muestra un mensaje claro y NO modifica el logo actual.
   */
  seleccionarLogo(evento: Event): void {
    this.logoError.set(null);
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    if (!archivo) {
      return;
    }
    if (!TIPOS_LOGO.includes(archivo.type)) {
      this.logoError.set('Formato no admitido. Usa PNG, JPG, SVG o WebP.');
      input.value = '';
      return;
    }
    if (archivo.size > MAX_LOGO_BYTES) {
      this.logoError.set('El logo supera el tamano maximo de 1 MB.');
      input.value = '';
      return;
    }
    const lector = new FileReader();
    lector.onload = () => {
      this.logo.set(String(lector.result));
      this.logoNombre.set(archivo.name);
    };
    lector.onerror = () => this.logoError.set('No se pudo leer el archivo del logo.');
    lector.readAsDataURL(archivo);
    // Permite volver a elegir el mismo archivo tras quitarlo.
    input.value = '';
  }

  /** Quita el logotipo actual (se enviara `logo: null` al guardar). */
  quitarLogo(): void {
    this.logo.set(null);
    this.logoNombre.set(null);
    this.logoError.set(null);
  }

  /** Guarda el nombre visible, el logotipo y el color de marca (Req 1.6, 6.7, 9.2). */
  guardar(): void {
    if (!this.puedeActualizar) {
      return;
    }
    // No permitir guardar un color con formato invalido (Req 1.4).
    if (this.colorInvalido()) {
      return;
    }
    this.guardando.set(true);
    const valores = this.formulario.getRawValue();
    const nombreVisible = valores.nombreVisible.trim();
    const colorHex = valores.colorPrimario.trim();
    // Color a persistir: hex valido en minusculas, o `null` para limpiarlo (Req 1.7).
    const colorPrimario = esHexValido(colorHex) ? colorHex.toLowerCase() : null;
    this.service
      .actualizar({
        nombreVisible: nombreVisible ? nombreVisible : null,
        logo: this.logo(),
        colorPrimario,
      })
      .subscribe({
        next: (b) => {
          this.guardando.set(false);
          this.estado.set(conDatos(b));
          this.logo.set(b.logo ?? null);
          this.logoNombre.set(null);
          // Aplica definitivamente el color persistido o restaura el tema (Req 1.6, 1.7).
          if (esHexValido(b.colorPrimario ?? '')) {
            this.tematizacion.aplicar(b.colorPrimario as string);
          } else {
            this.tematizacion.limpiar();
          }
          this.toast.exito('Branding actualizado.');
        },
        error: (e: HttpErrorResponse) => {
          this.guardando.set(false);
          this.toast.error(mensajeDeError(e));
        },
      });
  }
}
