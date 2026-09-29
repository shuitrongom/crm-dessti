// =============================================================================
// Pruebas de la vista de Productos (Req 59) — listado + acciones
// -----------------------------------------------------------------------------
// El alta/edicion ocurre en un modal (ProductoFormDialog, probado aparte). Aqui
// se verifica, sin red real:
//   - Carga inicial del listado (GET /productos?estado=activo) y render.
//   - nuevo()/editar() abren el modal (MatDialog.open) con los datos correctos y
//     recargan cuando el dialogo confirma.
//   - activar() y desactivar() llaman al servicio y recargan.
//   - El filtro por estado recarga pasando el estado.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { Observable, of } from 'rxjs';

import { ComercialProductos } from './productos';
import { ProductoFormDialog } from './producto-form-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { Producto } from '../models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

class AuthServiceStub {
  tienePermiso(recurso: string): boolean {
    return recurso === 'producto';
  }
}

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

class OverlayStub {
  ejecutar<T>(origen: Observable<T>): Observable<T> {
    return origen;
  }
}

class ConfirmStub {
  async confirmar(): Promise<boolean> {
    return true;
  }
}

class MatDialogStub {
  ultimoComponente: unknown = null;
  ultimaData: unknown = undefined;
  resultado: unknown = undefined;
  aperturas = 0;
  open(componente: unknown, config?: { data?: unknown }) {
    this.aperturas++;
    this.ultimoComponente = componente;
    this.ultimaData = config?.data;
    return { afterClosed: () => of(this.resultado) };
  }
}

function productoDe(over: Partial<Producto> = {}): Producto {
  return {
    id: 'p-1',
    nombre: 'Producto Demo',
    unidad: 'pieza',
    descripcion: 'desc',
    clienteMeta: null,
    alianzas: null,
    competencia: null,
    foto: null,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as Producto;
}

interface Probe {
  nuevo(): void;
  editar(p: Producto): void;
  activar(p: Producto): void;
  desactivar(p: Producto): Promise<void>;
  cambiarEstado(v: 'activo' | 'inactivo' | 'todos'): void;
}

describe('ComercialProductos', () => {
  let fixture: ComponentFixture<ComercialProductos>;
  let http: HttpTestingController;
  let dialog: MatDialogStub;
  let toast: ToastSpy;

  beforeEach(async () => {
    dialog = new MatDialogStub();
    toast = new ToastSpy();
    await TestBed.configureTestingModule({
      imports: [ComercialProductos, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: NotificacionesService, useValue: toast },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: ConfirmDialogService, useClass: ConfirmStub },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /**
   * Resuelve la carga inicial: la lista principal (estado=activo, size=20) y las
   * dos consultas de conteo (activo/inactivo, size=1). Devuelve totales de prueba.
   */
  function crear(contenido: Producto[] = [productoDe()]): void {
    fixture = TestBed.createComponent(ComercialProductos);
    fixture.detectChanges();
    const peticiones = http.match((r) => r.url === '/api/v1/productos' && r.method === 'GET');
    for (const req of peticiones) {
      const size = req.request.params.get('size');
      const estado = req.request.params.get('estado');
      if (size === '20') {
        expect(estado).toBe('activo');
        req.flush({
          content: contenido,
          totalElements: contenido.length,
          totalPages: 1,
          number: 0,
          size: 20,
        });
      } else {
        // Conteos (size=1): activo=20, inactivo=3 (valores de prueba).
        const total = estado === 'inactivo' ? 3 : 20;
        req.flush({ content: [], totalElements: total, totalPages: 1, number: 0, size: 1 });
      }
    }
    fixture.detectChanges();
  }

  /** Resuelve una recarga (lista + 2 conteos) tras una accion. */
  function resolverRecarga(): void {
    for (const req of http.match((r) => r.url === '/api/v1/productos' && r.method === 'GET')) {
      const size = req.request.params.get('size');
      req.flush({
        content: [],
        totalElements: size === '1' ? 0 : 0,
        totalPages: 0,
        number: 0,
        size: Number(size),
      });
    }
  }

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('carga el listado activo al iniciar y renderiza la tabla', () => {
    crear();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Producto Demo');
  });

  it('nuevo() abre el modal en modo alta (sin producto)', () => {
    crear();
    comp().nuevo();
    expect(dialog.aperturas).toBe(1);
    expect(dialog.ultimoComponente).toBe(ProductoFormDialog);
    expect((dialog.ultimaData as { producto?: Producto }).producto).toBeUndefined();
  });

  it('editar() abre el modal con el producto y recarga si confirma', () => {
    crear();
    dialog.resultado = productoDe();
    comp().editar(productoDe());
    expect(dialog.ultimoComponente).toBe(ProductoFormDialog);
    expect((dialog.ultimaData as { producto?: Producto }).producto?.id).toBe('p-1');
    resolverRecarga();
    expect(toast.exitos).toContain('Producto actualizado.');
  });

  it('desactivar() confirma, llama DELETE y recarga', async () => {
    crear();
    await comp().desactivar(productoDe());
    http.expectOne((r) => r.url === '/api/v1/productos/p-1' && r.method === 'DELETE').flush(
      productoDe({ activo: false }),
    );
    resolverRecarga();
    expect(toast.exitos).toContain('Producto desactivado.');
  });

  it('activar() llama PUT /activar y recarga', () => {
    crear([productoDe({ activo: false })]);
    comp().activar(productoDe({ activo: false }));
    http.expectOne((r) => r.url === '/api/v1/productos/p-1/activar' && r.method === 'PUT').flush(
      productoDe({ activo: true }),
    );
    resolverRecarga();
    expect(toast.exitos).toContain('Producto activado.');
  });

  it('el filtro por estado recarga pasando el estado seleccionado', () => {
    crear();
    comp().cambiarEstado('inactivo');
    const req = http.expectOne((r) => r.url === '/api/v1/productos' && r.method === 'GET');
    expect(req.request.params.get('estado')).toBe('inactivo');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    crear();
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
