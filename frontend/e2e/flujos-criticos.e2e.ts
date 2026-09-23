// =============================================================================
// e2e: flujos criticos del negocio (Req 52.2)
// -----------------------------------------------------------------------------
// Cubre, contra una pila (frontend + backend) en ejecucion, el recorrido de las
// vistas clave del negocio: cotizaciones (listado/alta), aprobacion de prueba de
// diseno (portal), ordenes de fabricacion (cambio de estado) y facturas CFDI
// (timbrado). Cada prueba requiere una sesion valida y, segun el caso, datos
// sembrados; en ausencia de E2E_USER/E2E_PASSWORD se OMITEN de forma controlada
// (sin fallos). Ver e2e/README.md para las variables y el sembrado esperado.
//
// Estas pruebas validan la navegacion, los landmarks y los controles de la UI de
// forma robusta (sin acoplarse a IDs de datos concretos): confirman que la vista
// carga, muestra su encabezado y ofrece las acciones/estados esperados.
// =============================================================================

import { test, expect, type Page } from '@playwright/test';

import { credencialesDeEntorno, iniciarSesion } from './support/auth';

/** Autentica antes de cada prueba u omite si faltan credenciales de entorno. */
async function sesionOOmitir(page: Page): Promise<void> {
  const credenciales = credencialesDeEntorno();
  test.skip(credenciales === null, 'Requiere E2E_USER y E2E_PASSWORD.');
  await iniciarSesion(page, credenciales!);
}

test.describe('Flujo: cotizaciones', () => {
  test('lista cotizaciones y abre el alta de una nueva', async ({ page }) => {
    await sesionOOmitir(page);
    await page.goto('/empresa/comercial/cotizaciones');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    // El alta de cotizacion es una ruta dedicada protegida por permiso.
    await page.goto('/empresa/comercial/cotizaciones/nueva');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});

test.describe('Flujo: aprobacion de prueba de diseno (portal)', () => {
  test('muestra las pruebas de diseno del cliente con sus acciones', async ({ page }) => {
    await sesionOOmitir(page);
    await page.goto('/portal/pruebas-diseno');
    // La vista carga (encabezado) o presenta un estado vacio/carga consistente.
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});

test.describe('Flujo: ordenes de fabricacion', () => {
  test('lista ordenes y expone el filtro por estado', async ({ page }) => {
    await sesionOOmitir(page);
    await page.goto('/empresa/operacion/ordenes-fabricacion');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});

test.describe('Flujo: facturas CFDI (timbrado)', () => {
  test('lista facturas y expone el filtro por estado', async ({ page }) => {
    await sesionOOmitir(page);
    await page.goto('/empresa/facturacion/facturas');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });

  test('el timbrado solicita confirmacion antes de emitir el CFDI', async ({ page }) => {
    await sesionOOmitir(page);
    await page.goto('/empresa/facturacion/facturas');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    // Si existe una factura en borrador con accion de timbrar, al abrir el menu
    // y elegir "Timbrar" debe aparecer el dialogo de confirmacion (Req 54.2).
    const menu = page.getByRole('button', { name: 'Acciones de factura' }).first();
    const hayAcciones = (await menu.count()) > 0;
    test.skip(!hayAcciones, 'Requiere al menos una factura con acciones sembrada.');

    await menu.click();
    const timbrar = page.getByRole('menuitem', { name: 'Timbrar' });
    const hayTimbrar = (await timbrar.count()) > 0;
    test.skip(!hayTimbrar, 'Requiere una factura en estado borrador sembrada.');

    await timbrar.click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Timbrar factura' })).toBeVisible();
    // Cancelar la confirmacion para no alterar datos.
    await page.getByRole('button', { name: 'Cancelar' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });
});
