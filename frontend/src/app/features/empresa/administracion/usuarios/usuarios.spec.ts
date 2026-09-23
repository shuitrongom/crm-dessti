// =============================================================================
// Pruebas de la vista AdminUsuarios: render de la tabla, busqueda con debounce,
// desactivacion con confirmacion y accesibilidad (Req 4, 54, 57)
// -----------------------------------------------------------------------------
// Deterministas, sin zona ni red real (proyecto zoneless: temporizadores falsos
// de Vitest). Cubren:
//   - Render de las cuentas devueltas por GET /usuarios (nombre, correo, chips
//     de roles y chip de estado).
//   - La busqueda con debounce (300ms) recarga con `q` y reinicia a la pagina 0.
//   - Desactivar confirma y llama POST /usuarios/{id}/desactivar.
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

import { AdminUsuarios } from './usuarios';
import { AuthService } from '../../../../core/auth/auth.service';
import { ConfirmDialogService } from '../../../../shared/components/confirm-dialog/confirm-dialog';
import { Usuario } from '../services/usuarios.service';
import { esperarSinViolaciones } from '../../../../../testing/axe';

/** AuthService de prueba: admin_empresa con todos los permisos de usuario. */
class AuthServiceStub {
  tienePermiso(recurso: string, _operacion: string): boolean {
    return recurso === 'usuario';
  }
}

/** ConfirmDialogService de prueba: confirma o rechaza segun se configure. */
class ConfirmStub {
  respuesta = true;
  llamadas = 0;
  async confirmar(): Promise<boolean> {
    this.llamadas += 1;
    return this.respuesta;
  }
}

function usuario(parcial: Partial<Usuario> = {}): Usuario {
  return {
    id: 'u1',
    identificadorAcceso: 'ana@acme.test',
    nombreVisible: 'Ana Lopez',
    activo: true,
    roles: [{ id: 'r1', nombre: 'gerente' }],
    ...parcial,
  };
}

function flushLista(req: TestRequest, filas: Usuario[]): void {
  req.flush({ content: filas, page: 0, size: 20, totalElements: filas.length, totalPages: 1 });
}

interface ComponenteUsuarios {
  cambiarBusqueda(t: string): void;
  desactivar(u: Usuario): Promise<void>;
}

describe('AdminUsuarios', () => {
  let fixture: ComponentFixture<AdminUsuarios>;
  let http: HttpTestingController;
  let confirm: ConfirmStub;

  beforeEach(async () => {
    vi.useFakeTimers();
    confirm = new ConfirmStub();
    await TestBed.configureTestingModule({
      imports: [AdminUsuarios, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminUsuarios);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  /** Resuelve la carga inicial de la lista (size 20). */
  function resolverCargaInicial(filas: Usuario[]): void {
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === '/api/v1/usuarios');
    flushLista(req, filas);
    // Vence el debounce inicial (valor vacio -> sin cambio -> sin nueva peticion).
    vi.advanceTimersByTime(300);
    fixture.detectChanges();
  }

  it('renderiza la tabla con nombre, correo, roles y estado', () => {
    resolverCargaInicial([usuario({ nombreVisible: 'Ana Lopez', roles: [{ id: 'r1', nombre: 'gerente' }] })]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Usuarios');
    expect(texto).toContain('Ana Lopez');
    expect(texto).toContain('ana@acme.test');
    expect(texto).toContain('gerente');
    expect(texto).toContain('Activa');
  });

  it('muestra un guion cuando la cuenta no tiene nombre para mostrar', () => {
    resolverCargaInicial([usuario({ nombreVisible: null })]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('—');
    expect(texto).toContain('ana@acme.test');
  });

  it('la busqueda con debounce recarga con q y reinicia a la pagina 0', () => {
    resolverCargaInicial([usuario()]);

    const componente = fixture.componentInstance as unknown as ComponenteUsuarios;
    componente.cambiarBusqueda('ana');
    fixture.detectChanges();
    // Antes del debounce no hay peticion nueva.
    http.expectNone((r) => r.url === '/api/v1/usuarios');
    vi.advanceTimersByTime(300);

    const req = http.expectOne((r) => r.url === '/api/v1/usuarios' && r.params.get('q') === 'ana');
    expect(req.request.params.get('page')).toBe('0');
    flushLista(req, []);
    fixture.detectChanges();
  });

  it('desactivar confirma y llama POST /usuarios/{id}/desactivar', async () => {
    resolverCargaInicial([usuario({ id: 'u9' })]);

    const componente = fixture.componentInstance as unknown as ComponenteUsuarios;
    const promesa = componente.desactivar(usuario({ id: 'u9' }));
    await vi.runOnlyPendingTimersAsync();
    await promesa;

    expect(confirm.llamadas).toBe(1);
    const req = http.expectOne('/api/v1/usuarios/u9/desactivar');
    expect(req.request.method).toBe('POST');
    req.flush(usuario({ id: 'u9', activo: false }));

    // Tras desactivar se recarga la lista.
    flushLista(
      http.expectOne((r) => r.url === '/api/v1/usuarios'),
      [],
    );
    fixture.detectChanges();
  });

  it('no llama al endpoint si la confirmacion se rechaza', async () => {
    resolverCargaInicial([usuario({ id: 'u9' })]);
    confirm.respuesta = false;

    const componente = fixture.componentInstance as unknown as ComponenteUsuarios;
    const promesa = componente.desactivar(usuario({ id: 'u9' }));
    await vi.runOnlyPendingTimersAsync();
    await promesa;

    expect(confirm.llamadas).toBe(1);
    http.expectNone('/api/v1/usuarios/u9/desactivar');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    resolverCargaInicial([usuario()]);
    vi.useRealTimers();
    await esperarSinViolaciones(fixture);
  });
});
