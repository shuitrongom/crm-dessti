// =============================================================================
// e2e: Inicio de sesion + accesibilidad de pagina completa (Req 1, 52.2, 57)
// -----------------------------------------------------------------------------
// - La pagina de login se renderiza y valida como accesible con axe-core
//   (incluye color-contrast, no evaluable en jsdom) para WCAG 2.1 A/AA.
// - Con credenciales de entorno (E2E_USER/E2E_PASSWORD) valida el flujo completo
//   de login y el aterrizaje en el ambito del rol; en su ausencia se omite.
// =============================================================================

import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

import { credencialesDeEntorno, iniciarSesion } from './support/auth';

test.describe('Login', () => {
  test('la pagina de login se muestra con su formulario', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Bienvenido de nuevo' })).toBeVisible();
    await expect(page.getByLabel('Identificador')).toBeVisible();
    await expect(page.getByLabel('Contrasena')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Iniciar sesion' })).toBeVisible();
  });

  test('la pagina de login no tiene violaciones de accesibilidad (WCAG 2.1 A/AA)', async ({
    page,
  }) => {
    await page.goto('/login');
    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    expect(resultados.violations).toEqual([]);
  });

  test('inicia sesion con credenciales validas y aterriza en el ambito', async ({ page }) => {
    const credenciales = credencialesDeEntorno();
    test.skip(credenciales === null, 'Requiere E2E_USER y E2E_PASSWORD.');
    await iniciarSesion(page, credenciales!);
    await expect(page).toHaveURL(/\/(empresa|plataforma|portal)/);
  });

  test('una vista autenticada no tiene violaciones de accesibilidad', async ({ page }) => {
    const credenciales = credencialesDeEntorno();
    test.skip(credenciales === null, 'Requiere E2E_USER y E2E_PASSWORD.');
    await iniciarSesion(page, credenciales!);
    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    expect(resultados.violations).toEqual([]);
  });
});

