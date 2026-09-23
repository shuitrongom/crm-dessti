// =============================================================================
// Pruebas de la vista de Permisos de Instalacion (Req 15.1-15.3, 10.2)
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

import { OperacionPermisos } from './permisos';
import { AuthService } from '../../../core/auth/auth.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

const PERMISO_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const SITIO_ID = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

class AuthServiceStub {
  tienePermiso(): boolean {
    return true;
  }
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

describe('OperacionPermisos', () => {
  let fixture: ComponentFixture<OperacionPermisos>;
  let http: HttpTestingController;

  function montar(): void {
    TestBed.configureTestingModule({
      imports: [OperacionPermisos, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    });
    fixture = TestBed.createComponent(OperacionPermisos);
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

  /** Responde el GET del listado de permisos. */
  function resolverListado(permisos: Record<string, unknown>[]): void {
    const req = http.expectOne(
      (r) => r.url === '/api/v1/permisos-instalacion' && r.method === 'GET',
    );
    req.flush({
      content: permisos,
      page: 0,
      size: 20,
      totalElements: permisos.length,
      totalPages: permisos.length === 0 ? 0 : 1,
    });
    fixture.detectChanges();
  }

  it('muestra el estado de carga antes de resolver los datos', () => {
    montar();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="status"]')).not.toBeNull();
    expect(host.textContent).toContain('Cargando informacion');
    resolverListado([permisoDto()]);
  });

  it('muestra el estado vacio cuando no hay permisos', () => {
    montar();
    resolverListado([]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'No hay permisos para los filtros seleccionados.',
    );
  });

  it('muestra el estado de error cuando el listado falla', () => {
    montar();
    http
      .expectOne((r) => r.url === '/api/v1/permisos-instalacion' && r.method === 'GET')
      .flush({ detail: 'Error interno' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect((fixture.componentInstance as unknown as { fase(): string }).fase()).toBe('error');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Reintentar');
  });

  it('no expone UUIDs crudos ni recortes en el listado', () => {
    montar();
    resolverListado([permisoDto()]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Municipal');
    expect(texto).not.toContain(PERMISO_ID);
    expect(texto).not.toContain(SITIO_ID);
    expect(texto).not.toContain(PERMISO_ID.slice(0, 8));
  });

  // ---------------------------------------------------------------------------
  // Rediseño enterprise: uso de ChipEstado por estado (Req 7.2, 7.5).
  // solicitado→info, aprobado→exito, rechazado→error; etiqueta es-MX, sin UUIDs.
  // ---------------------------------------------------------------------------
  const UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;

  const CASOS_ESTADO_PERMISO: ReadonlyArray<{
    estado: string;
    variante: string;
    etiqueta: string;
  }> = [
    { estado: 'solicitado', variante: 'info', etiqueta: 'Solicitado' },
    { estado: 'aprobado', variante: 'exito', etiqueta: 'Aprobado' },
    { estado: 'rechazado', variante: 'error', etiqueta: 'Rechazado' },
  ];

  for (const caso of CASOS_ESTADO_PERMISO) {
    it(`renderiza un ChipEstado ${caso.variante} para el estado "${caso.estado}"`, () => {
      montar();
      resolverListado([permisoDto({ estado: caso.estado })]);
      const host = fixture.nativeElement as HTMLElement;
      const chip = host.querySelector<HTMLElement>(
        `app-chip-estado .chip-estado[data-variante="${caso.variante}"]`,
      );
      expect(chip).not.toBeNull();
      expect(chip?.textContent?.trim()).toBe(caso.etiqueta);
      expect(chip?.textContent ?? '').not.toMatch(UUID_RE);
    });
  }

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    montar();
    resolverListado([permisoDto()]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
