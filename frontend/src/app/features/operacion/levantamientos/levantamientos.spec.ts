// =============================================================================
// Pruebas de la vista de Levantamientos de Sitio (Req 15.1-15.3, 10.2)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless):
//   - Estados carga / vacio / error de la vista (StateContainer + signal fase).
//   - El DOM NO expone UUIDs crudos ni recortes de los identificadores.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom), en un solo intento.
// =============================================================================

import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';
import { LOCALE_ID } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { OperacionLevantamientos } from './levantamientos';
import { AuthService } from '../../../core/auth/auth.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

const LEV_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const SITIO_ID = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

class AuthServiceStub {
  tienePermiso(): boolean {
    return true;
  }
}

/** Levantamiento minimo (sin exponer identificadores en el DOM). */
function levantamientoDto(over: Record<string, unknown> = {}) {
  return {
    id: LEV_ID,
    sitioId: SITIO_ID,
    cotizacionId: null,
    ordenFabricacionId: null,
    mediciones: '3x2 metros',
    tipoSuperficie: 'Muro de concreto',
    condicionesElectricas: '110V disponible',
    estado: 'en_proceso',
    completadoPor: null,
    completadoEn: null,
    version: 0,
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
    ...over,
  };
}

describe('OperacionLevantamientos', () => {
  let fixture: ComponentFixture<OperacionLevantamientos>;
  let http: HttpTestingController;

  function montar(): void {
    TestBed.configureTestingModule({
      imports: [OperacionLevantamientos, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    });
    fixture = TestBed.createComponent(OperacionLevantamientos);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  /** Responde el GET del listado de levantamientos. */
  function resolverListado(levantamientos: Record<string, unknown>[]): void {
    const req = http.expectOne((r) => r.url === '/api/v1/levantamientos' && r.method === 'GET');
    req.flush({
      content: levantamientos,
      page: 0,
      size: 20,
      totalElements: levantamientos.length,
      totalPages: levantamientos.length === 0 ? 0 : 1,
    });
    fixture.detectChanges();
  }

  it('muestra el estado de carga antes de resolver los datos', () => {
    montar();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="status"]')).not.toBeNull();
    expect(host.textContent).toContain('Cargando informacion');
    resolverListado([levantamientoDto()]);
  });

  it('muestra el estado vacio cuando no hay levantamientos', () => {
    montar();
    resolverListado([]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'No hay levantamientos para el filtro seleccionado.',
    );
  });

  it('muestra el estado de error cuando el listado falla', () => {
    montar();
    http
      .expectOne((r) => r.url === '/api/v1/levantamientos' && r.method === 'GET')
      .flush({ detail: 'Error interno' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect((fixture.componentInstance as unknown as { fase(): string }).fase()).toBe('error');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Reintentar');
  });

  it('no expone UUIDs crudos ni recortes en el listado', () => {
    montar();
    resolverListado([levantamientoDto()]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Muro de concreto');
    expect(texto).not.toContain(LEV_ID);
    expect(texto).not.toContain(SITIO_ID);
    expect(texto).not.toContain(LEV_ID.slice(0, 8));
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    montar();
    resolverListado([levantamientoDto()]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
