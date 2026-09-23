// =============================================================================
// Configuracion de Vitest para `ng test` (@angular/build:unit-test) (Req 57)
// -----------------------------------------------------------------------------
// El builder de pruebas de Angular carga este archivo cuando en angular.json se
// declara `runnerConfig: true`. Solo se ajusta el tiempo maximo por prueba: los
// analisis de accesibilidad con axe-core son intensivos en CPU y, en maquinas
// bajo carga (Windows/CI), pueden superar el limite por defecto de 5000 ms, lo
// que provoca falsos negativos por expiracion (no por violaciones reales). Un
// limite mas holgado hace deterministas las pruebas de accesibilidad sin alterar
// lo que verifican. El resto de la configuracion la aporta el builder de Angular.
// =============================================================================

import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // Holgura para las pruebas de accesibilidad (axe-core) bajo carga.
    testTimeout: 30000,
    hookTimeout: 30000,
    // axe-core es intensivo en CPU: con demasiados procesos en paralelo, la
    // contencion de CPU en Windows hace que algunos analisis superen el limite
    // por prueba y fallen de forma no determinista. Se acota el numero de
    // procesos de trabajo para eliminar esa contencion sin serializar del todo
    // la suite (mantiene el paralelismo, pero estable).
    pool: 'forks',
    poolOptions: {
      forks: { minForks: 1, maxForks: 2 },
    },
  },
});
