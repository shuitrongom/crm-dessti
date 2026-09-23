// =============================================================================
// Pruebas del detalle de Orden de Trabajo de Instalacion / OTI (Req 15.1-15.3, 10.2, 8)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless):
//   - Estados carga / ok (con datos) / error del detalle (StateContainer + fase).
//   - El DOM NO expone UUIDs crudos ni recortes: Cliente/Sitio/OF por NOMBRE.
//   - Estados vacios de pendientes y evidencias cuando no hay elementos.
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

import { OperacionOtiDetalle } from './oti-detalle';
import { AuthService } from '../../../core/auth/auth.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

const OTI_ID = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee';
const CLIENTE_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const SITIO_ID = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const OF_ID = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
const PROYECTO_ID = 'dddddddd-dddd-dddd-dddd-dddddddddddd';
const CUADRILLA_ID = 'ffffffff-ffff-ffff-ffff-ffffffffffff';

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

function proyectoConSitio() {
  return {
    id: PROYECTO_ID,
    clienteId: CLIENTE_ID,
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

function otiDetalle(over: Record<string, unknown> = {}) {
  return {
    id: OTI_ID,
    ordenFabricacionId: OF_ID,
    sitioId: SITIO_ID,
    cuadrillaId: CUADRILLA_ID,
    clienteId: CLIENTE_ID,
    fechaProgramada: '2026-04-01',
    estado: 'en_curso',
    pendientes: [],
    evidencias: [],
    version: 0,
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
    ...over,
  };
}

describe('OperacionOtiDetalle', () => {
  let fixture: ComponentFixture<OperacionOtiDetalle>;
  let http: HttpTestingController;

  function montar(): void {
    TestBed.configureTestingModule({
      imports: [OperacionOtiDetalle, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    });
    fixture = TestBed.createComponent(OperacionOtiDetalle);
    fixture.componentRef.setInput('id', OTI_ID);
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

  /** Responde el GET del detalle de la OTI. */
  function resolverDetalle(detalle: Record<string, unknown>): void {
    http
      .expectOne(
        (r) => r.url === `/api/v1/ordenes-trabajo-instalacion/${OTI_ID}` && r.method === 'GET',
      )
      .flush(detalle);
    fixture.detectChanges();
  }

  it('muestra el estado de carga antes de resolver los datos', () => {
    montar();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="status"]')).not.toBeNull();
    expect(host.textContent).toContain('Cargando informacion');
    resolverCatalogos();
    resolverDetalle(otiDetalle());
  });

  it('muestra el estado de error cuando el detalle falla', () => {
    montar();
    resolverCatalogos();
    http
      .expectOne((r) => r.url === `/api/v1/ordenes-trabajo-instalacion/${OTI_ID}`)
      .flush({ detail: 'No encontrada' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect((fixture.componentInstance as unknown as { fase(): string }).fase()).toBe('error');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Reintentar');
  });

  it('resuelve Cliente y Sitio por nombre sin exponer UUIDs ni recortes', () => {
    montar();
    resolverCatalogos();
    resolverDetalle(otiDetalle());
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Rotulos Acme');
    expect(texto).toContain('Sucursal Centro');
    expect(texto).not.toContain(OTI_ID);
    expect(texto).not.toContain(CLIENTE_ID);
    expect(texto).not.toContain(SITIO_ID);
    expect(texto).not.toContain(OF_ID);
    expect(texto).not.toContain(CLIENTE_ID.slice(0, 8));
  });

  it('muestra los estados vacios de pendientes y evidencias cuando no hay elementos', () => {
    montar();
    resolverCatalogos();
    resolverDetalle(otiDetalle({ pendientes: [], evidencias: [] }));
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Esta orden de trabajo no tiene pendientes registrados.');
    expect(texto).toContain('Esta orden de trabajo no tiene evidencias registradas.');
  });

  // ---------------------------------------------------------------------------
  // Rediseño enterprise: uso de ChipEstado por estado (Req 7.2, 7.5).
  // Cada estado de la OTI se renderiza con un <app-chip-estado> cuya variante
  // semantica y etiqueta es-MX corresponden al estado, sin UUIDs en el texto.
  // ---------------------------------------------------------------------------
  const UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;

  const CASOS_ESTADO_OTI: ReadonlyArray<{
    estado: string;
    variante: string;
    etiqueta: string;
  }> = [
    { estado: 'programada', variante: 'info', etiqueta: 'Programada' },
    { estado: 'en_curso', variante: 'advertencia', etiqueta: 'En curso' },
    { estado: 'completada', variante: 'exito', etiqueta: 'Completada' },
    { estado: 'cancelada', variante: 'neutro', etiqueta: 'Cancelada' },
  ];

  for (const caso of CASOS_ESTADO_OTI) {
    it(`renderiza un ChipEstado ${caso.variante} para el estado "${caso.estado}"`, () => {
      montar();
      resolverCatalogos();
      resolverDetalle(otiDetalle({ estado: caso.estado }));
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
    resolverDetalle(otiDetalle());
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
