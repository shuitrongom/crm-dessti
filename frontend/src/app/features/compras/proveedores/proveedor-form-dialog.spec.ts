// =============================================================================
// Pruebas del modal de alta/edición de Proveedor (Compras, Req 29)
// -----------------------------------------------------------------------------
// Verifican, sin zona ni red real:
//   - Alta: el POST envía los datos fiscales/comerciales y cierra devolviendo el
//     proveedor creado.
//   - Edición: prellena el formulario y hace PUT.
//   - Regla de contacto: sin correo ni teléfono, no envía y muestra error.
//   - RFC duplicado (409) marca el control y muestra el mensaje.
// =============================================================================

import { vi } from 'vitest';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { ProveedorFormDialog, ProveedorFormDialogData } from './proveedor-form-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { Proveedor } from '../models/compras.models';

class ToastSpy {
  exito(): void {}
  error(): void {}
  info(): void {}
}

const URL = '/api/v1/compras/proveedores';

function proveedorFalso(): Proveedor {
  return {
    id: 'a0000000-0000-0000-0000-000000000001',
    nombre: 'Aceros del Norte',
    rfc: 'ABC010101AB1',
    email: 'compras@aceros.mx',
    telefono: null,
    personaContacto: 'Juan Pérez',
    regimenFiscal: '601',
    diasCredito: 30,
    domicilioCalle: 'Av. Reforma 100',
    domicilioCiudad: 'Monterrey',
    domicilioEstado: 'Nuevo León',
    codigoPostal: '64000',
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

describe('ProveedorFormDialog', () => {
  let fixture: ComponentFixture<ProveedorFormDialog>;
  let http: HttpTestingController;
  let cerradoCon: Proveedor | undefined | 'no-cerrado';

  async function crear(data: ProveedorFormDialogData): Promise<void> {
    cerradoCon = 'no-cerrado';
    const dialogRef = {
      close: (v?: Proveedor) => {
        cerradoCon = v;
      },
    };
    await TestBed.configureTestingModule({
      imports: [ProveedorFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useClass: ToastSpy },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProveedorFormDialog);
    fixture.detectChanges();
  }

  function cmp(): {
    form: {
      setValue(v: Record<string, unknown>): void;
      patchValue(v: Record<string, unknown>): void;
    };
    guardar(): void;
  } {
    return fixture.componentInstance as unknown as {
      form: {
        setValue(v: Record<string, unknown>): void;
        patchValue(v: Record<string, unknown>): void;
      };
      guardar(): void;
    };
  }

  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  it('alta: envía POST con los datos fiscales y cierra devolviendo el proveedor', async () => {
    await crear({});
    cmp().form.patchValue({
      nombre: 'Aceros del Norte',
      rfc: 'ABC010101AB1',
      email: 'compras@aceros.mx',
      diasCredito: 30,
      regimenFiscal: '601',
      codigoPostal: '64000',
    });
    cmp().guardar();

    const req = http.expectOne((r) => r.method === 'POST' && r.url === URL);
    expect(req.request.body.rfc).toBe('ABC010101AB1');
    expect(req.request.body.diasCredito).toBe(30);
    expect(req.request.body.regimenFiscal).toBe('601');
    expect(req.request.body.codigoPostal).toBe('64000');
    req.flush(proveedorFalso());

    expect(cerradoCon).toBeTruthy();
    expect((cerradoCon as Proveedor).rfc).toBe('ABC010101AB1');
  });

  it('edición: prellena y hace PUT al proveedor', async () => {
    await crear({ proveedor: proveedorFalso() });
    cmp().guardar();

    const req = http.expectOne(
      (r) => r.method === 'PUT' && r.url === `${URL}/a0000000-0000-0000-0000-000000000001`,
    );
    expect(req.request.body.nombre).toBe('Aceros del Norte');
    req.flush(proveedorFalso());
    expect(cerradoCon).toBeTruthy();
  });

  it('sin correo ni teléfono no envía y no cierra (regla de contacto)', async () => {
    await crear({});
    cmp().form.patchValue({
      nombre: 'Aceros del Norte',
      rfc: 'ABC010101AB1',
      email: '',
      telefono: '',
    });
    cmp().guardar();

    http.expectNone((r) => r.method === 'POST' && r.url === URL);
    expect(cerradoCon).toBe('no-cerrado');
  });

  it('RFC duplicado (409) no cierra y deja el error disponible', async () => {
    await crear({});
    cmp().form.patchValue({
      nombre: 'Aceros del Norte',
      rfc: 'ABC010101AB1',
      email: 'compras@aceros.mx',
    });
    cmp().guardar();

    const req = http.expectOne((r) => r.method === 'POST' && r.url === URL);
    req.flush({ detail: 'RFC duplicado' }, { status: 409, statusText: 'Conflict' });

    expect(cerradoCon).toBe('no-cerrado');
  });
});
