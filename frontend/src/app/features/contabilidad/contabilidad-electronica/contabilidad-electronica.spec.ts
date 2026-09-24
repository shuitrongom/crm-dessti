// =============================================================================
// Pruebas de la vista Contabilidad Electrónica (SAT) — Anexo 24
// -----------------------------------------------------------------------------
// Verifican, con backend HTTP simulado, que la vista renderiza el encabezado y
// que la acción "Vista previa" del catálogo consulta el endpoint y pinta el
// resultado (conteo de cuentas amarradas y advertencia de cuentas sin amarrar).
// =============================================================================

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { ContabilidadElectronica } from './contabilidad-electronica';

const BASE = '/api/v1';

describe('ContabilidadElectronica (vista SAT)', () => {
  let fixture: ComponentFixture<ContabilidadElectronica>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ContabilidadElectronica, NoopAnimationsModule],
      providers: [provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(ContabilidadElectronica);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('renderiza el encabezado de la vista', () => {
    const texto: string = fixture.nativeElement.textContent;
    expect(texto).toContain('Contabilidad Electrónica');
  });

  it('la vista previa del catálogo consulta el endpoint y muestra advertencia', () => {
    const componente = fixture.componentInstance;
    componente.previewCatalogo();

    const req = http.expectOne(`${BASE}/contabilidad/contabilidad-electronica/catalogo/preview`);
    expect(req.request.method).toBe('GET');
    req.flush({ cuentasAmarradas: 2, cuentasSinAmarrar: ['105-01'] });
    fixture.detectChanges();

    const texto: string = fixture.nativeElement.textContent;
    expect(texto).toContain('2');
    expect(texto).toContain('105-01');
  });
});
