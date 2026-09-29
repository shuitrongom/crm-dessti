// =============================================================================
// Pruebas del dialogo OtiAvanceDialog (Req 19.4)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Registro: POST /ordenes-trabajo-instalacion/{id}/avance con pendientes y
//     evidencias derivados del texto (split por lineas/comas) y cierra con `true`.
//   - Sin pendientes ni evidencias no hace POST.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { OtiAvanceDialog } from './oti-avance-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { OrdenTrabajoInstalacion } from '../models/operacion.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

const OTI_ID = 'oti-1';

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

function otiDe(): OrdenTrabajoInstalacion {
  return {
    id: OTI_ID,
    ordenFabricacionId: 'of-1',
    sitioId: 'sit-1',
    cuadrillaId: 'cua-1',
    clienteId: 'cli-1',
    fechaProgramada: '2026-04-01',
    estado: 'en_curso',
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  } as OrdenTrabajoInstalacion;
}

interface Probe {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
}

describe('OtiAvanceDialog', () => {
  let fixture: ComponentFixture<OtiAvanceDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  const URL_AVANCE = `/api/v1/ordenes-trabajo-instalacion/${OTI_ID}/avance`;

  async function montar(): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [OtiAvanceDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: MAT_DIALOG_DATA, useValue: { oti: otiDe(), nombreCliente: 'Rotulos Acme' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OtiAvanceDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('registra: POST con pendientes/evidencias derivados del texto y cierra con true', async () => {
    await montar();
    comp().form.patchValue({
      nuevosPendientes: 'Fijar poste\nConectar energía',
      evidencias: 'https://a/1.jpg, https://a/2.jpg',
    });
    comp().guardar();
    const req = http.expectOne((r) => r.url === URL_AVANCE && r.method === 'POST');
    expect(req.request.body.nuevosPendientes).toEqual(['Fijar poste', 'Conectar energía']);
    expect(req.request.body.evidencias).toEqual(['https://a/1.jpg', 'https://a/2.jpg']);
    req.flush(otiDe());
    expect(dialogRef.cerradoCon).toBe(true);
  });

  it('sin pendientes ni evidencias no hace POST', async () => {
    await montar();
    comp().guardar();
    http.expectNone((r) => r.url === URL_AVANCE);
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
