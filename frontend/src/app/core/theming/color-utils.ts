/**
 * Utilidades de color **puras** para la derivación de paletas de marca.
 *
 * Todas las funciones de este módulo son deterministas y libres de efectos
 * secundarios: no acceden al DOM (`document`, `window`), no leen ni escriben
 * señales, y no dependen de estado externo. La misma entrada produce siempre
 * la misma salida, lo que las hace idóneas para *property-based testing*.
 *
 * Los cálculos de luminancia y contraste implementan la fórmula de las
 * Web Content Accessibility Guidelines (WCAG) 2.1:
 * @see https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 * @see https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio
 *
 * @module color-utils
 */

/** Expresión regular canónica para un color hexadecimal de 6 dígitos (`#RRGGBB`). */
const REGEX_HEX_6 = /^#[0-9a-fA-F]{6}$/;

/**
 * Color de respaldo usado como comportamiento defensivo cuando una función que
 * requiere un color válido recibe una entrada que no cumple `#RRGGBB`.
 *
 * Se eligió negro (`#000000`) por ser un extremo de luminancia bien definido y
 * determinista: garantiza que las funciones nunca lancen ni produzcan `NaN`
 * ante datos corruptos, y deja un valor visible y predecible. La capa llamante
 * (por ejemplo la vista de branding) valida con {@link esHexValido} antes de
 * invocar estas utilidades, de modo que en el flujo normal este respaldo no se
 * ejerce; existe solo como red de seguridad.
 */
const COLOR_RESPALDO = '#000000';

/**
 * Indica si una cadena es un color hexadecimal de 6 dígitos válido.
 *
 * Acepta la cadena **si y solo si** coincide con `^#[0-9a-fA-F]{6}$`, es decir,
 * un `#` seguido de exactamente seis dígitos hexadecimales (mayúsculas o
 * minúsculas). No acepta la forma corta de 3 dígitos ni valores con canal alfa.
 *
 * @param valor Cadena a validar.
 * @returns `true` si `valor` es un `#RRGGBB` válido; `false` en cualquier otro caso.
 */
export function esHexValido(valor: string): boolean {
  return REGEX_HEX_6.test(valor);
}

/**
 * Normaliza un color a `#rrggbb` en minúsculas.
 *
 * Si el color no es válido según {@link esHexValido}, aplica el comportamiento
 * defensivo devolviendo {@link COLOR_RESPALDO}. Función interna auxiliar.
 *
 * @param color Color de entrada.
 * @returns Color normalizado a minúsculas, o el color de respaldo.
 */
function normalizar(color: string): string {
  return esHexValido(color) ? color.toLowerCase() : COLOR_RESPALDO;
}

/**
 * Extrae los tres canales RGB de un `#RRGGBB` como enteros en el rango [0, 255].
 *
 * Asume que `color` ya fue normalizado con {@link normalizar}.
 *
 * @param color Color válido `#rrggbb` en minúsculas.
 * @returns Tupla `[r, g, b]` con valores enteros [0..255].
 */
function canales(color: string): [number, number, number] {
  const r = parseInt(color.slice(1, 3), 16);
  const g = parseInt(color.slice(3, 5), 16);
  const b = parseInt(color.slice(5, 7), 16);
  return [r, g, b];
}

/**
 * Lineariza un canal sRGB al espacio lineal según WCAG 2.1.
 *
 * Convierte un canal en [0, 1] aplicando la corrección gamma inversa de sRGB:
 * `c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ^ 2.4`.
 *
 * @see https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 * @param canal Valor del canal normalizado en [0, 1].
 * @returns Valor lineal del canal en [0, 1].
 */
function linealizar(canal: number): number {
  return canal <= 0.03928 ? canal / 12.92 : Math.pow((canal + 0.055) / 1.055, 2.4);
}

/**
 * Calcula la **luminancia relativa** de un color según WCAG 2.1.
 *
 * Parsea `#RRGGBB` a canales sRGB en [0, 1], lineariza cada canal (ver
 * {@link linealizar}) y combina los canales lineales con los coeficientes de
 * la recomendación: `0.2126 * R + 0.7152 * G + 0.0722 * B`. El resultado está
 * en el rango [0, 1] (0 = negro, 1 = blanco).
 *
 * Comportamiento defensivo: si `color` no es un hex válido, se usa el color de
 * respaldo ({@link COLOR_RESPALDO}), evitando `NaN`.
 *
 * @see https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 * @param color Color `#RRGGBB` (mayúsculas o minúsculas).
 * @returns Luminancia relativa en [0, 1].
 */
export function luminanciaRelativa(color: string): number {
  const [r, g, b] = canales(normalizar(color));
  const rLineal = linealizar(r / 255);
  const gLineal = linealizar(g / 255);
  const bLineal = linealizar(b / 255);
  return 0.2126 * rLineal + 0.7152 * gLineal + 0.0722 * bLineal;
}

/**
 * Calcula la **relación de contraste** entre dos colores según WCAG 2.1.
 *
 * Aplica la fórmula `(L1 + 0.05) / (L2 + 0.05)`, donde `L1` es la mayor
 * luminancia relativa y `L2` la menor de los dos colores. El resultado está en
 * el rango [1, 21] (1 = sin contraste, 21 = negro contra blanco).
 *
 * El orden de los argumentos es irrelevante: `relacionContraste(a, b)` es igual
 * a `relacionContraste(b, a)`.
 *
 * Comportamiento defensivo: un color inválido se trata como el color de
 * respaldo (ver {@link luminanciaRelativa}).
 *
 * @see https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio
 * @param a Primer color `#RRGGBB`.
 * @param b Segundo color `#RRGGBB`.
 * @returns Relación de contraste en [1, 21].
 */
export function relacionContraste(a: string, b: string): number {
  const la = luminanciaRelativa(a);
  const lb = luminanciaRelativa(b);
  const mayor = Math.max(la, lb);
  const menor = Math.min(la, lb);
  return (mayor + 0.05) / (menor + 0.05);
}

/**
 * Convierte un componente de canal [0, 255] a dos dígitos hexadecimales.
 *
 * Redondea al entero más cercano, acota al rango [0, 255] y aplica *padding*
 * con cero a la izquierda. Función interna auxiliar.
 *
 * @param valor Valor del canal (puede ser fraccionario tras interpolar).
 * @returns Dos dígitos hexadecimales en minúsculas (`00`..`ff`).
 */
function aDosHex(valor: number): string {
  const entero = Math.min(255, Math.max(0, Math.round(valor)));
  return entero.toString(16).padStart(2, '0');
}

/**
 * Interpola linealmente dos colores en el espacio RGB.
 *
 * Mezcla canal por canal entre el color `a` (cuando `t = 0`) y el color `b`
 * (cuando `t = 1`) usando `canal = a + (b - a) * t`. El parámetro `t` se acota
 * ("clamp") al rango [0, 1]: valores fuera de rango se tratan como el extremo
 * más cercano. El resultado se devuelve como `#rrggbb` en minúsculas, con dos
 * dígitos por canal y *padding* de cero.
 *
 * Comportamiento defensivo: cualquier color inválido se sustituye por el color
 * de respaldo ({@link COLOR_RESPALDO}) antes de interpolar.
 *
 * @param a Color inicial `#RRGGBB` (resultado cuando `t = 0`).
 * @param b Color final `#RRGGBB` (resultado cuando `t = 1`).
 * @param t Factor de interpolación; se acota a [0, 1].
 * @returns Color mezclado en formato `#rrggbb` (minúsculas).
 */
export function mezclar(a: string, b: string, t: number): string {
  const tAcotado = Math.min(1, Math.max(0, t));
  const [ra, ga, ba] = canales(normalizar(a));
  const [rb, gb, bb] = canales(normalizar(b));
  const r = ra + (rb - ra) * tAcotado;
  const g = ga + (gb - ga) * tAcotado;
  const bAzul = ba + (bb - ba) * tAcotado;
  return `#${aDosHex(r)}${aDosHex(g)}${aDosHex(bAzul)}`;
}
