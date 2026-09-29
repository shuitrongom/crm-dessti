// =============================================================================
// Pruebas del diálogo AvanceSitioDialog (Req 3.2, 3-bis)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Modo AVANCE: nota opcional; guardar hace PUT .../sitios/{id}/avance.
//   - Modo AVANCE a instalación: muestra el aviso de precondiciones.
//   - Modo CORRECCIÓN: el motivo es OBLIGATORIO (no envía si está vacío);
//     con motivo hace PUT .../sitios/{id}/correccion-fase.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { AvanceSitioDialog, AvanceSitioDialogData } from './avance-sitio-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { Proyecto, SitioFase } from '../models/operacion.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

const PROYECTO_ID = 'pppppppp-1111-2222-3333-444444444444';
const SITIO_ID = 'ssssssss-1111-2222-3333-444444444444';

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

function sitioFase(fase: SitioFase['fase']): SitioFase {
  return {
    sitio: {
      id: SITIO_ID,
      proyectoId: PROYECTO_ID,
      nombre: 'Sucursal Centro',
      direccion: null,
      version: 0,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
    fase,
    nota: null,
    evidenciaUrl: null,
  } as unknown as SitioFase;
}

function proyectoDto(): Proyecto {
  return { id: PROYECTO_ID } as unknown as Proyecto;
}

interface DialogTest {
  form: { patchValue(v: Record<string, unknown>): void };
  guardar(): void;
}

describe('AvanceSitioDialog', () => {
  let fixture: ComponentFixture<AvanceSitioDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(data: AvanceSitioDialogData): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [AvanceSitioDialog, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AvanceSitioDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): DialogTest {
    return fixture.componentInstance as unknown as DialogTest;
  }

  it('modo avance: guarda con PUT .../avance y cierra con el proyecto actualizado', async () => {
    await montar({
      proyectoId: PROYECTO_ID,
      sitio: sitioFase('pendiente'),
      destino: 'en_preparacion',
      icono: 'engineering',
      modo: 'avance',
    });
    comp().guardar();
    const put = http.expectOne(
      (r) =>
        r.method === 'PUT' &&
        r.url === `/api/v1/proyectos/${PROYECTO_ID}/sitios/${SITIO_ID}/avance`,
    );
    expect(put.request.body).toEqual({ fase: 'en_preparacion', nota: null });
    put.flush(proyectoDto());
    expect((dialogRef.cerradoCon as Proyecto).id).toBe(PROYECTO_ID);
  });

  it('modo avance a instalación: muestra el aviso de precondiciones (levantamiento + permiso vigente)', async () => {
    await montar({
      proyectoId: PROYECTO_ID,
      sitio: sitioFase('en_preparacion'),
      destino: 'en_instalacion',
      icono: 'local_shipping',
      modo: 'avance',
      requierePrecondicionesInstalacion: true,
    });
    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('permiso de instalación aprobado y vigente');
    // Igual permite intentar (el backend es la fuente de verdad).
    comp().guardar();
    const put = http.expectOne(
      (r) =>
        r.method === 'PUT' &&
        r.url === `/api/v1/proyectos/${PROYECTO_ID}/sitios/${SITIO_ID}/avance`,
    );
    put.flush(proyectoDto());
  });

  it('modo corrección sin motivo: NO envía la petición (motivo obligatorio)', async () => {
    await montar({
      proyectoId: PROYECTO_ID,
      sitio: sitioFase('en_instalacion'),
      destino: 'pendiente',
      icono: 'undo',
      modo: 'correccion',
    });
    // Sin motivo: guardar no debe disparar ninguna petición.
    comp().guardar();
    http.expectNone(
      (r) => r.url === `/api/v1/proyectos/${PROYECTO_ID}/sitios/${SITIO_ID}/correccion-fase`,
    );
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('modo corrección con motivo: hace PUT .../correccion-fase y cierra', async () => {
    await montar({
      proyectoId: PROYECTO_ID,
      sitio: sitioFase('en_instalacion'),
      destino: 'pendiente',
      icono: 'undo',
      modo: 'correccion',
    });
    comp().form.patchValue({ nota: 'Marcado por error, se revierte' });
    comp().guardar();
    const put = http.expectOne(
      (r) =>
        r.method === 'PUT' &&
        r.url === `/api/v1/proyectos/${PROYECTO_ID}/sitios/${SITIO_ID}/correccion-fase`,
    );
    expect(put.request.body).toEqual({ fase: 'pendiente', nota: 'Marcado por error, se revierte' });
    put.flush(proyectoDto());
    expect((dialogRef.cerradoCon as Proyecto).id).toBe(PROYECTO_ID);
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar({
      proyectoId: PROYECTO_ID,
      sitio: sitioFase('en_instalacion'),
      destino: 'pendiente',
      icono: 'undo',
      modo: 'correccion',
    });
    await esperarSinViolaciones(fixture);
  }, 30000);
});
