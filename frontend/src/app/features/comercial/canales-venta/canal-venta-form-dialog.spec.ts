// =============================================================================
// Pruebas del dialogo CanalVentaFormDialog (Req 63.1)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Alta: POST /canales-venta con el cuerpo (nombre/descripcion) y cierra con
//     el canal creado.
//   - Edicion: prellena y hace PUT /canales-venta/{id}.
//   - 409 (nombre duplicado): marca el error inline en el campo nombre y NO
//     cierra el dialogo.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { CanalVentaFormDialog } from './canal-venta-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { CanalVenta } from '../models/comercial.models';
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

function canalDe(over: Partial<CanalVenta> = {}): CanalVenta {
  return {
    id: 'cv-1',
    nombre: 'Venta directa',
    descripcion: null,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as CanalVenta;
}

interface Probe {
  form: {
    patchValue(v: Record<string, unknown>): void;
    controls: { nombre: { hasError(codigo: string): boolean } };
  };
  guardar(): void;
  esEdicion: boolean;
}

describe('CanalVentaFormDialog', () => {
  let fixture: ComponentFixture<CanalVentaFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(data: { canal?: CanalVenta } = {}): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [CanalVentaFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CanalVentaFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('alta: POST /canales-venta con el cuerpo y cierra con el creado', async () => {
    await montar();
    comp().form.patchValue({ nombre: 'Referidos', descripcion: 'Clientes que llegan por recomendación' });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/canales-venta' && r.method === 'POST');
    expect(req.request.body.nombre).toBe('Referidos');
    expect(req.request.body.descripcion).toBe('Clientes que llegan por recomendación');
    req.flush(canalDe({ nombre: 'Referidos' }));
    expect((dialogRef.cerradoCon as CanalVenta).id).toBe('cv-1');
  });

  it('alta sin descripcion: envia descripcion null', async () => {
    await montar();
    comp().form.patchValue({ nombre: 'Redes' });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/canales-venta' && r.method === 'POST');
    expect(req.request.body.descripcion).toBeNull();
    req.flush(canalDe({ nombre: 'Redes' }));
  });

  it('edicion: prellena y hace PUT /canales-venta/{id}', async () => {
    await montar({ canal: canalDe({ id: 'cv-9', nombre: 'Marketplace' }) });
    expect(comp().esEdicion).toBe(true);
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/canales-venta/cv-9' && r.method === 'PUT');
    expect(req.request.body.nombre).toBe('Marketplace');
    req.flush(canalDe({ id: 'cv-9', nombre: 'Marketplace' }));
    expect((dialogRef.cerradoCon as CanalVenta).id).toBe('cv-9');
  });

  it('409: marca error de duplicado en nombre y NO cierra', async () => {
    await montar();
    comp().form.patchValue({ nombre: 'Venta directa' });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/canales-venta' && r.method === 'POST');
    req.flush({ mensaje: 'duplicado' }, { status: 409, statusText: 'Conflict' });
    expect(comp().form.controls.nombre.hasError('duplicado')).toBe(true);
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await esperarSinViolaciones(fixture);
  });
});
