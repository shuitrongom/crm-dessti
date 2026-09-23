// =============================================================================
// Pruebas de la vista PlataformaGiros: render, KPIs y accesibilidad (Req 9, 57)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin zona ni red real:
//   - Render del encabezado, KPIs y tabla con los giros devueltos.
//   - Resolucion de los conteos KPI (forkJoin de paginas de tamano 1).
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// El super_admin de prueba posee todos los permisos de giro.
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { PlataformaGiros } from './giros';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { Giro } from '../models/plataforma.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

/** AuthService de prueba: super_admin con todos los permisos de giro. */
class AuthServiceStub {
  tienePermiso(recurso: string, _operacion: string): boolean {
    return recurso === 'giro';
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

function giro(parcial: Partial<Giro>): Giro {
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

describe('PlataformaGiros', () => {
  let fixture: ComponentFixture<PlataformaGiros>;
  let http: HttpTestingController;
  let confirm: ConfirmDialogServiceStub;
  let toast: NotificacionesServiceStub;

  beforeEach(async () => {
    confirm = new ConfirmDialogServiceStub();
    toast = new NotificacionesServiceStub();
    await TestBed.configureTestingModule({
      imports: [PlataformaGiros, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: NotificacionesService, useValue: toast },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PlataformaGiros);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    // Limpia cualquier overlay de menu abierto para no contaminar otras pruebas.
    document.querySelectorAll('.cdk-overlay-container').forEach((el) => (el.innerHTML = ''));
  });

  /** Resuelve la carga inicial (lista) y los tres conteos KPI. */
  function resolverCargaInicial(): void {
    const filas: Giro[] = [
      giro({ id: 'g1', clave: 'carpinteria', nombreVisible: 'Carpinteria', activo: true }),
      giro({ id: 'g2', clave: 'telas-textiles', nombreVisible: 'Telas y textiles', activo: false }),
    ];
    // La primera peticion es la lista (size 20); las siguientes tres son los KPI (size 1).
    const peticiones = http.match((r) => r.url === '/api/v1/plataforma/giros');
    const lista = peticiones.find((r) => r.request.params.get('size') === '20')!;
    lista.flush({ content: filas, page: 0, size: 20, totalElements: 2, totalPages: 1 });

    for (const req of peticiones.filter((r) => r.request.params.get('size') === '1')) {
      const activo = req.request.params.get('activo');
      const total = activo === null ? 2 : activo === 'true' ? 1 : 1;
      req.flush({ content: [], page: 0, size: 1, totalElements: total, totalPages: total });
    }
    fixture.detectChanges();
  }

  it('renderiza el encabezado y la fila de KPIs', async () => {
    fixture.detectChanges();
    resolverCargaInicial();
    await fixture.whenStable();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Giros');
    expect(texto).toContain('Giros totales');
    expect(texto).toContain('Activos');
    expect(texto).toContain('Inactivos');
  });

  it('lista los giros con su clave y estado', async () => {
    fixture.detectChanges();
    resolverCargaInicial();
    await fixture.whenStable();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Carpinteria');
    expect(texto).toContain('telas-textiles');
    expect(texto).toContain('Activo');
    expect(texto).toContain('Inactivo');
  });

  it('muestra el badge de completitud "Completo"/"Base" segun tieneReglasNegocio', async () => {
    fixture.detectChanges();
    const filas: Giro[] = [
      giro({ id: 'g1', clave: 'anuncios-luminosos', nombreVisible: 'Anuncios', tieneReglasNegocio: true, modulosEspecificos: 2 }),
      giro({ id: 'g2', clave: 'carpinteria', nombreVisible: 'Carpinteria', tieneReglasNegocio: false, modulosEspecificos: 0 }),
    ];
    const peticiones = http.match((r) => r.url === '/api/v1/plataforma/giros');
    const lista = peticiones.find((r) => r.request.params.get('size') === '20')!;
    lista.flush({ content: filas, page: 0, size: 20, totalElements: 2, totalPages: 1 });
    for (const req of peticiones.filter((r) => r.request.params.get('size') === '1')) {
      req.flush({ content: [], page: 0, size: 1, totalElements: 1, totalPages: 1 });
    }
    fixture.detectChanges();
    await fixture.whenStable();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Completo');
    expect(texto).toContain('Base');
  });

  /**
   * Resuelve la carga inicial con una unica fila (giro) y sus tres conteos KPI,
   * para poder abrir el menu de acciones de esa fila de forma inequivoca.
   */
  function resolverConGiro(unico: Giro): void {
    const peticiones = http.match((r) => r.url === '/api/v1/plataforma/giros');
    const lista = peticiones.find((r) => r.request.params.get('size') === '20')!;
    lista.flush({ content: [unico], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    for (const req of peticiones.filter((r) => r.request.params.get('size') === '1')) {
      req.flush({ content: [], page: 0, size: 1, totalElements: 1, totalPages: 1 });
    }
    fixture.detectChanges();
  }

  /** Abre el menu de acciones de la (unica) fila renderizada. */
  function abrirMenu(): void {
    const trigger = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      'button[aria-label^="Mas acciones"]',
    )!;
    trigger.click();
    fixture.detectChanges();
  }

  /** Localiza el item "Eliminar" del menu abierto (vive en el overlay del CDK). */
  function itemEliminar(): HTMLButtonElement | null {
    return document.querySelector<HTMLButtonElement>('.giros__accion--eliminar');
  }

  it('muestra el item Eliminar en el menu cuando se tiene el permiso', () => {
    fixture.detectChanges();
    resolverConGiro(giro({ id: 'g1', nombreVisible: 'Carpinteria', tieneReglasNegocio: false }));
    abrirMenu();
    expect(itemEliminar()).not.toBeNull();
    expect(itemEliminar()?.textContent).toContain('Eliminar');
  });

  it('deshabilita Eliminar (con tooltip) cuando el giro ya tiene reglas de negocio', () => {
    fixture.detectChanges();
    resolverConGiro(giro({ id: 'g1', nombreVisible: 'Anuncios', tieneReglasNegocio: true }));
    abrirMenu();
    const item = itemEliminar();
    expect(item).not.toBeNull();
    expect(item?.disabled).toBe(true);
  });

  it('elimina el giro y recarga lista + KPIs al confirmar', async () => {
    confirm.respuesta = true;
    fixture.detectChanges();
    resolverConGiro(giro({ id: 'g9', nombreVisible: 'Textiles', tieneReglasNegocio: false }));
    abrirMenu();
    itemEliminar()!.click();
    await fixture.whenStable();

    // DELETE del giro seleccionado.
    const del = http.expectOne('/api/v1/plataforma/giros/g9');
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });

    // Tras el exito se recargan lista (size 20) y conteos (size 1).
    const recargas = http.match((r) => r.url === '/api/v1/plataforma/giros');
    expect(recargas.some((r) => r.request.params.get('size') === '20')).toBe(true);
    expect(recargas.filter((r) => r.request.params.get('size') === '1').length).toBe(3);
    for (const req of recargas) {
      req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    }
    expect(toast.exitos).toContain('Giro "Textiles" eliminado.');
  });

  it('muestra un toast de error cuando la eliminacion falla (422 del backend)', async () => {
    confirm.respuesta = true;
    fixture.detectChanges();
    resolverConGiro(giro({ id: 'g9', nombreVisible: 'Textiles', tieneReglasNegocio: false }));
    abrirMenu();
    itemEliminar()!.click();
    await fixture.whenStable();

    const del = http.expectOne('/api/v1/plataforma/giros/g9');
    del.flush(
      { detail: 'El giro esta en uso por 2 empresas.' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    await fixture.whenStable();
    expect(toast.errores.length).toBe(1);
  });

  it(
    'no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)',
    async () => {
      fixture.detectChanges();
      resolverCargaInicial();
      await fixture.whenStable();
      await esperarSinViolaciones(fixture);
    },
    // axe-core es intensivo en CPU; bajo carga en Windows puede superar el limite
    // por defecto de 5 s. Un timeout explicito hace determinista esta prueba.
    30000,
  );
});
