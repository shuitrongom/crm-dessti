// =============================================================================
// Prueba de propiedad del contraste AA de los tokens corporativos refinados.
// -----------------------------------------------------------------------------
// Cubre la propiedad P10 del documento de diseño del spec
// `tematizacion-empresa-enterprise` (sección Correctness Properties, Req 7.6):
// tras el refinamiento de tokens, todo par texto/superficie corporativo
// ENUMERADO cumple su umbral WCAG 2.1 AA (≥ 4.5:1 texto normal; ≥ 3:1 para
// componentes de interfaz no textuales, como el anillo de foco).
//
// Stack de pruebas: Vitest (NO Karma/Jasmine). `fast-check` NO está instalado y
// NO se usa. El espacio de entrada de esta propiedad es un CONJUNTO FINITO de
// pares (los tokens corporativos definidos en `_tokens.scss`), por lo que el
// "for any" se satisface con un RECORRIDO EXHAUSTIVO de la tabla — no requiere
// aleatoriedad. Es lógica pura sobre constantes de color, así que NO se usa
// TestBed ni `document`: solo se invoca `relacionContraste` sobre los valores.
//
// IMPORTANTE: los valores hex de esta tabla se replican de
// `frontend/src/styles/_tokens.scss` (no importable desde TypeScript). Si
// cambian los valores en _tokens.scss, actualizar esta tabla para que la
// prueba siga reflejando los tokens reales.
// =============================================================================

import { describe, it, expect } from 'vitest';

import { relacionContraste } from './color-utils';

/** Umbral WCAG 2.1 AA para texto normal. */
const AA_TEXTO = 4.5;
/** Umbral WCAG 2.1 AA para componentes de interfaz no textuales. */
const AA_COMPONENTE = 3;
/** Tolerancia numérica para comparaciones de contraste (redondeo/coma flotante). */
const EPS = 1e-9;

/** Un par texto/superficie corporativo con su umbral AA a verificar. */
interface ParContraste {
  /** Etiqueta legible del par (para diagnóstico si falla). */
  readonly nombre: string;
  /** Modo de tema al que pertenece el par. */
  readonly modo: 'claro' | 'oscuro';
  /** Color de primer plano (texto o componente). */
  readonly frente: string;
  /** Color de fondo/superficie sobre el que se dibuja `frente`. */
  readonly fondo: string;
  /** Umbral mínimo de contraste que debe cumplir el par. */
  readonly umbral: number;
}

// -----------------------------------------------------------------------------
// Tabla ENUMERADA de pares corporativos, replicada de _tokens.scss.
// Si cambian los valores en _tokens.scss, actualizar esta tabla.
// -----------------------------------------------------------------------------
const PARES: readonly ParContraste[] = [
  // --- Tema claro: pares texto/superficie (≥ 4.5) ---
  { nombre: 'text sobre background', modo: 'claro', frente: '#1c1f24', fondo: '#f5f6f8', umbral: AA_TEXTO },
  { nombre: 'text sobre surface', modo: 'claro', frente: '#1c1f24', fondo: '#ffffff', umbral: AA_TEXTO },
  { nombre: 'text sobre surface-variant', modo: 'claro', frente: '#1c1f24', fondo: '#eceef1', umbral: AA_TEXTO },
  { nombre: 'text-muted sobre surface', modo: 'claro', frente: '#565c66', fondo: '#ffffff', umbral: AA_TEXTO },
  { nombre: 'text-on-primary sobre primary', modo: 'claro', frente: '#ffffff', fondo: '#35507a', umbral: AA_TEXTO },
  { nombre: 'text-on-primary-container sobre primary-container', modo: 'claro', frente: '#1c2f4a', fondo: '#dbe4f3', umbral: AA_TEXTO },
  { nombre: 'text-on-accent-surface sobre accent-surface', modo: 'claro', frente: '#1c1f24', fondo: '#eef2f8', umbral: AA_TEXTO },

  // --- Tema oscuro: pares texto/superficie (≥ 4.5) ---
  { nombre: 'text sobre background', modo: 'oscuro', frente: '#e8eaed', fondo: '#14171c', umbral: AA_TEXTO },
  { nombre: 'text sobre surface', modo: 'oscuro', frente: '#e8eaed', fondo: '#1c2027', umbral: AA_TEXTO },
  { nombre: 'text-muted sobre surface', modo: 'oscuro', frente: '#a6adb8', fondo: '#1c2027', umbral: AA_TEXTO },
  { nombre: 'text-on-primary sobre primary', modo: 'oscuro', frente: '#0b1220', fondo: '#9db8e6', umbral: AA_TEXTO },
  { nombre: 'text-on-primary-container sobre primary-container', modo: 'oscuro', frente: '#dbe4f3', fondo: '#2b3d5c', umbral: AA_TEXTO },
  { nombre: 'text-on-accent-surface sobre accent-surface', modo: 'oscuro', frente: '#e8eaed', fondo: '#232c3a', umbral: AA_TEXTO },

  // --- Componentes de interfaz no textuales: anillo de foco (≥ 3.0) ---
  { nombre: 'focus-ring sobre surface', modo: 'claro', frente: '#35507a', fondo: '#ffffff', umbral: AA_COMPONENTE },
  { nombre: 'focus-ring sobre surface', modo: 'oscuro', frente: '#9db8e6', fondo: '#1c2027', umbral: AA_COMPONENTE },
];

// -----------------------------------------------------------------------------
// Property 10 (tarea 10.5): Contraste AA de los tokens corporativos refinados.
// -----------------------------------------------------------------------------
describe('tokens corporativos — Property 10: contraste AA de los tokens refinados', () => {
  // Feature: tematizacion-empresa-enterprise, Property 10: Contraste AA de los tokens corporativos refinados
  it('todo par texto/superficie corporativo enumerado cumple su umbral AA (claro y oscuro)', () => {
    // Conjunto finito => recorrido exhaustivo de la tabla (no requiere aleatoriedad).
    // Se acumulan las fallas para reportar TODOS los pares que no cumplen su
    // umbral (junto con el valor real), en vez de detenerse en la primera.
    const fallas: string[] = [];

    for (const par of PARES) {
      const contraste = relacionContraste(par.frente, par.fondo);
      if (!(contraste + EPS >= par.umbral)) {
        fallas.push(
          `[${par.modo}] ${par.nombre}: frente=${par.frente} fondo=${par.fondo} ` +
            `contraste=${contraste.toFixed(3)} < umbral=${par.umbral}`,
        );
      }
    }

    expect(
      fallas.length === 0,
      fallas.length === 0
        ? ''
        : `Pares corporativos que NO cumplen su umbral AA (posible token mal ajustado):\n${fallas.join('\n')}`,
    ).toBe(true);

    // Sanidad: la tabla enumera los 15 pares corporativos esperados.
    expect(PARES.length).toBe(15);
  });
});
