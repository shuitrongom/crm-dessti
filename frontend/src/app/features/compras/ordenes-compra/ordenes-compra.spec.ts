// =============================================================================
// Pruebas de la vista de Órdenes de compra (Compras, Req 31)
// -----------------------------------------------------------------------------
// Verifican, sin zona ni red real:
//   - Lista órdenes y muestra el proveedor por NOMBRE (no UUID).
//   - Filtra por estado enviando el parámetro al endpoint.
//   - Al elegir un proveedor en el buscador premium, filtra por su id.
//   - Ofrece las transiciones manuales correctas por estado (cancelar/cerrar).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';

import { ComprasOrdenesCompra } from './ordenes-compra';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { OrdenCompra, Proveedor } from '../models/compras.models';

registerLocaleData(localeEsMx);

class AuthServiceStub {
  permisos = new Set<string>([
    'orden_compra:crear',
    'orden_compra:listar',
    'orden_compra:cambiar_estado',
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

class ConfirmStub {
  async confirmar(): Promise<boolean> {
    return true;
  }
}

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

function orden(overrides: Partial<OrdenCompra> = {}): OrdenCompra {
  return {
    id: OC_ID,
    proveedorId: PROV_ID,
    requisicionCompraId: null,
    estado: 'abierta',
    total: 15930,
    partidas: [],
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('ComprasOrdenesCompra', () => {
  let fixture: ComponentFixture<ComprasOrdenesCompra>;
  let http: HttpTestingController;
  let auth: AuthServiceStub;

  const URL = '/api/v1/compras/ordenes-compra';
  const URL_PROVEEDORES = '/api/v1/compras/proveedores';
  const URL_MATERIALES = '/api/v1/materiales';

  function pagina<T>(content: T[]) {
    return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
  }

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ComprasOrdenesCompra, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: new ToastSpy() },
        { provide: ConfirmDialogService, useClass: ConfirmStub },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ComprasOrdenesCompra);
  }

  /** Drena el arranque: catálogo de nombres (proveedores + materiales) y la lista. */
  function resolverArranque(lista: OrdenCompra[]): void {
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
    transicionesDe(estado: string): { estado: string }[];
    nombreProveedor(id: string): string;
  } {
    return fixture.componentInstance as unknown as {
      aplicarFiltroEstado(v: string): void;
      seleccionarProveedor(p: Proveedor): void;
      transicionesDe(estado: string): { estado: string }[];
      nombreProveedor(id: string): string;
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

  it('lista órdenes y muestra el proveedor por nombre (no UUID)', async () => {
    await crear();
    resolverArranque([orden()]);

    expect(texto()).toContain('Aceros del Norte');
    expect(texto()).not.toContain(PROV_ID);
    expect(vista().nombreProveedor(PROV_ID)).toBe('Aceros del Norte');
  });

  it('filtra por estado enviando el parámetro al endpoint', async () => {
    await crear();
    resolverArranque([orden()]);

    vista().aplicarFiltroEstado('abierta');
    const req = http.expectOne((r) => r.method === 'GET' && r.url === URL);
    expect(req.request.params.get('estado')).toBe('abierta');
    req.flush(pagina([]));
  });

  it('al elegir un proveedor en el buscador filtra por su id', async () => {
    await crear();
    resolverArranque([orden()]);

    vista().seleccionarProveedor(proveedor());
    const req = http.expectOne((r) => r.method === 'GET' && r.url === URL);
    expect(req.request.params.get('proveedorId')).toBe(PROV_ID);
    req.flush(pagina([orden()]));
  });

  it('ofrece las transiciones manuales correctas por estado', async () => {
    await crear();
    resolverArranque([orden()]);

    expect(vista().transicionesDe('abierta').map((t) => t.estado)).toEqual(['cancelada']);
    expect(vista().transicionesDe('recibida_total').map((t) => t.estado)).toEqual(['cerrada']);
    expect(vista().transicionesDe('cerrada')).toEqual([]);
  });

  it('verDetalle abre el modal de detalle sin lanzar', async () => {
    await crear();
    resolverArranque([orden()]);

    // No debe lanzar; el modal se abre con MatDialog real (NoopAnimations).
    expect(() =>
      (fixture.componentInstance as unknown as { verDetalle(o: OrdenCompra): void }).verDetalle(
        orden(),
      ),
    ).not.toThrow();
    // El detalle carga el catálogo de nombres; se drena para no dejar peticiones abiertas.
    for (const req of http.match((r) => r.method === 'GET')) {
      req.flush(pagina([]));
    }
  });
});
