// =============================================================================
// Pruebas del dialogo EditarUsuarioDialog: prellenado, guardado selectivo de
// nombre/roles y accesibilidad (Req 4.3, 57)
// -----------------------------------------------------------------------------
// Deterministas, sin zona ni red real. Cubren:
//   - El formulario se prellena con el nombre y los roles actuales de la cuenta.
//   - Guardar solo el nombre llama PUT /usuarios/{id}.
//   - Guardar solo los roles llama PUT /usuarios/{id}/roles con los ids elegidos.
//   - Ausencia de violaciones WCAG 2.1 A/AA.
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { EditarUsuarioDialog, EditarUsuarioDialogData } from './editar-usuario-dialog';
import { RolAsignable, Usuario } from '../services/usuarios.service';
import { esperarSinViolaciones } from '../../../../../testing/axe';

class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

interface EditarUsuarioDialogTest {
  formulario: {
    getRawValue(): { nombreVisible: string };
    controls: { nombreVisible: { value: string; setValue(v: string): void } };
  };
  estaSeleccionado(id: string): boolean;
  alternar(id: string, seleccionado: boolean): void;
  guardar(): void;
}

function rol(parcial: Partial<RolAsignable> = {}): RolAsignable {
  return { id: 'r1', nombre: 'gerente', modulo: null, descripcion: 'Gestiona la operacion', ...parcial };
}

function usuario(parcial: Partial<Usuario> = {}): Usuario {
  return {
    id: 'u5',
    identificadorAcceso: 'ana@acme.test',
    nombreVisible: 'Ana Lopez',
    activo: true,
    roles: [{ id: 'r1', nombre: 'gerente' }],
    ...parcial,
  };
}

function usuarioDto(parcial: Partial<Usuario> = {}): Usuario {
  return usuario(parcial);
}

describe('EditarUsuarioDialog', () => {
  let fixture: ComponentFixture<EditarUsuarioDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function crearFixture(data: EditarUsuarioDialogData): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [EditarUsuarioDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(EditarUsuarioDialog);
    http = TestBed.inject(HttpTestingController);
  }

  function resolverRoles(roles: RolAsignable[]): void {
    fixture.detectChanges();
    http.expectOne('/api/v1/roles/asignables').flush(roles);
    fixture.detectChanges();
  }

  function componenteDe(): EditarUsuarioDialogTest {
    return fixture.componentInstance as unknown as EditarUsuarioDialogTest;
  }

  afterEach(() => http.verify());

  it('prellena el nombre y marca los roles actuales de la cuenta', async () => {
    await crearFixture({ usuario: usuario() });
    resolverRoles([rol({ id: 'r1' }), rol({ id: 'r2', nombre: 'supervisor' })]);
    const c = componenteDe();
    expect(c.formulario.controls.nombreVisible.value).toBe('Ana Lopez');
    expect(c.estaSeleccionado('r1')).toBe(true);
    expect(c.estaSeleccionado('r2')).toBe(false);
    // El identificador se muestra en un campo de solo lectura (valor del input).
    const inputs = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('input'),
    ) as HTMLInputElement[];
    expect(inputs.some((i) => i.value === 'ana@acme.test')).toBe(true);
  });

  it('guardar solo el nombre llama PUT /usuarios/{id}', async () => {
    await crearFixture({ usuario: usuario() });
    resolverRoles([rol({ id: 'r1' })]);
    const c = componenteDe();
    c.formulario.controls.nombreVisible.setValue('Ana Maria Lopez');
    c.guardar();

    const req = http.expectOne('/api/v1/usuarios/u5');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.nombreVisible).toBe('Ana Maria Lopez');
    req.flush(usuarioDto({ nombreVisible: 'Ana Maria Lopez' }));
    // Los roles no cambiaron: no se llama al endpoint de roles.
    http.expectNone('/api/v1/usuarios/u5/roles');
    expect(dialogRef.cerradoCon).toBeTruthy();
  });

  it('guardar solo los roles llama PUT /usuarios/{id}/roles con los ids elegidos', async () => {
    await crearFixture({ usuario: usuario() });
    resolverRoles([rol({ id: 'r1' }), rol({ id: 'r2', nombre: 'supervisor' })]);
    const c = componenteDe();
    // Añade r2, mantiene r1; el nombre no cambia.
    c.alternar('r2', true);
    c.guardar();

    // El nombre no cambio: no se llama PUT /usuarios/u5.
    http.expectNone('/api/v1/usuarios/u5');
    const req = http.expectOne('/api/v1/usuarios/u5/roles');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.rolIds).toEqual(['r1', 'r2']);
    req.flush(usuarioDto());
    expect(dialogRef.cerradoCon).toBeTruthy();
  });

  it('sin cambios cierra sin peticiones', async () => {
    await crearFixture({ usuario: usuario() });
    resolverRoles([rol({ id: 'r1' })]);
    const c = componenteDe();
    c.guardar();
    http.expectNone('/api/v1/usuarios/u5');
    http.expectNone('/api/v1/usuarios/u5/roles');
    expect(dialogRef.cerradoCon).toBeUndefined();
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await crearFixture({ usuario: usuario() });
    resolverRoles([rol()]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
