// =============================================================================
// Pruebas de la vista de Proyectos (Req 15.1-15.3, 10.2)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless):
//   - Estados carga / vacio / error de la vista (StateContainer + signal fase).
//   - El DOM NO expone UUIDs crudos ni recortes: el Cliente se resuelve por NOMBRE.
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

import { OperacionProyectos } from './proyectos';
import { AuthService } from '../../../core/auth/auth.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

const CLIENTE_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const PROYECTO_ID = 'dddddddd-dddd-dddd-dddd-dddddddddddd';

class AuthServiceStub {
  tienePermiso(): boolean {
    return true;
  }
}

function paginaVacia() {
  return { content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 };
}

function clienteDto() {
  return {
    id: CLIENTE_ID,
    nombre: 'Rotulos Acme',
    rfc: 'ABC010101AB1',
    email: null,
    telefono: null,
    nombreComercial: null,
    tipoPersona: null,
    telefonoAdicional: null,
    direccionCalle: null,
    direccionCiudad: null,
    direccionEstado: null,
    direccionCp: null,
    direccionPais: null,
    notas: null,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

function proyectoDto(over: Record<string, unknown> = {}) {
  return {
    id: PROYECTO_ID,
    clienteId: CLIENTE_ID,
    nombre: 'Proyecto centro',
    estadoConsolidado: null,
    sitios: [],
    version: 0,
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
    ...over,
  };
}

describe('OperacionProyectos', () => {
  let fixture: ComponentFixture<OperacionProyectos>;
  let http: HttpTestingController;

  function montar(): void {
    TestBed.configureTestingModule({
      imports: [OperacionProyectos, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    });
    fixture = TestBed.createComponent(OperacionProyectos);
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

  /**
   * Resuelve los 5 GET de catalogos de NombresOperacionService. El catalogo de
   * proyectos (size=200) comparte URL con el listado de la vista (size=20).
   */
  function resolverCatalogos(): void {
    http.expectOne((r) => r.url === '/api/v1/clientes' && r.method === 'GET').flush({
      content: [clienteDto()],
      page: 0,
      size: 200,
      totalElements: 1,
      totalPages: 1,
    });
    http
      .expectOne((r) => r.url === '/api/v1/cotizaciones' && r.method === 'GET')
      .flush(paginaVacia());
    http
      .expectOne((r) => r.url === '/api/v1/ordenes-fabricacion' && r.method === 'GET')
      .flush(paginaVacia());
    http.expectOne((r) => r.url === '/api/v1/materiales' && r.method === 'GET').flush(paginaVacia());
    // El catalogo de proyectos (proyectosService.listar(null,0,200)).
    http
      .expectOne((r) => r.url === '/api/v1/proyectos' && r.params.get('size') === '200')
      .flush(paginaVacia());
    fixture.detectChanges();
  }

  /** Responde el GET del listado de la vista (size del componente = 20). */
  function resolverListado(proyectos: Record<string, unknown>[]): void {
    const req = http.expectOne(
      (r) => r.url === '/api/v1/proyectos' && r.params.get('size') === '20',
    );
    req.flush({
      content: proyectos,
      page: 0,
      size: 20,
      totalElements: proyectos.length,
      totalPages: proyectos.length === 0 ? 0 : 1,
    });
    fixture.detectChanges();
  }

  it('muestra el estado de carga antes de resolver los datos', () => {
    montar();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="status"]')).not.toBeNull();
    expect(host.textContent).toContain('Cargando informacion');
    resolverCatalogos();
    resolverListado([proyectoDto()]);
  });

  it('muestra el estado vacio cuando no hay proyectos', () => {
    montar();
    resolverCatalogos();
    resolverListado([]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'No hay proyectos para el filtro seleccionado.',
    );
  });

  it('muestra el estado de error cuando el listado falla', () => {
    montar();
    resolverCatalogos();
    http
      .expectOne((r) => r.url === '/api/v1/proyectos' && r.params.get('size') === '20')
      .flush({ detail: 'Error interno' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect((fixture.componentInstance as unknown as { fase(): string }).fase()).toBe('error');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Reintentar');
  });

  it('resuelve el Cliente por nombre sin exponer UUIDs ni recortes', () => {
    montar();
    resolverCatalogos();
    resolverListado([proyectoDto()]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Proyecto centro');
    expect(texto).toContain('Rotulos Acme');
    expect(texto).not.toContain(CLIENTE_ID);
    expect(texto).not.toContain(PROYECTO_ID);
    expect(texto).not.toContain(CLIENTE_ID.slice(0, 8));
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    montar();
    resolverCatalogos();
    resolverListado([proyectoDto()]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
