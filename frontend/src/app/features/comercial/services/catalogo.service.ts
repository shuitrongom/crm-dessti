// =============================================================================
// Servicios de catalogo comercial: Productos, Listas de precios y Canales de venta
// (Req 59, 63)
// -----------------------------------------------------------------------------
// Integran los contratos REST comercial-crm (ProductoController,
// ListaPreciosController, CanalVentaController). Cada servicio expone su CRUD
// paginado; las acciones sensibles (baja) se gobiernan por permiso en la vista.
// =============================================================================

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiConfigService } from '../../../core/services/api-config.service';
import { PaginaResponse } from '../../../core/models/pagina-response';
import {
  AsignarPrecioRequest,
  CanalVenta,
  CanalVentaRequest,
  ListaPrecios,
  ListaPreciosRequest,
  PrecioProducto,
  PrecioLista,
  PrecioSugerido,
  Producto,
  ProductoRequest,
} from '../models/comercial.models';

/** Construye los HttpParams comunes (filtro + paginacion). */
function paramsFiltro(filtro: string | null, page: number, size: number): HttpParams {
  let params = new HttpParams().set('page', page).set('size', size);
  if (filtro && filtro.trim().length > 0) {
    params = params.set('filtro', filtro.trim());
  }
  return params;
}

@Injectable({ providedIn: 'root' })
export class ProductosService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /**
   * Lista Productos de forma paginada, filtrando por nombre y por estado (Req 59.6,
   * 59.7). `estado`: 'activo' (por defecto), 'inactivo' o 'todos'. Poder listar
   * inactivos habilita reactivarlos desde la interfaz.
   */
  listar(
    filtro: string | null,
    page: number,
    size: number,
    estado: 'activo' | 'inactivo' | 'todos' = 'activo',
  ): Observable<PaginaResponse<Producto>> {
    let params = paramsFiltro(filtro, page, size);
    params = params.set('estado', estado);
    return this.http.get<PaginaResponse<Producto>>(this.api.url('/productos'), {
      params,
    });
  }

  /** Consulta un Producto por su identificador (Req 59). */
  consultar(id: string): Observable<Producto> {
    return this.http.get<Producto>(this.api.url(`/productos/${id}`));
  }

  /** Da de alta un Producto (Req 59.1). */
  crear(request: ProductoRequest): Observable<Producto> {
    return this.http.post<Producto>(this.api.url('/productos'), request);
  }

  /** Actualiza un Producto activo (Req 59). */
  actualizar(id: string, request: ProductoRequest): Observable<Producto> {
    return this.http.put<Producto>(this.api.url(`/productos/${id}`), request);
  }

  /** Baja logica de un Producto (Req 59.6). */
  eliminar(id: string): Observable<Producto> {
    return this.http.delete<Producto>(this.api.url(`/productos/${id}`));
  }

  /** Reactiva un Producto dado de baja (Req 59.6). */
  activar(id: string): Observable<Producto> {
    return this.http.put<Producto>(this.api.url(`/productos/${id}/activar`), {});
  }

  /**
   * Consulta el precio sugerido (de lista) vigente de un Producto (Req 59.4, 59.9)
   * para previsualizarlo al armar una Cotizacion, sin crearla. Reutiliza la regla
   * de seleccion del backend. Si ninguna lista vigente aplica, la respuesta trae
   * `disponible: false` y `precioSugerido: null`.
   *
   * @param id       identificador del Producto.
   * @param segmento segmento del Cliente para preferir su lista; opcional.
   * @param fecha    fecha de referencia (ISO `yyyy-MM-dd`); opcional, por defecto hoy.
   */
  precioSugerido(
    id: string,
    segmento?: string | null,
    fecha?: string | null,
  ): Observable<PrecioSugerido> {
    let params = new HttpParams();
    if (segmento && segmento.trim().length > 0) {
      params = params.set('segmento', segmento.trim());
    }
    if (fecha && fecha.trim().length > 0) {
      params = params.set('fecha', fecha.trim());
    }
    return this.http.get<PrecioSugerido>(this.api.url(`/productos/${id}/precio-sugerido`), {
      params,
    });
  }
}

@Injectable({ providedIn: 'root' })
export class ListasPreciosService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /** Lista Listas de precios activas de forma paginada, filtrando por nombre (Req 59.7). */
  listar(
    filtro: string | null,
    page: number,
    size: number,
  ): Observable<PaginaResponse<ListaPrecios>> {
    return this.http.get<PaginaResponse<ListaPrecios>>(this.api.url('/listas-precios'), {
      params: paramsFiltro(filtro, page, size),
    });
  }

  /** Consulta una Lista de precios por su identificador (Req 59.3). */
  consultar(id: string): Observable<ListaPrecios> {
    return this.http.get<ListaPrecios>(this.api.url(`/listas-precios/${id}`));
  }

  /** Define una Lista de precios (Req 59.3, 59.9). */
  definir(request: ListaPreciosRequest): Observable<ListaPrecios> {
    return this.http.post<ListaPrecios>(this.api.url('/listas-precios'), request);
  }

  /** Actualiza una Lista de precios activa (Req 59.3). */
  actualizar(id: string, request: ListaPreciosRequest): Observable<ListaPrecios> {
    return this.http.put<ListaPrecios>(this.api.url(`/listas-precios/${id}`), request);
  }

  /** Baja logica de una Lista de precios (Req 59). */
  eliminar(id: string): Observable<ListaPrecios> {
    return this.http.delete<ListaPrecios>(this.api.url(`/listas-precios/${id}`));
  }

  /** Asigna (o actualiza) el precio de un Producto en la lista (Req 59.3, 59.10). */
  asignarPrecio(id: string, request: AsignarPrecioRequest): Observable<PrecioProducto> {
    return this.http.put<PrecioProducto>(this.api.url(`/listas-precios/${id}/precios`), request);
  }

  /** Lista los precios asignados en una lista (producto + precio) para mostrarlos. */
  listarPrecios(id: string): Observable<PrecioLista[]> {
    return this.http.get<PrecioLista[]>(this.api.url(`/listas-precios/${id}/precios`));
  }
}

@Injectable({ providedIn: 'root' })
export class CanalesVentaService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiConfigService);

  /**
   * Lista Canales de venta de forma paginada, filtrando por nombre y por estado
   * (Req 63.1). `estado`: 'activo' (por defecto), 'inactivo' o 'todos'. Poder
   * listar inactivos habilita reactivarlos desde la interfaz.
   */
  listar(
    filtro: string | null,
    page: number,
    size: number,
    estado: 'activo' | 'inactivo' | 'todos' = 'activo',
  ): Observable<PaginaResponse<CanalVenta>> {
    let params = paramsFiltro(filtro, page, size);
    params = params.set('estado', estado);
    return this.http.get<PaginaResponse<CanalVenta>>(this.api.url('/canales-venta'), {
      params,
    });
  }

  /** Consulta un Canal de venta por su identificador (Req 63.1). */
  consultar(id: string): Observable<CanalVenta> {
    return this.http.get<CanalVenta>(this.api.url(`/canales-venta/${id}`));
  }

  /** Da de alta un Canal de venta (Req 63.1). */
  crear(request: CanalVentaRequest): Observable<CanalVenta> {
    return this.http.post<CanalVenta>(this.api.url('/canales-venta'), request);
  }

  /** Actualiza un Canal de venta activo (Req 63.1). */
  actualizar(id: string, request: CanalVentaRequest): Observable<CanalVenta> {
    return this.http.put<CanalVenta>(this.api.url(`/canales-venta/${id}`), request);
  }

  /** Baja logica de un Canal de venta (Req 63.1). */
  eliminar(id: string): Observable<CanalVenta> {
    return this.http.delete<CanalVenta>(this.api.url(`/canales-venta/${id}`));
  }

  /** Reactiva un Canal de venta dado de baja (Req 63.1). PUT /{id}/activar. */
  activar(id: string): Observable<CanalVenta> {
    return this.http.put<CanalVenta>(this.api.url(`/canales-venta/${id}/activar`), {});
  }
}
