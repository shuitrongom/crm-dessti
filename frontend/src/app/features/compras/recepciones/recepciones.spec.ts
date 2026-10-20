// =============================================================================
// Pruebas de la vista de Recepciones de mercancía (Compras, Req 32)
// -----------------------------------------------------------------------------
// Verifican, sin zona ni red real:
//   - Lista recepciones y no expone el UUID completo de la orden.
//   - Al elegir una orden en el buscador premium, filtra por su id.
//   - Los KPIs de la página (recepciones y renglones) se calculan bien.
//   - verDetalle abre el modal sin lanzar.
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';

import { ComprasRecepciones } from './recepciones';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { OrdenCompra, RecepcionMercancia } from '../models/compras.models';

registerLocaleData(localeEsMx);

class AuthServiceStub {
  permisos = new Set<string>([
    'recepcion_mercancia:crear',
    'recepcion_mercancia:listar',
    'recepcion_mercancia:leer',
  ]);
  tienePermiso(recurso: string, operacion: string): boolean {
    return this.permisos.has(`${recurso}:${operacion}`);
  }
}

class ToastSpy {
  exito(): void {}
  error(): void {}
  info(): void {}
}

const REC_ID = 'a0000000-0000-0000-0000-000000000010';
const OC_ID = 'a0000000-0000-0000-0000-000000000001';
const PROV_ID = 'b0000000-0000-0000-0000-000000000001';
const MATERIAL_ID = 'c0000000-0000-0000-0000-000000000001';

function proveedor() {
  return {
    id: PROV_ID,
    nombre: 'Aceros del Norte',
    rfc: 'ABC010101AB1',
    email: null,
    telefono: null,
    personaContacto: null,
    regimenFiscal: null,
    diasCredito: null,
    domicilioCalle: null,
    domicilioCiudad: null,
    domicilioEstado: null,
    codigoPostal: null,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

function orden(): OrdenCompra {
  return {
    id: OC_ID,
    proveedorId: PROV_ID,
    requisicionCompraId: null,
    estado: 'abierta',
    total: 300,
    partidas: [],
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

function recepcion(overrides: Partial<RecepcionMercancia> = {}): RecepcionMercancia {
  return {
    id: REC_ID,
    ordenCompraId: OC_ID,
    recibidaEn: '2026-01-02T10:00:00Z',
    partidas: [
      { id: 'pr1', partidaOrdenCompraId: 'poc1', materialId: MATERIAL_ID, cantidadRecibida: 3 },
    ],
    version: 0,
    createdAt: '2026-01-02T10:00:00Z',
    updatedAt: '2026-01-02T10:00:00Z',
    ...overrides,
  };
}

describe('ComprasRecepciones', () => {
  let fixture: ComponentFixture<ComprasRecepciones>;
  let http: HttpTestingController;
  let auth: AuthServiceStub;

  const URL = '/api/v1/compras/recepciones';
  const URL_ORDENES = '/api/v1/compras/ordenes-compra';
  const URL_PROVEEDORES = '/api/v1/compras/proveedores';
  const URL_MATERIALES = '/api/v1/materiales';

  function pagina<T>(content: T[]) {
    return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
  }

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ComprasRecepciones, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: new ToastSpy() },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ComprasRecepciones);
  }

  /** Drena el arranque: catálogo de nombres (proveedores + materiales) y la lista. */
  function resolverArranque(lista: RecepcionMercancia[]): void {
    fixture.detectChanges();
    for (let i = 0; i < 4; i++) {
      const pendientes = http.match((r) => r.method === 'GET');
      if (pendientes.length === 0) {
        break;
      }
      for (const req of pendientes) {
        if (req.request.url === URL_PROVEEDORES) {
          req.flush(pagina([proveedor()]));
        } else if (req.request.url === URL_MATERIALES) {
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
    seleccionarOrden(o: OrdenCompra): void;
    totalMostrado(): number;
    totalRenglones(): number;
    verDetalle(r: RecepcionMercancia): void;
  } {
    return fixture.componentInstance as unknown as {
      seleccionarOrden(o: OrdenCompra): void;
      totalMostrado(): number;
      totalRenglones(): number;
      verDetalle(r: RecepcionMercancia): void;
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

  it('lista recepciones sin exponer el UUID completo de la orden', async () => {
    await crear();
    resolverArranque([recepcion()]);

    // Se muestra el folio corto (8 chars), nunca el UUID completo.
    expect(texto()).toContain(OC_ID.slice(0, 8));
    expect(texto()).not.toContain(OC_ID);
  });

  it('calcula los KPIs de la página (recepciones y renglones)', async () => {
    await crear();
    resolverArranque([recepcion(), recepcion({ id: 'otra' })]);

    expect(vista().totalMostrado()).toBe(2);
    expect(vista().totalRenglones()).toBe(2); // un renglón por recepción
  });

  it('al elegir una orden en el buscador filtra por su id', async () => {
    await crear();
    resolverArranque([recepcion()]);

    vista().seleccionarOrden(orden());
    const req = http.expectOne((r) => r.method === 'GET' && r.url === URL);
    expect(req.request.params.get('ordenCompraId')).toBe(OC_ID);
    req.flush(pagina([recepcion()]));
  });

  it('verDetalle abre el modal de detalle sin lanzar', async () => {
    await crear();
    resolverArranque([recepcion()]);

    expect(() => vista().verDetalle(recepcion())).not.toThrow();
    // El detalle carga el catálogo de nombres; se drena para no dejar peticiones abiertas.
    for (const req of http.match((r) => r.method === 'GET')) {
      req.flush(pagina([]));
    }
  });
});
