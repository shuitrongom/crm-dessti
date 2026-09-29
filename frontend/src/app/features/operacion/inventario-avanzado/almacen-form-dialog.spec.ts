// =============================================================================
// Pruebas del dialogo AlmacenFormDialog (Req 60)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Alta: POST /inventario-avanzado/almacenes con { nombre, tipo } y cierra con
//     el almacén creado.
//   - Edicion: prellena y hace PUT /inventario-avanzado/almacenes/{id}.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { AlmacenFormDialog } from './almacen-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { Almacen } from '../models/operacion.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

class OverlayStub {
  ejecutar<T>(origen: Observable<T>): Observable<T> {
    return origen;
  }
}

function almacenDe(over: Partial<Almacen> = {}): Almacen {
  return {
    id: 'alm-1',
    nombre: 'Bodega Central',
    tipo: 'bodega',
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as Almacen;
}

interface Probe {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
  esEdicion: boolean;
}

describe('AlmacenFormDialog', () => {
  let fixture: ComponentFixture<AlmacenFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  const URL = '/api/v1/inventario-avanzado/almacenes';

  async function montar(data: { almacen?: Almacen } = {}): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [AlmacenFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AlmacenFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('alta: POST /almacenes con el cuerpo y cierra con el creado', async () => {
    await montar();
    comp().form.patchValue({ nombre: 'Sucursal Norte', tipo: 'sucursal' });
    comp().guardar();
    const req = http.expectOne((r) => r.url === URL && r.method === 'POST');
    expect(req.request.body.nombre).toBe('Sucursal Norte');
    expect(req.request.body.tipo).toBe('sucursal');
    req.flush(almacenDe({ nombre: 'Sucursal Norte', tipo: 'sucursal' }));
    expect((dialogRef.cerradoCon as Almacen).id).toBe('alm-1');
  });

  it('edicion: prellena y hace PUT /almacenes/{id}', async () => {
    await montar({ almacen: almacenDe({ id: 'alm-9', nombre: 'Editado' }) });
    expect(comp().esEdicion).toBe(true);
    comp().guardar();
    const req = http.expectOne((r) => r.url === `${URL}/alm-9` && r.method === 'PUT');
    expect(req.request.body.nombre).toBe('Editado');
    req.flush(almacenDe({ id: 'alm-9', nombre: 'Editado' }));
    expect((dialogRef.cerradoCon as Almacen).id).toBe('alm-9');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
