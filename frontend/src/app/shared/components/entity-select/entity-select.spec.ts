// =============================================================================
// Pruebas del componente EntitySelect: seleccion por nombre que enlaza el UUID
// (Req 5, 6, 59, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless, con los
// temporizadores falsos de Vitest para el debounce del autocompletado):
//   - Al elegir una entidad, el control del formulario recibe SU id (UUID) y el
//     Usuario nunca teclea el identificador.
//   - Cuando es obligatorio y no hay seleccion, el componente reporta el error de
//     validacion `seleccionRequerida` (bloquea el envio del formulario).
//   - "Limpiar" borra la seleccion y devuelve el control a vacio.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { Component, signal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { EntitySelect } from './entity-select';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** Entidad de prueba minima. */
interface EntidadPrueba {
  id: string;
  nombre: string;
  detalle: string;
}

/** Pagina de una sola entidad para el buscador de prueba. */
function pagina(items: EntidadPrueba[]): PaginaResponse<EntidadPrueba> {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

const ENTIDAD: EntidadPrueba = { id: '11111111-2222-3333-4444-555555555555', nombre: 'Acme', detalle: 'RFC123' };

/** Host que enlaza el EntitySelect a un control reactivo obligatorio. */
@Component({
  imports: [ReactiveFormsModule, EntitySelect],
  template: `
    <app-entity-select
      [formControl]="control"
      etiqueta="Cliente"
      [obligatorio]="true"
      [buscador]="buscador"
      [etiquetaDe]="etiquetaDe"
      [detalleDe]="detalleDe"
    />
  `,
})
class HostObligatorio {
  readonly control = new FormControl<string>('', { nonNullable: true, validators: [Validators.required] });
  readonly llamadas = signal<string[]>([]);
  readonly buscador = (filtro: string) => {
    this.llamadas.update((l) => [...l, filtro]);
    return of(pagina([ENTIDAD]));
  };
  readonly etiquetaDe = (e: EntidadPrueba) => e.nombre;
  readonly detalleDe = (e: EntidadPrueba) => e.detalle;
}

/** Superficie protegida del componente que las pruebas necesitan accionar. */
interface EntitySelectProbe {
  alEscribir(v: string): void;
  alSeleccionar(evento: { option: { value: EntidadPrueba } }): void;
  limpiar(): void;
  marcarTocado(): void;
}

describe('EntitySelect', () => {
  let fixture: ComponentFixture<HostObligatorio>;
  let host: HostObligatorio;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [HostObligatorio, NoopAnimationsModule],
    }).compileComponents();
    fixture = TestBed.createComponent(HostObligatorio);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  /** Obtiene la instancia del EntitySelect renderizado por el host. */
  function probe(): EntitySelectProbe {
    const debug = fixture.debugElement.query((n) => n.name === 'app-entity-select');
    return debug.componentInstance as unknown as EntitySelectProbe;
  }

  it('al elegir una entidad, enlaza SU id (UUID) al control del formulario', () => {
    // Antes de elegir, el control esta vacio e invalido (obligatorio).
    expect(host.control.value).toBe('');
    expect(host.control.valid).toBe(false);

    // El Usuario escribe un nombre; tras el debounce se consulta el buscador.
    probe().alEscribir('acm');
    // toObservable emite el valor del signal via un effect en la deteccion de
    // cambios; hay que propagarlo antes de vencer el debounce.
    fixture.detectChanges();
    vi.advanceTimersByTime(300);
    fixture.detectChanges();
    expect(host.llamadas()).toContain('acm');

    // Elige la opcion: el control recibe el UUID, no el texto tecleado.
    probe().alSeleccionar({ option: { value: ENTIDAD } });
    fixture.detectChanges();

    expect(host.control.value).toBe(ENTIDAD.id);
    expect(host.control.valid).toBe(true);
  });

  it('bloquea el envio cuando es obligatorio y solo se teclea sin elegir', () => {
    probe().alEscribir('texto libre sin elegir');
    probe().marcarTocado();
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    // El control sigue vacio: escribir no equivale a seleccionar.
    expect(host.control.value).toBe('');
    expect(host.control.valid).toBe(false);
  });

  it('limpiar la seleccion devuelve el control a vacio', () => {
    probe().alSeleccionar({ option: { value: ENTIDAD } });
    fixture.detectChanges();
    expect(host.control.value).toBe(ENTIDAD.id);

    probe().limpiar();
    fixture.detectChanges();
    expect(host.control.value).toBe('');
    expect(host.control.valid).toBe(false);
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    vi.useRealTimers();
    await esperarSinViolaciones(fixture);
  });
});
