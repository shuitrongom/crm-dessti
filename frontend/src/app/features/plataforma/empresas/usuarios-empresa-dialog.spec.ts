// =============================================================================
// Pruebas del dialogo UsuariosEmpresaDialog (super_admin) (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Carga las cuentas de la Empresa (GET /empresas/{id}/usuarios) y muestra el
//     identificador de acceso (login), el nombre visible y el estado.
//   - Estado vacio cuando la Empresa no tiene usuarios.
//   - Mapeo del error 404 (empresa no encontrada) a un mensaje en espanol.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { UsuariosEmpresaDialog } from './usuarios-empresa-dialog';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** Forma minima del componente accedida por las pruebas. */
interface UsuariosTest {
  cargando(): boolean;
  error(): string | null;
  usuarios(): unknown[];
}

describe('UsuariosEmpresaDialog', () => {
  let fixture: ComponentFixture<UsuariosEmpresaDialog>;
  let http: HttpTestingController;

  const URL = '/api/v1/empresas/e1/usuarios';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UsuariosEmpresaDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { empresaId: 'e1', empresaNombre: 'Acme' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(UsuariosEmpresaDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function componenteDe(): UsuariosTest {
    return fixture.componentInstance as unknown as UsuariosTest;
  }

  it('carga y muestra las cuentas de la Empresa (login y estado)', () => {
    const c = componenteDe();
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 'u1',
        identificadorAcceso: 'admin@acme.test',
        nombreVisible: 'Admin Acme',
        activo: true,
        roles: ['admin_empresa'],
      },
    ]);
    fixture.detectChanges();

    expect(c.cargando()).toBe(false);
    expect(c.usuarios().length).toBe(1);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('admin@acme.test');
    expect(texto).toContain('Admin Acme');
    expect(texto).toContain('Activo');
  });

  it('muestra el estado vacio cuando la Empresa no tiene usuarios', () => {
    const c = componenteDe();
    http.expectOne(URL).flush([]);
    fixture.detectChanges();

    expect(c.usuarios().length).toBe(0);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('no tiene usuarios');
  });

  it('mapea el error 404 (empresa no encontrada) a un mensaje en espanol', () => {
    const c = componenteDe();
    http.expectOne(URL).flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(c.error()).toContain('No se encontró la empresa');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    http.expectOne(URL).flush([
      {
        id: 'u1',
        identificadorAcceso: 'admin@acme.test',
        nombreVisible: 'Admin Acme',
        activo: true,
        roles: ['admin_empresa'],
      },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
