# Implementation Plan: Tematización por empresa + rediseño enterprise

## Overview

Convert the feature design into a series of prompts for a code-generation LLM that will implement each step with incremental progress. Make sure that each prompt builds on the previous prompts, and ends with wiring things together. There should be no hanging or orphaned code that isn't integrated into a previous step. Focus ONLY on tasks that involve writing, modifying, or testing code.

El plan avanza de la lógica pura de color (frontend) → servicio de tematización → backend (migración V67, DTO, servicio, endpoint) → carga por tenant → rediseño enterprise → verificación integral. Lenguajes: **TypeScript/Angular 20** (frontend) y **Java 21/Spring Boot** (backend), según el diseño. Las subtareas de prueba están marcadas con `*` (opcionales); las de propiedades usan **fast-check** (frontend) / **jqwik** (backend), mínimo 100 iteraciones.

## Tasks

- [x] 1. Núcleo de derivación de paleta (lógica pura, frontend)
  - [x] 1.1 Implementar utilidades de color puras
    - Crear `frontend/src/app/core/theming/color-utils.ts`: `esHexValido`, `luminanciaRelativa`, `relacionContraste`, `mezclar` (linealización sRGB + fórmula WCAG 2.1)
    - _Requirements: 2.2, 8.1, 1.4, 6.4_
  - [x] 1.2 Implementar `derivarPaleta` y el tipo `PaletaDerivada`
    - Crear `frontend/src/app/core/theming/derivar-paleta.ts` con el algoritmo de derivación (pasos 1–8 del diseño) para modos claro/oscuro
    - _Requirements: 2.1, 2.3, 2.4, 2.5, 2.6_
  - [x]* 1.3 Property test: contraste AA texto-sobre-primario
    - **Property 1: Contraste AA del texto sobre el primario**
    - **Validates: Requirements 2.2, 8.1**
  - [x]* 1.4 Property test: AA de todos los pares texto/superficie derivados
    - **Property 2: Contraste AA de todos los pares texto/superficie derivados**
    - **Validates: Requirements 2.4, 2.5, 8.2, 8.4**
  - [x]* 1.5 Property test: completitud estructural de la paleta
    - **Property 3: Completitud estructural de la paleta**
    - **Validates: Requirements 2.1**
  - [x]* 1.6 Property test: determinismo de la derivación
    - **Property 4: Determinismo de la derivación**
    - **Validates: Requirements 2.3, 2.6**
  - [x]* 1.7 Property test: validación de formato hexadecimal (frontend)
    - **Property 8: Validación de formato hexadecimal**
    - **Validates: Requirements 1.4, 6.4**

- [x] 2. Servicio de tematización en runtime (frontend)
  - [x] 2.1 Implementar `TematizacionService`
    - Crear `frontend/src/app/core/services/tematizacion.service.ts`: `aplicar`, `previsualizar`, `limpiar`, `colorActivo`; escribe/borra `--ds-color-*` en `document.documentElement`; se suscribe a `ThemeService` para reaplicar al cambiar de modo
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_
  - [x]* 2.2 Property test: aplicación de tokens (round-trip paleta → CSS)
    - **Property 5: Aplicación de tokens (round-trip paleta → CSS)**
    - **Validates: Requirements 3.1**
  - [x]* 2.3 Property test: idempotencia de aplicar/limpiar
    - **Property 6: Idempotencia de aplicar/limpiar (restauración exacta)**
    - **Validates: Requirements 1.7, 3.5, 9.3**
  - [x]* 2.4 Unit tests de modo y no-color
    - Aplicar en claro/oscuro, conmutar modo reaplica, color nulo/carga fallida mantienen tema corporativo, plataforma sin color
    - _Requirements: 3.2, 3.3, 3.4, 4.3, 4.4, 5.1, 9.1_

- [x] 3. Checkpoint — Lógica de color y tematización
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Persistencia backend del color de marca
  - [x] 4.1 Migración Flyway V67
    - Crear `backend/src/main/resources/db/migration/V67__empresa_branding_color_primario.sql`: `ADD COLUMN branding_color_primario VARCHAR(7) NULL` con `CHECK` de formato `#RRGGBB` (o nulo), default nulo
    - _Requirements: 6.1, 9.1_
  - [x] 4.2 Extender entidad `Empresa`
    - Añadir campo `brandingColorPrimario`, getter, y ampliar el mutador `actualizarBranding(...)` para aceptar el color preservando nombre/logo
    - _Requirements: 6.3, 6.7, 9.2_
  - [x] 4.3 Extender contratos: DTO, command y request
    - `BrandingDto` (+ `colorPrimario`, `de(Empresa)`), `ActualizarBrandingCommand` (+ `colorPrimario`), `ActualizarBrandingRequest` (+ `@Pattern(^#[0-9a-fA-F]{6}$)`)
    - _Requirements: 6.2, 6.4_
  - [x]* 4.4 Property test: validación de formato hexadecimal (backend)
    - **Property 8: Validación de formato hexadecimal**
    - **Validates: Requirements 6.4**
  - [x]* 4.5 Schema/smoke test de V67
    - Verificar columna existe, nullable, default nulo y `CHECK` presente
    - _Requirements: 6.1_

- [x] 5. Servicio y endpoint de branding con color
  - [x] 5.1 Extender `ServicioBranding`
    - `actualizarBranding` persiste el color (tenant por `TenantContext.require()`), revalida formato como defensa en profundidad, y audita el cambio de color por el camino central (`detalle` legible; JSONB null,null)
    - _Requirements: 6.3, 6.5, 6.6, 6.7_
  - [x]* 5.2 Property test: aislamiento multi-tenant del color
    - **Property 7: Aislamiento multi-tenant del color**
    - **Validates: Requirements 4.5, 5.2, 6.6**
  - [x]* 5.3 Integration tests del endpoint
    - `GET`/`PUT /empresa/branding` con color, 403 sin `branding:actualizar`, auditoría invocada, tenant desde contexto no desde cuerpo
    - _Requirements: 6.2, 6.3, 6.5, 6.6, 1.8_

- [x] 6. Checkpoint — Backend de color persistido
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Integración frontend: modelo, servicio y carga por tenant
  - [x] 7.1 Extender modelo y `BrandingService`
    - `Branding` (+ `colorPrimario`) en `home.models.ts`; `BrandingService.consultar()` mapea el color; añadir `actualizar(payload)` y limpieza vía `PUT /empresa/branding`
    - _Requirements: 4.1, 6.2_
  - [x] 7.2 Cargar color al entrar al ámbito empresa
    - En `shell-layout`, tras `consultar()`, invocar `TematizacionService.aplicar` si hay color; mantener Tema_Corporativo si es nulo o si la carga falla; no aplicar en ámbito plataforma
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 5.1_
  - [x]* 7.3 Tests de carga y fallback
    - Con color aplica; sin color/carga fallida mantiene tema; se emite el GET al entrar
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [x] 8. Vista de administración de branding (selección de color)
  - [x] 8.1 Selector de color y presets con previsualización
    - En `/empresa/administracion/branding`: selector libre + `Preset_Color`, validación es-MX, previsualización en vivo (`previsualizar`), guardar (`PUT`) y limpiar (color nulo + `limpiar()`); controles de edición solo con `branding:actualizar`; preservar nombre/logo
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 6.7, 9.2_
  - [x]* 8.2 Unit tests de la vista
    - Presencia de selector/presets por permiso, selección de preset/entrada libre, mensaje de validación, previsualización aplica tokens, guardar/limpiar
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.7, 1.8_
  - [x]* 8.3 Accesibilidad de la vista (axe one-shot)
    - Ejecutar `esperarSinViolaciones` en la vista de branding (`--include`, one-shot)
    - _Requirements: 8.1, 8.2_

- [x] 9. Checkpoint — Tematización por empresa de extremo a extremo
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Rediseño enterprise: componentes compartidos
  - [x] 10.1 Patrón/componente `ChipEstado`
    - Crear `frontend/src/app/shared/components/chip-estado/`: mapea variante {éxito, advertencia, error, info, neutro} al Token_CSS semántico
    - _Requirements: 7.3, 7.4_
  - [x]* 10.2 Property test: mapeo semántico del Chip_Estado
    - **Property 9: Mapeo semántico del Chip_Estado**
    - **Validates: Requirements 7.3, 7.4**
  - [x] 10.3 Acento de marca en `PageHeader` y refinamiento de tarjetas
    - `PageHeader` con acento basado en `--ds-color-primary`; refinar estilos de tarjeta/`StateContainer`/`DataTable` usando tokens; respetar `prefers-reduced-motion`
    - _Requirements: 7.1, 7.2, 8.3_
  - [x] 10.4 Refinar tokens corporativos sin romper AA
    - Ajustes finos en `_tokens.scss` (claro y oscuro) manteniendo AA en pares texto/superficie
    - _Requirements: 7.1, 7.6_
  - [x]* 10.5 Property test: contraste AA de tokens corporativos
    - **Property 10: Contraste AA de los tokens corporativos refinados**
    - **Validates: Requirements 7.6**

- [x] 11. Aplicar rediseño a las vistas de Operación
  - [x] 11.1 Vistas de Orden de Fabricación (detalle + lista)
    - Aplicar `ChipEstado` a estados y tarjeta refinada
    - _Requirements: 7.5_
  - [x] 11.2 Vistas de Levantamiento de Sitio (detalle + lista)
    - Aplicar `ChipEstado` y tarjeta refinada
    - _Requirements: 7.5_
  - [x] 11.3 Vistas de Permiso de Instalación (detalle + lista)
    - Aplicar `ChipEstado` y tarjeta refinada
    - _Requirements: 7.5_
  - [x] 11.4 Vistas de Orden de Trabajo de Instalación (detalle + lista)
    - Aplicar `ChipEstado` y tarjeta refinada
    - _Requirements: 7.5_
  - [x]* 11.5 Tests de render y accesibilidad de las vistas de Operación
    - Uso de `ChipEstado` por estado en las 4 vistas; axe one-shot por vista (`--include`)
    - _Requirements: 7.2, 7.5, 8.3_

- [x] 12. Verificación integral final
  - [x] 12.1 Verificación de no-regresión y build completo
    - Backend: `mvn -o compile` y `mvn -o test-compile`; ejecutar suites tocadas con `mvn -o surefire:test -Dtest=<Clase>`. Frontend: `npx ng build --configuration production` y specs one-shot `npx ng test --watch=false --include=<spec>` (nunca la suite completa). Confirmar que la migración aplicada es **V67** y que shell/navegación y branding (nombre/logo) siguen funcionando
    - _Requirements: 6.1, 9.2, 9.3, 9.4_

- [x] 13. Checkpoint final
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Las tareas marcadas con `*` son opcionales (pruebas) y pueden omitirse para un MVP más rápido; las de propiedades referencian su propiedad del diseño y usan fast-check/jqwik con mínimo 100 iteraciones.
- Cada tarea referencia subrequisitos específicos para trazabilidad.
- Los checkpoints garantizan validación incremental.
- Las pruebas de propiedades validan propiedades universales de corrección; las unitarias/de ejemplo cubren casos concretos, bordes y errores; las de integración cubren wiring y contratos.
- Los sub-agentes de test deben hacer **compile-only** (evitar timeouts); el orquestador ejecuta las pruebas y las specs de axe siempre one-shot.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "4.1"] },
    { "id": 1, "tasks": ["1.2", "1.7", "4.2", "4.5"] },
    { "id": 2, "tasks": ["1.3", "1.4", "1.5", "1.6", "2.1", "4.3", "10.1", "10.4"] },
    { "id": 3, "tasks": ["2.2", "2.3", "2.4", "4.4", "5.1", "7.1", "10.2", "10.3", "10.5"] },
    { "id": 4, "tasks": ["5.2", "5.3", "7.2", "11.1", "11.2", "11.3", "11.4"] },
    { "id": 5, "tasks": ["7.3", "8.1", "11.5"] },
    { "id": 6, "tasks": ["8.2", "8.3"] },
    { "id": 7, "tasks": ["12.1"] }
  ]
}
```
