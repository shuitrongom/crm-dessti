// =============================================================================
// Pruebas del dialogo EditarEmpresaDialog: prellenado, PUT /empresas/{id},
// correo obligatorio, RFC duplicado (409) y accesibilidad (Req 24, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - El formulario se prellena con los valores actuales de la Empresa.
//   - Con datos validos, el envio dispara PUT /empresas/{id} con el cuerpo
//     descriptivo/fiscal (nombre, rfc, emailContacto, direccion*, ...).
//   - Sin correo de contacto el envio se bloquea (no se emite el PUT).
//   - Un 409 marca el error de RFC duplicado en el campo y el formulario.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { EditarEmpresaDialog } from './editar-empresa-dialog';
import { Empresa } from '../models/plataforma.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** MatDialogRef de prueba que registra si se cerro y con que resultado. */
class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

function empresa(parcial: Partial<Empresa> = {}): Empresa {
  return {
    id: 'e1',
    nombre: 'Acme',
    rfc: 'ABCD901231XYZ',
    giroId: 'g1',
    estado: 'activa',
    brandingNombreVisible: null,
    brandingLogo: null,
    nombreComercial: 'Acme Signs',
    emailContacto: 'contacto@acme.test',
    telefono: '5551234567',
    sitioWeb: 'https://acme.test',
    direccion: { calle: 'Av. Central 100', ciudad: 'Monterrey', estado: 'NL', cp: '64000', pais: 'Mexico' },
    notas: 'Cliente clave',
    fechaCancelacion: null,
    finPeriodoGracia: null,
    planVigente: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...parcial,
  };
}

/** Forma minima del componente accedida por las pruebas. */
interface EditarEmpresaDialogTest {
  formulario: {
    getRawValue(): Record<string, unknown>;
    patchValue(v: Record<string, unknown>): void;
    controls: {
      rfc: { hasError(k: string): boolean };
      emailContacto: { setValue(v: string): void };
    };
  };
  guardar(): void;
  error(): string | null;
}

describe('EditarEmpresaDialog', () => {
  let fixture: ComponentFixture<EditarEmpresaDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function crear(data: Empresa): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [EditarEmpresaDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { empresa: data } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(EditarEmpresaDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  function componenteDe(): EditarEmpresaDialogTest {
    return fixture.componentInstance as unknown as EditarEmpresaDialogTest;
  }

  it('prellena el formulario con los valores actuales de la empresa', async () => {
    await crear(empresa());
    const v = componenteDe().formulario.getRawValue();
    expect(v['nombre']).toBe('Acme');
    expect(v['rfc']).toBe('ABCD901231XYZ');
    expect(v['emailContacto']).toBe('contacto@acme.test');
    expect(v['nombreComercial']).toBe('Acme Signs');
    expect(v['direccionCiudad']).toBe('Monterrey');
    expect(v['direccionCp']).toBe('64000');
  });

  it('envia PUT /empresas/{id} con el cuerpo descriptivo/fiscal', async () => {
    await crear(empresa());
    const c = componenteDe();
    c.formulario.patchValue({ nombre: 'Acme Actualizada', telefono: '5559998877' });
    c.guardar();
    const req = http.expectOne('/api/v1/empresas/e1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.nombre).toBe('Acme Actualizada');
    expect(req.request.body.rfc).toBe('ABCD901231XYZ');
    expect(req.request.body.emailContacto).toBe('contacto@acme.test');
    expect(req.request.body.telefono).toBe('5559998877');
    expect(req.request.body.direccionCiudad).toBe('Monterrey');
    req.flush(empresa({ nombre: 'Acme Actualizada' }));
    expect(dialogRef.cerradoCon).toMatchObject({ nombre: 'Acme Actualizada' });
  });

  it('bloquea el envio cuando falta el correo de contacto (no emite PUT)', async () => {
    await crear(empresa({ emailContacto: null }));
    const c = componenteDe();
    c.formulario.controls.emailContacto.setValue('');
    c.guardar();
    http.expectNone('/api/v1/empresas/e1');
  });

  it('ante 409 marca el error de RFC duplicado', async () => {
    await crear(empresa());
    const c = componenteDe();
    c.guardar();
    http
      .expectOne('/api/v1/empresas/e1')
      .flush(
        { detail: 'RFC duplicado' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(c.formulario.controls.rfc.hasError('duplicado')).toBe(true);
    expect(c.error()).toContain('Ya existe una empresa con ese RFC.');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await crear(empresa());
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
