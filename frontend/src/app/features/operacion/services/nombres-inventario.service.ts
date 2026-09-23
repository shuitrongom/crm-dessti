// =============================================================================
// Servicio de resolucion de nombres del inventario avanzado (Req 1.2, 2.1)
// -----------------------------------------------------------------------------
// Los DTOs de existencias/movimientos del backend traen SOLO los identificadores
// (almacenId/materialId), NO los nombres. Para cumplir el requisito "sin UUIDs
// visibles" sin tocar el backend probado, este servicio carga UNA VEZ los
// catalogos de Almacenes (InventarioAvanzadoService) y Materiales
// (MaterialesService), arma mapas id -> nombre y expone helpers de resolucion.
// Un id no resuelto (por paginacion o baja) cae a un marcador neutro,
// NUNCA al UUID.
// =============================================================================

import { Injectable, inject, signal } from '@angular/core';
import { forkJoin, Observable, map } from 'rxjs';

import { Almacen, Material } from '../models/operacion.models';
import { InventarioAvanzadoService, MaterialesService } from './inventario.service';

/** Marcador neutro para un identificador que no se pudo resolver a un nombre. */
export const MARCADOR_SIN_NOMBRE = '(sin nombre)';

/** Tamano de pagina para la carga inicial de catalogos (Req 2.1). */
const TAMANO_CATALOGO = 200;

/**
 * Resuelve identificadores tecnicos a nombres legibles para el inventario
 * avanzado. Inyectable a nivel raiz; los catalogos se cargan bajo demanda con
 * {@link cargar} y quedan disponibles via los helpers y las listas expuestas.
 */
@Injectable({ providedIn: 'root' })
export class NombresInventarioService {
  private readonly inventario = inject(InventarioAvanzadoService);
  private readonly materiales = inject(MaterialesService);

  private readonly almacenesPorId = new Map<string, Almacen>();
  private readonly materialesPorId = new Map<string, Material>();

  /** Almacenes cargados (para construir selectores por nombre). */
  readonly almacenes = signal<Almacen[]>([]);
  /** Materiales cargados (para construir selectores por nombre). */
  readonly listaMateriales = signal<Material[]>([]);
  /** `true` cuando los catalogos ya se cargaron al menos una vez. */
  readonly cargado = signal(false);

  /**
   * Carga una vez los catalogos de Almacenes y Materiales y arma los mapas
   * id -> entidad. Emite las listas cargadas.
   */
  cargar(): Observable<{ almacenes: Almacen[]; materiales: Material[] }> {
    return forkJoin({
      almacenes: this.inventario.listarAlmacenes(null, true, 0, TAMANO_CATALOGO),
      materiales: this.materiales.listar(null, false, 0, TAMANO_CATALOGO),
    }).pipe(
      map((res) => {
        const almacenes = res.almacenes.content;
        const materiales = res.materiales.content;

        this.almacenesPorId.clear();
        for (const a of almacenes) {
          this.almacenesPorId.set(a.id, a);
        }
        this.materialesPorId.clear();
        for (const m of materiales) {
          this.materialesPorId.set(m.id, m);
        }

        this.almacenes.set(almacenes);
        this.listaMateriales.set(materiales);
        this.cargado.set(true);
        return { almacenes, materiales };
      }),
    );
  }

  /** Nombre del Almacen o el marcador neutro; jamas el UUID. */
  nombreAlmacen(id: string | null | undefined): string {
    if (!id) {
      return MARCADOR_SIN_NOMBRE;
    }
    return this.almacenesPorId.get(id)?.nombre ?? MARCADOR_SIN_NOMBRE;
  }

  /** Nombre del Material o el marcador neutro; jamas el UUID. */
  nombreMaterial(id: string | null | undefined): string {
    if (!id) {
      return MARCADOR_SIN_NOMBRE;
    }
    return this.materialesPorId.get(id)?.nombre ?? MARCADOR_SIN_NOMBRE;
  }

  /** Almacen cargado por su identificador, o `undefined`. */
  almacen(id: string | null | undefined): Almacen | undefined {
    return id ? this.almacenesPorId.get(id) : undefined;
  }

  /** Material cargado por su identificador, o `undefined`. */
  material(id: string | null | undefined): Material | undefined {
    return id ? this.materialesPorId.get(id) : undefined;
  }
}
