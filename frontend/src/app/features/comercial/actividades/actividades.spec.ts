// =============================================================================
// Pruebas de la agenda ComercialActividades (V79) — completar y KPIs reales
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Al cargar, los KPIs (Pendientes/Vencidas/Completadas) se leen de los
//     totalElements de consultas de conteo por estado (no del recuento de la
//     pagina).
//   - "Completar" hace PUT /actividades/{id}/completar, quita la fila de la
//     lista (filtro por defecto = pendiente) y decrementa el KPI de pendientes
//     de inmediato (actualizacion optimista).
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { ComercialActividades } from './actividades';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { Actividad } from '../models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';
import { Observable } from 'rxjs';

class AuthServiceStub {
  tienePermiso(): boolean {
    return true;
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

/** Actividad pendiente de prueba con vencimiento en el pasado (vencida). */
function actividadDto(over: Partial<Actividad> = {}): Actividad {
  return {
    id: 'act-1',
    clienteId: 'cli-1',
    oportunidadId: null,
    tipo: 'tarea',
    estado: 'pendiente',
    asunto: 'preparar cotizacion',
    descripcion: null,
    fechaProgramada: '2020-01-01T10:00:00Z',
    vencimiento: '2020-01-02T10:00:00Z',
    completadaEn: null,
    responsableUsuarioId: null,
    version: 0,
    createdAt: '2020-01-01T00:00:00Z',
    updatedAt: '2020-01-01T00:00:00Z',
    ...over,
  } as Actividad;
}

interface Probe {
  totalPendientes(): number | null;
  totalVencidas(): number | null;
  totalCompletadas(): number | null;
  actividades(): Actividad[];
  completar(a: Actividad): void;
}

describe('ComercialActividades', () => {
  let fixture: ComponentFixture<ComercialActividades>;
  let http: HttpTestingController;
  let toast: ToastSpy;

  beforeEach(async () => {
    toast = new ToastSpy();
    await TestBed.configureTestingModule({
      imports: [ComercialActividades, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: NotificacionesService, useValue: toast },
        { provide: OperacionOverlayService, useClass: OverlayStub },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ComercialActividades);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /**
   * Resuelve la carga inicial: la lista principal (estado=pendiente) y las tres
   * consultas de conteo (pendiente/completada/pendiente-para-vencidas).
   */
  function resolverCargaInicial(items: Actividad[], totalPendientes = items.length): void {
    fixture.detectChanges();
    // 1) Lista principal (page=0, size=100, estado=pendiente).
    const lista = http.match((r) => r.url === '/api/v1/actividades' && r.method === 'GET');
    // La primera peticion es la lista; las demas son los conteos. Se resuelven
    // todas con datos coherentes.
    // cargar(): size=100
    const principal = lista.find((r) => r.request.params.get('size') === '100');
    principal!.flush({
      content: items,
      page: 0,
      size: 100,
      totalElements: totalPendientes,
      totalPages: 1,
    });
    // cargarConteos(): pendiente size=1, completada size=1, pendiente size=100.
    for (const req of lista) {
      if (req === principal || req.cancelled) {
        continue;
      }
      const estado = req.request.params.get('estado');
      const size = req.request.params.get('size');
      if (estado === 'pendiente' && size === '1') {
        req.flush({ content: [], page: 0, size: 1, totalElements: totalPendientes, totalPages: 1 });
      } else if (estado === 'completada') {
        req.flush({ content: [], page: 0, size: 1, totalElements: 3, totalPages: 1 });
      } else if (estado === 'pendiente' && size === '100') {
        req.flush({ content: items, page: 0, size: 100, totalElements: totalPendientes, totalPages: 1 });
      }
    }
    fixture.detectChanges();
  }

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('los KPIs se leen de los totalElements del backend', () => {
    resolverCargaInicial([actividadDto()], 42);
    expect(comp().totalPendientes()).toBe(42);
    expect(comp().totalCompletadas()).toBe(3);
    // La unica actividad cargada esta vencida (vencimiento en 2020).
    expect(comp().totalVencidas()).toBe(1);
  });

  it('completar hace PUT /completar, quita la fila y decrementa pendientes', () => {
    resolverCargaInicial([actividadDto()], 42);
    const a = comp().actividades()[0];
    comp().completar(a);

    const put = http.expectOne(
      (r) => r.method === 'PUT' && r.url === `/api/v1/actividades/${a.id}/completar`,
    );
    put.flush(actividadDto({ estado: 'completada', completadaEn: '2026-01-01T00:00:00Z' }));

    // La fila desaparece (filtro por defecto = pendiente) y el KPI baja al instante.
    expect(comp().actividades().length).toBe(0);
    expect(comp().totalPendientes()).toBe(41);
    expect(toast.exitos).toContain('Actividad marcada como completada.');

    // Tras completar se refrescan los conteos (3 consultas nuevas).
    for (const req of http.match((r) => r.url === '/api/v1/actividades' && r.method === 'GET')) {
      const estado = req.request.params.get('estado');
      const size = req.request.params.get('size');
      if (estado === 'completada') {
        req.flush({ content: [], page: 0, size: 1, totalElements: 4, totalPages: 1 });
      } else {
        req.flush({ content: [], page: 0, size: Number(size), totalElements: 41, totalPages: 1 });
      }
    }
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    resolverCargaInicial([actividadDto()], 1);
    await esperarSinViolaciones(fixture);
  });
});
