// =============================================================================
// Pruebas de la vista AdminRoles: catalogo de roles asignables agrupado por
// modulo, roles del Usuario y accesibilidad (Req 27, 28, 57)
// -----------------------------------------------------------------------------
// Deterministas, sin zona ni red real (proyecto zoneless, Vitest + jsdom).
// Cubren:
//   - Render de los roles devueltos por GET /roles/asignables agrupados por
//     modulo (Administracion para los roles transversales con modulo nulo).
//   - No se renderizan roles de modulos no contratados: la vista solo pinta lo
//     que devuelve /roles/asignables (ya filtrado por el backend).
//   - Los roles del Usuario autenticado se muestran como chips (referencia).
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { AdminRoles } from './roles';
import { AuthService } from '../../../../core/auth/auth.service';
import { RolAsignable } from '../services/usuarios.service';
import { esperarSinViolaciones } from '../../../../../testing/axe';

/** AuthService de prueba con roles de cuenta conocidos. */
class AuthServiceStub {
  private readonly rolesUsuario = ['admin_empresa', 'gerente'];
  roles(): readonly string[] {
    return this.rolesUsuario;
  }
}

function rol(parcial: Partial<RolAsignable> = {}): RolAsignable {
  return { id: 'r1', nombre: 'gerente', modulo: null, descripcion: 'Gestiona la operacion', ...parcial };
}

describe('AdminRoles', () => {
  let fixture: ComponentFixture<AdminRoles>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminRoles, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminRoles);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Resuelve la carga inicial del catalogo de roles asignables. */
  function resolverRoles(roles: RolAsignable[]): void {
    fixture.detectChanges();
    http.expectOne('/api/v1/roles/asignables').flush(roles);
    fixture.detectChanges();
  }

  it('renderiza los roles de /roles/asignables agrupados por modulo', () => {
    resolverRoles([
      rol({ id: 'r1', nombre: 'admin_empresa', modulo: null, descripcion: 'Administra la empresa' }),
      rol({ id: 'r2', nombre: 'vendedor', modulo: 'comercial', descripcion: 'Opera ventas' }),
    ]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    // Grupo transversal (modulo nulo) etiquetado como Administracion.
    expect(texto).toContain('Administración');
    expect(texto).toContain('Admin empresa');
    expect(texto).toContain('Administra la empresa');
    // Grupo de modulo humanizado.
    expect(texto).toContain('Comercial');
    expect(texto).toContain('Vendedor');
    expect(texto).toContain('Opera ventas');
  });

  it('solo pinta los roles que devuelve /roles/asignables (sin modulos no contratados)', () => {
    // El backend ya filtra por plan: la vista NO debe inventar roles de modulos
    // como Calidad o Contexto_organizacion que no vienen en la respuesta.
    resolverRoles([rol({ id: 'r1', nombre: 'admin_empresa', modulo: null })]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Admin empresa');
    expect(texto).not.toContain('Calidad');
    expect(texto).not.toContain('Contexto');
  });

  it('muestra los roles del Usuario autenticado como chips', () => {
    resolverRoles([rol()]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Tus roles');
    expect(texto).toContain('Admin empresa');
    expect(texto).toContain('Gerente');
  });

  it('muestra el estado vacio cuando el plan no incluye roles asignables', () => {
    resolverRoles([]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Tu plan no incluye roles asignables');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    resolverRoles([
      rol({ id: 'r1', nombre: 'admin_empresa', modulo: null }),
      rol({ id: 'r2', nombre: 'vendedor', modulo: 'comercial' }),
    ]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
