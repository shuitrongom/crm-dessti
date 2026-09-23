// =============================================================================
// Pruebas del dialogo ObjetivoDialog: alta de Objetivo estrategico (OKR)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real:
//   - El POST /estrategia/objetivos se envia con el cuerpo correcto.
//   - Al crear, el dialogo se cierra devolviendo el objetivo creado.
//   - El validador de periodo rechaza fin < inicio (no se envia).
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { ObjetivoDialog } from './objetivo-dialog';
import { ObjetivoEstrategico } from '../models/estrategia.models';
import { esperarSinViolaciones } from '../../../../testing/axe';
import { provideFechaIsoDatepicker } from '../../../shared/date/provide-fecha-iso';

class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

interface ObjetivoDialogTest {
  formulario: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
}

function objetivoCreado(): ObjetivoEstrategico {
  return {
    id: 'o1',
    nombre: 'Crecer en el mercado',
    responsable: 'Dirección Comercial',
    periodoInicio: '2025-01-01',
    periodoFin: '2025-12-31',
    meta: '25% de participación',
    avance: 0,
    estadoDerivado: 'en_curso',
    resultadosClave: [],
    version: 0,
    createdAt: '',
    updatedAt: '',
  };
}

describe('ObjetivoDialog', () => {
  let fixture: ComponentFixture<ObjetivoDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  beforeEach(async () => {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [ObjetivoDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        ...provideFechaIsoDatepicker(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ObjetivoDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function componente(): ObjetivoDialogTest {
    return fixture.componentInstance as unknown as ObjetivoDialogTest;
  }

  it('envia el POST con el cuerpo correcto y cierra devolviendo el objetivo creado', () => {
    const c = componente();
    c.formulario.patchValue({
      nombre: 'Crecer en el mercado',
      responsable: 'Dirección Comercial',
      meta: '25% de participación',
      periodoInicio: '2025-01-01',
      periodoFin: '2025-12-31',
    });
    c.guardar();

    const req = http.expectOne('/api/v1/estrategia/objetivos');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      nombre: 'Crecer en el mercado',
      responsable: 'Dirección Comercial',
      meta: '25% de participación',
      periodoInicio: '2025-01-01',
      periodoFin: '2025-12-31',
    });
    req.flush(objetivoCreado());

    expect(dialogRef.cerradoCon).toEqual(objetivoCreado());
  });

  it('no envia la solicitud cuando el periodo fin es anterior al inicio', () => {
    const c = componente();
    c.formulario.patchValue({
      nombre: 'X',
      responsable: 'Y',
      meta: 'Z',
      periodoInicio: '2025-12-31',
      periodoFin: '2025-01-01',
    });
    c.guardar();

    http.expectNone('/api/v1/estrategia/objetivos');
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
