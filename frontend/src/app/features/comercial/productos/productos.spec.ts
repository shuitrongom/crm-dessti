// =============================================================================
// Pruebas de la vista de Productos (Req 59, V61)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real, la carga de FOTO del producto
// como ARCHIVO de imagen y su envio en la peticion:
//   - Seleccionar una imagen valida fija un data-URI en el control `foto`.
//   - Un archivo que no es imagen o que supera 1 MB se rechaza con mensaje y NO
//     modifica la foto actual.
//   - "Quitar foto" limpia la foto.
//   - Guardar (alta) envia POST /productos con `foto` en el cuerpo.
//   - Editar prefija la foto y guardar (edicion) envia PUT /productos/{id} con `foto`.
//   - El listado renderiza una miniatura cuando hay foto y un placeholder cuando no.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom) en formulario y lista.
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { ComercialProductos } from './productos';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { Producto } from '../models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** AuthService de prueba: concede todos los permisos de producto. */
class AuthServiceStub {
  tienePermiso(recurso: string): boolean {
    return recurso === 'producto';
  }
}

/** Espia del servicio de notificaciones. */
class ToastSpy {
  exitos: string[] = [];
  errores: string[] = [];
  exito(m: string): void {
    this.exitos.push(m);
  }
  error(m: string): void {
    this.errores.push(m);
  }
  info(): void {}
}

/** Stub de confirmacion (no usado en estas pruebas, requerido por el componente). */
class ConfirmStub {
  confirmar(): Promise<boolean> {
    return Promise.resolve(true);
  }
}

/** Forma minima del componente accedida por las pruebas. */
interface ProductosTest {
  form: { patchValue(v: Record<string, unknown>): void };
  foto(): string | null;
  fotoError(): string | null;
  seleccionarFoto(evento: Event): void;
  quitarFoto(): void;
  nuevo(): void;
  editar(producto: Producto): void;
  guardar(): void;
}

/** Construye un `Event` de tipo change con un FileList que contiene `archivo`. */
function eventoArchivo(archivo: File | null): Event {
  const input = document.createElement('input');
  input.type = 'file';
  Object.defineProperty(input, 'files', {
    value: archivo ? [archivo] : [],
    configurable: true,
  });
  return { target: input } as unknown as Event;
}

/** Crea un File de imagen del tamano indicado (bytes) y tipo MIME dado. */
function archivoImagen(tipo: string, bytes = 8, nombre = 'foto.png'): File {
  return new File([new Uint8Array(bytes)], nombre, { type: tipo });
}

/** Producto de prueba con los campos minimos + foto opcional. */
function productoDe(overrides: Partial<Producto> = {}): Producto {
  return {
    id: 'p-1',
    nombre: 'Letrero luminoso',
    unidad: 'pieza',
    descripcion: 'Letrero LED para exterior',
    clienteMeta: null,
    alianzas: null,
    competencia: null,
    foto: null,
    activo: true,
    version: 0,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('ComercialProductos (foto del producto)', () => {
  let fixture: ComponentFixture<ComercialProductos>;
  let http: HttpTestingController;
  let toast: ToastSpy;

  beforeEach(async () => {
    toast = new ToastSpy();
    await TestBed.configureTestingModule({
      imports: [ComercialProductos, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: toast },
        { provide: AuthService, useValue: new AuthServiceStub() },
        { provide: ConfirmDialogService, useValue: new ConfirmStub() },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ComercialProductos);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  /** Resuelve la carga inicial (GET /productos) con la lista dada. */
  function resolverCarga(productos: Producto[] = []): void {
    const req = http.expectOne((r) => r.url === '/api/v1/productos' && r.method === 'GET');
    req.flush({
      content: productos,
      totalElements: productos.length,
      totalPages: 1,
      number: 0,
      size: 20,
    });
    fixture.detectChanges();
  }

  function componenteDe(): ProductosTest {
    return fixture.componentInstance as unknown as ProductosTest;
  }

  it('seleccionar una imagen valida fija un data-URI en la foto', async () => {
    resolverCarga();
    const c = componenteDe();
    c.nuevo();
    c.seleccionarFoto(eventoArchivo(archivoImagen('image/png')));
    await vi.waitFor(() => expect(c.foto()).toBeTruthy());
    expect(c.foto()!.startsWith('data:')).toBe(true);
    expect(c.fotoError()).toBeNull();
  });

  it('rechaza un archivo que no es imagen con mensaje y sin cambiar la foto', () => {
    resolverCarga();
    const c = componenteDe();
    c.editar(productoDe({ foto: 'data:image/png;base64,PREVIO' }));
    c.seleccionarFoto(eventoArchivo(archivoImagen('application/pdf', 8, 'doc.pdf')));
    expect(c.fotoError()).toBe('El archivo debe ser una imagen (PNG, JPG, WebP, etc.).');
    expect(c.foto()).toBe('data:image/png;base64,PREVIO');
  });

  it('rechaza una imagen que supera 1 MB con mensaje y sin cambiar la foto', () => {
    resolverCarga();
    const c = componenteDe();
    c.nuevo();
    c.seleccionarFoto(eventoArchivo(archivoImagen('image/png', 1024 * 1024 + 1)));
    expect(c.fotoError()).toBe('La foto supera el tamano maximo de 1 MB.');
    expect(c.foto()).toBeNull();
  });

  it('"Quitar foto" limpia la foto', () => {
    resolverCarga();
    const c = componenteDe();
    c.editar(productoDe({ foto: 'data:image/png;base64,PREVIO' }));
    expect(c.foto()).toBe('data:image/png;base64,PREVIO');
    c.quitarFoto();
    expect(c.foto()).toBeNull();
  });

  it('guardar (alta) envia POST /productos con la foto en el cuerpo', async () => {
    resolverCarga();
    const c = componenteDe();
    c.nuevo();
    c.form.patchValue({ nombre: 'Rotulo', unidad: 'pieza', descripcion: 'Rotulo exterior' });
    c.seleccionarFoto(eventoArchivo(archivoImagen('image/png')));
    await vi.waitFor(() => expect(c.foto()).toBeTruthy());
    const dataUri = c.foto();

    c.guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/productos' && r.method === 'POST');
    expect(req.request.body.nombre).toBe('Rotulo');
    expect(req.request.body.foto).toBe(dataUri);
    req.flush(productoDe({ foto: dataUri }));
    // La recarga posterior emite un nuevo GET.
    resolverCarga();
    expect(toast.exitos).toContain('Producto creado.');
  });

  it('editar prefija la foto y guardar envia PUT /productos/{id} con la foto', () => {
    resolverCarga();
    const c = componenteDe();
    c.editar(productoDe({ id: 'p-9', foto: 'data:image/png;base64,EDIT' }));
    expect(c.foto()).toBe('data:image/png;base64,EDIT');

    c.guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/productos/p-9' && r.method === 'PUT');
    expect(req.request.body.foto).toBe('data:image/png;base64,EDIT');
    req.flush(productoDe({ id: 'p-9', foto: 'data:image/png;base64,EDIT' }));
    resolverCarga();
    expect(toast.exitos).toContain('Producto actualizado.');
  });

  it('el listado renderiza una miniatura cuando hay foto', () => {
    resolverCarga([productoDe({ foto: 'data:image/png;base64,LISTA' })]);
    const img = fixture.nativeElement.querySelector('img.producto-miniatura') as HTMLImageElement;
    expect(img).toBeTruthy();
    expect(img.getAttribute('src')).toBe('data:image/png;base64,LISTA');
    expect(img.getAttribute('alt')).toContain('Letrero luminoso');
  });

  it('el listado renderiza un placeholder cuando no hay foto', () => {
    resolverCarga([productoDe({ foto: null })]);
    expect(fixture.nativeElement.querySelector('img.producto-miniatura')).toBeNull();
    expect(fixture.nativeElement.querySelector('.producto-miniatura--vacia')).toBeTruthy();
  });

  it('no muestra la seccion "Precios por lista" sin el permiso lista_precios:listar', () => {
    resolverCarga();
    componenteDe().editar(productoDe());
    fixture.detectChanges();
    // El AuthServiceStub solo concede permisos de 'producto', no 'lista_precios'.
    const cta = fixture.nativeElement.querySelector(
      'a[href="/empresa/comercial/listas-precios"]',
    );
    expect(cta).toBeNull();
  });

  it('no presenta violaciones de accesibilidad en el formulario (WCAG 2.1 A/AA)', async () => {
    resolverCarga();
    componenteDe().nuevo();
    fixture.detectChanges();
    await esperarSinViolaciones(fixture);
  });

  it('no presenta violaciones de accesibilidad en el listado (WCAG 2.1 A/AA)', async () => {
    resolverCarga([productoDe({ foto: 'data:image/png;base64,LISTA' }), productoDe({ id: 'p-2', foto: null })]);
    await esperarSinViolaciones(fixture);
  });
});

// -----------------------------------------------------------------------------
// Seccion "Precios por lista" con permiso lista_precios:listar (Req 4.1, 4.3)
// -----------------------------------------------------------------------------
describe('ComercialProductos (precios por lista)', () => {
  let fixture: ComponentFixture<ComercialProductos>;
  let http: HttpTestingController;

  /** AuthService que concede producto + lista_precios (para ver la seccion). */
  class AuthTodoStub {
    tienePermiso(recurso: string): boolean {
      return recurso === 'producto' || recurso === 'lista_precios';
    }
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ComercialProductos, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: NotificacionesService, useValue: new ToastSpy() },
        { provide: AuthService, useValue: new AuthTodoStub() },
        { provide: ConfirmDialogService, useValue: new ConfirmStub() },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ComercialProductos);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('al editar un producto muestra el acceso directo a Listas de precios (sin UUID)', () => {
    http.expectOne((r) => r.url === '/api/v1/productos' && r.method === 'GET').flush({
      content: [],
      totalElements: 0,
      totalPages: 1,
      number: 0,
      size: 20,
    });
    fixture.detectChanges();
    (fixture.componentInstance as unknown as { editar(p: Producto): void }).editar(productoDe());
    fixture.detectChanges();
    const cta = fixture.nativeElement.querySelector(
      'a[href="/empresa/comercial/listas-precios"]',
    ) as HTMLAnchorElement | null;
    expect(cta).not.toBeNull();
    expect(cta?.textContent).toContain('Gestionar precios por lista');
    // No aparece la seccion al dar de alta (sin producto que editar).
    (fixture.componentInstance as unknown as { nuevo(): void }).nuevo();
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('a[href="/empresa/comercial/listas-precios"]'),
    ).toBeNull();
  });
});
