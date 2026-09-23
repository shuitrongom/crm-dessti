// =============================================================================
// Pruebas del dialogo VigenciaDialog (super_admin)
// (plan-suscripcion-empresa-super-admin) (Req 8)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Al confirmar hace PUT /suscripciones/{id}/vigencia con
//     { vigenciaInicio, vigenciaFin } y cierra devolviendo la Suscripcion
//     actualizada.
//   - Un error 422 (vigencia invalida) muestra el mensaje del backend SIN
//     cerrar el dialogo.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { VigenciaDialog, VigenciaDialogData } from './vigencia-dialog';
import { Suscripcion } from '../models/plataforma.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** MatDialogRef de prueba que registra si se cerro y con que resultado. */
class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

function suscripcion(parcial: Partial<Suscripcion> = {}): Suscripcion {
  return {
    id: 's1',
    tenantId: 'e1',
    planId: 'p1',
    tipoInstrumento: 'plan',
    paqueteSuscripcionId: null,
    estado: 'activa',
    vigenciaInicio: '2026-01-01',
    vigenciaFin: null,
    modulosHabilitados: null,
    monedaFacturacion: null,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...parcial,
  };
}

interface VigenciaTest {
  formulario: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
  error(): string | null;
}

describe('VigenciaDialog', () => {
  let fixture: ComponentFixture<VigenciaDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  const URL = '/api/v1/suscripciones/s1/vigencia';

  async function crear(data: VigenciaDialogData): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [VigenciaDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(VigenciaDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function componenteDe(): VigenciaTest {
    return fixture.componentInstance as unknown as VigenciaTest;
  }

  it('al confirmar hace PUT con { vigenciaInicio, vigenciaFin } y cierra con la suscripcion', async () => {
    await crear({
      suscripcionId: 's1',
      vigenciaInicioActual: '2026-01-01',
      vigenciaFinActual: null,
      empresaNombre: 'Acme',
    });
    const c = componenteDe();
    c.formulario.patchValue({ vigenciaInicio: '2026-03-01', vigenciaFin: '2026-12-31' });
    c.guardar();
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      vigenciaInicio: '2026-03-01',
      vigenciaFin: '2026-12-31',
    });
    const actualizada = suscripcion({ vigenciaInicio: '2026-03-01', vigenciaFin: '2026-12-31' });
    req.flush(actualizada);
    expect(dialogRef.cerradoCon).toEqual(actualizada);
  });

  it('un error 422 (vigencia invalida) muestra el mensaje del backend sin cerrar el dialogo', async () => {
    await crear({
      suscripcionId: 's1',
      vigenciaInicioActual: '2026-01-01',
      vigenciaFinActual: null,
      empresaNombre: 'Acme',
    });
    const c = componenteDe();
    c.formulario.patchValue({ vigenciaInicio: '2026-12-31', vigenciaFin: '2026-01-01' });
    c.guardar();
    http.expectOne(URL).flush(
      { detail: 'La vigencia de inicio no puede ser posterior a la de fin.' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    expect(c.error()).toContain('vigencia');
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await crear({
      suscripcionId: 's1',
      vigenciaInicioActual: '2026-01-01',
      vigenciaFinActual: null,
      empresaNombre: 'Acme',
    });
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
