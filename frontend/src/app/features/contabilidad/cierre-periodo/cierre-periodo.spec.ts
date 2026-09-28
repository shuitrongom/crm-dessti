// =============================================================================
// Pruebas de la vista Cierre de periodo contable (candado contable)
// -----------------------------------------------------------------------------
// Verifican, con backend HTTP simulado, que la vista consulta los periodos del
// año al iniciar, renderiza el estado de cada mes, cierra un mes abierto y reabre
// un mes cerrado enviando el motivo capturado.
// =============================================================================

import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';
import { LOCALE_ID } from '@angular/core';
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { ContabilidadCierrePeriodo } from './cierre-periodo';
import { PeriodoContable } from '../models/cierre-periodo.models';

// Registra es-MX para que el DatePipe con locale explícito no lance NG0701 en tests.
registerLocaleData(localeEsMx);

const BASE = '/api/v1';
const ANIO = new Date().getFullYear();

/** Construye la lista de 12 meses; por defecto todos abiertos salvo overrides. */
function doceMeses(overrides: Partial<Record<number, Partial<PeriodoContable>>> = {}): PeriodoContable[] {
  const lista: PeriodoContable[] = [];
  for (let mes = 1; mes <= 12; mes++) {
    lista.push({
      anio: ANIO,
      mes,
      estado: 'abierto',
      fechaCierre: null,
      cerradoPor: null,
      fechaReapertura: null,
      reabiertoPor: null,
      motivoReapertura: null,
      ...(overrides[mes] ?? {}),
    });
  }
  return lista;
}

describe('ContabilidadCierrePeriodo (cierre de periodo)', () => {
  let fixture: ComponentFixture<ContabilidadCierrePeriodo>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ContabilidadCierrePeriodo, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ContabilidadCierrePeriodo);
    http = TestBed.inject(HttpTestingController);
    // El constructor dispara la consulta inicial del año.
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  /** Resuelve la consulta inicial GET /contabilidad/periodos?anio=. */
  function resolverConsultaInicial(lista: PeriodoContable[]): void {
    const req = http.expectOne((r) => r.url === `${BASE}/contabilidad/periodos`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('anio')).toBe(String(ANIO));
    req.flush(lista);
    fixture.detectChanges();
  }

  it('consulta los periodos al iniciar y renderiza el encabezado', () => {
    resolverConsultaInicial(doceMeses());
    const texto: string = fixture.nativeElement.textContent;
    expect(texto).toContain('Cierre de periodo contable');
    expect(texto).toContain('Enero');
    expect(texto).toContain('Diciembre');
  });

  it('cerrar un mes abierto llama al endpoint de cierre y actualiza el estado', () => {
    resolverConsultaInicial(doceMeses());
    const componente = fixture.componentInstance;

    componente.cerrar(3);

    const req = http.expectOne(`${BASE}/contabilidad/periodos/cerrar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ anio: ANIO, mes: 3 });
    req.flush({
      anio: ANIO,
      mes: 3,
      estado: 'cerrado',
      fechaCierre: '2026-03-31T12:00:00Z',
      cerradoPor: 'contador@empresa',
      fechaReapertura: null,
      reabiertoPor: null,
      motivoReapertura: null,
    } as PeriodoContable);
    fixture.detectChanges();

    const cerrado = componente['periodos']().find((p) => p.mes === 3);
    expect(cerrado?.estado).toBe('cerrado');
  });

  it('reabrir un mes cerrado envía el motivo capturado', () => {
    resolverConsultaInicial(doceMeses({ 5: { estado: 'cerrado', cerradoPor: 'admin' } }));
    const componente = fixture.componentInstance;

    componente.iniciarReapertura(5);
    componente['formReapertura'].setValue({ motivo: 'Ajuste por revisión SAT' });
    componente.confirmarReapertura(5);

    const req = http.expectOne(`${BASE}/contabilidad/periodos/reabrir`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ anio: ANIO, mes: 5, motivo: 'Ajuste por revisión SAT' });
    req.flush({
      anio: ANIO,
      mes: 5,
      estado: 'abierto',
      fechaCierre: null,
      cerradoPor: null,
      fechaReapertura: '2026-06-01T10:00:00Z',
      reabiertoPor: 'admin',
      motivoReapertura: 'Ajuste por revisión SAT',
    } as PeriodoContable);
    fixture.detectChanges();

    const reabierto = componente['periodos']().find((p) => p.mes === 5);
    expect(reabierto?.estado).toBe('abierto');
  });

  it('no envía la reapertura si el motivo está vacío', () => {
    resolverConsultaInicial(doceMeses({ 7: { estado: 'cerrado' } }));
    const componente = fixture.componentInstance;

    componente.iniciarReapertura(7);
    componente['formReapertura'].setValue({ motivo: '' });
    componente.confirmarReapertura(7);

    // No debe emitirse ninguna petición de reapertura.
    http.expectNone(`${BASE}/contabilidad/periodos/reabrir`);
  });
});
