// =============================================================================
// Pruebas de la vista ComercialOportunidades: alta con selector de Cliente
// (Req 14, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless, con los
// temporizadores falsos de Vitest para el debounce del autocompletado):
//   - Al elegir un Cliente del selector (por nombre) y capturar titulo y valor,
//     el POST /oportunidades viaja con { clienteId: <UUID>, titulo, valorEstimado }
//     y el Usuario nunca teclea el identificador.
//   - Si solo se teclea texto sin elegir un Cliente, el formulario es invalido y
//     NO se emite el POST.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';

import { ComercialOportunidades } from './oportunidades';
import { AuthService } from '../../../core/auth/auth.service';
import { Cliente, Oportunidad } from '../models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

/**
 * AuthService de prueba. Por defecto concede todos los permisos comerciales que
 * la vista consulta (oportunidad, cliente:leer y cotizacion:leer para los
 * enlaces). Los casos de gating instancian un stub con permisos acotados.
 */
class AuthServiceStub {
  constructor(private readonly permisos: string[] | null = null) {}
  tienePermiso(recurso: string, operacion: string): boolean {
    if (this.permisos) {
      return this.permisos.includes(`${recurso}:${operacion}`);
    }
    return recurso === 'oportunidad' || recurso === 'cliente' || recurso === 'cotizacion';
  }
}

/** Cliente de prueba (solo los campos que el selector consume). */
const CLIENTE = {
  id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  nombre: 'Acme',
  rfc: 'ABCD901231XYZ',
} as unknown as Cliente;

/** Superficie protegida del EntitySelect que las pruebas necesitan accionar. */
interface EntitySelectProbe {
  alEscribir(v: string): void;
  alSeleccionar(evento: { option: { value: Cliente } }): void;
}

/** Superficie protegida del componente que las pruebas necesitan accionar. */
interface OportunidadesProbe {
  alternarFormulario(): void;
  form: { patchValue(v: Record<string, unknown>): void };
  crear(): void;
  aplicarFiltroCanal(canalId: string): void;
  abrirAsignarCanal(o: Oportunidad): void;
  formCanal: { patchValue(v: Record<string, unknown>): void };
  guardarCanal(): void;
  canalObjetivo(): Oportunidad | null;
  puedeAsignarCanal: boolean;
}

/** Oportunidad de prueba (solo los campos que la vista consume). */
function oportunidadDto(over: Partial<Oportunidad> = {}): Oportunidad {
  return {
    id: 'op-1',
    clienteId: CLIENTE.id,
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
  } as Oportunidad;
}

describe('ComercialOportunidades', () => {
  let fixture: ComponentFixture<ComercialOportunidades>;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [ComercialOportunidades, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ComercialOportunidades);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  /** Canal de venta de prueba para el filtro y los selectores. */
  const CANAL = {
    id: 'cccccccc-1111-2222-3333-444444444444',
    nombre: 'Redes sociales',
  } as unknown as { id: string; nombre: string };

  /**
   * Resuelve la carga inicial: los canales (GET /canales-venta), el pipeline
   * (GET /oportunidades) y, si hay oportunidades, la resolucion de nombres de
   * cliente (GET /clientes/{id}) que alimenta los enlaces a la Ficha 360.
   */
  function resolverCargaInicial(items: Oportunidad[] = []): void {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === '/api/v1/canales-venta')
      .flush({ content: [CANAL], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    const req = http.expectOne((r) => r.url === '/api/v1/oportunidades');
    req.flush({ content: items, page: 0, size: 100, totalElements: items.length, totalPages: 1 });
    fixture.detectChanges();
    // Resuelve el nombre de cada cliente referido por las oportunidades visibles.
    const ids = [...new Set(items.map((o) => o.clienteId))];
    for (const id of ids) {
      const pendiente = http.match((r) => r.url === `/api/v1/clientes/${id}`);
      for (const p of pendiente) {
        p.flush(CLIENTE);
      }
    }
    fixture.detectChanges();
  }

  /** Localiza la instancia del EntitySelect del selector de Cliente. */
  function selectorCliente(): EntitySelectProbe {
    const debug = fixture.debugElement.query((n) => n.name === 'app-entity-select');
    return debug.componentInstance as unknown as EntitySelectProbe;
  }

  it('crea la oportunidad con el UUID del cliente elegido en el selector', () => {
    resolverCargaInicial();
    const componente = fixture.componentInstance as unknown as OportunidadesProbe;

    // Abre el formulario y elige un Cliente por nombre.
    componente.alternarFormulario();
    fixture.detectChanges();

    const selector = selectorCliente();
    selector.alEscribir('acm');
    // toObservable emite el nuevo valor del signal via un effect en la deteccion
    // de cambios; hay que propagarlo antes de vencer el debounce.
    fixture.detectChanges();
    vi.advanceTimersByTime(300);
    // Responde la busqueda de clientes del selector.
    const busqueda = http.expectOne((r) => r.url === '/api/v1/clientes');
    busqueda.flush({ content: [CLIENTE], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    fixture.detectChanges();

    selector.alSeleccionar({ option: { value: CLIENTE } });
    fixture.detectChanges();

    // Captura titulo y valor y envia.
    componente.form.patchValue({ titulo: 'Proyecto rotulos', valorEstimado: 1500 });
    fixture.detectChanges();
    componente.crear();

    const post = http.expectOne(
      (r) => r.method === 'POST' && r.url === '/api/v1/oportunidades',
    );
    expect(post.request.body).toEqual({
      clienteId: CLIENTE.id,
      titulo: 'Proyecto rotulos',
      valorEstimado: 1500,
    });
    post.flush({} as Oportunidad);
    // Recarga del pipeline tras el alta.
    http.expectOne((r) => r.url === '/api/v1/oportunidades').flush({
      content: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    });
  });

  it('no emite el POST si no se eligio un cliente (solo texto tecleado)', () => {
    resolverCargaInicial();
    const componente = fixture.componentInstance as unknown as OportunidadesProbe;

    componente.alternarFormulario();
    fixture.detectChanges();

    // Se completan titulo y valor pero NO se elige un cliente del selector.
    componente.form.patchValue({ titulo: 'Sin cliente', valorEstimado: 500 });
    fixture.detectChanges();
    componente.crear();

    // El formulario es invalido: no viaja ninguna peticion de alta.
    http.expectNone((r) => r.method === 'POST' && r.url === '/api/v1/oportunidades');
  });

  it('asigna el canal de venta invocando el endpoint con el canal elegido', () => {
    const op = oportunidadDto();
    resolverCargaInicial([op]);
    const componente = fixture.componentInstance as unknown as OportunidadesProbe;

    componente.abrirAsignarCanal(op);
    fixture.detectChanges();
    componente.formCanal.patchValue({ canalId: CANAL.id });
    componente.guardarCanal();

    const put = http.expectOne(
      (r) => r.method === 'PUT' && r.url === `/api/v1/oportunidades/${op.id}/canal-venta`,
    );
    expect(put.request.body).toEqual({ canalVentaId: CANAL.id });
    put.flush(oportunidadDto({ canalVentaId: CANAL.id }));
    fixture.detectChanges();
    // El dialogo se cierra tras guardar (no se recarga todo el pipeline).
    expect(componente.canalObjetivo()).toBeNull();
  });

  it('el filtro por canal recarga el pipeline pasando canalVentaId a listar', () => {
    resolverCargaInicial([oportunidadDto()]);
    const componente = fixture.componentInstance as unknown as OportunidadesProbe;

    componente.aplicarFiltroCanal(CANAL.id);
    const req = http.expectOne((r) => r.url.startsWith('/api/v1/oportunidades'));
    expect(req.request.params.get('canalVentaId')).toBe(CANAL.id);
    req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 1 });
  });

  it('muestra el cliente como enlace a su Ficha 360 y "Ver cotizacion" cuando ya fue convertida', () => {
    resolverCargaInicial([oportunidadDto({ cotizacionId: 'cot-9', etapa: 'ganado' })]);
    const host = fixture.nativeElement as HTMLElement;

    const enlaceCliente = host.querySelector(
      `a[href="/empresa/comercial/clientes/${CLIENTE.id}"]`,
    );
    expect(enlaceCliente).not.toBeNull();
    expect(enlaceCliente?.textContent).toContain('Acme');

    const enlaceCotizacion = host.querySelector('a[href="/empresa/comercial/cotizaciones/cot-9"]');
    expect(enlaceCotizacion).not.toBeNull();

    // Ningun enlace expone el UUID como texto visible.
    expect(host.textContent).not.toContain(CLIENTE.id);
  });

  it('sin permiso oportunidad:actualizar no ofrece la accion de asignar canal', () => {
    TestBed.resetTestingModule();
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [ComercialOportunidades, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: new AuthServiceStub(['oportunidad:listar', 'cliente:leer', 'cotizacion:leer']),
        },
      ],
    });
    fixture = TestBed.createComponent(ComercialOportunidades);
    http = TestBed.inject(HttpTestingController);
    resolverCargaInicial([oportunidadDto()]);
    const componente = fixture.componentInstance as unknown as OportunidadesProbe;
    expect(componente.puedeAsignarCanal).toBe(false);
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('button[aria-label^="Asignar canal de venta"]')).toBeNull();
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    resolverCargaInicial();
    (fixture.componentInstance as unknown as OportunidadesProbe).alternarFormulario();
    fixture.detectChanges();
    // axe usa temporizadores internos; se ejecuta con los reales.
    vi.useRealTimers();
    await esperarSinViolaciones(fixture);
  });
});
