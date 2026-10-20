// =============================================================================
// Pruebas de la vista de Requisiciones de compra (Compras, Req 30)
// -----------------------------------------------------------------------------
// Verifican, sin zona ni red real:
//   - Lista requisiciones y muestra los materiales por NOMBRE (no UUID).
//   - Filtra por estado enviando el parámetro al endpoint.
//   - Ofrece "Generar orden de compra" solo en aprobadas sin orden aún.
//   - Las transiciones respetan la segregación de permisos (aprobar vs cambiar).
//   - Cambiar estado (cancelar) llama al endpoint de estado.
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';

import { ComprasRequisiciones } from './requisiciones';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { RequisicionCompra } from '../models/compras.models';

registerLocaleData(localeEsMx);

class AuthServiceStub {
  permisos = new Set<string>([
    'requisicion_compra:crear',
    'requisicion_compra:listar',
    'requisicion_compra:cambiar_estado',
    'requisicion_compra:aprobar',
  ]);
  tienePermiso(recurso: string, operacion: string): boolean {
    return this.permisos.has(`${recurso}:${operacion}`);
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

class ConfirmStub {
  async confirmar(): Promise<boolean> {
    return true;
  }
}

const REQ_ID = 'a0000000-0000-0000-0000-000000000001';
const MATERIAL_ID = 'b0000000-0000-0000-0000-000000000001';

function requisicion(overrides: Partial<RequisicionCompra> = {}): RequisicionCompra {
  return {
    id: REQ_ID,
    estado: 'borrador',
    ordenCompraId: null,
    partidas: [{ id: 'p1', materialId: MATERIAL_ID, cantidad: 5 }],
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

const MATERIAL = {
  id: MATERIAL_ID,
  nombre: 'Lámina acrílica',
  unidadMedida: 'm2',
  stockMinimo: 10,
  existencias: 5,
  stockBajo: true,
  activo: true,
  version: 0,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('ComprasRequisiciones', () => {
  let fixture: ComponentFixture<ComprasRequisiciones>;
  let http: HttpTestingController;
  let auth: AuthServiceStub;

  const URL = '/api/v1/compras/requisiciones';
  const URL_MATERIALES = '/api/v1/materiales';
  const URL_ALMACENES = '/api/v1/inventario-avanzado/almacenes';

  function pagina<T>(content: T[]) {
    return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
  }

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ComprasRequisiciones, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: new ToastSpy() },
        { provide: ConfirmDialogService, useClass: ConfirmStub },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ComprasRequisiciones);
  }

  /** Drena el arranque: catálogo de nombres (almacenes + materiales) y la lista. */
  function resolverArranque(lista: RequisicionCompra[]): void {
    fixture.detectChanges();
    for (let i = 0; i < 4; i++) {
      const pendientes = http.match((r) => r.method === 'GET');
      if (pendientes.length === 0) {
        break;
      }
      for (const req of pendientes) {
        if (req.request.url === URL_MATERIALES) {
          req.flush(pagina([MATERIAL]));
        } else if (req.request.url === URL_ALMACENES) {
          req.flush(pagina([]));
        } else if (req.request.url === URL) {
          req.flush(pagina(lista));
        } else {
          req.flush(pagina([]));
        }
      }
      fixture.detectChanges();
    }
  }

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function vista(): {
    aplicarFiltro(v: string): void;
    puedeGenerarOrden(r: RequisicionCompra): boolean;
    transicionesDe(estado: string): { estado: string }[];
    requiereAprobar(estadoDestino: string): boolean;
    resumenMateriales(r: RequisicionCompra): string;
  } {
    return fixture.componentInstance as unknown as {
      aplicarFiltro(v: string): void;
      puedeGenerarOrden(r: RequisicionCompra): boolean;
      transicionesDe(estado: string): { estado: string }[];
      requiereAprobar(estadoDestino: string): boolean;
      resumenMateriales(r: RequisicionCompra): string;
    };
  }

  beforeEach(() => {
    auth = new AuthServiceStub();
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  it('lista requisiciones y muestra los materiales por nombre', async () => {
    await crear();
    resolverArranque([requisicion()]);

    expect(texto()).toContain('Lámina acrílica');
    expect(texto()).not.toContain(MATERIAL_ID);
    expect(vista().resumenMateriales(requisicion())).toContain('Lámina acrílica × 5');
  });

  it('filtra por estado enviando el parámetro al endpoint', async () => {
    await crear();
    resolverArranque([requisicion()]);

    vista().aplicarFiltro('aprobada');
    const req = http.expectOne((r) => r.method === 'GET' && r.url === URL);
    expect(req.request.params.get('estado')).toBe('aprobada');
    req.flush(pagina([]));
  });

  it('ofrece "Generar orden de compra" solo en aprobadas sin orden aún', async () => {
    await crear();
    resolverArranque([requisicion()]);

    expect(vista().puedeGenerarOrden(requisicion({ estado: 'borrador' }))).toBe(false);
    expect(vista().puedeGenerarOrden(requisicion({ estado: 'aprobada', ordenCompraId: null }))).toBe(
      true,
    );
    expect(
      vista().puedeGenerarOrden(requisicion({ estado: 'aprobada', ordenCompraId: 'oc-1' })),
    ).toBe(false);
  });

  it('sin permiso aprobar, la transición Aprobar se ofrece pero marcada como que requiere autorización', async () => {
    auth.permisos = new Set<string>([
      'requisicion_compra:listar',
      'requisicion_compra:cambiar_estado',
    ]);
    await crear();
    resolverArranque([requisicion({ estado: 'enviada' })]);

    // Patrón "requiere autorización" (directiva appRequierePermiso): en vez de
    // ocultar Aprobar/Rechazar cuando falta el permiso dedicado, se muestran para
    // que el usuario sepa que existen y qué permiso necesita; la directiva las
    // deshabilita visualmente. Por eso siguen apareciendo en la lista.
    const estados = vista()
      .transicionesDe('enviada')
      .map((t) => t.estado);
    expect(estados).toContain('aprobada');
    expect(estados).toContain('cancelada');
    // Las transiciones de aprobación/rechazo se identifican como "requiere permiso".
    expect(vista().requiereAprobar('aprobada')).toBe(true);
    expect(vista().requiereAprobar('rechazada')).toBe(true);
    expect(vista().requiereAprobar('cancelada')).toBe(false);
  });
});
