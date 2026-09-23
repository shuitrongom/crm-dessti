/**
 * Derivación **pura y determinista** de una paleta de marca a partir de un
 * único color primario (`#RRGGBB`) y un modo de tema (`light` | `dark`).
 *
 * A partir del Color_Primario_Marca este módulo calcula, sin acceso al DOM y
 * sin efectos secundarios, una {@link PaletaDerivada} coherente (primario,
 * hover, container, textos, anillo de foco y superficies de acento),
 * **garantizando contraste WCAG 2.1 AA** en los pares texto/superficie y AA de
 * componentes (≥ 3:1) en el anillo de foco.
 *
 * Reutiliza las utilidades puras de {@link module:color-utils}
 * (`esHexValido`, `luminanciaRelativa`, `relacionContraste`, `mezclar`); este
 * módulo **no** reimplementa cálculos de color.
 *
 * La derivación es determinista: la misma entrada produce siempre la misma
 * salida, lo que la hace idónea para *property-based testing* del contraste,
 * la completitud estructural y la idempotencia.
 *
 * @see https://www.w3.org/TR/WCAG21/#contrast-minimum  (AA texto ≥ 4.5:1)
 * @see https://www.w3.org/TR/WCAG21/#non-text-contrast  (AA componentes ≥ 3:1)
 * @module derivar-paleta
 */

import {
  esHexValido,
  luminanciaRelativa,
  mezclar,
  relacionContraste,
} from './color-utils';

/**
 * Paleta derivada de un único color primario de marca. Todos los campos son
 * colores hexadecimales de 6 dígitos (`#rrggbb`, minúsculas).
 */
export interface PaletaDerivada {
  /** Color primario efectivo (posiblemente ajustado por accesibilidad, paso 6). */
  primary: string;
  /** Variante para estado *hover* del primario (más oscura en claro, más clara en oscuro). */
  primaryHover: string;
  /** Superficie tenue basada en el primario, para contenedores destacados. */
  primaryContainer: string;
  /** Texto (`#ffffff` o `#000000`) que cumple AA (≥ 4.5:1) sobre `primary`. */
  textOnPrimary: string;
  /** Texto (`#ffffff` o `#000000`) que cumple AA (≥ 4.5:1) sobre `primaryContainer`. */
  textOnContainer: string;
  /** Anillo de foco derivado del primario, con ≥ 3:1 contra la superficie del modo. */
  focusRing: string;
  /** Superficie de acento muy tenue, apta como fondo de realce. */
  accentSurface: string;
  /** Texto del modo que cumple AA (≥ 4.5:1) sobre `accentSurface`. */
  textOnAccentSurface: string;
}

/** Blanco puro; extremo de luminancia usado para texto claro y mezclas. */
const BLANCO = '#ffffff';
/** Negro puro; extremo de luminancia usado para texto oscuro y mezclas. */
const NEGRO = '#000000';

/** Umbral WCAG 2.1 AA para texto normal (relación de contraste mínima). */
const AA_TEXTO = 4.5;
/** Umbral WCAG 2.1 AA para componentes de interfaz no textuales. */
const AA_COMPONENTE = 3;

/**
 * Superficies base de cada modo, tomadas de los tokens de diseño
 * (`frontend/src/styles/_tokens.scss`):
 *
 * - Claro:  `--ds-color-background: #f5f6f8`, `--ds-color-surface: #ffffff`.
 * - Oscuro: `--ds-color-background: #14171c`, `--ds-color-surface: #1c2027`.
 *
 * `background` es el lienzo del modo; `surface` es la superficie de tarjetas y
 * es la referencia usada para el contraste del anillo de foco (paso 7) y el
 * destino de las mezclas de container (paso 5) y de acento (paso 8). Mantener
 * estas constantes sincronizadas con `_tokens.scss`.
 */
const SUPERFICIES = {
  light: { background: '#f5f6f8', surface: '#ffffff' },
  dark: { background: '#14171c', surface: '#1c2027' },
} as const;

/** Factor de mezcla del hover (12% hacia negro/blanco según el modo). */
const FACTOR_HOVER = 0.12;
/** Factor de mezcla del container (85% hacia la superficie del modo). */
const FACTOR_CONTAINER = 0.85;
/** Factor de mezcla de la superficie de acento (90% hacia la superficie del modo). */
const FACTOR_ACENTO = 0.9;
/** Incremento por paso al buscar un valor accesible mediante mezcla iterativa. */
const PASO_AJUSTE = 0.05;
/** Cota superior de iteraciones de ajuste (t ∈ [0, 1] en pasos de {@link PASO_AJUSTE}). */
const MAX_PASOS = Math.round(1 / PASO_AJUSTE);

/**
 * Elige, entre `#ffffff` y `#000000`, el color que ofrece **mayor** relación de
 * contraste sobre `fondo`. Usado para decidir el texto sobre una superficie.
 *
 * @param fondo Color de fondo `#rrggbb`.
 * @returns `#ffffff` o `#000000`, el de mayor contraste con `fondo`.
 */
function mejorTextoBN(fondo: string): string {
  return relacionContraste(BLANCO, fondo) >= relacionContraste(NEGRO, fondo)
    ? BLANCO
    : NEGRO;
}

/**
 * Ajusta iterativamente `primary` mezclándolo hacia un extremo (`objetivo`)
 * hasta que el mejor de blanco/negro alcance el umbral AA de texto sobre él.
 *
 * Implementa el **paso 6** del algoritmo (respaldo de contraste para primarios
 * de luminancia intermedia): en modo claro se oscurece (mezcla hacia negro), en
 * modo oscuro se aclara (mezcla hacia blanco), en pasos de {@link PASO_AJUSTE}.
 * Devuelve el primer valor que cumple AA; si ninguno lo hace (caso teórico,
 * pues los extremos negro/blanco superan AA con creces), devuelve el extremo.
 *
 * @param primary Color primario de partida (`#rrggbb`).
 * @param objetivo Extremo hacia el que mezclar (`#000000` en claro, `#ffffff` en oscuro).
 * @returns Color primario accesible cuyo mejor texto B/N cumple ≥ 4.5:1.
 */
function ajustarPrimarioAccesible(primary: string, objetivo: string): string {
  let candidato = primary;
  for (let paso = 0; paso <= MAX_PASOS; paso++) {
    const t = paso * PASO_AJUSTE;
    candidato = mezclar(primary, objetivo, t);
    const mejorContraste = Math.max(
      relacionContraste(BLANCO, candidato),
      relacionContraste(NEGRO, candidato),
    );
    if (mejorContraste >= AA_TEXTO) {
      return candidato;
    }
  }
  // Extremo alcanzado: negro/blanco puro siempre supera AA sobre sí mismo.
  return candidato;
}

/**
 * Ajusta iterativamente `color` mezclándolo hacia un extremo hasta alcanzar el
 * `umbral` de contraste indicado contra una `superficie` de referencia.
 *
 * Reutilizado por el **paso 7** (anillo de foco, umbral de componente 3:1): en
 * modo claro se oscurece el color (mezcla hacia negro) y en modo oscuro se
 * aclara (mezcla hacia blanco), de modo que destaque sobre la superficie.
 * Devuelve el primer valor que cumple el umbral; si ninguno lo hace, devuelve
 * el último candidato (el extremo, que maximiza el contraste posible).
 *
 * @param color Color de partida (`#rrggbb`).
 * @param objetivo Extremo hacia el que mezclar.
 * @param superficie Superficie contra la que se mide el contraste.
 * @param umbral Relación de contraste mínima requerida.
 * @returns Color ajustado que alcanza `umbral` contra `superficie` si es posible.
 */
function ajustarContraContraste(
  color: string,
  objetivo: string,
  superficie: string,
  umbral: number,
): string {
  let candidato = color;
  for (let paso = 0; paso <= MAX_PASOS; paso++) {
    const t = paso * PASO_AJUSTE;
    candidato = mezclar(color, objetivo, t);
    if (relacionContraste(candidato, superficie) >= umbral) {
      return candidato;
    }
  }
  return candidato;
}

/**
 * Deriva una {@link PaletaDerivada} accesible a partir de un color primario y
 * un modo de tema, siguiendo los pasos 1–8 del algoritmo del documento de
 * diseño.
 *
 * **Contrato de entrada:** la capa llamante (p. ej. la vista de branding o
 * `TematizacionService`) valida el color con `esHexValido` **antes** de invocar
 * esta función; si es inválido, no debe llamarla. Como red de seguridad y para
 * mantener el carácter puro/no-lanzante coherente con `color-utils`, aquí se
 * normaliza defensivamente (sin lanzar): una entrada inválida se trata como
 * `#000000` a través de las utilidades subyacentes.
 *
 * Pasos del algoritmo:
 * 1. **Validación de formato** con `esHexValido` (contrato del llamante;
 *    normalización defensiva a minúsculas aquí).
 * 2. **`primary`** = color de entrada (normalizado); puede ajustarse en el paso 6.
 * 3. **`textOnPrimary`** = mejor de `#ffffff`/`#000000` por contraste; ≥ 4.5:1 AA.
 * 4. **`primaryHover`** = `mezclar(primary, negro, 0.12)` en claro; hacia blanco en oscuro.
 * 5. **`primaryContainer`** = `mezclar(primary, superficie, 0.85)` del modo;
 *    **`textOnContainer`** = B/N que cumpla AA (≥ 4.5:1) sobre el container.
 * 6. **Respaldo de contraste**: si ni blanco ni negro alcanzan 4.5:1 sobre el
 *    primario (luminancia intermedia), se oscurece (claro) o aclara (oscuro) el
 *    primario por pasos hasta lograr AA, y se recalcula `textOnPrimary`.
 * 7. **`focusRing`** = primario ajustado para ≥ 3:1 contra la superficie del modo.
 * 8. **`accentSurface`** = variante muy tenue (mezcla fuerte a la superficie);
 *    **`textOnAccentSurface`** = B/N que cumpla AA (≥ 4.5:1) sobre esa superficie.
 *
 * @param colorPrimario Color primario de marca `#RRGGBB`.
 * @param modo Modo de tema activo: `'light'` u `'dark'`.
 * @returns Paleta derivada, coherente y accesible, con todos los campos `#rrggbb`.
 */
export function derivarPaleta(
  colorPrimario: string,
  modo: 'light' | 'dark',
): PaletaDerivada {
  // Paso 1: contrato de validación del llamante; normalización defensiva.
  // `mezclar(color, color, 0)` reutiliza la normalización de color-utils
  // (minúsculas + respaldo si el formato fuese inválido), sin reimplementarla.
  const entradaNormalizada = esHexValido(colorPrimario)
    ? colorPrimario.toLowerCase()
    : mezclar(colorPrimario, colorPrimario, 0);

  const { surface } = SUPERFICIES[modo];
  // Extremo hacia el que se oscurece (claro) o aclara (oscuro).
  const extremoModo = modo === 'light' ? NEGRO : BLANCO;

  // Paso 2 (+ paso 6): primario efectivo, garantizando texto B/N accesible.
  let primary = entradaNormalizada;
  const mejorContrasteEntrada = Math.max(
    relacionContraste(BLANCO, primary),
    relacionContraste(NEGRO, primary),
  );
  if (mejorContrasteEntrada < AA_TEXTO) {
    // Paso 6: luminancia intermedia; ajustar el primario hasta lograr AA.
    primary = ajustarPrimarioAccesible(primary, extremoModo);
  }

  // Paso 3: texto sobre el primario (mejor de blanco/negro), recalculado sobre
  // el primario ya posiblemente ajustado en el paso 6.
  const textOnPrimary = mejorTextoBN(primary);

  // Paso 4: hover (oscurece en claro, aclara en oscuro).
  const primaryHover = mezclar(primary, extremoModo, FACTOR_HOVER);

  // Paso 5: container (mezcla suave hacia la superficie del modo) y su texto.
  const primaryContainer = mezclar(primary, surface, FACTOR_CONTAINER);
  const textOnContainer = mejorTextoBN(primaryContainer);

  // Paso 7: anillo de foco con ≥ 3:1 contra la superficie del modo.
  const focusRing =
    relacionContraste(primary, surface) >= AA_COMPONENTE
      ? primary
      : ajustarContraContraste(primary, extremoModo, surface, AA_COMPONENTE);

  // Paso 8: superficie de acento muy tenue y su texto accesible.
  const accentSurface = mezclar(primary, surface, FACTOR_ACENTO);
  const textOnAccentSurface = mejorTextoBN(accentSurface);

  return {
    primary,
    primaryHover,
    primaryContainer,
    textOnPrimary,
    textOnContainer,
    focusRing,
    accentSurface,
    textOnAccentSurface,
  };
}
