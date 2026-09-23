// =============================================================================
// Pruebas de la vista de Branding de empresa (admin_empresa) (Req 26)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real, la carga de logotipo como
// ARCHIVO de imagen:
//   - Seleccionar una imagen valida fija un data-URI en el formulario.
//   - Un archivo de tipo no admitido o que supera 1 MB se rechaza con mensaje y
//     NO modifica el logo actual.
//   - "Quitar logo" limpia el logotipo.
//   - Guardar envia PUT /empresa/branding con { nombreVisible, logo }.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { AdminBranding } from './branding';
import { NotificacionesService } from '../../../../shared/services/notificaciones.service';
import { AuthService } from '../../../../core/auth/auth.service';
import { TematizacionService } from '../../../../core/services/tematizacion.service';
import { esperarSinViolaciones } from '../../../../../testing/axe';

/** AuthService de prueba: admin_empresa con permiso branding:actualizar. */
class AuthServiceStub {
  tienePermiso(recurso: string, operacion: string): boolean {
    return recurso === 'branding' && (operacion === 'actualizar' || operacion === 'leer');
  }
}

/** Espia del servicio de notificaciones para verificar los toasts. */
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

/** Forma minima del componente accedida por las pruebas. */
interface BrandingTest {
  formulario: { patchValue(v: Record<string, unknown>): void };
  logo(): string | null;
  logoError(): string | null;
  seleccionarLogo(evento: Event): void;
  quitarLogo(): void;
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
function archivoImagen(tipo: string, bytes = 8, nombre = 'logo.png'): File {
  return new File([new Uint8Array(bytes)], nombre, { type: tipo });
}

describe('AdminBranding (carga de logotipo como archivo)', () => {
  let fixture: ComponentFixture<AdminBranding>;
  let http: HttpTestingController;
  let toast: ToastSpy;

  beforeEach(async () => {
    toast = new ToastSpy();
    await TestBed.configureTestingModule({
      imports: [AdminBranding, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: toast },
        { provide: AuthService, useValue: new AuthServiceStub() },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminBranding);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  /** Resuelve la carga inicial (GET /empresa/branding) con el branding dado. */
  function resolverCarga(
    branding: { nombreVisible: string | null; logo: string | null } = { nombreVisible: 'ACME', logo: null },
  ): void {
    http.expectOne('/api/v1/empresa/branding').flush(branding);
    fixture.detectChanges();
  }

  function componenteDe(): BrandingTest {
    return fixture.componentInstance as unknown as BrandingTest;
  }

  it('seleccionar una imagen valida fija un data-URI en el logo', async () => {
    resolverCarga();
    const c = componenteDe();
    c.seleccionarLogo(eventoArchivo(archivoImagen('image/png')));
    // FileReader.readAsDataURL es asincrono: se espera un microtick.
    await vi.waitFor(() => expect(c.logo()).toBeTruthy());
    expect(c.logo()!.startsWith('data:')).toBe(true);
    expect(c.logoError()).toBeNull();
  });

  it('rechaza un archivo que no es imagen con mensaje y sin cambiar el logo', () => {
    resolverCarga({ nombreVisible: 'ACME', logo: 'data:image/png;base64,PREVIO' });
    const c = componenteDe();
    c.seleccionarLogo(eventoArchivo(archivoImagen('application/pdf', 8, 'doc.pdf')));
    expect(c.logoError()).toBe('Formato no admitido. Usa PNG, JPG, SVG o WebP.');
    // El logo previo se conserva intacto.
    expect(c.logo()).toBe('data:image/png;base64,PREVIO');
  });

  it('rechaza una imagen que supera 1 MB con mensaje y sin cambiar el logo', () => {
    resolverCarga({ nombreVisible: 'ACME', logo: null });
    const c = componenteDe();
    const grande = archivoImagen('image/png', 1024 * 1024 + 1);
    c.seleccionarLogo(eventoArchivo(grande));
    expect(c.logoError()).toBe('El logo supera el tamano maximo de 1 MB.');
    expect(c.logo()).toBeNull();
  });

  it('"Quitar logo" limpia el logotipo', () => {
    resolverCarga({ nombreVisible: 'ACME', logo: 'data:image/png;base64,PREVIO' });
    const c = componenteDe();
    expect(c.logo()).toBe('data:image/png;base64,PREVIO');
    c.quitarLogo();
    expect(c.logo()).toBeNull();
  });

  it('guardar envia PUT /empresa/branding con { nombreVisible, logo }', async () => {
    resolverCarga({ nombreVisible: 'ACME', logo: null });
    const c = componenteDe();
    c.formulario.patchValue({ nombreVisible: 'ACME Rotulos' });
    c.seleccionarLogo(eventoArchivo(archivoImagen('image/png')));
    await vi.waitFor(() => expect(c.logo()).toBeTruthy());
    const dataUri = c.logo();

    c.guardar();
    const req = http.expectOne('/api/v1/empresa/branding');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.nombreVisible).toBe('ACME Rotulos');
    expect(req.request.body.logo).toBe(dataUri);
    req.flush({ nombreVisible: 'ACME Rotulos', logo: dataUri });
    expect(toast.exitos).toContain('Branding actualizado.');
  });

  it('guardar sin logotipo envia logo: null', () => {
    resolverCarga({ nombreVisible: 'ACME', logo: null });
    const c = componenteDe();
    c.formulario.patchValue({ nombreVisible: 'Solo nombre' });
    c.guardar();
    const req = http.expectOne('/api/v1/empresa/branding');
    // El cuerpo incluye colorPrimario (null si no hay color de marca) ademas de
    // nombreVisible y logo; el color se cubre en detalle en la tarea 8.2.
    expect(req.request.body).toEqual({ nombreVisible: 'Solo nombre', logo: null, colorPrimario: null });
    req.flush({ nombreVisible: 'Solo nombre', logo: null, colorPrimario: null });
  });

  it('no presenta violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    resolverCarga();
    await esperarSinViolaciones(fixture);
  }, 30000);
});

// =============================================================================
// Seleccion del color de marca (Tarea 8.2 — Req 1.1–1.5, 1.7, 1.8)
// -----------------------------------------------------------------------------
// Amplian la vista de Branding con el selector de Color_Primario_Marca: presencia
// de controles por permiso, seleccion de preset y entrada libre, validacion de
// formato #RRGGBB, previsualizacion en vivo via TematizacionService (espiado),
// guardado con color en minusculas y limpieza del color (color nulo + limpiar()).
// Reutilizan el mismo patron de montaje (provideHttpClient(Testing), stubs).
// =============================================================================

/**
 * Espia del servicio de tematizacion: registra las invocaciones de
 * previsualizar/aplicar/limpiar para verificar la previsualizacion en vivo (Req 1.5)
 * y la aplicacion/limpieza al guardar (Req 1.6, 1.7) sin tocar el documento real.
 */
class TematizacionServiceSpy {
  previsualizaciones: string[] = [];
  aplicados: string[] = [];
  limpiezas = 0;
  previsualizar(hex: string): void {
    this.previsualizaciones.push(hex);
  }
  aplicar(hex: string): void {
    this.aplicados.push(hex);
  }
  limpiar(): void {
    this.limpiezas += 1;
  }
}

/** Forma del componente accedida por las pruebas de color. */
interface BrandingColorTest {
  formulario: {
    controls: { colorPrimario: { value: string; setValue(v: string): void } };
    patchValue(v: Record<string, unknown>): void;
  };
  presets: ReadonlyArray<{ nombre: string; hex: string }>;
  puedeActualizar: boolean;
  seleccionarPreset(hex: string): void;
  usarColorPorDefecto(): void;
  colorVacio(): boolean;
  colorInvalido(): boolean;
  colorSelector(): string;
  guardar(): void;
}

describe('AdminBranding (seleccion de color de marca) (Req 1.x)', () => {
  let fixture: ComponentFixture<AdminBranding>;
  let http: HttpTestingController;
  let toast: ToastSpy;
  let tematizacion: TematizacionServiceSpy;

  beforeEach(async () => {
    toast = new ToastSpy();
    tematizacion = new TematizacionServiceSpy();
    await TestBed.configureTestingModule({
      imports: [AdminBranding, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: toast },
        { provide: AuthService, useValue: new AuthServiceStub() },
        { provide: TematizacionService, useValue: tematizacion },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminBranding);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  /** Resuelve la carga inicial (GET /empresa/branding) incluyendo colorPrimario. */
  function resolverCarga(
    branding: { nombreVisible: string | null; logo: string | null; colorPrimario?: string | null } = {
      nombreVisible: 'ACME',
      logo: null,
      colorPrimario: null,
    },
  ): void {
    http.expectOne('/api/v1/empresa/branding').flush(branding);
    fixture.detectChanges();
  }

  function componenteDe(): BrandingColorTest {
    return fixture.componentInstance as unknown as BrandingColorTest;
  }

  it('presenta el selector, los presets y el campo hex con permiso (Req 1.1)', () => {
    resolverCarga();
    const el: HTMLElement = fixture.nativeElement;
    // 8 muestras de Preset_Color, el selector nativo y el campo hexadecimal.
    expect(el.querySelectorAll('.admin-branding__preset').length).toBe(8);
    expect(el.querySelector('input[type="color"]')).not.toBeNull();
    expect(el.querySelector('.admin-branding__color-hex')).not.toBeNull();
    // Coherencia con la lista de presets del componente.
    expect(componenteDe().presets.length).toBe(8);
  });

  it('seleccionar un preset fija el Color_Primario_Marca (Req 1.2)', () => {
    resolverCarga();
    const c = componenteDe();
    c.seleccionarPreset('#4f46e5');
    expect(c.formulario.controls.colorPrimario.value).toBe('#4f46e5');
    expect(c.colorInvalido()).toBe(false);
  });

  it('la entrada libre valida se acepta como color valido (Req 1.3)', () => {
    resolverCarga();
    const c = componenteDe();
    c.formulario.controls.colorPrimario.setValue('#abcdef');
    expect(c.colorInvalido()).toBe(false);
    expect(c.colorVacio()).toBe(false);
  });

  it('un color con formato invalido se marca invalido y no emite PUT (Req 1.4)', () => {
    resolverCarga({ nombreVisible: 'ACME', logo: null, colorPrimario: null });
    const c = componenteDe();
    c.formulario.controls.colorPrimario.setValue('rojo');
    expect(c.colorInvalido()).toBe(true);
    c.guardar();
    // Un color invalido no debe generar ninguna peticion de guardado (Req 1.4).
    http.expectNone('/api/v1/empresa/branding');
    // El valor introducido se conserva sin modificar (Req 1.4).
    expect(c.formulario.controls.colorPrimario.value).toBe('rojo');
  });

  it('previsualiza en vivo al fijar un hex valido y limpia al vaciarlo (Req 1.5)', () => {
    resolverCarga();
    const c = componenteDe();
    c.formulario.controls.colorPrimario.setValue('#4f46e5');
    expect(tematizacion.previsualizaciones).toContain('#4f46e5');
    // Al vaciar el color se restaura el Tema_Corporativo via limpiar().
    const limpiezasPrevias = tematizacion.limpiezas;
    c.formulario.controls.colorPrimario.setValue('');
    expect(tematizacion.limpiezas).toBeGreaterThan(limpiezasPrevias);
  });

  it('guardar con color valido envia PUT con colorPrimario en minusculas y aplica el tema (Req 1.6)', () => {
    resolverCarga({ nombreVisible: 'ACME', logo: null, colorPrimario: null });
    const c = componenteDe();
    c.formulario.controls.colorPrimario.setValue('#ABCDEF');
    c.guardar();
    const req = http.expectOne('/api/v1/empresa/branding');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.colorPrimario).toBe('#abcdef');
    req.flush({ nombreVisible: 'ACME', logo: null, colorPrimario: '#abcdef' });
    // Tras OK se aplica definitivamente el color persistido (Req 1.6).
    expect(tematizacion.aplicados).toContain('#abcdef');
  });

  it('usar color por defecto limpia el control y el siguiente guardado envia null (Req 1.7)', () => {
    resolverCarga({ nombreVisible: 'ACME', logo: null, colorPrimario: '#4f46e5' });
    const c = componenteDe();
    c.usarColorPorDefecto();
    expect(c.colorVacio()).toBe(true);
    expect(c.formulario.controls.colorPrimario.value).toBe('');
    c.guardar();
    const req = http.expectOne('/api/v1/empresa/branding');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.colorPrimario).toBeNull();
    req.flush({ nombreVisible: 'ACME', logo: null, colorPrimario: null });
    // Restaura el Tema_Corporativo al limpiar el color (Req 1.7).
    expect(tematizacion.limpiezas).toBeGreaterThan(0);
  });
});

/** AuthService de prueba que NIEGA branding:actualizar (solo lectura). */
class AuthServiceSoloLecturaStub {
  tienePermiso(recurso: string, operacion: string): boolean {
    return recurso === 'branding' && operacion === 'leer';
  }
}

describe('AdminBranding (sin permiso branding:actualizar) (Req 1.8)', () => {
  let fixture: ComponentFixture<AdminBranding>;
  let http: HttpTestingController;
  let tematizacion: TematizacionServiceSpy;

  beforeEach(async () => {
    tematizacion = new TematizacionServiceSpy();
    await TestBed.configureTestingModule({
      imports: [AdminBranding, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: new ToastSpy() },
        { provide: AuthService, useValue: new AuthServiceSoloLecturaStub() },
        { provide: TematizacionService, useValue: tematizacion },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminBranding);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('oculta los controles de edicion de color y no permite guardar (Req 1.8)', () => {
    http.expectOne('/api/v1/empresa/branding').flush({
      nombreVisible: 'ACME',
      logo: null,
      colorPrimario: null,
    });
    fixture.detectChanges();

    const c = fixture.componentInstance as unknown as BrandingColorTest;
    // Sin permiso no se pueden editar los controles de color de marca (Req 1.8).
    expect(c.puedeActualizar).toBe(false);

    const el: HTMLElement = fixture.nativeElement;
    // Los controles de edicion (presets, selector y campo hex) no se renderizan.
    expect(el.querySelectorAll('.admin-branding__preset').length).toBe(0);
    expect(el.querySelector('input[type="color"]')).toBeNull();
    expect(el.querySelector('.admin-branding__color-hex')).toBeNull();
    // No hay boton de guardado disponible.
    expect(el.querySelector('button[type="submit"]')).toBeNull();

    // Un intento programatico de guardar no emite ninguna peticion.
    c.guardar();
    http.expectNone('/api/v1/empresa/branding');
  });
});
