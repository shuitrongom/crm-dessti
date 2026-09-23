# Documento de Diseño — Operación y Producción (Enterprise)

_(Generalización del núcleo del módulo `operacion`: Órdenes de Fabricación y Proyectos/Sitios al Núcleo sin amarre a giro; cableado del consumo de materiales; exposición de datos ya persistidos; UX enterprise; disparo y notificación real de vencimientos de permisos; calidad y no-regresión.)_

## Overview

Este diseño afina y generaliza el módulo `operacion` de la plataforma multi-tenant **Dess-TI / plataforma-multigiro** (Spring Boot + Angular 20 + PostgreSQL, arquitectura hexagonal, RLS y RBAC deny-by-default), partiendo del diagnóstico verificado en el código y de las **Decisiones acordadas** del `requirements.md` (vinculantes).

El eje del diseño es una separación limpia, idéntica a la que ya viven **Materiales e Inventario** (que están en el Núcleo `com.dessti.crm.operacion.inventario` y por eso funcionan para cualquier giro):

- **Al Núcleo (cualquier giro):** Órdenes de Fabricación (producción) y Proyectos/Sitios. Se mueven a `com.dessti.crm.operacion.produccion` y `com.dessti.crm.operacion.proyecto`, se les relaja el gating por giro (se conserva módulo + permiso) y la Orden de Fabricación gana un origen genérico (creación directa por Cliente + partidas), conservando intacta su génesis desde Cotización.
- **Permanecen en el vertical de anuncios (giro `anuncios-luminosos`):** Levantamientos, Permisos e Instalación (OTIs), con su gating por giro sin cambios.

Sobre esa base, el diseño cierra los seis bloques A–F:

| Bloque | Qué resuelve | Cómo lo satisface este diseño |
|---|---|---|
| **A** | Generalización del núcleo (OF y Proyectos para cualquier giro) | Mover OF/Proyectos al Núcleo, relajar `giroCorresponde`, origen genérico de OF, `ClienteExistentePort`, derivación de estado de Proyecto por fases aplicables |
| **B** | Consumo de materiales en la OF | Tabla `partida_orden_fabricacion` (BOM) editable solo en `pendiente`; consumo atómico vía `ConsumoMaterialPort` en la transición `pendiente → en_produccion` (422 si insuficiente) |
| **C** | Exponer datos ya persistidos | Fotos de Levantamiento y pendientes/evidencias de OTI vía DTO enriquecido + endpoints GET; guarda de cierre 422 que informa qué pendientes faltan |
| **D** | Frontend enterprise (UX) | Vistas de detalle (patrón `proyecto-detalle`), `NombresOperacionService` (sin UUIDs), filtros por nombre, `agregarFotos`, tokens/es-MX/WCAG AA |
| **E** | Notificación de vencimiento de permisos | Planificador diario multi-tenant (`@Scheduled`) + endpoint manual, ambos disparando `notificarVencimientosProximos()`; notificador real integrado con el módulo `notificaciones` |
| **F** | Calidad y no-regresión | Data Models + migración Flyway **V66**; Correctness Properties (jqwik ≥100 iter); pruebas de controlador/servicio de OTI; specs de frontend; riesgos y no-regresión |

### Alcance verificado en el código (no supuesto)

- **Gating.** `Autorizador.giroCorresponde(modulo)` devuelve `true` cuando ningún vertical declara ese módulo en `ContratoVertical.modulos()` (módulo de Núcleo, transversal). Hoy `AnunciosVertical.modulos()` declara `operacion`, de modo que `giroCorresponde('operacion')` ata todo endpoint que lo invoque al giro anuncios. `MaterialController` (Núcleo) usa **solo** `moduloHabilitado('operacion') and @autorizador.tiene('material','crear')` — **sin** `giroCorresponde` — por eso Materiales ya funciona para cualquier giro. El recurso `material` **no** figura en `AnunciosVertical.recursos()`; los recursos `orden_fabricacion` y `proyecto` **sí** figuran, y `RegistroVerticales.giroDeRecurso(...)` los clasifica como de anuncios (lo consume `ClasificadorRecursosVertical` para ofrecer/validar permisos por giro).
- **OF.** Entidad `OrdenFabricacion` (tabla `orden_fabricacion`, migración V17) con `cotizacion_id NOT NULL updatable=false`, `cliente_id NOT NULL`, `estado`. Estados `pendiente → {en_produccion|cancelada}`, `en_produccion → {terminada|cancelada}` (`EstadoOrdenFabricacion`, máquina pura). `ServicioOrdenesFabricacion.generar(cotizacionId)` aplica las 3 precondiciones (Cotización existe/`aprobada`, sin OF previa vía índice único `uq_orden_fabricacion_cotizacion`, con Prueba_Diseño `aprobada`). Índice único `(tenant_id, cotizacion_id)`.
- **Consumo.** `ConsumoMaterialPort.consumirParaOrdenFabricacion(UUID ordenFabricacionId, List<ConsumoMaterial>)` lo implementa `ServicioInventario` (Núcleo), atómico dentro de `@Transactional`, registra un `Movimiento_Inventario` `salida` por material, 404 si el Material no es accesible, 422 si dejaría existencias `< 0` (`Material.aplicarMovimiento` valida antes de mutar). El fix de auditoría JSONB (`aJson`) **no se toca**.
- **Proyecto.** `DerivacionEstadoProyecto.derivar(List<AvanceFasesSitio>)` es una función pura (vacío → `SIN_SITIOS`; fase incompleta más temprana en el orden Levantamiento → Permiso → Fabricación → Instalación → `COMPLETADO`). `AvanceSitioPort.avanceDe(sitioId)` lo implementa `AvanceSitioAdapter`, que compone `LevantamientoCompletadoPort`, `PermisoAprobadoPort` e `InstalacionCompletadaPort`. `ServicioProyectos.crear` hoy solo valida `clienteId != null` (422) y se apoya en la FK `fk_proyecto_cliente`; su javadoc ya anticipa un `ClienteExistentePort`.
- **Notificaciones.** El módulo `com.dessti.crm.notificaciones` ya expone `NotificacionPort.notificar(SolicitudNotificacion)` con reintentos y auditoría; `TipoEventoNotificacion.PERMISO_POR_VENCER` ya existe (V42). `ServicioPermisos.notificarVencimientosProximos()` ya localiza y emite por cada permiso vía `NotificadorPermisoPort`, pero **sin disparador** y con `NotificadorPermisoRegistroLog` (solo log, registrado con `@ConditionalOnMissingBean`).
- **Multi-tenant en jobs.** `TenantSessionInitializer.applyTenant(UUID)` fija `app.current_tenant` (SET LOCAL) dentro de la transacción; `ServicioEmpresas.listarEmpresas(...)` enumera Empresas (cada `empresa.getId()` es el tenant). El único `@Scheduled` existente es `ProgramadorRespaldos` (`platform.respaldo`), con `@ConditionalOnProperty` + cron configurable; no itera tenants.
- **OTI/Levantamiento (huecos).** `OrdenTrabajoInstalacionDto` **no** expone pendientes ni evidencias; `LevantamientoSitioDto` **no** expone fotos. Ya existen `PendienteInstalacionDto`, `EvidenciaInstalacionDto`, `LevantamientoFotoDto`, y la guarda de cierre 422 (`ServicioOrdenesTrabajoInstalacion` con `existsByOrdenTrabajoInstalacionIdAndResueltoFalse`) devuelve un mensaje genérico "existen pendientes por resolver" (no lista cuáles).
- **Frontend.** `operacion.routes.ts` protege `ordenes-fabricacion`, `levantamientos`, `permisos`, `instalacion`, `proyectos`, `proyectos/:id` con `guardaModulo('operacion') + guardaGiro(GIRO_ANUNCIOS) + guardaPorPermiso(...)`; `materiales` usa solo `guardaModulo('operacion') + permiso` (patrón sin giro). `NombresInventarioService` implementa el patrón sin-UUID (carga catálogos, mapas id→entidad, `MARCADOR_SIN_NOMBRE`); `proyecto-detalle.ts` es la única vista de detalle existente. La última migración Flyway es **V65**; la nueva de este spec será **V66**.

### Mapa Requisito → Diseño

| Requisito | Dónde se satisface |
|---|---|
| **1** OF genéricas | Architecture › Mover al Núcleo; BLOQUE A › §A1 (factoría `crearDirecta`, endpoint `POST /ordenes-fabricacion/directa`, conservación de la génesis por Cotización) |
| **2** Gating por giro relajado en OF | BLOQUE A › §A2 (quitar `giroCorresponde` de los endpoints de OF; conservar módulo + permiso + RLS) |
| **3** Proyectos genéricos con fases aplicables | BLOQUE A › §A3 (`PerfilFasesGiro` + `DerivacionEstadoProyecto.derivar(fases, avances)`) |
| **4** Verificación de Cliente en Proyectos | BLOQUE A › §A4 (`ClienteExistentePort` + 404/422/auditoría) |
| **5** Partidas/BOM de la OF | BLOQUE B › §B1 (tabla `partida_orden_fabricacion`, editable solo en `pendiente`) |
| **6** Consumo atómico al fabricar | BLOQUE B › §B2 (consumo en `pendiente → en_produccion`, transacción única, 422) |
| **7** Exposición de fotos del Levantamiento | BLOQUE C › §C1 (DTO enriquecido + `GET /levantamientos/{id}/fotos`) |
| **8** Pendientes y evidencias de OTI | BLOQUE C › §C2 (DTO enriquecido + GET dedicados + guarda de cierre que informa) |
| **9** Vistas de detalle | BLOQUE D › §D1 (detalle de OF, Levantamiento, Permiso, OTI con patrón `proyecto-detalle`) |
| **10** Eliminación de UUIDs | BLOQUE D › §D2 (`NombresOperacionService` + `entity-select`) |
| **11** Filtros del frontend | BLOQUE D › §D3 (filtro por Cliente en OF; Cliente/Cuadrilla en OTI) |
| **12** `agregarFotos` | BLOQUE D › §D4 (flujo de carga + `POST /levantamientos/{id}/fotos`) |
| **13** Disparo y notificación real de vencimientos | BLOQUE E › §E1 (planificador diario multi-tenant + endpoint manual + `NotificadorPermisoNotificaciones`) |
| **14** Pruebas de backend | Testing Strategy › Backend (jqwik + controller/service OTI) |
| **15** Pruebas de frontend | Testing Strategy › Frontend (specs one-shot, sin UUIDs) |
| **16** No regresión | Consideraciones de no-regresión (flujo anuncios, contratos, auditoría JSONB, dependencias de módulos, guardas) |

---

## Architecture

### El plan concreto: mover OF y Proyectos al Núcleo

Hoy la estructura es asimétrica: Materiales/Inventario viven en el Núcleo y funcionan para cualquier giro; OF/Proyectos viven en el vertical de anuncios y quedan atados al giro. El diseño la vuelve simétrica.

```
ANTES                                                    DESPUÉS
─────────────────────────────────────────────────       ─────────────────────────────────────────────────
com.dessti.crm.operacion.inventario  (Núcleo)            com.dessti.crm.operacion.inventario   (Núcleo)  — sin cambios
                                                         com.dessti.crm.operacion.produccion   (Núcleo)  ← OF (movida)
                                                         com.dessti.crm.operacion.proyecto     (Núcleo)  ← Proyecto/Sitio (movidos)

com.dessti.crm.vertical.anuncios.ordenfabricacion        (paquete eliminado; su código migra al Núcleo)
com.dessti.crm.vertical.anuncios.proyecto                (paquete eliminado; su código migra al Núcleo)
com.dessti.crm.vertical.anuncios.levantamiento (giro)    com.dessti.crm.vertical.anuncios.levantamiento (giro)  — sin cambios
com.dessti.crm.vertical.anuncios.permiso       (giro)    com.dessti.crm.vertical.anuncios.permiso       (giro)  — + notificador real (§E)
com.dessti.crm.vertical.anuncios.instalacion   (giro)    com.dessti.crm.vertical.anuncios.instalacion   (giro)  — + exposición datos (§C)
```

**Decisión (D1) — Reubicación física de paquetes.** OF y Proyecto/Sitio se mueven **con toda su capa hexagonal** (`adapter/{in/rest,out/persistence}`, `application`, `domain`) a:
- `com.dessti.crm.operacion.produccion` (Orden de Fabricación + Partidas/BOM).
- `com.dessti.crm.operacion.proyecto` (Proyecto y Sitio).

El movimiento se hace con refactor de renombrado de paquete (actualiza imports automáticamente), no reescritura. **No cambian** las rutas REST (`/ordenes-fabricacion`, `/proyectos`), ni los nombres de tabla (`orden_fabricacion`, `proyecto`, `sitio`), ni los nombres de clase; solo el paquete. Por eso el contrato publicado y el esquema quedan intactos (Req 16.2).

**Decisión (D2) — El módulo `operacion` deja de pertenecer a un giro para estos recursos.** El mecanismo real de amarre no es "el módulo pertenece a anuncios", sino la **cláusula `giroCorresponde('operacion')` en los `@PreAuthorize` de los controladores**. El plan:

1. **Controladores de OF y Proyecto:** se elimina `and @autorizador.giroCorresponde('operacion')`, dejando `@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('<recurso>','<op>')` — exactamente el patrón de `MaterialController`. Esto los vuelve accesibles a cualquier giro con el módulo habilitado y el permiso (Req 2, 3), conservando el aislamiento intra-tenant por RLS y `TenantContext` (Req 2.4).
2. **Controladores de Levantamiento, Permiso, OTI:** **conservan** `giroCorresponde('operacion')` sin cambios (Req 16.6). Nota: como estos siguen invocando `giroCorresponde('operacion')` y `operacion` seguirá declarado en `AnunciosVertical.modulos()`, su gating por giro sigue funcionando idénticamente.
3. **`AnunciosVertical.recursos()`:** se **retiran** `orden_fabricacion` y `proyecto` del conjunto de recursos del vertical. Con ello `RegistroVerticales.giroDeRecurso('orden_fabricacion')`/`('proyecto')` pasa a vacío → `ClasificadorRecursosVertical` los trata como recursos de **Núcleo** (transversales a todo giro), de modo que cualquier giro pueda ofrecer/validar esos permisos en sus roles personalizados (Req 2, 3). Se **conservan** en `recursos()` los específicos de anuncios (`prueba_diseno`, `levantamiento_sitio`, `permiso_instalacion`, `orden_trabajo_instalacion`, `cuadrilla`).
4. **`AnunciosVertical.modulos()`:** **no se toca** (`operacion` permanece), porque Levantamiento/Permiso/OTI siguen dependiendo del gating por giro de `operacion`. La generalización de OF/Proyecto no requiere que `operacion` deje de ser un módulo de anuncios; requiere solo que sus endpoints no invoquen `giroCorresponde`. Esto es coherente con Materiales (mismo módulo `operacion`, sin `giroCorresponde`).
5. **Navegación (`AnunciosVertical.navegacion()`):** las entradas de OF y Proyectos permanecen para el giro anuncios (siguen viéndose ahí). Para otros giros, la visibilidad se resuelve por permiso en el frontend (§D) — no por el metadato del vertical anuncios.

**Impacto en `RegistroVerticales` (fail-fast).** El registro exige unicidad de giro/módulo/recurso entre verticales. Retirar `orden_fabricacion`/`proyecto` de `recursos()` no introduce duplicados; que `operacion` siga solo en anuncios preserva `giroDeModulo('operacion') = anuncios-luminosos`, determinista. El arranque no se rompe.

**Convivencia con lo que queda en anuncios.** Los submódulos de anuncios que **consumen** puertos de OF/Proyecto lo hacen a través de interfaces estables, no de persistencia:
- `AvanceSitioAdapter` (anuncios/proyecto → ahora núcleo/proyecto) compone `LevantamientoCompletadoPort`, `PermisoAprobadoPort`, `InstalacionCompletadaPort` (todos del vertical anuncios).
- `OrdenFabricacionTerminadaPort` (usado por instalación para programar OTIs) se mueve al Núcleo `produccion` como interfaz estable; su adaptador también. Instalación (anuncios) sigue dependiendo del **puerto**, ahora importado desde el Núcleo. Es una dependencia anuncios → Núcleo (permitida por la regla hexagonal: los verticales consumen capacidades del Núcleo por puertos, Req 4.5).

```
Dependencias tras el movimiento (todas anuncios → Núcleo, por puerto):

  anuncios.instalacion  ──(OrdenFabricacionTerminadaPort)──►  operacion.produccion   (Núcleo)
  operacion.proyecto (Núcleo) ──(AvanceSitioPort→3 puertos de fase)──►  anuncios.{levantamiento,permiso,instalacion}
  operacion.produccion (Núcleo) ──(ConsumoMaterialPort)──►  operacion.inventario    (Núcleo)
  operacion.produccion (Núcleo) ──(ClienteExistentePort)──►  comercial (adaptador de solo lectura)
```

> **Sobre el `AvanceSitioAdapter`:** un Proyecto del Núcleo (cualquier giro) no debe exigir los tres puertos de fase de anuncios. Ver la Decisión D5 (§A3): la derivación se parametriza por las fases aplicables al giro y el adaptador se divide en un adaptador base (solo producción) y uno de anuncios (las cuatro fases).

**¿El movimiento rompe imports/rutas?** El renombrado de paquete actualiza los `import` de todos los consumidores (instalación, proyecto, tests). Las rutas REST y los nombres de tabla no dependen del paquete Java. Los `@PreAuthorize` cambian solo en el texto de la expresión SpEL (quitar `giroCorresponde` en OF/Proyecto). El flujo de anuncios (génesis de OF desde Cotización, encadenamiento con levantamiento/permiso/OTI) se conserva porque el servicio, la entidad y las precondiciones no cambian de comportamiento (Req 16.1).

### Gating resultante por recurso

| Recurso | Módulo | ¿`giroCorresponde`? | Alcance |
|---|---|---|---|
| `material`, `movimiento_inventario` | `operacion` | No | Cualquier giro (ya vigente) |
| `orden_fabricacion` | `operacion` | **No (se retira)** | Cualquier giro (Req 2) |
| `proyecto` | `operacion` | **No (se retira)** | Cualquier giro (Req 3) |
| `levantamiento_sitio`, `permiso_instalacion`, `orden_trabajo_instalacion`, `cuadrilla`, `prueba_diseno` | `operacion` | Sí (se conserva) | Solo giro anuncios (Req 16.6) |

---

## Components and Interfaces

## BLOQUE A — Generalización del núcleo (cualquier giro)

### §A1 — Origen genérico de la Orden de Fabricación (Req 1)

**Cambio de esquema (migración V66, ver Data Models).** Hoy `orden_fabricacion.cotizacion_id` es `NOT NULL` con índice único `(tenant_id, cotizacion_id)`. Para permitir OF sin Cotización:
- `cotizacion_id` pasa a **NULLABLE**.
- El índice único de unicidad se vuelve **parcial**: `UNIQUE (tenant_id, cotizacion_id) WHERE cotizacion_id IS NOT NULL`. Así se conserva la regla "una Cotización → a lo sumo una OF" (Req 1.3) sin bloquear múltiples OF genéricas (todas con `cotizacion_id NULL`).

**Entidad `OrdenFabricacion` (Núcleo `produccion`).**
- El campo `cotizacionId` deja de ser `updatable=false`/obligatorio a nivel de columna; sigue inmutable tras la creación.
- Nueva factoría de dominio `crearDirecta(UUID clienteId, String actor)`: crea la OF en `pendiente`, con `cotizacionId = null`, `clienteId` obligatorio (422 si falta, Req 1.4). La factoría existente `generar(cotizacionId, clienteId, actor)` se conserva sin cambios (Req 1.3, 1.6).

**Servicio `ServicioOrdenesFabricacion` (Núcleo `produccion`).**
- Se conserva `generar(cotizacionId)` con sus 3 precondiciones y contrato (Req 1.3, 1.6).
- Nuevo método `crearDirecta(CrearOrdenDirectaCommand comando)` con `clienteId` y la lista de partidas iniciales (§B1):
  1. Si `clienteId` es nulo → 422 sin persistir (Req 1.4).
  2. Verifica el Cliente vía `ClienteExistentePort` (§A4): si no existe/no accesible → 404 + auditoría de acceso cruzado con recurso `cliente` (Req 1.5).
  3. Crea la OF con `OrdenFabricacion.crearDirecta(...)` en `pendiente`, persiste, persiste sus partidas (§B1) y audita la creación con recurso `orden_fabricacion` (Req 1.2).
- `consultar`, `cambiarEstado`, `listar` se comportan idénticamente con independencia del origen (Req 1.7): la única diferencia observable es `cotizacionId = null` en el DTO.

**Controlador `OrdenFabricacionController` (Núcleo `produccion`).**
- Se conserva `POST /ordenes-fabricacion` (cuerpo `{ cotizacionId }`) para la génesis desde Cotización (Req 1.6, contrato intacto).
- Nuevo `POST /ordenes-fabricacion/directa` (cuerpo `{ clienteId, partidas: [{ materialId, cantidad }] }`) para el origen genérico (Req 1.1). 201 con el DTO; 422 si falta Cliente o una cantidad ≤ 0; 404 si Cliente o Material no accesibles.
- Todos los endpoints con `@PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('orden_fabricacion','<op>')")` (sin `giroCorresponde`, §A2).

### §A2 — Gating por giro relajado en Órdenes de Fabricación (Req 2)

- En los cuatro endpoints de OF (`crear`, `directa`, `leer`, `cambiar_estado`, `listar`) se **retira** `and @autorizador.giroCorresponde('operacion')`. Se conserva `moduloHabilitado('operacion')` y el permiso atómico (Req 2.1, 2.2). Un usuario sin módulo o sin permiso sigue recibiendo 403 con la auditoría de denegación actual (Req 2.3).
- El aislamiento intra-tenant no cambia: `OrdenFabricacion extends TenantScopedEntity`, RLS activa, `tenant_id` desde `TenantContext` (Req 2.4).
- Frontend (§D): la ruta `ordenes-fabricacion` pierde `guardaGiro(GIRO_ANUNCIOS)` (Req 2.5).

### §A3 — Proyectos multi-sitio genéricos con derivación por fases aplicables (Req 3)

**Decisión (D5) — Fases aplicables por giro.** Se introduce en el dominio `proyecto` un valor `PerfilFasesGiro` que enumera qué fases aplican:

```
enum FaseProyecto { LEVANTAMIENTO, PERMISO, PRODUCCION, INSTALACION }

record PerfilFasesGiro(Set<FaseProyecto> fasesAplicables) {
    // Anuncios: las cuatro fases (secuencia completa).
    static PerfilFasesGiro ANUNCIOS = new PerfilFasesGiro(EnumSet.allOf(FaseProyecto.class));
    // Genérico (otros giros): solo producción (OF terminada por Sitio).
    static PerfilFasesGiro GENERICO = new PerfilFasesGiro(EnumSet.of(FaseProyecto.PRODUCCION));
}
```

**`DerivacionEstadoProyecto` (función pura, refactor generalizador).** Nueva firma:

```
EstadoConsolidadoProyecto derivar(PerfilFasesGiro perfil, List<AvanceFasesSitio> avances)
```

- Sigue siendo pura y determinista (Req 3.6). Recorre las fases **en orden** pero **solo las contenidas en `perfil.fasesAplicables()`**, devolviendo la primera fase aplicable no cubierta por todos los Sitios; si todas las aplicables están cubiertas → `COMPLETADO` (Req 3.2, 3.3). Lista vacía → `SIN_SITIOS` con independencia del giro (Req 3.4).
- **Compatibilidad anuncios:** con `PerfilFasesGiro.ANUNCIOS` el resultado es idéntico a la derivación actual (Levantamiento → Permiso → Producción → Instalación) (Req 3.5). La firma vieja `derivar(avances)` se conserva como conveniencia delegando en `derivar(ANUNCIOS, avances)` para no romper llamadores/tests existentes.
- Para el perfil `GENERICO`, solo se evalúa `PRODUCCION` (`tieneOrdenFabricacionTerminada`): todos los Sitios con OF terminada → `COMPLETADO`; alguno sin ella → `EN_PRODUCCION` (Req 3.2).

**Resolución del perfil de giro.** `ServicioProyectos.consultar` obtiene el perfil vía un `PerfilFasesGiroPort` de solo lectura, cuyo adaptador consulta el giro del tenant (`GiroEmpresaPort`, ya existente): `anuncios-luminosos → ANUNCIOS`, cualquier otro → `GENERICO`. Así el mismo servicio del Núcleo sirve a todos los giros sin ramas por giro embebidas.

**Cómputo del avance por Sitio (D5-b).** El `AvanceSitioPort` se conserva, pero su composición se separa:
- `AvanceProduccionAdapter` (Núcleo `proyecto`): computa solo `tieneOrdenFabricacionTerminada` (vía `OrdenFabricacionTerminadaPort` / puerto de producción por Sitio). Las otras tres banderas quedan en `false` para giros no-anuncios (no se consultan puertos de anuncios inexistentes para su flujo).
- `AvanceSitioAnunciosAdapter` (vertical anuncios): compone las cuatro fases como hoy. Se registra solo para el giro anuncios.

La selección del adaptador correcto la resuelve `ServicioProyectos` según el `PerfilFasesGiro`: para `GENERICO` usa el adaptador de producción; para `ANUNCIOS`, el de las cuatro fases. Esto evita que un proyecto genérico dependa de los tres puertos de anuncios.

- Gating de los endpoints de Proyecto: `moduloHabilitado('operacion') and @autorizador.tiene('proyecto','<op>')` (sin `giroCorresponde`, Req 3.1).

### §A4 — Verificación de existencia del Cliente (Req 4)

**Nuevo puerto de solo lectura `ClienteExistentePort`** (definido en la capa `application` de quien lo consume — `produccion` y `proyecto` — como interfaz estable):

```
interface ClienteExistentePort {
    /** true si el Cliente existe y es accesible en el tenant vigente (RLS). */
    boolean existeEnTenant(UUID clienteId);
}
```

**Adaptador `ClienteExistenteAdapter`** en el módulo **comercial** (donde vive el Cliente): consulta el repositorio de Cliente acotado al tenant (RLS + `TenantContext`), devolviendo `true`/`false`. Es de solo lectura y no expone la entidad Cliente fuera de comercial (respeta el aislamiento entre módulos, Req 16.4).

**Uso en `ServicioProyectos.crear` (Req 4).**
1. `clienteId` nulo → 422 sin persistir (Req 4.3).
2. `!clienteExistente.existeEnTenant(clienteId)` → 404 + auditoría de acceso cruzado con recurso `cliente` (Req 4.2).
3. Cliente accesible → crea el Proyecto en `SIN_SITIOS` y audita (Req 4.4).
4. Contrato `POST /proyectos` intacto (cuerpo `{ clienteId, nombre }`, Req 4.5).

El mismo puerto lo usa `ServicioOrdenesFabricacion.crearDirecta` (Req 1.5).

---

## BLOQUE B — Consumo de materiales en la OF (cualquier giro)

### §B1 — Partidas/BOM de la Orden de Fabricación (Req 5)

**Tabla nueva `partida_orden_fabricacion`** (tenant-scoped, RLS, concurrencia optimista; ver Data Models). Entidad `PartidaOrdenFabricacion` (Núcleo `produccion`, `TenantScopedEntity`): `id`, `orden_fabricacion_id` (FK), `material_id`, `cantidad NUMERIC(18,4) > 0`.

**Reglas (Req 5).**
- Cada partida referencia un Material y una cantidad positiva; cantidad ≤ 0 → 422 sin persistir la partida (Req 5.1, 5.2).
- Material no accesible en el tenant → 404 + auditoría de acceso cruzado (se verifica con el puerto de lectura del inventario del Núcleo antes de persistir, Req 5.3).
- **Editables solo mientras la OF esté en `pendiente`** (Req 4 de Decisiones acordadas; Req 5): un servicio `agregarPartida`/`reemplazarPartidas`/`eliminarPartida` verifica el estado de la OF y rechaza con 422 ("las partidas solo se editan con la Orden_Fabricacion en pendiente") si la OF ya salió de `pendiente`.
- Persistencia tenant-scoped con RLS y `version` (Req 5.4), siguiendo el patrón de `Material`/`Movimiento_Inventario`.
- La consulta de OF expone sus partidas (Material + cantidad) en el DTO enriquecido `OrdenFabricacionDetalleDto` (Req 5.5); el frontend muestra el nombre del Material, nunca el UUID (§D2).

**Endpoints (Núcleo `produccion`).**
- `POST /ordenes-fabricacion/{id}/partidas` (permiso `orden_fabricacion:cambiar_estado`): agrega/reemplaza partidas (solo si `pendiente`).
- `GET /ordenes-fabricacion/{id}` devuelve `OrdenFabricacionDetalleDto` con la lista de partidas. El listado (`GET /ordenes-fabricacion`) sigue devolviendo el DTO de resumen (sin partidas) para no cambiar su forma.

### §B2 — Consumo atómico de materiales al fabricar (Req 6)

**Decisión (D3) — Punto de consumo: transición `pendiente → en_produccion`.** Coherente con la Decisión acordada 2 (reserva del material al arrancar la fabricación). El wiring vive en `ServicioOrdenesFabricacion.cambiarEstado`, dentro de la **misma transacción** que el cambio de estado:

```
@Transactional
cambiarEstado(ordenId, nuevoEstado):
    orden   = cargar(ordenId)                 // 404 si no accesible
    destino = interpretar(nuevoEstado)         // 422 si etiqueta desconocida
    if destino == EN_PRODUCCION:
        partidas = partidaRepo.findByOrdenId(orden.id)
        if !partidas.isEmpty():
            consumos = partidas.map(p -> new ConsumoMaterial(p.materialId, p.cantidad))
            consumoMaterialPort.consumirParaOrdenFabricacion(orden.id, consumos)  // 422 si insuficiente; 404 si material inaccesible
    orden.cambiarEstado(destino, actor)        // 409 si transición inválida (máquina pura)
    save(orden); auditar(consumo + cambio)
```

- El consumo se invoca **antes** de confirmar el cambio de estado, pero ambos dentro de la misma transacción `@Transactional` (Req 6.5). Si `consumirParaOrdenFabricacion` lanza 422 (existencias insuficientes) o 404, la transacción hace rollback: no se persiste ningún `Movimiento_Inventario`, ni el cambio de estado de la OF (Req 6.3, atomicidad).
- Cuando el consumo procede: `ConsumoMaterialPort` registra un `Movimiento_Inventario` `salida` por Material y descuenta existencias (Req 6.1, 6.2). La no-negatividad la garantiza `Material.aplicarMovimiento` (valida antes de mutar).
- OF sin partidas: la transición ocurre sin generar movimientos (Req 6.6).
- La acción se audita con actor, recurso `orden_fabricacion`, la OF y el detalle del consumo (Req 6.4). El consumo respeta el aislamiento multi-tenant (materiales/movimientos del mismo tenant; `ConsumoMaterialPort` ya lo hace, Req 6.7).

> **Nota de orden de operaciones.** Como la máquina de estados valida la transición dentro de `orden.cambiarEstado(...)`, y el consumo se hace **solo** cuando `destino == EN_PRODUCCION`, un intento de transición inválida (p. ej. `terminada → en_produccion`) debe rechazarse con 409 **sin** consumir. Para ello, el diseño comprueba primero la validez de la transición con `orden.getEstado().puedeTransicionarA(destino)` antes de invocar el consumo, y solo consume si la transición es válida y el destino es `EN_PRODUCCION`. Así el consumo nunca ocurre en una transición que luego sería rechazada.

---

## BLOQUE C — Exponer datos ya persistidos (giro anuncios)

### §C1 — Exposición de fotos del Levantamiento (Req 7)

**DTO enriquecido `LevantamientoSitioDetalleDto`** (vertical anuncios/levantamiento): el `LevantamientoSitioDto` actual + `List<LevantamientoFotoDto> fotos`. La consulta `GET /levantamientos/{id}` devuelve el DTO enriquecido con las fotos vinculadas (lista vacía si no hay) (Req 7.1, 7.2).

**Endpoint de lectura dedicado** `GET /levantamientos/{id}/fotos` (permiso `levantamiento_sitio:leer`, con `moduloHabilitado('operacion') and giroCorresponde('operacion')`, Req 7.4): devuelve `List<LevantamientoFotoDto>` (referencias), incluyendo lista vacía (Req 7.2). 404 + auditoría si el Levantamiento no es accesible (Req 7.3).

`ServicioLevantamientos` gana `fotosDe(levantamientoId)` (lee `LevantamientoFotoRepository` acotado al tenant tras verificar acceso al Levantamiento). El alta de fotos (`POST /levantamientos/{id}/fotos`) y su auditoría se conservan (Req 7.5).

### §C2 — Pendientes y evidencias de OTI + guarda de cierre informativa (Req 8)

**DTO enriquecido `OrdenTrabajoInstalacionDetalleDto`** (vertical anuncios/instalacion): el DTO actual + `List<PendienteInstalacionDto> pendientes` + `List<EvidenciaInstalacionDto> evidencias`. `GET /ordenes-trabajo-instalacion/{id}` devuelve el DTO enriquecido (Req 8.1), con listas vacías cuando no haya (Req 8.2, 8.3).

**Endpoints de lectura dedicados** (permiso `orden_trabajo_instalacion:leer`, gating de anuncios):
- `GET /ordenes-trabajo-instalacion/{id}/pendientes` → `List<PendienteInstalacionDto>` (cada uno con `resuelto`) (Req 8.2).
- `GET /ordenes-trabajo-instalacion/{id}/evidencias` → `List<EvidenciaInstalacionDto>` (Req 8.3).

**Resolución de pendientes.** Se conserva la ruta de avance (`POST /ordenes-trabajo-instalacion/{id}/avance`, permiso `orden_trabajo_instalacion:cambiar_estado`) que ya marca pendientes como `resuelto` y audita (Req 8.4). 404 si la OTI/pendiente/evidencia no son accesibles (Req 8.6).

**Guarda de cierre informativa (Req 8.5).** Hoy el cierre con pendientes lanza 422 con "existen pendientes por resolver". Se enriquece el mensaje para **informar qué pendientes faltan**: `ServicioOrdenesTrabajoInstalacion.cambiarEstado`, antes de completar, consulta `pendienteRepository.findByOrdenTrabajoInstalacionIdAndResueltoFalse(otiId)` y, si hay elementos, lanza 422 con un mensaje que enumera sus descripciones (p. ej. "no se puede completar: pendientes por resolver: [«fijar anclas», «conectar acometida»]"). Se conserva el orden: la validez de la transición (409) se comprueba antes que la guarda de pendientes (Req 8.5). Permisos y gating de anuncios sin cambios (Req 8.7).

---

## BLOQUE D — Frontend enterprise (UX)

### §D1 — Vistas de detalle (Req 9)

**Decisión (D6) — Reutilizar el patrón `proyecto-detalle`.** `proyecto-detalle.ts` (única vista de detalle existente) fija el patrón: `input.required<string>()` para el `:id` de la ruta, `signal` de fase (`cargando|ok|error|vacio`) + `mensajeError`, `StateContainer` para carga/vacío/error, `PageHeader`, acciones por permiso vía `AuthService.tienePermiso(...)`, textos es-MX y solo design tokens. Se crean cuatro vistas de detalle nuevas siguiendo ese patrón:

- **`orden-fabricacion-detalle`** (Genérico, ruta `ordenes-fabricacion/:id`): información de la OF (Cliente por nombre, estado con etiqueta es-MX, origen "directa"/"desde cotización"), **tabla de partidas** (Material por nombre + cantidad, §B1), y acciones de estado **contextualizadas** al estado actual (solo las transiciones válidas de la máquina: desde `pendiente` → "Iniciar producción"/"Cancelar"; desde `en_produccion` → "Terminar"/"Cancelar"; estados finales sin acciones) (Req 9.1, 9.5).
- **`levantamiento-detalle`** (anuncios): información + **galería de fotos** (§C1) (Req 9.2).
- **`permiso-detalle`** (anuncios): información + acciones aprobar/rechazar contextualizadas (solo si `solicitado`) (Req 9.3).
- **`oti-detalle`** (anuncios): información + **lista de pendientes** con acción "Resolver", **galería de evidencias**, acciones de estado contextualizadas (Req 9.4). Al intentar completar con pendientes, se muestra el mensaje 422 informativo de §C2 (Req 9.7).

Reglas transversales: listas vacías → estado vacío en es-MX (Req 9.6); error de operación → mensaje es-MX (Req 9.7); responsivas, WCAG AA, solo tokens (Req 9.8). Rutas de detalle: OF sin `guardaGiro`; Levantamiento/Permiso/OTI con `guardaGiro(GIRO_ANUNCIOS)`.

### §D2 — Eliminación de UUIDs (Req 10)

**Nuevo `NombresOperacionService`** análogo a `NombresInventarioService`: carga una vez (páginas ≤ 200) los catálogos necesarios y arma mapas `id → nombre` con marcador neutro `(sin nombre)` para ids no resueltos (nunca el UUID). Resuelve nombres para **Cotización, Orden de Fabricación, Sitio, Cuadrilla, Cliente y Material** (Req 10.1, 10.2). Reutiliza `MaterialesService` para Materiales (mismo catálogo del Núcleo, sin duplicar).

- Todos los selectores usan el componente compartido **`app-entity-select`** sobre listas en memoria (selección por nombre; internamente usa el id sin mostrarlo, Req 10.1, 10.3).
- Tablas y detalles muestran el nombre legible, no el id ni un recorte (Req 10.2).
- Selectores accesibles (WCAG AA), responsivos, con tokens y es-MX (Req 10.4); búsqueda sin coincidencias → estado sin resultados en es-MX (Req 10.5).

### §D3 — Filtros soportados por el backend (Req 11)

- **OF (Genérico):** el listado gana un filtro por **Cliente** (por nombre, vía `entity-select`) además del filtro por estado. `ProduccionService.listar` se amplía para enviar `clienteId` (el backend ya lo soporta en `ServicioOrdenesFabricacion.listar(estado, clienteId, ...)`) (Req 11.1).
- **OTI (anuncios):** el listado gana filtros por **Cliente** y por **Cuadrilla** (por nombre) además del estado; `OtisService.listar` ya acepta `FiltroOti { estado, cuadrillaId, clienteId }` (Req 11.2).
- La selección por nombre envía al backend el `id` correspondiente (Req 11.3); sin coincidencias → estado vacío es-MX (Req 11.4); controles accesibles/responsivos/tokens (Req 11.5).

### §D4 — `agregarFotos` en Levantamientos (Req 12)

- En la vista de Levantamientos (y en `levantamiento-detalle`) se implementa `agregarFotos`: el usuario captura una o más referencias y se envían a `POST /levantamientos/{id}/fotos`; la vista refleja las fotos adjuntas recargando la galería (Req 12.1).
- Sin referencias → no se llama al backend y se muestra validación es-MX (Req 12.2). Fallo de la operación → mensaje es-MX conservando el estado previo (Req 12.3). Carga accesible (WCAG AA), responsiva, con tokens (Req 12.4).

---

## BLOQUE E — Notificación de vencimiento de permisos (anuncios)

### §E1 — Disparo (planificador + endpoint) y notificación real (Req 13)

**Decisión (D7) — Notificador real integrado con el módulo `notificaciones`.** Nuevo adaptador `NotificadorPermisoNotificaciones` (vertical anuncios/permiso, `adapter/out`) que implementa `NotificadorPermisoPort` delegando en `NotificacionPort.notificar(...)`:

```
notificarVencimientoProximo(NotificacionVencimientoPermiso n):
    contenido = "Permiso próximo a vencer. Sitio " + n.sitioId()  // sin UUID crudo al usuario final; el frontend resuelve nombre
              + ", tipo " + n.tipo() + ", vence " + n.fechaVencimiento()
              + " (" + n.diasParaVencer() + " días)."
    notificacionPort.notificar(new SolicitudNotificacion(
        TipoEventoNotificacion.PERMISO_POR_VENCER,
        canalPreferido,                 // CORREO por defecto (ver resolución de destinatario)
        destinatario,                   // correo del responsable del Sitio o de la Empresa
        "Permiso por vencer",           // asunto minimizado
        contenido,                      // contenido minimizado (Req 46.4)
        false,                          // no es marketing → sin guarda Opt-In
        "permiso_instalacion", n.permisoId()))
```

- Se registra como bean del canal real y, por `@ConditionalOnMissingBean`, **reemplaza automáticamente** a `NotificadorPermisoRegistroLog` (que ya es el placeholder condicional). La notificación deja de ser "solo log": genera una `Notificacion` persistida (estado `enviada`/`fallida`/`omitida`) consultable en el sistema de notificaciones (la "campana" es la bandeja de `Notificacion` del tenant) e integrada con reintentos y auditoría (Req 13.2). La `Notificacion` incluye Sitio, tipo, fecha de vencimiento y días restantes en su contenido (Req 13.3).
- **Resolución de destinatario/canal (mínima y explícita):** el adaptador usa `CanalNotificacion.CORREO` como canal por defecto y resuelve el `destinatario` con un puerto de lectura mínimo (correo del responsable del Sitio si existe; en su defecto, el correo de contacto de la Empresa del tenant). Si no hay destinatario resoluble, registra la omisión (sin romper el barrido). No se usa un canal social para este aviso (no es marketing), evitando la guarda de Opt-In.

**Decisión (D8) — Disparo dual: planificador diario + endpoint manual.**

*Planificador (multi-tenant).* Nuevo `ProgramadorVencimientosPermisos` (vertical anuncios/permiso), patrón `ProgramadorRespaldos`:
- `@Component @ConditionalOnProperty(name="crm.permisos.vencimientos.habilitado", havingValue="true")` para que en pruebas y arranques sin configurar no se dispare.
- `@Scheduled(cron="${crm.permisos.vencimientos.cron}")` (por defecto diario, p. ej. `0 0 6 * * *`).
- **Iteración multi-tenant** (Req 13.6): enumera las Empresas activas (vía un puerto de lectura `EmpresasActivasPort` cuyo adaptador delega en `ServicioEmpresas`/repositorio de Empresa) y, por cada `tenantId`, dentro de una transacción: `TenantContext.set(tenantId)` + `TenantSessionInitializer.applyTenant(tenantId)` (fija RLS) e invoca `servicioPermisos.notificarVencimientosProximos()`, con `TenantContext.clear()` en `finally`. Un fallo por tenant se registra y no detiene el barrido del resto. La localización opera solo sobre permisos del tenant en contexto (Req 13.6). Para tenants sin permisos por vencer, el barrido no emite nada (Req 13.5). (El barrido corre para todos los giros; en giros no-anuncios simplemente no hay permisos, por lo que es inocuo.)

*Endpoint manual.* Nuevo `POST /permisos-instalacion/notificar-vencimientos` en el controlador de permisos (vertical anuncios): opera sobre el tenant en contexto e invoca `notificarVencimientosProximos()`, devolviendo el número de notificaciones emitidas (Req 13.1). Gating: `moduloHabilitado('operacion') and giroCorresponde('operacion') and @autorizador.tiene('permiso_instalacion','cambiar_estado')` (permiso atómico específico de anuncios, Req 13.4). Reutiliza `cambiar_estado` por coherencia (es una acción de gestión del ciclo de vida del permiso; V5 no define `notificar`).

`ServicioPermisos.notificarVencimientosProximos()` **no cambia** su lógica (localiza `aprobado` que vencen en ≤ 30 días y emite por cada uno); solo gana dos disparadores y un notificador real.

---

## Data Models

### Tablas nuevas (migración Flyway V66)

La última migración existente es **V65**; la nueva es **`V66__operacion_produccion_generalizacion.sql`**. Agrupa: (1) generalización de `orden_fabricacion`, (2) tabla de partidas/BOM, (3) reclasificación/seed de permisos. Toda tabla tenant-scoped lleva RLS por `tenant_id` y `version` (concurrencia optimista), como el resto del módulo.

**1) `orden_fabricacion` — generalización (Req 1).**
```sql
-- cotizacion_id pasa a NULLABLE (OF de origen genérico no tiene Cotización).
ALTER TABLE orden_fabricacion ALTER COLUMN cotizacion_id DROP NOT NULL;

-- La unicidad "una Cotización -> a lo sumo una OF" se vuelve PARCIAL
-- (no aplica a OF genéricas, cuyo cotizacion_id es NULL).
DROP INDEX IF EXISTS uq_orden_fabricacion_cotizacion;
CREATE UNIQUE INDEX uq_orden_fabricacion_cotizacion
    ON orden_fabricacion (tenant_id, cotizacion_id)
    WHERE cotizacion_id IS NOT NULL;
```
Nota de implementación: el nombre real del índice existente (V17) se confirma leyendo V17 antes de escribir el SQL final.

**2) `partida_orden_fabricacion` — Partidas/BOM (Req 5).**
```sql
CREATE TABLE partida_orden_fabricacion (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    orden_fabricacion_id  UUID        NOT NULL,
    material_id           UUID        NOT NULL,
    cantidad              NUMERIC(18,4) NOT NULL CHECK (cantidad > 0),
    version               BIGINT      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            VARCHAR(120),
    updated_by            VARCHAR(120),
    CONSTRAINT fk_partida_of_orden
        FOREIGN KEY (orden_fabricacion_id) REFERENCES orden_fabricacion (id) ON DELETE CASCADE,
    CONSTRAINT fk_partida_of_material
        FOREIGN KEY (material_id) REFERENCES material (id)
);
CREATE INDEX ix_partida_of_orden ON partida_orden_fabricacion (tenant_id, orden_fabricacion_id);
-- RLS: política tenant_isolation análoga al resto de tablas tenant-scoped (V2 patrón).
ALTER TABLE partida_orden_fabricacion ENABLE ROW LEVEL SECURITY;
-- (policy tenant_isolation USING/ WITH CHECK sobre app.current_tenant, según patrón V2)
```

**3) Reclasificación y seed de permisos (Req 2, 3; Decisión acordada 8).**
- Los permisos `orden_fabricacion:{crear,leer,listar,cambiar_estado}` y `proyecto:{crear,leer,listar,actualizar}` **ya existen** en `permiso` (sembrados en V5). No se re-crean.
- La generalización real de la clasificación por giro la hace el código (retirar `orden_fabricacion`/`proyecto` de `AnunciosVertical.recursos()`, D2). La migración **siembra la asignación de esos permisos a roles predefinidos usados por otros giros** para que su acceso quede habilitado sin tocar datos de anuncios, con el patrón idempotente de V5/V10:
```sql
-- Asignar orden_fabricacion:* y proyecto:* al rol 'gerente' (a0000000-...-003),
-- transversal a giros, para no bloquear a giros no-anuncios (idempotente).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000003', p.id
FROM permiso p
WHERE p.recurso IN ('orden_fabricacion', 'proyecto')
ON CONFLICT DO NOTHING;
```
No se altera ningún permiso/rol/dato del giro anuncios (los roles `produccion`/`ventas` ya tenían estos permisos). El conjunto exacto de roles destino se afina en implementación según los roles predefinidos disponibles para otros giros.

### Modelos de dominio nuevos / modificados (no persistentes salvo lo anterior)

- **Backend:** `OrdenFabricacion` (`cotizacionId` nullable + factoría `crearDirecta`); `PartidaOrdenFabricacion` (entidad); `OrdenFabricacionDetalleDto` (OF + partidas); `PartidaOrdenFabricacionDto`; `CrearOrdenDirectaCommand`; `ClienteExistentePort` + `ClienteExistenteAdapter` (comercial); `PerfilFasesGiro` + `FaseProyecto` + `PerfilFasesGiroPort`; `DerivacionEstadoProyecto.derivar(perfil, avances)`; `AvanceProduccionAdapter` (Núcleo) y `AvanceSitioAnunciosAdapter` (anuncios); `LevantamientoSitioDetalleDto`; `OrdenTrabajoInstalacionDetalleDto`; `NotificadorPermisoNotificaciones`; `ProgramadorVencimientosPermisos`; `EmpresasActivasPort`.
- **Frontend:** `NombresOperacionService`; modelos de partida (`Partida { materialId, cantidad }`), `OrdenFabricacionDetalle`, `LevantamientoDetalle` (con `fotos`), `OtiDetalle` (con `pendientes`, `evidencias`); ampliación de `ProduccionService.listar` (parámetro `clienteId`) y `crearDirecta`; servicio de partidas; `agregarFotos` en `LevantamientosService`.

---

## Correctness Properties

_Una propiedad es una característica o comportamiento que debe cumplirse en todas las ejecuciones válidas del sistema — una afirmación formal de lo que el sistema debe hacer. Las propiedades son el puente entre la especificación legible por humanos y garantías de corrección verificables por máquina._

> **Aplicabilidad de PBT.** El corazón lógico de este spec —la **derivación pura del estado de Proyecto** por fases aplicables, el **consumo atómico de materiales** y las **reglas de negocio de las partidas y la guarda de cierre de OTI**— tiene propiedades universales claras (determinismo, todo-o-nada, no-negatividad, equivalencia por perfil) y es **ideal para property-based testing** con **jqwik** (ya presente en `backend/`, hay `.jqwik-database`). En cambio, el **gating/autorización**, la **exposición de datos ya persistidos** (fotos, pendientes, evidencias), el **barrido multi-tenant del planificador** y todo el **frontend** no son adecuados para PBT (autorización determinista, infraestructura/RLS, rendering/UX): se cubren con pruebas de controlador/servicio de ejemplo, tests de integración/repositorio y specs de componente (ver Testing Strategy). Tras la reflexión de propiedades se consolidaron las derivaciones (determinismo + completitud + fase-más-temprana + equivalencia por perfil) para que cada propiedad aporte valor de validación único y no redundante.

### Property 1: Derivación del estado de Proyecto por fases aplicables

*Para toda* lista de avances de Sitios y todo `PerfilFasesGiro`, `DerivacionEstadoProyecto.derivar(perfil, avances)` cumple: una lista vacía produce `SIN_SITIOS`; en otro caso el resultado es `COMPLETADO` **si y solo si** todos los Sitios cubren todas las fases aplicables del perfil, y en caso contrario el resultado corresponde a la **primera fase aplicable** (en el orden Levantamiento → Permiso → Producción → Instalación) que no cubran todos los Sitios. La función es determinista (misma entrada ⇒ misma salida).

**Validates: Requirements 3.2, 3.3, 3.4, 3.6**

### Property 2: La derivación con perfil de anuncios equivale a la secuencia completa clásica

*Para toda* lista de avances de Sitios, `derivar(PerfilFasesGiro.ANUNCIOS, avances)` produce el mismo estado consolidado que la derivación clásica de anuncios (secuencia Levantamiento → Permiso → Producción → Instalación); y `derivar(PerfilFasesGiro.GENERICO, avances)` depende únicamente de la fase Producción (todos los Sitios con Orden_Fabricacion terminada ⇒ `COMPLETADO`; alguno sin ella ⇒ `EN_PRODUCCION`).

**Validates: Requirements 3.2, 3.5**

### Property 3: Consumo de materiales atómico y no-negativo

*Para toda* Orden de Fabricación con un conjunto de partidas y todo estado de existencias, al ejecutar la transición `pendiente → en_produccion`: si algún consumo dejaría las existencias de algún Material por debajo de cero, entonces **ningún** `Movimiento_Inventario` se persiste y la Orden de Fabricación **permanece en `pendiente`** (rechazo 422, todo-o-nada); si todos los consumos caben, entonces se registra exactamente un `Movimiento_Inventario` `salida` por partida, las existencias resultantes de cada Material son **mayores o iguales a cero**, y la Orden pasa a `en_produccion`. Una Orden sin partidas transita sin generar movimientos.

**Validates: Requirements 6.1, 6.2, 6.3, 6.5, 6.6**

### Property 4: Las partidas/BOM solo se editan mientras la OF está en `pendiente`

*Para toda* Orden de Fabricación, agregar, reemplazar o eliminar partidas es aceptado **si y solo si** la Orden está en estado `pendiente`; en cualquier otro estado la edición se rechaza con 422 y las partidas persistidas quedan sin cambios.

**Validates: Requirements 5.1, 5.2**

### Property 5: La guarda de cierre de OTI informa los pendientes no resueltos

*Para toda* Orden_Trabajo_Instalacion en estado `en_curso` con un conjunto de pendientes, completar la orden es aceptado **si y solo si** no existe ningún pendiente sin resolver; si existe al menos uno, la transición se rechaza con 422, el estado se conserva y el mensaje de error enumera exactamente las descripciones de los pendientes no resueltos.

**Validates: Requirements 8.5**

### Property 6: Verificación de existencia del Cliente

*Para todo* `clienteId` usado al crear un Proyecto (o una Orden de Fabricación por origen genérico): si es nulo, la creación se rechaza con 422 sin persistir; si no es nulo pero el Cliente no existe o no es accesible en el tenant, se responde 404 y no se persiste el recurso; y solo cuando el Cliente existe y es accesible se crea el recurso (Proyecto en `SIN_SITIOS`, Orden de Fabricación en `pendiente`).

**Validates: Requirements 4.1, 4.2, 4.3, 1.4, 1.5**

---

## Error Handling

- **Creación de OF/Proyecto sin Cliente o con Cliente inexistente:** `clienteId` nulo → 422 (regla de negocio) sin persistir; Cliente no accesible (`ClienteExistentePort.existeEnTenant == false`) → 404 + auditoría de acceso cruzado con recurso `cliente`. Mapeado por `ManejadorGlobalErrores`.
- **Partidas:** cantidad ≤ 0 → 422 sin persistir la partida; Material inaccesible → 404 + auditoría; editar partidas con la OF fuera de `pendiente` → 422 ("las partidas solo se editan con la Orden_Fabricacion en pendiente").
- **Consumo de materiales:** existencias insuficientes → 422 ("existencias insuficientes") desde `Material.aplicarMovimiento`; al correr dentro de la transacción de `cambiarEstado`, el rollback deja intactos existencias, movimientos y el estado de la OF (atomicidad). Transición inválida (p. ej. desde estado final) → 409 **antes** de consumir (se comprueba `puedeTransicionarA` previo al consumo).
- **Exposición de fotos/pendientes/evidencias:** recurso padre inaccesible → 404 + auditoría; listas vacías se devuelven como `[]` (no 404).
- **Guarda de cierre de OTI:** primero 409 si la transición no es válida en la máquina; luego 422 con la enumeración de pendientes no resueltos (Req 8.5).
- **Notificación de vencimientos:** el barrido por tenant captura fallos por tenant, los registra y continúa con el resto (un tenant que falle no aborta el job). Si no hay destinatario resoluble para un permiso, la `Notificacion` se registra como omitida con su motivo (sin romper el barrido). El endpoint manual devuelve el conteo emitido; 403 si falta módulo/giro/permiso.
- **Gating:** ausencia de módulo `operacion` o del permiso atómico → 403 con la auditoría de denegación vigente; para Levantamiento/Permiso/OTI, giro distinto de anuncios → 403 con auditoría `denegar_giro`.
- **Auditoría JSONB:** se conserva el saneamiento `aJson(...)` de `ServicioInventario`/servicios de auditoría; no se introduce ninguna escritura de auditoría que evada ese punto único.

---

## Testing Strategy

### Enfoque dual

- **Property-based tests (jqwik, lógica pura y reglas de negocio):** las 6 propiedades de arriba. Mínimo **100 iteraciones** por propiedad; cada test etiquetado con un comentario `Feature: operacion-produccion-enterprise, Property N: <texto>`. Se elige jqwik (ya presente en el backend) y **no** se implementa PBT desde cero. Cada propiedad se implementa con **una sola** prueba de propiedades:
  - **Property 1 y 2:** generadores de `List<AvanceFasesSitio>` (cada bandera aleatoria) y de `PerfilFasesGiro`; puras, sin infraestructura (costo mínimo).
  - **Property 3:** generador de OF con partidas y de existencias por Material, usando una implementación in-memory del `ConsumoMaterialPort` (o el `ServicioInventario` con repos en memoria) para mantener el costo bajo; verifica atomicidad, no-negatividad y coherencia de estado.
  - **Property 4:** generador de estado de OF; edición de partidas permitida sii `pendiente`.
  - **Property 5:** generador de conjuntos de pendientes (resueltos/no) sobre una OTI `en_curso`; completar aceptado sii no hay no resueltos; el mensaje contiene las descripciones no resueltas.
  - **Property 6:** generador de `clienteId` (nulo / inexistente / existente) con `ClienteExistentePort` fake; verifica 422 / 404 / creación.
- **Unit / controller / service tests (ejemplos, bordes, integración):**
  - **OTI (Req 14.1):** controller y service — consulta con pendientes/evidencias (con y sin), resolución de pendientes, y guarda de cierre (422 informativo).
  - **Derivación de estado de Proyecto (Req 14.2):** unit tests de ejemplo (perfiles ANUNCIOS y GENERICO) complementando las propiedades.
  - **OF genérica y no-regresión de anuncios (Req 14.4):** controller test — usuario de giro no-anuncios con módulo `operacion` + `orden_fabricacion:crear` crea una OF directa (201); el flujo de anuncios conserva sus 3 precondiciones (Cotización aprobada, sin OF previa, Prueba_Diseño aprobada).
  - **Verificación de Cliente en Proyecto (Req 14.5):** service/controller — Cliente inexistente → 404 + auditoría; nulo → 422; existente → creado.
  - **Consumo (integración inventario):** 1-2 ejemplos de extremo a extremo con el `ServicioInventario` real para confirmar el cableado del `ConsumoMaterialPort` en la transición.
  - **Notificaciones (integración):** service test con repositorio/notificador mockeados — N permisos en ventana → N notificaciones; 0 → 0; barrido multi-tenant con 2 tenants (solo cuenta los del tenant en contexto).
- **Frontend (specs de componente, one-shot, Req 15):**
  - `.spec.ts` para las cinco vistas (Órdenes de Fabricación, Levantamientos, Permisos, Instalación, Proyectos) y las vistas de detalle nuevas.
  - Verificación de accesibilidad (axe) ejecutada **en un solo intento (one-shot)**, con el spec en aislamiento, para evitar inestabilidad (Req 15.2).
  - Aserción de que las vistas **no muestran UUIDs crudos ni recortes** de identificador (Req 15.3).

### Verificación

- **Backend:** `mvn -o test` de las clases tocadas (produccion, proyecto, partidas, permiso/notificador, migración V66) y luego build; la suite existente debe seguir verde (Req 16).
- **Frontend:** `ng build` de producción (0 errores) + los specs tocados en aislamiento (one-shot).
- **No se levantan servidores ni watchers** como parte del diseño/pruebas automatizadas; los comandos de larga duración se dejan al usuario.
- Limpieza de cualquier archivo temporal de verificación.

---

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Mover OF/Proyecto de paquete rompe imports/rutas | Refactor de renombrado de paquete (actualiza imports); rutas REST y nombres de tabla no dependen del paquete; contratos y esquema intactos. |
| Retirar `giroCorresponde` de OF/Proyecto abre acceso no deseado | Se conserva `moduloHabilitado('operacion')` + permiso atómico + RLS; solo se retira la condición de giro; Levantamiento/Permiso/OTI conservan su gating por giro. |
| `cotizacion_id` nullable rompe la unicidad "una Cotización → una OF" | Índice único **parcial** `WHERE cotizacion_id IS NOT NULL`; conserva la regla para el flujo anuncios sin bloquear OF genéricas. |
| Consumo no atómico (movimientos parciales) | Consumo dentro de la misma transacción del cambio de estado; `Material.aplicarMovimiento` valida antes de mutar; Property 3 verifica todo-o-nada y no-negatividad. |
| Consumir en una transición que luego sería inválida | Se comprueba `puedeTransicionarA(destino)` antes de invocar el consumo; solo se consume en `pendiente → en_produccion` válido. |
| Proyecto genérico dependiendo de los 3 puertos de anuncios | `PerfilFasesGiro` + `AvanceProduccionAdapter` (solo producción) separado de `AvanceSitioAnunciosAdapter`; el servicio elige el adaptador por perfil. |
| Scheduler multi-tenant sin contexto (RLS oculta filas) | Patrón probado `TenantContext.set` + `TenantSessionInitializer.applyTenant(tenant)` por tenant dentro de transacción, con `clear()` en `finally`; fallos por tenant aislados. |
| Notificación real rompe pruebas por integraciones externas | `@ConditionalOnProperty` en el planificador (desactivado por defecto); el notificador delega en `NotificacionPort` (ya con reintentos/omisión); canal CORREO no marketing evita guarda Opt-In. |
| Cambiar el mensaje de la guarda de cierre altera contratos | Solo cambia el **texto** del 422 (más informativo); el código de estado y el punto de guarda no cambian; cubierto por Property 5 y tests de OTI. |

## Consideraciones de no-regresión (Req 16)

- **Flujo completo de anuncios (Req 16.1):** se conserva `generar(cotizacionId)` con sus 3 precondiciones y contrato; la derivación con `PerfilFasesGiro.ANUNCIOS` es equivalente a la clásica (Property 2); levantamientos, permisos, OTIs y su encadenamiento no cambian su comportamiento (solo se enriquecen DTOs y se agregan endpoints de lectura + el disparo/notificador de vencimientos).
- **Contratos de endpoints existentes (Req 16.2):** `POST /ordenes-fabricacion` (cotizacionId), `GET/PUT` de OF, `POST /proyectos`, y los endpoints de levantamiento/permiso/OTI conservan rutas, verbos y campos de request. Solo se **agregan** endpoints (`/ordenes-fabricacion/directa`, `/partidas`, GET de fotos/pendientes/evidencias, `/permisos-instalacion/notificar-vencimientos`) y se **enriquecen** DTOs de salida (campos nuevos, no removidos).
- **Auditoría JSONB (Req 16.3):** `ServicioAuditoria`/`aJson` permanecen como punto único; las nuevas acciones (creación directa, partidas, consumo, notificación) auditan por el mismo camino.
- **Dependencias de módulos (Req 16.4):** `inventario-avanzado → operacion` se preserva; el `ClienteExistentePort` es una dependencia de solo lectura hacia comercial por puerto (no introduce dependencia incorrecta ni acceso a persistencia ajena).
- **Guardas de acceso (Req 16.5, 16.6):** deny-by-default por módulo y permiso en todos los endpoints; el gating por giro se **mantiene** para Levantamientos, Permisos e Instalación y se **relaja solo** para OF y Proyectos (Req 16.6).
