// =============================================================================
// Pruebas de la vista de Ordenes de Trabajo de Instalacion / OTI (Req 15.1-15.3, 10.2)
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

import { OperacionInstalacion } from './instalacion';
import { AuthService } from '../../../core/auth/auth.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

const CLIENTE_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const OTI_ID = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee';
const SITIO_ID = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const OF_ID = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
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

function otiDto(over: Record<string, unknown> = {}) {
  return {
    id: OTI_ID,
    ordenFabricacionId: OF_ID,
    sitioId: SITIO_ID,
    cuadrillaId: CUADRILLA_ID,
    clienteId: CLIENTE_ID,
    fechaProgramada: '2026-04-01',
    estado: 'programada',
    version: 0,
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
    ...over,
  };
}

describe('OperacionInstalacion', () => {
  let fixture: ComponentFixture<OperacionInstalacion>;
  let http: HttpTestingController;

  function montar(): void {
    TestBed.configureTestingModule({
      imports: [OperacionInstalacion, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    });
    fixture = TestBed.createComponent(OperacionInstalacion);
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
    http.expectOne((r) => r.url === '/api/v1/proyectos' && r.method === 'GET').flush(paginaVacia());
    http.expectOne((r) => r.url === '/api/v1/materiales' && r.method === 'GET').flush(paginaVacia());
    fixture.detectChanges();
  }

  /**
   * Responde los GET de arranque: el listado principal y los tres conteos de KPIs
   * (estado=programada|en_curso|completada, size=1). Todos van a la misma URL
   * base; el primero lleva el contenido y los conteos se resuelven con una pagina
   * de tamano 1 para no dejar peticiones pendientes.
   */
  function resolverListado(otis: Record<string, unknown>[]): void {
    const reqs = http.match(
      (r) => r.url === '/api/v1/ordenes-trabajo-instalacion' && r.method === 'GET',
    );
    reqs[0].flush({
      content: otis,
      page: 0,
      size: 20,
      totalElements: otis.length,
      totalPages: otis.length === 0 ? 0 : 1,
    });
    for (const r of reqs.slice(1)) {
      r.flush({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 });
    }
    fixture.detectChanges();
  }

  it('muestra el estado de carga antes de resolver los datos', () => {
    montar();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="status"]')).not.toBeNull();
    expect(host.textContent).toContain('Cargando informacion');
    resolverCatalogos();
    resolverListado([otiDto()]);
  });

  it('muestra el estado vacio cuando no hay ordenes de trabajo', () => {
    montar();
    resolverCatalogos();
    resolverListado([]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'No hay ordenes de trabajo para el filtro seleccionado.',
    );
  });

  it('muestra el estado de error cuando el listado falla', () => {
    montar();
    resolverCatalogos();
    // El primer GET es el listado principal (falla); los conteos se resuelven
    // para no dejar peticiones pendientes.
    const reqs = http.match(
      (r) => r.url === '/api/v1/ordenes-trabajo-instalacion' && r.method === 'GET',
    );
    reqs[0].flush({ detail: 'Error interno' }, { status: 500, statusText: 'Server Error' });
    for (const r of reqs.slice(1)) {
      r.flush({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 });
    }
    fixture.detectChanges();
    expect((fixture.componentInstance as unknown as { fase(): string }).fase()).toBe('error');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Reintentar');
  });

  it('resuelve el Cliente por nombre sin exponer UUIDs ni recortes', () => {
    montar();
    resolverCatalogos();
    resolverListado([otiDto()]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Rotulos Acme');
    expect(texto).not.toContain(CLIENTE_ID);
    expect(texto).not.toContain(OTI_ID);
    expect(texto).not.toContain(SITIO_ID);
    expect(texto).not.toContain(CLIENTE_ID.slice(0, 8));
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    montar();
    resolverCatalogos();
    resolverListado([otiDto()]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);

  it('impide completar una OTI sin evidencia: consulta el detalle y no envia el cambio de estado (Req 19.6)', async () => {
    montar();
    resolverCatalogos();
    resolverListado([otiDto({ estado: 'en_curso' })]);

    const componente = fixture.componentInstance as unknown as {
      cambiarEstado(oti: { id: string; estado: string }, estado: string): Promise<void>;
    };
    const promesa = componente.cambiarEstado({ id: OTI_ID, estado: 'en_curso' }, 'completada');

    // La guarda preventiva consulta el detalle de la OTI (pendientes + evidencias).
    const detalle = http.expectOne(
      (r) => r.url === `/api/v1/ordenes-trabajo-instalacion/${OTI_ID}` && r.method === 'GET',
    );
    // Detalle SIN evidencias y sin pendientes: la guarda de evidencia debe frenar.
    detalle.flush({ ...otiDto({ estado: 'en_curso' }), pendientes: [], evidencias: [] });

    await promesa;

    // No debe intentarse el cambio de estado (PUT) porque falta la evidencia.
    http.expectNone(
      (r) => r.url === `/api/v1/ordenes-trabajo-instalacion/${OTI_ID}/estado` && r.method === 'PUT',
    );
  });

  it('permite completar una OTI con evidencia y sin pendientes: envia el cambio de estado (Req 19.6)', async () => {
    montar();
    resolverCatalogos();
    resolverListado([otiDto({ estado: 'en_curso' })]);

    const componente = fixture.componentInstance as unknown as {
      cambiarEstado(oti: { id: string; estado: string }, estado: string): Promise<void>;
    };
    // Auto-confirma el modal de confirmacion (el ConfirmDialogService real abre un
    // dialogo; aqui interceptamos el metodo para no depender de su UI).
    (
      componente as unknown as { confirm: { confirmar: () => Promise<boolean> } }
    ).confirm.confirmar = () => Promise.resolve(true);

    const promesa = componente.cambiarEstado({ id: OTI_ID, estado: 'en_curso' }, 'completada');

    const detalle = http.expectOne(
      (r) => r.url === `/api/v1/ordenes-trabajo-instalacion/${OTI_ID}` && r.method === 'GET',
    );
    // Con una evidencia adjunta y sin pendientes, el cierre puede proceder.
    detalle.flush({
      ...otiDto({ estado: 'en_curso' }),
      pendientes: [],
      evidencias: [
        {
          id: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
          ordenTrabajoInstalacionId: OTI_ID,
          url: 'https://evidencias/foto.jpg',
          version: 0,
          createdAt: '2026-03-01T00:00:00Z',
          updatedAt: '2026-03-01T00:00:00Z',
        },
      ],
    });

    // cambiarEstado encadena varias microtareas (await del detalle y del modal de
    // confirmacion) antes de emitir el PUT. En zoneless, whenStable() no rastrea
    // esas promesas nativas; se cede al event loop (macrotask) para garantizar que
    // TODAS las microtareas pendientes ya corrieron antes de reclamar la peticion.
    await new Promise((resolve) => setTimeout(resolve));

    // Tras validar y confirmar, se envia el PUT de cambio de estado.
    const put = http.expectOne(
      (r) => r.url === `/api/v1/ordenes-trabajo-instalacion/${OTI_ID}/estado` && r.method === 'PUT',
    );
    put.flush(otiDto({ estado: 'completada' }));

    await promesa;
    // Tras completar, la vista recarga listado y conteos.
    const recarga = http.match(
      (r) => r.url === '/api/v1/ordenes-trabajo-instalacion' && r.method === 'GET',
    );
    for (const r of recarga) {
      r.flush({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 });
    }
  });
});
