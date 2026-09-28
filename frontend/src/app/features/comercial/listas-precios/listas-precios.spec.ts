// =============================================================================
// Pruebas de la vista de Listas de precios (Req 59) — listado + acciones
// -----------------------------------------------------------------------------
// El alta/edicion y la asignacion de precios ocurren en modales (probados
// aparte). Aqui se verifica, sin red real:
//   - Carga inicial del listado (GET /listas-precios) y render.
//   - nuevo()/editar() abren ListaFormDialog con los datos correctos y recargan.
//   - abrirAsignarPrecio() abre AsignarPrecioDialog con la lista.
//   - eliminar() confirma, hace DELETE y recarga.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { ComercialListasPrecios } from './listas-precios';
import { ListaFormDialog } from './lista-form-dialog';
import { AsignarPrecioDialog } from './asignar-precio-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { ListaPrecios } from '../models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

class AuthServiceStub {
  tienePermiso(): boolean {
    return true;
  }
}

class ToastSpy {
  exitos: string[] = [];
  exito(m: string): void {
    this.exitos.push(m);
  }
  error(): void {}
  info(): void {}
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

function listaDe(over: Partial<ListaPrecios> = {}): ListaPrecios {
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
    ...over,
  } as ListaPrecios;
}

interface Probe {
  nuevo(): void;
  editar(l: ListaPrecios): void;
  abrirAsignarPrecio(l: ListaPrecios): void;
  eliminar(l: ListaPrecios): Promise<void>;
}

describe('ComercialListasPrecios', () => {
  let fixture: ComponentFixture<ComercialListasPrecios>;
  let http: HttpTestingController;
  let dialog: MatDialogStub;
  let toast: ToastSpy;

  beforeEach(async () => {
    dialog = new MatDialogStub();
    toast = new ToastSpy();
    await TestBed.configureTestingModule({
      imports: [ComercialListasPrecios, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: NotificacionesService, useValue: toast },
        { provide: ConfirmDialogService, useClass: ConfirmStub },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function crear(contenido: ListaPrecios[] = [listaDe()]): void {
    fixture = TestBed.createComponent(ComercialListasPrecios);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/listas-precios' && r.method === 'GET').flush({
      content: contenido,
      totalElements: contenido.length,
      totalPages: 1,
      number: 0,
      size: 20,
    });
    fixture.detectChanges();
  }

  function resolverRecarga(): void {
    http.expectOne((r) => r.url === '/api/v1/listas-precios' && r.method === 'GET').flush({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    });
  }

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('carga el listado y renderiza la tabla', () => {
    crear();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Lista general demo');
  });

  it('nuevo() abre ListaFormDialog en modo alta (sin lista)', () => {
    crear();
    comp().nuevo();
    expect(dialog.aperturas).toBe(1);
    expect(dialog.ultimoComponente).toBe(ListaFormDialog);
    expect((dialog.ultimaData as { lista?: ListaPrecios }).lista).toBeUndefined();
  });

  it('editar() abre ListaFormDialog con la lista y recarga si confirma', () => {
    crear();
    dialog.resultado = listaDe();
    comp().editar(listaDe());
    expect(dialog.ultimoComponente).toBe(ListaFormDialog);
    expect((dialog.ultimaData as { lista?: ListaPrecios }).lista?.id).toBe('lp-1');
    resolverRecarga();
    expect(toast.exitos).toContain('Lista actualizada.');
  });

  it('abrirAsignarPrecio() abre AsignarPrecioDialog con la lista', () => {
    crear();
    dialog.resultado = false;
    comp().abrirAsignarPrecio(listaDe());
    expect(dialog.ultimoComponente).toBe(AsignarPrecioDialog);
    expect((dialog.ultimaData as { lista: ListaPrecios }).lista.id).toBe('lp-1');
  });

  it('eliminar() confirma, hace DELETE y recarga', async () => {
    crear();
    await comp().eliminar(listaDe());
    http.expectOne((r) => r.url === '/api/v1/listas-precios/lp-1' && r.method === 'DELETE').flush(
      listaDe({ activo: false }),
    );
    resolverRecarga();
    expect(toast.exitos).toContain('Lista dada de baja.');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    crear();
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
