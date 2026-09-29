// =============================================================================
// Pruebas del dialogo OtiFormDialog (Req 19)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Programacion: POST /ordenes-trabajo-instalacion con el cuerpo
//     (ordenFabricacionId/sitioId/cuadrillaId/fechaProgramada) y cierra con la OTI.
//   - No envia peticion si el formulario es invalido.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';
import { LOCALE_ID } from '@angular/core';
import { Observable } from 'rxjs';

import { OtiFormDialog } from './oti-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { OrdenTrabajoInstalacion } from '../models/operacion.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

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

function otiDe(over: Partial<OrdenTrabajoInstalacion> = {}): OrdenTrabajoInstalacion {
  return {
    id: 'oti-1',
    ordenFabricacionId: 'of-1',
    sitioId: 'sit-1',
    cuadrillaId: 'cua-1',
    clienteId: 'cli-1',
    fechaProgramada: '2026-04-01',
    estado: 'programada',
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as OrdenTrabajoInstalacion;
}

interface Probe {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
}

describe('OtiFormDialog', () => {
  let fixture: ComponentFixture<OtiFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [OtiFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OtiFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('programa: POST /ordenes-trabajo-instalacion con el cuerpo y cierra con la OTI', async () => {
    await montar();
    comp().form.patchValue({
      ordenFabricacionId: 'of-9',
      sitioId: 'sit-9',
      cuadrillaId: 'cua-9',
      fechaProgramada: '2026-05-15',
    });
    comp().guardar();
    const req = http.expectOne(
      (r) => r.url === '/api/v1/ordenes-trabajo-instalacion' && r.method === 'POST',
    );
    expect(req.request.body.ordenFabricacionId).toBe('of-9');
    expect(req.request.body.sitioId).toBe('sit-9');
    expect(req.request.body.cuadrillaId).toBe('cua-9');
    expect(req.request.body.fechaProgramada).toBe('2026-05-15');
    req.flush(otiDe());
    expect((dialogRef.cerradoCon as OrdenTrabajoInstalacion).id).toBe('oti-1');
  });

  it('no envia peticion si el formulario es invalido', async () => {
    await montar();
    comp().guardar();
    http.expectNone((r) => r.url === '/api/v1/ordenes-trabajo-instalacion');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
