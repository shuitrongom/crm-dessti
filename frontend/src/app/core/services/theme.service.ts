import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

/**
 * Servicio de tema del Sistema de Diseño (Req 53.5).
 *
 * Conmuta el modo claro/oscuro fijando el atributo `data-theme` en el elemento
 * <html>, sobre el que reaccionan las CSS custom properties de `_tokens.scss`
 * y el `color-scheme` del tema de Angular Material.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly modeSignal = signal<ThemeMode>('light');

  /** Modo de tema activo (solo lectura). */
  readonly mode = this.modeSignal.asReadonly();

  /** Establece el modo de tema y lo aplica al documento. */
  setMode(mode: ThemeMode): void {
    this.modeSignal.set(mode);
    document.documentElement.setAttribute('data-theme', mode);
  }

  /** Alterna entre modo claro y oscuro. */
  toggle(): void {
    this.setMode(this.modeSignal() === 'light' ? 'dark' : 'light');
  }
}
