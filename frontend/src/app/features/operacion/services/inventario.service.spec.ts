// =============================================================================
// Pruebas unitarias de InventarioAvanzadoService (Req 60, tareas 1.2/1.3)
// -----------------------------------------------------------------------------
// Verifican de forma determinista y sin zona el mapeo HTTP del servicio contra
// el contrato REST del backend (InventarioAvanzadoController) usando
// provideHttpClientTesting: URL, verbo, parametros de paginacion y cuerpo de
// cada operacion nueva (entrada/salida/transferencia/config/lote). No arrancan
// la aplicacion completa. Los nombres de campo espejan EXACTAMENTE los records
// del backend (loteCodigo, no loteId; codigo/fechaCaducidad en lotes).
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { InventarioAvanzadoService } from './inventario.service';
import {
  ConfigInventarioMaterial,
  ConfigurarInventarioMaterialRequest,
  CrearLoteRequest,
  Lote,
  MovimientoAlmacen,
  RegistrarEntradaRequest,
  RegistrarSalidaRequest,
  TransferirRequest,
} from '../models/operacion.models';

const ALMACEN_ID = '11111111-1111-1111-1111-111111111111';
const ALMACEN_DESTINO_ID = '22222222-2222-2222-2222-222222222222';
const MATERIAL_ID = '33333333-3333-3333-3333-333333333333';

/** Construye una fila de Kardex minima pero valida para los flush. */
function movimientoFalso(): MovimientoAlmacen {
  return {
    id: '44444444-4444-4444-4444-444444444444',
    almacenId: ALMACEN_ID,
    materialId: MATERIAL_ID,
    loteId: null,
    tipo: 'entrada',
    cantidad: 5,
    costoUnitario: 10,
    costoTotal: 50,
    saldoCantidad: 5,
    saldoCostoTotal: 50,
    transferenciaId: null,
    motivo: null,
    version: 0,
    createdAt: '2024-01-01T00:00:00Z',
  };
}

describe('InventarioAvanzadoService', () => {
  let service: InventarioAvanzadoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    });
    service = TestBed.inject(InventarioAvanzadoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listarAlmacenes arma la URL con page, size y filtro de estado activo', () => {
    service.listarAlmacenes(null, true, 0, 200).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/inventario-avanzado/almacenes');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('200');
    expect(req.request.params.get('activo')).toBe('true');
    req.flush({ content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 });
  });

  it('registrarEntrada envia POST .../almacenes/{id}/entradas con el cuerpo', () => {
    const body: RegistrarEntradaRequest = {
      materialId: MATERIAL_ID,
      loteCodigo: 'L-001',
      cantidad: 5,
      costoUnitario: 10,
      motivo: 'compra',
    };
    let recibido: MovimientoAlmacen | undefined;
    service.registrarEntrada(ALMACEN_ID, body).subscribe((m) => (recibido = m));

    const req = http.expectOne(`/api/v1/inventario-avanzado/almacenes/${ALMACEN_ID}/entradas`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);

    const esperado = movimientoFalso();
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('registrarSalida envia POST .../almacenes/{id}/salidas sin costo (loteCodigo)', () => {
    const body: RegistrarSalidaRequest = {
      materialId: MATERIAL_ID,
      loteCodigo: 'L-001',
      cantidad: 2,
      motivo: 'consumo',
    };
    service.registrarSalida(ALMACEN_ID, body).subscribe();

    const req = http.expectOne(`/api/v1/inventario-avanzado/almacenes/${ALMACEN_ID}/salidas`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    // La salida NO envia costo unitario: el backend lo determina.
    expect(req.request.body.costoUnitario).toBeUndefined();
    req.flush({ ...movimientoFalso(), tipo: 'salida' });
  });

  it('transferir envia POST /inventario-avanzado/transferencias con origen y destino', () => {
    const body: TransferirRequest = {
      almacenOrigenId: ALMACEN_ID,
      almacenDestinoId: ALMACEN_DESTINO_ID,
      materialId: MATERIAL_ID,
      cantidad: 3,
      motivo: 'reubicacion',
    };
    service.transferir(body).subscribe();

    const req = http.expectOne('/api/v1/inventario-avanzado/transferencias');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ ...movimientoFalso(), tipo: 'transferencia_entrada' });
  });

  it('configurarInventarioMaterial envia PUT .../config-inventario con el cuerpo', () => {
    const body: ConfigurarInventarioMaterialRequest = {
      metodoCosteo: 'peps',
      stockMaximo: 100,
      controlLote: true,
      consumoPromedio: 2,
      tiempoEntregaDias: 5,
      stockSeguridad: 10,
    };
    let recibido: ConfigInventarioMaterial | undefined;
    service.configurarInventarioMaterial(MATERIAL_ID, body).subscribe((c) => (recibido = c));

    const req = http.expectOne(
      `/api/v1/inventario-avanzado/materiales/${MATERIAL_ID}/config-inventario`,
    );
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);

    const esperado: ConfigInventarioMaterial = {
      id: '55555555-5555-5555-5555-555555555555',
      materialId: MATERIAL_ID,
      metodoCosteo: 'peps',
      stockMaximo: 100,
      controlLote: true,
      consumoPromedio: 2,
      tiempoEntregaDias: 5,
      stockSeguridad: 10,
      puntoReorden: 20,
      version: 0,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
    };
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('crearLote envia POST .../materiales/{id}/lotes con codigo y fechaCaducidad', () => {
    const body: CrearLoteRequest = { codigo: 'L-001', fechaCaducidad: '2025-12-31' };
    let recibido: Lote | undefined;
    service.crearLote(MATERIAL_ID, body).subscribe((l) => (recibido = l));

    const req = http.expectOne(`/api/v1/inventario-avanzado/materiales/${MATERIAL_ID}/lotes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);

    const esperado: Lote = {
      id: '66666666-6666-6666-6666-666666666666',
      materialId: MATERIAL_ID,
      codigo: 'L-001',
      fechaCaducidad: '2025-12-31',
      version: 0,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
    };
    req.flush(esperado);
    expect(recibido).toEqual(esperado);
  });

  it('listarLotes envia GET .../materiales/{id}/lotes con page y size', () => {
    service.listarLotes(MATERIAL_ID, 1, 50).subscribe();
    const req = http.expectOne(
      (r) => r.url === `/api/v1/inventario-avanzado/materiales/${MATERIAL_ID}/lotes`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ content: [], page: 1, size: 50, totalElements: 0, totalPages: 0 });
  });
});
