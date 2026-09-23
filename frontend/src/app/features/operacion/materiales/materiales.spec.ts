// =============================================================================
// Pruebas de la vista Materiales / inventario base (tarea 5.2)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Estado de carga -> ok: al resolver GET /materiales con contenido, la tabla
//     muestra los materiales por NOMBRE (Req 12.1).
//   - Estado vacio: content vacio -> fase 'vacio' con mensajeVacio (Req 12.4).
//   - Estado error: GET /materiales con error -> fase 'error' con mensaje (Req 12.5).
//   - Sin UUIDs en el DOM: el id del Material nunca se renderiza (Req 13.2).
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom) (Req 15.2).
//   - Crear material hace POST /materiales; registrar movimiento hace
//     POST /materiales/{id}/movimientos con el cuerpo esperado.
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';

import { OperacionMateriales } from './materiales';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Material, MovimientoInventario, TipoMovimiento } from '../models/operacion.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

/** AuthService de prueba con permisos configurables. */
class AuthServiceStub {
  permisos = new Set<string>([
    'material:crear',
    'material:listar',
    'material:eliminar',
    'movimiento_inventario:crear',
  ]);
  tienePermiso(recurso: string, operacion: string): boolean {
    return this.permisos.has(`${recurso}:${operacion}`);
  }
}

/** Espia de notificaciones. */
class ToastSpy {
  exitos: string[] = [];
  errores: string[] = [];
  exito(m: string): void {
    this.exitos.push(m);
  }
  error(m: string): void {
    this.errores.push(m);
  }
  info(): void {}
}

const MATERIAL_UUID = 'b0000000-0000-0000-0000-000000000001';
const MATERIAL2_UUID = 'b0000000-0000-0000-0000-000000000002';

function materialFalso(overrides: Partial<Material> = {}): Material {
  return {
    id: MATERIAL_UUID,
    nombre: 'Lamina acrilica',
    unidadMedida: 'm2',
    stockMinimo: 10,
    existencias: 5,
    stockBajo: true,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

interface VistaTest {
  form: { setValue(v: Record<string, unknown>): void };
  formMovimiento: { setValue(v: Record<string, unknown>): void };
  alternarFormulario(): void;
  crear(): void;
  abrirMovimiento(m: Material): void;
  registrarMovimiento(): void;
  fase(): string;
}

describe('OperacionMateriales', () => {
  let fixture: ComponentFixture<OperacionMateriales>;
  let http: HttpTestingController;
  let toast: ToastSpy;
  let auth: AuthServiceStub;

  const URL_MATERIALES = '/api/v1/materiales';

  function pagina<T>(content: T[]) {
    return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
  }

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [OperacionMateriales, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: toast },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OperacionMateriales);
  }

  /**
   * Resuelve la peticion de arranque: el listado inicial de Materiales
   * (GET /api/v1/materiales). El componente solo dispara esta peticion al init.
   */
  function resolverArranque(materiales: Material[] = [materialFalso()]): void {
    fixture.detectChanges();
    http.expectOne((r) => r.method === 'GET' && r.url === URL_MATERIALES).flush(pagina(materiales));
    fixture.detectChanges();
  }

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function vista(): VistaTest {
    return fixture.componentInstance as unknown as VistaTest;
  }

  beforeEach(() => {
    toast = new ToastSpy();
    auth = new AuthServiceStub();
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  it('estado de carga -> ok: la tabla muestra los materiales por NOMBRE', async () => {
    await crear();
    resolverArranque([
      materialFalso(),
      materialFalso({ id: MATERIAL2_UUID, nombre: 'Vinil adhesivo', unidadMedida: 'ml', stockBajo: false }),
    ]);

    expect(vista().fase()).toBe('ok');
    const t = texto();
    expect(t).toContain('Lamina acrilica');
    expect(t).toContain('Vinil adhesivo');
  });

  it('estado vacio: content vacio -> fase "vacio" con el mensaje descriptivo', async () => {
    await crear();
    resolverArranque([]);

    expect(vista().fase()).toBe('vacio');
    expect(texto()).toContain('No hay materiales para los filtros seleccionados.');
  });

  it('estado error: GET /materiales con error -> fase "error" con mensaje', async () => {
    await crear();
    fixture.detectChanges();
    http
      .expectOne((r) => r.method === 'GET' && r.url === URL_MATERIALES)
      .flush(
        { detail: 'No fue posible cargar los materiales.' },
        { status: 500, statusText: 'Internal Server Error' },
      );
    fixture.detectChanges();

    expect(vista().fase()).toBe('error');
    expect(texto()).toContain('No fue posible cargar los materiales.');
  });

  it('no expone ningun UUID en el DOM (usa nombre/unidad)', async () => {
    await crear();
    resolverArranque([materialFalso()]);

    const t = texto();
    expect(t).toContain('Lamina acrilica');
    expect(t).toContain('m2');
    expect(t).not.toContain(MATERIAL_UUID);
  });

  it('crear material hace POST /materiales con el cuerpo esperado', async () => {
    await crear();
    resolverArranque([materialFalso()]);

    vista().alternarFormulario();
    fixture.detectChanges();
    vista().form.setValue({ nombre: '  Lona banner  ', unidadMedida: '  m2  ', stockMinimo: 15 });
    vista().crear();

    const post = http.expectOne((r) => r.method === 'POST' && r.url === URL_MATERIALES);
    expect(post.request.body).toEqual({ nombre: 'Lona banner', unidadMedida: 'm2', stockMinimo: 15 });
    post.flush(materialFalso({ id: MATERIAL2_UUID, nombre: 'Lona banner', unidadMedida: 'm2' }));

    // Tras el exito recarga el listado.
    http.expectOne((r) => r.method === 'GET' && r.url === URL_MATERIALES).flush(pagina([materialFalso()]));
    expect(toast.exitos).toContain('Material creado.');
  });

  it('registrar movimiento hace POST /materiales/{id}/movimientos con el cuerpo esperado', async () => {
    await crear();
    resolverArranque([materialFalso()]);

    vista().abrirMovimiento(materialFalso());
    fixture.detectChanges();
    vista().formMovimiento.setValue({
      tipo: 'entrada' as TipoMovimiento,
      cantidad: 12,
      motivo: '  Compra inicial  ',
    });
    vista().registrarMovimiento();

    const post = http.expectOne(
      (r) => r.method === 'POST' && r.url === `${URL_MATERIALES}/${MATERIAL_UUID}/movimientos`,
    );
    expect(post.request.body).toEqual({ tipo: 'entrada', cantidad: 12, motivo: 'Compra inicial' });
    post.flush({} as MovimientoInventario);

    // Tras el exito recarga el listado.
    http.expectOne((r) => r.method === 'GET' && r.url === URL_MATERIALES).flush(pagina([materialFalso()]));
    expect(toast.exitos).toContain('Movimiento registrado.');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await crear();
    resolverArranque([materialFalso()]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
