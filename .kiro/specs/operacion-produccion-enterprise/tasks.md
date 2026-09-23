# Plan de Implementación

_(Operación y Producción — Enterprise: generalización del núcleo de `operacion` (OF y Proyectos/Sitios al Núcleo sin amarre a giro), cableado del consumo de materiales, exposición de datos ya persistidos, UX enterprise, disparo y notificación real de vencimientos de permisos, y calidad/no-regresión.)_

## Overview

Plan incremental orientado a código, en el orden de las dependencias técnicas del diseño (Decisiones D1–D8). Se ejecuta en cinco bloques:

- **BLOQUE A** — Generalización del núcleo: mover OF a `com.dessti.crm.operacion.produccion` y Proyecto/Sitio a `com.dessti.crm.operacion.proyecto` (refactor de renombrado de paquete, sin cambiar rutas REST ni tablas), relajar el gating por giro, origen genérico de la OF, `ClienteExistentePort`, y derivación de estado de Proyecto por fases aplicables.
- **BLOQUE B** — Consumo de materiales: partidas/BOM (`partida_orden_fabricacion`) editables solo en `pendiente`, y consumo atómico en la transición `pendiente → en_produccion` vía `ConsumoMaterialPort` (que **ya está implementado** en `ServicioInventario`; aquí solo se **cablea**).
- **BLOQUE C** — Exposición de datos ya persistidos: fotos de Levantamiento y pendientes/evidencias de OTI; guarda de cierre de OTI que enumera los pendientes no resueltos.
- **BLOQUE D** — Frontend enterprise: vistas de detalle (patrón `proyecto-detalle`), `NombresOperacionService` (sin UUIDs), filtros por nombre, `agregarFotos`.
- **BLOQUE E** — Notificación de vencimiento de permisos: notificador real integrado con `notificaciones`, planificador diario multi-tenant y endpoint manual.

Cada tarea de implementación se empareja con su(s) tarea(s) de prueba. Las 6 propiedades de corrección del diseño se implementan con **jqwik** (ya presente en `backend/`, ver `.jqwik-database`), mínimo **100 iteraciones** por propiedad, cada test etiquetado `Feature: operacion-produccion-enterprise, Property N: ...`. El frontend se cubre con **specs de componente one-shot** (axe en un solo intento).

**Ya resuelto — NO rehacer (solo verificación/cableado donde aplique):**
- **Auditoría JSONB** en `ServicioAuditoria` como punto único (`aJson`): no se toca (Req 16.3). Las acciones nuevas auditan por ese mismo camino.
- **Consumo de materiales** en `ServicioInventario` (`ConsumoMaterialPort.consumirParaOrdenFabricacion`): ya implementado, atómico y probado. **Solo se cablea** desde producción; no se reimplementa (Req 6).
- **Dependencia de módulos** `inventario-avanzado → operacion`: se preserva sin cambios (Req 16.4).
- **`DerivacionEstadoProyecto`** existente es pura; se **generaliza** (nueva firma) conservando la firma vieja como conveniencia; no se reescribe su semántica de anuncios (Req 3.5).
- **Rutas REST y nombres de tabla** (`/ordenes-fabricacion`, `/proyectos`, `orden_fabricacion`, `proyecto`, `sitio`): no cambian; solo cambia el paquete Java y el texto SpEL de los `@PreAuthorize` (Req 16.2).

**Comandos de verificación del proyecto (notas, no tareas triviales):**
- `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot`; Maven en `C:\apache-maven-3.9.10\bin\mvn.cmd`.
- Backend (en `backend/`): compilar `mvn -o compile`; empaquetar `mvn -o package -DskipTests`; una clase `mvn -o test -Dtest=Clase`. Ante staleness de pruebas: borrar `target\test-classes` y recompilar.
- Frontend (en `frontend/`): build de producción `ng build --configuration production`; spec en aislamiento `ng test --watch=false --include=<ruta-spec>` (NUNCA la suite completa, por flakiness de axe).

## Tasks

- [x] 1. BLOQUE A — Mover OF y Proyecto/Sitio al Núcleo (refactor de renombrado de paquete)

  - [x] 1.1 Mover Orden de Fabricación al Núcleo `operacion.produccion`
    - Refactor de **renombrado de paquete** (actualiza imports automáticamente, NO reescritura) de toda la capa hexagonal de OF desde `com.dessti.crm.vertical.anuncios.ordenfabricacion.{adapter/in/rest,adapter/out/persistence,application,domain}` a `com.dessti.crm.operacion.produccion.*`. Incluye `OrdenFabricacion`, `EstadoOrdenFabricacion`, `ServicioOrdenesFabricacion`, `OrdenFabricacionController`, repositorio/entidad JPA, DTOs y el puerto `OrdenFabricacionTerminadaPort` + su adaptador (interfaz estable consumida por instalación).
    - NO cambiar: nombre de tabla `orden_fabricacion`, ruta REST `/ordenes-fabricacion`, nombres de clase, ni el comportamiento del servicio/entidad. Solo cambia el paquete.
    - Cambio de amplio alcance (muchos imports en instalación, proyecto y tests). Es una tarea **temprana y secuencial**; no debe correr en paralelo con tareas que editan estos mismos archivos.
    - Verificar `mvn -o compile` (0 errores) tras el movimiento; si hay staleness, borrar `target\test-classes`.
    - _Requisitos: 16.2, 16.4_

  - [x] 1.2 Mover Proyecto/Sitio al Núcleo `operacion.proyecto`
    - Refactor de **renombrado de paquete** de la capa hexagonal de Proyecto y Sitio desde `com.dessti.crm.vertical.anuncios.proyecto.*` a `com.dessti.crm.operacion.proyecto.*`. Incluye `Proyecto`, `Sitio`, `ServicioProyectos`, controlador, repositorios/entidades, `EstadoConsolidadoProyecto`, `DerivacionEstadoProyecto`, `AvanceFasesSitio`, `AvanceSitioPort` y `AvanceSitioAdapter` (este último se dividirá en 1.9/1.10).
    - NO cambiar: nombres de tabla `proyecto`/`sitio`, ruta REST `/proyectos`, nombres de clase ni comportamiento. Solo el paquete.
    - Tarea temprana y **secuencial respecto a 1.1** (comparten consumidores/imports; el `AvanceSitioAdapter` referencia el puerto de producción movido en 1.1). No paralelizar con tareas que editan estos archivos.
    - Verificar `mvn -o compile` (0 errores); ante staleness, borrar `target\test-classes`.
    - _Requisitos: 16.2, 16.4_

  - [x] 1.3 Relajar el gating por giro en los `@PreAuthorize` de OF y Proyecto
    - En `OrdenFabricacionController` (Núcleo `produccion`) y en el controlador de Proyecto (Núcleo `proyecto`): **retirar** `and @autorizador.giroCorresponde('operacion')` de todos los endpoints, dejando `@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('<recurso>','<op>')` (patrón de `MaterialController`). Conservar módulo + permiso atómico + RLS.
    - NO tocar el gating de Levantamiento, Permiso y OTI (conservan `giroCorresponde('operacion')`, Req 16.6).
    - _Requisitos: 2.1, 2.2, 2.3, 2.4, 3.1, 16.5, 16.6_

  - [x] 1.4 Reclasificar `orden_fabricacion` y `proyecto` como recursos de Núcleo
    - En `AnunciosVertical`: **retirar** `orden_fabricacion` y `proyecto` del conjunto de `recursos()`. Conservar `prueba_diseno`, `levantamiento_sitio`, `permiso_instalacion`, `orden_trabajo_instalacion`, `cuadrilla`. NO tocar `modulos()` (`operacion` permanece) ni `navegacion()`.
    - Verificar que `RegistroVerticales` arranca sin fallar (no se introducen duplicados; `giroDeModulo('operacion')` sigue determinista).
    - _Requisitos: 2.1, 3.1, 16.6_

  - [x]* 1.5 Prueba de arranque/registro de verticales sin regresión
    - Test que verifica que `RegistroVerticales` inicializa sin excepción tras retirar los recursos, que `giroDeRecurso('orden_fabricacion')`/`('proyecto')` quedan sin giro (Núcleo) y que `giroDeRecurso('levantamiento_sitio')` sigue mapeando a anuncios.
    - _Requisitos: 2.1, 3.1, 16.6_

  - [x] 1.6 `ClienteExistentePort` + `ClienteExistenteAdapter`
    - Definir la interfaz `ClienteExistentePort { boolean existeEnTenant(UUID clienteId); }` en la capa `application` del Núcleo (consumible por `produccion` y `proyecto`).
    - Implementar `ClienteExistenteAdapter` en el módulo **comercial** (donde vive Cliente): consulta el repositorio de Cliente acotado al tenant (RLS + `TenantContext`), devuelve `true`/`false` de solo lectura, sin exponer la entidad Cliente fuera de comercial (Req 16.4).
    - _Requisitos: 4.1, 1.5, 16.4_

  - [x]* 1.7 PBT: verificación de existencia del Cliente
    - `backend/src/test/java/com/dessti/crm/operacion/proyecto/application/VerificacionClientePropertyTest.java` (jqwik, `@Property` con ≥100 iteraciones), usando un `ClienteExistentePort` fake:
      - **Property 6: Verificación de existencia del Cliente** — `Feature: operacion-produccion-enterprise, Property 6: para todo clienteId (nulo → 422 sin persistir; inexistente/no accesible → 404 sin persistir; existente → crea el recurso)`.
    - Cubre `ServicioProyectos.crear` y `ServicioOrdenesFabricacion.crearDirecta` (mismo puerto).
    - _Requisitos: 4.1, 4.2, 4.3, 1.4, 1.5_

  - [x] 1.8 Origen genérico de la OF: factoría `crearDirecta` + `crearDirecta` en el servicio + endpoint
    - En `OrdenFabricacion` (Núcleo `produccion`): quitar `updatable=false`/obligatoriedad de columna de `cotizacionId` (sigue inmutable tras creación); nueva factoría de dominio `crearDirecta(UUID clienteId, String actor)` que crea la OF en `pendiente` con `cotizacionId = null` y `clienteId` obligatorio. **Conservar** `generar(cotizacionId, clienteId, actor)` sin cambios.
    - En `ServicioOrdenesFabricacion`: nuevo `crearDirecta(CrearOrdenDirectaCommand)` — 422 si `clienteId` nulo (sin persistir); verifica Cliente vía `ClienteExistentePort` (404 + auditoría de acceso cruzado con recurso `cliente`); crea la OF, persiste, persiste sus partidas iniciales (§B1, tarea 3.1) y audita la creación con recurso `orden_fabricacion`. **Conservar** `generar(cotizacionId)` con sus 3 precondiciones.
    - En `OrdenFabricacionController`: **conservar** `POST /ordenes-fabricacion` (`{ cotizacionId }`); agregar `POST /ordenes-fabricacion/directa` (`{ clienteId, partidas: [{ materialId, cantidad }] }`) → 201 con el DTO; 422 si falta Cliente o cantidad ≤ 0; 404 si Cliente/Material no accesibles.
    - _Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_

  - [x]* 1.9 Controller test: OF genérica accesible por giro no-anuncios + no-regresión de anuncios
    - Test de controlador: usuario de un giro **no-anuncios** con módulo `operacion` + permiso `orden_fabricacion:crear` crea una OF directa (201). Verifica que el flujo de anuncios conserva sus **3 precondiciones** en orden (Cotización existente/`aprobada`, sin OF previa vía índice único, Prueba_Diseño `aprobada`).
    - _Requisitos: 1.1, 1.3, 1.6, 2.1, 14.4, 16.1_

  - [x]* 1.10 Service/controller test: verificación de Cliente (404/422) en Proyecto y OF directa
    - Ejemplos y bordes complementarios a la Property 6: Cliente inexistente → 404 + auditoría con recurso `cliente`; `clienteId` nulo → 422 sin persistir; Cliente existente → creado (Proyecto en `SIN_SITIOS`, OF directa en `pendiente`).
    - _Requisitos: 4.2, 4.3, 4.4, 1.4, 1.5, 14.5_

  - [x] 1.11 Derivación por fases aplicables: `PerfilFasesGiro`, `FaseProyecto` y `DerivacionEstadoProyecto.derivar(perfil, avances)`
    - En `operacion.proyecto.domain`: `enum FaseProyecto { LEVANTAMIENTO, PERMISO, PRODUCCION, INSTALACION }`; `record PerfilFasesGiro(Set<FaseProyecto> fasesAplicables)` con `ANUNCIOS` (las cuatro fases) y `GENERICO` (solo `PRODUCCION`).
    - Nueva firma pura `EstadoConsolidadoProyecto derivar(PerfilFasesGiro perfil, List<AvanceFasesSitio> avances)`: lista vacía → `SIN_SITIOS`; recorre las fases en orden pero solo las de `perfil.fasesAplicables()`, devuelve la primera fase aplicable no cubierta por todos los Sitios; todas cubiertas → `COMPLETADO`. **Conservar** `derivar(avances)` como conveniencia delegando en `derivar(ANUNCIOS, avances)` para no romper llamadores/tests.
    - _Requisitos: 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x]* 1.12 PBT: derivación por fases aplicables y equivalencia por perfil
    - `backend/src/test/java/com/dessti/crm/operacion/proyecto/domain/DerivacionEstadoProyectoPropertyTest.java` (jqwik, ≥100 iteraciones), generadores de `List<AvanceFasesSitio>` (banderas aleatorias) y de `PerfilFasesGiro`:
      - **Property 1: Derivación del estado de Proyecto por fases aplicables** — `Feature: operacion-produccion-enterprise, Property 1: vacío→SIN_SITIOS; COMPLETADO sii todos cubren todas las fases aplicables; si no, primera fase aplicable no cubierta; determinista`.
      - **Property 2: Equivalencia perfil ANUNCIOS vs clásico y GENERICO=solo producción** — `Feature: operacion-produccion-enterprise, Property 2: derivar(ANUNCIOS, avances) == derivación clásica; derivar(GENERICO, avances) depende solo de PRODUCCION (todos con OF terminada→COMPLETADO; alguno sin ella→EN_PRODUCCION)`.
    - _Requisitos: 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x]* 1.13 Unit tests de ejemplo de la derivación (ANUNCIOS / GENERICO)
    - Ejemplos concretos: proyecto anuncios con avance parcial en cada fase; proyecto genérico con todos los Sitios con OF terminada (`COMPLETADO`) y con alguno sin ella (`EN_PRODUCCION`); proyecto sin Sitios (`SIN_SITIOS`) para ambos perfiles.
    - _Requisitos: 3.2, 3.4, 3.5, 14.2_

  - [x] 1.14 `PerfilFasesGiroPort` + resolución del perfil y selección de adaptador de avance
    - Definir `PerfilFasesGiroPort` (solo lectura) y su adaptador que consulta el giro del tenant vía `GiroEmpresaPort` (`anuncios-luminosos → ANUNCIOS`; cualquier otro → `GENERICO`).
    - Dividir el cómputo de avance: `AvanceProduccionAdapter` (Núcleo `proyecto`) computa solo `tieneOrdenFabricacionTerminada` (vía `OrdenFabricacionTerminadaPort`; las otras banderas en `false`); `AvanceSitioAnunciosAdapter` (vertical anuncios) compone las cuatro fases (`LevantamientoCompletadoPort`, `PermisoAprobadoPort`, `InstalacionCompletadaPort`), registrado solo para anuncios.
    - En `ServicioProyectos.consultar`: obtener el perfil vía `PerfilFasesGiroPort` y elegir el adaptador de avance según el perfil, luego derivar el estado consolidado.
    - _Requisitos: 3.1, 3.2, 3.5_

  - [x] 1.15 Verificación de Cliente en `ServicioProyectos.crear`
    - En `ServicioProyectos.crear`: `clienteId` nulo → 422 sin persistir; `!clienteExistente.existeEnTenant(clienteId)` → 404 + auditoría de acceso cruzado con recurso `cliente`; Cliente accesible → crea el Proyecto en `SIN_SITIOS` y audita. Conservar el contrato `POST /proyectos` (`{ clienteId, nombre }`).
    - _Requisitos: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 2. Checkpoint BLOQUE A — Asegurar que las pruebas pasan
    - Ejecutar `mvn -o compile` y `mvn -o test -Dtest=DerivacionEstadoProyectoPropertyTest,VerificacionClientePropertyTest` (y los controller/service tests de A). Confirmar que la suite existente sigue verde. Si surgen dudas, preguntar al usuario antes de continuar.

- [x] 3. BLOQUE B — Consumo de materiales en la OF

  - [x] 3.1 Entidad/repositorio de partidas y servicio de edición (solo en `pendiente`)
    - En `operacion.produccion`: entidad `PartidaOrdenFabricacion extends TenantScopedEntity` (`id`, `orden_fabricacion_id` FK, `material_id`, `cantidad NUMERIC(18,4) > 0`, `version`), repositorio con `findByOrdenFabricacionId(...)`. DTOs `PartidaOrdenFabricacionDto`, `OrdenFabricacionDetalleDto` (OF + partidas) y `CrearOrdenDirectaCommand`.
    - Servicio `agregarPartida`/`reemplazarPartidas`/`eliminarPartida`: cantidad ≤ 0 → 422 sin persistir; Material no accesible en el tenant → 404 + auditoría (verifica con el puerto de lectura del inventario del Núcleo antes de persistir); si la OF **no** está en `pendiente` → 422 ("las partidas solo se editan con la Orden_Fabricacion en pendiente"). Persistencia tenant-scoped con RLS y `version`.
    - Depende de la migración V66 (tarea 5.1) para la tabla; el desarrollo puede avanzar en paralelo pero la ejecución/verificación requiere V66 aplicada.
    - _Requisitos: 5.1, 5.2, 5.3, 5.4_

  - [x] 3.2 Endpoints de partidas y `OrdenFabricacionDetalleDto` en GET
    - `POST /ordenes-fabricacion/{id}/partidas` (permiso `orden_fabricacion:cambiar_estado`): agrega/reemplaza partidas solo si `pendiente`.
    - `GET /ordenes-fabricacion/{id}` devuelve `OrdenFabricacionDetalleDto` (con la lista de partidas: Material + cantidad). El listado `GET /ordenes-fabricacion` conserva el DTO de resumen (sin partidas) para no cambiar su forma.
    - _Requisitos: 5.5, 16.2_

  - [x]* 3.3 PBT: partidas editables solo en `pendiente`
    - `backend/src/test/java/com/dessti/crm/operacion/produccion/application/PartidasEdicionPropertyTest.java` (jqwik, ≥100 iteraciones), generador de estado de OF:
      - **Property 4: Las partidas/BOM solo se editan mientras la OF está en `pendiente`** — `Feature: operacion-produccion-enterprise, Property 4: agregar/reemplazar/eliminar aceptado sii estado == pendiente; en otro estado 422 y partidas sin cambios`.
    - _Requisitos: 5.1, 5.2_

  - [x] 3.4 Cablear el consumo atómico en `ServicioOrdenesFabricacion.cambiarEstado`
    - En `cambiarEstado`, dentro de la misma `@Transactional`: interpretar el destino (422 si etiqueta desconocida); comprobar `orden.getEstado().puedeTransicionarA(destino)` **antes** de consumir (transición inválida → 409 sin consumir); si el destino es `EN_PRODUCCION` y hay partidas, mapear a `List<ConsumoMaterial>` e invocar `consumoMaterialPort.consumirParaOrdenFabricacion(orden.id, consumos)` (**cableado**, no reimplementación); luego `orden.cambiarEstado(destino, actor)`, `save` y auditar (actor, recurso `orden_fabricacion`, la OF y el detalle del consumo).
    - 422 por existencias insuficientes o 404 por Material inaccesible → rollback total (ni movimientos ni cambio de estado). OF sin partidas → transita sin generar movimientos.
    - NO reimplementar el consumo ni la validación de no-negatividad (viven en `ServicioInventario`/`Material.aplicarMovimiento`).
    - _Requisitos: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

  - [x]* 3.5 PBT: consumo atómico y no-negativo
    - `backend/src/test/java/com/dessti/crm/operacion/produccion/application/ConsumoAtomicoPropertyTest.java` (jqwik, ≥100 iteraciones), generador de OF con partidas y de existencias por Material, con `ConsumoMaterialPort` in-memory (o `ServicioInventario` con repos en memoria) para costo bajo:
      - **Property 3: Consumo de materiales atómico y no-negativo** — `Feature: operacion-produccion-enterprise, Property 3: si algún consumo dejaría existencias < 0, ningún Movimiento_Inventario se persiste y la OF permanece en pendiente (422); si todos caben, un salida por partida, existencias resultantes ≥ 0 y OF en en_produccion; sin partidas transita sin movimientos`.
    - _Requisitos: 6.1, 6.2, 6.3, 6.5, 6.6_

  - [x]* 3.6 Test de integración del consumo (cableado real con `ServicioInventario`)
    - 1–2 ejemplos de extremo a extremo con el `ServicioInventario` **real** para confirmar el cableado del `ConsumoMaterialPort` en la transición `pendiente → en_produccion`: caso con existencias suficientes (movimientos `salida` + OF `en_produccion`) y caso con existencias insuficientes (422, sin efectos, OF en `pendiente`).
    - _Requisitos: 6.1, 6.2, 6.3, 6.7, 14.3_

  - [x] 4. Checkpoint BLOQUE B — Asegurar que las pruebas pasan
    - Ejecutar `mvn -o test -Dtest=PartidasEdicionPropertyTest,ConsumoAtomicoPropertyTest` y el test de integración del consumo. Requiere V66 aplicada (tarea 5.1). Si surgen dudas, preguntar al usuario.

- [x] 5. BLOQUE B/F — Migración Flyway V66 (Data Models)

  - [x] 5.1 Crear `V66__operacion_produccion_generalizacion.sql`
    - Antes de escribir el SQL: **verificado** que la última migración es **V65** y **V66 está libre**; el índice único real de V17 es `uq_orden_fabricacion_cotizacion (tenant_id, cotizacion_id)`; la tabla es `orden_fabricacion`; el patrón RLS es `ENABLE` + `FORCE ROW LEVEL SECURITY` + `CREATE POLICY tenant_isolation USING/WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid)` (V2/V17).
    - Crear `backend/src/main/resources/db/migration/V66__operacion_produccion_generalizacion.sql` con tres bloques:
      1. **Generalización de `orden_fabricacion`:** `ALTER TABLE orden_fabricacion ALTER COLUMN cotizacion_id DROP NOT NULL;` y volver el índice único **parcial**: `DROP INDEX IF EXISTS uq_orden_fabricacion_cotizacion;` + `CREATE UNIQUE INDEX uq_orden_fabricacion_cotizacion ON orden_fabricacion (tenant_id, cotizacion_id) WHERE cotizacion_id IS NOT NULL;`.
      2. **Tabla `partida_orden_fabricacion`:** columnas del diseño (`id UUID PK`, `tenant_id`, `orden_fabricacion_id` FK `ON DELETE CASCADE` a `orden_fabricacion`, `material_id` FK a `material`, `cantidad NUMERIC(18,4) CHECK (cantidad > 0)`, `version BIGINT DEFAULT 0`, marcas de auditoría), índice `ix_partida_of_orden (tenant_id, orden_fabricacion_id)`, y RLS con el patrón `tenant_isolation` (ENABLE + FORCE + policy).
      3. **Seed idempotente de permisos:** asignar `orden_fabricacion:*` y `proyecto:*` a roles de otros giros con `INSERT INTO rol_permiso (...) SELECT ... FROM permiso p WHERE p.recurso IN ('orden_fabricacion','proyecto') ON CONFLICT DO NOTHING;` (no re-crea permisos ni altera datos del giro anuncios).
    - _Requisitos: 1.3, 2.1, 3.1, 5.4_

  - [x]* 5.2 Verificación de aplicación de V66
    - Confirmar que V66 aplica en limpio (arranque Flyway sin conflicto de versión), que `cotizacion_id` acepta NULL, que el índice único parcial rechaza dos OF con la misma `(tenant_id, cotizacion_id)` no-nula pero admite múltiples OF con `cotizacion_id NULL`, y que `partida_orden_fabricacion` tiene RLS activa.
    - _Requisitos: 1.3, 5.4_

- [x] 6. BLOQUE C — Exponer fotos de Levantamiento y pendientes/evidencias de OTI

  - [x] 6.1 Fotos de Levantamiento: `LevantamientoSitioDetalleDto` + `GET /{id}/fotos` + `fotosDe`
    - En anuncios/levantamiento: `LevantamientoSitioDetalleDto` = `LevantamientoSitioDto` + `List<LevantamientoFotoDto> fotos`. `GET /levantamientos/{id}` devuelve el DTO enriquecido (lista vacía si no hay). Endpoint dedicado `GET /levantamientos/{id}/fotos` (permiso `levantamiento_sitio:leer`, gating `moduloHabilitado('operacion') and giroCorresponde('operacion')`) → `List<LevantamientoFotoDto>` (incluye `[]`); 404 + auditoría si el Levantamiento no es accesible.
    - `ServicioLevantamientos.fotosDe(levantamientoId)`: verifica acceso y lee `LevantamientoFotoRepository` acotado al tenant. Conservar el alta de fotos (`POST /levantamientos/{id}/fotos`) y su auditoría.
    - _Requisitos: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 6.2 OTI: `OrdenTrabajoInstalacionDetalleDto` + `GET /{id}/pendientes` y `/{id}/evidencias`
    - En anuncios/instalacion: `OrdenTrabajoInstalacionDetalleDto` = DTO actual + `List<PendienteInstalacionDto> pendientes` + `List<EvidenciaInstalacionDto> evidencias`. `GET /ordenes-trabajo-instalacion/{id}` devuelve el DTO enriquecido (listas vacías cuando no haya).
    - Endpoints dedicados (permiso `orden_trabajo_instalacion:leer`, gating de anuncios): `GET /ordenes-trabajo-instalacion/{id}/pendientes` (cada uno con `resuelto`) y `GET /ordenes-trabajo-instalacion/{id}/evidencias`. 404 + auditoría si la OTI no es accesible.
    - _Requisitos: 8.1, 8.2, 8.3, 8.6, 8.7_

  - [x] 6.3 Guarda de cierre de OTI que enumera los pendientes no resueltos
    - En `ServicioOrdenesTrabajoInstalacion.cambiarEstado`: conservar la comprobación de validez de transición (409) **antes** de la guarda de pendientes. Antes de completar, consultar `pendienteRepository.findByOrdenTrabajoInstalacionIdAndResueltoFalse(otiId)`; si hay elementos, lanzar 422 con un mensaje que **enumera las descripciones** de los pendientes no resueltos (p. ej. "no se puede completar: pendientes por resolver: [«…», «…»]"). Conservar la ruta de avance/resolución (`POST /{id}/avance`) y su auditoría.
    - _Requisitos: 8.4, 8.5, 16.2_

  - [x]* 6.4 PBT: la guarda de cierre de OTI informa los pendientes no resueltos
    - `backend/src/test/java/com/dessti/crm/vertical/anuncios/instalacion/application/GuardaCierreOtiPropertyTest.java` (jqwik, ≥100 iteraciones), generador de conjuntos de pendientes (resueltos/no) sobre una OTI `en_curso`:
      - **Property 5: La guarda de cierre de OTI informa los pendientes no resueltos** — `Feature: operacion-produccion-enterprise, Property 5: completar aceptado sii no hay pendientes sin resolver; si existe ≥1, 422, estado conservado y el mensaje enumera exactamente las descripciones no resueltas`.
    - _Requisitos: 8.5_

  - [x]* 6.5 Controller + service tests de OTI (pendientes/evidencias/guarda)
    - Controller y service: consulta con pendientes/evidencias (con y sin elementos → listas vacías), resolución de un pendiente (marca `resuelto` + auditoría), y guarda de cierre (422 informativo que lista los pendientes; 409 si la transición no es válida). 404 si la OTI no es accesible.
    - _Requisitos: 8.1, 8.2, 8.3, 8.4, 8.6, 14.1_

- [x] 7. BLOQUE D — Frontend enterprise (UX)

  - [x] 7.1 `NombresOperacionService` + `entity-select` (sin UUIDs)
    - Crear `frontend/src/app/features/operacion/services/nombres-operacion.service.ts` análogo a `NombresInventarioService`: carga una vez (páginas ≤ 200) los catálogos y arma mapas `id → nombre` con marcador `(sin nombre)` (nunca el UUID). Resuelve nombres para Cotización, Orden de Fabricación, Sitio, Cuadrilla, Cliente y Material (reutiliza `MaterialesService` para Materiales, sin duplicar). Selectores por nombre con el componente compartido `app-entity-select`.
    - _Requisitos: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [x] 7.2 Vista `orden-fabricacion-detalle` (genérica, sin guardaGiro)
    - Crear la vista siguiendo el patrón `proyecto-detalle` (`input.required<string>()` del `:id`, signal de fase `cargando|ok|error|vacio` + `mensajeError`, `StateContainer`, `PageHeader`, acciones por permiso vía `AuthService.tienePermiso`, es-MX, solo tokens, WCAG AA). Muestra info de la OF (Cliente por nombre, estado etiqueta es-MX, origen "directa"/"desde cotización"), **tabla de partidas** (Material por nombre + cantidad) y acciones de estado **contextualizadas** (desde `pendiente`: Iniciar producción/Cancelar; desde `en_produccion`: Terminar/Cancelar; estados finales sin acciones). NO usa `guardaGiro`. Ampliar `ProduccionService` con `crearDirecta` y `listar(clienteId)`; servicio de partidas.
    - _Requisitos: 9.1, 9.5, 9.6, 9.7, 9.8, 10.2, 10.3_

  - [x] 7.3 Vistas `levantamiento-detalle`, `permiso-detalle`, `oti-detalle` (con guardaGiro)
    - `levantamiento-detalle`: info + galería de fotos (§C1) + flujo `agregarFotos` (7.5). `permiso-detalle`: info + aprobar/rechazar contextualizadas (solo si `solicitado`). `oti-detalle`: info + lista de pendientes con acción "Resolver" + galería de evidencias + acciones de estado contextualizadas; al completar con pendientes muestra el mensaje 422 informativo (§C2). Todas con patrón `proyecto-detalle`, es-MX, tokens, WCAG AA, estados vacíos, y `guardaGiro(GIRO_ANUNCIOS)` en sus rutas de detalle.
    - _Requisitos: 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8_

  - [x] 7.4 Filtros por nombre (Cliente en OF; Cliente/Cuadrilla en OTI) y rutas
    - Listado de OF: agregar filtro por **Cliente** (por nombre vía `entity-select`) además de estado; `ProduccionService.listar` envía `clienteId` (backend ya lo soporta). Listado de OTI: filtros por **Cliente** y **Cuadrilla** (por nombre); `OtisService.listar` usa `FiltroOti { estado, cuadrillaId, clienteId }`. Sin coincidencias → estado vacío es-MX; controles accesibles/responsivos/tokens.
    - En `operacion.routes.ts`: **quitar** `guardaGiro(GIRO_ANUNCIOS)` de las rutas de `ordenes-fabricacion` (lista y `:id`) y `proyectos` (lista y `:id`); conservar `guardaModulo('operacion') + guardaPorPermiso(...)`. Mantener `guardaGiro` en `levantamientos`, `permisos`, `instalacion` y sus detalles.
    - _Requisitos: 2.5, 11.1, 11.2, 11.3, 11.4, 11.5_

  - [x] 7.5 `agregarFotos` en `LevantamientosService` y la vista de Levantamientos
    - Implementar `agregarFotos` en `LevantamientosService` y en la vista de Levantamientos (y `levantamiento-detalle`): capturar una o más referencias y enviarlas a `POST /levantamientos/{id}/fotos`, recargando la galería. Sin referencias → no llamar al backend y mostrar validación es-MX; fallo → mensaje es-MX conservando el estado previo; carga accesible/responsiva/tokens.
    - _Requisitos: 12.1, 12.2, 12.3, 12.4_

  - [x]* 7.6 Specs one-shot de las cinco vistas + vistas de detalle (sin UUIDs, axe one-shot)
    - `.spec.ts` para las cinco vistas del módulo (Órdenes de Fabricación, Levantamientos, Permisos, Instalación, Proyectos) y las vistas de detalle nuevas (`orden-fabricacion-detalle`, `levantamiento-detalle`, `permiso-detalle`, `oti-detalle`): estados carga/vacío/error, **sin UUIDs crudos ni recortes** en el DOM, y axe **en un solo intento (one-shot)** con el spec en aislamiento.
    - Ejecutar cada uno con `ng test --watch=false --include=<ruta-spec>` (nunca la suite completa).
    - _Requisitos: 15.1, 15.2, 15.3, 10.2_

- [x] 8. BLOQUE E — Notificación de vencimiento de permisos (anuncios)

  - [x] 8.1 `NotificadorPermisoNotificaciones` (reemplaza el log vía `@ConditionalOnMissingBean`)
    - Crear `NotificadorPermisoNotificaciones` (anuncios/permiso, `adapter/out`) que implementa `NotificadorPermisoPort` delegando en `NotificacionPort.notificar(...)`: construye `SolicitudNotificacion` con `TipoEventoNotificacion.PERMISO_POR_VENCER`, canal `CORREO` por defecto, destinatario resuelto por un puerto de lectura mínimo (correo del responsable del Sitio; en su defecto, contacto de la Empresa), asunto/contenido minimizados incluyendo Sitio, tipo, fecha de vencimiento y días restantes, `marketing=false` (sin guarda Opt-In), recurso `permiso_instalacion`. Si no hay destinatario resoluble, registrar omisión sin romper el barrido. Registrarlo como bean real para que **reemplace** a `NotificadorPermisoRegistroLog` (que ya es placeholder `@ConditionalOnMissingBean`).
    - NO cambiar la lógica de `ServicioPermisos.notificarVencimientosProximos()`.
    - _Requisitos: 13.2, 13.3_

  - [x] 8.2 `ProgramadorVencimientosPermisos` (planificador multi-tenant) + `EmpresasActivasPort`
    - Definir `EmpresasActivasPort` (lectura) cuyo adaptador delega en `ServicioEmpresas`/repositorio de Empresa. Crear `ProgramadorVencimientosPermisos` (anuncios/permiso, patrón `ProgramadorRespaldos`): `@Component @ConditionalOnProperty(name="crm.permisos.vencimientos.habilitado", havingValue="true")` + `@Scheduled(cron="${crm.permisos.vencimientos.cron}")` (por defecto diario, p. ej. `0 0 6 * * *`). Itera Empresas activas y por cada `tenantId`, dentro de una transacción: `TenantContext.set(tenantId)` + `TenantSessionInitializer.applyTenant(tenantId)` e invoca `notificarVencimientosProximos()`, con `TenantContext.clear()` en `finally`. Un fallo por tenant se registra y no detiene el barrido.
    - _Requisitos: 13.1, 13.5, 13.6_

  - [x] 8.3 Endpoint manual `POST /permisos-instalacion/notificar-vencimientos`
    - En el controlador de permisos (anuncios): `POST /permisos-instalacion/notificar-vencimientos` que opera sobre el tenant en contexto e invoca `notificarVencimientosProximos()`, devolviendo el número de notificaciones emitidas. Gating: `moduloHabilitado('operacion') and giroCorresponde('operacion') and @autorizador.tiene('permiso_instalacion','cambiar_estado')`.
    - _Requisitos: 13.1, 13.4_

  - [x]* 8.4 Tests de notificaciones (N permisos → N notificaciones; barrido multi-tenant)
    - Service test con repositorio/notificador mockeados: N permisos `aprobado` en ventana (≤ 30 días) → N notificaciones emitidas; 0 en ventana → 0 notificaciones (Req 13.5); barrido multi-tenant con 2 tenants que solo cuenta los del tenant en contexto (Req 13.6). Controller test del endpoint: 200 con conteo; 403 si falta módulo/giro/permiso.
    - _Requisitos: 13.1, 13.2, 13.5, 13.6, 14.1_

- [x] 9. Verificación final integral y no-regresión
    - Backend: `mvn -o package -DskipTests` (0 errores) y suite verde de las clases tocadas (produccion, proyecto, partidas, derivación, consumo, OTI, permiso/notificador y las PBT); la suite existente permanece verde. Confirmar que la **migración V66 aplica** en limpio.
    - Frontend: `ng build --configuration production` (0 errores) y los specs tocados en aislamiento (one-shot, tarea 7.6) — nunca la suite completa por flakiness de axe.
    - No-regresión: flujo de anuncios intacto (génesis de OF desde Cotización con 3 precondiciones, encadenamiento levantamiento/permiso/OTI); contratos de endpoints existentes sin cambios (solo se agregan endpoints y se enriquecen DTOs de salida); auditoría JSONB (`ServicioAuditoria`/`aJson`) como punto único; dependencia `inventario-avanzado → operacion` preservada; gating por giro conservado en Levantamiento/Permiso/OTI y relajado solo en OF/Proyecto. Limpiar cualquier archivo temporal de verificación.
    - Ante staleness de pruebas: borrar `target\test-classes` y recompilar.
    - _Requisitos: 14.1, 14.2, 14.3, 14.4, 14.5, 15.1, 15.2, 15.3, 16.1, 16.2, 16.3, 16.4, 16.5, 16.6_

## Notes

- Tareas marcadas con `*` son de prueba (opcionales para un MVP rápido, pero el usuario pidió incluirlas). No se implementan las subtareas con `*`; sí las que no lo llevan.
- **Orden de las olas:** el refactor de renombrado de paquete (1.1 y 1.2) es de amplio alcance (muchos imports) y va **primero y secuencial** — 1.1 antes de 1.2, ninguna en paralelo con tareas que editan esos mismos archivos. El resto de BLOQUE A depende de la nueva ubicación.
- **NO rehacer:** auditoría JSONB en `ServicioAuditoria` (punto único); el consumo ya implementado en `ServicioInventario` (solo se **cablea** en `cambiarEstado`, no se reimplementa); la dependencia `inventario-avanzado → operacion`; la semántica de anuncios de `DerivacionEstadoProyecto` (se **generaliza**, no se reescribe).
- **PBT con jqwik** (ya presente en `backend/`, ver `.jqwik-database`), mínimo **100 iteraciones** por propiedad; cada test etiquetado `Feature: operacion-produccion-enterprise, Property N: ...`. Las 6 propiedades: 1 (derivación por fases), 2 (equivalencia ANUNCIOS/GENERICO), 3 (consumo atómico/no-negativo), 4 (partidas solo en `pendiente`), 5 (guarda de cierre OTI informa), 6 (verificación de Cliente). El frontend no usa PBT: specs de componente one-shot + build.
- **Migración V66:** verificado que la última es **V65** y **V66 está libre**; índice único real de V17 `uq_orden_fabricacion_cotizacion (tenant_id, cotizacion_id)`; patrón RLS `tenant_isolation` de V2/V17. El seed de permisos usa `ON CONFLICT DO NOTHING` (idempotente) y no altera datos del giro anuncios.
- **Entorno:** `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot`; Maven `C:\apache-maven-3.9.10\bin\mvn.cmd`. Backend en `backend/`; frontend en `frontend/`. Ante staleness de pruebas, borrar `target\test-classes`.
- Archivos UTF-8 sin BOM; español es-MX en toda la UI, solo design tokens, WCAG AA, sin UUIDs crudos.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["1.3", "1.4", "1.6", "1.11", "5.1", "7.1"] },
    { "id": 3, "tasks": ["1.5", "1.8", "1.12", "1.13", "1.14", "3.1", "5.2", "6.1", "6.2", "8.1", "8.2"] },
    { "id": 4, "tasks": ["1.7", "1.9", "1.15", "3.2", "3.4", "6.3", "7.2", "7.3", "7.5", "8.3"] },
    { "id": 5, "tasks": ["1.10", "3.3", "3.5", "3.6", "6.4", "6.5", "7.4", "8.4"] },
    { "id": 6, "tasks": ["7.6"] }
  ]
}
```
