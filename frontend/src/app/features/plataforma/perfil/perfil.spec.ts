// =============================================================================
// Pruebas de la vista "Mi perfil" (super_admin) (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Carga del perfil (GET /auth/perfil) y muestra de identificador/roles/ambito.
//   - Validacion del formulario: contrasenas no coincidentes y nueva muy corta.
//   - Camino de exito (204): envia {passwordActual, passwordNueva}, muestra el
//     mensaje de exito y reinicia el formulario.
//   - 422: muestra el error de contrasena actual incorrecta.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { PlataformaPerfil } from './perfil';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { esperarSinViolaciones } from '../../../../testing/axe';

/**
 * AuthService de prueba: por defecto super_admin (sin tenant, no admin_empresa),
 * de modo que la seccion "Datos de mi empresa" no se renderiza. Se puede
 * configurar como admin_empresa para las pruebas de esa seccion.
 */
class AuthServiceStub {
  private admin = false;
  configurarAdminEmpresa(esAdmin: boolean): void {
    this.admin = esAdmin;
  }
  tieneRol(rol: string): boolean {
    return this.admin && rol === 'admin_empresa';
  }
  tenantId(): string | null {
    return this.admin ? 'tenant-1' : null;
  }
}

/** Espia del servicio de notificaciones para verificar el toast de exito. */
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

/** Forma minima del componente accedida por las pruebas. */
interface PerfilTest {
  formulario: {
    patchValue(v: Record<string, unknown>): void;
    reset(): void;
    controls: {
      passwordActual: { hasError(k: string): boolean };
      passwordNueva: { hasError(k: string): boolean };
      confirmarPassword: { hasError(k: string): boolean };
    };
  };
  cambiarPassword(): void;
  errorPassword(): string | null;
}

/** Forma minima para las pruebas de la seccion "Datos de mi empresa". */
interface PerfilEmpresaTest {
  formularioEmpresa: {
    getRawValue(): Record<string, unknown>;
    patchValue(v: Record<string, unknown>): void;
    controls: { emailContacto: { setValue(v: string): void } };
  };
  guardarEmpresa(): void;
}

/** EmpresaDto minimo devuelto por GET/PUT /empresas/mi-empresa. */
function miEmpresaDto() {
  return {
    id: 'e1',
    nombre: 'Acme',
    rfc: 'ABCD901231XYZ',
    giroId: 'g1',
    estado: 'activa',
    brandingNombreVisible: null,
    brandingLogo: null,
    nombreComercial: null,
    emailContacto: 'contacto@acme.test',
    telefono: '5551234567',
    sitioWeb: null,
    direccion: { calle: 'Av. Central 100', ciudad: 'Monterrey', estado: 'NL', cp: '64000', pais: 'Mexico' },
    notas: null,
    fechaCancelacion: null,
    finPeriodoGracia: null,
    planVigente: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

describe('PlataformaPerfil', () => {
  let fixture: ComponentFixture<PlataformaPerfil>;
  let http: HttpTestingController;
  let toast: ToastSpy;
  let auth: AuthServiceStub;

  beforeEach(async () => {
    toast = new ToastSpy();
    auth = new AuthServiceStub();
    await TestBed.configureTestingModule({
      imports: [PlataformaPerfil, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: toast },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  /**
   * Crea el componente. Debe llamarse DESPUES de configurar `auth`, porque el
   * componente decide en su constructor si carga los datos de empresa.
   */
  function crearComponente(): void {
    fixture = TestBed.createComponent(PlataformaPerfil);
  }

  afterEach(() => http.verify());

  /** Resuelve la carga inicial del perfil con un perfil de plataforma. */
  function resolverPerfil(tenantId: string | null = null): void {
    crearComponente();
    fixture.detectChanges();
    http.expectOne('/api/v1/auth/perfil').flush({
      id: 'b0000000-0000-0000-0000-000000000001',
      identificador: 'superadmin@dessti',
      roles: ['super_admin'],
      tenantId,
    });
    fixture.detectChanges();
  }

  function componenteDe(): PerfilTest {
    return fixture.componentInstance as unknown as PerfilTest;
  }

  it('muestra el identificador legible, los roles y el ambito Plataforma', () => {
    resolverPerfil(null);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('superadmin@dessti');
    expect(texto).toContain('super admin');
    expect(texto).toContain('Plataforma');
  });

  it('marca error cuando las contrasenas no coinciden', () => {
    resolverPerfil();
    const c = componenteDe();
    c.formulario.patchValue({
      passwordActual: 'actual123',
      passwordNueva: 'nuevaSegura1',
      confirmarPassword: 'distinta1',
    });
    c.cambiarPassword();
    expect(c.formulario.controls.confirmarPassword.hasError('noCoincide')).toBe(true);
    // No se emite la peticion mientras el formulario es invalido.
    http.expectNone('/api/v1/auth/perfil/password');
  });

  it('marca error cuando la nueva contrasena es demasiado corta', () => {
    resolverPerfil();
    const c = componenteDe();
    c.formulario.patchValue({
      passwordActual: 'actual123',
      passwordNueva: 'corta',
      confirmarPassword: 'corta',
    });
    c.cambiarPassword();
    expect(c.formulario.controls.passwordNueva.hasError('minlength')).toBe(true);
    http.expectNone('/api/v1/auth/perfil/password');
  });

  it('en el camino de exito (204) envia el cuerpo correcto, notifica y reinicia', () => {
    resolverPerfil();
    const c = componenteDe();
    c.formulario.patchValue({
      passwordActual: 'actual123',
      passwordNueva: 'nuevaSegura1',
      confirmarPassword: 'nuevaSegura1',
    });
    c.cambiarPassword();
    const req = http.expectOne('/api/v1/auth/perfil/password');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      passwordActual: 'actual123',
      passwordNueva: 'nuevaSegura1',
    });
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(toast.exitos).toContain('Contraseña actualizada');
    expect(c.formulario.controls.passwordActual.hasError('required')).toBe(true);
  });

  it('ante 422 muestra el error de contrasena actual incorrecta', () => {
    resolverPerfil();
    const c = componenteDe();
    c.formulario.patchValue({
      passwordActual: 'incorrecta',
      passwordNueva: 'nuevaSegura1',
      confirmarPassword: 'nuevaSegura1',
    });
    c.cambiarPassword();
    http
      .expectOne('/api/v1/auth/perfil/password')
      .flush(
        { detail: 'La contrasena actual no es correcta.' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

    expect(c.formulario.controls.passwordActual.hasError('incorrecta')).toBe(true);
    expect(c.errorPassword()).toContain('La contraseña actual no es correcta.');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    resolverPerfil();
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });

  describe('seccion "Datos de mi empresa"', () => {
    /** Resuelve la carga como admin_empresa: perfil + datos de la empresa. */
    function resolverComoAdminEmpresa(): void {
      auth.configurarAdminEmpresa(true);
      crearComponente();
      fixture.detectChanges();
      http.expectOne('/api/v1/auth/perfil').flush({
        id: 'u1',
        identificador: 'admin@acme.test',
        roles: ['admin_empresa'],
        tenantId: 'tenant-1',
      });
      http.expectOne('/api/v1/empresas/mi-empresa').flush(miEmpresaDto());
      fixture.detectChanges();
    }

    function empresaDe(): PerfilEmpresaTest {
      return fixture.componentInstance as unknown as PerfilEmpresaTest;
    }

    it('para admin_empresa carga GET /empresas/mi-empresa y prellena el formulario', () => {
      resolverComoAdminEmpresa();
      const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(texto).toContain('Datos de mi empresa');
      const v = empresaDe().formularioEmpresa.getRawValue();
      expect(v['nombre']).toBe('Acme');
      expect(v['emailContacto']).toBe('contacto@acme.test');
      expect(v['direccionCiudad']).toBe('Monterrey');
    });

    it('envia PUT /empresas/mi-empresa con el cuerpo correcto', () => {
      resolverComoAdminEmpresa();
      const c = empresaDe();
      c.formularioEmpresa.patchValue({ nombre: 'Acme Nueva', telefono: '5550001111' });
      c.guardarEmpresa();
      const req = http.expectOne('/api/v1/empresas/mi-empresa');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body.nombre).toBe('Acme Nueva');
      expect(req.request.body.emailContacto).toBe('contacto@acme.test');
      expect(req.request.body.telefono).toBe('5550001111');
      expect(req.request.body.direccionCiudad).toBe('Monterrey');
      // El request NO debe incluir rfc/giro/plan/estado.
      expect('rfc' in req.request.body).toBe(false);
      expect('estado' in req.request.body).toBe(false);
      req.flush(miEmpresaDto());
      expect(toast.exitos).toContain('Datos de empresa actualizados');
    });

    it('bloquea el envio cuando falta el correo de contacto (no emite PUT)', () => {
      resolverComoAdminEmpresa();
      const c = empresaDe();
      c.formularioEmpresa.controls.emailContacto.setValue('');
      c.guardarEmpresa();
      http.expectNone('/api/v1/empresas/mi-empresa');
    });

    it('para super_admin la seccion "Datos de mi empresa" NO se renderiza', () => {
      auth.configurarAdminEmpresa(false);
      resolverPerfil(null);
      const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(texto).not.toContain('Datos de mi empresa');
      // No se emite la peticion de datos de empresa para super_admin.
      http.expectNone('/api/v1/empresas/mi-empresa');
    });
  });
});
