// =============================================================================
// Pruebas del ChipEstado: mapeo semantico variante -> data-variante (Req 7.3, 7.4)
// -----------------------------------------------------------------------------
// El color real del chip proviene del SCSS a traves del atributo `data-variante`
// (que selecciona el Token_CSS semantico correspondiente). En jsdom no hay CSS
// aplicado, de modo que la propiedad verificable de forma determinista es el
// mapeo variante -> atributo `data-variante` sobre `<span class="chip-estado">`.
// Ademas, la `etiqueta` es el portador de significado (WCAG 2.1, uso del color):
// el texto renderizado debe coincidir siempre con la etiqueta indicada.
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';

import { ChipEstado, type VarianteChipEstado } from './chip-estado';

/** Conjunto finito de variantes semanticas admitidas por el chip. */
const VARIANTES: readonly VarianteChipEstado[] = ['exito', 'advertencia', 'error', 'info', 'neutro'];

describe('ChipEstado (mapeo semantico)', () => {
  let fixture: ComponentFixture<ChipEstado>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChipEstado],
    }).compileComponents();
    fixture = TestBed.createComponent(ChipEstado);
  });

  /** Localiza el <span class="chip-estado"> renderizado. */
  function chip(): HTMLElement {
    return fixture.nativeElement.querySelector('.chip-estado') as HTMLElement;
  }

  /** Monta el chip con la variante y etiqueta dadas y refresca la vista. */
  function montar(variante: VarianteChipEstado, etiqueta: string): void {
    fixture.componentRef.setInput('variante', variante);
    fixture.componentRef.setInput('etiqueta', etiqueta);
    fixture.detectChanges();
  }

  // Feature: tematizacion-empresa-enterprise, Property 9: para CADA variante del
  // conjunto finito {exito, advertencia, error, info, neutro}, el chip renderizado
  // expone `data-variante` igual a la variante (token semantico que consume el SCSS)
  // y su texto renderizado es la etiqueta. Recorrido exhaustivo del conjunto finito.
  // Validates: Requirements 7.3, 7.4
  it('P9: mapea cada variante a su atributo data-variante y muestra la etiqueta', () => {
    for (const variante of VARIANTES) {
      const etiqueta = `Estado ${variante}`;
      montar(variante, etiqueta);

      const span = chip();
      expect(span).toBeTruthy();
      expect(span.getAttribute('data-variante')).toBe(variante);
      expect(span.textContent?.trim()).toBe(etiqueta);
    }
  });

  it('muestra la etiqueta tal cual (portador de significado, WCAG)', () => {
    montar('error', 'Pago rechazado');
    expect(chip().textContent?.trim()).toBe('Pago rechazado');
  });

  it('al cambiar la variante actualiza el atributo data-variante', () => {
    montar('neutro', 'Pendiente');
    expect(chip().getAttribute('data-variante')).toBe('neutro');

    fixture.componentRef.setInput('variante', 'exito');
    fixture.detectChanges();
    expect(chip().getAttribute('data-variante')).toBe('exito');
  });
});
