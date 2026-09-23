// =============================================================================
// Servicios de inventario: base (Materiales/movimientos) y avanzado por Almacen
// (Req 18, 60)
// -----------------------------------------------------------------------------
// Integran los contratos REST operacion-produccion (MaterialController,
// InventarioAvanzadoController).
//
// NOTA DE INTEGRACION (unica y explicita): el inventario BASE no expone un
// LISTADO de Movimiento_Inventario por Material (solo el alta POST
// /materiales/{id}/movimientos). El historial cronologico (Kardex) de solo
// lectura lo aporta el inventario AVANZADO por Almacen
// (GET .../almacenes/{almacenId}/materiales/{materialId}/kardex). La vista de
// Materiales permite registrar movimientos y mostrar el resultado; el Kardex se
// consulta desde la vista de inventario avanzado. No se simula un listado
// inexistente del inventario base.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  Almacen,
  AlmacenRequest,
  ConfigInventarioMaterial,
  ConfigurarInventarioMaterialRequest,
  CrearLoteRequest,
  ExistenciaAlmacen,
  Lote,
  Material,
  MaterialRequest,
  MovimientoAlmacen,
  MovimientoInventario,
  MovimientoRequest,
  RegistrarEntradaRequest,
  RegistrarSalidaRequest,
  TransferirRequest,
} from '../models/operacion.models';

@Injectable({ providedIn: 'root' })
export class MaterialesService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista Materiales de forma paginada, filtrando por nombre y stock bajo (Req 18.6). */
  listar(
    nombre: string | null,
    stockBajo: boolean,
    page: number,
    size: number,
  ): Observable<PaginaResponse<Material>> {
    let params = new HttpParams().set('page', page).set('size', size).set('stockBajo', stockBajo);
    if (nombre && nombre.trim().length > 0) {
      params = params.set('nombre', nombre.trim());
    }
    return this.http.get<PaginaResponse<Material>>(this.api.url('/materiales'), { params });
  }

  /** Consulta un Material por su identificador (Req 18). */
  consultar(id: string): Observable<Material> {
    return this.http.get<Material>(this.api.url(`/materiales/${id}`));
  }

  /** Da de alta un Material con existencias iniciales 0 (Req 18.1). */
  crear(request: MaterialRequest): Observable<Material> {
    return this.http.post<Material>(this.api.url('/materiales'), request);
  }

  /** Registra un movimiento de inventario sobre un Material (Req 18.2). */
  registrarMovimiento(id: string, request: MovimientoRequest): Observable<MovimientoInventario> {
    return this.http.post<MovimientoInventario>(
      this.api.url(`/materiales/${id}/movimientos`),
      request,
    );
  }

  /** Baja logica de un Material (Req 18). */
  eliminar(id: string): Observable<Material> {
    return this.http.delete<Material>(this.api.url(`/materiales/${id}`));
  }
}

@Injectable({ providedIn: 'root' })
export class InventarioAvanzadoService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista Almacenes de forma paginada, filtrando por nombre y estado (Req 60). */
  listarAlmacenes(
    nombre: string | null,
    activo: boolean | null,
    page: number,
    size: number,
  ): Observable<PaginaResponse<Almacen>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (nombre && nombre.trim().length > 0) {
      params = params.set('nombre', nombre.trim());
    }
    if (activo !== null) {
      params = params.set('activo', activo);
    }
    return this.http.get<PaginaResponse<Almacen>>(this.api.url('/inventario-avanzado/almacenes'), {
      params,
    });
  }

  /** Consulta un Almacen por su identificador (Req 60). */
  consultarAlmacen(id: string): Observable<Almacen> {
    return this.http.get<Almacen>(this.api.url(`/inventario-avanzado/almacenes/${id}`));
  }

  /** Da de alta un Almacen (Req 60). */
  crearAlmacen(request: AlmacenRequest): Observable<Almacen> {
    return this.http.post<Almacen>(this.api.url('/inventario-avanzado/almacenes'), request);
  }

  /** Edita (renombra/reclasifica) un Almacen (Req 60). */
  actualizarAlmacen(id: string, request: AlmacenRequest): Observable<Almacen> {
    return this.http.put<Almacen>(this.api.url(`/inventario-avanzado/almacenes/${id}`), request);
  }

  /** Lista los saldos de existencias por Almacen/Material (Req 60), solo lectura. */
  listarExistencias(
    almacenId: string | null,
    materialId: string | null,
    page: number,
    size: number,
  ): Observable<PaginaResponse<ExistenciaAlmacen>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (almacenId) {
      params = params.set('almacenId', almacenId);
    }
    if (materialId) {
      params = params.set('materialId', materialId);
    }
    return this.http.get<PaginaResponse<ExistenciaAlmacen>>(
      this.api.url('/inventario-avanzado/existencias'),
      { params },
    );
  }

  /** Consulta el Kardex cronologico de un Material en un Almacen (Req 60), solo lectura. */
  consultarKardex(
    almacenId: string,
    materialId: string,
    page: number,
    size: number,
  ): Observable<PaginaResponse<MovimientoAlmacen>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PaginaResponse<MovimientoAlmacen>>(
      this.api.url(`/inventario-avanzado/almacenes/${almacenId}/materiales/${materialId}/kardex`),
      { params },
    );
  }

  /**
   * Registra una ENTRADA de inventario en un Almacen con costeo (Req 60.5, 60.10,
   * 60.11). El costo lo calcula el backend a partir del costo unitario enviado.
   */
  registrarEntrada(
    almacenId: string,
    body: RegistrarEntradaRequest,
  ): Observable<MovimientoAlmacen> {
    return this.http.post<MovimientoAlmacen>(
      this.api.url(`/inventario-avanzado/almacenes/${almacenId}/entradas`),
      body,
    );
  }

  /**
   * Registra una SALIDA de inventario de un Almacen (Req 60.5, 60.10, 60.11). El
   * costo NO se envia: lo determina el metodo de costeo configurado del Material.
   * Una salida que excede el saldo se rechaza con 422 ("existencias insuficientes").
   */
  registrarSalida(almacenId: string, body: RegistrarSalidaRequest): Observable<MovimientoAlmacen> {
    return this.http.post<MovimientoAlmacen>(
      this.api.url(`/inventario-avanzado/almacenes/${almacenId}/salidas`),
      body,
    );
  }

  /**
   * Transfiere una cantidad de un Material entre dos Almacenes (Req 60.13):
   * salida en el origen y entrada por la misma cantidad en el destino,
   * conservando el costo. Devuelve la pata de ENTRADA en el destino.
   */
  transferir(body: TransferirRequest): Observable<MovimientoAlmacen> {
    return this.http.post<MovimientoAlmacen>(
      this.api.url('/inventario-avanzado/transferencias'),
      body,
    );
  }

  /**
   * Configura (upsert) el inventario avanzado de un Material (Req 60). No existe
   * un GET dedicado de configuracion en el backend: la UI edita a partir de
   * valores por defecto y del DTO que devuelve este PUT (no se inventa endpoint).
   */
  configurarInventarioMaterial(
    materialId: string,
    body: ConfigurarInventarioMaterialRequest,
  ): Observable<ConfigInventarioMaterial> {
    return this.http.put<ConfigInventarioMaterial>(
      this.api.url(`/inventario-avanzado/materiales/${materialId}/config-inventario`),
      body,
    );
  }

  /** Da de alta un Lote de un Material (Req 60.4). El codigo es unico por Material. */
  crearLote(materialId: string, body: CrearLoteRequest): Observable<Lote> {
    return this.http.post<Lote>(
      this.api.url(`/inventario-avanzado/materiales/${materialId}/lotes`),
      body,
    );
  }

  /** Lista los Lotes de un Material de forma paginada (Req 60.4). */
  listarLotes(materialId: string, page: number, size: number): Observable<PaginaResponse<Lote>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PaginaResponse<Lote>>(
      this.api.url(`/inventario-avanzado/materiales/${materialId}/lotes`),
      { params },
    );
  }
}
