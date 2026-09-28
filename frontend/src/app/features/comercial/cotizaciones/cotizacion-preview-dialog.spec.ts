// =============================================================================
// Pruebas del dialogo de vista previa del PDF de una Cotizacion (Req 6, 57)
// -----------------------------------------------------------------------------
// Verifican, sin red real:
//   - Al abrir, descarga el PDF (GET /cotizaciones/{id}/pdf como blob) y muestra
//     el iframe con el object URL sanitizado (ya no está en estado "cargando").
//   - Un error de descarga muestra el estado de error con reintentar.
//   - "Descargar" reutiliza el blob ya cargado (no repite la petición).
//   - Ausencia de violaciones WCAG 2.1 A/AA (axe-core, jsdom).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

import { CotizacionPreviewDialog } from './cotizacion-preview-dialog';
import { esperarSinViolaciones } from '../../../../testing/axe';

interface Probe {
  cargando(): boolean;
  error(): string | null;
  pdfUrl(): unknown;
  cargar(): void;
  descargar(): void;
}

describe('CotizacionPreviewDialog', () => {
  let fixture: ComponentFixture<CotizacionPreviewDialog>;
  let http: HttpTestingController;

  const URL_PDF = '/api/v1/cotizaciones/cot-1/pdf';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CotizacionPreviewDialog, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { id: 'cot-1', folio: 'COT-0001' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CotizacionPreviewDialog);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function comp(): Probe {
    return fixture.componentInstance as unknown as Probe;
  }

  /** Blob PDF mínimo de prueba. */
  function pdfBlob(): Blob {
    return new Blob(['%PDF-1.4 test'], { type: 'application/pdf' });
  }

  it('descarga el PDF y muestra el visor (deja de cargar)', () => {
    const req = http.expectOne(URL_PDF);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(pdfBlob());
    fixture.detectChanges();

    expect(comp().cargando()).toBe(false);
    expect(comp().error()).toBeNull();
    expect(comp().pdfUrl()).not.toBeNull();
    // El iframe del visor está presente.
    expect((fixture.nativeElement as HTMLElement).querySelector('.cot-preview__iframe')).not.toBeNull();
  });

  it('ante un error de descarga muestra el estado de error', () => {
    http.expectOne(URL_PDF).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(comp().cargando()).toBe(false);
    expect(comp().error()).not.toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Reintentar');
  });

  it('descargar reutiliza el blob ya cargado sin repetir la petición', () => {
    http.expectOne(URL_PDF).flush(pdfBlob());
    fixture.detectChanges();
    comp().descargar();
    // No debe haber una segunda petición al endpoint del PDF.
    http.expectNone(URL_PDF);
  });

  it('no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async () => {
    http.expectOne(URL_PDF).flush(pdfBlob());
    fixture.detectChanges();
    await esperarSinViolaciones(fixture);
  });
});
