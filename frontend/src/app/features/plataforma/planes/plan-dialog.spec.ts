// =============================================================================
// Pruebas del dialogo PlanDialog (Req 25.1, plataforma-multigiro)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - El giro es obligatorio (el formulario invalido no envia nada).
//   - Al elegir un giro se muestran SOLO el nucleo comun y los modulos de ese
//     giro; nunca los de otros giros.
//   - Marcar un modulo habilita su campo de precio; el total en vivo suma los
//     precios de los modulos marcados y se formatea con la moneda elegida.
//   - Al guardar se envia { nombre, maxUsuarios, giroId, monedaCodigo,
//     preciosModulos } con preciosModulos SOLO de los modulos marcados.
//   - En edicion se preseleccionan giro, moneda y modulos con sus precios.
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

import { PlanDialog } from './plan-dialog';
import { DependenciasModulos, Giro, Moneda, ModuloCatalogo, Plan } from '../models/plataforma.models';
import { TITULO_NUCLEO } from '../models/modulos-agrupados';
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
interface PlanDialogTest {
  formulario: {
    patchValue(v: Record<string, unknown>): void;
    controls: { giroId: { value: string } };
  };
  cambiarGiro(giroId: string): void;
  alternar(clave: string, seleccionado: boolean): void;
  fijarPrecio(clave: string, valor: string): void;
  estaSeleccionado(clave: string): boolean;
  precioDe(clave: string): string;
  total: () => number;
  totalSeleccionados: () => number;
  moneda: () => string;
  avisoDependencia: () => string | null;
  estaBloqueado(clave: string): boolean;
  motivoBloqueado(clave: string): string;
  guardar(): void;
}

describe('PlanDialog', () => {
  let fixture: ComponentFixture<PlanDialog>;
  let http: HttpTestingController;
  let dialogRef: DialogRefStub;

  function configurar(plan: Plan | null): void {
    dialogRef = new DialogRefStub();
    TestBed.configureTestingModule({
      imports: [PlanDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { plan } },
      ],
    });
    fixture = TestBed.createComponent(PlanDialog);
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

  function componenteDe(): PlanDialogTest {
    return fixture.componentInstance as unknown as PlanDialogTest;
  }

  function titulosDeGrupo(): (string | undefined)[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.plan-dialog__grupo-titulo'),
    ).map((h) => h.textContent?.trim());
  }

  it('sin giro solo muestra el nucleo comun (pista para elegir giro)', () => {
    configurar(null);
    resolverCarga();
    const titulos = titulosDeGrupo();
    expect(titulos).toEqual([TITULO_NUCLEO]);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Selecciona un giro');
  });

  it('el giro es obligatorio: guardar sin giro no envia peticion', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.formulario.patchValue({ nombre: 'Basico', maxUsuarios: 5, giroId: '' });
    c.guardar();
    http.expectNone('/api/v1/planes');
    expect(dialogRef.cerradoCon).toBe('no-cerrado');
  });

  it('al elegir un giro muestra SOLO el nucleo y los modulos de ese giro', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    fixture.detectChanges();
    const titulos = titulosDeGrupo();
    expect(titulos[0]).toBe(TITULO_NUCLEO);
    expect(titulos).toContain('Anuncios luminosos');
    // Nunca el giro ajeno (carpinteria).
    expect(titulos).not.toContain('Carpinteria');
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Operacion');
    expect(texto).not.toContain('Produccion');
  });

  it('marcar un modulo habilita su precio y el total en vivo suma los marcados', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    fixture.detectChanges();

    c.alternar('comercial', true);
    c.fijarPrecio('comercial', '1500');
    c.alternar('operacion', true);
    c.fijarPrecio('operacion', '500.50');
    fixture.detectChanges();

    expect(c.estaSeleccionado('comercial')).toBe(true);
    expect(c.precioDe('comercial')).toBe('1500');
    expect(c.total()).toBe(2000.5);
    expect(c.totalSeleccionados()).toBe(2);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('2 módulos');
  });

  it('cambiar de giro descarta los modulos especificos del giro anterior (conserva nucleo)', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    c.alternar('comercial', true); // nucleo
    c.alternar('operacion', true); // especifico de anuncios
    c.cambiarGiro('g-carpinteria');
    fixture.detectChanges();

    expect(c.estaSeleccionado('comercial')).toBe(true);
    expect(c.estaSeleccionado('operacion')).toBe(false);
  });

  it('al crear envia POST /planes con preciosModulos solo de los marcados', () => {
    configurar(null);
    resolverCarga();
    const c = componenteDe();
    c.cambiarGiro('g-anuncios');
    c.formulario.patchValue({ nombre: 'Basico', maxUsuarios: 5, monedaCodigo: 'USD' });
    c.alternar('comercial', true);
    c.fijarPrecio('comercial', '1500');
    c.alternar('compras', true);
    c.fijarPrecio('compras', '500');
    c.guardar();

    const req = http.expectOne('/api/v1/planes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.nombre).toBe('Basico');
    expect(req.request.body.maxUsuarios).toBe(5);
    expect(req.request.body.giroId).toBe('g-anuncios');
    expect(req.request.body.monedaCodigo).toBe('USD');
    expect(req.request.body.preciosModulos).toEqual({ comercial: 1500, compras: 500 });
    req.flush({
      id: 'p9',
      nombre: 'Basico',
      maxUsuarios: 5,
      giroId: 'g-anuncios',
      monedaCodigo: 'USD',
      preciosModulos: { comercial: 1500, compras: 500 },
      total: 2000,
      modulosHabilitados: ['comercial', 'compras'],
      version: 0,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });
    expect(dialogRef.cerradoCon).toBeTruthy();
  });

  it('en edicion preselecciona giro, moneda y modulos con sus precios', () => {
    const plan: Plan = {
      id: 'p1',
      nombre: 'Pro',
      maxUsuarios: 10,
      duracionDias: 730,
      giroId: 'g-anuncios',
      monedaCodigo: 'USD',
      preciosModulos: { comercial: 1200, operacion: 800 },
      total: 2000,
      modulosHabilitados: ['comercial', 'operacion'],
      version: 3,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    };
    configurar(plan);
    resolverCarga();
    const c = componenteDe();

    expect(c.formulario.controls.giroId.value).toBe('g-anuncios');
    expect(c.moneda()).toBe('USD');
    expect(c.estaSeleccionado('comercial')).toBe(true);
    expect(c.precioDe('comercial')).toBe('1200');
    expect(c.estaSeleccionado('operacion')).toBe(true);
    expect(c.precioDe('operacion')).toBe('800');
    expect(c.estaSeleccionado('compras')).toBe(false);
    expect(c.total()).toBe(2000);
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
