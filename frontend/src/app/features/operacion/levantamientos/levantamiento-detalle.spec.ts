// =============================================================================
// Pruebas del detalle de Levantamiento de Sitio (Req 15.1-15.3, 10.2, 12)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless):
//   - Estados carga / ok (con datos) / error del detalle (StateContainer + fase).
//   - El DOM NO expone UUIDs crudos ni recortes: Sitio resuelto por NOMBRE.
//   - Estado vacio de la galeria cuando el levantamiento no tiene fotos.
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

import { OperacionLevantamientoDetalle } from './levantamiento-detalle';
import { AuthService } from '../../../core/auth/auth.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

const LEV_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const SITIO_ID = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const PROYECTO_ID = 'dddddddd-dddd-dddd-dddd-dddddddddddd';

class AuthServiceStub {
  tienePermiso(): boolean {
    return true;
  }
}

function paginaVacia() {
  return { content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 };
}

/** Proyecto con un Sitio (para que NombresOperacionService resuelva el nombre). */
function proyectoConSitio() {
  return {
    id: PROYECTO_ID,
    clienteId: 'cli-1',
    nombre: 'Proyecto centro',
    estadoConsolidado: null,
    sitios: [
      {
        sitio: {
          id: SITIO_ID,
          proyectoId: PROYECTO_ID,
          nombre: 'Sucursal Centro',
          direccion: 'Av. Reforma 100',
          version: 0,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
        },
        tieneLevantamientoCompletado: false,
        tienePermisoAprobado: false,
        tieneOrdenFabricacionTerminada: false,
        tieneInstalacionCompletada: false,
      },
    ],
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

function levantamientoDetalle(over: Record<string, unknown> = {}) {
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
    fotos: [],
    version: 0,
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
    ...over,
  };
}

describe('OperacionLevantamientoDetalle', () => {
  let fixture: ComponentFixture<OperacionLevantamientoDetalle>;
  let http: HttpTestingController;

  function montar(): void {
    TestBed.configureTestingModule({
      imports: [OperacionLevantamientoDetalle, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    });
    fixture = TestBed.createComponent(OperacionLevantamientoDetalle);
    fixture.componentRef.setInput('id', LEV_ID);
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

  /** Resuelve los 5 GET de catalogos de NombresOperacionService. */
  function resolverCatalogos(): void {
    http.expectOne((r) => r.url === '/api/v1/clientes' && r.method === 'GET').flush(paginaVacia());
    http
      .expectOne((r) => r.url === '/api/v1/cotizaciones' && r.method === 'GET')
      .flush(paginaVacia());
    http
      .expectOne((r) => r.url === '/api/v1/ordenes-fabricacion' && r.method === 'GET')
      .flush(paginaVacia());
    http.expectOne((r) => r.url === '/api/v1/proyectos' && r.method === 'GET').flush({
      content: [proyectoConSitio()],
      page: 0,
      size: 200,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne((r) => r.url === '/api/v1/materiales' && r.method === 'GET').flush(paginaVacia());
  }

  /** Responde el GET del detalle del levantamiento. */
  function resolverDetalle(detalle: Record<string, unknown>): void {
    http
      .expectOne((r) => r.url === `/api/v1/levantamientos/${LEV_ID}` && r.method === 'GET')
      .flush(detalle);
    fixture.detectChanges();
  }

  it('muestra el estado de carga antes de resolver los datos', () => {
    montar();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="status"]')).not.toBeNull();
    expect(host.textContent).toContain('Cargando informacion');
    resolverCatalogos();
    resolverDetalle(levantamientoDetalle());
  });

  it('muestra el estado de error cuando el detalle falla', () => {
    montar();
    resolverCatalogos();
    http
      .expectOne((r) => r.url === `/api/v1/levantamientos/${LEV_ID}`)
      .flush({ detail: 'No encontrado' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect((fixture.componentInstance as unknown as { fase(): string }).fase()).toBe('error');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Reintentar');
  });

  it('resuelve el Sitio por nombre sin exponer UUIDs ni recortes', () => {
    montar();
    resolverCatalogos();
    resolverDetalle(levantamientoDetalle());
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Sucursal Centro');
    expect(texto).not.toContain(LEV_ID);
    expect(texto).not.toContain(SITIO_ID);
    expect(texto).not.toContain(SITIO_ID.slice(0, 8));
  });

  it('muestra el estado vacio de la galeria cuando no hay fotos', () => {
    montar();
    resolverCatalogos();
    resolverDetalle(levantamientoDetalle({ fotos: [] }));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Este levantamiento no tiene fotos registradas.',
    );
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    montar();
    resolverCatalogos();
    resolverDetalle(levantamientoDetalle());
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
