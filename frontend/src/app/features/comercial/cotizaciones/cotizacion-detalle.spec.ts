// =============================================================================
// Pruebas de la vista ComercialCotizacionDetalle (Req 6, 15)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless), la
// experiencia enterprise del detalle de una Cotizacion:
//   - Renderiza folio, chip de estado, datos del cliente, partidas y total.
//   - "Descargar PDF" dispara la descarga del blob (createObjectURL + anchor).
//   - "Enviar por correo" abre el dialogo, envia con el correo elegido y avisa.
//   - "Enviar por WhatsApp" construye un enlace wa.me con folio y total y abre
//     una nueva pestana (window.open).
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';
import { LOCALE_ID } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { ComercialCotizacionDetalle } from './cotizacion-detalle';
import { EnviarCorreoDialogService } from './enviar-correo-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { Cotizacion } from '../models/comercial.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

registerLocaleData(localeEsMx);

/**
 * AuthService de prueba: por defecto concede todos los permisos comerciales/pruebas.
 * Los permisos denegados se pueden configurar por prueba (recurso:operacion) para
 * verificar el gating de los enlaces a Cliente/Oportunidad (Req 3.1, 7.2).
 */
class AuthServiceStub {
  private readonly denegados = new Set<string>();
  /** Por defecto el tenant es de anuncios-luminosos con el modulo operacion. */
  private modulos = new Set<string>(['operacion']);
  private giroClave: string | null = 'anuncios-luminosos';

  denegar(recurso: string, operacion: string): void {
    this.denegados.add(`${recurso}:${operacion}`);
  }

  /** Simula un tenant sin el modulo indicado (gating por modulo del vertical). */
  sinModulo(clave: string): void {
    this.modulos.delete(clave);
  }

  /** Simula un tenant de otro Giro (gating por giro del Vertical_Anuncios). */
  conGiro(clave: string | null): void {
    this.giroClave = clave;
  }

  tienePermiso(recurso: string, operacion: string): boolean {
    return !this.denegados.has(`${recurso}:${operacion}`);
  }

  tieneModulo(clave: string): boolean {
    return this.modulos.has(clave);
  }

  esGiro(clave: string): boolean {
    return this.giroClave === clave;
  }
}

/** Espia del servicio de notificaciones para asertar los mensajes. */
class ToastSpy {
  exito = vi.fn();
  error = vi.fn();
  info = vi.fn();
}

/** Cotizacion de detalle de prueba con todos los campos descriptivos. */
function cotizacion(): Cotizacion {
  return {
    id: 'cot-1',
    clienteId: 'cli-1',
    oportunidadId: null,
    estado: 'borrador',
    subtotal: 1500,
    descuentoGlobal: 0,
    iva: 240,
    retencionIsr: 0,
    retencionIva: 0,
    total: 1740,
    partidas: [
      {
        id: 'p1',
        productoId: null,
        descripcion: 'Letrero luminoso',
        cantidad: 1,
        precioUnitario: 1500,
        descuento: 0,
        importeBase: 1500,
        tasaIva: '16',
        iva: 240,
        subtotal: 1500,
      },
    ],
    canalVentaId: null,
    folio: 'COT-2026-0001',
    fechaEmision: '2026-01-01',
    // Sin fecha de vigencia por defecto (no vence): los tests de envío/estado usan
    // este caso; la vigencia (Req 6.11) se ejercita con cotizacionVencida()/vigente().
    validoHasta: null,
    condiciones: 'Precios en MXN, mas IVA.',
    notas: 'Entrega en 10 dias.',
    moneda: 'MXN',
    enviadaEn: null,
    clienteNombre: 'Acme S.A.',
    clienteRfc: 'ABCD901231XYZ',
    clienteEmail: 'contacto@acme.com',
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

describe('ComercialCotizacionDetalle', () => {
  let fixture: ComponentFixture<ComercialCotizacionDetalle>;
  let http: HttpTestingController;
  let toast: ToastSpy;
  let auth: AuthServiceStub;
  let correoDialog: { pedir: ReturnType<typeof vi.fn> };

  /** Canal de venta de prueba para poblar el selector y el mapa id -> nombre. */
  const CANAL = { id: 'canal-1', nombre: 'Directo', activo: true } as const;

  /** Resuelve el GET de canales de venta que dispara ngOnInit (Req 3.3). */
  function resolverCanales(canales: ReadonlyArray<Record<string, unknown>> = [CANAL]): void {
    http.expectOne((r) => r.url === '/api/v1/canales-venta' && r.method === 'GET').flush({
      content: canales,
      page: 0,
      size: 100,
      totalElements: canales.length,
      totalPages: 1,
    });
  }

  /** Crea el componente con el id de ruta y resuelve la carga inicial. */
  function iniciar(dto: Cotizacion = cotizacion()): void {
    fixture = TestBed.createComponent(ComercialCotizacionDetalle);
    fixture.componentRef.setInput('id', 'cot-1');
    fixture.detectChanges();
    // consultar() + listarPruebas() (por permiso) + cargarCanales() al construir.
    http.expectOne('/api/v1/cotizaciones/cot-1').flush(dto);
    http.expectOne((r) => r.url === '/api/v1/cotizaciones/cot-1/pruebas-diseno').flush({
      content: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    });
    resolverCanales();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    toast = new ToastSpy();
    auth = new AuthServiceStub();
    correoDialog = { pedir: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [ComercialCotizacionDetalle, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: NotificacionesService, useValue: toast },
        { provide: EnviarCorreoDialogService, useValue: correoDialog },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renderiza folio, estado, cliente, partidas y total', () => {
    iniciar();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Cotizacion COT-2026-0001');
    expect(texto).toContain('Borrador');
    expect(texto).toContain('Acme S.A.');
    expect(texto).toContain('ABCD901231XYZ');
    expect(texto).toContain('contacto@acme.com');
    expect(texto).toContain('Letrero luminoso');
    expect(texto).toContain('Precios en MXN, mas IVA.');
    // El chip de estado se renderiza como componente.
    expect(fixture.nativeElement.querySelector('app-estado-chip')).toBeTruthy();
  });

  it('Descargar PDF dispara la descarga del blob (createObjectURL + anchor)', () => {
    iniciar();
    const url = 'blob:mock-url';
    const createSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue(url);
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);

    (fixture.componentInstance as unknown as { descargarPdf(): void }).descargarPdf();
    const req = http.expectOne('/api/v1/cotizaciones/cot-1/pdf');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['%PDF'], { type: 'application/pdf' }));

    expect(createSpy).toHaveBeenCalledOnce();
    expect(clickSpy).toHaveBeenCalledOnce();
    expect(revokeSpy).toHaveBeenCalledWith(url);

    createSpy.mockRestore();
    revokeSpy.mockRestore();
    clickSpy.mockRestore();
  });

  it('Enviar por correo usa el correo elegido en el dialogo y avisa el exito', async () => {
    iniciar();
    correoDialog.pedir.mockResolvedValue('nuevo@acme.com');

    await (fixture.componentInstance as unknown as { enviarCorreo(): Promise<void> }).enviarCorreo();

    expect(correoDialog.pedir).toHaveBeenCalledWith({
      folio: 'COT-2026-0001',
      correoCliente: 'contacto@acme.com',
    });
    const req = http.expectOne('/api/v1/cotizaciones/cot-1/enviar-correo');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'nuevo@acme.com' });
    req.flush({ ...cotizacion(), estado: 'enviada', enviadaEn: '2026-01-02T10:00:00Z' });

    expect(toast.exito).toHaveBeenCalledWith('Cotizacion enviada a nuevo@acme.com.');
  });

  it('Enviar por correo no hace peticion si se cancela el dialogo', async () => {
    iniciar();
    correoDialog.pedir.mockResolvedValue(null);
    await (fixture.componentInstance as unknown as { enviarCorreo(): Promise<void> }).enviarCorreo();
    http.expectNone('/api/v1/cotizaciones/cot-1/enviar-correo');
  });

  it('Enviar por WhatsApp abre wa.me con el folio y el total', () => {
    iniciar();
    const openSpy = vi.spyOn(window, 'open').mockReturnValue(null);

    (fixture.componentInstance as unknown as { compartirWhatsApp(): void }).compartirWhatsApp();

    expect(openSpy).toHaveBeenCalledOnce();
    const [urlLlamada, target] = openSpy.mock.calls[0];
    const url = String(urlLlamada);
    expect(url.startsWith('https://wa.me/?text=')).toBe(true);
    const decodificado = decodeURIComponent(url.replace('https://wa.me/?text=', ''));
    expect(decodificado).toContain('COT-2026-0001');
    expect(decodificado).toContain('Acme S.A.');
    expect(target).toBe('_blank');
    expect(toast.info).toHaveBeenCalled();

    openSpy.mockRestore();
  });

  it('muestra el aviso de datos fiscales incompletos cuando emisorIncompleto es true', () => {
    iniciar({ ...cotizacion(), emisorIncompleto: true });
    const aviso = fixture.nativeElement.querySelector(
      '.cotizacion-detalle__aviso-fiscal',
    ) as HTMLElement | null;
    expect(aviso).not.toBeNull();
    expect(aviso?.getAttribute('role')).toBe('status');
    expect(aviso?.textContent ?? '').toContain('datos fiscales de tu empresa');
    expect(aviso?.textContent ?? '').toContain('Mi empresa');
  });

  it('no muestra el aviso fiscal cuando emisorIncompleto es false o ausente', () => {
    iniciar({ ...cotizacion(), emisorIncompleto: false });
    expect(fixture.nativeElement.querySelector('.cotizacion-detalle__aviso-fiscal')).toBeNull();
  });

  it('tras enviar por correo con emisorIncompleto avisa que complete los datos fiscales', async () => {
    iniciar();
    correoDialog.pedir.mockResolvedValue('nuevo@acme.com');
    await (fixture.componentInstance as unknown as { enviarCorreo(): Promise<void> }).enviarCorreo();
    http
      .expectOne('/api/v1/cotizaciones/cot-1/enviar-correo')
      .flush({ ...cotizacion(), estado: 'enviada', emisorIncompleto: true });
    fixture.detectChanges();
    expect(toast.info).toHaveBeenCalledWith(
      expect.stringContaining('datos fiscales de tu empresa'),
    );
    // El aviso inline tambien aparece al refrescar el DTO con el flag activo.
    expect(fixture.nativeElement.querySelector('.cotizacion-detalle__aviso-fiscal')).not.toBeNull();
  });

  it('el nombre del cliente es un enlace a la ficha 360 cuando hay permiso cliente:leer', () => {
    iniciar();
    const enlace = fixture.nativeElement.querySelector(
      'a[href="/empresa/comercial/clientes/cli-1"]',
    ) as HTMLAnchorElement | null;
    expect(enlace).not.toBeNull();
    expect(enlace?.textContent?.trim()).toBe('Acme S.A.');
  });

  it('el nombre del cliente es texto plano sin el permiso cliente:leer', () => {
    auth.denegar('cliente', 'leer');
    iniciar();
    expect(
      fixture.nativeElement.querySelector('a[href="/empresa/comercial/clientes/cli-1"]'),
    ).toBeNull();
    // El nombre sigue visible como texto.
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Acme S.A.');
  });

  it('muestra el enlace a la oportunidad de origen con el queryParam cuando hay oportunidadId', () => {
    iniciar({ ...cotizacion(), oportunidadId: 'opo-9' });
    const enlace = fixture.nativeElement.querySelector(
      'a[href="/empresa/comercial/oportunidades?oportunidad=opo-9"]',
    ) as HTMLAnchorElement | null;
    expect(enlace).not.toBeNull();
    expect(enlace?.textContent).toContain('Generada desde una oportunidad');
  });

  it('no muestra la fila de oportunidad de origen cuando oportunidadId es null', () => {
    iniciar({ ...cotizacion(), oportunidadId: null });
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain(
      'Generada desde una oportunidad',
    );
  });

  it('no ofrece enlace a la oportunidad sin el permiso oportunidad:leer (texto plano)', () => {
    auth.denegar('oportunidad', 'leer');
    iniciar({ ...cotizacion(), oportunidadId: 'opo-9' });
    expect(
      fixture.nativeElement.querySelector(
        'a[href="/empresa/comercial/oportunidades?oportunidad=opo-9"]',
      ),
    ).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Generada desde una oportunidad',
    );
  });

  it('muestra el nombre del canal de venta (no el UUID) cuando la cotizacion tiene canal', () => {
    iniciar({ ...cotizacion(), canalVentaId: 'canal-1' });
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Canal de venta: Directo');
    expect(texto).not.toContain('canal-1');
  });

  it('muestra "Sin canal" cuando la cotizacion no tiene canal asignado', () => {
    iniciar({ ...cotizacion(), canalVentaId: null });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Canal de venta: Sin canal');
  });

  it('asignar canal invoca asignarCanalVenta (PUT) con cotizacion:actualizar', () => {
    iniciar({ ...cotizacion(), canalVentaId: null });
    (fixture.componentInstance as unknown as { abrirAsignarCanal(): void }).abrirAsignarCanal();
    fixture.detectChanges();
    (
      fixture.componentInstance as unknown as { formCanal: { setValue(v: { canalId: string }): void } }
    ).formCanal.setValue({ canalId: 'canal-1' });
    (fixture.componentInstance as unknown as { guardarCanal(): void }).guardarCanal();

    const req = http.expectOne('/api/v1/cotizaciones/cot-1/canal-venta');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ canalVentaId: 'canal-1' });
    req.flush({ ...cotizacion(), canalVentaId: 'canal-1' });
    fixture.detectChanges();

    expect(toast.exito).toHaveBeenCalledWith('Canal de venta asignado.');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Canal de venta: Directo');
  });

  it('no ofrece asignar canal sin el permiso cotizacion:actualizar', () => {
    auth.denegar('cotizacion', 'actualizar');
    iniciar({ ...cotizacion(), canalVentaId: null });
    const boton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => (b.textContent ?? '').includes('Asignar canal'));
    expect(boton).toBeUndefined();
  });

  it('no renderiza ningun UUID tecnico en la vista de detalle', () => {
    iniciar({ ...cotizacion(), oportunidadId: 'opo-9', canalVentaId: 'canal-1' });
    // Los ids tecnicos (cliente, oportunidad, canal) nunca se muestran como texto.
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).not.toContain('cli-1');
    expect(texto).not.toContain('opo-9');
    expect(texto).not.toContain('canal-1');
  });

  // ---------------------------------------------------------------------------
  // Guarda de vigencia (Req 6.11): una cotizacion vencida no se envia ni aprueba.
  // Se usan fechas relativas a HOY para ser robustas ante la fecha de ejecucion.
  // ---------------------------------------------------------------------------

  /** ISO date (YYYY-MM-DD) desplazada `dias` respecto a hoy. */
  function fechaISO(dias: number): string {
    const d = new Date();
    d.setDate(d.getDate() + dias);
    return d.toISOString().slice(0, 10);
  }

  it('muestra el aviso de vencida y NO envia por correo una cotizacion vencida (Req 6.11)', async () => {
    iniciar({ ...cotizacion(), validoHasta: fechaISO(-1) });
    // Aviso visible.
    const aviso = fixture.nativeElement.querySelector(
      '.cotizacion-detalle__aviso-vencida',
    ) as HTMLElement | null;
    expect(aviso).not.toBeNull();
    expect(aviso?.getAttribute('role')).toBe('alert');

    // Intentar enviar: guarda preventiva -> no abre dialogo ni hace peticion.
    await (fixture.componentInstance as unknown as { enviarCorreo(): Promise<void> }).enviarCorreo();
    expect(correoDialog.pedir).not.toHaveBeenCalled();
    http.expectNone('/api/v1/cotizaciones/cot-1/enviar-correo');
    expect(toast.error).toHaveBeenCalled();
  });

  it('NO cambia a "aprobada" una cotizacion vencida y avisa (Req 6.11)', async () => {
    iniciar({ ...cotizacion(), estado: 'enviada', validoHasta: fechaISO(-1) });
    await (
      fixture.componentInstance as unknown as { cambiarEstado(e: string): Promise<void> }
    ).cambiarEstado('aprobada');
    http.expectNone('/api/v1/cotizaciones/cot-1/estado');
    expect(toast.error).toHaveBeenCalled();
  });

  it('permite RECHAZAR una cotizacion vencida (la vigencia no restringe rechazar, Req 6.11)', async () => {
    iniciar({ ...cotizacion(), estado: 'enviada', validoHasta: fechaISO(-1) });
    // El ConfirmDialogService real resuelve; se intercepta para no depender de su UI.
    (
      fixture.componentInstance as unknown as { confirm: { confirmar: () => Promise<boolean> } }
    ).confirm.confirmar = () => Promise.resolve(true);

    await (
      fixture.componentInstance as unknown as { cambiarEstado(e: string): Promise<void> }
    ).cambiarEstado('rechazada');

    const req = http.expectOne('/api/v1/cotizaciones/cot-1/estado');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ estado: 'rechazada' });
    req.flush({ ...cotizacion(), estado: 'rechazada', validoHasta: fechaISO(-1) });
    expect(toast.exito).toHaveBeenCalledWith('Estado actualizado.');
  });

  it('una cotizacion VIGENTE se puede enviar (no muestra aviso de vencida)', async () => {
    iniciar({ ...cotizacion(), validoHasta: fechaISO(30) });
    expect(fixture.nativeElement.querySelector('.cotizacion-detalle__aviso-vencida')).toBeNull();

    correoDialog.pedir.mockResolvedValue('nuevo@acme.com');
    await (fixture.componentInstance as unknown as { enviarCorreo(): Promise<void> }).enviarCorreo();
    const req = http.expectOne('/api/v1/cotizaciones/cot-1/enviar-correo');
    expect(req.request.method).toBe('POST');
    req.flush({ ...cotizacion(), estado: 'enviada', validoHasta: fechaISO(30) });
    expect(toast.exito).toHaveBeenCalled();
  });

  // ---------------------------------------------------------------------------
  // Gating del Vertical_Anuncios para Pruebas de Diseno (Req 9.2, 9.3, 15):
  // los endpoints exigen modulo `operacion` + giro `anuncios-luminosos` + permiso.
  // Si falta el giro o el modulo, la vista NO debe disparar la XHR (evita el 403
  // y el toast) ni ofrecer el boton "Generar prueba".
  // ---------------------------------------------------------------------------

  /** Monta el detalle SIN esperar la XHR de pruebas (no debe dispararse). */
  function iniciarSinPruebas(dto: Cotizacion = cotizacion()): void {
    fixture = TestBed.createComponent(ComercialCotizacionDetalle);
    fixture.componentRef.setInput('id', 'cot-1');
    fixture.detectChanges();
    http.expectOne('/api/v1/cotizaciones/cot-1').flush(dto);
    resolverCanales();
    fixture.detectChanges();
  }

  it('con giro distinto a anuncios-luminosos NO consulta pruebas de diseno ni ofrece generar (Req 9.3)', () => {
    auth.conGiro('manufactura');
    iniciarSinPruebas();
    // No debe existir ninguna peticion al endpoint de pruebas (evita el 403).
    http.expectNone((r) => r.url === '/api/v1/cotizaciones/cot-1/pruebas-diseno');
    const boton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => (b.textContent ?? '').includes('Generar prueba'));
    expect(boton).toBeUndefined();
  });

  it('sin el modulo operacion NO consulta pruebas de diseno ni ofrece generar (Req 9.2)', () => {
    auth.sinModulo('operacion');
    iniciarSinPruebas();
    http.expectNone((r) => r.url === '/api/v1/cotizaciones/cot-1/pruebas-diseno');
    const boton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => (b.textContent ?? '').includes('Generar prueba'));
    expect(boton).toBeUndefined();
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    iniciar();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
