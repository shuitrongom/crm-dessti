// =============================================================================
// Componente SelectableCard — tarjeta seleccionable animada (Sistema de Diseño)
// -----------------------------------------------------------------------------
// Tarjeta interactiva reutilizable para selección visual (p. ej. módulos de un
// plan, opciones de una lista). Sustituye a los checkboxes "planos" por una
// superficie con estado seleccionado destacado, micro-animación de selección y
// contenido proyectado (título, precio, etc.).
//
// Accesibilidad (Req 57): actúa como checkbox (role="checkbox",
// aria-checked), es enfocable y responde a Espacio/Enter. Cuando está
// deshabilitada no emite cambios y expone aria-disabled. La animación respeta
// prefers-reduced-motion (neutralizada globalmente en styles.scss).
// =============================================================================

import { Component, EventEmitter, HostBinding, HostListener, Input, Output } from '@angular/core';

@Component({
  selector: 'app-selectable-card',
  standalone: true,
  template: `
    <div class="ds-scard__check" aria-hidden="true">
      @if (seleccionado) {
        <span class="ds-scard__check-icon">&#10003;</span>
      }
    </div>
    <div class="ds-scard__content">
      <ng-content />
    </div>
  `,
  styleUrl: './selectable-card.scss',
})
export class SelectableCard {
  /** Estado seleccionado de la tarjeta. */
  @Input({ transform: (v: unknown) => v === true || v === '' }) seleccionado = false;

  /** Deshabilita la interacción (no emite cambios). */
  @Input({ transform: (v: unknown) => v === true || v === '' }) deshabilitado = false;

  /** Emite el nuevo estado seleccionado al alternar. */
  @Output() readonly seleccionadoChange = new EventEmitter<boolean>();

  @HostBinding('class.ds-scard') readonly base = true;

  @HostBinding('class.ds-scard--seleccionado')
  get claseSeleccionado(): boolean {
    return this.seleccionado;
  }

  @HostBinding('class.ds-scard--deshabilitado')
  get claseDeshabilitado(): boolean {
    return this.deshabilitado;
  }

  @HostBinding('attr.role') readonly role = 'checkbox';

  @HostBinding('attr.tabindex')
  get tabindex(): string {
    return this.deshabilitado ? '-1' : '0';
  }

  @HostBinding('attr.aria-checked')
  get ariaChecked(): string {
    return String(this.seleccionado);
  }

  @HostBinding('attr.aria-disabled')
  get ariaDisabled(): string | null {
    return this.deshabilitado ? 'true' : null;
  }

  @HostListener('click')
  alClic(): void {
    this.alternar();
  }

  @HostListener('keydown', ['$event'])
  alTecla(evento: KeyboardEvent): void {
    if (evento.key === ' ' || evento.key === 'Enter') {
      evento.preventDefault();
      this.alternar();
    }
  }

  private alternar(): void {
    if (this.deshabilitado) {
      return;
    }
    this.seleccionado = !this.seleccionado;
    this.seleccionadoChange.emit(this.seleccionado);
  }
}
