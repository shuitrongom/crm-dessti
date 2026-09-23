// =============================================================================
// Pruebas de la vista Conexiones (Req 2, redes-sociales)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Estados de la lista: carga, vacio (sin cuentas) y error.
//   - Alta exitosa: envia el cuerpo correcto y recarga la lista.
//   - 409: muestra un mensaje de conflicto claro sin duplicar.
//   - No se renderiza ninguna credencial ni UUID.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { Conexiones } from './conexiones';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { CuentaCanalSocial } from '../models/social.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** AuthService de prueba: por defecto concede listar y crear. */
class AuthServiceStub {
  permisos = new Set<string>(['cuenta_canal_social:listar', 'cuenta_canal_social:crear']);
  tienePermiso(recurso: string, operacion: string): boolean {
    return this.permisos.has(`${recurso}:${operacion}`);
  }
}

/** Espia de notificaciones. */
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

const CUENTA_UUID = 'b0000000-0000-0000-0000-000000000009';

function cuentaFalsa(): CuentaCanalSocial {
  return {
    id: CUENTA_UUID,
    canal: 'facebook',
    identificadorExterno: 'pagina-123',
    nombre: 'Pagina de la empresa',
    credencialesRef: 'secreto-fb-super-secreto',
    activa: true,
    version: 0,
    createdAt: '2026-01-10T10:00:00Z',
    updatedAt: '2026-01-10T10:00:00Z',
  };
}

interface ConexionesTest {
  formAlta: { patchValue(v: Record<string, unknown>): void };
  crear(): void;
}

describe('Conexiones', () => {
  let fixture: ComponentFixture<Conexiones>;
  let http: HttpTestingController;
  let toast: ToastSpy;
  let auth: AuthServiceStub;

  beforeEach(async () => {
    toast = new ToastSpy();
    auth = new AuthServiceStub();
    await TestBed.configureTestingModule({
      imports: [Conexiones, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: toast },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Conexiones);
  });

  afterEach(() => http.verify());

  /** Resuelve la carga inicial con la pagina dada de cuentas. */
  function resolverLista(cuentas: CuentaCanalSocial[]): void {
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === '/api/v1/social/cuentas-canal');
    expect(req.request.method).toBe('GET');
    req.flush({
      content: cuentas,
      page: 0,
      size: 100,
      totalElements: cuentas.length,
      totalPages: 1,
    });
    fixture.detectChanges();
  }

  function componente(): ConexionesTest {
    return fixture.componentInstance as unknown as ConexionesTest;
  }

  /** Abre el panel de alta pulsando el boton "Conectar cuenta" del encabezado. */
  function abrirAlta(): void {
    const boton = (fixture.nativeElement as HTMLElement).querySelector(
      'app-page-header button',
    ) as HTMLButtonElement;
    boton.click();
    fixture.detectChanges();
  }

  it('muestra el estado de carga antes de resolver la lista', () => {
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Cargando');
    http.expectOne((r) => r.url === '/api/v1/social/cuentas-canal').flush({
      content: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    });
  });

  it('muestra el estado vacio cuando no hay cuentas conectadas', () => {
    resolverLista([]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Aun no has conectado ninguna cuenta');
  });

  it('muestra el estado de error cuando la carga falla', () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === '/api/v1/social/cuentas-canal')
      .flush({ detail: 'Fallo la carga.' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Fallo la carga.');
  });

  it('lista las cuentas mostrando canal y nombre, sin exponer la credencial ni el UUID', () => {
    resolverLista([cuentaFalsa()]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Facebook');
    expect(texto).toContain('Pagina de la empresa');
    expect(texto).toContain('pagina-123');
    expect(texto).toContain('Activa');
    // Nunca se muestra la referencia de credencial ni el UUID.
    expect(texto).not.toContain('secreto-fb-super-secreto');
    expect(texto).not.toContain(CUENTA_UUID);
  });

  it('en el alta exitosa envia el cuerpo correcto y recarga la lista', () => {
    resolverLista([]);
    abrirAlta();
    const c = componente();
    c.formAlta.patchValue({
      canal: 'tiktok',
      nombre: 'Perfil TikTok',
      identificadorExterno: '@miempresa',
      credencialesRef: 'secreto-tiktok',
    });
    c.crear();

    const post = http.expectOne(
      (r) => r.method === 'POST' && r.url === '/api/v1/social/cuentas-canal',
    );
    expect(post.request.body).toEqual({
      canal: 'tiktok',
      nombre: 'Perfil TikTok',
      identificadorExterno: '@miempresa',
      credencialesRef: 'secreto-tiktok',
    });
    post.flush({ ...cuentaFalsa(), canal: 'tiktok', nombre: 'Perfil TikTok' });

    // Tras el 201 recarga la lista.
    http.expectOne((r) => r.method === 'GET' && r.url === '/api/v1/social/cuentas-canal').flush({
      content: [{ ...cuentaFalsa(), canal: 'tiktok', nombre: 'Perfil TikTok' }],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    expect(toast.exitos).toContain('Cuenta conectada.');
  });

  it('ante 409 muestra un mensaje de conflicto claro y no recarga', () => {
    resolverLista([]);
    abrirAlta();
    const c = componente();
    c.formAlta.patchValue({
      canal: 'facebook',
      nombre: 'Pagina de la empresa',
      identificadorExterno: 'pagina-123',
      credencialesRef: 'secreto-fb',
    });
    c.crear();

    http
      .expectOne((r) => r.method === 'POST' && r.url === '/api/v1/social/cuentas-canal')
      .flush({}, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Ya existe una cuenta de ese canal con ese identificador.');
    // No se dispara una segunda peticion de listado tras el conflicto.
    http.expectNone((r) => r.method === 'GET' && r.url === '/api/v1/social/cuentas-canal');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    resolverLista([cuentaFalsa()]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
