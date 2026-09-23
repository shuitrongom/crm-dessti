// =============================================================================
// Servicio de resolucion de nombres de operacion/produccion (Req 10.1-10.5)
// -----------------------------------------------------------------------------
// Los DTOs de operacion (Orden de Fabricacion, OTI, Levantamiento, etc.) traen
// SOLO identificadores (clienteId, cotizacionId, ordenFabricacionId, sitioId,
// cuadrillaId, materialId), NO los nombres legibles. Para cumplir el requisito
// "sin UUIDs visibles" sin tocar el backend probado, este servicio carga UNA VEZ
// los catalogos necesarios (paginas <= 200), arma mapas id -> nombre y expone
// helpers de resolucion mas las listas cargadas para alimentar selectores por
// nombre (app-entity-select). Un id no resuelto (por paginacion o baja) cae a un
// marcador neutro, NUNCA al UUID.
//
// Analogo a NombresInventarioService. Reutiliza MaterialesService para el
// catalogo de Materiales del Nucleo (sin duplicar).
//
// FUENTES REALES POR CATALOGO (verificadas en el codigo del proyecto):
//   - Cliente     -> ClientesService.listar        (GET /clientes)
//   - Cotizacion  -> CotizacionesService.listar     (GET /cotizaciones); etiqueta = folio
//   - Orden Fab.  -> ProduccionService.listar        (GET /ordenes-fabricacion);
//                    la OF no tiene nombre propio: se compone una etiqueta legible
//                    con su origen (directa / desde cotizacion) y el Cliente.
//   - Sitio       -> ProyectosService.listar          (GET /proyectos): el Sitio NO
//                    tiene endpoint de listado propio; los Sitios se obtienen
//                    aplanando los Proyectos cargados (proyecto.sitios[].sitio).
//   - Material    -> MaterialesService.listar          (GET /materiales) [reutilizado]
//
// CATALOGO SIN SERVICIO DE LISTADO ACCESIBLE (documentado, NO se inventa ruta):
//   - Cuadrilla: en el backend `cuadrilla_id` es una referencia debil (sin FK) y
//     NO existe tabla `cuadrilla` ni endpoint REST de listado (ver
//     `vertical/anuncios/instalacion/package-info.java`). Por eso `nombreCuadrilla`
//     devuelve el marcador neutro y `cuadrillas()` es una lista vacia hasta que el
//     backend publique el catalogo. No se simula un listado inexistente.
// =============================================================================

import { Injectable, inject, signal } from '@angular/core';
import { forkJoin, Observable, map } from 'rxjs';

import { Cliente, Cotizacion } from '../../comercial/models/comercial.models';
import { ClientesService } from '../../comercial/services/clientes.service';
import { CotizacionesService } from '../../comercial/services/cotizaciones.service';
import { ETIQUETA_ESTADO_OF, Material, OrdenFabricacion, Proyecto, Sitio } from '../models/operacion.models';
import { MaterialesService } from './inventario.service';
import { ProduccionService } from './produccion.service';
import { ProyectosService } from './proyectos.service';

/** Marcador neutro para un identificador que no se pudo resolver a un nombre. */
export const MARCADOR_SIN_NOMBRE = '(sin nombre)';

/** Tamano de pagina para la carga inicial de catalogos (Req 10.1). */
const TAMANO_CATALOGO = 200;

/**
 * Resuelve identificadores tecnicos a nombres legibles para el modulo de
 * operacion/produccion. Inyectable a nivel raiz; los catalogos se cargan bajo
 * demanda con {@link cargar} y quedan disponibles via los helpers y las listas
 * expuestas.
 */
@Injectable({ providedIn: 'root' })
export class NombresOperacionService {
  private readonly clientesService = inject(ClientesService);
  private readonly cotizacionesService = inject(CotizacionesService);
  private readonly produccionService = inject(ProduccionService);
  private readonly proyectosService = inject(ProyectosService);
  private readonly materialesService = inject(MaterialesService);

  private readonly clientesPorId = new Map<string, Cliente>();
  private readonly cotizacionesPorId = new Map<string, Cotizacion>();
  private readonly ordenesFabricacionPorId = new Map<string, OrdenFabricacion>();
  private readonly sitiosPorId = new Map<string, Sitio>();
  private readonly materialesPorId = new Map<string, Material>();

  /** Clientes cargados (para construir selectores por nombre). */
  readonly clientes = signal<Cliente[]>([]);
  /** Cotizaciones cargadas (para construir selectores por nombre). */
  readonly cotizaciones = signal<Cotizacion[]>([]);
  /** Ordenes de Fabricacion cargadas (para construir selectores por nombre). */
  readonly ordenesFabricacion = signal<OrdenFabricacion[]>([]);
  /** Sitios cargados (aplanados desde los Proyectos), para selectores por nombre. */
  readonly sitios = signal<Sitio[]>([]);
  /** Materiales cargados (para construir selectores por nombre). */
  readonly materiales = signal<Material[]>([]);
  /**
   * Cuadrillas cargadas. Vacio: el backend no expone un catalogo de Cuadrillas
   * (referencia debil, sin tabla ni endpoint). Se conserva para uniformidad con
   * el resto de selectores y para cuando el catalogo exista.
   */
  readonly cuadrillas = signal<never[]>([]);
  /** `true` cuando los catalogos ya se cargaron al menos una vez. */
  readonly cargado = signal(false);

  /**
   * Carga una vez los catalogos necesarios (paginas <= 200) y arma los mapas
   * id -> entidad. Emite las listas cargadas. Reutiliza MaterialesService para
   * Materiales (sin duplicar el catalogo del Nucleo).
   */
  cargar(): Observable<{
    clientes: Cliente[];
    cotizaciones: Cotizacion[];
    ordenesFabricacion: OrdenFabricacion[];
    sitios: Sitio[];
    materiales: Material[];
  }> {
    return forkJoin({
      clientes: this.clientesService.listar(null, 0, TAMANO_CATALOGO),
      cotizaciones: this.cotizacionesService.listar({}, 0, TAMANO_CATALOGO),
      ordenesFabricacion: this.produccionService.listar(null, 0, TAMANO_CATALOGO),
      proyectos: this.proyectosService.listar(null, 0, TAMANO_CATALOGO),
      materiales: this.materialesService.listar(null, false, 0, TAMANO_CATALOGO),
    }).pipe(
      map((res) => {
        const clientes = res.clientes.content;
        const cotizaciones = res.cotizaciones.content;
        const ordenesFabricacion = res.ordenesFabricacion.content;
        const materiales = res.materiales.content;
        // El Sitio no tiene listado propio: se aplana desde los Proyectos.
        const sitios = this.aplanarSitios(res.proyectos.content);

        this.clientesPorId.clear();
        for (const c of clientes) {
          this.clientesPorId.set(c.id, c);
        }
        this.cotizacionesPorId.clear();
        for (const q of cotizaciones) {
          this.cotizacionesPorId.set(q.id, q);
        }
        this.ordenesFabricacionPorId.clear();
        for (const o of ordenesFabricacion) {
          this.ordenesFabricacionPorId.set(o.id, o);
        }
        this.sitiosPorId.clear();
        for (const s of sitios) {
          this.sitiosPorId.set(s.id, s);
        }
        this.materialesPorId.clear();
        for (const m of materiales) {
          this.materialesPorId.set(m.id, m);
        }

        this.clientes.set(clientes);
        this.cotizaciones.set(cotizaciones);
        this.ordenesFabricacion.set(ordenesFabricacion);
        this.sitios.set(sitios);
        this.materiales.set(materiales);
        this.cargado.set(true);
        return { clientes, cotizaciones, ordenesFabricacion, sitios, materiales };
      }),
    );
  }

  /** Nombre del Cliente o el marcador neutro; jamas el UUID. */
  nombreCliente(id: string | null | undefined): string {
    if (!id) {
      return MARCADOR_SIN_NOMBRE;
    }
    return this.clientesPorId.get(id)?.nombre ?? MARCADOR_SIN_NOMBRE;
  }

  /**
   * Etiqueta legible de la Cotizacion (su folio) o el marcador neutro; jamas el
   * UUID. Una Cotizacion sin folio cae al marcador.
   */
  nombreCotizacion(id: string | null | undefined): string {
    if (!id) {
      return MARCADOR_SIN_NOMBRE;
    }
    const folio = this.cotizacionesPorId.get(id)?.folio;
    return folio && folio.trim().length > 0 ? folio : MARCADOR_SIN_NOMBRE;
  }

  /**
   * Etiqueta legible de la Orden de Fabricacion o el marcador neutro; jamas el
   * UUID. La OF no tiene nombre propio: se compone con su origen (directa / desde
   * cotizacion), el Cliente y el estado, todo con nombres legibles.
   */
  nombreOrdenFabricacion(id: string | null | undefined): string {
    if (!id) {
      return MARCADOR_SIN_NOMBRE;
    }
    const of = this.ordenesFabricacionPorId.get(id);
    if (!of) {
      return MARCADOR_SIN_NOMBRE;
    }
    const origen = of.cotizacionId
      ? `desde ${this.nombreCotizacion(of.cotizacionId)}`
      : 'directa';
    const estado = ETIQUETA_ESTADO_OF[of.estado] ?? MARCADOR_SIN_NOMBRE;
    return `OF ${this.nombreCliente(of.clienteId)} — ${origen} (${estado})`;
  }

  /** Nombre del Sitio o el marcador neutro; jamas el UUID. */
  nombreSitio(id: string | null | undefined): string {
    if (!id) {
      return MARCADOR_SIN_NOMBRE;
    }
    return this.sitiosPorId.get(id)?.nombre ?? MARCADOR_SIN_NOMBRE;
  }

  /**
   * Nombre de la Cuadrilla o el marcador neutro; jamas el UUID. El backend no
   * expone un catalogo de Cuadrillas (referencia debil, sin tabla ni endpoint),
   * por lo que hoy siempre cae al marcador neutro.
   */
  nombreCuadrilla(_id: string | null | undefined): string {
    return MARCADOR_SIN_NOMBRE;
  }

  /** Nombre del Material o el marcador neutro; jamas el UUID. */
  nombreMaterial(id: string | null | undefined): string {
    if (!id) {
      return MARCADOR_SIN_NOMBRE;
    }
    return this.materialesPorId.get(id)?.nombre ?? MARCADOR_SIN_NOMBRE;
  }

  /** Cliente cargado por su identificador, o `undefined`. */
  cliente(id: string | null | undefined): Cliente | undefined {
    return id ? this.clientesPorId.get(id) : undefined;
  }

  /** Cotizacion cargada por su identificador, o `undefined`. */
  cotizacion(id: string | null | undefined): Cotizacion | undefined {
    return id ? this.cotizacionesPorId.get(id) : undefined;
  }

  /** Orden de Fabricacion cargada por su identificador, o `undefined`. */
  ordenFabricacion(id: string | null | undefined): OrdenFabricacion | undefined {
    return id ? this.ordenesFabricacionPorId.get(id) : undefined;
  }

  /** Sitio cargado por su identificador, o `undefined`. */
  sitio(id: string | null | undefined): Sitio | undefined {
    return id ? this.sitiosPorId.get(id) : undefined;
  }

  /** Material cargado por su identificador, o `undefined`. */
  material(id: string | null | undefined): Material | undefined {
    return id ? this.materialesPorId.get(id) : undefined;
  }

  /** Aplana los Sitios de una pagina de Proyectos, sin duplicados por id. */
  private aplanarSitios(proyectos: Proyecto[]): Sitio[] {
    const porId = new Map<string, Sitio>();
    for (const proyecto of proyectos) {
      for (const avance of proyecto.sitios ?? []) {
        const sitio = avance.sitio;
        if (sitio && !porId.has(sitio.id)) {
          porId.set(sitio.id, sitio);
        }
      }
    }
    return [...porId.values()];
  }
}
