# Pruebas e2e (Playwright) — flujos, responsive y accesibilidad

Suite de pruebas end-to-end **separada** de las pruebas unitarias (`ng test`). No
forma parte de la compilación de producción (`ng build`) ni del pipeline de
Vitest: los archivos `*.e2e.ts` viven en `./e2e` con su propio `tsconfig.json`, y
`tsconfig.app.json` / `tsconfig.spec.json` excluyen `./e2e` y `playwright.config.ts`.
Estas pruebas se ejecutan manualmente o en CI con la pila (frontend + backend)
levantada.

## Cómo ejecutarlas

```bash
# 1) Instalar el navegador (una sola vez por entorno)
npm run e2e:install

# 2) Ejecutar la suite (arranca `ng serve` automáticamente si no fijas E2E_BASE_URL)
npm run e2e
```

Por defecto Playwright arranca el servidor de desarrollo (`npm run start`) en
`http://localhost:4200`. Si apuntas a un entorno ya desplegado, fija
`E2E_BASE_URL` y Playwright no arrancará ningún servidor.

## Variables de entorno

| Variable        | Requerida | Descripción                                                                 |
| --------------- | --------- | --------------------------------------------------------------------------- |
| `E2E_BASE_URL`  | No        | URL base de la app. Si se define, no se arranca el `webServer` local.       |
| `E2E_USER`      | Para flujos autenticados | Identificador de una cuenta de prueba.                        |
| `E2E_PASSWORD`  | Para flujos autenticados | Contraseña de la cuenta de prueba.                            |

Las pruebas que necesitan sesión se **omiten automáticamente** (`test.skip`)
cuando `E2E_USER` / `E2E_PASSWORD` no están definidas, de modo que la suite puede
correr parcialmente (por ejemplo, la accesibilidad y el responsive del login) sin
datos sembrados. Algunos flujos de negocio requieren además datos sembrados (una
factura en borrador para el timbrado, cotizaciones, pruebas de diseño, órdenes de
fabricación); esas comprobaciones se saltan de forma controlada si no encuentran
los elementos correspondientes.

## Proyectos (viewports)

- `chromium-desktop`: Chromium de escritorio (1280×800).
- `mobile-chromium`: Pixel 5 — valida el comportamiento **responsive** (Req 52),
  incluido el colapso del drawer del shell y la usabilidad a partir de 320px.

## Qué se valida aquí (y qué no en `ng test`)

Esta capa cubre lo que jsdom no puede evaluar de forma fiable:

- **Contraste de color** (`color-contrast`): requiere maquetado y estilos
  computados de un navegador real; está **desactivado** en las pruebas de
  componente (jsdom) y se valida aquí con `@axe-core/playwright`.
- **Accesibilidad de página completa** (WCAG 2.1 A/AA) sobre el login y una vista
  autenticada, con `AxeBuilder(...).withTags(['wcag2a','wcag2aa']).analyze()`.
- **Responsive mobile-first**: sin desbordamiento horizontal a 320px y colapso del
  drawer en viewport móvil.
- **Flujos críticos**: login, cotización (listado/alta), aprobación de prueba de
  diseño, órdenes de fabricación y timbrado de CFDI (incluida la confirmación
  previa al timbrado).

## Nota sobre WCAG 2.1 AA

La validación automatizada (axe-core en componentes + Playwright en página
completa) no garantiza el cumplimiento total de WCAG 2.1 AA. El cumplimiento
completo requiere además **pruebas manuales con tecnología de asistencia**
(lectores de pantalla, navegación solo por teclado) y revisión experta de
accesibilidad.
