// =============================================================================
// Pruebas del dialogo MaterialFormDialog (Req 18)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Alta: POST /materiales con el cuerpo (nombre/unidad/stockMinimo) y cierra
//     con el material creado.
//   - Edicion: prellena y hace PUT /materiales/{id}.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { MaterialFormDialog } from './material-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { Material } from '../models/operacion.models';
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

function materialDe(over: Partial<Material> = {}): Material {
  return {
    id: 'm-1',
    nombre: 'Perfil de aluminio',
    unidadMedida: 'metro',
    stockMinimo: 5,
    existencias: 0,
    stockBajo: true,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as Material;
}

interface Probe {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
  esEdicion: boolean;
}

describe('MaterialFormDialog', () => {
  let fixture: ComponentFixture<MaterialFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(data: { material?: Material } = {}): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [MaterialFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(MaterialFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('alta: POST /materiales con el cuerpo y cierra con el creado', async () => {
    await montar();
    comp().form.patchValue({ nombre: 'Tornillo', unidadMedida: 'pieza', stockMinimo: 100 });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/materiales' && r.method === 'POST');
    expect(req.request.body.nombre).toBe('Tornillo');
    expect(req.request.body.unidadMedida).toBe('pieza');
    expect(req.request.body.stockMinimo).toBe(100);
    req.flush(materialDe({ nombre: 'Tornillo' }));
    expect((dialogRef.cerradoCon as Material).id).toBe('m-1');
  });

  it('edicion: prellena y hace PUT /materiales/{id}', async () => {
    await montar({ material: materialDe({ id: 'm-9', nombre: 'Editado' }) });
    expect(comp().esEdicion).toBe(true);
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/materiales/m-9' && r.method === 'PUT');
    expect(req.request.body.nombre).toBe('Editado');
    req.flush(materialDe({ id: 'm-9', nombre: 'Editado' }));
    expect((dialogRef.cerradoCon as Material).id).toBe('m-9');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await esperarSinViolaciones(fixture);
  });
});
