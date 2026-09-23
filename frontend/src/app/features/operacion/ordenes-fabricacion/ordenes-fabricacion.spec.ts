// =============================================================================
// Pruebas de la vista de Ordenes de Fabricacion (Req 15.1-15.3, 10.2)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless):
//   - Estados carga / vacio / error de la vista (StateContainer + signal fase).
//   - El DOM NO expone UUIDs crudos ni recortes: el Cliente y la Cotizacion se
//     resuelven por NOMBRE via NombresOperacionService.
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

import { OperacionOrdenesFabricacion } from './ordenes-fabricacion';
import { AuthService } from '../../../core/auth/auth.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

// Registra es-MX para pipes con locale explicito (evita NG0701).
registerLocaleData(localeEsMx);

const CLIENTE_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const COTIZACION_ID = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const OF_ID = 'cccccccc-cccc-cccc-cccc-cccccccccccc';

/** AuthService de prueba: concede todos los permisos de la OF. */
class AuthServiceStub {
  tienePermiso(): boolean {
    return true;
  }
}

/** Pagina vacia estandar. */
function paginaVacia() {
  return { content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 };
}

/** Cliente minimo (con nombre legible). */
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

/** Cotizacion minima (con folio legible). */
function cotizacionDto() {
  return {
    id: COTIZACION_ID,
    clienteId: CLIENTE_ID,
    oportunidadId: null,
    estado: 'aprobada',
    subtotal: 1000,
    total: 1160,
    partidas: [],
    canalVentaId: null,
    folio: 'COT-2026-0007',
    fechaEmision: '2026-02-01',
    validoHasta: null,
    condiciones: null,
    notas: null,
    moneda: 'MXN',
    enviadaEn: null,
    version: 0,
    createdAt: '2026-02-01T00:00:00Z',
    updatedAt: '2026-02-01T00:00:00Z',
  };
}

/** Orden de fabricacion (desde cotizacion). */
function ordenDto(over: Record<string, unknown> = {}) {
  return {
    id: OF_ID,
    cotizacionId: COTIZACION_ID,
    clienteId: CLIENTE_ID,
    estado: 'pendiente',
    version: 0,
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
    ...over,
  };
}

describe('OperacionOrdenesFabricacion', () => {
  let fixture: ComponentFixture<OperacionOrdenesFabricacion>;
  let http: HttpTestingController;

  function montar(): void {
    TestBed.configureTestingModule({
      imports: [OperacionOrdenesFabricacion, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    });
    fixture = TestBed.createComponent(OperacionOrdenesFabricacion);
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
   * Resuelve los 5 GET de catalogos de NombresOperacionService (clientes,
   * cotizaciones, ordenes-fabricacion, proyectos, materiales). El GET de
   * ordenes-fabricacion del catalogo comparte URL con el del listado: se
   * responden todos los pendientes a esa URL con la pagina del catalogo.
   */
  function resolverCatalogos(): void {
    http.expectOne((r) => r.url === '/api/v1/clientes' && r.method === 'GET').flush({
      content: [clienteDto()],
      page: 0,
      size: 200,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne((r) => r.url === '/api/v1/cotizaciones' && r.method === 'GET').flush({
      content: [cotizacionDto()],
      page: 0,
      size: 200,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne((r) => r.url === '/api/v1/proyectos' && r.method === 'GET').flush(paginaVacia());
    http.expectOne((r) => r.url === '/api/v1/materiales' && r.method === 'GET').flush(paginaVacia());
    // El catalogo de OF (produccionService.listar(null,0,200)).
    http
      .expectOne((r) => r.url === '/api/v1/ordenes-fabricacion' && r.params.get('size') === '200')
      .flush({ content: [ordenDto()], page: 0, size: 200, totalElements: 1, totalPages: 1 });
    fixture.detectChanges();
  }

  /** Responde el GET del listado de la vista (size del componente = 20). */
  function resolverListado(ordenes: Record<string, unknown>[]): void {
    const req = http.expectOne(
      (r) => r.url === '/api/v1/ordenes-fabricacion' && r.params.get('size') === '20',
    );
    req.flush({
      content: ordenes,
      page: 0,
      size: 20,
      totalElements: ordenes.length,
      totalPages: ordenes.length === 0 ? 0 : 1,
    });
    fixture.detectChanges();
  }

  it('muestra el estado de carga (spinner) antes de resolver los datos', () => {
    montar();
    // Antes de resolver los catalogos y el listado, la vista esta en fase carga.
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="status"]')).not.toBeNull();
    expect(host.textContent).toContain('Cargando informacion');
    // Resuelve todo para dejar el backend de prueba sin peticiones abiertas.
    resolverCatalogos();
    resolverListado([ordenDto()]);
  });

  it('muestra el estado vacio cuando no hay ordenes para el filtro', () => {
    montar();
    resolverCatalogos();
    resolverListado([]);
    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('No hay ordenes de fabricacion para el filtro seleccionado.');
  });

  it('muestra el estado de error cuando el listado falla', () => {
    montar();
    resolverCatalogos();
    http
      .expectOne((r) => r.url === '/api/v1/ordenes-fabricacion' && r.params.get('size') === '20')
      .flush({ detail: 'Error interno' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    // El StateContainer ofrece el boton de reintentar en el estado de error.
    expect(host.querySelector('button')?.textContent ?? host.textContent).toBeTruthy();
    expect((fixture.componentInstance as unknown as { fase(): string }).fase()).toBe('error');
  });

  it('resuelve Cliente y Cotizacion por nombre sin exponer UUIDs ni recortes', () => {
    montar();
    resolverCatalogos();
    resolverListado([ordenDto()]);
    const host = fixture.nativeElement as HTMLElement;
    const texto = host.textContent ?? '';
    // Muestra el nombre del Cliente y el folio de la Cotizacion.
    expect(texto).toContain('Rotulos Acme');
    expect(texto).toContain('COT-2026-0007');
    // No aparece ningun UUID crudo (ni completo ni recortado).
    expect(texto).not.toContain(CLIENTE_ID);
    expect(texto).not.toContain(COTIZACION_ID);
    expect(texto).not.toContain(OF_ID);
    expect(texto).not.toContain(CLIENTE_ID.slice(0, 8));
  });

  // ---------------------------------------------------------------------------
  // Rediseño enterprise: uso de ChipEstado por estado (Req 7.2, 7.5).
  // Cada estado de la OF se renderiza con un <app-chip-estado> cuya variante
  // semantica y etiqueta es-MX corresponden al estado, sin UUIDs en el texto.
  // ---------------------------------------------------------------------------
  const UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;

  const CASOS_ESTADO_OF: ReadonlyArray<{
    estado: string;
    variante: string;
    etiqueta: string;
  }> = [
    { estado: 'pendiente', variante: 'info', etiqueta: 'Pendiente' },
    { estado: 'en_produccion', variante: 'advertencia', etiqueta: 'En produccion' },
    { estado: 'terminada', variante: 'exito', etiqueta: 'Terminada' },
    { estado: 'cancelada', variante: 'neutro', etiqueta: 'Cancelada' },
  ];

  for (const caso of CASOS_ESTADO_OF) {
    it(`renderiza un ChipEstado ${caso.variante} para el estado "${caso.estado}"`, () => {
      montar();
      resolverCatalogos();
      resolverListado([ordenDto({ estado: caso.estado })]);
      const host = fixture.nativeElement as HTMLElement;
      const chip = host.querySelector<HTMLElement>(
        `app-chip-estado .chip-estado[data-variante="${caso.variante}"]`,
      );
      // Existe el chip con la variante semantica esperada para este estado.
      expect(chip).not.toBeNull();
      // La etiqueta es-MX del chip corresponde al estado.
      expect(chip?.textContent?.trim()).toBe(caso.etiqueta);
      // El texto del chip nunca expone un UUID crudo.
      expect(chip?.textContent ?? '').not.toMatch(UUID_RE);
    });
  }

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    montar();
    resolverCatalogos();
    resolverListado([ordenDto()]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
