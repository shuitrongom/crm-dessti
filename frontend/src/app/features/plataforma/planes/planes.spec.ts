// =============================================================================
// Pruebas de la vista PlataformaPlanes (Req 25, plataforma-multigiro)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Cada tarjeta muestra el TOTAL del Plan tal cual lo devuelve el backend
//     (plan.total), formateado con la moneda del Plan.
//   - La tarjeta muestra el chip del Giro (nombre visible resoluble del catalogo
//     de giros).
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';

import { PlataformaPlanes } from './planes';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { Giro, ModuloCatalogo, PaqueteSuscripcion, Plan } from '../models/plataforma.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

// Registra es-MX para que el CurrencyPipe con locale explicito no lance NG0701.
registerLocaleData(localeEsMx);

/** AuthService de prueba: super_admin con permisos de plan y de suscripcion. */
class AuthServiceStub {
  tienePermiso(recurso: string, _operacion: string): boolean {
    return recurso === 'plan' || recurso === 'suscripcion';
  }
}

/** ConfirmDialogService de prueba: resuelve segun `respuesta` sin abrir modal. */
class ConfirmDialogServiceStub {
  respuesta = true;
  async confirmar(): Promise<boolean> {
    return this.respuesta;
  }
}

/** NotificacionesService de prueba: registra los mensajes emitidos. */
class NotificacionesServiceStub {
  exitos: string[] = [];
  errores: string[] = [];
  exito(mensaje: string): void {
    this.exitos.push(mensaje);
  }
  error(mensaje: string): void {
    this.errores.push(mensaje);
  }
  info(): void {}
}

function modulo(parcial: Partial<ModuloCatalogo> & { clave: string }): ModuloCatalogo {
  return {
    nombreVisible: parcial.clave,
    giro: null,
    catalogoModuloId: 'cat-' + parcial.clave,
    precio: null,
    monedaCodigo: 'MXN',
    ...parcial,
  };
}

function plan(parcial: Partial<Plan> & { id: string }): Plan {
  return {
    nombre: 'Plan',
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

const catalogo: ModuloCatalogo[] = [
  modulo({ clave: 'comercial', nombreVisible: 'Comercial (CRM)' }),
  modulo({ clave: 'compras', nombreVisible: 'Compras' }),
];

const giros: Giro[] = [
  {
    id: 'g-anuncios',
    clave: 'anuncios-luminosos',
    nombreVisible: 'Anuncios luminosos',
    descripcion: null,
    activo: true,
    version: 0,
    tieneReglasNegocio: true,
    modulosEspecificos: 3,
  },
];

describe('PlataformaPlanes', () => {
  let fixture: ComponentFixture<PlataformaPlanes>;
  let http: HttpTestingController;
  let confirm: ConfirmDialogServiceStub;
  let toast: NotificacionesServiceStub;

  beforeEach(async () => {
    confirm = new ConfirmDialogServiceStub();
    toast = new NotificacionesServiceStub();
    await TestBed.configureTestingModule({
      imports: [PlataformaPlanes, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: NotificacionesService, useValue: toast },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PlataformaPlanes);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /**
   * Resuelve la carga: pagina de planes + pagina de paquetes (pestana
   * Suscripciones) + catalogo de modulos + giros. La vista ahora carga tambien
   * los Paquetes de Suscripcion al iniciar, por lo que se responde esa peticion
   * (vacia salvo que se indique) para no dejar solicitudes pendientes.
   */
  function resolverCarga(planes: Plan[], paquetes: PaqueteSuscripcion[] = []): void {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === '/api/v1/planes')
      .flush({ content: planes, page: 0, size: 20, totalElements: planes.length, totalPages: 1 });
    http
      .expectOne((r) => r.url === '/api/v1/paquetes-suscripcion')
      .flush({
        content: paquetes,
        page: 0,
        size: 20,
        totalElements: paquetes.length,
        totalPages: paquetes.length === 0 ? 0 : 1,
      });
    http.expectOne('/api/v1/plataforma/modulos').flush(catalogo);
    http
      .expectOne((r) => r.url === '/api/v1/plataforma/giros')
      .flush({ content: giros, page: 0, size: 100, totalElements: giros.length, totalPages: 1 });
    fixture.detectChanges();
  }

  it('muestra el total del Plan tal cual lo devuelve el backend (plan.total)', () => {
    resolverCarga([
      plan({ id: 'p1', nombre: 'Pro', total: 2000, monedaCodigo: 'MXN', giroId: 'g-anuncios' }),
    ]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    // 2000 con formato es-MX incluye el separador de miles.
    expect(texto).toContain('2,000');
  });

  it('muestra el chip del Giro (nombre visible del catalogo de giros)', () => {
    resolverCarga([plan({ id: 'p1', nombre: 'Pro', total: 1000, giroId: 'g-anuncios' })]);
    const chip = (fixture.nativeElement as HTMLElement).querySelector('.planes__giro-chip');
    expect(chip?.textContent).toContain('Anuncios luminosos');
  });

  it('no muestra chip de giro cuando el Plan no tiene giro', () => {
    resolverCarga([plan({ id: 'p2', nombre: 'Sin giro', giroId: null })]);
    const chip = (fixture.nativeElement as HTMLElement).querySelector('.planes__giro-chip');
    expect(chip).toBeNull();
  });

  /** Boton de eliminacion de la (primera) tarjeta renderizada. */
  function botonEliminar(): HTMLButtonElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '.planes__accion--eliminar',
    );
  }

  it('muestra el boton Eliminar en la tarjeta cuando se tiene el permiso', () => {
    resolverCarga([plan({ id: 'p1', nombre: 'Pro', total: 1000, giroId: 'g-anuncios' })]);
    const boton = botonEliminar();
    expect(boton).not.toBeNull();
    expect(boton?.getAttribute('aria-label')).toBe('Eliminar plan Pro');
  });

  it('elimina el plan y recarga la lista al confirmar', async () => {
    confirm.respuesta = true;
    resolverCarga([plan({ id: 'p9', nombre: 'Pro', total: 1000, giroId: 'g-anuncios' })]);
    botonEliminar()!.click();
    await fixture.whenStable();

    const del = http.expectOne('/api/v1/planes/p9');
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });

    // Tras el exito se recarga la lista de planes.
    http
      .expectOne((r) => r.url === '/api/v1/planes')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(toast.exitos).toContain('Plan "Pro" eliminado.');
  });

  it('muestra un toast de error cuando la eliminacion falla (422 del backend)', async () => {
    confirm.respuesta = true;
    resolverCarga([plan({ id: 'p9', nombre: 'Pro', total: 1000, giroId: 'g-anuncios' })]);
    botonEliminar()!.click();
    await fixture.whenStable();

    const del = http.expectOne('/api/v1/planes/p9');
    del.flush(
      { detail: 'Primero cambia el plan de esas empresas.' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    await fixture.whenStable();
    expect(toast.errores.length).toBe(1);
  });

  // ---------------------------------------------------------------------------
  // Dos secciones: pestanas "Planes" y "Suscripciones" (Req 10.1, 10.6)
  // ---------------------------------------------------------------------------

  /** Etiquetas de las pestanas del mat-tab-group, en orden de aparicion. */
  function etiquetasDePestanas(): string[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.mat-mdc-tab .mdc-tab__text-label'),
    ).map((e) => e.textContent?.trim() ?? '');
  }

  it('presenta DOS pestanas: "Planes" y "Suscripciones"', () => {
    resolverCarga([plan({ id: 'p1', nombre: 'Pro', total: 1000, giroId: 'g-anuncios' })]);
    const etiquetas = etiquetasDePestanas();
    expect(etiquetas.length).toBe(2);
    expect(etiquetas).toContain('Planes');
    expect(etiquetas).toContain('Suscripciones');
    // Las pestanas se exponen con rol accesible "tab".
    const roles = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('[role="tab"]'),
    );
    expect(roles.length).toBe(2);
  });

  it('la pestana activa (Planes) muestra la rejilla de planes con su tarjeta', () => {
    resolverCarga([plan({ id: 'p1', nombre: 'Pro', total: 1000, giroId: 'g-anuncios' })]);
    // La primera pestana esta activa por defecto: se ve la tarjeta del plan.
    const titulos = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.planes__card-titulo'),
    ).map((h) => h.textContent?.trim());
    expect(titulos).toContain('Pro');
    // Y el disparador "Nuevo plan" de la seccion de Planes.
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Nuevo plan');
  });

  it('la seccion Suscripciones existe con su disparador "Nueva suscripcion" y su rejilla', async () => {
    resolverCarga(
      [plan({ id: 'p1', nombre: 'Pro', total: 1000, giroId: 'g-anuncios' })],
      [paquete({ id: 'q1', nombre: 'Mensual', total: 900, giroId: 'g-anuncios' })],
    );

    // Ambas etiquetas de pestana estan presentes en el DOM (robusto y one-shot).
    expect(etiquetasDePestanas()).toEqual(expect.arrayContaining(['Planes', 'Suscripciones']));

    // Activa la segunda pestana (Suscripciones) haciendo clic en su etiqueta.
    const etiquetaSuscripciones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.mat-mdc-tab'),
    ).find((t) => t.textContent?.trim() === 'Suscripciones');
    expect(etiquetaSuscripciones).toBeTruthy();
    etiquetaSuscripciones!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    // El disparador de alta de Suscripcion y la tarjeta del paquete son visibles.
    expect(texto).toContain('Nueva suscripcion');
    const titulos = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.planes__card-titulo'),
    ).map((h) => h.textContent?.trim());
    expect(titulos).toContain('Mensual');
  });

  it(
    'no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)',
    async () => {
      resolverCarga([
        plan({ id: 'p1', nombre: 'Pro', total: 1500, giroId: 'g-anuncios', modulosHabilitados: ['comercial'] }),
      ]);
      await fixture.whenStable();
      await esperarSinViolaciones(fixture);
    },
    // axe-core es intensivo en CPU; bajo carga en Windows puede superar el limite
    // por defecto de 5 s. Un timeout explicito hace determinista esta prueba.
    30000,
  );
});
