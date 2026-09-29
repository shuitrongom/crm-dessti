// =============================================================================
// Pruebas del dialogo ListaFormDialog (Req 59.3)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Alta: POST /listas-precios con el cuerpo (nombre/prioridad/segmento/
//     vigencias en ISO) y cierra con la lista creada.
//   - Edicion: prellena y hace PUT /listas-precios/{id}.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNativeDateAdapter } from '@angular/material/core';
import { Observable } from 'rxjs';

import { ListaFormDialog } from './lista-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { ListaPrecios } from '../models/comercial.models';
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

function listaDe(over: Partial<ListaPrecios> = {}): ListaPrecios {
  return {
    id: 'lp-1',
    nombre: 'Lista general demo',
    prioridad: 1,
    segmento: null,
    vigenciaInicio: '2026-01-01',
    vigenciaFin: null,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as ListaPrecios;
}

interface Probe {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
  esEdicion: boolean;
}

describe('ListaFormDialog', () => {
  let fixture: ComponentFixture<ListaFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(data: { lista?: ListaPrecios } = {}): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [ListaFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        provideNativeDateAdapter(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ListaFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('alta: POST /listas-precios con el cuerpo y cierra con la creada', async () => {
    await montar();
    comp().form.patchValue({
      nombre: 'Lista mayoreo',
      prioridad: 5,
      segmento: 'mayoreo',
      vigenciaInicio: '2026-02-01',
    });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/listas-precios' && r.method === 'POST');
    expect(req.request.body.nombre).toBe('Lista mayoreo');
    expect(req.request.body.prioridad).toBe(5);
    expect(req.request.body.segmento).toBe('mayoreo');
    expect(req.request.body.vigenciaInicio).toBe('2026-02-01');
    req.flush(listaDe({ nombre: 'Lista mayoreo' }));
    expect((dialogRef.cerradoCon as ListaPrecios).id).toBe('lp-1');
  });

  it('edicion: prellena y hace PUT /listas-precios/{id}', async () => {
    await montar({ lista: listaDe({ id: 'lp-9', nombre: 'Editada' }) });
    expect(comp().esEdicion).toBe(true);
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/listas-precios/lp-9' && r.method === 'PUT');
    expect(req.request.body.nombre).toBe('Editada');
    req.flush(listaDe({ id: 'lp-9', nombre: 'Editada' }));
    expect((dialogRef.cerradoCon as ListaPrecios).id).toBe('lp-9');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
