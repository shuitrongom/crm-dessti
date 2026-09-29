// =============================================================================
// Pruebas del dialogo SitioFormDialog (Req 21.2)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Alta: POST /proyectos/{id}/sitios con { nombre, direccion } y cierra con `true`.
//   - Edicion: prellena y hace PUT /proyectos/{id}/sitios/{sitioId}; cierra con el
//     Proyecto detallado devuelto.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { SitioFormDialog, SitioFormDialogData } from './sitio-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { Proyecto, SitioFase } from '../models/operacion.models';
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

const PROYECTO_ID = 'p-1';

function sitioFaseDe(): SitioFase {
  return {
    sitio: {
      id: 's-1',
      proyectoId: PROYECTO_ID,
      nombre: 'Sucursal Centro',
      direccion: 'Av. Juárez 100',
      version: 0,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
    fase: 'pendiente',
    nota: null,
    evidenciaUrl: null,
  };
}

function proyectoDe(): Proyecto {
  return {
    id: PROYECTO_ID,
    clienteId: 'cli-1',
    nombre: 'Proyecto demo',
    estadoConsolidado: null,
    sitios: [],
    estadoMultisitio: 'en_preparacion',
    sitiosMultisitio: [],
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  } as Proyecto;
}

interface Probe {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
  esEdicion: boolean;
}

describe('SitioFormDialog', () => {
  let fixture: ComponentFixture<SitioFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(data: SitioFormDialogData): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [SitioFormDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SitioFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('alta: POST /proyectos/{id}/sitios y cierra con true', async () => {
    await montar({ proyectoId: PROYECTO_ID });
    comp().form.patchValue({ nombre: 'Sucursal Sur', direccion: 'Calle 5' });
    comp().guardar();
    const req = http.expectOne(
      (r) => r.url === `/api/v1/proyectos/${PROYECTO_ID}/sitios` && r.method === 'POST',
    );
    expect(req.request.body.nombre).toBe('Sucursal Sur');
    expect(req.request.body.direccion).toBe('Calle 5');
    req.flush({ id: 's-nuevo' });
    expect(dialogRef.cerradoCon).toBe(true);
  });

  it('edicion: PUT /proyectos/{id}/sitios/{sitioId} y cierra con el Proyecto', async () => {
    await montar({ proyectoId: PROYECTO_ID, sitio: sitioFaseDe() });
    expect(comp().esEdicion).toBe(true);
    comp().form.patchValue({ nombre: 'Sucursal Centro editada' });
    comp().guardar();
    const req = http.expectOne(
      (r) => r.url === `/api/v1/proyectos/${PROYECTO_ID}/sitios/s-1` && r.method === 'PUT',
    );
    expect(req.request.body.nombre).toBe('Sucursal Centro editada');
    req.flush(proyectoDe());
    expect((dialogRef.cerradoCon as Proyecto).id).toBe(PROYECTO_ID);
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar({ proyectoId: PROYECTO_ID });
    await esperarSinViolaciones(fixture);
  });
});
