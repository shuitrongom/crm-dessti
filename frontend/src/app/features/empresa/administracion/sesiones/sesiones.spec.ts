// =============================================================================
// Pruebas de la vista AdminSesiones: selector de cuenta, carga de sesiones,
// revocacion con confirmacion y accesibilidad (Req 68, 54, 57)
// -----------------------------------------------------------------------------
// Deterministas, sin zona ni red real (proyecto zoneless, Vitest + jsdom).
// Cubren:
//   - Las opciones del selector provienen de GET /usuarios (nombre — correo).
//   - Al seleccionar una cuenta se carga GET /usuarios/{id}/sesiones y se pintan
//     las sesiones en la tabla.
//   - Revocar confirma y llama POST /usuarios/{id}/sesiones/revocar, luego
//     recarga las sesiones.
//   - La confirmacion rechazada no dispara la revocacion.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { AdminSesiones } from './sesiones';
import { ConfirmDialogService } from '../../../../shared/components/confirm-dialog/confirm-dialog';
import { Usuario } from '../services/usuarios.service';
import { SesionActiva } from '../services/sesiones.service';
import { esperarSinViolaciones } from '../../../../../testing/axe';

/** ConfirmDialogService de prueba: confirma o rechaza segun se configure. */
class ConfirmStub {
  respuesta = true;
  llamadas = 0;
  async confirmar(): Promise<boolean> {
    this.llamadas += 1;
    return this.respuesta;
  }
}

/** Forma minima del componente accedida por las pruebas. */
interface ComponenteSesiones {
  seleccionar(id: string): void;
  revocar(): Promise<void>;
}

function usuario(parcial: Partial<Usuario> = {}): Usuario {
  return {
    id: 'u1',
    identificadorAcceso: 'ana@acme.test',
    nombreVisible: 'Ana Lopez',
    activo: true,
    roles: [],
    ...parcial,
  };
}

function sesion(parcial: Partial<SesionActiva> = {}): SesionActiva {
  return {
    jti: 'jti-1',
    usuarioId: 'u1',
    tenantId: 't1',
    emitidoEn: '2024-01-01T10:00:00Z',
    expiraEn: '2024-01-02T10:00:00Z',
    ...parcial,
  };
}

describe('AdminSesiones', () => {
  let fixture: ComponentFixture<AdminSesiones>;
  let http: HttpTestingController;
  let confirm: ConfirmStub;

  beforeEach(async () => {
    confirm = new ConfirmStub();
    await TestBed.configureTestingModule({
      imports: [AdminSesiones, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminSesiones);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Resuelve la carga inicial del selector de cuentas (GET /usuarios). */
  function resolverCuentas(cuentas: Usuario[]): void {
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === '/api/v1/usuarios');
    req.flush({ content: cuentas, page: 0, size: 100, totalElements: cuentas.length, totalPages: 1 });
    fixture.detectChanges();
  }

  /** Resuelve la carga de sesiones de una cuenta. */
  function flushSesiones(req: TestRequest, filas: SesionActiva[]): void {
    req.flush({ content: filas, page: 0, size: 20, totalElements: filas.length, totalPages: 1 });
  }

  function componenteDe(): ComponenteSesiones {
    return fixture.componentInstance as unknown as ComponenteSesiones;
  }

  it('las opciones del selector provienen de GET /usuarios (nombre — correo)', () => {
    resolverCuentas([
      usuario({ id: 'u1', nombreVisible: 'Ana Lopez', identificadorAcceso: 'ana@acme.test' }),
      usuario({ id: 'u2', nombreVisible: null, identificadorAcceso: 'sinnombre@acme.test' }),
    ]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Sesiones');
    expect(texto).toContain('Cuenta');
    // No hay ningun campo para escribir un identificador (sin input de UUID).
    const inputs = (fixture.nativeElement as HTMLElement).querySelectorAll('input[type="text"]');
    expect(inputs.length).toBe(0);
  });

  it('al seleccionar una cuenta carga GET /usuarios/{id}/sesiones', () => {
    resolverCuentas([usuario({ id: 'u9' })]);
    componenteDe().seleccionar('u9');
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === '/api/v1/usuarios/u9/sesiones');
    expect(req.request.method).toBe('GET');
    flushSesiones(req, [sesion({ jti: 'jti-abc' })]);
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('jti-abc');
  });

  it('revocar confirma y llama POST /usuarios/{id}/sesiones/revocar y recarga', async () => {
    resolverCuentas([usuario({ id: 'u9' })]);
    componenteDe().seleccionar('u9');
    fixture.detectChanges();
    flushSesiones(
      http.expectOne((r) => r.url === '/api/v1/usuarios/u9/sesiones'),
      [sesion()],
    );

    await componenteDe().revocar();

    expect(confirm.llamadas).toBe(1);
    const req = http.expectOne('/api/v1/usuarios/u9/sesiones/revocar');
    expect(req.request.method).toBe('POST');
    req.flush(null);

    // Tras revocar se recargan las sesiones de la cuenta.
    flushSesiones(
      http.expectOne((r) => r.url === '/api/v1/usuarios/u9/sesiones'),
      [],
    );
    fixture.detectChanges();
  });

  it('no revoca si la confirmacion se rechaza', async () => {
    resolverCuentas([usuario({ id: 'u9' })]);
    componenteDe().seleccionar('u9');
    fixture.detectChanges();
    flushSesiones(
      http.expectOne((r) => r.url === '/api/v1/usuarios/u9/sesiones'),
      [sesion()],
    );
    confirm.respuesta = false;

    await componenteDe().revocar();

    expect(confirm.llamadas).toBe(1);
    http.expectNone('/api/v1/usuarios/u9/sesiones/revocar');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    resolverCuentas([usuario()]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
