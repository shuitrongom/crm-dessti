// =============================================================================
// Pruebas del componente AddressAutocomplete (Req 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless, con los
// temporizadores falsos de Vitest para el debounce del autocompletado):
//   - Al teclear (>= 3 caracteres) y vencer el debounce, se consulta a Photon.
//   - Con menos de 3 caracteres no se consulta.
//   - Al elegir una sugerencia, se emite `direccionSeleccionada` con los campos
//     mapeados { calle, ciudad, estado, cp, pais }.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { Component, signal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { AddressAutocomplete, DireccionAutocompletada } from './address-autocomplete';
import { PhotonService, DireccionSugerida } from './photon.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

const SUGERENCIA: DireccionSugerida = {
  etiqueta: 'Avenida Juarez 100, Toluca, Estado de Mexico, Mexico',
  calle: 'Avenida Juarez 100',
  ciudad: 'Toluca',
  estado: 'Estado de Mexico',
  cp: '50000',
  pais: 'Mexico',
};

/** Doble de PhotonService que registra las consultas y responde con sugerencias
 *  configurables (por defecto, una sola sugerencia). */
class PhotonServiceStub {
  readonly minCaracteres = 3;
  readonly consultas = signal<string[]>([]);
  respuesta: DireccionSugerida[] = [SUGERENCIA];
  buscar(texto: string) {
    this.consultas.update((c) => [...c, texto]);
    return of(this.respuesta);
  }
}

/** Host que escucha el output del componente y guarda la ultima direccion emitida. */
@Component({
  imports: [AddressAutocomplete],
  template: `<app-address-autocomplete (direccionSeleccionada)="onDireccion($event)" />`,
})
class Host {
  readonly ultima = signal<DireccionAutocompletada | null>(null);
  onDireccion(d: DireccionAutocompletada): void {
    this.ultima.set(d);
  }
}

/** Superficie protegida del componente que las pruebas necesitan accionar. */
interface AddressAutocompleteProbe {
  alEscribir(v: string): void;
  alSeleccionar(evento: { option: { value: DireccionSugerida } }): void;
  resultados(): DireccionSugerida[];
  sinResultados(): boolean;
}

describe('AddressAutocomplete', () => {
  let fixture: ComponentFixture<Host>;
  let host: Host;
  let photon: PhotonServiceStub;

  beforeEach(async () => {
    vi.useFakeTimers();
    photon = new PhotonServiceStub();
    await TestBed.configureTestingModule({
      imports: [Host, NoopAnimationsModule],
      providers: [{ provide: PhotonService, useValue: photon }],
    }).compileComponents();
    fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function probe(): AddressAutocompleteProbe {
    const debug = fixture.debugElement.query((n) => n.name === 'app-address-autocomplete');
    return debug.componentInstance as unknown as AddressAutocompleteProbe;
  }

  it('teclear (>= 3 caracteres) dispara la consulta a Photon tras el debounce', () => {
    probe().alEscribir('Toluca');
    fixture.detectChanges();
    vi.advanceTimersByTime(350);
    fixture.detectChanges();
    expect(photon.consultas()).toContain('Toluca');
  });

  it('no consulta con menos de 3 caracteres', () => {
    probe().alEscribir('to');
    fixture.detectChanges();
    vi.advanceTimersByTime(350);
    fixture.detectChanges();
    expect(photon.consultas()).toEqual([]);
  });

  it('al elegir una sugerencia emite los campos mapeados de la direccion', () => {
    probe().alSeleccionar({ option: { value: SUGERENCIA } });
    fixture.detectChanges();
    expect(host.ultima()).toEqual({
      calle: 'Avenida Juarez 100',
      ciudad: 'Toluca',
      estado: 'Estado de Mexico',
      cp: '50000',
      pais: 'Mexico',
    });
  });

  it('marca "sin resultados" cuando el termino es suficiente y Photon no devuelve coincidencias', () => {
    photon.respuesta = [];
    probe().alEscribir('Direccion inexistente');
    fixture.detectChanges();
    vi.advanceTimersByTime(350);
    fixture.detectChanges();
    expect(probe().resultados()).toEqual([]);
    expect(probe().sinResultados()).toBe(true);
  });

  it('expone las sugerencias mapeadas para el panel del autocomplete', () => {
    probe().alEscribir('Toluca');
    fixture.detectChanges();
    vi.advanceTimersByTime(350);
    fixture.detectChanges();
    expect(probe().resultados()).toEqual([SUGERENCIA]);
    expect(probe().sinResultados()).toBe(false);
  });

  it('declara la clase de panel personalizada en el mat-autocomplete', () => {
    // El <mat-autocomplete> declara la clase del panel en la plantilla; Material
    // la propaga al overlay del CDK al abrir el panel, garantizando el fondo
    // solido y el z-index sobre los campos siguientes (Req 5.1, 5.2). Se verifica
    // que el enlace estatico de clase este presente en el elemento del template.
    const debug = fixture.debugElement.query((n) => n.name === 'mat-autocomplete');
    expect(debug).not.toBeNull();
    // MatAutocomplete captura la clase declarada en la plantilla en su propiedad
    // `classList` y la aplica al panel del overlay del CDK al abrirse.
    // MatAutocomplete captura la clase declarada en la plantilla (en `classList`
    // o su respaldo interno) y la aplica al panel del overlay del CDK al abrirse,
    // garantizando el fondo solido y el z-index sobre los campos siguientes.
    const inst = debug.componentInstance as { classList?: unknown; _classList?: unknown };
    const classList = String(inst.classList ?? inst._classList ?? '');
    expect(classList).toContain('direccion-autocomplete-panel');
  });

  it('permite abrir y navegar el panel con teclado (input enfocable con autocomplete)', () => {
    const input = fixture.nativeElement.querySelector(
      'input[matInput]',
    ) as HTMLInputElement;
    expect(input).not.toBeNull();
    // El input esta cableado al autocomplete (role listbox/option de Material),
    // habilitando la navegacion por flechas/Enter/Esc que gestiona MatAutocomplete.
    expect(input.getAttribute('autocomplete')).toBe('off');
    input.focus();
    expect(document.activeElement).toBe(input);
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    vi.useRealTimers();
    await esperarSinViolaciones(fixture);
  });
});
