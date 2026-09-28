// =============================================================================
// Componente Scorecard — fila de KPIs destacados del negocio (Req 48)
// -----------------------------------------------------------------------------
// Encabeza el dashboard con los 4-6 numeros mas importantes de toda la empresa,
// seleccionados por clave estable de indicador entre todas las areas del
// consolidado. Cada tarjeta destaca el valor, su tendencia frente al periodo
// anterior y un icono representativo. Es robusto: si una clave no viene en la
// respuesta, simplemente se omite (no rompe la fila).
// =============================================================================

import { Component, computed, inject, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';

import { InteligenciaNegocio, Indicador } from '../models/reportes.models';
import { IndicadorInfoDialog, type DatosIndicadorInfo } from '../../../shared/indicadores/indicador-info-dialog';
import { fichaIndicador } from '../../../shared/indicadores/indicadores-catalogo';

/** KPI destacado ya resuelto para la vista. */
interface Destacado {
  clave: string;
  etiqueta: string;
  valor: number;
  unidad: string;
  comparativo: number | null;
  variacion: number | null;
  icono: string;
  /** true si un aumento es "bueno" (verde); false si un aumento es "malo" (p. ej. CxC vencidas). */
  aumentoEsBueno: boolean;
}

/** Definicion de un KPI candidato: clave estable, icono y semantica de color. */
interface DefDestacado {
  clave: string;
  icono: string;
  aumentoEsBueno: boolean;
}

@Component({
  selector: 'app-scorecard',
  imports: [DecimalPipe, MatIconModule],
  templateUrl: './scorecard.html',
  styleUrl: './scorecard.scss',
})
export class Scorecard {
  private readonly dialog = inject(MatDialog);

  /** Consolidado del que se extraen los KPIs destacados. */
  readonly datos = input.required<InteligenciaNegocio>();

  /** Abre el dialogo que explica, en lenguaje de negocio, el indicador elegido. */
  protected abrirInfo(d: Destacado): void {
    const datos: DatosIndicadorInfo = {
      clave: d.clave,
      etiqueta: d.etiqueta,
      valor: d.valor,
      unidad: d.unidad,
      comparativo: d.comparativo,
      variacion: d.variacion,
    };
    this.dialog.open(IndicadorInfoDialog, { data: datos, width: '32rem', maxWidth: '92vw', autoFocus: false });
  }

  /** Claves prioritarias, en orden de aparicion, con su icono y semantica. */
  private readonly definiciones: DefDestacado[] = [
    { clave: 'valor_pipeline_abierto', icono: 'trending_up', aumentoEsBueno: true },
    { clave: 'facturacion_periodo', icono: 'receipt_long', aumentoEsBueno: true },
    { clave: 'saldo_bancario_total', icono: 'account_balance', aumentoEsBueno: true },
    { clave: 'activos_valor_neto_libros', icono: 'savings', aumentoEsBueno: true },
    { clave: 'costo_nomina_periodo', icono: 'groups', aumentoEsBueno: false },
    { clave: 'cxc_vencidas_saldo', icono: 'account_balance_wallet', aumentoEsBueno: false },
    { clave: 'oportunidades_ganadas', icono: 'emoji_events', aumentoEsBueno: true },
    { clave: 'cotizaciones_aprobadas', icono: 'verified', aumentoEsBueno: true },
  ];

  /** KPIs destacados presentes en el consolidado, en el orden de las definiciones. */
  protected readonly destacados = computed<Destacado[]>(() => {
    const indice = this.indexarPorClave(this.datos());
    const salida: Destacado[] = [];
    for (const def of this.definiciones) {
      const ind = indice.get(def.clave);
      if (ind) {
        salida.push({
          clave: ind.clave,
          etiqueta: ind.etiqueta,
          valor: ind.valor,
          unidad: ind.unidad,
          comparativo: ind.comparativo,
          variacion: ind.variacion,
          icono: def.icono,
          aumentoEsBueno: def.aumentoEsBueno,
        });
      }
    }
    return salida;
  });

  /** Indexa todos los indicadores de todas las areas por su clave. */
  private indexarPorClave(datos: InteligenciaNegocio): Map<string, Indicador> {
    const mapa = new Map<string, Indicador>();
    for (const area of datos.areas ?? []) {
      for (const ind of area.indicadores ?? []) {
        if (!mapa.has(ind.clave)) {
          mapa.set(ind.clave, ind);
        }
      }
    }
    return mapa;
  }

  /** Unidad monetaria => prefijo; conteo => vacio. */
  protected esMonetario(d: Destacado): boolean {
    const u = (d.unidad ?? '').toUpperCase();
    return u === 'MXN' || u === 'USD' || u === 'EUR';
  }

  protected unidadVisible(d: Destacado): string {
    return (d.unidad ?? '').toLowerCase() === 'conteo' ? '' : d.unidad;
  }

  /** Porcentaje de variacion; null si no hay comparativo o comparativo cero. */
  protected porcentaje(d: Destacado): number | null {
    if (d.comparativo === null || d.comparativo === undefined || d.comparativo === 0) {
      return null;
    }
    const v = d.variacion ?? d.valor - d.comparativo;
    return (v / Math.abs(d.comparativo)) * 100;
  }

  /** Tono semantico del delta (verde/rojo) segun si el aumento es bueno o malo. */
  protected tono(d: Destacado): 'bueno' | 'malo' | 'neutro' {
    if (d.variacion === null || d.variacion === undefined || d.variacion === 0) {
      return 'neutro';
    }
    const sube = d.variacion > 0;
    return sube === d.aumentoEsBueno ? 'bueno' : 'malo';
  }

  protected iconoDelta(d: Destacado): string {
    if (d.variacion === null || d.variacion === undefined || d.variacion === 0) {
      return 'trending_flat';
    }
    return d.variacion > 0 ? 'trending_up' : 'trending_down';
  }

  /**
   * Tamano de fuente (en rem) del numero, escalado segun cuantos caracteres tiene
   * el valor formateado, para que NUNCA se desborde de la tarjeta. Numeros cortos
   * se ven grandes (1.85rem); numeros largos (millones) bajan progresivamente
   * hasta 1.05rem. Se aplica inline al span del numero.
   */
  protected tamanoNumero(d: Destacado): string {
    // Aproxima la cantidad de digitos del valor mostrado (sin separadores).
    const digitos = Math.round(Math.abs(d.valor)).toString().length;
    let rem: number;
    if (digitos <= 4) {
      rem = 1.85; // hasta miles: tamano completo
    } else if (digitos <= 6) {
      rem = 1.55; // cientos de miles
    } else if (digitos <= 7) {
      rem = 1.3; // millones
    } else if (digitos <= 9) {
      rem = 1.15; // decenas/cientos de millones
    } else {
      rem = 1.0; // miles de millones+
    }
    return `${rem}rem`;
  }

  /** Descripcion corta (una linea) del catalogo central que se muestra en la tarjeta. */
  protected descripcionCorta(d: Destacado): string {
    return fichaIndicador(d.clave, d.unidad).corta;
  }
}
