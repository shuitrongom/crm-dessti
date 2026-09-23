// =============================================================================
// Pruebas de la vista PlataformaFacturacion (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Verifican, de forma determinista y sin red real (proyecto zoneless):
//   - Al seleccionar una Empresa se cargan y listan sus facturas emitidas.
//   - "Ver PDF" / "Descargar" llaman al servicio y abren/descargan el Blob
//     (window.open / URL.createObjectURL mockeados).
//   - "Emitir" hace POST y recarga el listado.
//   - Un error de emision (409) muestra un toast con el mensaje del backend.
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';
import { of, throwError } from 'rxjs';

import { PlataformaFacturacion } from './facturacion';
import { EmpresasService } from '../services/empresas.service';
import { FacturacionService } from '../services/facturacion.service';
import { NotificacionesService } from '../../../shared/services/notificaciones.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Empresa, FacturaRenta } from '../models/plataforma.models';
import { esperarSinViolaciones } from '../../../../testing/axe';

// Registra es-MX para que los pipes currency/date con locale explicito no lancen NG0701.
registerLocaleData(localeEsMx);

/** AuthService de prueba: super_admin con permisos de factura_renta. */
class AuthServiceStub {
  tienePermiso(recurso: string, _operacion: string): boolean {
    return recurso === 'factura_renta';
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

/** EmpresasService de prueba: no se usa el autocompletado en estas pruebas. */
class EmpresasServiceStub {
  listar() {
    return of({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  }
}

/** FacturacionService de prueba: espia las llamadas y devuelve datos controlados. */
class FacturacionServiceStub {
  facturas: FacturaRenta[] = [];
  listadas: string[] = [];
  emitidos: { tenantId: string; periodo: string }[] = [];
  previsualizados: { tenantId: string; periodo: string }[] = [];
  descargados: string[] = [];
  errorEmitir: HttpErrorResponse | null = null;

  listarPorEmpresa(tenantId: string) {
    this.listadas.push(tenantId);
    return of(this.facturas);
  }
  previsualizar(tenantId: string, periodo: string) {
    this.previsualizados.push({ tenantId, periodo });
    return of(factura());
  }
  emitir(tenantId: string, periodo: string) {
    this.emitidos.push({ tenantId, periodo });
    if (this.errorEmitir) {
      return throwError(() => this.errorEmitir);
    }
    return of(factura());
  }
  descargarPdf(facturaId: string) {
    this.descargados.push(facturaId);
    return of(new Blob(['%PDF-1.4'], { type: 'application/pdf' }));
  }
}

/** Fabrica de Empresa con valores por defecto sobreescribibles. */
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
    planVigente: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...parcial,
  };
}

/** Fabrica de FacturaRenta con valores por defecto sobreescribibles. */
function factura(parcial: Partial<FacturaRenta> = {}): FacturaRenta {
  return {
    id: 'f1',
    tenantId: 'e1',
    periodo: '2026-03-01',
    monedaCodigo: 'MXN',
    total: 1500,
    estado: 'emitida',
    emitidaEn: '2026-03-01T10:00:00Z',
    lineas: [{ id: 'l1', moduloClave: 'crm', moduloNombre: 'CRM', precioAplicado: 1500 }],
    ...parcial,
  };
}

/** Superficie protegida que las pruebas necesitan tocar. */
interface FacturacionProbe {
  seleccionada: { set(v: Empresa | null): void };
  previsualizacion: { (): FacturaRenta | null };
  cargar(): void;
}

describe('PlataformaFacturacion', () => {
  let fixture: ComponentFixture<PlataformaFacturacion>;
  let service: FacturacionServiceStub;
  let toast: NotificacionesServiceStub;

  beforeEach(async () => {
    service = new FacturacionServiceStub();
    toast = new NotificacionesServiceStub();

    await TestBed.configureTestingModule({
      imports: [PlataformaFacturacion, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useClass: AuthServiceStub },
        { provide: EmpresasService, useClass: EmpresasServiceStub },
        { provide: FacturacionService, useValue: service },
        { provide: NotificacionesService, useValue: toast },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PlataformaFacturacion);
    fixture.detectChanges();
  });

  /** Selecciona una Empresa y carga sus facturas mediante la superficie protegida. */
  function seleccionar(e: Empresa): void {
    const probe = fixture.componentInstance as unknown as FacturacionProbe;
    probe.seleccionada.set(e);
    probe.cargar();
    fixture.detectChanges();
  }

  /** Localiza un boton por el texto que contiene. */
  function boton(texto: string): HTMLButtonElement {
    const botones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ) as HTMLButtonElement[];
    const encontrado = botones.find((b) => (b.textContent ?? '').includes(texto));
    expect(encontrado).toBeTruthy();
    return encontrado!;
  }

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('al seleccionar una empresa carga y lista sus facturas emitidas', () => {
    service.facturas = [factura({ id: 'f1', periodo: '2026-03-01' })];
    seleccionar(empresa({ id: 'e1' }));

    expect(service.listadas).toContain('e1');
    // El total se muestra formateado como moneda (aparece el importe).
    expect(texto()).toContain('1,500');
    // KPI de conteo.
    expect(texto()).toContain('Facturas emitidas');
  });

  it('"Ver PDF" descarga el Blob y lo abre en una pestana nueva', () => {
    const createObjectURL = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:mock');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const open = vi.spyOn(window, 'open').mockReturnValue(null);

    service.facturas = [factura({ id: 'f1' })];
    seleccionar(empresa({ id: 'e1' }));

    boton('Ver PDF').click();

    expect(service.descargados).toContain('f1');
    expect(createObjectURL).toHaveBeenCalled();
    expect(open).toHaveBeenCalledWith('blob:mock', '_blank');
  });

  it('"Descargar" descarga el Blob como archivo', () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);

    service.facturas = [factura({ id: 'f1' })];
    seleccionar(empresa({ id: 'e1' }));

    boton('Descargar').click();

    expect(service.descargados).toContain('f1');
    expect(click).toHaveBeenCalled();
  });

  it('"Emitir factura" hace POST y recarga el listado', () => {
    seleccionar(empresa({ id: 'e1' }));
    const cargasPrevias = service.listadas.length;

    boton('Emitir factura').click();

    expect(service.emitidos.length).toBe(1);
    expect(service.emitidos[0].tenantId).toBe('e1');
    // Recarga: se vuelve a listar tras emitir.
    expect(service.listadas.length).toBe(cargasPrevias + 1);
    expect(toast.exitos.length).toBe(1);
  });

  it('muestra un toast de error cuando la emision falla (409 ya emitida)', () => {
    service.errorEmitir = new HttpErrorResponse({
      status: 409,
      error: { detail: 'Ya existe una factura para ese periodo.' },
    });
    seleccionar(empresa({ id: 'e1' }));

    boton('Emitir factura').click();

    expect(toast.errores).toContain('Ya existe una factura para ese periodo.');
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    service.facturas = [factura({ id: 'f1' })];
    seleccionar(empresa({ id: 'e1' }));
    await esperarSinViolaciones(fixture);
  });
});
