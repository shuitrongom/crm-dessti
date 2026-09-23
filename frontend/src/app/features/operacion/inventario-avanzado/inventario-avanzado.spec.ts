// =============================================================================
// Pruebas de la vista Inventario Avanzado enterprise (spec inventario-avanzado-enterprise)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Existencias se muestran por NOMBRE (nunca el UUID) resolviendo los mapas.
//   - Registrar entrada / salida / transferencia llaman al endpoint correcto.
//   - Una salida 422 muestra el mensaje de negocio sin romper la vista.
//   - Kardex se consulta por seleccion (sin teclear UUID) y "Ver Kardex" desde
//     una existencia preselecciona almacen + material.
//   - Configuracion hace el PUT con los valores del formulario.
//   - Lotes: crear/listar; resalte de caducidad.
//   - Gating: secciones/acciones ocultas sin el permiso.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { vi } from 'vitest';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';

import { OperacionInventarioAvanzado } from './inventario-avanzado';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  Almacen,
  ConfigInventarioMaterial,
  ExistenciaAlmacen,
  Lote,
  Material,
  MovimientoAlmacen,
} from '../models/operacion.models';
import { esperarSinViolaciones } from '../../../../testing/axe';
import { provideFechaIsoDatepicker } from '../../../shared/date/provide-fecha-iso';

registerLocaleData(localeEsMx);

/** AuthService de prueba con permisos configurables. */
class AuthServiceStub {
  permisos = new Set<string>([
    'almacen:crear',
    'almacen:actualizar',
    'almacen:listar',
    'almacen:leer',
    'material:leer',
    'material:actualizar',
    'movimiento_inventario:crear',
    'kardex:leer',
    'lote:crear',
    'lote:listar',
  ]);
  tienePermiso(recurso: string, operacion: string): boolean {
    return this.permisos.has(`${recurso}:${operacion}`);
  }
}

/** Espia de notificaciones. */
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

const ALMACEN_UUID = 'a0000000-0000-0000-0000-000000000001';
const ALMACEN2_UUID = 'a0000000-0000-0000-0000-000000000002';
const MATERIAL_UUID = 'b0000000-0000-0000-0000-000000000001';

function almacenFalso(id = ALMACEN_UUID, nombre = 'Bodega Central'): Almacen {
  return {
    id,
    nombre,
    tipo: 'bodega',
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

function materialFalso(overrides: Partial<Material> = {}): Material {
  return {
    id: MATERIAL_UUID,
    nombre: 'Lamina acrilica',
    unidadMedida: 'm2',
    stockMinimo: 10,
    existencias: 5,
    stockBajo: true,
    activo: true,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function existenciaFalsa(): ExistenciaAlmacen {
  return {
    id: 'e0000000-0000-0000-0000-000000000001',
    almacenId: ALMACEN_UUID,
    materialId: MATERIAL_UUID,
    cantidad: 3,
    costoPromedio: 120.5,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

interface ViewTest {
  formEntrada: { setValue(v: Record<string, unknown>): void };
  formSalida: { setValue(v: Record<string, unknown>): void };
  formTransferencia: { setValue(v: Record<string, unknown>): void };
  formKardex: { getRawValue(): { almacenId: string; materialId: string } };
  formConfig: { setValue(v: Record<string, unknown>): void };
  formLoteMaterial: { setValue(v: Record<string, unknown>): void };
  formLote: { setValue(v: Record<string, unknown>): void };
  registrarEntrada(): void;
  registrarSalida(): void;
  registrarTransferencia(): void;
  verKardexDeExistencia(e: ExistenciaAlmacen): void;
  guardarConfig(): void;
  cargarLotes(): void;
  crearLote(): void;
  errorSalida(): string | undefined;
  configGuardada(): ConfigInventarioMaterial | null;
  lotes(): Lote[];
  estadoLote(l: Lote): string;
  etiquetaEstadoLote(l: Lote): string;
}

describe('OperacionInventarioAvanzado', () => {
  let fixture: ComponentFixture<OperacionInventarioAvanzado>;
  let http: HttpTestingController;
  let toast: ToastSpy;
  let auth: AuthServiceStub;

  const URL_ALMACENES = '/api/v1/inventario-avanzado/almacenes';
  const URL_MATERIALES = '/api/v1/materiales';
  const URL_EXISTENCIAS = '/api/v1/inventario-avanzado/existencias';

  function pagina<T>(content: T[]) {
    return { content, page: 0, size: 200, totalElements: content.length, totalPages: 1 };
  }

  async function crear(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [OperacionInventarioAvanzado, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        ...provideFechaIsoDatepicker(),
        { provide: NotificacionesService, useValue: toast },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OperacionInventarioAvanzado);
  }

  /**
   * Resuelve las peticiones de arranque: Almacenes (CRUD), catalogos de nombres
   * (Almacenes + Materiales) y las Existencias iniciales.
   */
  function resolverArranque(opts?: {
    almacenes?: Almacen[];
    materiales?: Material[];
    existencias?: ExistenciaAlmacen[];
  }): void {
    const almacenes = opts?.almacenes ?? [almacenFalso(), almacenFalso(ALMACEN2_UUID, 'Sucursal Norte')];
    const materiales = opts?.materiales ?? [materialFalso()];
    const existencias = opts?.existencias ?? [existenciaFalsa()];

    fixture.detectChanges();

    // Drena todas las peticiones GET de arranque (el orden no esta garantizado y
    // algunas se disparan en cascada al resolver los catalogos).
    for (let i = 0; i < 5; i++) {
      const pendientes = http.match((r) => r.method === 'GET');
      if (pendientes.length === 0) {
        break;
      }
      for (const req of pendientes) {
        if (req.request.url === URL_MATERIALES) {
          req.flush(pagina(materiales));
        } else if (req.request.url === URL_EXISTENCIAS) {
          req.flush(pagina(existencias));
        } else if (req.request.url === URL_ALMACENES) {
          req.flush(pagina(almacenes));
        } else {
          req.flush(pagina([]));
        }
      }
      fixture.detectChanges();
    }
  }

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function vista(): ViewTest {
    return fixture.componentInstance as unknown as ViewTest;
  }

  beforeEach(() => {
    toast = new ToastSpy();
    auth = new AuthServiceStub();
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  /** Activa la pestana con el indice dado (mat-tab renderiza solo la activa). */
  function activarPestana(indice: number): void {
    (fixture.componentInstance as unknown as { onCambioPestana(i: number): void }).onCambioPestana(
      indice,
    );
    fixture.detectChanges();
  }

  it('muestra las existencias por NOMBRE de almacen y material, sin exponer UUIDs', async () => {
    await crear();
    resolverArranque();
    // La tabla de Existencias vive en la segunda pestana (indice 1).
    activarPestana(1);

    const t = texto();
    expect(t).toContain('Bodega Central');
    expect(t).toContain('Lamina acrilica');
    expect(t).not.toContain(ALMACEN_UUID);
    expect(t).not.toContain(MATERIAL_UUID);
  });

  it('registrarEntrada llama al endpoint correcto con el cuerpo esperado', async () => {
    await crear();
    resolverArranque();

    vista().formEntrada.setValue({
      almacenId: ALMACEN_UUID,
      materialId: MATERIAL_UUID,
      cantidad: 8,
      costoUnitario: 100,
      loteCodigo: 'L-001',
    });
    vista().registrarEntrada();

    const post = http.expectOne(
      (r) => r.method === 'POST' && r.url === `${URL_ALMACENES}/${ALMACEN_UUID}/entradas`,
    );
    expect(post.request.body).toEqual({
      materialId: MATERIAL_UUID,
      cantidad: 8,
      costoUnitario: 100,
      loteCodigo: 'L-001',
    });
    post.flush({} as MovimientoAlmacen);

    // Tras el exito recarga existencias.
    for (const req of http.match((r) => r.url === URL_EXISTENCIAS && r.method === 'GET')) {
      req.flush(pagina([existenciaFalsa()]));
    }
    expect(toast.exitos).toContain('Entrada registrada.');
  });

  it('registrarSalida llama al endpoint correcto sin costo', async () => {
    await crear();
    resolverArranque();

    vista().formSalida.setValue({
      almacenId: ALMACEN_UUID,
      materialId: MATERIAL_UUID,
      cantidad: 2,
      loteCodigo: '',
    });
    vista().registrarSalida();

    const post = http.expectOne(
      (r) => r.method === 'POST' && r.url === `${URL_ALMACENES}/${ALMACEN_UUID}/salidas`,
    );
    expect(post.request.body).toEqual({
      materialId: MATERIAL_UUID,
      cantidad: 2,
      loteCodigo: null,
    });
    post.flush({} as MovimientoAlmacen);
    for (const req of http.match((r) => r.url === URL_EXISTENCIAS && r.method === 'GET')) {
      req.flush(pagina([existenciaFalsa()]));
    }
    expect(toast.exitos).toContain('Salida registrada.');
  });

  it('una salida 422 muestra el mensaje de negocio sin romper la vista', async () => {
    await crear();
    resolverArranque();

    vista().formSalida.setValue({
      almacenId: ALMACEN_UUID,
      materialId: MATERIAL_UUID,
      cantidad: 999,
      loteCodigo: '',
    });
    vista().registrarSalida();

    http
      .expectOne((r) => r.method === 'POST' && r.url === `${URL_ALMACENES}/${ALMACEN_UUID}/salidas`)
      .flush(
        { detail: 'Existencias insuficientes para la salida.' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    // El mensaje 422 de negocio queda disponible para la vista de Movimientos.
    expect(vista().errorSalida()).toBe('Existencias insuficientes para la salida.');
    // No se dispara recarga de existencias tras el error.
    http.expectNone((r) => r.method === 'GET' && r.url === URL_EXISTENCIAS);
  });

  it('transferir llama al endpoint con origen y destino distintos', async () => {
    await crear();
    resolverArranque();

    vista().formTransferencia.setValue({
      almacenOrigenId: ALMACEN_UUID,
      almacenDestinoId: ALMACEN2_UUID,
      materialId: MATERIAL_UUID,
      cantidad: 4,
    });
    vista().registrarTransferencia();

    const post = http.expectOne(
      (r) => r.method === 'POST' && r.url === '/api/v1/inventario-avanzado/transferencias',
    );
    expect(post.request.body).toEqual({
      almacenOrigenId: ALMACEN_UUID,
      almacenDestinoId: ALMACEN2_UUID,
      materialId: MATERIAL_UUID,
      cantidad: 4,
    });
    post.flush({} as MovimientoAlmacen);
    for (const req of http.match((r) => r.url === URL_EXISTENCIAS && r.method === 'GET')) {
      req.flush(pagina([existenciaFalsa()]));
    }
    expect(toast.exitos).toContain('Transferencia registrada.');
  });

  it('"Ver Kardex" desde una existencia preselecciona almacen+material y consulta', async () => {
    await crear();
    resolverArranque();

    vista().verKardexDeExistencia(existenciaFalsa());
    fixture.detectChanges();

    // Se consulta el Kardex por seleccion (ids en la ruta, sin teclear UUID).
    const req = http.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url ===
          `${URL_ALMACENES}/${ALMACEN_UUID}/materiales/${MATERIAL_UUID}/kardex`,
    );
    req.flush(pagina<MovimientoAlmacen>([]));

    const raw = vista().formKardex.getRawValue();
    expect(raw.almacenId).toBe(ALMACEN_UUID);
    expect(raw.materialId).toBe(MATERIAL_UUID);
  });

  it('guardarConfig hace el PUT con los valores del formulario', async () => {
    await crear();
    resolverArranque();

    vista().formConfig.setValue({
      materialId: MATERIAL_UUID,
      metodoCosteo: 'peps',
      stockMaximo: 100,
      controlLote: true,
      consumoPromedio: 3,
      tiempoEntregaDias: 5,
      stockSeguridad: 2,
    });
    vista().guardarConfig();

    const put = http.expectOne(
      (r) =>
        r.method === 'PUT' &&
        r.url === `/api/v1/inventario-avanzado/materiales/${MATERIAL_UUID}/config-inventario`,
    );
    expect(put.request.body).toEqual({
      metodoCosteo: 'peps',
      stockMaximo: 100,
      controlLote: true,
      consumoPromedio: 3,
      tiempoEntregaDias: 5,
      stockSeguridad: 2,
    });
    const config: ConfigInventarioMaterial = {
      id: 'c1',
      materialId: MATERIAL_UUID,
      metodoCosteo: 'peps',
      stockMaximo: 100,
      controlLote: true,
      consumoPromedio: 3,
      tiempoEntregaDias: 5,
      stockSeguridad: 2,
      puntoReorden: 17,
      version: 0,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    };
    put.flush(config);
    fixture.detectChanges();
    expect(toast.exitos).toContain('Configuración guardada.');
    // La config devuelta (con el punto de reorden derivado) queda disponible.
    expect(vista().configGuardada()?.puntoReorden).toBe(17);
  });

  it('lotes: listar hace el GET y resalta lotes caducados/proximos', async () => {
    await crear();
    resolverArranque();

    vista().formLoteMaterial.setValue({ materialId: MATERIAL_UUID });
    vista().cargarLotes();

    const ayer = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
    const lotes: Lote[] = [
      {
        id: 'l1',
        materialId: MATERIAL_UUID,
        codigo: 'LOTE-CADUCADO',
        fechaCaducidad: ayer,
        version: 0,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ];
    http
      .expectOne(
        (r) =>
          r.method === 'GET' &&
          r.url === `/api/v1/inventario-avanzado/materiales/${MATERIAL_UUID}/lotes`,
      )
      .flush(pagina(lotes));
    fixture.detectChanges();

    expect(vista().lotes().map((l) => l.codigo)).toContain('LOTE-CADUCADO');
    // El lote con caducidad en el pasado se clasifica como caducado.
    expect(vista().estadoLote(lotes[0])).toBe('caducado');
    expect(vista().etiquetaEstadoLote(lotes[0])).toBe('Caducado');
  });

  it('lotes: crear hace el POST con codigo y recarga', async () => {
    await crear();
    resolverArranque();

    vista().formLoteMaterial.setValue({ materialId: MATERIAL_UUID });
    vista().cargarLotes();
    http
      .expectOne(
        (r) =>
          r.method === 'GET' &&
          r.url === `/api/v1/inventario-avanzado/materiales/${MATERIAL_UUID}/lotes`,
      )
      .flush(pagina<Lote>([]));
    fixture.detectChanges();

    vista().formLote.setValue({ codigo: 'L-NUEVO', fechaCaducidad: null });
    vista().crearLote();

    const post = http.expectOne(
      (r) =>
        r.method === 'POST' &&
        r.url === `/api/v1/inventario-avanzado/materiales/${MATERIAL_UUID}/lotes`,
    );
    expect(post.request.body).toEqual({ codigo: 'L-NUEVO', fechaCaducidad: null });
    post.flush({
      id: 'l2',
      materialId: MATERIAL_UUID,
      codigo: 'L-NUEVO',
      fechaCaducidad: null,
      version: 0,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    } as Lote);
    http
      .expectOne(
        (r) =>
          r.method === 'GET' &&
          r.url === `/api/v1/inventario-avanzado/materiales/${MATERIAL_UUID}/lotes`,
      )
      .flush(pagina<Lote>([]));
    expect(toast.exitos).toContain('Lote creado.');
  });

  it('gating: sin permisos de movimiento/kardex/lote/config las pestanas se ocultan', async () => {
    auth.permisos = new Set<string>(['almacen:listar', 'material:leer']);
    await crear();
    resolverArranque();

    const t = texto();
    expect(t).toContain('Existencias');
    expect(t).not.toContain('Movimientos');
    expect(t).not.toContain('Configuracion');
    expect(t).not.toContain('Lotes');
    // Kardex oculto: no aparece la accion "Ver Kardex" en las filas.
    expect(t).not.toContain('Ver Kardex');
  });

  // ---------------------------------------------------------------------------
  // Rediseno del tab Movimientos: UN formulario con selector de tipo (tarea 1.2)
  // Req 2.1 (un solo formulario), 2.4 (indica el tipo actual), 4.1 (sin UUIDs).
  // ---------------------------------------------------------------------------

  /** Indice de la pestana Movimientos con todos los permisos (resumen=0, existencias=1). */
  const INDICE_MOVIMIENTOS = 2;

  interface VistaMovimiento {
    tipoMovimiento: { set(v: 'entrada' | 'salida' | 'transferencia'): void };
    registrarMovimiento(): void;
    registrarEntrada(): void;
    registrarSalida(): void;
    registrarTransferencia(): void;
  }

  function vistaMovimiento(): VistaMovimiento {
    return fixture.componentInstance as unknown as VistaMovimiento;
  }

  it('Movimientos muestra UN solo formulario con selector de tipo (button-toggle-group)', async () => {
    await crear();
    resolverArranque();
    activarPestana(INDICE_MOVIMIENTOS);

    const raiz = fixture.nativeElement as HTMLElement;
    // Un unico grupo selector de tipo de movimiento...
    const grupos = raiz.querySelectorAll('mat-button-toggle-group');
    expect(grupos.length).toBe(1);
    // ...con las tres opciones Entrada / Salida / Transferencia.
    const opciones = Array.from(raiz.querySelectorAll('mat-button-toggle')).map(
      (b) => b.textContent?.trim() ?? '',
    );
    expect(opciones.length).toBe(3);
    expect(opciones.some((o) => o.includes('Entrada'))).toBe(true);
    expect(opciones.some((o) => o.includes('Salida'))).toBe(true);
    expect(opciones.some((o) => o.includes('Transferencia'))).toBe(true);

    // El grupo se expone como region etiquetada (indica que captura el tipo, Req 2.4).
    const region = raiz.querySelector('.inventario-avanzado__tipo-movimiento[role="group"]');
    expect(region?.getAttribute('aria-label')).toBe('Tipo de movimiento');

    // Por defecto (entrada) hay UN solo <form> visible en la seccion.
    const formularios = raiz.querySelectorAll('.inventario-avanzado__movimiento-campos');
    expect(formularios.length).toBe(1);
  });

  it('cambiar tipoMovimiento cambia los campos visibles (transferencia muestra origen/destino)', async () => {
    await crear();
    resolverArranque();
    activarPestana(INDICE_MOVIMIENTOS);
    const raiz = fixture.nativeElement as HTMLElement;

    // En "entrada" hay Costo unitario y NO hay campos de origen/destino.
    expect(raiz.textContent ?? '').toContain('Costo unitario');
    expect(raiz.textContent ?? '').not.toContain('Almacen de origen');

    // Al cambiar a "transferencia" aparecen origen y destino; desaparece costo.
    vistaMovimiento().tipoMovimiento.set('transferencia');
    fixture.detectChanges();
    const tTransfer = raiz.textContent ?? '';
    expect(tTransfer).toContain('Almacen de origen');
    expect(tTransfer).toContain('Almacen de destino');
    expect(tTransfer).not.toContain('Costo unitario');

    // Al cambiar a "salida" no hay costo unitario ni origen/destino.
    vistaMovimiento().tipoMovimiento.set('salida');
    fixture.detectChanges();
    const tSalida = raiz.textContent ?? '';
    expect(tSalida).not.toContain('Costo unitario');
    expect(tSalida).not.toContain('Almacen de origen');
  });

  it('registrarMovimiento() despacha a registrarEntrada cuando el tipo es "entrada"', async () => {
    await crear();
    resolverArranque();

    const v = vistaMovimiento();
    v.tipoMovimiento.set('entrada');
    const espiaEntrada = vi.spyOn(v, 'registrarEntrada');
    const espiaSalida = vi.spyOn(v, 'registrarSalida');
    const espiaTransfer = vi.spyOn(v, 'registrarTransferencia');

    v.registrarMovimiento();

    expect(espiaEntrada).toHaveBeenCalledTimes(1);
    expect(espiaSalida).not.toHaveBeenCalled();
    expect(espiaTransfer).not.toHaveBeenCalled();
  });

  it('registrarMovimiento() despacha a registrarSalida cuando el tipo es "salida"', async () => {
    await crear();
    resolverArranque();

    const v = vistaMovimiento();
    v.tipoMovimiento.set('salida');
    const espiaEntrada = vi.spyOn(v, 'registrarEntrada');
    const espiaSalida = vi.spyOn(v, 'registrarSalida');
    const espiaTransfer = vi.spyOn(v, 'registrarTransferencia');

    v.registrarMovimiento();

    expect(espiaSalida).toHaveBeenCalledTimes(1);
    expect(espiaEntrada).not.toHaveBeenCalled();
    expect(espiaTransfer).not.toHaveBeenCalled();
  });

  it('registrarMovimiento() despacha a registrarTransferencia cuando el tipo es "transferencia"', async () => {
    await crear();
    resolverArranque();

    const v = vistaMovimiento();
    v.tipoMovimiento.set('transferencia');
    const espiaEntrada = vi.spyOn(v, 'registrarEntrada');
    const espiaSalida = vi.spyOn(v, 'registrarSalida');
    const espiaTransfer = vi.spyOn(v, 'registrarTransferencia');

    v.registrarMovimiento();

    expect(espiaTransfer).toHaveBeenCalledTimes(1);
    expect(espiaEntrada).not.toHaveBeenCalled();
    expect(espiaSalida).not.toHaveBeenCalled();
  });

  it('el tab Movimientos no expone ningun UUID en el DOM (nombres via NombresInventarioService)', async () => {
    await crear();
    resolverArranque();
    activarPestana(INDICE_MOVIMIENTOS);

    // Recorre los tres tipos: ninguno debe volcar un UUID al DOM.
    for (const tipo of ['entrada', 'salida', 'transferencia'] as const) {
      vistaMovimiento().tipoMovimiento.set(tipo);
      fixture.detectChanges();
      const t = texto();
      expect(t).not.toContain(ALMACEN_UUID);
      expect(t).not.toContain(ALMACEN2_UUID);
      expect(t).not.toContain(MATERIAL_UUID);
    }
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    await crear();
    resolverArranque();
    await fixture.whenStable();
    await esperarSinViolaciones(fixture);
  }, 30000);
});
