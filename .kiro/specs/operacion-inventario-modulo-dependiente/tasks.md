# Plan de Implementación

_(Operación e Inventario como bloque dependiente — rediseño enterprise de Inventario Avanzado, dependencia de módulos `inventario-avanzado` → `operacion`, y término del módulo Operación/Materiales)_

## Overview

Plan incremental orientado a código, en el **orden de prioridad del usuario**: primero el **Bloque 1** (rediseño enterprise de Inventario Avanzado — el tab Movimientos deja de "encimarse"), luego el **Bloque 2** (declaración de la dependencia de módulos + normalización al persistir + backfill Flyway V65 + UX del super_admin), y por último el **Bloque 3** (término de la vista Materiales). Cada tarea de implementación se empareja con su(s) tarea(s) de prueba; las pruebas de lógica pura del Bloque 2 usan **jqwik** (property-based, ≥100 iteraciones) para las 6 propiedades del diseño, y el frontend se cubre con **specs de componente one-shot**.

**Ya resuelto — NO rehacer (solo verificación de no-regresión donde aplique):**
- Fix de auditoría JSONB en `backend/.../operacion/inventario/avanzado/application/ServicioInventarioAvanzado.java` (`auditar`/`aJson`). No se toca (Req 15.4).
- Guardas de acceso vigentes (`guardaModulo`, `guardaModuloAlguno`, `guardaGiro`, `guardaPorPermiso`). No se modifican; posible simplificación de `guardaModuloAlguno` queda documentada, **no aplicada** (Req 15.5).
- Los 13 endpoints de Inventario Avanzado y `POST /api/v1/materiales`: contratos verificados, no cambian. Solo se agrega el endpoint de solo lectura de dependencias.
- Estrategia sin-UUIDs (`NombresInventarioService`) y modelos de inventario del frontend: completos, se preservan.

**Comandos de verificación del proyecto (usar como notas, no como tareas triviales):**
- Backend: compilar `mvn -o compile`; empaquetar `mvn -o package -DskipTests`; pruebas `mvn -o test` (o una clase con `-Dtest=Clase`). Ejecutar en `backend/`.
- Frontend: build de producción `ng build --configuration production`; spec en aislamiento `ng test --watch=false --include=<ruta-spec>` (NUNCA la suite completa, por flakiness de axe). Ejecutar en `frontend/`.

## Tasks

- [x] 1. BLOQUE 1 — Rediseño enterprise de Inventario Avanzado (frontend)

  - [x] 1.1 Rediseñar el tab Movimientos a un formulario único con selector de tipo
    - En `frontend/src/app/features/operacion/inventario-avanzado/inventario-avanzado.ts`: agregar signal `tipoMovimiento: WritableSignal<'entrada'|'salida'|'transferencia'>` (default `'entrada'`) y un despachador `registrarMovimiento()` que haga `switch (tipoMovimiento())` y reutilice los `FormGroup` y métodos existentes (`formEntrada`/`formSalida`/`formTransferencia`, `registrarEntrada/Salida/Transferencia`). No cambiar contratos de servicio ni la validación existente (incluida "origen ≠ destino" y el manejo del 422 por existencias insuficientes).
    - En `inventario-avanzado.html`: sustituir las tres `mat-card` de Movimientos por **un** `mat-card` "Registrar movimiento" con un `mat-button-toggle-group` (Entrada/Salida/Transferencia) enlazado a `tipoMovimiento`, y render condicional de campos con `@if`/`@switch`. Botón único "Registrar movimiento" visible completo en `operacion-form__acciones`.
    - En `inventario-avanzado.scss`: **eliminar** `&__movimientos` (grid `repeat(auto-fit, minmax(300px,1fr))`) y añadir estilos del formulario único con **solo design tokens** (`--ds-space-*`, `--ds-radius-*`, etc.); una columna en `bp.until(md)`, grid de 2 columnas con `minmax` amplio en escritorio; ningún campo/botón recortado.
    - Accesibilidad: grupo de tipo como `role="group"` con `aria-label="Tipo de movimiento"`, toggle activo con `aria-pressed`; orden de foco tipo → campos → botón.
    - _Requisitos: 2.1, 2.2, 2.3, 2.4, 2.5, 1.2_

  - [x]* 1.2 Spec one-shot del tab Movimientos rediseñado
    - Crear/actualizar `frontend/src/app/features/operacion/inventario-avanzado/inventario-avanzado.spec.ts`: verificar que Movimientos muestra **un** solo formulario con selector de tipo; que cambiar `tipoMovimiento` cambia los campos visibles; que enviar invoca el método correcto (`registrarEntrada`/`Salida`/`Transferencia`); que no aparece ningún UUID en el DOM; y axe sin violaciones en el tab.
    - Ejecutar en aislamiento: `ng test --watch=false --include=src/app/features/operacion/inventario-avanzado/inventario-avanzado.spec.ts`.
    - _Requisitos: 2.1, 2.2, 2.4, 4.1, 3.6, 15.2_

  - [x] 1.3 Resumen como dashboard con KPIs y estados vacíos descriptivos
    - En `inventario-avanzado.{ts,html,scss}`: estructurar el tab Resumen como tablero operativo — fila de KPIs con `StatCard` (Almacenes, Stock bajo, **Por reabastecer** cuando la config esté disponible, Valor del inventario cargado conservando su nota de "sobre lo paginado") + panel de alertas de stock bajo accionables. Usar `StateContainer` para carga/vacío/error; ningún cero engañoso (estado vacío descriptivo en es-MX cuando falten datos).
    - _Requisitos: 3.1, 3.2, 3.4, 3.5_

  - [x] 1.4 Pulir Existencias, Kardex, Lotes, Configuración y Almacenes al estándar enterprise
    - Existencias: tabla con nombre de Almacén/Material (vía `NombresInventarioService`), cantidad, costo promedio, resalte de stock bajo con icono+color-token (no solo color), filtros por nombre y acción "Ver Kardex".
    - Kardex: selectores Almacén+Material por nombre; tabla cronológica con tipo en etiqueta es-MX; estado vacío que invita a elegir Almacén y Material.
    - Lotes: estados de caducidad `Vigente` / `Próximo a caducar` (≤30 días) / `Caducado` / `Sin caducidad` con icono + color-token (conservar `estadoLote`/`etiquetaEstadoLote` existentes).
    - Configuración: etiquetas es-MX de costeo ("Promedio ponderado" / "PEPS"), validaciones `min(0)` antes de enviar; mostrar punto de reorden calculado del `PUT`; conservar datos capturados ante error (es-MX).
    - Almacenes: listado y alta/edición sin UUIDs.
    - Todo con solo design tokens, es-MX, `StateContainer`, WCAG AA (contraste, foco visible, orden de tabulación, nombres accesibles). No introducir nuevos hardcodes; conservar los `var(--x, #hex)` de fallback existentes.
    - _Requisitos: 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5, 3.3, 3.5, 3.6, 1.3, 1.4, 1.5_

  - [x] 2. Checkpoint Bloque 1 — Asegurar que las pruebas pasan
    - Ejecutar `ng build --configuration production` (0 errores) y el spec de 1.2 en aislamiento. Si surgen dudas, preguntar al usuario antes de continuar al Bloque 2.

- [x] 3. BLOQUE 2 — Dependencia de módulos `inventario-avanzado` → `operacion`

  - [x] 3.1 Declarar la dependencia: `CatalogoDependenciasModulos`
    - Crear `backend/src/main/java/com/dessti/crm/platform/modulos/CatalogoDependenciasModulos.java`: mapa estático `{"inventario-avanzado" → {"operacion"}}`, `requeridosDe(String)` (clave normalizada, o vacío) y `normalizar(Collection<String>)` que calcule el cierre transitivo, sea idempotente, normalice claves (recorte + minúsculas), preserve el orden de inserción y no duplique. Diseñado para agregar futuras dependencias sin cambiar la regla existente.
    - _Requisitos: 6.1, 6.3_

  - [x]* 3.2 PBT: normalización agrega `operacion`, es idempotente y unidireccional
    - En `backend/src/test/java/com/dessti/crm/platform/modulos/CatalogoDependenciasModulosPropertyTest.java` (jqwik, `@Property` con `@Tries(100)` mínimo cada uno):
      - **Property 1: La normalización agrega `operacion` cuando hay `inventario-avanzado`** — `Feature: operacion-inventario-modulo-dependiente, Property 1: ...`. _Valida: 6.1, 7.1, 7.2, 8.1_
      - **Property 2: La normalización es idempotente** (`normalizar(x) == normalizar(normalizar(x))`, sin duplicar `operacion`) — `Feature: operacion-inventario-modulo-dependiente, Property 2: ...`. _Valida: 7.3, 8.3, 10.4_
      - **Property 3: La dependencia es unidireccional y preserva el resto** (`operacion` sin `inventario-avanzado` no agrega `inventario-avanzado`; se conservan los demás módulos) — `Feature: operacion-inventario-modulo-dependiente, Property 3: ...`. _Valida: 7.4, 10.5_
    - _Requisitos: 6.1, 7.1, 7.2, 7.3, 7.4, 8.1, 8.3, 10.4, 10.5_

  - [x]* 3.3 Unit tests de ejemplos/bordes de `CatalogoDependenciasModulos`
    - En `CatalogoDependenciasModulosTest.java`: casos base, claves con espacios/mayúsculas (normalización), conjunto vacío/nulo, `operacion` ya presente (no duplica), preservación de orden.
    - _Requisitos: 6.1, 6.3, 7.3, 8.3_

  - [x] 3.4 Endpoint consultable `GET /plataforma/dependencias-modulos`
    - Extender `backend/src/main/java/com/dessti/crm/platform/modulos/rest/ModuloController.java` (o clase hermana en el mismo paquete `platform.modulos.rest`) para exponer `GET /plataforma/dependencias-modulos` guardado con `plan:listar` (mismo permiso que `/plataforma/modulos`), devolviendo un DTO `Map<String,List<String>>` derivado de `CatalogoDependenciasModulos` (p. ej. `{"inventario-avanzado":["operacion"]}`; `{}` si vacío).
    - _Requisitos: 6.2_

  - [x]* 3.5 Unit test de rebanada del endpoint de dependencias
    - En el estilo de `ModuloControllerTest.java`: `GET /plataforma/dependencias-modulos` con `super_admin` devuelve 200 y el mapa esperado; sin permiso `plan:listar` devuelve 403.
    - _Requisitos: 6.2_

  - [x] 3.6 Normalizar al persistir Plan y Paquete de Suscripción
    - En `backend/.../platform/empresas/Plan.java` (`aplicarPrecios`): tras construir el mapa normalizado de precios, si las claves contienen `inventario-avanzado` y NO `operacion`, agregar `operacion` con precio `0.00` (escala monetaria); respetar el precio capturado si ya existe la clave; derivar `modulos_habilitados` de las claves de `precios_modulos`. Aplica a crear y actualizar (ambos pasan por `aplicarPrecios`).
    - Replicar el mismo cambio en `backend/.../platform/empresas/PaqueteSuscripcion.java` (`aplicarPrecios`).
    - _Requisitos: 7.1, 7.2, 7.3, 7.4, 8.1_

  - [x]* 3.7 PBT: persistir Plan/Paquete con `inventario-avanzado` habilita `operacion`
    - Test jqwik (≥100 iteraciones) en `backend/src/test/java/com/dessti/crm/platform/empresas/PlanDependenciaPropertyTest.java` (y equivalente para `PaqueteSuscripcion`):
      - **Property 4: Persistir un Plan/Paquete con `inventario-avanzado` habilita `operacion`** — para todo Plan/Paquete construido con `inventario-avanzado`, `getModulosHabilitados()` incluye `operacion` y las claves de `precios_modulos` coinciden exactamente con `modulos_habilitados`. Etiqueta `Feature: operacion-inventario-modulo-dependiente, Property 4: ...`.
    - _Requisitos: 7.1, 7.2, 8.1_

  - [x]* 3.8 Unit tests de ejemplos/bordes de Plan/Paquete
    - Plan/Paquete con y sin `inventario-avanzado`; con `operacion` preexistente (no duplica, respeta precio capturado); solo `operacion` (no agrega `inventario-avanzado`).
    - _Requisitos: 7.1, 7.3, 7.4, 8.1_

  - [x] 3.9 Normalizar el override de Empresa (Suscripción) antes de validar subconjunto
    - En `backend/.../platform/empresas/Suscripcion.java` (`asignarModulos`) o en `backend/.../platform/empresas/ServicioSuscripciones.java` (`actualizarModulosEmpresa`): aplicar `CatalogoDependenciasModulos.normalizar(modulos)` cuando `modulos != null` **antes** de `exigirSubconjuntoDelInstrumento`, validando contra el instrumento **ya normalizado** (que incluye `operacion`), de modo que un override con `inventario-avanzado` no sea rechazado por la dependencia agregada.
    - _Requisitos: 8.1, 8.3, 8.4_

  - [x] 3.10 (D7-b) Normalización defensiva idempotente en el claim
    - En `backend/.../platform/empresas/PlanModulosPlanAdapter.java` (`modulosHabilitadosDe`): aplicar `CatalogoDependenciasModulos.normalizar(...)` sobre la lista efectiva antes de devolverla (red de seguridad ante datos legados). Idempotente; no altera el comportamiento cuando la fuente ya está normalizada.
    - _Requisitos: 8.2, 8.4, 11.1, 11.2_

  - [x]* 3.11 PBT: el claim efectivo incluye `operacion` mientras haya `inventario-avanzado`
    - Test jqwik (≥100 iteraciones) en `backend/src/test/java/com/dessti/crm/platform/empresas/PlanModulosPlanAdapterPropertyTest.java`:
      - **Property 5: El claim efectivo incluye `operacion` mientras haya `inventario-avanzado`** — para toda suscripción cuya lista efectiva (override o herencia) incluya `inventario-avanzado`, `modulosHabilitadosDe(tenant)` incluye `operacion`. Etiqueta `Feature: operacion-inventario-modulo-dependiente, Property 5: ...`.
    - _Requisitos: 8.2, 8.4, 11.1, 11.2_

  - [x]* 3.12 Unit tests de override y claim (ejemplos/bordes)
    - `ServicioSuscripciones.actualizarModulosEmpresa` con override que incluye `inventario-avanzado` (agrega `operacion`, pasa validación de subconjunto; 422 si el instrumento no ofrece el módulo). `PlanModulosPlanAdapter`: override presente vs herencia; claim incluye `operacion`.
    - _Requisitos: 8.1, 8.2, 8.4, 11.1, 11.2, 11.3_

  - [x] 3.13 Migración Flyway V65 de backfill idempotente
    - Antes de escribir el SQL, **verificar los nombres de columna** contra `V21` y `V64` (`plan.modulos_habilitados`/`precios_modulos`, `paquete_suscripcion.modulos_habilitados`/`precios_modulos`, `suscripcion.modulos_habilitados` override nullable).
    - Crear `backend/src/main/resources/db/migration/V65__dependencia_operacion_inventario_avanzado.sql`: para `plan` y `paquete_suscripcion` que contengan `inventario-avanzado` y no `operacion`, agregar `operacion` al array `modulos_habilitados` (`|| '["operacion"]'::jsonb`) y la clave a `precios_modulos` con `0.00` (`jsonb_set(..., true)`); para `suscripcion` con override no nulo, agregar solo al array. Predicado idempotente `WHERE ... @> '["inventario-avanzado"]' AND NOT (... @> '["operacion"]')`.
    - _Requisitos: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [x]* 3.14 PBT + prueba de idempotencia del backfill
    - **Property 6: El backfill es idempotente y preserva los demás módulos** — test jqwik (≥100 iteraciones) sobre la transformación pura equivalente al SQL: para todo array inicial, aplicar el backfill una o más veces produce el mismo resultado (agrega `operacion` exactamente una vez si había `inventario-avanzado` sin `operacion`; conserva el resto sin duplicados). Etiqueta `Feature: operacion-inventario-modulo-dependiente, Property 6: ...`.
    - Prueba de integración de la migración: aplicar V65 sobre un dataset mixto (planes/paquetes/suscripciones con y sin la clave, **incluyendo el caso tester** `cf1acdb6-75e6-40f4-a28d-d364142fbfbe` con `["comercial","inventario-avanzado","estrategia","redes-sociales"]`), verificar el resultado y **reaplicar la sentencia** confirmando que no cambia.
    - _Requisitos: 10.1, 10.2, 10.3, 10.4, 10.5, 11.2_

  - [x] 3.15 Frontend super_admin: servicio y tipo de dependencias
    - En `frontend/src/app/features/plataforma/services/planes.service.ts`: agregar `listarDependenciasModulos()` → `GET /plataforma/dependencias-modulos`. En `frontend/src/app/features/plataforma/models/plataforma.models.ts`: agregar `type DependenciasModulos = Record<string, string[]>`.
    - _Requisitos: 6.2, 9.4_

  - [x] 3.16 Frontend super_admin: aviso y bloqueo de deselección en diálogos
    - En `frontend/src/app/features/plataforma/planes/plan-dialog.ts` (+ `.html`), `frontend/src/app/features/plataforma/planes/paquete-suscripcion-dialog.ts` (+ `.html`) y `frontend/src/app/features/plataforma/empresas/plan-suscripcion-dialog.ts` (override): cargar el mapa con `listarDependenciasModulos()` a un signal `dependencias`; al marcar `inventario-avanzado` marcar también `operacion` (precio 0.00 por defecto donde aplique) y mostrar aviso es-MX derivado del mapa (_"Inventario avanzado depende de Operación..."_); bloquear el desmarcado de `operacion` mientras `inventario-avanzado` siga marcado, con motivo es-MX. Todo derivado del mapa, **sin hardcode** (`computed requeridosBloqueados()`; `alternar(clave, seleccionado)` rechaza el desmarcado de requeridos).
    - _Requisitos: 9.1, 9.2, 9.3, 9.4_

  - [x]* 3.17 Specs one-shot de los diálogos del super_admin
    - `frontend/src/app/features/plataforma/planes/plan-dialog.spec.ts` y `paquete-suscripcion-dialog.spec.ts`: con mapa de dependencias mockeado, marcar `inventario-avanzado` marca `operacion` y muestra el aviso; intentar desmarcar `operacion` con `inventario-avanzado` activo lo impide y muestra el motivo; el aviso deriva del mapa (no hardcode).
    - Ejecutar en aislamiento con `ng test --watch=false --include=<ruta-spec>`.
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 15.2_

- [x] 4. Checkpoint Bloque 2 — Asegurar que las pruebas pasan
    - Ejecutar `mvn -o test` de las clases tocadas (dependencia, Plan/Paquete, Suscripción/adaptador, migración) y `mvn -o package -DskipTests`. Ejecutar los specs de 3.17 en aislamiento. Si surgen dudas, preguntar al usuario.

- [x] 5. BLOQUE 3 — Terminar el módulo Operación (Materiales / inventario base)

  - [x] 5.1 Rediseñar la vista Materiales al estándar enterprise
    - En `frontend/src/app/features/operacion/materiales/materiales.{ts,html,scss}`: alinear al lenguaje visual de Inventario Avanzado — solo design tokens (`_tokens.scss`), `StateContainer` para carga/vacío/error, sin UUIDs (columnas nombre/código/unidad/existencias/stock mínimo/estado), es-MX, WCAG AA. Alta/edición vía `MaterialesService` sobre `/materiales` (permisos `material:crear`/`material:listar`); movimientos base por nombre. Errores es-MX conservando lo capturado.
    - En las secciones del Inventario Avanzado que dependen de Materiales (`inventario-avanzado.html`): cuando no haya Materiales, estado vacío descriptivo es-MX que oriente a crear Materiales primero.
    - _Requisitos: 12.1, 12.2, 12.3, 12.4, 12.5, 13.1, 13.2, 13.3, 13.4, 14.1, 14.2, 14.3_

  - [x]* 5.2 Spec one-shot de Materiales
    - `frontend/src/app/features/operacion/materiales/materiales.spec.ts`: estados de carga/vacío/error; sin UUIDs en el DOM; axe sin violaciones. Ejecutar en aislamiento con `ng test --watch=false --include=<ruta-spec>`.
    - _Requisitos: 12.1, 12.4, 12.5, 13.2, 15.2_

- [x] 6. Verificación final integral y no-regresión
    - Backend: `mvn -o package -DskipTests` (0 errores) y `mvn -o test` verde de las clases tocadas (dependencia, Plan/Paquete, Suscripción, adaptador, endpoint y migración); la suite existente permanece verde.
    - Frontend: `ng build --configuration production` (0 errores) y los specs tocados en aislamiento (1.2, 3.17, 5.2) — nunca la suite completa por flakiness de axe.
    - No-regresión: guardas de acceso sin cambios; fix de auditoría JSONB intacto; contratos de los 13 endpoints de Inventario Avanzado y `POST /materiales` sin cambios; UI en es-MX, tokens, responsiva, WCAG AA, sin UUIDs. Limpiar cualquier archivo temporal de verificación.
    - _Requisitos: 15.1, 15.2, 15.3, 15.4, 15.5, 11.2_

## Notes

- Tareas marcadas con `*` son de prueba (opcionales para un MVP rápido, pero el usuario pidió incluirlas).
- Orden de prioridad: Bloque 1 → Bloque 2 → Bloque 3, con checkpoints entre bloques.
- PBT del Bloque 2 con **jqwik** (ya presente en `backend/`, ver `.jqwik-database`), mínimo **100 iteraciones** por propiedad; cada test etiquetado `Feature: operacion-inventario-modulo-dependiente, Property N: ...`. El frontend (layout/UX) no usa PBT: specs de componente one-shot + build.
- NO rehacer: fix JSONB de auditoría, guardas de acceso, endpoints existentes. `guardaModuloAlguno` se deja como está (simplificación solo documentada).
- Verificar nombres de columna JSONB contra V21/V64 antes de escribir V65.
- Archivos UTF-8 sin BOM; español es-MX en toda la UI.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "3.1", "3.13"] },
    { "id": 1, "tasks": ["1.2", "1.3", "3.2", "3.3", "3.4", "3.6", "3.15"] },
    { "id": 2, "tasks": ["1.4", "3.5", "3.7", "3.8", "3.9", "3.14", "3.16"] },
    { "id": 3, "tasks": ["3.10", "3.17", "5.1"] },
    { "id": 4, "tasks": ["3.11", "3.12", "5.2"] }
  ]
}
```
