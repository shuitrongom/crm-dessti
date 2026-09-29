// =============================================================================
// Pruebas del dialogo LevantamientoFormDialog (Req 16)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Alta: POST /levantamientos con el cuerpo (mediciones/tipoSuperficie/
//     condicionesElectricas + vinculos opcionales) y cierra con el creado.
//   - Vinculos vacios se envian como null.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { LevantamientoFormDialog } from './levantamiento-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { LevantamientoSitio } from '../models/operacion.models';
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

function levantamientoDe(over: Partial<LevantamientoSitio> = {}): LevantamientoSitio {
  return {
    id: 'lev-1',
    sitioId: null,
    cotizacionId: null,
    ordenFabricacionId: null,
    mediciones: '3x2 metros',
    tipoSuperficie: 'Muro',
    condicionesElectricas: '110V',
    estado: 'en_proceso',
    completadoPor: null,
    completadoEn: null,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as LevantamientoSitio;
}

interface Probe {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
}

describe('LevantamientoFormDialog', () => {
  let fixture: ComponentFixture<LevantamientoFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [LevantamientoFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LevantamientoFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('alta: POST /levantamientos con el cuerpo y cierra con el creado', async () => {
    await montar();
    comp().form.patchValue({
      mediciones: '3x2 metros',
      tipoSuperficie: 'Muro de concreto',
      condicionesElectricas: '110V disponible',
      sitioId: 'sit-1',
    });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/levantamientos' && r.method === 'POST');
    expect(req.request.body.mediciones).toBe('3x2 metros');
    expect(req.request.body.tipoSuperficie).toBe('Muro de concreto');
    expect(req.request.body.condicionesElectricas).toBe('110V disponible');
    expect(req.request.body.sitioId).toBe('sit-1');
    // Los vinculos no capturados viajan como null.
    expect(req.request.body.cotizacionId).toBeNull();
    expect(req.request.body.ordenFabricacionId).toBeNull();
    req.flush(levantamientoDe());
    expect((dialogRef.cerradoCon as LevantamientoSitio).id).toBe('lev-1');
  });

  it('no envia peticion si el formulario es invalido', async () => {
    await montar();
    comp().guardar();
    http.expectNone((r) => r.url === '/api/v1/levantamientos');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
