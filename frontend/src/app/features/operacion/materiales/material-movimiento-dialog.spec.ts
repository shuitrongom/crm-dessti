// =============================================================================
// Pruebas del dialogo MaterialMovimientoDialog (Req 18.2)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - POST /materiales/{id}/movimientos con el cuerpo (tipo/cantidad/motivo) y
//     cierra con `true`.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { MaterialMovimientoDialog } from './material-movimiento-dialog';
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

const MATERIAL: Material = {
  id: 'm-1',
  nombre: 'Perfil de aluminio',
  unidadMedida: 'metro',
  stockMinimo: 5,
  existencias: 12,
  stockBajo: false,
  activo: true,
  version: 0,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

interface Probe {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
}

describe('MaterialMovimientoDialog', () => {
  let fixture: ComponentFixture<MaterialMovimientoDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [MaterialMovimientoDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: MAT_DIALOG_DATA, useValue: { material: MATERIAL } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(MaterialMovimientoDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('registra un movimiento (POST) y cierra con true', async () => {
    await montar();
    comp().form.patchValue({ tipo: 'entrada', cantidad: 8, motivo: 'Compra' });
    comp().guardar();
    const req = http.expectOne(
      (r) => r.url === '/api/v1/materiales/m-1/movimientos' && r.method === 'POST',
    );
    expect(req.request.body.tipo).toBe('entrada');
    expect(req.request.body.cantidad).toBe(8);
    expect(req.request.body.motivo).toBe('Compra');
    req.flush({});
    expect(dialogRef.cerradoCon).toBe(true);
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await esperarSinViolaciones(fixture);
  });
});
