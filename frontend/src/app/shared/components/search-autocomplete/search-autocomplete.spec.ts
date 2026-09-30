// =============================================================================
// Pruebas del buscador premium con sugerencias (SearchAutocomplete)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista (fake timers para el debounce, sin red real):
//   - Emite `terminoChange` con el texto tecleado tras el debounce.
//   - Al enfocar con `precargar`, consulta sugerencias con término vacío.
//   - Al elegir una sugerencia, emite `seleccion` con la entidad.
//   - Resalta la coincidencia del término en la etiqueta (segmentos).
//   - No hay violaciones de accesibilidad (WCAG 2.1 A/AA).
// =============================================================================

import { vi } from 'vitest';
import { Component, signal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { SearchAutocomplete } from './search-autocomplete';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { esperarSinViolaciones } from '../../../../testing/axe';

interface EntidadPrueba {
  id: string;
  nombre: string;
  rfc: string;
}

function pagina(items: EntidadPrueba[]): PaginaResponse<EntidadPrueba> {
  return { content: items, page: 0, size: 10, totalElements: items.length, totalPages: 1 };
}

const ACME: EntidadPrueba = { id: '1', nombre: 'Acme S.A.', rfc: 'ABC010101AB1' };

/** Host que enlaza el buscador y captura sus eventos. */
@Component({
  imports: [SearchAutocomplete],
  template: `
    <app-search-autocomplete
      etiqueta="Buscar"
      [precargar]="true"
      [buscador]="buscador"
      [etiquetaDe]="etiquetaDe"
      [detalleDe]="detalleDe"
      (terminoChange)="terminos.set([...terminos(), $event])"
      (seleccion)="elegido.set($event)"
    />
  `,
})
class Host {
  readonly terminos = signal<string[]>([]);
  readonly elegido = signal<EntidadPrueba | null>(null);
  readonly llamadas = signal<string[]>([]);
  readonly buscador = (filtro: string) => {
    this.llamadas.update((l) => [...l, filtro]);
    return of(pagina([ACME]));
  };
  readonly etiquetaDe = (e: EntidadPrueba) => e.nombre;
  readonly detalleDe = (e: EntidadPrueba) => e.rfc;
}

interface Probe {
  alEscribir(v: string): void;
  alEnfocar(): void;
  alSeleccionar(evento: { option: { value: EntidadPrueba } }): void;
  segmentos(e: EntidadPrueba): { texto: string; match: boolean }[];
}

describe('SearchAutocomplete', () => {
  let fixture: ComponentFixture<Host>;
  let host: Host;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [Host, NoopAnimationsModule],
    }).compileComponents();
    fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function probe(): Probe {
    const debug = fixture.debugElement.query((n) => n.name === 'app-search-autocomplete');
    return debug.componentInstance as unknown as Probe;
  }

  it('emite terminoChange con el texto tecleado tras el debounce', () => {
    probe().alEscribir('acme');
    fixture.detectChanges();
    vi.advanceTimersByTime(350);
    fixture.detectChanges();

    expect(host.terminos()).toContain('acme');
  });

  it('al enfocar con precargar consulta sugerencias con término vacío', () => {
    probe().alEnfocar();
    fixture.detectChanges();
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect(host.llamadas()).toContain('');
  });

  it('al elegir una sugerencia emite la entidad seleccionada', () => {
    probe().alSeleccionar({ option: { value: ACME } });
    fixture.detectChanges();

    expect(host.elegido()?.id).toBe('1');
  });

  it('resalta la coincidencia del término en la etiqueta', () => {
    probe().alEscribir('cme');
    fixture.detectChanges();

    const segs = probe().segmentos(ACME);
    // "Acme S.A." con término "cme" => [A][cme][ S.A.] con el del medio resaltado.
    expect(segs.some((s) => s.match && s.texto.toLowerCase() === 'cme')).toBe(true);
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    vi.useRealTimers();
    await esperarSinViolaciones(fixture);
  });
});
