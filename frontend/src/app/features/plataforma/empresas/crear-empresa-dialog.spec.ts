// =============================================================================
// Pruebas del dialogo CrearEmpresaDialog: giro obligatorio, RFC, logo, campos
// descriptivos y accesibilidad (Req 9, 24.2, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - El formulario expone un control `giroId` obligatorio.
//   - El control `rfc` usa el validador de RFC mexicano.
//   - Las secciones del formulario (Identidad, Contacto, Direccion, ...) existen.
//   - Sin giro seleccionado el envio se bloquea (no se emite POST /empresas).
//   - Con datos validos el envio dispara POST /empresas incluyendo `giroId` y los
//     campos descriptivos opcionales rellenados.
//   - La carga de logo fija un data-URI que viaja en el cuerpo del POST.
//   - Cuando no hay giros activos se muestra el aviso y se deshabilita el envio.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { CrearEmpresaDialog } from './crear-empresa-dialog';
import { Giro, PaqueteSuscripcion, Plan } from '../models/plataforma.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** MatDialogRef de prueba que registra si se cerro y con que resultado. */
class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

/** Forma minima del componente accedida por las pruebas. */
interface CrearEmpresaDialogTest {
  formulario: {
    patchValue(v: Record<string, unknown>): void;
    controls: {
      giroId: { hasError(clave: string): boolean };
      rfc: { setValue(v: string): void; hasError(clave: string): boolean };
      emailContacto: { setValue(v: string): void; hasError(clave: string): boolean };
    };
  };
  logo: { set(v: string | null): void };
  cambiarGiro(giroId: string): void;
  cambiarPlan(planId: string): void;
  cambiarPaquete(paqueteId: string): void;
  cambiarTipoInstrumento(tipo: 'plan' | 'suscripcion'): void;
  tipoInstrumentoSeleccionado(): 'plan' | 'suscripcion';
  guardar(): void;
}

function plan(parcial: Partial<Plan> = {}): Plan {
  return {
    id: 'p1',
    nombre: 'Plan Base',
    maxUsuarios: 10,
    duracionDias: 730,
    giroId: null,
    monedaCodigo: 'MXN',
    preciosModulos: {},
    total: 0,
    modulosHabilitados: [],
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...parcial,
  };
}

function paquete(parcial: Partial<PaqueteSuscripcion> & { id: string }): PaqueteSuscripcion {
  return {
    nombre: 'Paquete',
    maxUsuarios: 5,
    giroId: null,
    monedaCodigo: 'MXN',
    preciosModulos: {},
    total: 0,
    modulosHabilitados: [],
    duracionDias: 30,
    admitePrueba: false,
    duracionPruebaMeses: null,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...parcial,
  };
}

function giro(parcial: Partial<Giro> = {}): Giro {
  return {
    id: 'g1',
    clave: 'carpinteria',
    nombreVisible: 'Carpinteria',
    descripcion: null,
    activo: true,
    version: 0,
    tieneReglasNegocio: false,
    modulosEspecificos: 0,
    ...parcial,
  };
}

/** EmpresaDto de respuesta (forma actual del backend). */
function empresaDto() {
  return {
    id: 'e1',
    nombre: 'Acme',
    rfc: 'ABCD901231XYZ',
    giroId: 'g1',
    estado: 'activa',
    brandingNombreVisible: null,
    brandingLogo: null,
    nombreComercial: null,
    emailContacto: null,
    telefono: null,
    sitioWeb: null,
    direccion: null,
    notas: null,
    fechaCancelacion: null,
    finPeriodoGracia: null,
    planVigente: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

describe('CrearEmpresaDialog', () => {
  let fixture: ComponentFixture<CrearEmpresaDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  beforeEach(async () => {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [CrearEmpresaDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CrearEmpresaDialog);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /**
   * Resuelve las cargas iniciales del dialogo. Ademas de los planes, los giros
   * activos y el catalogo de modulos, el alta ahora ofrece el instrumento
   * excluyente Plan|Suscripcion, por lo que el dialogo tambien carga los
   * Paquetes de Suscripcion; se responde esa peticion (vacia por defecto) para
   * no dejar solicitudes pendientes.
   */
  function resolverCargas(giros: Giro[], paquetes: PaqueteSuscripcion[] = []): void {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === '/api/v1/planes')
      .flush({ content: [plan()], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    http
      .expectOne((r) => r.url === '/api/v1/paquetes-suscripcion')
      .flush({
        content: paquetes,
        page: 0,
        size: 100,
        totalElements: paquetes.length,
        totalPages: paquetes.length === 0 ? 0 : 1,
      });
    http
      .expectOne((r) => r.url === '/api/v1/plataforma/giros' && r.params.get('activo') === 'true')
      .flush({ content: giros, page: 0, size: 100, totalElements: giros.length, totalPages: 1 });
    http.expectOne('/api/v1/plataforma/modulos').flush([]);
    fixture.detectChanges();
  }

  function componenteDe(): CrearEmpresaDialogTest {
    return fixture.componentInstance as unknown as CrearEmpresaDialogTest;
  }

  it('el formulario expone un control giroId obligatorio', () => {
    resolverCargas([giro()]);
    expect(componenteDe().formulario.controls.giroId.hasError('required')).toBe(true);
  });

  it('el formulario expone un control emailContacto obligatorio', () => {
    resolverCargas([giro()]);
    const c = componenteDe();
    // Vacio: viola `required`.
    expect(c.formulario.controls.emailContacto.hasError('required')).toBe(true);
    // Con formato invalido: viola `email`.
    c.formulario.controls.emailContacto.setValue('no-es-correo');
    expect(c.formulario.controls.emailContacto.hasError('email')).toBe(true);
    // Con un correo valido: sin errores.
    c.formulario.controls.emailContacto.setValue('contacto@acme.test');
    expect(c.formulario.controls.emailContacto.hasError('required')).toBe(false);
    expect(c.formulario.controls.emailContacto.hasError('email')).toBe(false);
  });

  it('bloquea el envio cuando falta el correo de contacto (no emite POST /empresas)', () => {
    resolverCargas([giro()]);
    const c = componenteDe();
    // Completa todo menos el correo de contacto.
    c.formulario.patchValue({
      nombre: 'Acme',
      giroId: 'g1',
      rfc: 'ABCD901231XYZ',
      planId: 'p1',
      adminIdentificador: 'admin@acme.test',
    });
    c.guardar();
    http.expectNone('/api/v1/empresas');
  });

  it('el control rfc rechaza un RFC mal formado con el validador de RFC', () => {
    resolverCargas([giro()]);
    const c = componenteDe();
    c.formulario.controls.rfc.setValue('NO-VALIDO');
    expect(c.formulario.controls.rfc.hasError('rfc')).toBe(true);
    c.formulario.controls.rfc.setValue('ABC010101AB1');
    expect(c.formulario.controls.rfc.hasError('rfc')).toBe(false);
  });

  it('muestra las secciones del formulario', () => {
    resolverCargas([giro()]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Identidad');
    expect(texto).toContain('Contacto');
    expect(texto).toContain('Direccion');
    // La antigua seccion "Plan y modulos" se reestructuro en "Contratación"
    // (instrumento comercial excluyente Plan|Suscripción + modulos del mismo).
    expect(texto).toContain('Contratación');
    expect(texto).toContain('Administrador inicial');
  });

  it('bloquea el envio cuando falta el giro (no emite POST /empresas)', () => {
    resolverCargas([giro()]);
    const c = componenteDe();
    // Completa todo menos el giro.
    c.formulario.patchValue({
      nombre: 'Acme',
      rfc: 'ABCD901231XYZ',
      planId: 'p1',
      adminIdentificador: 'admin@acme.test',
    });
    c.guardar();
    http.expectNone('/api/v1/empresas');
  });

  it('envia POST /empresas con giroId, campos descriptivos y logo', () => {
    resolverCargas([giro()]);
    const c = componenteDe();
    c.formulario.patchValue({
      nombre: 'Acme',
      giroId: 'g1',
      rfc: 'ABCD901231XYZ',
      planId: 'p1',
      adminIdentificador: 'admin@acme.test',
      nombreComercial: 'Acme Signs',
      emailContacto: 'contacto@acme.test',
      telefono: '5551234567',
      direccionCiudad: 'Monterrey',
    });
    c.logo.set('data:image/png;base64,AAAA');
    c.guardar();
    const req = http.expectOne('/api/v1/empresas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.giroId).toBe('g1');
    expect(req.request.body.nombreComercial).toBe('Acme Signs');
    expect(req.request.body.emailContacto).toBe('contacto@acme.test');
    expect(req.request.body.telefono).toBe('5551234567');
    expect(req.request.body.direccionCiudad).toBe('Monterrey');
    expect(req.request.body.logo).toBe('data:image/png;base64,AAAA');
    req.flush({
      empresa: empresaDto(),
      adminUsuarioId: 'u1',
      adminIdentificador: 'admin@acme.test',
      adminPasswordTemporal: null,
    });
  });

  it('muestra la nota informativa al seleccionar un giro "Base" (sin reglas de negocio)', () => {
    resolverCargas([giro({ id: 'g1', tieneReglasNegocio: false })]);
    const c = componenteDe();
    c.formulario.patchValue({ giroId: 'g1' });
    c.cambiarGiro('g1');
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('solo incluye los módulos base');
  });

  it('no muestra la nota cuando el giro seleccionado es "Completo"', () => {
    resolverCargas([giro({ id: 'g1', tieneReglasNegocio: true, modulosEspecificos: 2 })]);
    const c = componenteDe();
    c.formulario.patchValue({ giroId: 'g1' });
    c.cambiarGiro('g1');
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).not.toContain('solo incluye los módulos base');
  });

  it('avisa y deshabilita el envio cuando no hay giros activos', () => {
    resolverCargas([]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('No hay giros activos');
    const boton = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Crear empresa'),
    ) as HTMLButtonElement;
    expect(boton.disabled).toBe(true);
  });

  it('avisa cuando el plan incluye modulos de un giro distinto al de la empresa', () => {
    // Empresa de giro "carpinteria"; el plan incluye un modulo especifico de
    // "anuncios-luminosos" (giro ajeno) -> debe mostrarse el aviso.
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/planes').flush({
      content: [plan({ id: 'p1', modulosHabilitados: ['comercial', 'operacion'] })],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    http
      .expectOne((r) => r.url === '/api/v1/paquetes-suscripcion')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    http
      .expectOne((r) => r.url === '/api/v1/plataforma/giros' && r.params.get('activo') === 'true')
      .flush({
        content: [giro({ id: 'g1', clave: 'carpinteria', nombreVisible: 'Carpinteria' })],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
      });
    http.expectOne('/api/v1/plataforma/modulos').flush([
      { clave: 'comercial', nombreVisible: 'Comercial (CRM)', giro: null, catalogoModuloId: 'c1', precio: 1500, monedaCodigo: 'MXN' },
      { clave: 'operacion', nombreVisible: 'Operacion', giro: 'anuncios-luminosos', catalogoModuloId: 'c2', precio: 800, monedaCodigo: 'MXN' },
    ]);
    fixture.detectChanges();

    const c = componenteDe() as unknown as {
      formulario: { patchValue(v: Record<string, unknown>): void };
      cambiarGiro(id: string): void;
      cambiarPlan(id: string): void;
    };
    c.formulario.patchValue({ giroId: 'g1', planId: 'p1' });
    c.cambiarGiro('g1');
    c.cambiarPlan('p1');
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('modulos especificos del giro');
    // La etiqueta se humaniza a partir de la clave del giro ajeno.
    expect(texto).toContain('Anuncios luminosos');
  });

  it('no avisa cuando los modulos del plan corresponden al giro de la empresa', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/planes').flush({
      content: [plan({ id: 'p1', modulosHabilitados: ['comercial', 'operacion'] })],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    http
      .expectOne((r) => r.url === '/api/v1/paquetes-suscripcion')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    http
      .expectOne((r) => r.url === '/api/v1/plataforma/giros' && r.params.get('activo') === 'true')
      .flush({
        content: [giro({ id: 'g1', clave: 'anuncios-luminosos', nombreVisible: 'Anuncios luminosos' })],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
      });
    http.expectOne('/api/v1/plataforma/modulos').flush([
      { clave: 'comercial', nombreVisible: 'Comercial (CRM)', giro: null, catalogoModuloId: 'c1', precio: 1500, monedaCodigo: 'MXN' },
      { clave: 'operacion', nombreVisible: 'Operacion', giro: 'anuncios-luminosos', catalogoModuloId: 'c2', precio: 800, monedaCodigo: 'MXN' },
    ]);
    fixture.detectChanges();

    const c = componenteDe() as unknown as {
      formulario: { patchValue(v: Record<string, unknown>): void };
      cambiarGiro(id: string): void;
      cambiarPlan(id: string): void;
    };
    c.formulario.patchValue({ giroId: 'g1', planId: 'p1' });
    c.cambiarGiro('g1');
    c.cambiarPlan('p1');
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).not.toContain('modulos especificos del giro');
  });

  // ---------------------------------------------------------------------------
  // Instrumento comercial EXCLUYENTE Plan | Suscripcion (Req 4.3, 4.4, 4.5).
  // ---------------------------------------------------------------------------

  it('por defecto el instrumento es Plan y el select de Plan está visible', () => {
    resolverCargas([giro()], [paquete({ id: 'q1' })]);
    const c = componenteDe();
    // Por defecto el instrumento elegido es un Plan.
    expect(c.tipoInstrumentoSeleccionado()).toBe('plan');
    const el = fixture.nativeElement as HTMLElement;
    const texto = el.textContent ?? '';
    // El radiogroup ofrece ambas opciones excluyentes.
    expect(el.querySelector('mat-radio-group')).not.toBeNull();
    expect(texto).toContain('Plan');
    expect(texto).toContain('Suscripción');
    // El select del instrumento por defecto (Plan) esta presente.
    const etiquetas = Array.from(el.querySelectorAll('mat-label')).map((n) => n.textContent ?? '');
    expect(etiquetas).toContain('Plan');
  });

  it('al elegir Suscripción se pide paquete y el envío se bloquea sin elegir uno', () => {
    resolverCargas([giro()], [paquete({ id: 'q1' })]);
    const c = componenteDe();
    c.cambiarTipoInstrumento('suscripcion');
    fixture.detectChanges();
    // El select de Paquete de suscripcion se muestra en lugar del de Plan.
    const etiquetas = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('mat-label'),
    ).map((n) => n.textContent ?? '');
    expect(etiquetas).toContain('Paquete de suscripción');
    // Se rellena todo menos el paquete: al ser instrumento Suscripcion el paquete
    // es requerido, de modo que el envio queda bloqueado (exclusividad).
    c.formulario.patchValue({
      nombre: 'Acme',
      giroId: 'g1',
      rfc: 'ABCD901231XYZ',
      emailContacto: 'contacto@acme.test',
      adminIdentificador: 'admin@acme.test',
      tipoInstrumento: 'suscripcion',
    });
    c.guardar();
    http.expectNone('/api/v1/empresas');
  });

  it('envía POST /empresas con paqueteSuscripcionId (no planId) al elegir Suscripción', () => {
    resolverCargas([giro()], [paquete({ id: 'q1' })]);
    const c = componenteDe();
    c.cambiarTipoInstrumento('suscripcion');
    c.cambiarPaquete('q1');
    c.formulario.patchValue({
      nombre: 'Acme',
      giroId: 'g1',
      rfc: 'ABCD901231XYZ',
      emailContacto: 'contacto@acme.test',
      adminIdentificador: 'admin@acme.test',
      tipoInstrumento: 'suscripcion',
      paqueteSuscripcionId: 'q1',
    });
    c.guardar();
    const req = http.expectOne('/api/v1/empresas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.paqueteSuscripcionId).toBe('q1');
    expect(req.request.body.planId).toBeNull();
    // El paquete no admite prueba: otorgarPrueba viaja en false.
    expect(req.request.body.otorgarPrueba).toBe(false);
    req.flush({
      empresa: empresaDto(),
      adminUsuarioId: 'u1',
      adminIdentificador: 'admin@acme.test',
      adminPasswordTemporal: null,
    });
  });

  it("el checkbox 'Otorgar periodo de prueba' aparece solo con un paquete que admite prueba", () => {
    // Caso con prueba: el checkbox se muestra.
    resolverCargas([giro()], [paquete({ id: 'q1', admitePrueba: true, duracionPruebaMeses: 3 })]);
    const c = componenteDe();
    c.cambiarTipoInstrumento('suscripcion');
    c.cambiarPaquete('q1');
    c.formulario.patchValue({ paqueteSuscripcionId: 'q1' });
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Otorgar periodo de prueba');
  });

  it("el checkbox 'Otorgar periodo de prueba' NO aparece con un paquete que no admite prueba", () => {
    resolverCargas([giro()], [paquete({ id: 'q2', admitePrueba: false })]);
    const c = componenteDe();
    c.cambiarTipoInstrumento('suscripcion');
    c.cambiarPaquete('q2');
    c.formulario.patchValue({ paqueteSuscripcionId: 'q2' });
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).not.toContain('Otorgar periodo de prueba');
  });

  it('envía otorgarPrueba=true cuando se marca la casilla en un paquete que admite prueba', () => {
    resolverCargas([giro()], [paquete({ id: 'q1', admitePrueba: true, duracionPruebaMeses: 3 })]);
    const c = componenteDe();
    c.cambiarTipoInstrumento('suscripcion');
    c.cambiarPaquete('q1');
    c.formulario.patchValue({
      nombre: 'Acme',
      giroId: 'g1',
      rfc: 'ABCD901231XYZ',
      emailContacto: 'contacto@acme.test',
      adminIdentificador: 'admin@acme.test',
      tipoInstrumento: 'suscripcion',
      paqueteSuscripcionId: 'q1',
      otorgarPrueba: true,
    });
    c.guardar();
    const req = http.expectOne('/api/v1/empresas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.otorgarPrueba).toBe(true);
    expect(req.request.body.paqueteSuscripcionId).toBe('q1');
    expect(req.request.body.planId).toBeNull();
    req.flush({
      empresa: empresaDto(),
      adminUsuarioId: 'u1',
      adminIdentificador: 'admin@acme.test',
      adminPasswordTemporal: null,
    });
  });

  it('al volver a Plan se limpia el paquete y planId vuelve a ser requerido (envío de Plan)', () => {
    resolverCargas([giro()], [paquete({ id: 'q1', admitePrueba: true, duracionPruebaMeses: 3 })]);
    const c = componenteDe();
    // Empezamos en Suscripcion con un paquete elegido...
    c.cambiarTipoInstrumento('suscripcion');
    c.cambiarPaquete('q1');
    c.formulario.patchValue({ paqueteSuscripcionId: 'q1', otorgarPrueba: true });
    // ...y volvemos a Plan: el paquete se limpia y planId vuelve a ser requerido.
    c.cambiarTipoInstrumento('plan');
    c.cambiarPlan('p1');
    c.formulario.patchValue({
      nombre: 'Acme',
      giroId: 'g1',
      rfc: 'ABCD901231XYZ',
      emailContacto: 'contacto@acme.test',
      adminIdentificador: 'admin@acme.test',
      tipoInstrumento: 'plan',
      planId: 'p1',
    });
    c.guardar();
    const req = http.expectOne('/api/v1/empresas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.planId).toBe('p1');
    expect(req.request.body.paqueteSuscripcionId).toBeNull();
    expect(req.request.body.otorgarPrueba).toBe(false);
    req.flush({
      empresa: empresaDto(),
      adminUsuarioId: 'u1',
      adminIdentificador: 'admin@acme.test',
      adminPasswordTemporal: null,
    });
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    resolverCargas([giro()]);
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
