// =============================================================================
// Agrupacion de modulos por Giro para los selectores de checkboxes (Req 25.1)
// -----------------------------------------------------------------------------
// Transforma el catalogo plano de GET /plataforma/modulos en grupos: primero el
// "Nucleo comun" (modulos con giro null) y luego un grupo por Giro. Lo consumen
// el dialogo de Plan y el alta de Empresa para pintar checkboxes agrupados.
// El backend ya devuelve el catalogo ordenado; aqui solo se agrupa preservando
// ese orden.
// =============================================================================

import { ModuloCatalogo } from './plataforma.models';

/** Grupo de modulos bajo un mismo encabezado (Nucleo o un Giro concreto). */
export interface GrupoModulos {
  /** Clave del Giro, o `null` para el grupo de Nucleo comun. */
  giro: string | null;
  /** Titulo visible del grupo. */
  titulo: string;
  /** Modulos del grupo, en el orden recibido del backend. */
  modulos: ModuloCatalogo[];
}

/** Titulo del grupo de modulos transversales (sin Giro). */
export const TITULO_NUCLEO = 'Núcleo común';

/**
 * Humaniza una clave de Giro para usarla como titulo cuando no hay una etiqueta
 * mas rica disponible (guiones/guiones bajos por espacios, capitalizacion).
 *
 * @param giro clave de Giro (p. ej. `anuncios-luminosos`).
 * @returns el titulo humanizado (p. ej. `Anuncios luminosos`).
 */
export function humanizarGiro(giro: string): string {
  const texto = giro.replace(/[-_]/g, ' ').trim();
  if (texto.length === 0) {
    return giro;
  }
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/**
 * Agrupa el catalogo de modulos por Giro. El grupo de Nucleo (giro null) va
 * primero; despues, un grupo por cada Giro en el orden de primera aparicion.
 *
 * @param modulos catalogo plano recibido de GET /plataforma/modulos.
 * @param etiquetasGiro etiquetas opcionales por clave de Giro; si falta una, se
 *                      humaniza la clave.
 * @returns los grupos, con el de Nucleo primero (si tiene modulos).
 */
export function agruparModulosPorGiro(
  modulos: readonly ModuloCatalogo[],
  etiquetasGiro?: ReadonlyMap<string, string>,
): GrupoModulos[] {
  const nucleo: ModuloCatalogo[] = [];
  const porGiro = new Map<string, ModuloCatalogo[]>();

  for (const modulo of modulos) {
    if (modulo.giro === null) {
      nucleo.push(modulo);
    } else {
      const lista = porGiro.get(modulo.giro) ?? [];
      lista.push(modulo);
      porGiro.set(modulo.giro, lista);
    }
  }

  const grupos: GrupoModulos[] = [];
  if (nucleo.length > 0) {
    grupos.push({ giro: null, titulo: TITULO_NUCLEO, modulos: nucleo });
  }
  for (const [giro, lista] of porGiro) {
    grupos.push({
      giro,
      titulo: etiquetasGiro?.get(giro) ?? humanizarGiro(giro),
      modulos: lista,
    });
  }
  return grupos;
}
