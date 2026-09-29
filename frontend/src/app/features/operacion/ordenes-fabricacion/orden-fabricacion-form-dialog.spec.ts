// =============================================================================
// Pruebas del dialogo OrdenFabricacionFormDialog (Req 7.1, 1.1)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Modo "desde cotización": POST /ordenes-fabricacion con { cotizacionId } y
//     cierra con la orden generada.
//   - Modo "directa": POST /ordenes-fabricacion/directa con { clienteId, partidas }
//     y cierra con la orden creada.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { signal } from '@angular/core';
import { Observable, of } from 'rxjs';

import { OrdenFabricacionFormDialog } from './orden-fabricacion-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { NombresOperacionService } from '../services/nombres-operacion.service';
import { OrdenFabricacion } from '../models/operacion.models';
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

/** Stub de NombresOperacionService con catalogos en memoria ya cargados. */
class NombresStub {
  cargado = signal(true);
  clientes = signal([{ id: 'cli-1', nombre: 'Acme', rfc: 'ABC010101AB1' }]);
  cotizaciones = signal([
    { id: 'cot-1', folio: 'COT-2026-0001', estado: 'aprobada', clienteNombre: 'Acme' },
  ]);
  materiales = signal([{ id: 'mat-1', nombre: 'Perfil', unidadMedida: 'metro' }]);
  cargar(): Observable<unknown> {
    return of({});
  }
}

function ordenDe(): OrdenFabricacion {
  return {
    id: 'of-1',
    cotizacionId: 'cot-1',
    clienteId: 'cli-1',
    estado: 'pendiente',
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  } as OrdenFabricacion;
}

interface Probe {
  formCotizacion: { patchValue(v: Record<string, unknown>): void };
  formDirecta: { patchValue(v: Record<string, unknown>): void };
  partidas: { at(i: number): { patchValue(v: Record<string, unknown>): void } };
  cambiarModo(m: 'cotizacion' | 'directa'): void;
  guardar(): void;
}

describe('OrdenFabricacionFormDialog', () => {
  let fixture: ComponentFixture<OrdenFabricacionFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [OrdenFabricacionFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: NombresOperacionService, useClass: NombresStub },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OrdenFabricacionFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('desde cotización: POST /ordenes-fabricacion y cierra con la orden', async () => {
    await montar();
    comp().formCotizacion.patchValue({ cotizacionId: 'cot-1' });
    comp().guardar();
    const req = http.expectOne(
      (r) => r.url === '/api/v1/ordenes-fabricacion' && r.method === 'POST',
    );
    expect(req.request.body.cotizacionId).toBe('cot-1');
    req.flush(ordenDe());
    expect((dialogRef.cerradoCon as OrdenFabricacion).id).toBe('of-1');
  });

  it('directa: POST /ordenes-fabricacion/directa con clienteId y partidas', async () => {
    await montar();
    comp().cambiarModo('directa');
    fixture.detectChanges();
    comp().formDirecta.patchValue({ clienteId: 'cli-1' });
    comp().partidas.at(0).patchValue({ materialId: 'mat-1', cantidad: 5 });
    comp().guardar();
    const req = http.expectOne(
      (r) => r.url === '/api/v1/ordenes-fabricacion/directa' && r.method === 'POST',
    );
    expect(req.request.body.clienteId).toBe('cli-1');
    expect(req.request.body.partidas).toEqual([{ materialId: 'mat-1', cantidad: 5 }]);
    req.flush(ordenDe());
    expect((dialogRef.cerradoCon as OrdenFabricacion).id).toBe('of-1');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
