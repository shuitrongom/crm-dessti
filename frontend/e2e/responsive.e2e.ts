// =============================================================================
// e2e: Comportamiento responsive mobile-first (Req 52)
// -----------------------------------------------------------------------------
// - La UI se conserva utilizable desde 320px de ancho (Req 52.4): el login se
//   renderiza sin desborde horizontal.
// - En viewport movil el shell colapsa el drawer y expone el boton de menu; en
//   escritorio el drawer es persistente (sin boton de menu). Requiere sesion.
// =============================================================================

import { test, expect } from '@playwright/test';

import { credencialesDeEntorno, iniciarSesion } from './support/auth';

test.describe('Responsive', () => {
  test('el login es utilizable a 320px sin desborde horizontal', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto('/login');
    await expect(page.getByLabel('Identificador')).toBeVisible();

    // No debe existir desbordamiento horizontal en el ancho minimo soportado.
    const desbordaX = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    );
    expect(desbordaX).toBe(false);
  });

  test('el shell colapsa el drawer y muestra el boton de menu en movil', async ({ page }, testInfo) => {
    const credenciales = credencialesDeEntorno();
    test.skip(credenciales === null, 'Requiere E2E_USER y E2E_PASSWORD.');
    // Esta comprobacion aplica al proyecto de viewport movil.
    test.skip(
      (testInfo.project.use.viewport?.width ?? 1280) >= 905,
      'Solo aplica al proyecto de viewport movil.',
    );

    await iniciarSesion(page, credenciales!);
    // En movil, el boton de alternar menu es visible (drawer superpuesto).
    await expect(
      page.getByRole('button', { name: 'Abrir o cerrar menu de navegacion' }),
    ).toBeVisible();
  });

  test('el shell muestra el drawer persistente en escritorio', async ({ page }, testInfo) => {
    const credenciales = credencialesDeEntorno();
    test.skip(credenciales === null, 'Requiere E2E_USER y E2E_PASSWORD.');
    test.skip(
      (testInfo.project.use.viewport?.width ?? 0) < 905,
      'Solo aplica al proyecto de viewport de escritorio.',
    );

    await iniciarSesion(page, credenciales!);
    // En escritorio no se ofrece el boton de menu: el drawer es persistente.
    await expect(
      page.getByRole('button', { name: 'Abrir o cerrar menu de navegacion' }),
    ).toHaveCount(0);
    await expect(page.getByRole('navigation', { name: 'Navegacion principal' })).toBeVisible();
  });
});
