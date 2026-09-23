// =============================================================================
// Pruebas del detalle de Permiso de Instalacion (Req 15.1-15.3, 10.2, 17)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless):
//   - Estados carga / ok (con datos) / error del detalle (StateContainer + fase).
//   - El DOM NO expone UUIDs crudos ni recortes: el Sitio se resuelve por NOMBRE.
//   - Un permiso ya decidido muestra la nota (sin acciones) — estado "no accionable".
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

import { OperacionPermisoDetalle } from './permiso-detalle';
import { AuthService } from '../../../core/auth/auth.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

const PERMISO_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
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

function permisoDto(over: Record<string, unknown> = {}) {
  return {
    id: PERMISO_ID,
    sitioId: SITIO_ID,
    tipo: 'municipal',
    fechaVencimiento: '2026-12-31',
    estado: 'solicitado',
    decididoPor: null,
    decididoEn: null,
    version: 0,
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
    ...over,
  };
}

describe('OperacionPermisoDetalle', () => {
  let fixture: ComponentFixture<OperacionPermisoDetalle>;
  let http: HttpTestingController;

  function montar(): void {
    TestBed.configureTestingModule({
      imports: [OperacionPermisoDetalle, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    });
    fixture = TestBed.createComponent(OperacionPermisoDetalle);
    fixture.componentRef.setInput('id', PERMISO_ID);
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

  /** Responde el GET del detalle del permiso. */
  function resolverDetalle(detalle: Record<string, unknown>): void {
    http
      .expectOne((r) => r.url === `/api/v1/permisos-instalacion/${PERMISO_ID}` && r.method === 'GET')
      .flush(detalle);
    fixture.detectChanges();
  }

  it('muestra el estado de carga antes de resolver los datos', () => {
    montar();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="status"]')).not.toBeNull();
    expect(host.textContent).toContain('Cargando informacion');
    resolverCatalogos();
    resolverDetalle(permisoDto());
  });

  it('muestra el estado de error cuando el detalle falla', () => {
    montar();
    resolverCatalogos();
    http
      .expectOne((r) => r.url === `/api/v1/permisos-instalacion/${PERMISO_ID}`)
      .flush({ detail: 'No encontrado' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect((fixture.componentInstance as unknown as { fase(): string }).fase()).toBe('error');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Reintentar');
  });

  it('resuelve el Sitio por nombre sin exponer UUIDs ni recortes', () => {
    montar();
    resolverCatalogos();
    resolverDetalle(permisoDto());
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Sucursal Centro');
    expect(texto).toContain('Municipal');
    expect(texto).not.toContain(PERMISO_ID);
    expect(texto).not.toContain(SITIO_ID);
    expect(texto).not.toContain(SITIO_ID.slice(0, 8));
  });

  it('un permiso ya decidido muestra la nota sin acciones (estado no accionable)', () => {
    montar();
    resolverCatalogos();
    resolverDetalle(permisoDto({ estado: 'aprobado', decididoEn: '2026-04-01T00:00:00Z' }));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('no admite mas acciones.');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    montar();
    resolverCatalogos();
    resolverDetalle(permisoDto());
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
