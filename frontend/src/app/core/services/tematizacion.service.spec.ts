// =============================================================================
// Pruebas de TematizacionService — aplicación de tokens de marca en runtime.
// -----------------------------------------------------------------------------
// Cubre las tareas 2.2 (Property 5), 2.3 (Property 6) y 2.4 (unit tests de modo
// y no-color) del spec `tematizacion-empresa-enterprise`.
//
// Enfoque property-based:
//   El proyecto usa Vitest y NO tiene `fast-check` disponible (no se agrega).
//   Por ello las propiedades se verifican con un generador "hecho a mano":
//   colores `#RRGGBB` deterministas producidos por un PRNG sembrado
//   (mulberry32), con ≥ 100 iteraciones para reproducibilidad, siguiendo el
//   patrón de `core/navigation/navigation.property.spec.ts`.
//
// Aislamiento del DOM:
//   El servicio escribe/borra los 8 tokens `--ds-color-*` en
//   `document.documentElement.style`. Cada test limpia esos 8 tokens en el
//   `afterEach` (via `removeProperty`) para no filtrar estado entre pruebas.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { PaletaDerivada, derivarPaleta } from '../theming/derivar-paleta';
import { TematizacionService } from './tematizacion.service';
import { ThemeService } from './theme.service';

// -----------------------------------------------------------------------------
// Constantes del contrato del servicio (deben coincidir con tematizacion.service.ts)
// -----------------------------------------------------------------------------

/**
 * Mapeo campo de {@link PaletaDerivada} → Token_CSS `--ds-color-*`, en el mismo
 * orden y con los mismos nombres que escribe `TematizacionService`. Es la única
 * fuente de verdad de la prueba para leer los tokens desde el DOM.
 */
const MAPEO_TOKENS: ReadonlyArray<readonly [keyof PaletaDerivada, string]> = [
  ['primary', '--ds-color-primary'],
  ['primaryHover', '--ds-color-primary-hover'],
  ['primaryContainer', '--ds-color-primary-container'],
  ['textOnPrimary', '--ds-color-text-on-primary'],
  ['focusRing', '--ds-color-focus-ring'],
  ['textOnContainer', '--ds-color-text-on-primary-container'],
  ['accentSurface', '--ds-color-accent-surface'],
  ['textOnAccentSurface', '--ds-color-text-on-accent-surface'],
];

/** Los 8 tokens de color que el servicio puede escribir/borrar. */
const TOKENS = MAPEO_TOKENS.map(([, token]) => token);

/**
 * Token NO-color de muestra (espaciado del Sistema de Diseño). El servicio
 * jamás debe tocarlo; se usa para verificar que aplicar/limpiar no filtran a
 * tokens ajenos al color.
 */
const TOKEN_NO_COLOR = '--ds-space-4';

// -----------------------------------------------------------------------------
// PRNG determinista (mulberry32) y generador de colores `#RRGGBB` válidos
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

/** Genera un color `#RRGGBB` válido (minúsculas) a partir del PRNG dado. */
function colorAleatorio(rnd: () => number): string {
  const canal = () =>
    Math.floor(rnd() * 256)
      .toString(16)
      .padStart(2, '0');
  return `#${canal()}${canal()}${canal()}`;
}

/**
 * Genera una lista determinista de `cantidad` colores `#RRGGBB` válidos,
 * anteponiendo casos borde (extremos de luminancia y saturados puros) para
 * ejercitar las ramas de ajuste de contraste de `derivarPaleta`.
 */
function coloresValidos(cantidad: number, semilla: number): string[] {
  const borde = ['#000000', '#ffffff', '#ff0000', '#00ff00', '#0000ff', '#808080', '#123456'];
  const rnd = mulberry32(semilla);
  const colores = [...borde];
  while (colores.length < cantidad) {
    colores.push(colorAleatorio(rnd));
  }
  return colores.slice(0, cantidad);
}

/** Lee el valor inline de un token desde el elemento raíz del documento. */
function leerToken(token: string): string {
  return document.documentElement.style.getPropertyValue(token);
}

// -----------------------------------------------------------------------------
// Suite
// -----------------------------------------------------------------------------

describe('TematizacionService', () => {
  let servicio: TematizacionService;
  let theme: ThemeService;

  beforeEach(() => {
    // Estado limpio del DOM y del TestBed antes de cada caso.
    for (const token of TOKENS) {
      document.documentElement.style.removeProperty(token);
    }
    document.documentElement.style.removeProperty(TOKEN_NO_COLOR);

    TestBed.resetTestingModule();
    // El servicio y ThemeService son `providedIn: 'root'`; se usa el ThemeService
    // real (solo toca `document.documentElement`), sin simulaciones.
    TestBed.configureTestingModule({});
    servicio = TestBed.inject(TematizacionService);
    theme = TestBed.inject(ThemeService);
    // Modo determinista de partida.
    theme.setMode('light');
  });

  afterEach(() => {
    // Aísla los tests: elimina cualquier sobrescritura inline que quedara.
    servicio.limpiar();
    for (const token of TOKENS) {
      document.documentElement.style.removeProperty(token);
    }
    document.documentElement.style.removeProperty(TOKEN_NO_COLOR);
  });

  // ---------------------------------------------------------------------------
  // Tarea 2.2 — Property 5: Aplicación de tokens (round-trip paleta → CSS)
  // ---------------------------------------------------------------------------
  // Feature: tematizacion-empresa-enterprise, Property 5: Aplicación de tokens
  // (round-trip paleta → CSS).
  //
  // Validates: Requirements 3.1
  //
  // Enunciado (design.md, Property 5):
  //   Para todo Color_Primario_Marca válido, tras `aplicar`, la lectura de cada
  //   Token_CSS correspondiente en el elemento raíz devuelve exactamente el
  //   valor de la Paleta_Derivada escrito para ese token.
  describe('Property 5: round-trip paleta → CSS', () => {
    it('cada token leído del elemento raíz coincide con derivarPaleta(color, modo)', () => {
      const modos: ReadonlyArray<'light' | 'dark'> = ['light', 'dark'];
      const colores = coloresValidos(120, 0x5eed_0005);
      let iteraciones = 0;

      for (const color of colores) {
        for (const modo of modos) {
          servicio.aplicar(color, modo);
          const esperada = derivarPaleta(color, modo);

          for (const [campo, token] of MAPEO_TOKENS) {
            const leido = leerToken(token);
            expect(
              leido,
              `color=${color} modo=${modo} token=${token}`,
            ).toBe(esperada[campo]);
          }
          iteraciones++;
        }
      }

      // 120 colores × 2 modos = 240 combinaciones (≥ 100 iteraciones).
      expect(iteraciones).toBe(colores.length * modos.length);
      expect(iteraciones).toBeGreaterThanOrEqual(100);
    });
  });

  // ---------------------------------------------------------------------------
  // Tarea 2.3 — Property 6: Idempotencia de aplicar/limpiar (restauración exacta)
  // ---------------------------------------------------------------------------
  // Feature: tematizacion-empresa-enterprise, Property 6: Idempotencia de
  // aplicar/limpiar (restauración exacta).
  //
  // Validates: Requirements 1.7, 3.5, 9.3
  //
  // Enunciado (design.md, Property 6):
  //   Para todo Color_Primario_Marca válido, aplicar la tematización y luego
  //   limpiarla restaura exactamente el Tema_Corporativo: no quedan
  //   sobrescrituras inline de `--ds-color-*` y `colorActivo()` vuelve a null.
  //   Además, un token NO-color de muestra nunca es tocado.
  describe('Property 6: idempotencia de aplicar/limpiar', () => {
    it('tras aplicar y limpiar no quedan tokens inline y colorActivo es null', () => {
      const colores = coloresValidos(120, 0x5eed_0006);
      let iteraciones = 0;

      for (const color of colores) {
        servicio.aplicar(color);
        // Precondición del caso: aplicar dejó al menos el token primario escrito.
        expect(leerToken('--ds-color-primary'), `color=${color}`).not.toBe('');

        servicio.limpiar();

        for (const token of TOKENS) {
          expect(leerToken(token), `color=${color} token=${token}`).toBe('');
        }
        expect(servicio.colorActivo(), `color=${color}`).toBeNull();
        // El token NO-color de muestra nunca fue tocado por el servicio.
        expect(leerToken(TOKEN_NO_COLOR), `color=${color}`).toBe('');
        iteraciones++;
      }

      expect(iteraciones).toBe(colores.length);
      expect(iteraciones).toBeGreaterThanOrEqual(100);
    });
  });

  // ---------------------------------------------------------------------------
  // Tarea 2.4 — Unit tests de modo y no-color
  // ---------------------------------------------------------------------------
  // _Requirements: 3.2, 3.3, 3.4, 4.3, 4.4, 5.1, 9.1_
  describe('unit: modo claro/oscuro y no-color', () => {
    it('aplicar en claro escribe los 8 tokens con los valores de la paleta clara', () => {
      const color = '#3366cc';
      servicio.aplicar(color, 'light');
      const esperada = derivarPaleta(color, 'light');

      for (const [campo, token] of MAPEO_TOKENS) {
        expect(leerToken(token), token).toBe(esperada[campo]);
      }
    });

    it('aplicar en oscuro escribe los 8 tokens con los valores de la paleta oscura', () => {
      const color = '#3366cc';
      servicio.aplicar(color, 'dark');
      const esperada = derivarPaleta(color, 'dark');

      for (const [campo, token] of MAPEO_TOKENS) {
        expect(leerToken(token), token).toBe(esperada[campo]);
      }
    });

    it('las variantes clara y oscura difieren en al menos un token (derivación por modo)', () => {
      // Confirma que el modo influye en la paleta: al menos un token cambia.
      const color = '#3366cc';
      const clara = derivarPaleta(color, 'light');
      const oscura = derivarPaleta(color, 'dark');
      const difiereAlgun = MAPEO_TOKENS.some(([campo]) => clara[campo] !== oscura[campo]);
      expect(difiereAlgun).toBe(true);
    });

    it('conmutar el modo con un color activo reaplica la variante correspondiente', () => {
      // El servicio observa ThemeService via `effect`: al cambiar el modo, con
      // un color activo, reaplica la variante del nuevo modo. En TestBed, el
      // efecto se procesa al leer signals; se fuerza con detectChanges/flush.
      const color = '#3366cc';
      servicio.aplicar(color, 'light');
      expect(leerToken('--ds-color-primary')).toBe(derivarPaleta(color, 'light').primary);

      theme.setMode('dark');
      // Procesa los efectos pendientes del contexto de inyección.
      TestBed.tick();

      const esperadaOscura = derivarPaleta(color, 'dark');
      for (const [campo, token] of MAPEO_TOKENS) {
        expect(leerToken(token), `dark ${token}`).toBe(esperadaOscura[campo]);
      }
    });

    it('color inválido no escribe ningún token y conserva el color vigente', () => {
      // Sin color activo: una entrada inválida no debe escribir nada.
      for (const invalido of ['', '#fff', 'rojo', '#12345', '#1234567', '123456']) {
        servicio.aplicar(invalido);
        for (const token of TOKENS) {
          expect(leerToken(token), `invalido=${invalido} token=${token}`).toBe('');
        }
        expect(servicio.colorActivo(), `invalido=${invalido}`).toBeNull();
      }

      // Con un color válido vigente: una entrada inválida no lo altera.
      const valido = '#20a0ff';
      servicio.aplicar(valido);
      const esperada = derivarPaleta(valido, 'light');
      servicio.aplicar('#nope');
      expect(servicio.colorActivo()).toBe(valido);
      for (const [campo, token] of MAPEO_TOKENS) {
        expect(leerToken(token), `preservado ${token}`).toBe(esperada[campo]);
      }
    });

    it('previsualizar se comporta como aplicar (deriva, escribe y registra el color)', () => {
      const color = '#c04000';
      servicio.previsualizar(color, 'light');
      const esperada = derivarPaleta(color, 'light');

      for (const [campo, token] of MAPEO_TOKENS) {
        expect(leerToken(token), token).toBe(esperada[campo]);
      }
      expect(servicio.colorActivo()).toBe(color);
    });

    it('sin color aplicado (plataforma) el elemento raíz no tiene tokens de color inline', () => {
      // Estado por defecto tras beforeEach: ningún color aplicado ⇒ Tema_Corporativo.
      for (const token of TOKENS) {
        expect(leerToken(token), token).toBe('');
      }
      expect(servicio.colorActivo()).toBeNull();
    });
  });
});
