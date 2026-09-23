// =============================================================================
// Pruebas del dialogo ResultadoClaveDialog: alta de Resultado clave (Key Result)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - El POST /estrategia/objetivos/{id}/resultados-clave se envia con el cuerpo
//     correcto y el dialogo se cierra devolviendo el objetivo actualizado.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { ResultadoClaveDialog } from './resultado-clave-dialog';
import { ObjetivoEstrategico } from '../models/estrategia.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

interface ResultadoDialogTest {
  formulario: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
}

function objetivoActualizado(): ObjetivoEstrategico {
  return {
    id: 'o1',
    nombre: 'Crecer',
    responsable: 'Comercial',
    periodoInicio: '2025-01-01',
    periodoFin: '2025-12-31',
    meta: 'Meta',
    avance: 40,
    estadoDerivado: 'en_curso',
    resultadosClave: [
      { id: 'rc1', descripcion: 'Contratos', valorObjetivo: 30, valorActual: 12, peso: 1 },
    ],
    version: 1,
    createdAt: '',
    updatedAt: '',
  };
}

describe('ResultadoClaveDialog', () => {
  let fixture: ComponentFixture<ResultadoClaveDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  beforeEach(async () => {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [ResultadoClaveDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { objetivoId: 'o1', objetivoNombre: 'Crecer' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ResultadoClaveDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function componente(): ResultadoDialogTest {
    return fixture.componentInstance as unknown as ResultadoDialogTest;
  }

  it('envia el POST al endpoint del objetivo con el cuerpo correcto y cierra devolviendo el objetivo', () => {
    const c = componente();
    c.formulario.patchValue({
      descripcion: 'Contratos',
      valorObjetivo: 30,
      valorActual: 12,
      peso: 2,
    });
    c.guardar();

    const req = http.expectOne('/api/v1/estrategia/objetivos/o1/resultados-clave');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      descripcion: 'Contratos',
      valorObjetivo: 30,
      valorActual: 12,
      peso: 2,
    });
    req.flush(objetivoActualizado());

    expect(dialogRef.cerradoCon).toEqual(objetivoActualizado());
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  });
});
