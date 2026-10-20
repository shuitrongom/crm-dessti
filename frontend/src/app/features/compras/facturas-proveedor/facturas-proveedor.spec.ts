// =============================================================================
// Pruebas de la vista de Facturas de proveedor / conciliación 3 vías (Req 33)
// -----------------------------------------------------------------------------
// Verifican, sin zona ni red real:
//   - Lista facturas y muestra el proveedor por NOMBRE (no UUID).
//   - Filtra por estado enviando el parámetro al endpoint.
//   - Al elegir un proveedor en el buscador premium, filtra por su id.
//   - Las acciones por estado (conciliar/autorizar/reabrir) respetan el permiso.
//   - Conciliar llama al endpoint y avisa la discrepancia.
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';

import { ComprasFacturasProveedor } from './facturas-proveedor';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { AuthService } from '../../../core/auth/auth.service';
import { FacturaProveedor, Proveedor } from '../models/compras.models';

registerLocaleData(localeEsMx);

class AuthServiceStub {
  permisos = new Set<string>([
    'factura_proveedor:crear',
    'factura_proveedor:listar',
    'factura_proveedor:leer',
    'factura_proveedor:cambiar_estado',
  ]);
  tienePermiso(recurso: string, operacion: string): boolean {
    return this.permisos.has(`${recurso}:${operacion}`);
  }
}

class ToastSpy {
  exitos: string[] = [];
  infos: string[] = [];
  errores: string[] = [];
  exito(m: string): void {
    this.exitos.push(m);
  }
  error(m: string): void {
    this.errores.push(m);
  }
  info(m: string): void {
    this.infos.push(m);
  }
}

class ConfirmStub {
  async confirmar(): Promise<boolean> {
    return true;
  }
}

/** Overlay que ejecuta directamente el observable (sin animación) para los tests. */
class OverlayStub {
  ejecutar<T>(obs: T): T {
    return obs;
  }
}

const FAC_ID = 'a0000000-0000-0000-0000-000000000020';
const OC_ID = 'a0000000-0000-0000-0000-000000000001';
const PROV_ID = 'b0000000-0000-0000-0000-000000000001';

function proveedor(): Proveedor {
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

function factura(overrides: Partial<FacturaProveedor> = {}): FacturaProveedor {
  return {
    id: FAC_ID,
    ordenCompraId: OC_ID,
    proveedorId: PROV_ID,
    folioProveedor: 'F-1001',
    monto: 300,
    estado: 'registrada',
    version: 0,
    createdAt: '2026-01-02T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
    ...overrides,
  };
}

describe('ComprasFacturasProveedor', () => {
  let fixture: ComponentFixture<ComprasFacturasProveedor>;
  let http: HttpTestingController;
  let auth: AuthServiceStub;
  let toast: ToastSpy;

  const URL = '/api/v1/compras/facturas-proveedor';
  const URL_PROVEEDORES = '/api/v1/compras/proveedores';
  const URL_MATERIALES = '/api/v1/materiales';

  function pagina<T>(content: T[]) {
    return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
  }

  async function crear(): Promise<void> {
    toast = new ToastSpy();
    await TestBed.configureTestingModule({
      imports: [ComprasFacturasProveedor, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: toast },
        { provide: ConfirmDialogService, useClass: ConfirmStub },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ComprasFacturasProveedor);
  }

  /** Drena el arranque: catálogo de nombres (proveedores + materiales) y la lista. */
  function resolverArranque(lista: FacturaProveedor[]): void {
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
    aplicarFiltroEstado(v: string): void;
    seleccionarProveedor(p: Proveedor): void;
    nombreProveedor(id: string): string;
    puedeConciliar(f: FacturaProveedor): boolean;
    puedeAutorizar(f: FacturaProveedor): boolean;
    puedeReabrir(f: FacturaProveedor): boolean;
    conciliar(f: FacturaProveedor): Promise<void>;
  } {
    return fixture.componentInstance as unknown as {
      aplicarFiltroEstado(v: string): void;
      seleccionarProveedor(p: Proveedor): void;
      nombreProveedor(id: string): string;
      puedeConciliar(f: FacturaProveedor): boolean;
      puedeAutorizar(f: FacturaProveedor): boolean;
      puedeReabrir(f: FacturaProveedor): boolean;
      conciliar(f: FacturaProveedor): Promise<void>;
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

  it('lista facturas y muestra el proveedor por nombre (no UUID)', async () => {
    await crear();
    resolverArranque([factura()]);

    expect(texto()).toContain('Aceros del Norte');
    expect(texto()).not.toContain(PROV_ID);
    expect(vista().nombreProveedor(PROV_ID)).toBe('Aceros del Norte');
  });

  it('filtra por estado enviando el parámetro al endpoint', async () => {
    await crear();
    resolverArranque([factura()]);

    vista().aplicarFiltroEstado('discrepancia');
    const req = http.expectOne((r) => r.method === 'GET' && r.url === URL);
    expect(req.request.params.get('estado')).toBe('discrepancia');
    req.flush(pagina([]));
  });

  it('al elegir un proveedor en el buscador filtra por su id', async () => {
    await crear();
    resolverArranque([factura()]);

    vista().seleccionarProveedor(proveedor());
    const req = http.expectOne((r) => r.method === 'GET' && r.url === URL);
    expect(req.request.params.get('proveedorId')).toBe(PROV_ID);
    req.flush(pagina([factura()]));
  });

  it('ofrece las acciones correctas por estado (segregadas por permiso)', async () => {
    await crear();
    resolverArranque([factura()]);

    expect(vista().puedeConciliar(factura({ estado: 'registrada' }))).toBe(true);
    expect(vista().puedeAutorizar(factura({ estado: 'conciliada' }))).toBe(true);
    expect(vista().puedeReabrir(factura({ estado: 'discrepancia' }))).toBe(true);
    // Una factura pagada no ofrece acciones.
    expect(vista().puedeConciliar(factura({ estado: 'pagada' }))).toBe(false);
    expect(vista().puedeAutorizar(factura({ estado: 'pagada' }))).toBe(false);
    expect(vista().puedeReabrir(factura({ estado: 'pagada' }))).toBe(false);
  });

  it('conciliar llama al endpoint y avisa cuando hay discrepancia', async () => {
    await crear();
    resolverArranque([factura()]);

    const promesa = vista().conciliar(factura());
    // El confirm es async (promesa); se ceden varios microtasks hasta que se
    // encola la petición POST de conciliación.
    let req = http.match((r) => r.method === 'POST' && r.url === `${URL}/${FAC_ID}/conciliar`);
    for (let i = 0; i < 5 && req.length === 0; i++) {
      await Promise.resolve();
      req = http.match((r) => r.method === 'POST' && r.url === `${URL}/${FAC_ID}/conciliar`);
    }
    expect(req.length).toBe(1);
    req[0].flush(factura({ estado: 'discrepancia' }));
    // Tras conciliar recarga el listado.
    const recarga = http.expectOne((r) => r.method === 'GET' && r.url === URL);
    recarga.flush(pagina([factura({ estado: 'discrepancia' })]));
    await promesa;

    expect(toast.infos.some((m) => m.toLowerCase().includes('discrepancia'))).toBe(true);
  });
});
