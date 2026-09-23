// =============================================================================
// Pruebas de la Ficha 360 del Cliente (Req 1, 7, 8) — actividad comercial conectada
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless):
//   - Con permisos de listar, las secciones de Oportunidades y Cotizaciones se
//     cargan con el filtro clienteId y sus enlaces apuntan al detalle correcto.
//   - Los indicadores se CALCULAN a partir de las listas (abiertas, pipeline,
//     numero de cotizaciones).
//   - Sin el permiso de listar cotizaciones, esa seccion NO se renderiza.
//   - Estado vacio cuando el cliente no tiene oportunidades.
//   - No se muestran UUIDs en la vista.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';
import { LOCALE_ID } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { ComercialClienteDetalle } from './cliente-detalle';
import { AuthService } from '../../../core/auth/auth.service';
import { Cliente, Cotizacion, Oportunidad } from '../models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

// Registra es-MX para que los pipes date/currency con locale explicito no lancen NG0701.
registerLocaleData(localeEsMx);

const CLIENTE_ID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';

/** AuthService de prueba con permisos configurables. */
class AuthServiceStub {
  constructor(private readonly permisos: string[]) {}
  tienePermiso(recurso: string, operacion: string): boolean {
    return this.permisos.includes(`${recurso}:${operacion}`);
  }
}

/** ClienteDto minimo. */
function clienteDto(): Cliente {
  return {
    id: CLIENTE_ID,
    nombre: 'Acme',
    rfc: 'ABCD901231XYZ',
    email: 'ventas@acme.test',
    telefono: '5551234567',
    nombreComercial: null,
    tipoPersona: 'moral',
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

function oportunidadDto(over: Partial<Oportunidad> = {}): Oportunidad {
  return {
    id: 'op-1',
    clienteId: CLIENTE_ID,
    titulo: 'Proyecto rotulos',
    valorEstimado: 1500,
    etapa: 'nuevo',
    responsableUsuarioId: null,
    cotizacionId: null,
    canalVentaId: null,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  };
}

function cotizacionDto(over: Partial<Cotizacion> = {}): Cotizacion {
  return {
    id: 'cot-1',
    clienteId: CLIENTE_ID,
    oportunidadId: null,
    estado: 'borrador',
    subtotal: 1000,
    total: 1160,
    partidas: [],
    canalVentaId: null,
    folio: 'COT-2026-0001',
    fechaEmision: '2026-02-01',
    validoHasta: null,
    condiciones: null,
    notas: null,
    moneda: 'MXN',
    enviadaEn: null,
    version: 0,
    createdAt: '2026-02-01T00:00:00Z',
    updatedAt: '2026-02-01T00:00:00Z',
    ...over,
  };
}

interface DetalleProbe {
  numOportunidadesAbiertas(): number;
  valorPipeline(): number;
  numCotizaciones(): number;
}

describe('ComercialClienteDetalle', () => {
  let fixture: ComponentFixture<ComercialClienteDetalle>;
  let http: HttpTestingController;

  /** Configura el TestBed con un conjunto de permisos y monta el componente. */
  function montar(permisos: string[]): void {
    TestBed.configureTestingModule({
      imports: [ComercialClienteDetalle, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: new AuthServiceStub(permisos) },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    });
    fixture = TestBed.createComponent(ComercialClienteDetalle);
    fixture.componentRef.setInput('id', CLIENTE_ID);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    // Datos del cliente (cabecera).
    http.expectOne((r) => r.url === `/api/v1/clientes/${CLIENTE_ID}`).flush(clienteDto());
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  /** Resuelve las listas relacionadas segun los permisos concedidos. */
  function resolverListas(oportunidades: Oportunidad[], cotizaciones: Cotizacion[]): void {
    const ops = http.match((r) => r.url.startsWith('/api/v1/oportunidades'));
    for (const req of ops) {
      req.flush({ content: oportunidades, page: 0, size: 20, totalElements: oportunidades.length, totalPages: 1 });
    }
    const cots = http.match((r) => r.url.startsWith('/api/v1/cotizaciones'));
    for (const req of cots) {
      req.flush({ content: cotizaciones, page: 0, size: 20, totalElements: cotizaciones.length, totalPages: 1 });
    }
    fixture.detectChanges();
  }

  it('carga oportunidades y cotizaciones con el filtro clienteId y calcula indicadores', () => {
    montar(['cliente:leer', 'oportunidad:listar', 'cotizacion:listar']);

    const reqOp = http.expectOne((r) => r.url.startsWith('/api/v1/oportunidades'));
    expect(reqOp.request.params.get('clienteId')).toBe(CLIENTE_ID);
    reqOp.flush({
      content: [
        oportunidadDto({ id: 'op-1', valorEstimado: 1000, etapa: 'nuevo' }),
        oportunidadDto({ id: 'op-2', valorEstimado: 500, etapa: 'propuesta' }),
        oportunidadDto({ id: 'op-3', valorEstimado: 9999, etapa: 'ganado' }),
      ],
      page: 0,
      size: 20,
      totalElements: 3,
      totalPages: 1,
    });

    const reqCot = http.expectOne((r) => r.url.startsWith('/api/v1/cotizaciones'));
    expect(reqCot.request.params.get('clienteId')).toBe(CLIENTE_ID);
    reqCot.flush({
      content: [cotizacionDto()],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const comp = fixture.componentInstance as unknown as DetalleProbe;
    // Dos oportunidades abiertas (nuevo, propuesta); la ganada no cuenta.
    expect(comp.numOportunidadesAbiertas()).toBe(2);
    expect(comp.valorPipeline()).toBe(1500);
    expect(comp.numCotizaciones()).toBe(1);
  });

  it('los enlaces apuntan al detalle correcto sin exponer UUIDs de las entidades', () => {
    montar(['cliente:leer', 'oportunidad:listar', 'cotizacion:listar']);
    resolverListas([oportunidadDto()], [cotizacionDto()]);

    const host = fixture.nativeElement as HTMLElement;
    // Enlace a la cotizacion por su id de ruta, mostrando el folio (no el UUID).
    const enlaceCot = host.querySelector('a[href="/empresa/comercial/cotizaciones/cot-1"]');
    expect(enlaceCot).not.toBeNull();
    expect(enlaceCot?.textContent).toContain('COT-2026-0001');
    // Enlace a la oportunidad (pantalla de pipeline).
    expect(host.querySelector('a[href^="/empresa/comercial/oportunidades"]')).not.toBeNull();
    // No se muestra ningun UUID como texto.
    expect(host.textContent).not.toContain('cot-1');
  });

  it('sin permiso de listar cotizaciones, la seccion de cotizaciones no se renderiza', () => {
    montar(['cliente:leer', 'oportunidad:listar']);
    // Solo se pide la lista de oportunidades; NO se pide cotizaciones.
    http.expectOne((r) => r.url.startsWith('/api/v1/oportunidades')).flush({
      content: [oportunidadDto()],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectNone((r) => r.url.startsWith('/api/v1/cotizaciones'));
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const titulos = Array.from(host.querySelectorAll('mat-card-title')).map((t) =>
      (t.textContent ?? '').trim(),
    );
    expect(titulos).toContain('Oportunidades');
    expect(titulos).not.toContain('Cotizaciones');
  });

  it('muestra el estado vacio cuando el cliente no tiene oportunidades', () => {
    montar(['cliente:leer', 'oportunidad:listar', 'cotizacion:listar']);
    resolverListas([], []);
    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Este cliente aun no tiene oportunidades.');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    montar(['cliente:leer', 'oportunidad:listar', 'cotizacion:listar']);
    resolverListas([oportunidadDto()], [cotizacionDto()]);
    await esperarSinViolaciones(fixture);
  }, 30000);
});
