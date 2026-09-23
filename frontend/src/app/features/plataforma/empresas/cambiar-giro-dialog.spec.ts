// =============================================================================
// Pruebas del dialogo CambiarGiroDialog (super_admin) (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Muestra el giro ACTUAL por su nombre (nunca el UUID) y los giros activos
//     como opciones del selector.
//   - Al confirmar con un giro distinto hace PUT /empresas/{id}/giro con
//     { giroId } y cierra devolviendo la Empresa actualizada.
//   - Un error 422 (giro invalido / datos del vertical) muestra el mensaje del
//     backend SIN cerrar el dialogo.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { CambiarGiroDialog, CambiarGiroDialogData } from './cambiar-giro-dialog';
import { Empresa, Giro } from '../models/plataforma.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** MatDialogRef de prueba que registra si se cerro y con que resultado. */
class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

function giro(parcial: Partial<Giro> = {}): Giro {
  return {
    id: 'g1',
    clave: 'rotulacion',
    nombreVisible: 'Rotulacion',
    descripcion: null,
    activo: true,
    version: 0,
    tieneReglasNegocio: false,
    modulosEspecificos: 0,
    ...parcial,
  };
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

interface CambiarGiroTest {
  formulario: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
  cambiarGiro(id: string): void;
  sinCambio(): boolean;
  error(): string | null;
  giroActual(): string;
}

describe('CambiarGiroDialog', () => {
  let fixture: ComponentFixture<CambiarGiroDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  const URL = '/api/v1/empresas/e1/giro';

  async function crear(data: CambiarGiroDialogData): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [CambiarGiroDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CambiarGiroDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function componenteDe(): CambiarGiroTest {
    return fixture.componentInstance as unknown as CambiarGiroTest;
  }

  it('muestra el giro actual por su nombre (nunca el UUID) y los giros activos', async () => {
    await crear({
      empresa: empresa({ giroId: 'g1' }),
      girosActivos: [giro(), giro({ id: 'g2', nombreVisible: 'Impresion' })],
    });
    expect(componenteDe().giroActual()).toBe('Rotulacion');
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Rotulacion');
    expect(texto).not.toContain('g1');
  });

  it('al confirmar un giro distinto hace PUT con { giroId } y cierra con la empresa', async () => {
    await crear({
      empresa: empresa({ giroId: 'g1' }),
      girosActivos: [giro(), giro({ id: 'g2', nombreVisible: 'Impresion' })],
    });
    const c = componenteDe();
    c.cambiarGiro('g2');
    c.guardar();
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ giroId: 'g2' });
    const actualizada = empresa({ giroId: 'g2' });
    req.flush(actualizada);
    expect(dialogRef.cerradoCon).toEqual(actualizada);
  });

  it('al elegir un giro distinto habilita el boton (sinCambio pasa a false)', async () => {
    await crear({
      empresa: empresa({ giroId: 'sin-giro' }),
      girosActivos: [giro(), giro({ id: 'g2', nombreVisible: 'Impresion' })],
    });
    const c = componenteDe();
    // Empresa "(sin giro)": inicialmente no hay cambio, boton deshabilitado.
    expect(c.giroActual()).toBe('(sin giro)');
    expect(c.sinCambio()).toBe(true);
    c.cambiarGiro('g2');
    expect(c.sinCambio()).toBe(false);
  });

  it('un error 422 muestra el mensaje del backend sin cerrar el dialogo', async () => {
    await crear({
      empresa: empresa({ giroId: 'g1' }),
      girosActivos: [giro(), giro({ id: 'g2', nombreVisible: 'Impresion' })],
    });
    const c = componenteDe();
    c.formulario.patchValue({ giroId: 'g2' });
    c.guardar();
    http.expectOne(URL).flush(
      { detail: 'La empresa ya tiene datos del vertical actual.' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    expect(c.error()).toContain('vertical');
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await crear({
      empresa: empresa({ giroId: 'g1' }),
      girosActivos: [giro(), giro({ id: 'g2', nombreVisible: 'Impresion' })],
    });
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
