// =============================================================================
// Pruebas del dialogo de alta/edicion de Cliente (Req 5) — ClienteFormDialog
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real, la VALIDACION EN EL
// CLIENTE y el envio del cuerpo completo, mas el modo edicion:
//   - RFC: patron invalido bloquea el envio; RFC valido (12/13) pasa; se envia
//     en mayusculas y el input se limita a 13 caracteres.
//   - telefono: exactamente 10 digitos (15 no permitido); no digitos rechazados.
//   - email: formato invalido marca error; valido lo limpia.
//   - al menos un contacto: ambos vacios -> error de formulario.
//   - tipoPersona: se mapea a 'fisica'/'moral' en el cuerpo.
//   - envio ALTA: POST /clientes; cierra el dialogo con el cliente creado.
//   - envio EDICION: PUT /clientes/{id} con el cliente prellenado.
//   - 409: error inline en RFC (duplicado).
//   - autocompletado de direccion: autollena los campos.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Observable } from 'rxjs';

import { ClienteFormDialog } from './cliente-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** MatDialogRef de prueba que registra si se cerro y con que resultado. */
class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

/** Overlay de prueba: ejecuta el observable de origen tal cual (sin UI). */
class OverlayStub {
  ejecutar<T>(origen: Observable<T>): Observable<T> {
    return origen;
  }
}

/** Forma minima del componente accedida por las pruebas. */
interface DialogTest {
  form: {
    patchValue(v: Record<string, unknown>): void;
    getRawValue(): Record<string, unknown>;
    hasError(k: string): boolean;
    controls: {
      rfc: { hasError(k: string): boolean; setValue(v: string): void };
      telefono: { hasError(k: string): boolean };
      email: { hasError(k: string): boolean };
    };
  };
  onDireccion(d: { calle: string; ciudad: string; estado: string; cp: string; pais: string }): void;
  guardar(): void;
  esEdicion: boolean;
}

/** ClienteDto minimo para el modo edicion. */
function clienteDto(over: Record<string, unknown> = {}) {
  return {
    id: 'c1',
    nombre: 'Acme',
    rfc: 'ABC010101AB1',
    email: 'ventas@acme.test',
    telefono: '5551234567',
    nombreComercial: null,
    tipoPersona: null,
    telefonoAdicional: null,
    direccionCalle: null,
    direccionCiudad: null,
    direccionEstado: null,
    direccionCp: null,
    direccionPais: null,
    notas: null,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  };
}

describe('ClienteFormDialog', () => {
  let fixture: ComponentFixture<ClienteFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(data: { cliente?: unknown } = {}): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [ClienteFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ClienteFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): DialogTest {
    return fixture.componentInstance as unknown as DialogTest;
  }

  /** Rellena un alta valida minima (nombre, RFC y un contacto). */
  function altaValidaMinima(): void {
    comp().form.patchValue({ nombre: 'Acme', rfc: 'ABC010101AB1', telefono: '5551234567' });
  }

  it('un RFC con patron invalido bloquea el envio y marca error', async () => {
    await montar();
    comp().form.patchValue({ nombre: 'Acme', rfc: 'INVALIDO', telefono: '5551234567' });
    comp().guardar();
    expect(comp().form.controls.rfc.hasError('rfc')).toBe(true);
    http.expectNone((r) => r.method === 'POST');
  });

  it('acepta un RFC de persona moral (12) y de persona fisica (13)', async () => {
    await montar();
    comp().form.patchValue({ nombre: 'Acme', rfc: 'ABC010101AB1', telefono: '5551234567' });
    expect(comp().form.controls.rfc.hasError('rfc')).toBe(false);
    comp().form.controls.rfc.setValue('ABCD901231XYZ');
    expect(comp().form.controls.rfc.hasError('rfc')).toBe(false);
  });

  it('envia el RFC en mayusculas, limita el input a 13 y cierra con el cliente creado', async () => {
    await montar();
    altaValidaMinima();
    comp().form.controls.rfc.setValue('abc010101ab1');
    fixture.detectChanges();
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/clientes' && r.method === 'POST');
    expect(req.request.body.rfc).toBe('ABC010101AB1');
    const input = (fixture.nativeElement as HTMLElement).querySelector(
      'input[formControlName="rfc"]',
    ) as HTMLInputElement;
    expect(input.maxLength).toBe(13);
    const creado = clienteDto();
    req.flush(creado);
    expect((dialogRef.cerradoCon as { id: string }).id).toBe('c1');
  });

  it('el telefono exige exactamente 10 digitos (15 no permitido)', async () => {
    await montar();
    comp().form.patchValue({ nombre: 'Acme', rfc: 'ABC010101AB1', telefono: '123456789012345' });
    fixture.detectChanges();
    expect(comp().form.controls.telefono.hasError('pattern')).toBe(true);
    const input = (fixture.nativeElement as HTMLElement).querySelector(
      'input[formControlName="telefono"]',
    ) as HTMLInputElement;
    expect(input.maxLength).toBe(10);
  });

  it('el telefono rechaza caracteres no numericos', async () => {
    await montar();
    comp().form.patchValue({ nombre: 'Acme', rfc: 'ABC010101AB1', telefono: '55512abcd0' });
    expect(comp().form.controls.telefono.hasError('pattern')).toBe(true);
  });

  it('un correo invalido marca error y uno valido lo limpia', async () => {
    await montar();
    comp().form.patchValue({ email: 'no-es-correo' });
    expect(comp().form.controls.email.hasError('email')).toBe(true);
    comp().form.patchValue({ email: 'ventas@acme.test' });
    expect(comp().form.controls.email.hasError('email')).toBe(false);
  });

  it('exige al menos un contacto: ambos vacios -> error de formulario', async () => {
    await montar();
    comp().form.patchValue({ nombre: 'Acme', rfc: 'ABC010101AB1', email: '', telefono: '' });
    expect(comp().form.hasError('contacto')).toBe(true);
    comp().guardar();
    http.expectNone((r) => r.method === 'POST');
    comp().form.patchValue({ email: 'ventas@acme.test' });
    expect(comp().form.hasError('contacto')).toBe(false);
  });

  it('mapea el tipo de persona a "moral" en el cuerpo', async () => {
    await montar();
    altaValidaMinima();
    comp().form.patchValue({ tipoPersona: 'moral' });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/clientes' && r.method === 'POST');
    expect(req.request.body.tipoPersona).toBe('moral');
    req.flush(clienteDto());
  });

  it('envia el cuerpo completo con los nuevos campos', async () => {
    await montar();
    comp().form.patchValue({
      nombre: 'Acme',
      nombreComercial: 'Acme MX',
      tipoPersona: 'fisica',
      rfc: 'ABCD901231XYZ',
      email: 'ventas@acme.test',
      telefono: '5551234567',
      telefonoAdicional: '5559876543',
      direccionCalle: 'Av. Central 100',
      direccionCiudad: 'Monterrey',
      direccionEstado: 'NL',
      direccionCp: '64000',
      direccionPais: 'Mexico',
      notas: 'Cliente preferente',
    });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/clientes' && r.method === 'POST');
    const body = req.request.body;
    expect(body.nombreComercial).toBe('Acme MX');
    expect(body.tipoPersona).toBe('fisica');
    expect(body.telefonoAdicional).toBe('5559876543');
    expect(body.direccionCiudad).toBe('Monterrey');
    expect(body.direccionCp).toBe('64000');
    expect(body.notas).toBe('Cliente preferente');
    req.flush(clienteDto());
  });

  it('en modo edicion prellena el form y hace PUT /clientes/{id}', async () => {
    await montar({ cliente: clienteDto({ nombre: 'Acme Editado' }) });
    expect(comp().esEdicion).toBe(true);
    expect(comp().form.getRawValue()['nombre']).toBe('Acme Editado');
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/clientes/c1' && r.method === 'PUT');
    expect(req.request.body.nombre).toBe('Acme Editado');
    req.flush(clienteDto({ nombre: 'Acme Editado' }));
    expect((dialogRef.cerradoCon as { id: string }).id).toBe('c1');
  });

  it('al elegir una sugerencia del autocompletado, autollena los campos de direccion', async () => {
    await montar();
    comp().onDireccion({
      calle: 'Avenida Juarez 100',
      ciudad: 'Toluca',
      estado: 'Estado de Mexico',
      cp: '50000',
      pais: 'Mexico',
    });
    const v = comp().form.getRawValue();
    expect(v['direccionCalle']).toBe('Avenida Juarez 100');
    expect(v['direccionCiudad']).toBe('Toluca');
    expect(v['direccionEstado']).toBe('Estado de Mexico');
    expect(v['direccionCp']).toBe('50000');
    expect(v['direccionPais']).toBe('Mexico');
  });

  it('ante 409 coloca el error de RFC duplicado en el campo RFC', async () => {
    await montar();
    altaValidaMinima();
    comp().guardar();
    http
      .expectOne((r) => r.url === '/api/v1/clientes' && r.method === 'POST')
      .flush({ detail: 'RFC duplicado' }, { status: 409, statusText: 'Conflict' });
    expect(comp().form.controls.rfc.hasError('duplicado')).toBe(true);
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
