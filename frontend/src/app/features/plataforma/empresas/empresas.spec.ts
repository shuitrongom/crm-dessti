// =============================================================================
// Pruebas de la vista PlataformaEmpresas: render, busqueda con debounce y
// accesibilidad (Req 24, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless: se usan
// los temporizadores falsos de Vitest en lugar de fakeAsync):
//   - Render del encabezado, KPIs y tabla con las empresas devueltas.
//   - La celda de empresa muestra RFC y nombre comercial; la de contacto el
//     correo/telefono.
//   - La busqueda con debounce (300ms) recarga con el parametro `q` y reinicia a
//     la pagina 0; limpiarla vuelve a cargar sin `q`.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { PlataformaEmpresas } from './empresas';
import { AuthService } from '../../../core/auth/auth.service';
import { Empresa } from '../models/plataforma.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

/**
 * AuthService de prueba: super_admin con todos los permisos de empresa.
 *
 * `tieneAlgunPermiso(...)` respeta la firma real del AuthService (varargs de
 * cadenas "recurso:operacion" y booleano directo). Por defecto concede la
 * lectura de suscripciones (`suscripcion:leer`/`suscripcion:listar`), de modo
 * que la columna "Plan" aparece; para la prueba de gating se puede construir el
 * stub con `permiteSuscripcion = false` y ocultarla (Req 10.4).
 */
class AuthServiceStub {
  constructor(public permiteSuscripcion = true) {}

  tienePermiso(recurso: string, _operacion: string): boolean {
    return recurso === 'empresa';
  }

  tieneAlgunPermiso(...permisos: string[]): boolean {
    if (!this.permiteSuscripcion) {
      return false;
    }
    return permisos.some((p) => p === 'suscripcion:leer' || p === 'suscripcion:listar');
  }
}

function empresa(parcial: Partial<Empresa> = {}): Empresa {
  return {
    id: 'e1',
    nombre: 'Acme',
    rfc: 'ABCD901231XYZ',
    giroId: 'g1',
    estado: 'activa',
    brandingNombreVisible: null,
    brandingLogo: null,
    nombreComercial: null,
    emailContacto: null,
    telefono: null,
    sitioWeb: null,
    direccion: null,
    notas: null,
    fechaCancelacion: null,
    finPeriodoGracia: null,
    planVigente: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...parcial,
  };
}

/** Responde una peticion de lista (size 20) con las filas dadas. */
function flushLista(req: TestRequest, filas: Empresa[]): void {
  req.flush({ content: filas, page: 0, size: 20, totalElements: filas.length, totalPages: 1 });
}

interface ComponenteBusqueda {
  cambiarBusqueda(t: string): void;
  limpiarBusqueda(): void;
}

describe('PlataformaEmpresas', () => {
  let fixture: ComponentFixture<PlataformaEmpresas>;
  let fixtureCreado = false;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers();
    // Solo se compila el modulo aqui; la creacion del componente se difiere a
    // `crearFixture(...)` para poder elegir el AuthService por prueba (gobierna
    // `puedeVerPlan`, que la vista resuelve en su constructor). El provider por
    // defecto usa el stub con permiso de suscripcion; se puede sustituir con
    // `TestBed.overrideProvider` ANTES de instanciar el modulo.
    await TestBed.configureTestingModule({
      imports: [PlataformaEmpresas, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: new AuthServiceStub() },
      ],
    }).compileComponents();
    fixtureCreado = false;
    http = TestBed.inject(HttpTestingController);
  });

  /**
   * Crea el fixture (instancia la vista). Con `permiteSuscripcion = false`
   * sustituye el AuthService por un stub sin permiso de lectura de suscripciones
   * para ejercitar el gating de la columna "Plan" (Req 10.4). Idempotente: si el
   * fixture ya existe, no lo recrea.
   */
  function crearFixture(permiteSuscripcion = true): void {
    if (fixtureCreado) {
      return;
    }
    // Se ajusta la instancia del stub YA provista (no se sustituye el provider,
    // que fallaria porque el modulo ya esta instanciado). La vista resuelve
    // `puedeVerPlan` en su constructor, asi que el valor debe fijarse ANTES de
    // crear el componente.
    (TestBed.inject(AuthService) as unknown as AuthServiceStub).permiteSuscripcion =
      permiteSuscripcion;
    fixture = TestBed.createComponent(PlataformaEmpresas);
    fixtureCreado = true;
  }

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  /**
   * Resuelve la carga inicial: la lista (size 20) y los cuatro conteos KPI
   * (size 1). El debounce de la busqueda emite el valor inicial vacio tras 300ms
   * pero, al no cambiar el termino, no genera una peticion nueva.
   */
  function resolverCargaInicial(filas: Empresa[], giros: { id: string; nombreVisible: string }[] = []): void {
    crearFixture();
    fixture.detectChanges();
    const peticiones = http.match((r) => r.url === '/api/v1/empresas');
    const lista = peticiones.find((r) => r.request.params.get('size') === '20')!;
    flushLista(lista, filas);
    for (const r of peticiones.filter((p) => p.request.params.get('size') === '1')) {
      r.flush({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 });
    }
    // La vista carga una vez el catalogo de giros activos (para resolver el
    // nombre del giro y el selector de "Cambiar giro"): se atiende su peticion.
    const giro = http.expectOne((r) => r.url === '/api/v1/plataforma/giros');
    giro.flush({ content: giros, page: 0, size: 100, totalElements: giros.length, totalPages: 1 });
    // Vence el debounce inicial (valor vacio -> sin cambio -> sin nueva peticion).
    vi.advanceTimersByTime(300);
    fixture.detectChanges();
  }

  it('renderiza el encabezado, los KPIs y la tabla con RFC, nombre comercial y contacto', () => {
    resolverCargaInicial([empresa({ nombreComercial: 'Acme Signs', emailContacto: 'hola@acme.test' })]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Empresas');
    expect(texto).toContain('Empresas totales');
    expect(texto).toContain('Acme');
    expect(texto).toContain('ABCD901231XYZ');
    expect(texto).toContain('Acme Signs');
    expect(texto).toContain('hola@acme.test');
  });

  it('muestra el nombre del giro y nunca el UUID', () => {
    resolverCargaInicial(
      [empresa({ giroId: 'g1' })],
      [{ id: 'g1', nombreVisible: 'Rotulacion' }],
    );
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Rotulacion');
    expect(texto).not.toContain('g1');
  });

  it('el giro no resuelto cae a un marcador neutro (nunca el UUID)', () => {
    resolverCargaInicial([empresa({ giroId: 'desconocido' })], []);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('(sin giro)');
    expect(texto).not.toContain('desconocido');
  });

  it('muestra el nombre del plan vigente y una etiqueta de estado (nunca el UUID)', () => {
    resolverCargaInicial([
      empresa({
        planVigente: {
          nombrePlan: 'Plan Pro',
          estado: 'activa',
          planId: 'p-uuid',
          suscripcionId: 's-uuid',
          vigenciaInicio: '2026-01-01',
          vigenciaFin: null,
          tipoInstrumento: 'plan',
          nombreInstrumento: 'Plan Pro',
          paqueteSuscripcionId: null,
          diasRestantes: null,
          enPrueba: false,
          vencida: false,
          porVencer: false,
        },
      }),
    ]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Plan Pro');
    expect(texto).toContain('Activa');
    expect(texto).not.toContain('p-uuid');
    expect(texto).not.toContain('s-uuid');
  });

  it("muestra 'Sin plan' cuando la empresa no tiene plan vigente", () => {
    resolverCargaInicial([empresa({ planVigente: null })]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Sin plan');
  });

  it('oculta la columna Plan cuando falta el permiso de lectura de suscripciones', () => {
    // Crea el fixture con un AuthService sin permiso de suscripcion ANTES de la
    // primera deteccion de cambios (asi la vista resuelve `puedeVerPlan=false`).
    crearFixture(false);
    resolverCargaInicial([
      empresa({
        planVigente: {
          nombrePlan: 'Plan Pro',
          estado: 'activa',
          planId: 'p-uuid',
          suscripcionId: 's-uuid',
          vigenciaInicio: '2026-01-01',
          vigenciaFin: null,
          tipoInstrumento: 'plan',
          nombreInstrumento: 'Plan Pro',
          paqueteSuscripcionId: null,
          diasRestantes: null,
          enPrueba: false,
          vencida: false,
          porVencer: false,
        },
      }),
    ]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    // Sin permiso la columna no existe: ni su encabezado ni el nombre del plan.
    expect(texto).not.toContain('Plan Pro');
    const encabezados = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('th'),
    ).map((th) => th.textContent?.trim());
    expect(encabezados).not.toContain('Plan');
  });

  it('la busqueda con debounce recarga con q y reinicia a la pagina 0', () => {
    resolverCargaInicial([empresa()]);

    const componente = fixture.componentInstance as unknown as ComponenteBusqueda;
    componente.cambiarBusqueda('acme');
    fixture.detectChanges();
    // Antes del debounce no hay peticion nueva.
    http.expectNone((r) => r.url === '/api/v1/empresas');
    vi.advanceTimersByTime(300);

    const req = http.expectOne((r) => r.url === '/api/v1/empresas' && r.params.get('q') === 'acme');
    expect(req.request.params.get('page')).toBe('0');
    flushLista(req, []);
    fixture.detectChanges();
  });

  it('limpiar la busqueda recarga sin el parametro q', () => {
    resolverCargaInicial([empresa()]);

    const componente = fixture.componentInstance as unknown as ComponenteBusqueda;
    componente.cambiarBusqueda('acme');
    fixture.detectChanges();
    vi.advanceTimersByTime(300);
    flushLista(
      http.expectOne((r) => r.url === '/api/v1/empresas' && r.params.get('q') === 'acme'),
      [],
    );

    componente.limpiarBusqueda();
    fixture.detectChanges();
    vi.advanceTimersByTime(300);
    const req = http.expectOne((r) => r.url === '/api/v1/empresas');
    expect(req.request.params.has('q')).toBe(false);
    flushLista(req, [empresa()]);
    fixture.detectChanges();
  });

  // ===========================================================================
  // Columna "Plan" del listado: nombreInstrumento, chip de tipo, estado derivado
  // (En prueba / Vencida) y aviso "Por vencer" con dias restantes (Req 1.4, 7.2).
  // El AuthServiceStub concede suscripcion por defecto, asi que la columna
  // renderiza; se lee el DOM completo via textContent.
  // ---------------------------------------------------------------------------

  it('muestra nombreInstrumento (no nombrePlan) en la columna Plan', () => {
    // nombreInstrumento es autoritativo: aunque el plan legado se llame "Viejo",
    // la celda debe mostrar el nombre del instrumento vigente resuelto por el
    // backend.
    resolverCargaInicial(
      [
        empresa({
          planVigente: {
            nombrePlan: 'Viejo',
            estado: 'activa',
            planId: 'p-uuid',
            suscripcionId: 's-uuid',
            vigenciaInicio: '2026-01-01',
            vigenciaFin: null,
            tipoInstrumento: 'suscripcion',
            nombreInstrumento: 'Suscripción Mensual',
            paqueteSuscripcionId: 'pq-uuid',
            diasRestantes: null,
            enPrueba: false,
            vencida: false,
            porVencer: false,
          },
        }),
      ],
      [{ id: 'g1', nombreVisible: 'Rotulacion' }],
    );
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Suscripción Mensual');
    expect(texto).not.toContain('Viejo');
  });

  it("muestra el chip de tipo 'Suscripción' para un instrumento de suscripcion", () => {
    resolverCargaInicial(
      [
        empresa({
          planVigente: {
            nombrePlan: 'Paquete Base',
            estado: 'activa',
            planId: 'p-uuid',
            suscripcionId: 's-uuid',
            vigenciaInicio: '2026-01-01',
            vigenciaFin: null,
            tipoInstrumento: 'suscripcion',
            nombreInstrumento: 'Paquete Base',
            paqueteSuscripcionId: 'pq-uuid',
            diasRestantes: null,
            enPrueba: false,
            vencida: false,
            porVencer: false,
          },
        }),
      ],
      [{ id: 'g1', nombreVisible: 'Rotulacion' }],
    );
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Suscripción');
  });

  it("muestra el chip de tipo 'Plan' para un instrumento de plan", () => {
    resolverCargaInicial(
      [
        empresa({
          planVigente: {
            nombrePlan: 'Plan Pro',
            estado: 'activa',
            planId: 'p-uuid',
            suscripcionId: 's-uuid',
            vigenciaInicio: '2026-01-01',
            vigenciaFin: null,
            tipoInstrumento: 'plan',
            nombreInstrumento: 'Plan Pro',
            paqueteSuscripcionId: null,
            diasRestantes: null,
            enPrueba: false,
            vencida: false,
            porVencer: false,
          },
        }),
      ],
      [{ id: 'g1', nombreVisible: 'Rotulacion' }],
    );
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    // El chip de tipo muestra "Plan" y el estado "Activa".
    expect(texto).toContain('Plan');
    expect(texto).toContain('Activa');
  });

  it("muestra 'En prueba' en el estado cuando enPrueba es true", () => {
    resolverCargaInicial(
      [
        empresa({
          planVigente: {
            nombrePlan: 'Paquete Base',
            estado: 'en_prueba',
            planId: 'p-uuid',
            suscripcionId: 's-uuid',
            vigenciaInicio: '2026-01-01',
            vigenciaFin: '2026-02-01',
            tipoInstrumento: 'suscripcion',
            nombreInstrumento: 'Paquete Base',
            paqueteSuscripcionId: 'pq-uuid',
            diasRestantes: null,
            enPrueba: true,
            vencida: false,
            porVencer: false,
          },
        }),
      ],
      [{ id: 'g1', nombreVisible: 'Rotulacion' }],
    );
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('En prueba');
  });

  it("muestra 'Vencida' en el estado cuando vencida es true", () => {
    resolverCargaInicial(
      [
        empresa({
          planVigente: {
            nombrePlan: 'Plan Pro',
            estado: 'activa',
            planId: 'p-uuid',
            suscripcionId: 's-uuid',
            vigenciaInicio: '2025-01-01',
            vigenciaFin: '2025-12-31',
            tipoInstrumento: 'plan',
            nombreInstrumento: 'Plan Pro',
            paqueteSuscripcionId: null,
            diasRestantes: -3,
            enPrueba: false,
            vencida: true,
            porVencer: false,
          },
        }),
      ],
      [{ id: 'g1', nombreVisible: 'Rotulacion' }],
    );
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Vencida');
  });

  it("muestra el aviso 'Por vencer' y los dias restantes cuando aplica", () => {
    resolverCargaInicial(
      [
        empresa({
          planVigente: {
            nombrePlan: 'Plan Pro',
            estado: 'activa',
            planId: 'p-uuid',
            suscripcionId: 's-uuid',
            vigenciaInicio: '2026-01-01',
            vigenciaFin: '2026-01-06',
            tipoInstrumento: 'plan',
            nombreInstrumento: 'Plan Pro',
            paqueteSuscripcionId: null,
            diasRestantes: 5,
            enPrueba: false,
            vencida: false,
            porVencer: true,
          },
        }),
      ],
      [{ id: 'g1', nombreVisible: 'Rotulacion' }],
    );
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Por vencer');
    expect(texto).toContain('5');
    expect(texto).toContain('días');
  });

  it("muestra 'Vence hoy' cuando quedan cero dias restantes", () => {
    resolverCargaInicial(
      [
        empresa({
          planVigente: {
            nombrePlan: 'Plan Pro',
            estado: 'activa',
            planId: 'p-uuid',
            suscripcionId: 's-uuid',
            vigenciaInicio: '2026-01-01',
            vigenciaFin: '2026-01-01',
            tipoInstrumento: 'plan',
            nombreInstrumento: 'Plan Pro',
            paqueteSuscripcionId: null,
            diasRestantes: 0,
            enPrueba: false,
            vencida: false,
            porVencer: true,
          },
        }),
      ],
      [{ id: 'g1', nombreVisible: 'Rotulacion' }],
    );
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Vence hoy');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    resolverCargaInicial([empresa({ emailContacto: 'hola@acme.test' })]);
    // axe usa temporizadores internos; se ejecuta con los reales.
    vi.useRealTimers();
    await esperarSinViolaciones(fixture);
  });
});
