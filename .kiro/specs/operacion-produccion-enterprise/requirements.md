# Documento de Requerimientos — Operación y Producción (Enterprise)

## Introducción

Este spec **afina y generaliza** el módulo `operacion` (Operación y producción) del CRM enterprise multi-tenant **Dess-TI / plataforma-multigiro** (Spring Boot + Angular 20 + PostgreSQL, arquitectura hexagonal). Parte de un diagnóstico verificado del código actual (no supuesto) y de decisiones del usuario ya confirmadas.

El módulo `operacion` hoy vive completo dentro del vertical de anuncios (`com.dessti.crm.vertical.anuncios.{ordenfabricacion,levantamiento,permiso,instalacion,proyecto}`) y todos sus endpoints se cierran con el **doble gating** `moduloHabilitado('operacion') AND giroCorresponde('operacion')` más el permiso atómico. Como `AnunciosVertical.modulos()` declara `operacion` como módulo del giro `anuncios-luminosos`, y `RegistroVerticales` exige que cada módulo pertenezca a un único giro (fail-fast), `giroCorresponde('operacion')` **ata todo el módulo al giro anuncios**. Materiales e Inventario avanzado, en cambio, viven en el Núcleo (`com.dessti.crm.operacion.inventario[.avanzado]`) y por eso no se filtran por giro.

El objetivo es dejar el módulo **"super afinado" (enterprise)**:
- **Generalizar** Órdenes de Fabricación (producción) y Proyectos multi-sitio para que los use **cualquier giro**, conservando **intacto** el flujo de anuncios.
- Mantener **Levantamientos, Permisos e Instalación (OTIs)** como **extensión específica del giro anuncios** (su gating por giro se conserva).
- **Cablear el consumo de materiales** en la Orden de Fabricación (integración con el Inventario ya terminado).
- Cerrar **huecos de exposición de datos** ya persistidos (fotos de levantamiento; pendientes/evidencias de OTI).
- Arreglar **UX del frontend** (vistas de detalle, eliminación de UUIDs, filtros, `agregarFotos`).
- **Disparar** la notificación de vencimiento de permisos con notificación real.
- Elevar **calidad y pruebas** (backend y frontend) sin regresión.

### Reglas duras del proyecto (transversales a todo requisito)

- **Multi-tenant con RLS**: toda tabla es tenant-scoped; el `tenant_id` se deriva del `TenantContext`, nunca de la petición.
- **RBAC deny-by-default** por permiso atómico `recurso:operacion` y por gating de módulo/giro.
- **UI en es-MX**, responsiva, **WCAG AA**, **solo design tokens** (sin colores/espaciados hardcodeados).
- **Nunca exponer UUIDs al usuario** (ni teclearlos ni mostrarlos crudos, ni `id.slice(0,8)`).
- **De raíz, sin parches, sin suponer.** Requisitos claros, verificables, con casos de error.

### Alcance (in/out)

- **Dentro de alcance:** los seis bloques A–F descritos abajo, para el módulo `operacion`.
- **Fuera de alcance:** el módulo `inventario-avanzado` (su propio módulo y gating); Prueba_Diseño (salvo como precondición ya existente del flujo anuncios); Mantenimiento; y cualquier cambio a Comercial/Facturación más allá de puertos de solo-lectura mínimos que requiera la generalización.

## Decisiones acordadas (confirmadas por el usuario)

Estas decisiones ya fueron confirmadas y son vinculantes para el diseño; sustituyen a las preguntas abiertas originales:

1. **Enfoque de generalización (arquitectura):** mover **Órdenes de Fabricación** y **Proyectos/Sitios** al **Núcleo** del módulo operacion (como ya lo están Materiales e Inventario), sin amarre a giro. **Levantamientos, Permisos e Instalación (OTIs) permanecen en el vertical de anuncios** con su gating por giro. Es la separación más limpia y coherente con Materiales.
2. **Transición de consumo de materiales:** el consumo se aplica al pasar la OF de pendiente → en_produccion (reserva del material al arrancar la fabricación). Si falla por existencias insuficientes, se rechaza toda la transición (atómico, 422).
3. **Origen genérico de la OF:** creación directa asociada a un **Cliente** + sus partidas (mínimo viable). El flujo de anuncios (génesis desde Cotización con sus 3 precondiciones) se conserva sin cambios.
4. **Partidas/BOM:** se capturan al crear la OF y son **editables solo mientras la OF esté en estado pendiente**.
5. **Verificación de Cliente:** se introduce un **ClienteExistentePort** de solo-lectura (hoy no existe) para validar la existencia del Cliente y responder 404 con auditoría.
6. **Derivación genérica del estado de Proyecto:** para giros sin las fases de anuncios, el avance se deriva **solo de producción** (OF terminada por Sitio). En el giro anuncios se conserva la secuencia completa (Levantamiento → Permiso → Producción → Instalación).
7. **Notificación de vencimiento de permisos:** **endpoint manual + planificador diario**, con **notificación real** integrada al sistema de notificaciones (campana), no solo log.
8. **Migración de datos:** al mover OF/Proyectos al Núcleo, **sembrar vía Flyway los permisos orden_fabricacion:* y proyecto:* para los roles de otros giros**, sin alterar los datos existentes del giro anuncios.

## Glosario

- **THE Sistema**: la plataforma CRM Dess-TI (backend Spring Boot + frontend Angular) en su conjunto, salvo que se nombre un componente más específico.
- **Módulo `operacion`**: módulo funcional que agrupa Órdenes de Fabricación, Levantamientos, Permisos, Instalación (OTIs) y Proyectos/Sitios.
- **Giro**: vertical de negocio del tenant (p. ej. `anuncios-luminosos`). Un módulo de vertical pertenece a un único giro; un módulo de Núcleo es transversal.
- **Giro `anuncios-luminosos`**: giro cuyo vertical (`AnunciosVertical`) aporta hoy el módulo `operacion`.
- **Genérico (cualquier giro)**: comportamiento que debe funcionar para tenants de cualquier giro, sin exigir pertenencia al giro anuncios.
- **Específico de anuncios**: comportamiento que solo se activa/visualiza cuando el giro del tenant es `anuncios-luminosos`.
- **Orden de Fabricación (OF)**: `orden_fabricacion` (tabla V17). Documento de producción. Estados: `pendiente` → {`en_produccion` | `cancelada`}, `en_produccion` → {`terminada` | `cancelada`}.
- **Partida/BOM de OF**: línea de consumo de material (Material + cantidad) asociada a una OF (tabla nueva de este spec).
- **Génesis desde Cotización**: creación de una OF a partir de una `cotizacion` aprobada con tres precondiciones (aprobada, sin OF previa, con Prueba_Diseño aprobada). Es el flujo **específico de anuncios**.
- **Levantamiento_Sitio**: `levantamiento_sitio` (V19) con hija `levantamiento_foto`. Estados: `en_proceso` → `completado`. **Específico de anuncios.**
- **Permiso_Instalacion**: `permiso_instalacion` (V20). Estados: `solicitado` → {`aprobado` | `rechazado`}. **Específico de anuncios.**
- **Orden_Trabajo_Instalacion (OTI)**: `orden_trabajo_instalacion` (V24) con hijas `pendiente_instalacion` y `evidencia_instalacion`. Estados: `programada` → {`en_curso` | `cancelada`}, `en_curso` → {`completada` | `cancelada`}. **Específico de anuncios.**
- **Pendiente de instalación**: fila de `pendiente_instalacion` con `resuelto` boolean; un pendiente "sin resolver" tiene `resuelto = false`.
- **Proyecto / Sitio**: `proyecto` y `sitio` (V25). Un Proyecto agrupa Sitios de un Cliente. **Genérico (cualquier giro).**
- **Estado consolidado de Proyecto**: `EstadoConsolidadoProyecto` derivado por `DerivacionEstadoProyecto` (función pura) a partir del avance de sus Sitios: `SIN_SITIOS` → `EN_LEVANTAMIENTO` → `EN_TRAMITE_PERMISOS` → `EN_PRODUCCION` → `EN_INSTALACION` → `COMPLETADO`.
- **AvanceFasesSitio**: agregado de solo-lectura por Sitio con las banderas `tieneLevantamientoCompletado`, `tienePermisoAprobado`, `tieneOrdenFabricacionTerminada`, `tieneInstalacionCompletada` (compuesto vía `AvanceSitioPort`).
- **ConsumoMaterialPort**: puerto de entrada del Inventario (`com.dessti.crm.operacion.inventario.application`) con `consumirParaOrdenFabricacion(ordenFabricacionId, List<ConsumoMaterial>)`. Ya implementado y probado; **nunca se invoca desde producción** (hueco).
- **ConsumoMaterial**: valor `(materialId, cantidad)`; `cantidad` positiva.
- **Movimiento_Inventario `salida`**: movimiento que descuenta existencias por cada material consumido.
- **NotificadorPermisoPort / notificarVencimientosProximos()**: mecanismo que localiza permisos aprobados que vencen en ≤ 30 días y emite una notificación por cada uno. Existe pero **sin disparador** y el notificador solo escribe a log.
- **NombresInventarioService / patrón entity-select**: patrón frontend ya usado por Inventario avanzado para mostrar nombres en vez de UUIDs y ofrecer selección por nombre (autocomplete/picker).
- **Actor**: identificador del usuario autenticado (o `sistema`) derivado del contexto, usado en auditoría.
- **Auditoría**: registro vía `AuditoriaPort` con actor, acción, recurso, detalle y valores anterior/nuevo cuando aplica.
- **404 / 409 / 422**: respuestas HTTP de recurso no encontrado / conflicto (unicidad o transición inválida) / regla de negocio, mapeadas por `ManejadorGlobalErrores`.

---

## Requerimientos

> **Nota de clasificación.** Cada requisito indica su alcance: **[Genérico]** (cualquier giro), **[Específico anuncios]** (solo giro `anuncios-luminosos`) o **[Transversal]** (calidad/no-regresión). El patrón EARS y GIVEN-WHEN-THEN se usan de forma combinada; los identificadores del código y del esquema son los reales verificados en el diagnóstico.

---

## BLOQUE A — Generalización del núcleo (cualquier giro)

### Requerimiento 1: Creación de Órdenes de Fabricación genéricas — [Genérico]

**Historia de usuario:** Como responsable de producción de un tenant de cualquier giro, quiero crear Órdenes de Fabricación sin depender del flujo de cotización de anuncios, para poder gestionar producción en mi giro.

#### Criterios de aceptación

1. WHERE el tenant es de cualquier giro, THE Sistema SHALL permitir crear una Orden de Fabricación a partir de un origen genérico válido, sin exigir que exista una Cotización de anuncios.
2. WHEN se crea una Orden de Fabricación por el origen genérico, THE Sistema SHALL crearla en estado `pendiente`, asociarla al Cliente indicado y registrar la creación en la auditoría con actor, recurso `orden_fabricacion` y valores de estado.
3. WHERE el tenant es del giro `anuncios-luminosos`, THE Sistema SHALL conservar la génesis desde Cotización con sus tres precondiciones en orden: Cotización existente en el tenant (404 con auditoría de acceso cruzado si no), Cotización `aprobada` (422 si no), sin Orden de Fabricación previa (409 si ya existe) y con Prueba_Diseño `aprobada` (422 si no).
4. IF falta el Cliente al crear una Orden de Fabricación por el origen genérico, THEN THE Sistema SHALL rechazar la creación con 422 y no persistir ninguna Orden de Fabricación.
5. IF el Cliente indicado no existe o no es accesible en el tenant, THEN THE Sistema SHALL responder 404 y registrar el intento de acceso cruzado en la auditoría.
6. THE Sistema SHALL preservar sin cambios el contrato del endpoint de génesis desde Cotización existente (`POST /ordenes-fabricacion` con `cotizacionId`) para el giro anuncios.
7. GIVEN una Orden de Fabricación creada por cualquier origen, WHEN se consulta, cambia de estado o se lista, THEN THE Sistema SHALL comportarse de forma idéntica con independencia del origen de creación.

### Requerimiento 2: Gating por giro relajado en Órdenes de Fabricación — [Genérico]

**Historia de usuario:** Como administrador de la plataforma, quiero que las Órdenes de Fabricación sean accesibles a cualquier giro con el módulo `operacion` habilitado y el permiso adecuado, para no bloquear a giros no-anuncios.

#### Criterios de aceptación

1. WHEN un usuario de cualquier giro con el módulo `operacion` habilitado y el permiso `orden_fabricacion:{crear|leer|listar|cambiar_estado}` invoca el endpoint correspondiente, THE Sistema SHALL autorizar la operación sin exigir que su giro sea `anuncios-luminosos`.
2. THE Sistema SHALL conservar el gating por módulo `operacion` y por permiso atómico en todos los endpoints de Orden de Fabricación (deny-by-default).
3. IF un usuario carece del módulo `operacion` habilitado o del permiso atómico requerido, THEN THE Sistema SHALL responder 403 y registrar la denegación conforme al comportamiento actual de auditoría.
4. THE Sistema SHALL mantener el aislamiento intra-tenant de las Órdenes de Fabricación (RLS y `TenantContext`) al relajar el gating por giro.
5. WHERE el frontend expone la vista de Órdenes de Fabricación, THE Sistema SHALL mostrarla a usuarios de cualquier giro con el permiso `orden_fabricacion:listar` y el módulo `operacion`, sin exigir el guarda de giro anuncios.

### Requerimiento 3: Proyectos multi-sitio genéricos con derivación por fases aplicables — [Genérico]

**Historia de usuario:** Como gestor de proyectos de cualquier giro, quiero crear y consultar proyectos multi-sitio cuyo estado se derive solo de las fases que apliquen a mi giro, para no verme forzado a fases específicas de anuncios (levantamiento/permiso/instalación).

#### Criterios de aceptación

1. WHEN un usuario de cualquier giro con el módulo `operacion` y el permiso `proyecto:{crear|leer|listar|actualizar}` invoca el endpoint correspondiente, THE Sistema SHALL autorizar la operación sin exigir que su giro sea `anuncios-luminosos`, conservando el gating por módulo y permiso.
2. WHERE el giro del tenant no habilita las fases de levantamiento, permiso o instalación, THE Sistema SHALL derivar el estado consolidado del Proyecto considerando únicamente las fases aplicables a ese giro.
3. WHEN todas las fases aplicables de todos los Sitios de un Proyecto están completas, THE Sistema SHALL derivar el estado consolidado `COMPLETADO`.
4. WHEN un Proyecto no tiene Sitios, THE Sistema SHALL derivar el estado consolidado `SIN_SITIOS` con independencia del giro.
5. WHERE el giro del tenant es `anuncios-luminosos`, THE Sistema SHALL conservar la derivación actual con la secuencia Levantamiento → Permiso → Producción → Instalación.
6. THE Sistema SHALL mantener la derivación del estado consolidado como una función pura y determinista del avance de los Sitios.

### Requerimiento 4: Verificación de existencia del Cliente en Proyectos — [Genérico]

**Historia de usuario:** Como gestor de proyectos, quiero que crear un Proyecto con un Cliente inexistente devuelva un error claro de no encontrado, para no recibir un error de integridad opaco.

#### Criterios de aceptación

1. WHEN se crea un Proyecto con un `clienteId`, THE Sistema SHALL verificar la existencia del Cliente en el tenant mediante un puerto de solo-lectura antes de persistir.
2. IF el Cliente no existe o no es accesible en el tenant, THEN THE Sistema SHALL responder 404 y registrar el intento de acceso cruzado en la auditoría con recurso `cliente`.
3. IF el `clienteId` es nulo, THEN THE Sistema SHALL responder 422 sin persistir el Proyecto.
4. WHEN el Cliente existe y es accesible, THE Sistema SHALL crear el Proyecto en estado `SIN_SITIOS` y registrar la creación en la auditoría.
5. THE Sistema SHALL preservar el contrato del endpoint `POST /proyectos` (cuerpo con `clienteId` y `nombre`).

---

## BLOQUE B — Integración de consumo de materiales en la OF (cualquier giro)

### Requerimiento 5: Partidas/BOM de la Orden de Fabricación — [Genérico]

**Historia de usuario:** Como responsable de producción, quiero registrar qué materiales y cuánto consume una Orden de Fabricación, para que la fabricación descuente el inventario correcto.

#### Criterios de aceptación

1. THE Sistema SHALL permitir asociar a una Orden de Fabricación una o más partidas de consumo, cada una con un Material y una cantidad positiva.
2. IF una partida indica una cantidad menor o igual a cero, THEN THE Sistema SHALL rechazar la operación con 422 sin persistir la partida.
3. IF una partida referencia un Material que no existe o no es accesible en el tenant, THEN THE Sistema SHALL responder 404 y registrar el intento de acceso cruzado.
4. THE Sistema SHALL persistir las partidas de la Orden de Fabricación como datos tenant-scoped con RLS y concurrencia optimista, siguiendo el patrón de tablas del módulo.
5. WHEN se consulta una Orden de Fabricación que tiene partidas, THE Sistema SHALL exponer las partidas (Material y cantidad) en su representación de salida sin exponer identificadores crudos al usuario final en el frontend.

### Requerimiento 6: Consumo atómico de materiales al fabricar — [Genérico]

**Historia de usuario:** Como responsable de producción, quiero que al avanzar la fabricación se descuente el inventario de forma atómica, para mantener existencias correctas y evitar consumos parciales.

#### Criterios de aceptación

1. WHEN una Orden de Fabricación con partidas realiza la transición de consumo definida en el diseño, THE Sistema SHALL invocar `ConsumoMaterialPort.consumirParaOrdenFabricacion` con las partidas de la Orden.
2. WHEN el consumo se aplica correctamente, THE Sistema SHALL registrar un Movimiento_Inventario de tipo `salida` por cada Material y descontar la cantidad correspondiente de las existencias.
3. IF algún consumo dejaría las existencias por debajo de cero, THEN THE Sistema SHALL rechazar la transición con 422 y no persistir ningún consumo ni el cambio de estado de la Orden de Fabricación (atomicidad).
4. WHEN el consumo y la transición se completan, THE Sistema SHALL registrar la acción en la auditoría con actor, recurso, la Orden de Fabricación y el detalle del consumo.
5. THE Sistema SHALL ejecutar el consumo y la transición dentro de una única transacción para garantizar que ambos efectos ocurran juntos o no ocurran.
6. WHERE una Orden de Fabricación no tiene partidas, THE Sistema SHALL permitir la transición sin generar movimientos de inventario.
7. THE Sistema SHALL aplicar el consumo respetando el aislamiento multi-tenant (materiales y movimientos del mismo tenant).

---

## BLOQUE C — Exponer datos ya persistidos

### Requerimiento 7: Exposición de fotos del Levantamiento — [Específico anuncios]

**Historia de usuario:** Como técnico de instalación del giro anuncios, quiero ver las fotos de un levantamiento, para revisar las condiciones del sitio registradas.

#### Criterios de aceptación

1. WHEN se consulta un Levantamiento_Sitio, THE Sistema SHALL exponer las fotografías asociadas (sus referencias) en la representación de salida o mediante un endpoint de lectura dedicado.
2. WHEN se solicitan las fotografías de un Levantamiento_Sitio accesible, THE Sistema SHALL devolver la lista de referencias vinculadas, incluyendo una lista vacía cuando no existan fotos.
3. IF el Levantamiento_Sitio no existe o no es accesible en el tenant, THEN THE Sistema SHALL responder 404 y registrar el intento de acceso cruzado.
4. THE Sistema SHALL exigir el permiso `levantamiento_sitio:leer` y el gating por módulo `operacion` y giro anuncios para exponer las fotografías.
5. THE Sistema SHALL conservar el comportamiento existente de alta de fotografías (`POST /levantamientos/{id}/fotos`) y de auditoría.

### Requerimiento 8: Exposición y resolución de pendientes y evidencias de OTI — [Específico anuncios]

**Historia de usuario:** Como supervisor de instalación del giro anuncios, quiero ver y resolver los pendientes y ver las evidencias de una OTI, para poder cerrar la orden con la información completa.

#### Criterios de aceptación

1. WHEN se consulta una Orden_Trabajo_Instalacion, THE Sistema SHALL exponer sus pendientes (con su descripción y estado `resuelto`) y sus evidencias (referencias) en la representación de salida o mediante endpoints de lectura dedicados.
2. WHEN se solicitan los pendientes de una OTI accesible, THE Sistema SHALL devolver cada pendiente indicando si está resuelto, incluyendo una lista vacía cuando no existan.
3. WHEN se solicitan las evidencias de una OTI accesible, THE Sistema SHALL devolver sus referencias, incluyendo una lista vacía cuando no existan.
4. WHEN el usuario resuelve un pendiente existente de una OTI accesible, THE Sistema SHALL marcar ese pendiente como `resuelto` y registrar la acción en la auditoría.
5. IF se intenta completar una OTI que tiene pendientes sin resolver, THEN THE Sistema SHALL rechazar la transición con 422 e informar qué pendientes faltan por resolver.
6. IF la OTI, un pendiente o una evidencia solicitados no existen o no son accesibles en el tenant, THEN THE Sistema SHALL responder 404 y registrar el intento de acceso cruzado.
7. THE Sistema SHALL exigir `orden_trabajo_instalacion:leer` para la consulta y `orden_trabajo_instalacion:cambiar_estado` para resolver pendientes, conservando el gating por módulo `operacion` y giro anuncios.

---

## BLOQUE D — Frontend enterprise (UX)

### Requerimiento 9: Vistas de detalle de OF, Levantamiento, Permiso y OTI — [Genérico OF; Específico anuncios el resto]

**Historia de usuario:** Como operador, quiero vistas de detalle para cada documento operativo, para ver toda su información y ejecutar acciones contextuales sin salir de la vista.

#### Criterios de aceptación

1. THE Sistema SHALL ofrecer una vista de detalle de Orden de Fabricación con su información, sus partidas y las acciones de cambio de estado contextualizadas al estado actual. **[Genérico]**
2. THE Sistema SHALL ofrecer una vista de detalle de Levantamiento_Sitio con su información y una galería de sus fotos. **[Específico anuncios]**
3. THE Sistema SHALL ofrecer una vista de detalle de Permiso_Instalacion con su información y las acciones aprobar/rechazar contextualizadas al estado. **[Específico anuncios]**
4. THE Sistema SHALL ofrecer una vista de detalle de OTI con su información, la lista de pendientes (con acción resolver), la galería de evidencias y las acciones de estado contextualizadas. **[Específico anuncios]**
5. WHEN una acción de estado no es válida para el estado actual de un documento, THE Sistema SHALL no ofrecer esa acción en la vista de detalle.
6. WHEN una lista de una vista de detalle no tiene elementos, THE Sistema SHALL mostrar un estado vacío en es-MX.
7. IF una operación de la vista de detalle falla, THEN THE Sistema SHALL mostrar un mensaje de error en es-MX describiendo el problema.
8. THE Sistema SHALL construir las vistas de detalle de forma responsiva, accesible (WCAG AA) y usando únicamente design tokens.

### Requerimiento 10: Eliminación de UUIDs en el frontend — [Genérico]

**Historia de usuario:** Como operador, quiero seleccionar entidades por nombre y ver nombres en las tablas, para no teclear ni leer UUIDs.

#### Criterios de aceptación

1. WHERE una vista requiere elegir una Cotización, Orden de Fabricación, Sitio, Cuadrilla, Cliente o Material, THE Sistema SHALL ofrecer un selector por nombre (autocomplete/picker) siguiendo el patrón de `NombresInventarioService`/entity-select, sin exigir teclear un UUID.
2. THE Sistema SHALL mostrar en las tablas y detalles el nombre legible de cada entidad en lugar de su identificador o de un recorte del identificador.
3. WHEN el usuario selecciona una entidad por nombre, THE Sistema SHALL usar su identificador internamente sin mostrarlo al usuario.
4. THE Sistema SHALL mostrar los selectores por nombre de forma accesible (WCAG AA), responsiva y con design tokens, con textos en es-MX.
5. IF una búsqueda por nombre no arroja coincidencias, THEN THE Sistema SHALL mostrar un estado sin resultados en es-MX.

### Requerimiento 11: Filtros soportados por el backend en el frontend — [Genérico OF; Específico anuncios OTI]

**Historia de usuario:** Como operador, quiero filtrar los listados por los criterios que el backend ya soporta, para encontrar documentos rápidamente.

#### Criterios de aceptación

1. THE Sistema SHALL exponer en el listado de Órdenes de Fabricación un filtro por Cliente (por nombre) además del filtro por estado. **[Genérico]**
2. THE Sistema SHALL exponer en el listado de OTIs filtros por Cliente y por Cuadrilla (por nombre) además del filtro por estado. **[Específico anuncios]**
3. WHEN el usuario aplica un filtro, THE Sistema SHALL enviar al backend el identificador correspondiente derivado de la selección por nombre.
4. WHEN un filtro no arroja coincidencias, THE Sistema SHALL mostrar un estado vacío en es-MX.
5. THE Sistema SHALL presentar los controles de filtro de forma accesible (WCAG AA), responsiva y con design tokens.

### Requerimiento 12: Implementación de `agregarFotos` en el frontend de Levantamientos — [Específico anuncios]

**Historia de usuario:** Como técnico del giro anuncios, quiero adjuntar fotos a un levantamiento desde la interfaz, para documentar el sitio.

#### Criterios de aceptación

1. WHEN el usuario adjunta una o más fotos a un Levantamiento_Sitio, THE Sistema SHALL enviar las referencias al endpoint `POST /levantamientos/{id}/fotos` y reflejar las fotos adjuntas en la vista.
2. IF no se proporciona ninguna referencia, THEN THE Sistema SHALL evitar la llamada y mostrar un mensaje de validación en es-MX.
3. IF la operación de adjuntar falla, THEN THE Sistema SHALL mostrar un mensaje de error en es-MX y conservar el estado previo de la vista.
4. THE Sistema SHALL presentar la carga de fotos de forma accesible (WCAG AA), responsiva y con design tokens.

---

## BLOQUE E — Notificación de vencimiento de permisos (anuncios)

### Requerimiento 13: Disparo y notificación real de vencimientos de permisos — [Específico anuncios]

**Historia de usuario:** Como coordinador de permisos del giro anuncios, quiero recibir una notificación real cuando un permiso aprobado esté por vencer, para renovarlo a tiempo.

#### Criterios de aceptación

1. THE Sistema SHALL disponer de un mecanismo de disparo (planificador o endpoint) que ejecute la localización de permisos `aprobado` que vencen en los próximos 30 días.
2. WHEN el mecanismo de disparo se ejecuta, THE Sistema SHALL emitir una notificación real por cada permiso por vencer, integrada con el sistema de notificaciones (no solo un registro en log).
3. WHEN se emite una notificación de vencimiento, THE Sistema SHALL incluir el Sitio, el tipo de permiso, la fecha de vencimiento y los días restantes.
4. WHERE el disparo es un endpoint, THE Sistema SHALL exigir el permiso atómico adecuado y el gating por módulo `operacion` y giro anuncios.
5. WHEN no existen permisos por vencer en la ventana, THE Sistema SHALL completar la ejecución sin emitir notificaciones.
6. THE Sistema SHALL ejecutar la localización de vencimientos respetando el aislamiento multi-tenant (permisos del tenant en contexto).

---

## BLOQUE F — Calidad y pruebas / no-regresión

### Requerimiento 14: Cobertura de pruebas de backend — [Transversal]

**Historia de usuario:** Como responsable de calidad, quiero pruebas de backend que cubran los flujos afinados, para prevenir regresiones y validar la generalización.

#### Criterios de aceptación

1. THE Sistema SHALL incluir pruebas de controlador y de servicio para la Orden_Trabajo_Instalacion que cubran consulta con pendientes/evidencias, resolución de pendientes y la guarda de cierre.
2. THE Sistema SHALL incluir pruebas unitarias y de propiedades para la derivación del estado consolidado de Proyecto, incluyendo la generalización por fases aplicables.
3. WHERE aplique, THE Sistema SHALL incluir una prueba de propiedades del consumo de materiales que valide atomicidad (todo o nada) y no-negatividad de existencias.
4. THE Sistema SHALL incluir pruebas que verifiquen que la Orden de Fabricación genérica es accesible por giros no-anuncios y que el flujo de anuncios conserva sus precondiciones.
5. THE Sistema SHALL incluir pruebas que verifiquen la verificación de existencia del Cliente en Proyectos (404 con auditoría).

### Requerimiento 15: Cobertura de pruebas de frontend — [Transversal]

**Historia de usuario:** Como responsable de calidad de frontend, quiero pruebas de las vistas operativas, para asegurar su funcionamiento y accesibilidad.

#### Criterios de aceptación

1. THE Sistema SHALL incluir `.spec.ts` para las cinco vistas del módulo (Órdenes de Fabricación, Levantamientos, Permisos, Instalación y Proyectos).
2. THE Sistema SHALL ejecutar las verificaciones de accesibilidad de esas pruebas en un solo intento (one-shot) para evitar inestabilidad.
3. THE Sistema SHALL verificar en las pruebas que las vistas no muestran UUIDs crudos ni recortes de identificador.

### Requerimiento 16: No regresión de comportamientos existentes — [Transversal]

**Historia de usuario:** Como equipo de plataforma, quiero garantizar que los cambios no rompen lo que ya funciona, para mantener la estabilidad del sistema.

#### Criterios de aceptación

1. THE Sistema SHALL preservar el flujo completo de anuncios (génesis de OF desde Cotización, levantamientos, permisos, OTIs y su encadenamiento).
2. THE Sistema SHALL preservar los contratos de los endpoints existentes del módulo `operacion` (rutas, verbos y campos de request ya publicados).
3. THE Sistema SHALL preservar el arreglo de auditoría JSONB ya aplicado en `ServicioAuditoria` como punto único de auditoría.
4. THE Sistema SHALL preservar la declaración de dependencias de módulos existente (`inventario-avanzado` → `operacion`) sin introducir dependencias incorrectas.
5. THE Sistema SHALL preservar las guardas de acceso (deny-by-default por módulo, giro cuando aplique y permiso atómico) para todos los endpoints del módulo.
6. WHILE se generaliza el gating por giro de Órdenes de Fabricación y Proyectos, THE Sistema SHALL mantener el gating por giro de Levantamientos, Permisos e Instalación (OTIs) sin cambios.

---

## Preguntas abiertas

Todas las preguntas abiertas originales fueron resueltas y quedaron registradas en la seccion **"Decisiones acordadas (confirmadas por el usuario)"** al inicio del documento. No quedan preguntas pendientes para iniciar el diseño.