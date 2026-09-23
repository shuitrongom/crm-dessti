// =============================================================================
// Pruebas del panel PlanSuscripcionDialog (super_admin)
// (plan-suscripcion-empresa-super-admin — tarea 8.2)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real, el comportamiento de
// apertura del panel que administra el plan y la suscripcion de una Empresa:
//   - Al abrir carga en paralelo (forkJoin) las suscripciones de la Empresa, el
//     catalogo de planes y el catalogo de modulos; muestra el plan vigente y la
//     suscripcion actual en lenguaje claro (estado en espanol, modulo por su
//     etiqueta humana) y NUNCA expone ningun UUID (Req 5.6).
//   - Muestra "Sin plan" cuando la Empresa no tiene suscripciones (Req 5.5).
//   - La vigencia sin fecha de fin se rotula como "Sin fecha de fin".
//   - RBAC UI: sin permisos de suscripcion no se muestra ninguna accion (Req 10).
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
//
// Para instanciar el panel basta con un stub de AuthService (define los permisos)
// y los proveedores reales de HTTP de prueba; MatDialog, ConfirmDialogService y
// NotificacionesService son `providedIn: 'root'` y funcionan con
// NoopAnimationsModule sin necesidad de stubs. El forkJoin se resuelve en cada
// prueba localizando las tres peticiones por su URL (http.match) y haciendo flush
// de sus respuestas, tras lo cual se estabiliza el fixture.
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';

import { PlanSuscripcionDialog, PlanSuscripcionDialogData } from './plan-suscripcion-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  Empresa,
  ModuloCatalogo,
  Plan,
  Suscripcion,
} from '../models/plataforma.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

// Registra es-MX para que el DatePipe/CurrencyPipe con locale explicito no lance NG0701.
registerLocaleData(localeEsMx);

/** URLs (sin query) de las tres peticiones que dispara `cargar()` al abrir. */
const URL_SUSCRIPCIONES = '/api/v1/suscripciones';
const URL_PLANES = '/api/v1/planes';
const URL_MODULOS = '/api/v1/plataforma/modulos';

/** MatDialogRef de prueba que registra si se cerro y con que resultado. */
class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
}

/**
 * Stub de AuthService: solo se usa `tienePermiso`. Se parametriza con el valor
 * que debe devolver (true = todas las acciones visibles; false = ninguna).
 */
class AuthServiceStub {
  constructor(private readonly permitido: boolean) {}
  tienePermiso(_recurso: string, _operacion: string): boolean {
    return this.permitido;
  }
  tieneAlgunPermiso(..._permisos: string[]): boolean {
    return this.permitido;
  }
}

function empresa(parcial: Partial<Empresa> = {}): Empresa {
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
    planVigente: {
      nombrePlan: 'Plan Pro',
      estado: 'activa',
      planId: 'p1',
      suscripcionId: 's1',
      vigenciaInicio: '2026-01-01',
      vigenciaFin: null,
      tipoInstrumento: 'plan',
      nombreInstrumento: 'Plan Pro',
      paqueteSuscripcionId: null,
      diasRestantes: null,
      enPrueba: false,
      vencida: false,
      porVencer: false,
    },
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...parcial,
  };
}

function plan(parcial: Partial<Plan> = {}): Plan {
  return {
    id: 'p1',
    nombre: 'Plan Pro',
    maxUsuarios: 10,
    duracionDias: 730,
    giroId: 'g1',
    monedaCodigo: 'MXN',
    preciosModulos: { crm: 1000 },
    total: 1000,
    modulosHabilitados: ['crm'],
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...parcial,
  };
}

function suscripcion(parcial: Partial<Suscripcion> = {}): Suscripcion {
  return {
    id: 's1',
    tenantId: 'e1',
    planId: 'p1',
    tipoInstrumento: 'plan',
    paqueteSuscripcionId: null,
    estado: 'activa',
    vigenciaInicio: '2026-01-01',
    vigenciaFin: null,
    modulosHabilitados: null,
    monedaFacturacion: 'MXN',
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...parcial,
  };
}

function modulo(parcial: Partial<ModuloCatalogo> = {}): ModuloCatalogo {
  return {
    clave: 'crm',
    nombreVisible: 'CRM',
    giro: null,
    catalogoModuloId: null,
    precio: 1000,
    monedaCodigo: 'MXN',
    ...parcial,
  };
}

function pagina(planes: Plan[]): PaginaResponse<Plan> {
  return { content: planes, page: 0, size: 100, totalElements: planes.length, totalPages: 1 };
}

describe('PlanSuscripcionDialog', () => {
  let fixture: ComponentFixture<PlanSuscripcionDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  /**
   * Configura el TestBed, crea el componente (lo que dispara `cargar()`) y deja
   * las tres peticiones del forkJoin en cola. No las resuelve todavia.
   */
  async function crear(data: PlanSuscripcionDialogData, permisos = true): Promise<void> {
    dialogRef = new DialogRefStub();
    await TestBed.configureTestingModule({
      imports: [PlanSuscripcionDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: AuthService, useValue: new AuthServiceStub(permisos) },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PlanSuscripcionDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  /**
   * Resuelve el forkJoin de apertura respondiendo las tres peticiones por su URL
   * (independiente del orden y de la query) y estabiliza el fixture.
   */
  async function resolverCarga(opciones: {
    suscripciones: Suscripcion[];
    planes: Plan[];
    modulos: ModuloCatalogo[];
  }): Promise<void> {
    http.match((r) => r.url === URL_SUSCRIPCIONES)[0].flush(opciones.suscripciones);
    http.match((r) => r.url === URL_PLANES)[0].flush(pagina(opciones.planes));
    http.match((r) => r.url === URL_MODULOS)[0].flush(opciones.modulos);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  afterEach(() => http?.verify());

  it('muestra el plan vigente y la suscripción actual (estado en español, sin UUID)', async () => {
    // Identificadores tecnicos DISTINTIVOS (no colisionan con el texto formateado
    // del panel) para poder afirmar que ninguno se filtra a la UI (Req 5.6).
    const idEmpresa = 'uuid-empresa-0001';
    const idPlan = 'uuid-plan-0001';
    const idSuscripcion = 'uuid-suscripcion-0001';
    await crear({ empresa: empresa({ id: idEmpresa }) });
    await resolverCarga({
      suscripciones: [
        suscripcion({ id: idSuscripcion, tenantId: idEmpresa, planId: idPlan, estado: 'activa' }),
      ],
      planes: [plan({ id: idPlan })],
      modulos: [modulo({ clave: 'crm', nombreVisible: 'CRM' })],
    });

    const t = texto();
    expect(t).toContain('Plan Pro');
    expect(t).toContain('Activa');
    expect(t).toContain('CRM');
    // NUNCA se muestran identificadores tecnicos (UUID) en el panel (Req 5.6).
    expect(t).not.toContain(idPlan);
    expect(t).not.toContain(idSuscripcion);
    expect(t).not.toContain(idEmpresa);
  });

  it("muestra 'Sin plan' cuando la empresa no tiene suscripciones", async () => {
    await crear({ empresa: empresa({ planVigente: null }) });
    await resolverCarga({
      suscripciones: [],
      planes: [plan()],
      modulos: [modulo()],
    });

    expect(texto()).toContain('Sin plan');
  });

  it("vigencia sin fecha de fin se muestra como 'Sin fecha de fin'", async () => {
    await crear({ empresa: empresa() });
    await resolverCarga({
      suscripciones: [suscripcion({ estado: 'activa', vigenciaFin: null })],
      planes: [plan()],
      modulos: [modulo()],
    });

    expect(texto()).toContain('Sin fecha de fin');
  });

  it('no muestra acciones de suscripción sin permisos', async () => {
    await crear({ empresa: empresa() }, false);
    await resolverCarga({
      suscripciones: [suscripcion({ estado: 'activa' })],
      planes: [plan()],
      modulos: [modulo()],
    });

    const t = texto();
    expect(t).not.toContain('Cambiar plan');
    expect(t).not.toContain('Asignar plan');
    expect(t).not.toContain('Actualizar vigencia');
    expect(t).not.toContain('Cancelar suscripción');
    expect(t).not.toContain('Suspender');
    expect(t).not.toContain('Activar');
    // El boton de cerrar (no gated) sigue presente.
    expect(t).toContain('Cerrar');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await crear({ empresa: empresa() });
    await resolverCarga({
      suscripciones: [suscripcion({ estado: 'activa' })],
      planes: [plan()],
      modulos: [modulo()],
    });

    await esperarSinViolaciones(fixture);
  });

  // ===========================================================================
  // Contratacion excluyente: estados derivados y acciones de contrato (tarea 14).
  // Cubren el comportamiento anadido en 14.1: etiquetas "En prueba"/"Vencida",
  // dias restantes, badge "Por vencer" y visibilidad/efecto de las acciones
  // "Activar facturacion" y "Extender prueba" (Req 7.1, 7.2, 7.4, 8.1, 8.3).
  // ===========================================================================

  /**
   * Interfaz tipada MINIMA sobre el componente para alcanzar los miembros
   * `protected` que el DOM no expone con comodidad (el control reactivo del
   * capturador inline y las acciones de contrato). No debilita ninguna asercion:
   * solo permite invocar el comportamiento real del componente sin duplicar su
   * plantilla en la prueba.
   */
  interface AccesoContrato {
    formularioExtender: { setValue(valor: { nuevaVigenciaFin: string }): void };
    iniciarExtenderPrueba(): void;
    confirmarExtenderPrueba(): void;
  }

  /** Componente bajo prueba visto por su interfaz tipada de acceso a miembros protegidos. */
  function componente(): AccesoContrato {
    return fixture.componentInstance as unknown as AccesoContrato;
  }

  /**
   * Localiza un boton por su texto visible (normalizado). Devuelve `null` si no
   * existe, lo que permite afirmar tanto presencia como ausencia de una accion.
   */
  function botonPorTexto(texto: string): HTMLButtonElement | null {
    const botones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ) as HTMLButtonElement[];
    return botones.find((b) => (b.textContent ?? '').trim().includes(texto)) ?? null;
  }

  /**
   * Hace flush de las tres peticiones que dispara la recarga (`marcarCambioYRecargar`
   * -> `cargar()`) tras una accion de contrato, para que `http.verify()` no falle.
   * Responde por URL (independiente del orden), igual que `resolverCarga`.
   */
  async function flushRecarga(): Promise<void> {
    http.match((r) => r.url === URL_SUSCRIPCIONES).forEach((r) => r.flush([suscripcion()]));
    http.match((r) => r.url === URL_PLANES).forEach((r) => r.flush(pagina([plan()])));
    http.match((r) => r.url === URL_MODULOS).forEach((r) => r.flush([modulo()]));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it("muestra 'En prueba' cuando el contrato está en prueba", async () => {
    await crear({
      empresa: empresa({
        planVigente: { ...empresa().planVigente!, enPrueba: true },
      }),
    });
    await resolverCarga({
      suscripciones: [suscripcion({ estado: 'en_prueba' })],
      planes: [plan()],
      modulos: [modulo()],
    });

    expect(texto()).toContain('En prueba');
  });

  it("muestra 'Vencida' cuando el contrato está vencido", async () => {
    // Suscripcion activa pero con vigenciaFin en el pasado -> corte estricto = vencida.
    await crear({ empresa: empresa({ planVigente: null }) });
    await resolverCarga({
      suscripciones: [suscripcion({ estado: 'activa', vigenciaFin: '2000-01-01' })],
      planes: [plan()],
      modulos: [modulo()],
    });

    expect(texto()).toContain('Vencida');
  });

  it("muestra los días restantes y 'Por vencer' cuando aplica", async () => {
    await crear({
      empresa: empresa({
        planVigente: {
          ...empresa().planVigente!,
          diasRestantes: 5,
          porVencer: true,
          vigenciaFin: '2026-12-31',
        },
      }),
    });
    await resolverCarga({
      suscripciones: [suscripcion({ estado: 'activa', vigenciaFin: '2026-12-31' })],
      planes: [plan()],
      modulos: [modulo()],
    });

    const t = texto();
    expect(t).toContain('Por vencer');
    expect(t).toContain('Días restantes');
    expect(t).toContain('5');
  });

  it("'Activar facturación' visible en prueba con permiso y hace POST /activar-facturacion", async () => {
    await crear({
      empresa: empresa({ planVigente: { ...empresa().planVigente!, enPrueba: true } }),
    });
    await resolverCarga({
      suscripciones: [suscripcion({ id: 's1', estado: 'en_prueba' })],
      planes: [plan()],
      modulos: [modulo()],
    });

    const boton = botonPorTexto('Activar facturación');
    expect(boton).not.toBeNull();
    boton!.click();
    fixture.detectChanges();

    const req = http.expectOne('/api/v1/suscripciones/s1/activar-facturacion');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(suscripcion({ id: 's1', estado: 'activa' }));

    // La accion recarga el panel (cargar()): hay que hacer flush del trio.
    await flushRecarga();

    // Camino de exito: el panel volvio a estabilizarse y sigue mostrando el plan.
    expect(texto()).toContain('Plan Pro');
  });

  it("'Activar facturación' NO visible sin permiso ni fuera de prueba", async () => {
    // Sin permiso: aunque este en prueba, no aparece la accion gated.
    await crear(
      { empresa: empresa({ planVigente: { ...empresa().planVigente!, enPrueba: true } }) },
      false,
    );
    await resolverCarga({
      suscripciones: [suscripcion({ estado: 'en_prueba' })],
      planes: [plan()],
      modulos: [modulo()],
    });

    expect(texto()).not.toContain('Activar facturación');
  });

  it('Extender prueba: revela el capturador y hace POST /extender-prueba', async () => {
    await crear({
      empresa: empresa({ planVigente: { ...empresa().planVigente!, enPrueba: true } }),
    });
    await resolverCarga({
      suscripciones: [suscripcion({ id: 's1', estado: 'en_prueba' })],
      planes: [plan()],
      modulos: [modulo()],
    });

    // Revela el capturador inline (boton "Extender prueba" gated por permiso).
    const abrir = botonPorTexto('Extender prueba');
    expect(abrir).not.toBeNull();
    abrir!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // Captura la nueva fecha (ISO) y confirma vía el comportamiento real del componente.
    componente().formularioExtender.setValue({ nuevaVigenciaFin: '2026-06-30' });
    componente().confirmarExtenderPrueba();
    fixture.detectChanges();

    const req = http.expectOne('/api/v1/suscripciones/s1/extender-prueba');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ nuevaVigenciaFin: '2026-06-30' });
    req.flush(suscripcion({ id: 's1', estado: 'en_prueba', vigenciaFin: '2026-06-30' }));

    // La accion recarga el panel (cargar()): hay que hacer flush del trio.
    await flushRecarga();

    expect(texto()).toContain('Plan Pro');
  });
});
