// =============================================================================
// Pruebas del dialogo de alta/edicion de Producto (Req 59) — ProductoFormDialog
// -----------------------------------------------------------------------------
// Verifican, sin red real, la carga de foto y el envio del cuerpo, y el modo
// edicion:
//   - Foto: una imagen valida se guarda como data-URI; un archivo no-imagen o
//     >1 MB se rechaza sin alterar la foto vigente; quitar foto la limpia.
//   - Alta: POST /productos con el cuerpo (incluida la foto) y cierra con el creado.
//   - Edicion: prellena y hace PUT /productos/{id}.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { ProductoFormDialog } from './producto-form-dialog';
import { OperacionOverlayService } from '../../../shared/components/operacion-overlay/operacion-overlay';
import { AuthService } from '../../../core/auth/auth.service';
import { Producto } from '../models/comercial.models';
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

class AuthServiceStub {
  tienePermiso(): boolean {
    return true;
  }
}

/** Construye un File de tamano y tipo dados (contenido irrelevante). */
function archivo(tipo: string, bytes = 8, nombre = 'foto.png'): File {
  return new File([new Uint8Array(bytes)], nombre, { type: tipo });
}

/** Evento con el File en target.files (como un <input type=file>). */
function eventoArchivo(f: File): Event {
  return { target: { files: [f], value: '' } } as unknown as Event;
}

function productoDe(over: Partial<Producto> = {}): Producto {
  return {
    id: 'p-1',
    nombre: 'Producto Demo',
    unidad: 'pieza',
    descripcion: 'desc',
    clienteMeta: null,
    alianzas: null,
    competencia: null,
    foto: null,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  } as Producto;
}

interface Probe {
  form: { patchValue(v: Record<string, unknown>): void };
  foto(): string | null;
  fotoError(): string | null;
  seleccionarFoto(e: Event): void;
  quitarFoto(): void;
  guardar(): void;
  esEdicion: boolean;
}

describe('ProductoFormDialog', () => {
  let fixture: ComponentFixture<ProductoFormDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  async function montar(data: { producto?: Producto } = {}): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [ProductoFormDialog, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: OperacionOverlayService, useClass: OverlayStub },
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ProductoFormDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => http?.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  it('una imagen valida se guarda como data-URI', async () => {
    await montar();
    comp().seleccionarFoto(eventoArchivo(archivo('image/png')));
    await vi.waitFor(() => expect(comp().foto()).toBeTruthy());
    expect(comp().foto()!.startsWith('data:')).toBe(true);
  });

  it('un archivo no-imagen se rechaza sin alterar la foto vigente', async () => {
    await montar({ producto: productoDe({ foto: 'data:image/png;base64,PREVIO' }) });
    comp().seleccionarFoto(eventoArchivo(archivo('application/pdf', 8, 'doc.pdf')));
    expect(comp().fotoError()).toContain('imagen');
    expect(comp().foto()).toBe('data:image/png;base64,PREVIO');
  });

  it('una imagen > 1 MB se rechaza', async () => {
    await montar();
    comp().seleccionarFoto(eventoArchivo(archivo('image/png', 1024 * 1024 + 1)));
    expect(comp().fotoError()).toContain('1 MB');
    expect(comp().foto()).toBeNull();
  });

  it('quitar foto la limpia', async () => {
    await montar({ producto: productoDe({ foto: 'data:image/png;base64,PREVIO' }) });
    expect(comp().foto()).toBe('data:image/png;base64,PREVIO');
    comp().quitarFoto();
    expect(comp().foto()).toBeNull();
  });

  it('alta: POST /productos con el cuerpo y cierra con el creado', async () => {
    await montar();
    comp().form.patchValue({ nombre: 'Rotulo', unidad: 'pieza', descripcion: 'Rotulo exterior' });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/productos' && r.method === 'POST');
    expect(req.request.body.nombre).toBe('Rotulo');
    expect(req.request.body.unidad).toBe('pieza');
    req.flush(productoDe({ nombre: 'Rotulo' }));
    expect((dialogRef.cerradoCon as Producto).id).toBe('p-1');
  });

  it('edicion: prellena y hace PUT /productos/{id}', async () => {
    await montar({ producto: productoDe({ id: 'p-9', nombre: 'Editado' }) });
    expect(comp().esEdicion).toBe(true);
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/productos/p-9' && r.method === 'PUT');
    expect(req.request.body.nombre).toBe('Editado');
    req.flush(productoDe({ id: 'p-9', nombre: 'Editado' }));
    expect((dialogRef.cerradoCon as Producto).id).toBe('p-9');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await montar();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
