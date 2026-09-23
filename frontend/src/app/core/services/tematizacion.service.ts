import { Injectable, computed, effect, inject, signal } from '@angular/core';

import { esHexValido } from '../theming/color-utils';
import { PaletaDerivada, derivarPaleta } from '../theming/derivar-paleta';
import { ThemeService } from './theme.service';

/**
 * Servicio de **tematización por empresa** en tiempo de ejecución (Req 3.1–3.5).
 *
 * Aplica en runtime un Color_Primario_Marca sobrescribiendo las CSS custom
 * properties de color (`--ds-color-*`) sobre el elemento raíz del documento
 * (`document.documentElement`). El color se transforma en una
 * {@link PaletaDerivada} coherente y accesible mediante la función pura
 * {@link derivarPaleta}, y cada campo se escribe en su Token_CSS
 * correspondiente. Limpiar la tematización elimina esas sobrescrituras inline,
 * de modo que el documento vuelve a resolver el Tema_Corporativo definido en
 * `frontend/src/styles/_tokens.scss`.
 *
 * **Alcance de la escritura:** este servicio toca **exclusivamente** tokens de
 * color `--ds-color-*`. Nunca modifica tokens de tipografía, espaciado, radios,
 * sombras ni animación, garantizando la no regresión del resto del Sistema de
 * Diseño (Req 9.3).
 *
 * **Modo claro/oscuro:** el servicio observa el modo activo de
 * {@link ThemeService} mediante un `effect` creado en el constructor (contexto
 * de inyección). Cuando el usuario conmuta claro/oscuro y hay un color activo,
 * reaplica la variante correspondiente sin recargar la aplicación (Req 3.4).
 *
 * **Contrato de color inválido:** si el color recibido no cumple `#RRGGBB`
 * (validado con {@link esHexValido}), `aplicar` y `previsualizar` no escriben
 * nada y no lanzan; el color vigente se conserva.
 *
 * @see derivarPaleta
 * @see ThemeService
 */
@Injectable({ providedIn: 'root' })
export class TematizacionService {
  private readonly theme = inject(ThemeService);

  /**
   * Mapeo de cada campo de {@link PaletaDerivada} a su Token_CSS de color.
   *
   * Los cinco primeros corresponden a tokens del Tema_Corporativo ya definidos
   * en `_tokens.scss` (`--ds-color-primary` y derivados). Los tres últimos son
   * **tokens de acento adicionales**, nuevos, que no rompen ni redefinen los
   * existentes: aportan superficies/textos de realce derivados de la marca.
   */
  private static readonly MAPEO_TOKENS: ReadonlyArray<
    readonly [keyof PaletaDerivada, string]
  > = [
    ['primary', '--ds-color-primary'],
    ['primaryHover', '--ds-color-primary-hover'],
    ['primaryContainer', '--ds-color-primary-container'],
    ['textOnPrimary', '--ds-color-text-on-primary'],
    ['focusRing', '--ds-color-focus-ring'],
    // Tokens de acento adicionales (nuevos; no rompen el Tema_Corporativo):
    ['textOnContainer', '--ds-color-text-on-primary-container'],
    ['accentSurface', '--ds-color-accent-surface'],
    ['textOnAccentSurface', '--ds-color-text-on-accent-surface'],
  ];

  /**
   * Puente hacia las variables de sistema de Angular Material (M3): al aplicar
   * el color de marca se escriben tambien estas `--mat-sys-*` para que la barra
   * superior, los botones y demas superficies de Material adopten el color del
   * tenant (el cambio se nota en TODA la interfaz, no solo en acentos). El valor
   * escrito es el mismo de la Paleta_Derivada, de modo que se conserva el
   * contraste AA. Al limpiar se eliminan tambien estas sobrescrituras.
   */
  private static readonly MAPEO_TOKENS_MATERIAL: ReadonlyArray<
    readonly [keyof PaletaDerivada, string]
  > = [
    ['primary', '--mat-sys-primary'],
    ['textOnPrimary', '--mat-sys-on-primary'],
    ['primaryContainer', '--mat-sys-primary-container'],
    ['textOnContainer', '--mat-sys-on-primary-container'],
  ];

  /** Color de marca actualmente aplicado (`#RRGGBB`), o `null` si no hay ninguno. */
  private readonly colorActivoSignal = signal<string | null>(null);

  /** Color de marca vigente aplicado al documento, o `null`. */
  readonly colorActivo = computed<string | null>(() => this.colorActivoSignal());

  constructor() {
    // Reacciona a los cambios de modo de ThemeService (Req 3.4): al conmutar
    // claro/oscuro con un color activo, reaplica la variante correspondiente.
    // El effect se crea en el constructor (contexto de inyección).
    effect(() => {
      const modo = this.theme.mode();
      const color = this.colorActivoSignal();
      if (color !== null) {
        this.escribirPaleta(color, modo);
      }
    });
  }

  /**
   * Aplica un Color_Primario_Marca al documento: deriva su {@link PaletaDerivada}
   * y escribe cada campo en su Token_CSS `--ds-color-*` (Req 3.1–3.3).
   *
   * Si `colorPrimario` no es un hex válido (`#RRGGBB`), no escribe nada y no
   * lanza; el color vigente se conserva. Si no se indica `modo`, usa el modo
   * activo de {@link ThemeService}.
   *
   * @param colorPrimario Color de marca `#RRGGBB`.
   * @param modo Modo de tema a derivar; por defecto, el modo activo del tema.
   */
  aplicar(colorPrimario: string, modo?: 'light' | 'dark'): void {
    if (!esHexValido(colorPrimario)) {
      return;
    }
    const modoEfectivo = modo ?? this.theme.mode();
    this.escribirPaleta(colorPrimario, modoEfectivo);
    this.colorActivoSignal.set(colorPrimario);
  }

  /**
   * Previsualiza en vivo un Color_Primario_Marca antes de guardarlo (Req 1.5).
   *
   * Comportamiento idéntico a {@link aplicar}: deriva y escribe la paleta, y
   * registra el color como activo. Se separa por semántica de uso (vista de
   * branding) para reflejar el flujo de diseño.
   *
   * @param colorPrimario Color de marca `#RRGGBB`.
   * @param modo Modo de tema a derivar; por defecto, el modo activo del tema.
   */
  previsualizar(colorPrimario: string, modo?: 'light' | 'dark'): void {
    this.aplicar(colorPrimario, modo);
  }

  /**
   * Limpia la tematización restaurando el Tema_Corporativo (Req 3.5, 9.3).
   *
   * Elimina las sobrescrituras inline de **todos** los Token_CSS que
   * {@link aplicar} pudo escribir (los 8 del mapeo), de modo que el documento
   * vuelva a resolver los valores definidos en `_tokens.scss`. Restablece el
   * color activo a `null`. No toca tokens ajenos al color.
   */
  limpiar(): void {
    const estilo = document.documentElement.style;
    for (const [, token] of TematizacionService.MAPEO_TOKENS) {
      estilo.removeProperty(token);
    }
    for (const [, token] of TematizacionService.MAPEO_TOKENS_MATERIAL) {
      estilo.removeProperty(token);
    }
    this.colorActivoSignal.set(null);
  }

  /**
   * Escribe la {@link PaletaDerivada} de `color`/`modo` en los Token_CSS del
   * elemento raíz. Auxiliar interno compartido por {@link aplicar} y el
   * `effect` de reaplicación por cambio de modo. Asume `color` ya validado.
   *
   * @param color Color de marca `#RRGGBB` válido.
   * @param modo Modo de tema a derivar.
   */
  private escribirPaleta(color: string, modo: 'light' | 'dark'): void {
    const paleta = derivarPaleta(color, modo);
    const estilo = document.documentElement.style;
    for (const [campo, token] of TematizacionService.MAPEO_TOKENS) {
      estilo.setProperty(token, paleta[campo]);
    }
    // Propaga el color de marca a las superficies de Angular Material (toolbar,
    // botones, etc.) para que el cambio se note en toda la interfaz (Req 7).
    for (const [campo, token] of TematizacionService.MAPEO_TOKENS_MATERIAL) {
      estilo.setProperty(token, paleta[campo]);
    }
  }
}
