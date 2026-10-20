// =============================================================================
// Directiva appRequierePermiso — indica al usuario cuándo una acción necesita
// autorización (permiso) que NO posee, en lugar de ocultarla en silencio.
// -----------------------------------------------------------------------------
// Patrón de experiencia (aplica a todo el sistema): cuando una acción existe pero
// el usuario carece del permiso atómico requerido, en vez de esconderla, la
// mostramos DESHABILITADA con una pista accesible (tooltip + candado opcional) que
// explica qué permiso hace falta. Así el usuario entiende que la acción es posible
// pero requiere autorización de su administrador, en vez de "desaparecer".
//
// Uso:
//   <button matButton
//           appRequierePermiso recurso="requisicion_compra" operacion="aprobar"
//           etiquetaPermiso="aprobar requisiciones"
//           (click)="aprobar()">Aprobar</button>
//
// - Si el usuario TIENE el permiso: el control queda intacto (interactivo).
// - Si NO lo tiene: se marca `disabled`, se le pone `aria-disabled`, se bloquea el
//   click (captura) y se añade un tooltip "Requiere permiso: <etiqueta>. Solicítalo
//   a tu administrador." El host puede además reaccionar al estado con la clase CSS
//   `requiere-permiso--bloqueado` que la directiva aplica.
//
// A diferencia de ocultar con @if, este patrón comunica la regla de negocio.
// =============================================================================

import {
  Directive,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  Renderer2,
} from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';

import { AuthService } from '../../core/auth/auth.service';

@Directive({
  selector: '[appRequierePermiso]',
  hostDirectives: [
    {
      directive: MatTooltip,
      inputs: ['matTooltipPosition'],
    },
  ],
})
export class RequierePermiso {
  private readonly auth = inject(AuthService);
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly renderer = inject(Renderer2);
  private readonly tooltip = inject(MatTooltip);

  /** Recurso del permiso atómico requerido (por ejemplo "requisicion_compra"). */
  readonly recurso = input.required<string>({ alias: 'recurso' });
  /** Operación del permiso atómico requerido (por ejemplo "aprobar"). */
  readonly operacion = input.required<string>({ alias: 'operacion' });
  /**
   * Etiqueta legible del permiso para el mensaje (por ejemplo "aprobar
   * requisiciones"). Si se omite, se usa "<operacion> <recurso>".
   */
  readonly etiquetaPermiso = input<string>('', { alias: 'etiquetaPermiso' });

  /** `true` cuando el usuario NO tiene el permiso requerido. */
  private readonly bloqueado = computed(
    () => !this.auth.tienePermiso(this.recurso(), this.operacion()),
  );

  constructor() {
    // Bloquea el click cuando falta el permiso (defensa en UI; el backend reimpone).
    this.el.nativeElement.addEventListener(
      'click',
      (evento: Event) => {
        if (this.bloqueado()) {
          evento.preventDefault();
          evento.stopImmediatePropagation();
        }
      },
      true,
    );

    effect(() => {
      const bloqueado = this.bloqueado();
      const nodo = this.el.nativeElement;
      if (bloqueado) {
        const etiqueta = this.etiquetaPermiso() || `${this.operacion()} ${this.recurso()}`;
        // El tooltip DEBE poder mostrarse, por eso NO se pone `disabled` nativo
        // (los botones deshabilitados no emiten eventos de puntero y ocultarían la
        // pista). En su lugar: aria-disabled + clase visual atenuada + bloqueo del
        // click por captura (arriba). Así el usuario ve por qué no puede y qué le
        // falta. El backend reimpone la autorización de todos modos.
        this.tooltip.message = `Requiere permiso: ${etiqueta}. Solicítalo a tu administrador.`;
        this.tooltip.disabled = false;
        this.renderer.setAttribute(nodo, 'aria-disabled', 'true');
        this.renderer.addClass(nodo, 'requiere-permiso--bloqueado');
      } else {
        this.tooltip.disabled = true;
        this.renderer.removeAttribute(nodo, 'aria-disabled');
        this.renderer.removeClass(nodo, 'requiere-permiso--bloqueado');
      }
    });
  }
}
