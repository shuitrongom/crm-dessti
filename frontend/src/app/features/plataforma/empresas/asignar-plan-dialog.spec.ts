// =============================================================================
// Pruebas del dialogo AsignarPlanDialog (super_admin)
// (plan-suscripcion-empresa-super-admin) (Req 6)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Muestra los Planes por su NOMBRE (nunca el UUID) en el estado del
//     componente y en el DOM.
//   - Al confirmar con un plan seleccionado hace POST /suscripciones con
//     { tenantId, planId, vigenciaInicio, vigenciaFin } y cierra devolviendo la
//     Suscripcion creada.
//   - Un error 422 (vigencia invalida) muestra el mensaje del backend SIN
//     cerrar el dialogo.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { AsignarPlanDialog, AsignarPlanDialogData } from './asignar-plan-dialog';
import { Empresa, Plan, Suscripcion } from '../models/plataforma.models';
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
    nombreComercial: null,
    emailContacto: null,
    telefono: null,
    sitioWeb: null,
    direccion: null,
    notas: null,
    fechaCancelacion: null,
    finPeriodoGracia: null,
    planVigente: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...parcial,
  };
}

function plan(parcial: Partial<Plan> = {}): Plan {
  return {
    id: 'p1',
    nombre: 'Plan Basico',
    maxUsuarios: 5,
    duracionDias: 730,
    giroId: 'g1',
    monedaCodigo: 'MXN',
    preciosModulos: {},
    total: 0,
    modulosHabilitados: [],
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...parcial,
  };
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

interface AsignarPlanTest {
  formulario: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
  error(): string | null;
  planes: Plan[];
}

describe('AsignarPlanDialog', () => {
  let fixture: ComponentFixture<AsignarPlanDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  const URL = '/api/v1/suscripciones';

  async function crear(data: AsignarPlanDialogData): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [AsignarPlanDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AsignarPlanDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function componenteDe(): AsignarPlanTest {
    return fixture.componentInstance as unknown as AsignarPlanTest;
  }

  it('muestra los planes por su nombre (nunca el UUID)', async () => {
    await crear({
      empresa: empresa(),
      planes: [plan(), plan({ id: 'p2', nombre: 'Plan Pro' })],
      planIdActual: 'p1',
    });
    // El componente expone los planes por su nombre (no por UUID).
    const nombres = componenteDe().planes.map((p) => p.nombre);
    expect(nombres).toEqual(['Plan Basico', 'Plan Pro']);
    // Al abrir el selector, las opciones se renderizan por nombre en el overlay.
    const trigger = (fixture.nativeElement as HTMLElement).querySelector(
      'mat-select',
    ) as HTMLElement;
    trigger.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const overlay = document.querySelector('.cdk-overlay-container') as HTMLElement;
    const texto = overlay?.textContent ?? '';
    expect(texto).toContain('Plan Basico');
    expect(texto).toContain('Plan Pro');
    // El identificador (UUID) NUNCA se muestra.
    expect(texto).not.toContain('p1');
    expect(texto).not.toContain('p2');
  });

  it('al confirmar con un plan seleccionado hace POST con el body y cierra con la suscripcion', async () => {
    await crear({
      empresa: empresa({ id: 'e1' }),
      planes: [plan(), plan({ id: 'p2', nombre: 'Plan Pro' })],
      planIdActual: null,
    });
    const c = componenteDe();
    c.formulario.patchValue({
      planId: 'p2',
      vigenciaInicio: '2026-02-01',
      vigenciaFin: '2026-12-31',
    });
    c.guardar();
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      tenantId: 'e1',
      planId: 'p2',
      vigenciaInicio: '2026-02-01',
      vigenciaFin: '2026-12-31',
    });
    const creada = suscripcion({ planId: 'p2' });
    req.flush(creada);
    expect(dialogRef.cerradoCon).toEqual(creada);
  });

  it('un error 422 muestra el mensaje del backend sin cerrar el dialogo', async () => {
    await crear({
      empresa: empresa(),
      planes: [plan(), plan({ id: 'p2', nombre: 'Plan Pro' })],
      planIdActual: 'p1',
    });
    const c = componenteDe();
    c.formulario.patchValue({ planId: 'p2' });
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
      empresa: empresa(),
      planes: [plan(), plan({ id: 'p2', nombre: 'Plan Pro' })],
      planIdActual: 'p1',
    });
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
