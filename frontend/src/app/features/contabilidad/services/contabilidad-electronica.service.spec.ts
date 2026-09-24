// =============================================================================
// Pruebas unitarias del ContabilidadElectronicaService (Anexo 24) — mapeo HTTP
// -----------------------------------------------------------------------------
// Verifican, con el pipeline real de HttpClient y backend simulado, que:
//   - Las vistas previa construyen la URL y los params anio/mes.
//   - La descarga de XML usa responseType blob y toma el nombre del header
//     Content-Disposition.
//   - El amarre hace PATCH con el cuerpo esperado.
//   - La búsqueda de códigos agrupadores construye el parámetro q.
// Deterministas, sin zona, con provideHttpClientTesting.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { ContabilidadElectronicaService } from './contabilidad-electronica.service';

const BASE = '/api/v1';

describe('ContabilidadElectronicaService', () => {
  let service: ContabilidadElectronicaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ContabilidadElectronicaService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ContabilidadElectronicaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('vista previa del catálogo hace GET al endpoint', () => {
    service.vistaPreviaCatalogo().subscribe((r) => expect(r.cuentasAmarradas).toBe(3));
    const req = http.expectOne(`${BASE}/contabilidad/contabilidad-electronica/catalogo/preview`);
    expect(req.request.method).toBe('GET');
    req.flush({ cuentasAmarradas: 3, cuentasSinAmarrar: [] });
  });

  it('vista previa de la balanza envía anio y mes', () => {
    service.vistaPreviaBalanza(2026, 9).subscribe((r) => expect(r.cuadra).toBe(true));
    const req = http.expectOne(
      (r) =>
        r.url === `${BASE}/contabilidad/contabilidad-electronica/balanza/preview` &&
        r.params.get('anio') === '2026' &&
        r.params.get('mes') === '9',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ numeroCuentas: 2, totalDebe: 100, totalHaber: 100, cuadra: true });
  });

  it('descarga del catálogo usa blob y toma el nombre del header', () => {
    service.descargarCatalogo(2026, 9).subscribe((archivo) => {
      expect(archivo.nombreArchivo).toBe('XAXX010101000202609CT.xml');
      expect(archivo.blob).toBeInstanceOf(Blob);
    });
    const req = http.expectOne(
      (r) => r.url === `${BASE}/contabilidad/contabilidad-electronica/catalogo/xml`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['<xml/>'], { type: 'application/xml' }), {
      headers: { 'Content-Disposition': 'attachment; filename="XAXX010101000202609CT.xml"' },
    });
  });

  it('amarrar código agrupador hace PATCH con el cuerpo esperado', () => {
    service.amarrarCodigoAgrupador('c1', '101.01').subscribe();
    const req = http.expectOne(`${BASE}/contabilidad/cuentas-contables/c1/codigo-agrupador`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ codigoAgrupadorSat: '101.01' });
    req.flush({});
  });

  it('buscar códigos agrupadores construye el parámetro q', () => {
    service.buscarCodigosAgrupadores('caja').subscribe((r) => expect(r.length).toBe(1));
    const req = http.expectOne(
      (r) =>
        r.url === `${BASE}/contabilidad/codigos-agrupadores-sat` && r.params.get('q') === 'caja',
    );
    expect(req.request.method).toBe('GET');
    req.flush([{ codigo: '101.01', nombre: 'Caja y efectivo', nivel: 2, naturaleza: 'D' }]);
  });
});
