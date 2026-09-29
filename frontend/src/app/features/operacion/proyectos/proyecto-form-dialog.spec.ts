// =============================================================================
// Pruebas del dialogo ProyectoFormDialog (Req 21.1)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Alta: POST /proyectos con { clienteId, nombre } y cierra con el creado.
//   - Edicion: prellena el nombre y hace PUT /proyectos/{id} (Cliente inmutable).
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { ProyectoFormDialog } from './proyecto-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { Proyecto } from '../models/operacion.models';
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

function proyectoDe(over: Partial<Proyecto> = {}): Proyecto {
  return {
    id: 'p-1',
    clienteId: 'cli-1',
    nombre: 'Proyecto demo',
    estadoConsolidado: null,
    sitios: [],
    estadoMultisitio: 'en_preparacion',
    sitiosMultisitio: [],
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as Proyecto;
}

interface Probe {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
  esEdicion: boolean;
}

describe('ProyectoFormDialog', () => {
  let fixture: ComponentFixture<ProyectoFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(data: { proyecto?: Proyecto } = {}): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [ProyectoFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ProyectoFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('alta: POST /proyectos con clienteId y nombre y cierra con el creado', async () => {
    await montar();
    comp().form.patchValue({ clienteId: 'cli-9', nombre: 'Proyecto Norte' });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/proyectos' && r.method === 'POST');
    expect(req.request.body.clienteId).toBe('cli-9');
    expect(req.request.body.nombre).toBe('Proyecto Norte');
    req.flush(proyectoDe({ nombre: 'Proyecto Norte' }));
    expect((dialogRef.cerradoCon as Proyecto).id).toBe('p-1');
  });

  it('edicion: prellena el nombre y hace PUT /proyectos/{id}', async () => {
    await montar({ proyecto: proyectoDe({ id: 'p-9', nombre: 'Renombrado' }) });
    expect(comp().esEdicion).toBe(true);
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/proyectos/p-9' && r.method === 'PUT');
    expect(req.request.body.nombre).toBe('Renombrado');
    // La edición no envía clienteId (el Cliente es inmutable).
    expect(req.request.body.clienteId).toBeUndefined();
    req.flush(proyectoDe({ id: 'p-9', nombre: 'Renombrado' }));
    expect((dialogRef.cerradoCon as Proyecto).id).toBe('p-9');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await esperarSinViolaciones(fixture);
  });
});
