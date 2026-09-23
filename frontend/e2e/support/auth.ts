// =============================================================================
// Utilidades compartidas de las pruebas e2e (Req 52.2)
// -----------------------------------------------------------------------------
// Ayudantes para autenticarse en la aplicacion y leer las credenciales/entorno
// requeridos. Las pruebas que necesitan una sesion se OMITEN (test.skip) cuando
// no se proporcionan las variables de entorno, de modo que la suite pueda
// ejecutarse parcialmente sin datos sembrados. Ver e2e/README.md.
// =============================================================================

import { expect, type Page } from '@playwright/test';

/** Credenciales de prueba tomadas del entorno (no se hardcodean secretos). */
export interface CredencialesE2E {
  usuario: string;
  password: string;
}

/**
 * Devuelve las credenciales de entorno si estan presentes, o `null` para que la
 * prueba pueda omitirse de forma controlada.
 */
export function credencialesDeEntorno(): CredencialesE2E | null {
  const usuario = process.env['E2E_USER'];
  const password = process.env['E2E_PASSWORD'];
  if (!usuario || !password) {
    return null;
  }
  return { usuario, password };
}

/**
 * Realiza el flujo de inicio de sesion en la UI: navega a /login, llena el
 * formulario y espera a salir de la pagina de login (aterrizaje en el ambito).
 */
export async function iniciarSesion(page: Page, credenciales: CredencialesE2E): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Identificador').fill(credenciales.usuario);
  await page.getByLabel('Contrasena').fill(credenciales.password);
  await page.getByRole('button', { name: 'Iniciar sesion' }).click();
  // Tras un login exitoso, la app redirige fuera de /login al ambito del rol.
  await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 });
}
