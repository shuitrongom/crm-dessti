// =============================================================================
// Pruebas de la vista Materiales / inventario base (premium)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Estado de carga -> ok: la tabla muestra los materiales por NOMBRE (Req 12.1).
//   - Estado vacio: content vacio -> fase 'vacio' con mensajeVacio (Req 12.4).
//   - Estado error: GET /materiales con error -> fase 'error' con mensaje (Req 12.5).
//   - Sin UUIDs en el DOM: el id del Material nunca se renderiza (Req 13.2).
//   - El alta/edicion y el movimiento se hacen por MODAL (MatDialog): "Nuevo
//     material" abre MaterialFormDialog; al cerrarse con resultado se recarga.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom) (Req 15.2).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';
import { of } from 'rxjs';

import { OperacionMateriales } from './materiales';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Material } from '../models/operacion.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

/** AuthService de prueba con permisos configurables. */
class AuthServiceStub {
  permisos = new Set<string>([
    'material:crear',
    'material:actualizar',
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

/** Stub de MatDialog: registra la última apertura y devuelve un resultado fijo. */
class MatDialogStub {
  abierto = 0;
  resultado: unknown = undefined;
  open() {
    this.abierto += 1;
    return { afterClosed: () => of(this.resultado) };
  }
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
  nuevo(): void;
  fase(): string;
}

describe('OperacionMateriales', () => {
  let fixture: ComponentFixture<OperacionMateriales>;
  let http: HttpTestingController;
  let toast: ToastSpy;
  let auth: AuthServiceStub;
  let dialog: MatDialogStub;

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
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OperacionMateriales);
  }

  /**
   * Resuelve las peticiones de arranque: el listado principal (GET /materiales) y
   * los dos conteos de KPIs (GET /materiales?estado=activo|inactivo, size=1). Los
   * conteos se resuelven de forma laxa (match por URL) sin exigir orden.
   */
  function resolverArranque(materiales: Material[] = [materialFalso()]): void {
    fixture.detectChanges();
    // Los tres GET van a la misma URL base; se resuelven todos con una página.
    for (const req of http.match((r) => r.method === 'GET' && r.url === URL_MATERIALES)) {
      req.flush(pagina(materiales));
    }
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
    dialog = new MatDialogStub();
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
    // El primer GET es el listado principal; los conteos también fallan pero no
    // afectan la fase de la tabla. Se resuelven todos para no dejar pendientes.
    const reqs = http.match((r) => r.method === 'GET' && r.url === URL_MATERIALES);
    reqs[0].flush(
      { detail: 'No fue posible cargar los materiales.' },
      { status: 500, statusText: 'Internal Server Error' },
    );
    for (const r of reqs.slice(1)) {
      r.flush(pagina([]));
    }
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

  it('"Nuevo material" abre el modal y recarga al confirmar', async () => {
    dialog.resultado = materialFalso({ id: MATERIAL2_UUID, nombre: 'Lona banner' });
    await crear();
    resolverArranque([materialFalso()]);

    vista().nuevo();

    expect(dialog.abierto).toBe(1);
    // Al cerrarse con un Material, recarga listado + conteos (GET /materiales).
    for (const req of http.match((r) => r.method === 'GET' && r.url === URL_MATERIALES)) {
      req.flush(pagina([materialFalso()]));
    }
    expect(toast.exitos).toContain('Material creado.');
  });

  it('muestra "Sin existencia" (no "Stock bajo") cuando las existencias son 0', async () => {
    await crear();
    // existencias 0: aunque el backend marque stockBajo, la UI debe distinguir la
    // ausencia total de existencias con su propio estado (mas grave que stock bajo).
    resolverArranque([materialFalso({ existencias: 0, stockBajo: true })]);

    const t = texto();
    expect(t).toContain('Sin existencia');
    expect(t).not.toContain('Stock bajo');
  });

  it('muestra "Stock bajo" cuando hay existencias pero por debajo del minimo', async () => {
    await crear();
    resolverArranque([materialFalso({ existencias: 3, stockBajo: true })]);

    const t = texto();
    expect(t).toContain('Stock bajo');
    expect(t).not.toContain('Sin existencia');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await crear();
    resolverArranque([materialFalso()]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
