# Implementation Plan: Plataforma Multigiro (Verticales Enchufables)

## Overview

El plan sigue un orden incremental que **primero construye el andamiaje del Núcleo** (contrato de vertical, registro, catálogo de giros, empresa con giro, doble gating, RBAC por giro), **luego extrae el Vertical de Anuncios** sobre ese andamiaje, y **por último añade el esqueleto de Manufactura** que prueba el modelo. Cada bloque cierra con un checkpoint verificado con la suite completa (`mvn -o clean verify` backend; `ng build` + `ng test` frontend), sin código huérfano ni duplicado (Req 13).

Convenciones: subtareas marcadas con `*` son de prueba y **opcionales** para un MVP; las de implementación no marcadas son obligatorias. Cada tarea referencia sus requisitos. Las pruebas de propiedad se etiquetan con `Feature: plataforma-multigiro, Property N`.

## Tasks

- [x] 1. Núcleo: Contrato de Vertical y Registro de Verticales
  - [x] 1.1 Definir el puerto `ContratoVertical` e `ItemNavegacionVertical`
    - Crear paquete `com.dessti.crm.platform.vertical` con la interfaz `ContratoVertical` (`giro()`, `modulos()`, `recursos()`, `navegacion()`) y el record `ItemNavegacionVertical` (etiqueta, ruta, icono, recurso, operacion)
    - _Requisitos: 4.1, 4.5_
  - [x] 1.2 Implementar `RegistroVerticales` (indexación por giro y fail-fast por duplicado)
    - Inyectar `List<ContratoVertical>`, indexar por `giro()`, exponer `porGiro`, `giroDeModulo`, `girosRegistrados`; lanzar `IllegalStateException` si hay clave de giro duplicada
    - _Requisitos: 4.2, 4.3, 4.4_
  - [x]* 1.3 Prueba de propiedad: indexación del registro
    - **Property 2: El Registro de Verticales indexa cada Giro**
    - **Validates: Requirements 4.3**
  - [x]* 1.4 Prueba de propiedad: rechazo de giro duplicado
    - **Property 3: El Registro de Verticales rechaza Giros duplicados**
    - **Validates: Requirements 4.4**

- [x] 2. Núcleo: Catálogo de Giros
  - [x] 2.1 Migración `V50`: tabla `giro` y siembra de `anuncios-luminosos`
    - Crear tabla `giro` sin RLS (dato de plataforma); sembrar el giro `anuncios-luminosos` activo; sembrar el recurso RBAC `giro` (crear/listar/activar/desactivar) asignado a `super_admin`
    - _Requisitos: 1.2, 8.4, 11.1_
  - [x] 2.2 Dominio y normalización de `Giro`
    - Entidad `Giro` (`platform.giros`) con clave normalizada (minúsculas/kebab), `nombre_visible`, `descripcion`, `activo`, auditoría; método de normalización de clave
    - _Requisitos: 1.2_
  - [x]* 2.3 Prueba de propiedad: normalización idempotente de clave
    - **Property 1: Normalización idempotente de la clave de Giro**
    - **Validates: Requirements 1.2**
  - [x] 2.4 Repositorio y adaptador JPA de `Giro`
    - `GiroRepository` (puerto) + adaptador JPA; consulta por clave y conteo de empresas por giro
    - _Requisitos: 1.2, 1.5_
  - [x] 2.5 `ServicioGiros` (crear/activar/desactivar/listar) con reglas
    - Clave única (422), no desactivar giro en uso (422 con conteo), listado paginado (20/máx 100) con filtro por estado, auditoría en cada operación
    - _Requisitos: 1.1, 1.3, 1.5, 1.6, 1.7_
  - [x] 2.6 `GiroController` REST bajo `/api/v1/plataforma/giros`
    - Endpoints protegidos con `@autorizador.tiene('giro', ...)`; DTOs distintos de la entidad; añadir `giro` a `ClasificadorRecursosPlataforma`
    - _Requisitos: 1.1, 1.6_
  - [x]* 2.7 Pruebas de ejemplo/borde de `ServicioGiros`
    - Clave duplicada (422), desactivar giro en uso (422+conteo), desactivar giro libre (ok)
    - _Requisitos: 1.3, 1.5_

- [x] 3. Checkpoint — Núcleo de giros y registro
  - Ejecutar `mvn -o clean verify`; asegurar 0 fallos. Ante dudas, preguntar al usuario.

- [x] 4. Núcleo: Empresa ligada a un Giro
  - [x] 4.1 Migración `V51`: `empresa.giro_id` con backfill y NOT NULL
    - Añadir `giro_id` FK a `giro`; backfill a `anuncios-luminosos` para empresas sin giro; luego `SET NOT NULL`; migración no destructiva y transaccional (fail-fast si inconsistente)
    - _Requisitos: 2.3, 11.2, 11.3, 11.4_
  - [x] 4.2 Modelo `Empresa` con Giro y `cambiarGiro`
    - Campo `giro_id` (getter), fábrica `crear(...)` exige `giroId`; método `cambiarGiro(nuevoGiroId, actor)` (la validación de datos del vertical la aplica el servicio)
    - _Requisitos: 2.1, 2.3, 3.1_
  - [x] 4.3 `CrearEmpresaCommand` y `ServicioEmpresas.crearEmpresa` con Giro
    - Añadir `giroId` al comando; validar giro existente y activo (422 si no); registrar el giro en la auditoría del alta
    - _Requisitos: 2.1, 2.2, 2.5_
  - [x] 4.4 `DatosVerticalPort` y `ServicioEmpresas.cambiarGiro`
    - Puerto de existencia de datos del vertical por giro; `cambiarGiro` rechaza (422) si la empresa tiene datos del vertical actual; auditoría con giro anterior/nuevo
    - _Requisitos: 3.1, 3.2, 3.3_
  - [x] 4.5 `GiroEmpresaPort` + `GiroEmpresaAdapter`
    - Puerto en `platform.security` (`giroDeTenant(tenantId)`) y adaptador en `platform.empresas` que lo resuelve desde la Empresa
    - _Requisitos: 2.4, 8.1, 8.2_
  - [x]* 4.6 Pruebas de ejemplo/borde del alta y cambio de Giro
    - giroId nulo/inexistente/inactivo → 422; giro activo → alta ok; cambio con datos → 422; cambio sin datos → ok
    - _Requisitos: 2.1, 2.2, 3.1, 3.2_
  - [x]* 4.7 IT de migración con Testcontainers
    - Empresas sin giro quedan con `anuncios-luminosos` y `giro_id NOT NULL`; no destructiva
    - _Requisitos: 11.2, 11.3, 11.5_

- [x] 5. Checkpoint — Empresa con Giro y migración
  - Ejecutar `mvn -o clean verify`; asegurar 0 fallos. Ante dudas, preguntar al usuario.

- [x] 6. Núcleo: Autorización consciente del Giro (doble gating y RBAC por giro)
  - [x] 6.1 `Autorizador.giroCorresponde(modulo)` y auditoría de denegación
    - Resolver giro del módulo vía `RegistroVerticales.giroDeModulo`; núcleo (vacío) → true; vertical → comparar con `GiroEmpresaPort`; deny-by-default; auditar denegación por giro
    - _Requisitos: 6.1, 6.2, 6.3, 6.4, 6.5_
  - [x]* 6.2 Prueba de propiedad: composición del doble gating
    - **Property 4: Composición del doble gating (Plan Y Giro)**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 12.4**
  - [x]* 6.3 Prueba de propiedad: el Núcleo nunca se bloquea por Giro
    - **Property 5: El Núcleo nunca se bloquea por Giro**
    - **Validates: Requirements 6.4**
  - [x] 6.4 `ClasificadorRecursosVertical` y validación de `Rol_Personalizado`
    - Clasificador análogo a `ClasificadorRecursosPlataforma` que resuelve recurso→giro vía `RegistroVerticales`; integrar en `ServicioRoles` para ofrecer solo permisos del giro y rechazar (422) permisos de vertical ajeno
    - _Requisitos: 7.1, 7.2, 7.3, 7.4, 7.5_
  - [x]* 6.5 Prueba de propiedad: clasificación de permisos por Giro
    - **Property 6: Los permisos aplicables se clasifican por Giro**
    - **Validates: Requirements 7.2, 7.3, 7.4**
  - [x]* 6.6 Regla ArchUnit: el Núcleo no depende de los verticales
    - `platform.*` no importa `vertical.*`
    - _Requisitos: 4.2, 5.4_

- [x] 7. Checkpoint — Doble gating y RBAC por giro
  - Ejecutar `mvn -o clean verify`; asegurar 0 fallos. Ante dudas, preguntar al usuario.

- [x] 8. Extracción del Vertical de Anuncios
  - [x] 8.1 Crear módulo `vertical.anuncios` y `AnunciosVertical` (contrato)
    - Nuevo paquete `com.dessti.crm.vertical.anuncios`; `AnunciosVertical implements ContratoVertical` declarando giro `anuncios-luminosos`, módulos, recursos y navegación
    - _Requisitos: 10.2, 4.1_
  - [x] 8.2 Mover flujo de Prueba_Diseno al vertical (consumo de comercial por puerto)
    - Mover `comercial/pruebadiseno` a `vertical.anuncios`; sustituir accesos directos a comercial por puertos del Núcleo
    - _Requisitos: 10.1, 10.3, 5.5_
  - [x] 8.3 Mover flujo operativo de anuncios al vertical
    - Mover `operacion/{ordenfabricacion,levantamiento,permiso,instalacion,proyecto}` y la parte vertical de `mantenimiento` a `vertical.anuncios`; conservar el inventario base en el Núcleo y consumirlo por puerto
    - _Requisitos: 10.1, 10.3, 5.5_
  - [x] 8.4 Aplicar doble gating en los endpoints del vertical
    - Añadir `@autorizador.giroCorresponde('anuncios')` junto al `moduloHabilitado`/`tiene` existentes en los controladores movidos
    - _Requisitos: 6.1, 6.2, 10.2_
  - [x] 8.5 Limpiar el Núcleo de referencias a anuncios
    - Eliminar del Núcleo el código/entidades específicos de anuncios que quedaron tras el movimiento; sin código huérfano
    - _Requisitos: 10.5, 13.3, 13.4_
  - [x]* 8.6 ArchUnit: aislamiento del vertical y núcleo limpio
    - Vertical no accede a persistencia interna del Núcleo (solo puertos); Núcleo no referencia entidades del vertical
    - _Requisitos: 4.5, 10.3, 10.5_
  - [x]* 8.7 IT de doble gating y RLS del vertical
    - Empresa de otro giro recibe 403 en operación de anuncios; entidad del vertical no visible cross-tenant
    - _Requisitos: 6.1, 8.3_

- [x] 9. Checkpoint — Vertical de Anuncios extraído (no regresión)
  - Ejecutar `mvn -o clean verify`; asegurar 0 fallos y comportamiento funcional preservado. Ante dudas, preguntar al usuario.
  - _Requisitos: 10.4_

- [x] 10. Frontend: navegación dinámica por Giro
  - [x] 10.1 Contexto de sesión con `giro` y navegación del vertical
    - Extender el endpoint de sesión/login del backend para incluir la clave de `giro` y la `navegacion` del vertical activo (desde el `ContratoVertical`)
    - _Requisitos: 9.1_
  - [~] 10.2 `AuthService` y `guardaGiro`
    - Signal `giro()` y helper `esGiro`; `guardaGiro(clave)` que redirige a `/acceso-denegado` si el giro no corresponde
    - _Requisitos: 9.1, 9.4_
  - [~] 10.3 `NavigationService`: items del vertical inyectados por Giro
    - Separar los items del vertical de `itemsEmpresa` (Núcleo); inyectar los del vertical activo desde el contexto; filtrar por permiso Y giro
    - _Requisitos: 9.2, 9.3_
  - [~] 10.4 `empresa.routes.ts`: proteger ramas del vertical con `guardaGiro`
    - Envolver las ramas del vertical de anuncios con `guardaGiro('anuncios-luminosos')`; ramas de Núcleo sin cambios; `/plataforma` y `/portal` independientes del giro
    - _Requisitos: 9.2, 9.4, 9.5_
  - [x]* 10.5 Prueba de propiedad: navegación sin verticales ajenos
    - **Property 7: La navegación no expone verticales ajenos (frontend)**
    - **Validates: Requirements 9.2, 9.3**

- [~] 11. Checkpoint — Frontend multigiro
  - Ejecutar `ng build` y `ng test`; asegurar 0 fallos. Ante dudas, preguntar al usuario.
  - _Requisitos: 13.2_

- [x] 12. Prueba del modelo: esqueleto del Vertical de Manufactura
  - [x]* 12.1 Esqueleto `vertical.manufactura` y `ManufacturaVertical` (contrato)
    - Nuevo paquete `com.dessti.crm.vertical.manufactura`; `ManufacturaVertical implements ContratoVertical` declarando giro `manufactura` con entidades de demostración (BOM, Orden_Produccion); consumir Núcleo solo por puertos
    - _Requisitos: 12.1, 12.2, 12.3_
  - [x]* 12.2 ArchUnit: verticales no dependen entre sí
    - `vertical.anuncios` no importa `vertical.manufactura` y viceversa
    - _Requisitos: 4.6_
  - [x]* 12.3 IT: coexistencia y aislamiento por giro entre dos verticales
    - Con anuncios y manufactura registrados, una empresa de un giro recibe 403 en operaciones del otro
    - _Requisitos: 12.4_

- [x] 13. Checkpoint final — Suite completa verde
  - Ejecutar `mvn -o clean verify` y `ng build` + `ng test`; asegurar 0 fallos, sin duplicación ni código huérfano. Ante dudas, preguntar al usuario.
  - _Requisitos: 13.1, 13.2, 13.3, 13.4_

## Notes

- Las subtareas marcadas con `*` son de prueba/demostración y opcionales para un MVP; el **esqueleto de Manufactura (tarea 12) es opcional** por decisión del usuario (prueba del modelo, no vertical funcional completo).
- Cada tarea referencia sus requisitos para trazabilidad; los checkpoints imponen la verificación por bloques (Req 13).
- Las pruebas de propiedad usan **jqwik** (ya en el proyecto), ≥100 iteraciones, y validan una propiedad de diseño cada una.
- El refactor (tareas 8) mueve código existente con cuidado, apoyado en ArchUnit y en la suite completa para garantizar no regresión.
- Todo el código, comentarios y mensajes de dominio en español (Req 13.5).

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1"] },
    { "id": 1, "tasks": ["1.2", "2.2", "2.4"] },
    { "id": 2, "tasks": ["1.3", "1.4", "2.3", "2.5"] },
    { "id": 3, "tasks": ["2.6", "2.7", "4.1"] },
    { "id": 4, "tasks": ["4.2", "4.5"] },
    { "id": 5, "tasks": ["4.3", "4.4"] },
    { "id": 6, "tasks": ["4.6", "4.7", "6.1", "6.4"] },
    { "id": 7, "tasks": ["6.2", "6.3", "6.5", "6.6"] },
    { "id": 8, "tasks": ["8.1"] },
    { "id": 9, "tasks": ["8.2", "8.3"] },
    { "id": 10, "tasks": ["8.4", "8.5"] },
    { "id": 11, "tasks": ["8.6", "8.7", "10.1"] },
    { "id": 12, "tasks": ["10.2", "10.3"] },
    { "id": 13, "tasks": ["10.4", "10.5"] },
    { "id": 14, "tasks": ["12.1"] },
    { "id": 15, "tasks": ["12.2", "12.3"] }
  ]
}
```
