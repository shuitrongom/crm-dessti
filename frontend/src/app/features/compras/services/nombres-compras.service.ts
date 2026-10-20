// =============================================================================
// Servicio de resolución de nombres del módulo Compras (proveedores + materiales)
// -----------------------------------------------------------------------------
// Los DTOs de órdenes de compra, recepciones y facturas traen SOLO identificadores
// (proveedorId, materialId), no los nombres. Para cumplir "sin UUIDs visibles" sin
// tocar el backend, este servicio carga una vez los catálogos de Proveedores
// (ComprasService) y Materiales (MaterialesService), arma mapas id → nombre y
// expone helpers. Un id no resuelto cae a un marcador neutro, nunca al UUID.
// =============================================================================

import { Injectable, inject, signal } from '@angular/core';
import { forkJoin, Observable, map } from 'rxjs';

import { MaterialesService } from '../../operacion/services/inventario.service';
import { Material } from '../../operacion/models/operacion.models';
import { ComprasService } from './compras.service';
import { Proveedor } from '../models/compras.models';

/** Marcador neutro para un identificador que no se pudo resolver a un nombre. */
export const MARCADOR_SIN_NOMBRE = '(sin nombre)';

/** Tamaño de página para la carga inicial de catálogos. */
const TAMANO_CATALOGO = 200;

/**
 * Resuelve identificadores técnicos a nombres legibles para el módulo de compras.
 * Inyectable a nivel raíz; los catálogos se cargan bajo demanda con {@link cargar}.
 */
@Injectable({ providedIn: 'root' })
export class NombresComprasService {
  private readonly compras = inject(ComprasService);
  private readonly materiales = inject(MaterialesService);

  private readonly proveedoresPorId = new Map<string, Proveedor>();
  private readonly materialesPorId = new Map<string, Material>();

  /** Proveedores cargados (activos + inactivos, para resolver históricos). */
  readonly listaProveedores = signal<Proveedor[]>([]);
  /** Materiales cargados. */
  readonly listaMateriales = signal<Material[]>([]);
  /** `true` cuando los catálogos ya se cargaron al menos una vez. */
  readonly cargado = signal(false);

  /**
   * Carga una vez los catálogos de Proveedores (todos, para resolver también los
   * inactivos referidos por órdenes antiguas) y Materiales, y arma los mapas.
   */
  cargar(): Observable<{ proveedores: Proveedor[]; materiales: Material[] }> {
    return forkJoin({
      proveedores: this.compras.listarProveedores(null, 0, TAMANO_CATALOGO, 'todos'),
      materiales: this.materiales.listar(null, 'activo', false, 0, TAMANO_CATALOGO),
    }).pipe(
      map((res) => {
        const proveedores = res.proveedores.content;
        const materiales = res.materiales.content;

        this.proveedoresPorId.clear();
        for (const p of proveedores) {
          this.proveedoresPorId.set(p.id, p);
        }
        this.materialesPorId.clear();
        for (const m of materiales) {
          this.materialesPorId.set(m.id, m);
        }

        this.listaProveedores.set(proveedores);
        this.listaMateriales.set(materiales);
        this.cargado.set(true);
        return { proveedores, materiales };
      }),
    );
  }

  /** Nombre del Proveedor o el marcador neutro; jamás el UUID. */
  nombreProveedor(id: string | null | undefined): string {
    if (!id) {
      return MARCADOR_SIN_NOMBRE;
    }
    return this.proveedoresPorId.get(id)?.nombre ?? MARCADOR_SIN_NOMBRE;
  }

  /** RFC del Proveedor, o cadena vacía si no se resuelve. */
  rfcProveedor(id: string | null | undefined): string {
    if (!id) {
      return '';
    }
    return this.proveedoresPorId.get(id)?.rfc ?? '';
  }

  /** Nombre del Material o el marcador neutro; jamás el UUID. */
  nombreMaterial(id: string | null | undefined): string {
    if (!id) {
      return MARCADOR_SIN_NOMBRE;
    }
    return this.materialesPorId.get(id)?.nombre ?? MARCADOR_SIN_NOMBRE;
  }
}
