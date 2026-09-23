// =============================================================================
// Pruebas de StateContainer: comportamiento + accesibilidad (Req 54, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona:
//   - Renderizado de cada fase (cargando / vacio / error) con sus roles ARIA.
//   - Emision del evento `reintentar` al pulsar el boton en la fase de error.
//   - Ausencia de violaciones de accesibilidad WCAG 2.1 A/AA (axe-core, jsdom;
//     `color-contrast` se valida en la capa e2e de Playwright).
// =============================================================================

import { Component, signal } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';

import { StateContainer } from './state-container';
import { FaseSolicitud } from '../../models/estado-solicitud';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** Host de prueba que proyecta contenido y captura el evento `reintentar`. */
@Component({
  imports: [StateContainer],
  template: `
    <app-state-container
      [fase]="fase()"
      [mensajeError]="mensajeError()"
      [mensajeVacio]="mensajeVacio()"
      (reintentar)="reintentos = reintentos + 1"
    >
      <p>Contenido con datos</p>
    </app-state-container>
  `,
})
class HostState {
  readonly fase = signal<FaseSolicitud>('cargando');
  readonly mensajeError = signal<string | undefined>('No se pudo cargar la informacion.');
  readonly mensajeVacio = signal('No hay registros.');
  reintentos = 0;
}

describe('StateContainer', () => {
  let fixture: ComponentFixture<HostState>;
  let host: HostState;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostState] }).compileComponents();
    fixture = TestBed.createComponent(HostState);
    host = fixture.componentInstance;
    await fixture.whenStable();
  });

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  describe('renderizado por fase', () => {
    it('muestra el spinner y un rol status en la fase de carga', async () => {
      host.fase.set('cargando');
      await fixture.whenStable();
      const status = (fixture.nativeElement as HTMLElement).querySelector('[role="status"]');
      expect(status).toBeTruthy();
      expect((fixture.nativeElement as HTMLElement).querySelector('mat-spinner')).toBeTruthy();
    });

    it('muestra el mensaje de vacio en la fase vacio', async () => {
      host.fase.set('vacio');
      await fixture.whenStable();
      expect(texto()).toContain('No hay registros.');
    });

    it('muestra el mensaje de error con rol alert en la fase error', async () => {
      host.fase.set('error');
      await fixture.whenStable();
      const alerta = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
      expect(alerta).toBeTruthy();
      expect(texto()).toContain('No se pudo cargar la informacion.');
    });

    it('proyecta el contenido cuando la fase es ok', async () => {
      host.fase.set('ok');
      await fixture.whenStable();
      expect(texto()).toContain('Contenido con datos');
    });
  });

  describe('interaccion', () => {
    it('emite reintentar al pulsar el boton en la fase de error', async () => {
      host.fase.set('error');
      await fixture.whenStable();
      const boton = (fixture.nativeElement as HTMLElement).querySelector(
        'button',
      ) as HTMLButtonElement;
      boton.click();
      await fixture.whenStable();
      expect(host.reintentos).toBe(1);
    });
  });

  describe('accesibilidad (axe-core, WCAG 2.1 A/AA)', () => {
    it('no tiene violaciones en la fase de carga', async () => {
      host.fase.set('cargando');
      await fixture.whenStable();
      await esperarSinViolaciones(fixture);
    });

    it('no tiene violaciones en la fase vacio', async () => {
      host.fase.set('vacio');
      await fixture.whenStable();
      await esperarSinViolaciones(fixture);
    });

    it('no tiene violaciones en la fase de error', async () => {
      host.fase.set('error');
      await fixture.whenStable();
      await esperarSinViolaciones(fixture);
    });
  });
});
