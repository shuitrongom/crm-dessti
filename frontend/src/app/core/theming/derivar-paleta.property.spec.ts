// =============================================================================
// Pruebas de propiedad de la derivación pura de paleta de marca.
// -----------------------------------------------------------------------------
// Cubre las propiedades P1, P2, P3, P4 y P8 del documento de diseño del spec
// `tematizacion-empresa-enterprise` (sección Correctness Properties).
//
// Stack de pruebas: Vitest (NO Karma/Jasmine). `fast-check` NO está instalado y
// NO se agrega. Se sigue el patrón de property testing LIGERO ya usado en el
// proyecto (ver `core/navigation/navigation.property.spec.ts`): un generador
// determinista con semilla fija (PRNG mulberry32) produce ≥100 colores hex
// aleatorios y se recorren ambos modos ('light' y 'dark'), complementando con
// casos borde fijos. Cada propiedad se verifica con un único `describe`/`it`.
// =============================================================================

import { describe, it, expect } from 'vitest';

import { derivarPaleta, type PaletaDerivada } from './derivar-paleta';
import { esHexValido, relacionContraste } from './color-utils';

// -----------------------------------------------------------------------------
// PRNG determinista (mulberry32) para reproducibilidad, mismo patrón que la
// propiedad de navegación. Semilla fija => secuencia estable entre corridas.
// -----------------------------------------------------------------------------

/** Generador pseudoaleatorio determinista sembrado, para reproducibilidad. */
function mulberry32(semilla: number): () => number {
  let a = semilla >>> 0;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Genera `cantidad` colores `#RRGGBB` aleatorios pero deterministas a partir de
 * `semilla`. Cada color se compone de tres canales [0, 255] en minúsculas, de
 * modo que todos cumplen `esHexValido`. Es un generador "hecho a mano"
 * equivalente al `hexaString`/mapeo de fast-check, sin dependencias nuevas.
 */
function generarHexAleatorios(cantidad: number, semilla: number): string[] {
  const rnd = mulberry32(semilla);
  const dosHex = (v: number) =>
    Math.floor(v * 256)
      .toString(16)
      .padStart(2, '0');
  const colores: string[] = [];
  for (let i = 0; i < cantidad; i++) {
    colores.push(`#${dosHex(rnd())}${dosHex(rnd())}${dosHex(rnd())}`);
  }
  return colores;
}

/** Casos borde fijos que conviene ejercer explícitamente (extremos y medios). */
const CASOS_BORDE: readonly string[] = [
  '#000000', // negro
  '#ffffff', // blanco
  '#ff0000', // rojo puro
  '#00ff00', // verde puro
  '#0000ff', // azul puro
  '#808080', // gris medio (luminancia intermedia)
  '#7f7f7f', // gris medio-bajo
  '#123456', // arbitrario oscuro
  '#abcdef', // arbitrario claro
  '#ffff00', // amarillo (luminancia alta intermedia)
];

/** Los dos modos de tema a recorrer en cada propiedad. */
const MODOS: readonly ('light' | 'dark')[] = ['light', 'dark'];

/**
 * Superficies base del modo, tomadas de `frontend/src/styles/_tokens.scss`
 * (claro `--ds-color-surface: #ffffff`; oscuro `--ds-color-surface: #1c2027`).
 * Son la referencia contra la que se mide el contraste del anillo de foco (P2).
 */
const SUPERFICIE_MODO: Record<'light' | 'dark', string> = {
  light: '#ffffff',
  dark: '#1c2027',
};

/** Umbral WCAG 2.1 AA para texto normal. */
const AA_TEXTO = 4.5;
/** Umbral WCAG 2.1 AA para componentes de interfaz no textuales. */
const AA_COMPONENTE = 3;
/** Tolerancia numérica para comparaciones de contraste (redondeo de mezcla). */
const EPS = 1e-9;

/** Conjunto de colores a ejercer: ≥100 aleatorios + casos borde, por propiedad. */
function corpusColores(semilla: number, cantidad = 120): string[] {
  return [...CASOS_BORDE, ...generarHexAleatorios(cantidad, semilla)];
}

/** Campos requeridos de la paleta (P3). */
const CAMPOS_PALETA: readonly (keyof PaletaDerivada)[] = [
  'primary',
  'primaryHover',
  'primaryContainer',
  'textOnPrimary',
  'textOnContainer',
  'focusRing',
  'accentSurface',
  'textOnAccentSurface',
];

// -----------------------------------------------------------------------------
// Property 1 (tarea 1.3): Contraste AA del texto sobre el primario.
// -----------------------------------------------------------------------------
describe('derivarPaleta — Property 1: contraste AA texto sobre primario', () => {
  // Feature: tematizacion-empresa-enterprise, Property 1: Contraste AA del texto sobre el primario
  it('para ≥100 colores y ambos modos, relacionContraste(textOnPrimary, primary) >= 4.5', () => {
    const colores = corpusColores(0x1111_1111);
    let iteraciones = 0;
    for (const color of colores) {
      for (const modo of MODOS) {
        const paleta = derivarPaleta(color, modo);
        const contraste = relacionContraste(paleta.textOnPrimary, paleta.primary);
        expect(
          contraste + EPS >= AA_TEXTO,
          `textOnPrimary/primary AA falla: color=${color} modo=${modo} ` +
            `primary=${paleta.primary} textOnPrimary=${paleta.textOnPrimary} contraste=${contraste}`,
        ).toBe(true);
        iteraciones++;
      }
    }
    expect(iteraciones).toBeGreaterThanOrEqual(100);
  });
});

// -----------------------------------------------------------------------------
// Property 2 (tarea 1.4): AA de todos los pares texto/superficie derivados.
// -----------------------------------------------------------------------------
describe('derivarPaleta — Property 2: AA de todos los pares texto/superficie derivados', () => {
  // Feature: tematizacion-empresa-enterprise, Property 2: Contraste AA de todos los pares texto/superficie derivados
  it('textOnContainer/primaryContainer y textOnAccentSurface/accentSurface >= 4.5; focusRing/superficie >= 3.0', () => {
    const colores = corpusColores(0x2222_2222);
    let iteraciones = 0;
    for (const color of colores) {
      for (const modo of MODOS) {
        const paleta = derivarPaleta(color, modo);

        const contrasteContainer = relacionContraste(
          paleta.textOnContainer,
          paleta.primaryContainer,
        );
        expect(
          contrasteContainer + EPS >= AA_TEXTO,
          `textOnContainer/primaryContainer AA falla: color=${color} modo=${modo} ` +
            `container=${paleta.primaryContainer} texto=${paleta.textOnContainer} contraste=${contrasteContainer}`,
        ).toBe(true);

        const contrasteAcento = relacionContraste(
          paleta.textOnAccentSurface,
          paleta.accentSurface,
        );
        expect(
          contrasteAcento + EPS >= AA_TEXTO,
          `textOnAccentSurface/accentSurface AA falla: color=${color} modo=${modo} ` +
            `accentSurface=${paleta.accentSurface} texto=${paleta.textOnAccentSurface} contraste=${contrasteAcento}`,
        ).toBe(true);

        const contrasteFoco = relacionContraste(paleta.focusRing, SUPERFICIE_MODO[modo]);
        expect(
          contrasteFoco + EPS >= AA_COMPONENTE,
          `focusRing/superficie AA-componente falla: color=${color} modo=${modo} ` +
            `focusRing=${paleta.focusRing} superficie=${SUPERFICIE_MODO[modo]} contraste=${contrasteFoco}`,
        ).toBe(true);

        iteraciones++;
      }
    }
    expect(iteraciones).toBeGreaterThanOrEqual(100);
  });
});

// -----------------------------------------------------------------------------
// Property 3 (tarea 1.5): Completitud estructural de la paleta.
// -----------------------------------------------------------------------------
describe('derivarPaleta — Property 3: completitud estructural de la paleta', () => {
  // Feature: tematizacion-empresa-enterprise, Property 3: Completitud estructural de la paleta
  it('la paleta tiene los 8 campos y cada uno cumple esHexValido', () => {
    const colores = corpusColores(0x3333_3333);
    let iteraciones = 0;
    for (const color of colores) {
      for (const modo of MODOS) {
        const paleta = derivarPaleta(color, modo);
        for (const campo of CAMPOS_PALETA) {
          const valor = paleta[campo];
          expect(
            typeof valor === 'string' && esHexValido(valor),
            `campo inválido: color=${color} modo=${modo} campo=${campo} valor=${String(valor)}`,
          ).toBe(true);
        }
        // Confirma que no hay campos extra ni faltantes respecto a los 8 esperados.
        expect(Object.keys(paleta).sort()).toEqual([...CAMPOS_PALETA].sort());
        iteraciones++;
      }
    }
    expect(iteraciones).toBeGreaterThanOrEqual(100);
  });
});

// -----------------------------------------------------------------------------
// Property 4 (tarea 1.6): Determinismo de la derivación.
// -----------------------------------------------------------------------------
describe('derivarPaleta — Property 4: determinismo de la derivación', () => {
  // Feature: tematizacion-empresa-enterprise, Property 4: Determinismo de la derivación
  it('derivarPaleta(c, modo) llamada dos veces da objetos con todos los campos idénticos', () => {
    const colores = corpusColores(0x4444_4444);
    let iteraciones = 0;
    for (const color of colores) {
      for (const modo of MODOS) {
        const primera = derivarPaleta(color, modo);
        const segunda = derivarPaleta(color, modo);
        for (const campo of CAMPOS_PALETA) {
          expect(
            segunda[campo],
            `no determinista: color=${color} modo=${modo} campo=${campo} ` +
              `primera=${primera[campo]} segunda=${segunda[campo]}`,
          ).toBe(primera[campo]);
        }
        iteraciones++;
      }
    }
    expect(iteraciones).toBeGreaterThanOrEqual(100);
  });
});

// -----------------------------------------------------------------------------
// Property 8 (tarea 1.7): Validación de formato hexadecimal (frontend).
// -----------------------------------------------------------------------------
describe('color-utils — Property 8: validación de formato hexadecimal', () => {
  // Feature: tematizacion-empresa-enterprise, Property 8: Validación de formato hexadecimal
  it('esHexValido acepta una cadena sii coincide con ^#[0-9a-fA-F]{6}$', () => {
    // Regex de referencia independiente (misma semántica que el contrato), para
    // comparar el resultado de esHexValido contra el oráculo esperado.
    const referencia = /^#[0-9a-fA-F]{6}$/;

    // Conjunto fijo de cadenas válidas e inválidas conocidas.
    const casosFijos: readonly string[] = [
      // válidas
      '#000000',
      '#ffffff',
      '#FFFFFF',
      '#AbCdEf',
      '#123abc',
      '#0f0f0f',
      // inválidas
      '',
      '#',
      '000000', // sin '#'
      '#fff', // forma corta de 3 dígitos
      '#fffffff', // 7 dígitos
      '#fffff', // 5 dígitos
      '#12345g', // dígito no hex
      '#12 456', // espacio
      ' #123456', // espacio inicial
      '#123456 ', // espacio final
      '#123456\n', // salto de línea (^$ multilínea no debe colar)
      '#12345', // corto
      'rgb(0,0,0)',
      '#gggggg',
      '##123456',
      '#1234567', // 7 dígitos
    ];

    // Generación de cadenas casi-válidas mezclando dígitos hex y caracteres
    // arbitrarios, con longitudes variables, todas deterministas por semilla.
    const rnd = mulberry32(0x8888_8888);
    const alfabeto = '0123456789abcdefABCDEF#gxyzGXYZ !/\t';
    const generadas: string[] = [];
    for (let i = 0; i < 200; i++) {
      const longitud = Math.floor(rnd() * 9); // 0..8 caracteres
      let s = '';
      for (let j = 0; j < longitud; j++) {
        s += alfabeto[Math.floor(rnd() * alfabeto.length)];
      }
      generadas.push(s);
    }
    // Además, hex válidos generados (deben aceptarse siempre).
    const validosGenerados = generarHexAleatorios(120, 0x8888_9999);

    const todas = [...casosFijos, ...generadas, ...validosGenerados];
    let iteraciones = 0;
    for (const s of todas) {
      const esperado = referencia.test(s);
      expect(
        esHexValido(s),
        `esHexValido discrepa del oráculo para ${JSON.stringify(s)}: esperado=${esperado}`,
      ).toBe(esperado);
      iteraciones++;
    }
    expect(iteraciones).toBeGreaterThanOrEqual(100);
  });
});
