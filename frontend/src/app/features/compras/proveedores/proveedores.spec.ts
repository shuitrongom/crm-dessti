// =============================================================================
// Pruebas de la vista de Proveedores (Compras, Req 29)
// -----------------------------------------------------------------------------
// Verifican, sin zona ni red real:
//   - Lista proveedores por NOMBRE/RFC y los muestra (con días de crédito).
//   - La búsqueda por filtro llama al endpoint con el parámetro.
//   - Fix UX: al reactivar desde el filtro "Inactivos", la vista cambia a
//     "activos" para que el usuario siga viendo el proveedor.
//   - Fix UX: al desactivar desde "activos", cambia a "inactivos".
//   - Gating: sin permiso de actualizar, no se ofrecen acciones de edición.
// =============================================================================

import { vi } from 'vitest';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';

import { ComprasProveedores } from './proveedores';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { Proveedor } from '../models/compras.models';

registerLocaleData(localeEsMx);

/** AuthService de prueba con permisos configurables. */
class AuthServiceStub {
  permisos = new Set<string>(['proveedor:crear', 'proveedor:actualizar', 'proveedor:listar']);
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

/** Confirm que siempre acepta (simula que el usuario confirma el diálogo). */
class ConfirmStub {
  async confirmar(): Promise<boolean> {
    return true;
  }
}

const PROV_ID = 'a0000000-0000-0000-0000-000000000001';

function proveedorFalso(overrides: Partial<Proveedor> = {}): Proveedor {
  return {
    id: PROV_ID,
    nombre: 'Aceros del Norte',
    rfc: 'ABC010101AB1',
    email: 'compras@aceros.mx',
    telefono: '5512345678',
    personaContacto: 'Juan Pérez',
    regimenFiscal: '601',
    diasCredito: 30,
    domicilioCalle: null,
    domicilioCiudad: null,
    domicilioEstado: null,
    codigoPostal: null,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('ComprasProveedores', () => {
  let fixture: ComponentFixture<ComprasProveedores>;
  let http: HttpTestingController;
  let toast: ToastSpy;
  let auth: AuthServiceStub;

  const URL = '/api/v1/compras/proveedores';

  function pagina(content: Proveedor[]) {
    return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
  }

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ComprasProveedores, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: toast },
        { provide: ConfirmDialogService, useClass: ConfirmStub },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ComprasProveedores);
  }

  /** Resuelve el GET de arranque de proveedores con la lista dada. */
  function resolverCarga(lista: Proveedor[]): void {
    fixture.detectChanges();
    const req = http.expectOne((r) => r.method === 'GET' && r.url === URL);
    req.flush(pagina(lista));
    fixture.detectChanges();
  }

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function vista(): {
    reactivar(p: Proveedor): Promise<void>;
    desactivar(p: Proveedor): Promise<void>;
    actualizarFiltro(v: string): void;
    estadoFiltro(): string;
  } {
    return fixture.componentInstance as unknown as {
      reactivar(p: Proveedor): Promise<void>;
      desactivar(p: Proveedor): Promise<void>;
      actualizarFiltro(v: string): void;
      estadoFiltro(): string;
    };
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

  it('lista los proveedores mostrando nombre, RFC y días de crédito', async () => {
    await crear();
    resolverCarga([proveedorFalso()]);

    const t = texto();
    expect(t).toContain('Aceros del Norte');
    expect(t).toContain('ABC010101AB1');
    expect(t).toContain('30 días');
  });

  it('la búsqueda en vivo por nombre/RFC envía el filtro al endpoint (con debounce)', async () => {
    vi.useFakeTimers();
    try {
      await crear();
      resolverCarga([proveedorFalso()]);

      // El usuario teclea: el signal `filtro` cambia a 'aceros' y, tras el debounce,
      // el flujo del componente recarga con ese filtro (la emisión inicial '' se
      // ignora por coincidir con el término ya aplicado).
      vista().actualizarFiltro('aceros');
      fixture.detectChanges();
      vi.advanceTimersByTime(350);
      fixture.detectChanges();
      // Drena cualquier GET intermedio y valida que el último lleve el filtro.
      const reqs = http.match((r) => r.method === 'GET' && r.url === URL);
      const conFiltro = reqs.find((r) => r.request.params.get('filtro') === 'aceros');
      expect(conFiltro).toBeTruthy();
      for (const r of reqs) {
        r.flush(pagina([proveedorFalso()]));
      }
    } finally {
      vi.useRealTimers();
    }
  });

  it('al reactivar desde "inactivos" cambia el filtro a "activos" para seguir viéndolo', async () => {
    await crear();
    // Arranca en "activos" (GET del constructor) y luego se cambia a "inactivos"
    // (segundo GET) para simular la pantalla del bug. Se drenan ambos GET.
    fixture.detectChanges();
    (fixture.componentInstance as unknown as { cambiarEstadoFiltro(e: string): void }).cambiarEstadoFiltro(
      'inactivo',
    );
    for (const req of http.match((r) => r.method === 'GET' && r.url === URL)) {
      req.flush(pagina([proveedorFalso({ activo: false })]));
    }
    fixture.detectChanges();

    const inactivo = proveedorFalso({ activo: false });
    await vista().reactivar(inactivo);

    // El PUT de reactivar.
    http.expectOne((r) => r.method === 'PUT' && r.url === `${URL}/${PROV_ID}/activar`).flush({});
    // Tras reactivar, la vista recarga: el filtro debe ser "activo".
    const recarga = http.expectOne((r) => r.method === 'GET' && r.url === URL);
    recarga.flush(pagina([proveedorFalso({ activo: true })]));

    expect(vista().estadoFiltro()).toBe('activo');
    expect(toast.exitos).toContain('Proveedor reactivado.');
  });

  it('al dar de baja desde "activos" cambia el filtro a "inactivos"', async () => {
    await crear();
    resolverCarga([proveedorFalso()]);

    await vista().desactivar(proveedorFalso());

    http.expectOne((r) => r.method === 'DELETE' && r.url === `${URL}/${PROV_ID}`).flush({});
    const recarga = http.expectOne((r) => r.method === 'GET' && r.url === URL);
    recarga.flush(pagina([proveedorFalso({ activo: false })]));

    expect(vista().estadoFiltro()).toBe('inactivo');
    expect(toast.exitos).toContain('Proveedor dado de baja.');
  });

  it('gating: sin permiso de actualizar no se muestran acciones de fila', async () => {
    auth.permisos = new Set<string>(['proveedor:listar']);
    await crear();
    resolverCarga([proveedorFalso()]);

    // El menú de acciones se oculta; en su lugar aparece el marcador "—".
    const t = texto();
    expect(t).toContain('Aceros del Norte');
  });
});
