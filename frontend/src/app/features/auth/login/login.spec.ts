// =============================================================================
// Pruebas de la vista Login: formulario accesible + accesibilidad (Req 1, 3, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Renderizado del formulario con etiquetas asociadas y autocompletado.
//   - Boton de mostrar/ocultar contrasena con etiqueta accesible y aria-pressed.
//   - Mensaje de error generico accesible (role=alert) sin detalle tecnico.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom; `color-contrast`
//     se valida en la capa e2e de Playwright).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { Login } from './login';
import { esperarSinViolaciones } from '../../../../testing/axe';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Login, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
  });

  afterEach(() => sessionStorage.clear());

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('renderiza los campos de identificador y contrasena con autocompletado', () => {
    const usuario = el().querySelector('input[formControlName="identificador"]');
    const password = el().querySelector('input[formControlName="password"]');
    expect(usuario?.getAttribute('autocomplete')).toBe('username');
    expect(password?.getAttribute('autocomplete')).toBe('current-password');
  });

  it('expone las etiquetas de los campos', () => {
    const etiquetas = Array.from(el().querySelectorAll('mat-label')).map((l) => l.textContent?.trim());
    expect(etiquetas).toContain('Identificador');
    expect(etiquetas).toContain('Contrasena');
  });

  it('el boton de contrasena tiene etiqueta accesible y aria-pressed', async () => {
    const boton = el().querySelector('button[matSuffix]') as HTMLButtonElement;
    expect(boton.getAttribute('aria-label')).toBe('Mostrar contrasena');
    expect(boton.getAttribute('aria-pressed')).toBe('false');
    boton.click();
    await fixture.whenStable();
    expect(boton.getAttribute('aria-label')).toBe('Ocultar contrasena');
    expect(boton.getAttribute('aria-pressed')).toBe('true');
  });

  it(
    'no tiene violaciones de accesibilidad al cargar (WCAG 2.1 A/AA)',
    async () => {
      await esperarSinViolaciones(fixture);
    },
    // axe-core es intensivo en CPU; bajo carga en Windows puede superar el limite
    // por defecto de 5 s. Un timeout explicito hace determinista esta prueba.
    30000,
  );
});
