// =============================================================================
// Pruebas del dialogo CrearUsuarioDialog: opciones de rol, envio del alta,
// mapeo de errores (409/422) y accesibilidad (Req 4.1, 57)
// -----------------------------------------------------------------------------
// Deterministas, sin zona ni red real. Cubren:
//   - Las opciones de rol provienen de GET /roles/asignables (nombre + descripcion).
//   - Con datos validos el envio dispara POST /usuarios con { identificadorAcceso,
//     password, nombreVisible, rolIds } incluyendo los ids de rol seleccionados.
//   - 409 muestra el error de identificador duplicado.
//   - 422 muestra el error de rol/plan no disponible.
//   - Ausencia de violaciones WCAG 2.1 A/AA.
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { CrearUsuarioDialog } from './crear-usuario-dialog';
import { RolAsignable } from '../services/usuarios.service';
import { esperarSinViolaciones } from '../../../../../testing/axe';

/** MatDialogRef de prueba que registra si se cerro y con que resultado. */
class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

/** Forma minima del componente accedida por las pruebas. */
interface CrearUsuarioDialogTest {
  formulario: { patchValue(v: Record<string, unknown>): void };
  alternar(id: string, seleccionado: boolean): void;
  guardar(): void;
}

function rol(parcial: Partial<RolAsignable> = {}): RolAsignable {
  return { id: 'r1', nombre: 'gerente', modulo: null, descripcion: 'Gestiona la operacion', ...parcial };
}

function usuarioDto() {
  return {
    id: 'u1',
    identificadorAcceso: 'nuevo@acme.test',
    nombreVisible: 'Nuevo',
    activo: true,
    roles: [] as { id: string; nombre: string }[],
  };
}

describe('CrearUsuarioDialog', () => {
  let fixture: ComponentFixture<CrearUsuarioDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  beforeEach(async () => {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [CrearUsuarioDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CrearUsuarioDialog);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Resuelve la carga inicial de roles asignables. */
  function resolverRoles(roles: RolAsignable[]): void {
    fixture.detectChanges();
    http.expectOne('/api/v1/roles/asignables').flush(roles);
    fixture.detectChanges();
  }

  function componenteDe(): CrearUsuarioDialogTest {
    return fixture.componentInstance as unknown as CrearUsuarioDialogTest;
  }

  it('muestra las opciones de rol de /roles/asignables (nombre y descripcion)', () => {
    resolverRoles([rol({ id: 'r1', nombre: 'gerente', descripcion: 'Gestiona la operacion' })]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('gerente');
    expect(texto).toContain('Gestiona la operacion');
  });

  it('envia POST /usuarios con los datos y los ids de rol seleccionados', () => {
    resolverRoles([rol({ id: 'r1' }), rol({ id: 'r2', nombre: 'supervisor' })]);
    const c = componenteDe();
    c.formulario.patchValue({
      nombreVisible: 'Nuevo Usuario',
      identificadorAcceso: 'nuevo@acme.test',
      password: 'secreto12',
    });
    c.alternar('r2', true);
    c.guardar();

    const req = http.expectOne('/api/v1/usuarios');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.identificadorAcceso).toBe('nuevo@acme.test');
    expect(req.request.body.password).toBe('secreto12');
    expect(req.request.body.nombreVisible).toBe('Nuevo Usuario');
    expect(req.request.body.rolIds).toEqual(['r2']);
    req.flush(usuarioDto());
    expect(dialogRef.cerradoCon).toEqual(usuarioDto());
  });

  it('no emite POST /usuarios cuando faltan datos obligatorios', () => {
    resolverRoles([rol()]);
    const c = componenteDe();
    // Sin identificador ni contrasena.
    c.guardar();
    http.expectNone('/api/v1/usuarios');
  });

  it('muestra el error de identificador duplicado ante un 409', () => {
    resolverRoles([rol()]);
    const c = componenteDe();
    c.formulario.patchValue({ identificadorAcceso: 'dup@acme.test', password: 'secreto12' });
    c.guardar();
    http.expectOne('/api/v1/usuarios').flush(
      { title: 'Conflicto' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Ya existe una cuenta con ese identificador');
  });

  it('muestra el error de rol/plan no disponible ante un 422', () => {
    resolverRoles([rol()]);
    const c = componenteDe();
    c.formulario.patchValue({ identificadorAcceso: 'nuevo@acme.test', password: 'secreto12' });
    c.alternar('r1', true);
    c.guardar();
    http.expectOne('/api/v1/usuarios').flush(
      { title: 'No procesable' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('no esta disponible en tu plan');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    resolverRoles([rol()]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
