// =============================================================================
// Pruebas del dialogo AsignarPrecioDialog (Req 59.3, 59.10)
// -----------------------------------------------------------------------------
// Verifican, sin red real (fake timers para el debounce del entity-select):
//   - Al abrir, carga los precios ya asignados (GET /listas-precios/{id}/precios).
//   - Asigna el precio del producto elegido (PUT /listas-precios/{id}/precios) y
//     recarga la lista; cerrar() devuelve true si hubo cambios.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { AsignarPrecioDialog, AsignarPrecioDialogData } from './asignar-precio-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { ListaPrecios, Producto } from '../models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

class OverlayStub {
  ejecutar<T>(origen: Observable<T>): Observable<T> {
    return origen;
  }
}

class ToastSpy {
  exito(): void {}
  error(): void {}
  info(): void {}
}

const PRODUCTO = { id: 'p-1', nombre: 'Producto Demo', unidad: 'pieza' } as unknown as Producto;

function listaDe(): ListaPrecios {
  return {
    id: 'lp-1',
    nombre: 'Lista general demo',
    prioridad: 1,
    segmento: null,
    vigenciaInicio: '2026-01-01',
    vigenciaFin: null,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  } as ListaPrecios;
}

interface EntitySelectProbe {
  alEscribir(v: string): void;
  alSeleccionar(evento: { option: { value: Producto } }): void;
}

interface Probe {
  form: { patchValue(v: Record<string, unknown>): void };
  asignar(): void;
  cerrar(): void;
  precios(): unknown[];
}

describe('AsignarPrecioDialog', () => {
  let fixture: ComponentFixture<AsignarPrecioDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  const URL_PRECIOS = '/api/v1/listas-precios/lp-1/precios';

  beforeEach(async () => {
    vi.useFakeTimers();
    dialogRef = new DialogRefStub();
    const data: AsignarPrecioDialogData = { lista: listaDe() };
    await TestBed.configureTestingModule({
      imports: [AsignarPrecioDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: NotificacionesService, useValue: new ToastSpy() },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AsignarPrecioDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    // Carga inicial de precios ya asignados.
    http.expectOne((r) => r.url === URL_PRECIOS && r.method === 'GET').flush([]);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  function selectorProducto(): EntitySelectProbe {
    const debug = fixture.debugElement.query((n) => n.name === 'app-entity-select');
    return debug.componentInstance as unknown as EntitySelectProbe;
  }

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('asigna el precio del producto elegido (PUT) y cerrar() devuelve true', () => {
    const selector = selectorProducto();
    selector.alEscribir('prod');
    fixture.detectChanges();
    vi.advanceTimersByTime(300);
    http
      .expectOne((r) => r.url === '/api/v1/productos')
      .flush({ content: [PRODUCTO], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    fixture.detectChanges();
    selector.alSeleccionar({ option: { value: PRODUCTO } });
    fixture.detectChanges();

    comp().form.patchValue({ precio: 1500 });
    comp().asignar();

    const put = http.expectOne((r) => r.method === 'PUT' && r.url === URL_PRECIOS);
    expect(put.request.body).toEqual({ productoId: PRODUCTO.id, precio: 1500 });
    put.flush({ id: 'pp-1', listaPreciosId: 'lp-1', productoId: PRODUCTO.id, precio: 1500, version: 0 });
    // Tras asignar, recarga los precios de la lista.
    http.expectOne((r) => r.url === URL_PRECIOS && r.method === 'GET').flush([
      { productoId: PRODUCTO.id, productoNombre: 'Producto Demo', precio: 1500 },
    ]);
    fixture.detectChanges();

    comp().cerrar();
    expect(dialogRef.cerradoCon).toBe(true);
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    vi.useRealTimers();
    await esperarSinViolaciones(fixture);
  });
});
