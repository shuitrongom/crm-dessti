// =============================================================================
// Pruebas del dialogo PaqueteSuscripcionDialog (Req 10.1, 10.4, 10.6)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - `duracionDias` fuera de rango (0 o 400) deja el formulario invalido y no
//     envia nada; con un valor valido (90) si envia.
//   - Al marcar "admite prueba" se revela y exige `duracionPruebaMeses`: sin ese
//     dato no se envia; con un valor coherente si.
//   - Coherencia del periodo de prueba (~30 dias/mes): si meses*30 excede la
//     duracion del Paquete, guardar() aborta sin enviar; si no la excede, envia.
//   - Al crear se envia POST /paquetes-suscripcion con el cuerpo esperado
//     (duracionDias, admitePrueba, duracionPruebaMeses, giroId, monedaCodigo,
//     preciosModulos) y el dialogo se cierra con el Paquete devuelto.
//   - Dependencia de modulos (Req 9): marcar `inventario-avanzado` marca tambien
//     `operacion` y muestra el aviso es-MX; intentar desmarcar `operacion` con
//     `inventario-avanzado` activo lo impide y da el motivo es-MX; todo derivado
//     del mapa de dependencias (sin hardcode: con mapa vacio no hay aviso ni
//     bloqueo).
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';

import { PaqueteSuscripcionDialog } from './paquete-suscripcion-dialog';
import {
  DependenciasModulos,
  Giro,
  Moneda,
  ModuloCatalogo,
  PaqueteSuscripcion,
} from '../models/plataforma.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

// Registra es-MX para que el CurrencyPipe con locale explicito no lance NG0701.
registerLocaleData(localeEsMx);

/** MatDialogRef de prueba que registra si se cerro y con que resultado. */
class DialogRefStub {
  cerradoCon: unknown = 'no-cerrado';
  close(resultado?: unknown): void {
    this.cerradoCon = resultado;
  }
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

function giro(parcial: Partial<Giro> & { id: string; clave: string }): Giro {
  return {
    nombreVisible: parcial.clave,
    descripcion: null,
    activo: true,
    version: 0,
    tieneReglasNegocio: false,
    modulosEspecificos: 0,
    ...parcial,
  };
}

const catalogo: ModuloCatalogo[] = [
  modulo({ clave: 'comercial', nombreVisible: 'Comercial (CRM)' }),
  modulo({ clave: 'compras', nombreVisible: 'Compras' }),
  modulo({ clave: 'operacion', nombreVisible: 'Operacion', giro: 'anuncios-luminosos' }),
  modulo({ clave: 'inventario-avanzado', nombreVisible: 'Inventario avanzado', giro: 'anuncios-luminosos' }),
  modulo({ clave: 'produccion', nombreVisible: 'Produccion', giro: 'carpinteria' }),
];

/** Mapa de dependencias por defecto: `inventario-avanzado` requiere `operacion`. */
const dependencias: DependenciasModulos = { 'inventario-avanzado': ['operacion'] };

const giros: Giro[] = [
  giro({ id: 'g-anuncios', clave: 'anuncios-luminosos', nombreVisible: 'Anuncios luminosos' }),
  giro({ id: 'g-carpinteria', clave: 'carpinteria', nombreVisible: 'Carpinteria' }),
];

const monedas: Moneda[] = [
  { codigo: 'MXN', nombre: 'Peso mexicano', activo: true },
  { codigo: 'USD', nombre: 'Dolar estadounidense', activo: true },
];

/** Forma minima del componente accedida por las pruebas. */
interface PaqueteDialogTest {
  formulario: {
    patchValue(v: Record<string, unknown>): void;
    controls: { giroId: { value: string } };
  };
  cambiarGiro(giroId: string): void;
  cambiarAdmitePrueba(admite: boolean): void;
  cambiarMoneda(codigo: string): void;
  alternar(clave: string, seleccionado: boolean): void;
  fijarPrecio(clave: string, valor: string): void;
  estaSeleccionado(clave: string): boolean;
  precioDe(clave: string): string;
  total: () => number;
  avisoDependencia: () => string | null;
  estaBloqueado(clave: string): boolean;
  motivoBloqueado(clave: string): string;
  guardar(): void;
}

describe('PaqueteSuscripcionDialog', () => {
  let fixture: ComponentFixture<PaqueteSuscripcionDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  function configurar(paquete: PaqueteSuscripcion | null): void {
    dialogRef = new DialogRefStub();
    TestBed.configureTestingModule({
      imports: [PaqueteSuscripcionDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { paquete } },
      ],
    });
    fixture = TestBed.createComponent(PaqueteSuscripcionDialog);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  /**
   * Resuelve la carga en paralelo de giros, monedas, catalogo de modulos y el
   * mapa de dependencias. El mapa es parametrizable para poder verificar que el
   * aviso/bloqueo derivan del mapa (con `{}` no hay aviso ni bloqueo).
   */
  function resolverCarga(mapaDependencias: DependenciasModulos = dependencias): void {
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/plataforma/giros').flush({
      content: giros,
      page: 0,
      size: 100,
      totalElements: giros.length,
      totalPages: 1,
    });
    http.expectOne('/api/v1/monedas').flush(monedas);
    http.expectOne('/api/v1/plataforma/modulos').flush(catalogo);
    http.expectOne('/api/v1/plataforma/dependencias-modulos').flush(mapaDependencias);
    fixture.detectChanges();
  }

  function componenteDe(): PaqueteDialogTest {
    return fixture.componentInstance as unknown as PaqueteDialogTest;
  }

  it('duracionDias fuera de rango (0) deja el formulario invalido y no envia', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    c.formulario.patchValue({
      nombre: 'Mensual',
      maxUsuarios: 5,
      duracionDias: 0,
      monedaCodigo: 'MXN',
    });
    c.guardar();
    http.expectNone('/api/v1/paquetes-suscripcion');
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('duracionDias fuera de rango (400) deja el formulario invalido y no envia', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    c.formulario.patchValue({
      nombre: 'Anual+',
      maxUsuarios: 5,
      duracionDias: 400,
      monedaCodigo: 'MXN',
    });
    c.guardar();
    http.expectNone('/api/v1/paquetes-suscripcion');
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('con admite prueba pero sin duracionPruebaMeses no envia (campo revelado y obligatorio)', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    c.formulario.patchValue({
      nombre: 'Con prueba',
      maxUsuarios: 5,
      duracionDias: 90,
      monedaCodigo: 'MXN',
    });
    // Al revelar el campo de prueba queda obligatorio; sin valor el form es invalido.
    c.cambiarAdmitePrueba(true);
    c.guardar();
    http.expectNone('/api/v1/paquetes-suscripcion');
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('coherencia de prueba: meses*30 que excede la duracion NO envia', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    c.formulario.patchValue({
      nombre: 'Prueba larga',
      maxUsuarios: 5,
      duracionDias: 30,
      monedaCodigo: 'MXN',
    });
    c.cambiarAdmitePrueba(true);
    // 3 meses ~= 90 dias > 30 dias de duracion: el componente aborta sin enviar.
    c.formulario.patchValue({ duracionPruebaMeses: 3 });
    c.guardar();
    http.expectNone('/api/v1/paquetes-suscripcion');
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('coherencia de prueba: meses*30 que NO excede la duracion si envia', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    c.formulario.patchValue({
      nombre: 'Prueba corta',
      maxUsuarios: 5,
      duracionDias: 180,
      monedaCodigo: 'MXN',
    });
    c.cambiarAdmitePrueba(true);
    // 3 meses ~= 90 dias <= 180 dias de duracion: envia.
    c.formulario.patchValue({ duracionPruebaMeses: 3 });
    c.guardar();

    const req = http.expectOne('/api/v1/paquetes-suscripcion');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.duracionDias).toBe(180);
    expect(req.request.body.admitePrueba).toBe(true);
    expect(req.request.body.duracionPruebaMeses).toBe(3);
    req.flush(paqueteRespuesta({ nombre: 'Prueba corta', duracionDias: 180 }));
    expect(dialogRef.cerradoCon).toBeTruthy();
  });

  it('al crear envia POST /paquetes-suscripcion con el cuerpo esperado y cierra el dialogo', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    c.formulario.patchValue({
      nombre: 'Basico',
      maxUsuarios: 8,
      duracionDias: 90,
    });
    c.cambiarMoneda('USD');
    c.alternar('comercial', true);
    c.fijarPrecio('comercial', '1500');
    c.alternar('operacion', true);
    c.fijarPrecio('operacion', '500');
    c.guardar();

    const req = http.expectOne('/api/v1/paquetes-suscripcion');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.nombre).toBe('Basico');
    expect(req.request.body.maxUsuarios).toBe(8);
    expect(req.request.body.duracionDias).toBe(90);
    expect(req.request.body.admitePrueba).toBe(false);
    expect(req.request.body.duracionPruebaMeses).toBeNull();
    expect(req.request.body.giroId).toBe('g-anuncios');
    expect(req.request.body.monedaCodigo).toBe('USD');
    expect(req.request.body.preciosModulos).toEqual({ comercial: 1500, operacion: 500 });
    req.flush(
      paqueteRespuesta({
        nombre: 'Basico',
        maxUsuarios: 8,
        duracionDias: 90,
        giroId: 'g-anuncios',
        monedaCodigo: 'USD',
        preciosModulos: { comercial: 1500, operacion: 500 },
        total: 2000,
        modulosHabilitados: ['comercial', 'operacion'],
      }),
    );
    expect(dialogRef.cerradoCon).toBeTruthy();
  });

  it('marcar inventario-avanzado marca tambien operacion y muestra el aviso de dependencia', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    fixture.detectChanges();

    c.alternar('inventario-avanzado', true);
    fixture.detectChanges();

    expect(c.estaSeleccionado('inventario-avanzado')).toBe(true);
    // Marca en cascada el requerido (operacion) derivado del mapa.
    expect(c.estaSeleccionado('operacion')).toBe(true);
    // El aviso es-MX no es null y menciona la dependencia.
    const aviso = c.avisoDependencia();
    expect(aviso).not.toBeNull();
    expect(aviso).toContain('depende de');
    expect(aviso).toContain('Operacion');
  });

  it('impide desmarcar operacion mientras inventario-avanzado sigue marcado y da el motivo', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    c.alternar('inventario-avanzado', true);
    fixture.detectChanges();

    // Intentar desmarcar el requerido no lo desmarca (bloqueo de deseleccion).
    c.alternar('operacion', false);
    fixture.detectChanges();

    expect(c.estaSeleccionado('operacion')).toBe(true);
    expect(c.estaBloqueado('operacion')).toBe(true);
    const motivo = c.motivoBloqueado('operacion');
    expect(motivo).toContain('No puedes desactivar');
    expect(motivo).toContain('Operacion');
  });

  it('el aviso y el bloqueo derivan del mapa: con mapa vacio no hay aviso ni bloqueo', () => {
    configurar(null);
    // Mapa de dependencias vacio: sin hardcode, no debe activarse aviso ni bloqueo.
    resolverCarga({});
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    c.alternar('inventario-avanzado', true);
    fixture.detectChanges();

    // Sin dependencia declarada, marcar inventario-avanzado NO arrastra operacion.
    expect(c.estaSeleccionado('operacion')).toBe(false);
    expect(c.avisoDependencia()).toBeNull();
    expect(c.estaBloqueado('operacion')).toBe(false);
  });

  it(
    'no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)',
    async () => {
      configurar(null);
      resolverCarga();
      // Elegir un giro hace que se rendericen los grupos de modulos.
      componenteDe().cambiarGiro('g-anuncios');
      fixture.detectChanges();
      await fixture.whenStable();
      await esperarSinViolaciones(fixture);
    },
    // axe-core es intensivo en CPU; bajo carga en Windows puede superar el limite
    // por defecto de 5 s. Un timeout explicito hace determinista esta prueba.
    30000,
  );
});

/** Construye una respuesta de Paquete de Suscripcion para flush() del backend. */
function paqueteRespuesta(parcial: Partial<PaqueteSuscripcion>): PaqueteSuscripcion {
  return {
    id: 'q9',
    nombre: 'Paquete',
    maxUsuarios: 5,
    giroId: 'g-anuncios',
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
