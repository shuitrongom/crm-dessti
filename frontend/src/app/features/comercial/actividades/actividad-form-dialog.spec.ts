// =============================================================================
// Pruebas del dialogo ActividadFormDialog (Actividades V79)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Alta: POST /actividades con el cuerpo (clienteId/oportunidadId/tipo/asunto/
//     descripcion/fechaProgramada en ISO) y cierra con la actividad creada.
//   - Edicion: prellena y hace PUT /actividades/{id} SOLO con asunto/descripcion
//     (el backend no permite cambiar tipo ni fechas al editar).
//   - Validacion: sin asunto el form es invalido y NO dispara peticion.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { ActividadFormDialog, ActividadFormDialogData } from './actividad-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { Actividad } from '../models/comercial.models';
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

function actividadDe(over: Partial<Actividad> = {}): Actividad {
  return {
    id: 'act-1',
    clienteId: 'cli-1',
    oportunidadId: null,
    tipo: 'llamada',
    estado: 'pendiente',
    asunto: 'Llamar al cliente',
    descripcion: null,
    fechaProgramada: '2026-02-01T15:00:00Z',
    vencimiento: null,
    completadaEn: null,
    responsableUsuarioId: null,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as Actividad;
}

interface Probe {
  form: {
    patchValue(v: Record<string, unknown>): void;
    controls: { asunto: { setValue(v: string): void } };
    invalid: boolean;
  };
  guardar(): void;
  esEdicion: boolean;
}

describe('ActividadFormDialog', () => {
  let fixture: ComponentFixture<ActividadFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(data: ActividadFormDialogData = {}): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [ActividadFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ActividadFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('alta: POST /actividades con el cuerpo y cierra con la creada', async () => {
    await montar({ clienteId: 'cli-9', oportunidadId: 'opo-3' });
    comp().form.patchValue({
      tipo: 'reunion',
      asunto: 'Reunión de arranque',
      descripcion: 'Alcance del proyecto',
    });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/actividades' && r.method === 'POST');
    expect(req.request.body.clienteId).toBe('cli-9');
    expect(req.request.body.oportunidadId).toBe('opo-3');
    expect(req.request.body.tipo).toBe('reunion');
    expect(req.request.body.asunto).toBe('Reunión de arranque');
    // La fecha programada se envía en ISO-8601.
    expect(typeof req.request.body.fechaProgramada).toBe('string');
    expect(req.request.body.fechaProgramada).toContain('T');
    req.flush(actividadDe({ id: 'act-nueva' }));
    expect((dialogRef.cerradoCon as Actividad).id).toBe('act-nueva');
  });

  it('edicion: PUT /actividades/{id} solo con asunto y descripcion', async () => {
    await montar({ actividad: actividadDe({ id: 'act-9', asunto: 'Original' }) });
    expect(comp().esEdicion).toBe(true);
    comp().form.patchValue({ asunto: 'Editado', descripcion: 'Nueva nota' });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/actividades/act-9' && r.method === 'PUT');
    expect(req.request.body.asunto).toBe('Editado');
    expect(req.request.body.descripcion).toBe('Nueva nota');
    // El PUT de edición no envía tipo, fechaProgramada ni clienteId.
    expect(req.request.body.tipo).toBeUndefined();
    expect(req.request.body.fechaProgramada).toBeUndefined();
    expect(req.request.body.clienteId).toBeUndefined();
    req.flush(actividadDe({ id: 'act-9', asunto: 'Editado' }));
    expect((dialogRef.cerradoCon as Actividad).id).toBe('act-9');
  });

  it('sin asunto el form es inválido y no dispara petición', async () => {
    await montar({ clienteId: 'cli-1' });
    comp().form.controls.asunto.setValue('');
    comp().guardar();
    expect(comp().form.invalid).toBe(true);
    http.expectNone((r) => r.method === 'POST');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar({ clienteId: 'cli-1' });
    await esperarSinViolaciones(fixture);
  }, 30000);
});
