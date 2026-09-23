// =============================================================================
// Pruebas de la vista de Clientes (Req 5) — formulario Nuevo/Editar cliente
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real, la VALIDACION EN EL
// CLIENTE del formulario de alta/edicion y el envio del cuerpo completo:
//   - RFC: patron invalido bloquea el envio; RFC valido (12/13) pasa; el input
//     se limita a 13 caracteres y se envia en mayusculas.
//   - telefono: exactamente 10 digitos (15 no permitido); no digitos rechazados.
//   - email: formato invalido marca error; valido acepta.
//   - al menos un contacto: ambos vacios -> error de formulario; uno presente ok.
//   - tipoPersona: se mapea a 'fisica'/'moral' en el cuerpo.
//   - envio: POST /clientes con los nuevos campos; 409 -> error inline en RFC.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { ComercialClientes } from './clientes';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** AuthService de prueba: concede todos los permisos de cliente. */
class AuthServiceStub {
  tienePermiso(): boolean {
    return true;
  }
  tieneRol(): boolean {
    return false;
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

/** Doble de ConfirmDialogService (la baja no se ejercita aqui). */
class ConfirmStub {
  async confirmar(): Promise<boolean> {
    return true;
  }
}

/** Cliente minimo aceptado por ver()/editar(). */
interface ClienteVista {
  id: string;
  nombre: string;
  rfc: string;
  [k: string]: unknown;
}

/** Forma minima del componente accedida por las pruebas. */
interface ClientesTest {
  form: {
    patchValue(v: Record<string, unknown>): void;
    getRawValue(): Record<string, unknown>;
    invalid: boolean;
    disabled: boolean;
    enabled: boolean;
    hasError(k: string): boolean;
    controls: {
      rfc: { hasError(k: string): boolean; setValue(v: string): void };
      telefono: { hasError(k: string): boolean };
      email: { hasError(k: string): boolean };
    };
  };
  onDireccion(d: {
    calle: string;
    ciudad: string;
    estado: string;
    cp: string;
    pais: string;
  }): void;
  nuevo(): void;
  ver(cliente: ClienteVista): void;
  editar(cliente: ClienteVista): void;
  eliminar(cliente: ClienteVista): Promise<void>;
  cancelar(): void;
  guardar(): void;
  formularioAbierto(): boolean;
  tituloFormulario(): string;
}

/** ClienteDto minimo devuelto por el listado. */
function clienteDto(over: Record<string, unknown> = {}) {
  return {
    id: 'c1',
    nombre: 'Acme',
    rfc: 'ABC010101AB1',
    email: 'ventas@acme.test',
    telefono: '5551234567',
    nombreComercial: null,
    tipoPersona: null,
    telefonoAdicional: null,
    direccionCalle: null,
    direccionCiudad: null,
    direccionEstado: null,
    direccionCp: null,
    direccionPais: null,
    notas: null,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...over,
  };
}

describe('ComercialClientes', () => {
  let fixture: ComponentFixture<ComercialClientes>;
  let http: HttpTestingController;
  let toast: ToastSpy;

  beforeEach(async () => {
    toast = new ToastSpy();
    await TestBed.configureTestingModule({
      imports: [ComercialClientes, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: toast },
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: ConfirmDialogService, useClass: ConfirmStub },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Crea el componente y resuelve la carga inicial del listado. */
  function crear(contenido: Record<string, unknown>[] = [clienteDto()]): void {
    fixture = TestBed.createComponent(ComercialClientes);
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url.startsWith('/api/v1/clientes') && r.method === 'GET');
    req.flush({ content: contenido, totalElements: contenido.length, totalPages: 1, number: 0, size: 20 });
    fixture.detectChanges();
  }

  function comp(): ClientesTest {
    return fixture.componentInstance as unknown as ClientesTest;
  }

  /** Rellena un alta valida minima (nombre, RFC y un contacto). */
  function altaValidaMinima(): void {
    comp().nuevo();
    comp().form.patchValue({
      nombre: 'Acme',
      rfc: 'ABC010101AB1',
      telefono: '5551234567',
    });
  }

  it('un RFC con patron invalido bloquea el envio y marca error', () => {
    crear();
    comp().nuevo();
    comp().form.patchValue({ nombre: 'Acme', rfc: 'INVALIDO', telefono: '5551234567' });
    comp().guardar();
    expect(comp().form.controls.rfc.hasError('rfc')).toBe(true);
    http.expectNone((r) => r.method === 'POST');
  });

  it('acepta un RFC de persona moral (12) y de persona fisica (13)', () => {
    crear();
    comp().nuevo();
    comp().form.patchValue({ nombre: 'Acme', rfc: 'ABC010101AB1', telefono: '5551234567' });
    expect(comp().form.controls.rfc.hasError('rfc')).toBe(false);
    comp().form.controls.rfc.setValue('ABCD901231XYZ');
    expect(comp().form.controls.rfc.hasError('rfc')).toBe(false);
  });

  it('envia el RFC en mayusculas y limita la longitud del input a 13', () => {
    crear();
    altaValidaMinima();
    comp().form.controls.rfc.setValue('abc010101ab1');
    fixture.detectChanges();
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/clientes' && r.method === 'POST');
    expect(req.request.body.rfc).toBe('ABC010101AB1');
    // El input del RFC impone maxlength=13 (se lee antes de que el exito cierre el formulario).
    const input = (fixture.nativeElement as HTMLElement).querySelector(
      'input[formControlName="rfc"]',
    ) as HTMLInputElement;
    expect(input.maxLength).toBe(13);
    req.flush(clienteDto());
    // El exito recarga el listado.
    http.expectOne((r) => r.url.startsWith('/api/v1/clientes') && r.method === 'GET').flush({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    });
  });

  it('el telefono exige exactamente 10 digitos (15 no permitido)', () => {
    crear();
    comp().nuevo();
    comp().form.patchValue({ nombre: 'Acme', rfc: 'ABC010101AB1', telefono: '123456789012345' });
    fixture.detectChanges();
    expect(comp().form.controls.telefono.hasError('pattern')).toBe(true);
    // El input impone maxlength=10.
    const input = (fixture.nativeElement as HTMLElement).querySelector(
      'input[formControlName="telefono"]',
    ) as HTMLInputElement;
    expect(input.maxLength).toBe(10);
  });

  it('el telefono rechaza caracteres no numericos', () => {
    crear();
    comp().nuevo();
    comp().form.patchValue({ nombre: 'Acme', rfc: 'ABC010101AB1', telefono: '55512abcd0' });
    expect(comp().form.controls.telefono.hasError('pattern')).toBe(true);
  });

  it('un correo invalido marca error y uno valido lo limpia', () => {
    crear();
    comp().nuevo();
    comp().form.patchValue({ email: 'no-es-correo' });
    expect(comp().form.controls.email.hasError('email')).toBe(true);
    comp().form.patchValue({ email: 'ventas@acme.test' });
    expect(comp().form.controls.email.hasError('email')).toBe(false);
  });

  it('exige al menos un contacto: ambos vacios -> error de formulario', () => {
    crear();
    comp().nuevo();
    comp().form.patchValue({ nombre: 'Acme', rfc: 'ABC010101AB1', email: '', telefono: '' });
    expect(comp().form.hasError('contacto')).toBe(true);
    comp().guardar();
    http.expectNone((r) => r.method === 'POST');
    // Con un contacto presente desaparece el error de formulario.
    comp().form.patchValue({ email: 'ventas@acme.test' });
    expect(comp().form.hasError('contacto')).toBe(false);
  });

  it('mapea el tipo de persona a "fisica"/"moral" en el cuerpo', () => {
    crear();
    altaValidaMinima();
    comp().form.patchValue({ tipoPersona: 'moral' });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/clientes' && r.method === 'POST');
    expect(req.request.body.tipoPersona).toBe('moral');
    req.flush(clienteDto());
    // El exito recarga el listado.
    http.expectOne((r) => r.url.startsWith('/api/v1/clientes') && r.method === 'GET').flush({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    });
  });

  it('envia el cuerpo completo con los nuevos campos y notifica exito', () => {
    crear();
    comp().nuevo();
    comp().form.patchValue({
      nombre: 'Acme',
      nombreComercial: 'Acme MX',
      tipoPersona: 'fisica',
      rfc: 'ABCD901231XYZ',
      email: 'ventas@acme.test',
      telefono: '5551234567',
      telefonoAdicional: '5559876543',
      direccionCalle: 'Av. Central 100',
      direccionCiudad: 'Monterrey',
      direccionEstado: 'NL',
      direccionCp: '64000',
      direccionPais: 'Mexico',
      notas: 'Cliente preferente',
    });
    comp().guardar();
    const req = http.expectOne((r) => r.url === '/api/v1/clientes' && r.method === 'POST');
    const body = req.request.body;
    expect(body.nombreComercial).toBe('Acme MX');
    expect(body.tipoPersona).toBe('fisica');
    expect(body.telefonoAdicional).toBe('5559876543');
    expect(body.direccionCiudad).toBe('Monterrey');
    expect(body.direccionCp).toBe('64000');
    expect(body.notas).toBe('Cliente preferente');
    req.flush(clienteDto());
    // Tras el exito se recarga el listado.
    http.expectOne((r) => r.url.startsWith('/api/v1/clientes') && r.method === 'GET').flush({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    });
    expect(toast.exitos).toContain('Cliente creado.');
  });

  it('al elegir una sugerencia del autocompletado, autollena los campos de direccion', () => {
    crear();
    comp().nuevo();
    comp().onDireccion({
      calle: 'Avenida Juarez 100',
      ciudad: 'Toluca',
      estado: 'Estado de Mexico',
      cp: '50000',
      pais: 'Mexico',
    });
    const v = comp().form.getRawValue();
    expect(v['direccionCalle']).toBe('Avenida Juarez 100');
    expect(v['direccionCiudad']).toBe('Toluca');
    expect(v['direccionEstado']).toBe('Estado de Mexico');
    expect(v['direccionCp']).toBe('50000');
    expect(v['direccionPais']).toBe('Mexico');
  });

  it('ante 409 coloca el error de RFC duplicado en el campo RFC', () => {
    crear();
    altaValidaMinima();
    comp().guardar();
    http
      .expectOne((r) => r.url === '/api/v1/clientes' && r.method === 'POST')
      .flush({ detail: 'RFC duplicado' }, { status: 409, statusText: 'Conflict' });
    expect(comp().form.controls.rfc.hasError('duplicado')).toBe(true);
    expect(toast.errores).toContain('Ya existe un cliente con ese RFC.');
  });

  it('ver() navega a la Ficha 360 del cliente por su id (sin teclear el identificador)', () => {
    crear();
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    comp().ver(clienteDto() as unknown as ClienteVista);
    expect(navegar).toHaveBeenCalledWith(['/empresa/comercial/clientes', 'c1']);
  });

  it('eliminar() con el formulario abierto para ese cliente lo cierra y re-habilita', async () => {
    crear();
    const dto = clienteDto() as unknown as ClienteVista;
    comp().editar(dto);
    fixture.detectChanges();
    expect(comp().formularioAbierto()).toBe(true);
    await comp().eliminar(dto);
    http.expectOne((r) => r.url === `/api/v1/clientes/${dto.id}` && r.method === 'DELETE').flush(null);
    // Tras la baja recarga el listado.
    http.expectOne((r) => r.url.startsWith('/api/v1/clientes') && r.method === 'GET').flush({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    });
    fixture.detectChanges();
    expect(comp().formularioAbierto()).toBe(false);
    expect(comp().form.enabled).toBe(true);
  });

  it('la accion de ver es un boton que navega (no un enlace directo en la fila)', () => {
    crear();
    const host = fixture.nativeElement as HTMLElement;
    const botonVer = host.querySelector('button[aria-label="Ver detalle del cliente"]');
    expect(botonVer).not.toBeNull();
    // No debe existir ningun enlace de navegacion (routerLink) en la fila.
    expect(host.querySelector('.comercial-acciones-fila a[href]')).toBeNull();
    expect(host.querySelector('a[aria-label="Ver detalle del cliente"]')).toBeNull();
  });

  it('no tiene violaciones de accesibilidad con el formulario abierto (WCAG 2.1 A/AA)', async () => {
    crear();
    comp().nuevo();
    fixture.detectChanges();
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
