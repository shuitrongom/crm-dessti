// =============================================================================
// Configuracion de Playwright — pruebas e2e, responsive y accesibilidad (Req 52.2, 57)
// -----------------------------------------------------------------------------
// Suite e2e SEPARADA de `ng test`: NO forma parte de la compilacion de produccion
// ni de las pruebas unitarias (los specs viven en ./e2e con su propio tsconfig y
// las tsconfig de la app excluyen ./e2e). Se ejecuta manualmente o en CI con la
// pila (frontend + backend) levantada:
//   1) npm run e2e:install   (instala navegadores)
//   2) npm run e2e           (ejecuta las pruebas)
//
// Cubre flujos criticos (login, cotizacion, aprobacion de diseno, orden de
// fabricacion, timbrado), comportamiento responsive (viewport movil >= 320px,
// colapso del drawer) y accesibilidad de pagina completa (incluye color-contrast,
// no evaluable en jsdom). Ver e2e/README.md.
// =============================================================================

import { defineConfig, devices } from '@playwright/test';

/** URL base de la aplicacion bajo prueba (configurable por entorno). */
const baseURL = process.env['E2E_BASE_URL'] ?? 'http://localhost:4200';

/** Indica si se debe arrancar el servidor de desarrollo desde Playwright. */
const arrancarServidor = !process.env['E2E_BASE_URL'];

export default defineConfig({
  testDir: './e2e',
  // Solo archivos de prueba e2e; nunca los *.spec.ts de Angular en src/.
  testMatch: /.*\.e2e\.ts/,
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  workers: process.env['CI'] ? 1 : undefined,
  reporter: process.env['CI'] ? [['github'], ['html', { open: 'never' }]] : [['list']],
  timeout: 60_000,
  expect: { timeout: 10_000 },

  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium-desktop',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } },
    },
    {
      // Viewport movil para validar el comportamiento responsive (Req 52).
      name: 'mobile-chromium',
      use: { ...devices['Pixel 5'] },
    },
  ],

  // Arranca el servidor de desarrollo solo si no se apunta a un E2E_BASE_URL
  // externo (p. ej. un entorno ya desplegado en CI).
  webServer: arrancarServidor
    ? {
        command: 'npm run start',
        url: baseURL,
        reuseExistingServer: !process.env['CI'],
        timeout: 180_000,
      }
    : undefined,
});
