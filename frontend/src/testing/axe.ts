// =============================================================================
// Ayudante de accesibilidad para pruebas de componente (axe-core) (Req 57)
// -----------------------------------------------------------------------------
// Ejecuta un analisis de accesibilidad axe-core sobre el DOM renderizado por un
// ComponentFixture de TestBed y afirma que no existen violaciones WCAG 2.1
// niveles A y AA (Req 57.1). Esta capa corre bajo `ng test` (Vitest + jsdom, sin
// navegador), por lo que se DESACTIVAN las reglas que requieren un motor de
// maquetado real (por ejemplo `color-contrast`), imposibles de evaluar de forma
// fiable en jsdom. El contraste de color, la accesibilidad de pagina completa y
// el comportamiento responsive se validan en la capa e2e de Playwright
// (@axe-core/playwright) y, para WCAG 2.1 AA completo, con revision manual y
// tecnologia de asistencia. Ver e2e/README.md.
// =============================================================================

import type { ComponentFixture } from '@angular/core/testing';
import axe from 'axe-core';

// axe-core v4 declara sus tipos dentro del namespace `axe`; se referencian con
// el prefijo del namespace en lugar de importarlos como exportaciones nombradas.
type RunOptions = axe.RunOptions;
type Result = axe.Result;
type AxeResults = axe.AxeResults;

/** Etiquetas WCAG evaluadas por esta capa (niveles A y AA). */
const ETIQUETAS_WCAG: readonly string[] = ['wcag2a', 'wcag2aa'];

/**
 * Reglas de axe-core que NO pueden evaluarse de forma fiable en jsdom porque
 * dependen de geometria/estilos computados de un navegador real. Se desactivan
 * aqui y se cubren en la capa e2e de Playwright.
 */
const REGLAS_DESACTIVADAS_JSDOM: RunOptions['rules'] = {
  // Requiere calcular color de fondo/texto efectivo tras el layout real.
  'color-contrast': { enabled: false },
};

/** Opciones de ejecucion de axe usadas por defecto en las pruebas de componente. */
const OPCIONES_POR_DEFECTO: RunOptions = {
  runOnly: { type: 'tag', values: [...ETIQUETAS_WCAG] },
  rules: REGLAS_DESACTIVADAS_JSDOM,
};

/**
 * Ejecuta axe-core sobre el elemento raiz del fixture y devuelve el resultado.
 *
 * @param fixture fixture de componente ya renderizado (idealmente tras
 *        `await fixture.whenStable()`).
 * @param opciones sobrescrituras opcionales de las opciones por defecto.
 */
export async function analizarAccesibilidad(
  fixture: ComponentFixture<unknown>,
  opciones: RunOptions = OPCIONES_POR_DEFECTO,
): Promise<AxeResults> {
  const elemento = fixture.nativeElement as Element;
  return axe.run(elemento, opciones);
}

/** Construye un resumen legible de las violaciones para el mensaje de fallo. */
function resumirViolaciones(violaciones: readonly Result[]): string {
  return violaciones
    .map((v) => {
      const nodos = v.nodes.map((n) => `      - ${n.target.join(' ')}`).join('\n');
      return `  [${v.impact ?? 'sin-impacto'}] ${v.id}: ${v.help}\n    (${v.helpUrl})\n${nodos}`;
    })
    .join('\n');
}

/**
 * Renderiza el analisis y AFIRMA que no hay violaciones WCAG 2.1 A/AA. En caso
 * de fallo, imprime un resumen legible de cada violacion (regla, impacto, nodos)
 * para facilitar la correccion.
 *
 * @param fixture fixture de componente ya renderizado.
 * @param opciones sobrescrituras opcionales de las opciones de axe.
 */
export async function esperarSinViolaciones(
  fixture: ComponentFixture<unknown>,
  opciones: RunOptions = OPCIONES_POR_DEFECTO,
): Promise<void> {
  const resultado = await analizarAccesibilidad(fixture, opciones);
  const violaciones = resultado.violations;
  if (violaciones.length > 0) {
    const resumen = resumirViolaciones(violaciones);
    throw new Error(
      `Se detectaron ${violaciones.length} violacion(es) de accesibilidad (WCAG 2.1 A/AA):\n${resumen}`,
    );
  }
  expect(violaciones.length).toBe(0);
}
