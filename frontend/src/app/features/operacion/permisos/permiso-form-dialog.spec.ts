// =============================================================================
// Pruebas del dialogo PermisoFormDialog (Req 17)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Alta: POST /permisos-instalacion con el cuerpo (tipo/fechaVencimiento/
//     sitioId) y cierra con el permiso creado.
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

import { PermisoFormDialog } from './permiso-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { PermisoInstalacion } from '../models/operacion.models';
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

function permisoDe(over: Partial<PermisoInstalacion> = {}): PermisoInstalacion {
  return {
    id: 'perm-1',
    sitioId: 'sit-1',
    tipo: 'municipal',
    fechaVencimiento: '2026-12-31',
    estado: 'solicitado',
    decididoPor: null,
    decididoEn: null,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as PermisoInstalacion;
}

interface Probe {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
}

describe('PermisoFormDialog', () => {
  let fixture: ComponentFixture<PermisoFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [PermisoFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PermisoFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('alta: POST /permisos-instalacion con el cuerpo y cierra con el creado', async () => {
    await montar();
    comp().form.patchValue({
      tipo: 'arrendador',
      fechaVencimiento: '2026-12-31',
      sitioId: 'sit-9',
    });
    comp().guardar();
    const req = http.expectOne(
      (r) => r.url === '/api/v1/permisos-instalacion' && r.method === 'POST',
    );
    expect(req.request.body.tipo).toBe('arrendador');
    expect(req.request.body.fechaVencimiento).toBe('2026-12-31');
    expect(req.request.body.sitioId).toBe('sit-9');
    req.flush(permisoDe({ tipo: 'arrendador' }));
    expect((dialogRef.cerradoCon as PermisoInstalacion).id).toBe('perm-1');
  });

  it('no envia peticion si el formulario es invalido', async () => {
    await montar();
    comp().guardar();
    http.expectNone((r) => r.url === '/api/v1/permisos-instalacion');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
