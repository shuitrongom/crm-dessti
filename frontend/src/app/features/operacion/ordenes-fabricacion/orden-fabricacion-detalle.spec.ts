// =============================================================================
// Pruebas del detalle de Orden de Fabricacion (Req 15.1-15.3, 10.2)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless):
//   - Estados carga / ok (con datos) / error del detalle (StateContainer + fase).
//   - El DOM NO expone UUIDs crudos ni recortes: Cliente y Material por NOMBRE.
//   - Estado "vacio" de la tabla de partidas cuando la OF no tiene partidas.
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

import { OperacionOrdenFabricacionDetalle } from './orden-fabricacion-detalle';
import { AuthService } from '../../../core/auth/auth.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

const CLIENTE_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const MATERIAL_ID = 'dddddddd-dddd-dddd-dddd-dddddddddddd';
const OF_ID = 'cccccccc-cccc-cccc-cccc-cccccccccccc';

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

function materialDto() {
  return {
    id: MATERIAL_ID,
    nombre: 'Lamina galvanizada',
    unidadMedida: 'm2',
    stockMinimo: 0,
    existencias: 100,
    stockBajo: false,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

function ordenDetalle(over: Record<string, unknown> = {}) {
  return {
    id: OF_ID,
    cotizacionId: null,
    clienteId: CLIENTE_ID,
    estado: 'pendiente',
    partidas: [{ materialId: MATERIAL_ID, cantidad: 12 }],
    version: 0,
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
    ...over,
  };
}

describe('OperacionOrdenFabricacionDetalle', () => {
  let fixture: ComponentFixture<OperacionOrdenFabricacionDetalle>;
  let http: HttpTestingController;

  function montar(): void {
    TestBed.configureTestingModule({
      imports: [OperacionOrdenFabricacionDetalle, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    });
    fixture = TestBed.createComponent(OperacionOrdenFabricacionDetalle);
    fixture.componentRef.setInput('id', OF_ID);
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
    http.expectOne((r) => r.url === '/api/v1/proyectos' && r.method === 'GET').flush(paginaVacia());
    http.expectOne((r) => r.url === '/api/v1/materiales' && r.method === 'GET').flush({
      content: [materialDto()],
      page: 0,
      size: 200,
      totalElements: 1,
      totalPages: 1,
    });
    http
      .expectOne((r) => r.url === '/api/v1/ordenes-fabricacion' && r.params.get('size') === '200')
      .flush(paginaVacia());
  }

  /** Instancia del componente de detalle. */
  function detalle(): { fase(): string } {
    return fixture.componentInstance as unknown as { fase(): string };
  }

  /** Responde el GET del detalle de la OF. */
  function resolverDetalle(orden: Record<string, unknown>): void {
    http
      .expectOne((r) => r.url === `/api/v1/ordenes-fabricacion/${OF_ID}` && r.method === 'GET')
      .flush(orden);
    fixture.detectChanges();
  }

  it('muestra el estado de carga antes de resolver los datos', () => {
    montar();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="status"]')).not.toBeNull();
    expect(host.textContent).toContain('Cargando informacion');
    resolverCatalogos();
    resolverDetalle(ordenDetalle());
  });

  it('muestra el estado de error cuando el detalle falla', () => {
    montar();
    // forkJoin: catalogos ok pero el detalle falla -> fase error.
    resolverCatalogos();
    http
      .expectOne((r) => r.url === `/api/v1/ordenes-fabricacion/${OF_ID}`)
      .flush({ detail: 'No encontrada' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(detalle().fase()).toBe('error');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Reintentar');
  });

  it('resuelve Cliente y Material por nombre sin exponer UUIDs ni recortes', () => {
    montar();
    resolverCatalogos();
    resolverDetalle(ordenDetalle());
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Rotulos Acme');
    expect(texto).toContain('Lamina galvanizada');
    expect(texto).toContain('Directa');
    expect(texto).not.toContain(CLIENTE_ID);
    expect(texto).not.toContain(MATERIAL_ID);
    expect(texto).not.toContain(OF_ID);
    expect(texto).not.toContain(CLIENTE_ID.slice(0, 8));
  });

  it('muestra el estado vacio de partidas cuando la OF no tiene partidas', () => {
    montar();
    resolverCatalogos();
    resolverDetalle(ordenDetalle({ partidas: [] }));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Esta orden no tiene partidas registradas.',
    );
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    montar();
    resolverCatalogos();
    resolverDetalle(ordenDetalle());
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
