# Requirements Document

_(Documento de requisitos — Plan y Suscripcion de la Empresa visibles y editables por el super_admin)_

## Introduction

Cada Empresa (tenant) tiene un Plan (limites, moneda, modulos habilitados y total) que se le entrega a
traves de una Suscripcion (asociacion Empresa↔Plan con estado y vigencia). Hoy el Plan solo se elige al
CREAR la Empresa (el alta exige `planId`), pero en el ambito del super_admin NO se VE el plan vigente de
una Empresa existente ni el estado de su suscripcion, y NO se pueden ADMINISTRAR (cambiar de plan,
activar/suspender/cancelar, ni ajustar la vigencia) desde la ficha de la Empresa. El backend YA expone el
contrato REST completo de Planes y Suscripciones (`PlanController` y `SuscripcionController`), y el
frontend YA tiene el `PlanesService` (planes + parte de suscripciones) y la pantalla de Planes; lo que
falta es la INTEGRACION en la vista de Empresas.

Esta especificacion cierra ese hueco con una feature de FRONTEND apoyada en un ENRIQUECIMIENTO puntual del
BACKEND (decision enterprise adoptada): en lugar de derivar el plan vigente en el frontend con N peticiones
(una por Empresa), el backend AGREGA el plan vigente al `EmpresaDto`, de modo que el listado y la consulta de
Empresas ya traen ese dato como fuente de verdad. El alcance funcional es:
(1) MOSTRAR una columna "Plan" en el listado de Empresas del super_admin, por su NOMBRE (nunca el UUID),
alimentada por el dato enriquecido que expone el backend en el `EmpresaDto`;
(2) ENRIQUECER en el backend el `EmpresaDto` (listado paginado GET `/empresas` y consulta GET
`/empresas/{id}`) con el plan vigente derivado (al menos nombre del plan y estado de la suscripcion vigente),
calculando la "suscripcion vigente" en el backend con la misma regla del Requirement 3 y evitando el problema
N+1;
(3) MOSTRAR, dentro de la ficha de cada Empresa, un panel "Plan y Suscripcion" con el plan vigente (nombre,
moneda, maximo de usuarios, modulos habilitados y total) y la suscripcion actual (estado, vigencia legible,
moneda de facturacion, modulos habilitados); y (4) permitir ACCIONES conectadas al backend existente:
asignar/cambiar plan (crear suscripcion con ese plan), activar/suspender/cancelar la suscripcion y
actualizar la vigencia, con todas las validaciones y errores del backend (404, 409, 422, 403) traducidos a
mensajes claros en espanol. Se preservan las reglas de la plataforma: solo el super_admin (permisos
`plan:*` y `suscripcion:*`), espanol (es-MX), WCAG AA, design tokens, multi-tenant, RBAC por permiso, sin
UUIDs visibles, todo conectado y sin codigo muerto. El backend SI se modifica en esta feature, pero de forma
ADITIVA: el enriquecimiento del `EmpresaDto` solo AGREGA campos (no rompe el contrato existente), respeta el
aislamiento del super_admin (el plan/suscripcion son datos de PLATAFORMA/monetizacion, no de negocio del
tenant) y mantiene en verde la suite de pruebas existente, agregando pruebas para el enriquecimiento.

## Glossary

- **Empresa (tenant):** organizacion cliente de la plataforma; se identifica ante el usuario por su nombre,
  nunca por su UUID (`tenantId`).
- **Plan:** definicion de limites (maxUsuarios), moneda, precios por modulo, modulos habilitados y `total`
  calculado por el backend. Se identifica al usuario por su `nombre`.
- **Suscripcion:** asociacion Empresa↔Plan con `estado` (activa | suspendida | cancelada), `vigenciaInicio`,
  `vigenciaFin` (opcional), `modulosHabilitados` y `monedaFacturacion`.
- **Suscripcion vigente (actual):** la suscripcion que se muestra como "actual" de la Empresa. Regla de la
  feature: la suscripcion en estado `activa`; si no hay ninguna `activa`, la mas reciente por
  `vigenciaInicio` (desempate por `createdAt` mas reciente).
- **Estado final:** estado `cancelada` de una suscripcion; es irreversible (no puede volver a `activa`).
- **Panel "Plan y Suscripcion":** seccion dentro de la ficha de la Empresa que muestra plan vigente +
  suscripcion actual y concentra las acciones.
- **Patron NO-UUID:** los nombres legibles los resuelve preferentemente el backend (el usuario nunca ve el
  UUID); adicionalmente, el frontend puede resolver identificadores a nombres para los selectores mediante
  mapas cargados una vez (como `nombreGiro` con el catalogo de giros). En esta feature, el NOMBRE del plan
  vigente del listado lo provee el backend enriquecido; el mapa `planId` → nombre en el frontend queda para
  los SELECTORES de plan.
- **super_admin:** rol de plataforma (Dess-TI) que administra Empresas, Planes y Suscripciones.
- **EmpresaDto:** DTO de backend (`platform.empresas.EmpresaDto`) que proyecta datos de PLATAFORMA de una
  Empresa. Su fabrica `EmpresaDto.de(Empresa)` expone unicamente datos de plataforma y no referencia dato
  alguno de NEGOCIO del tenant (aislamiento del super_admin, Req 24.3 de la plataforma). El plan/suscripcion
  son datos de PLATAFORMA (monetizacion), por lo que enriquecer el DTO con el plan vigente es coherente con
  ese aislamiento.
- **Plan vigente (dato enriquecido):** informacion derivada que el backend agrega al `EmpresaDto` para
  representar el plan y estado de la suscripcion vigente de la Empresa (al menos nombre del plan y estado de
  la suscripcion). Es la FUENTE DE VERDAD que consume el frontend para la columna "Plan".
- **Problema N+1:** patron de acceso a datos ineficiente en el que, al listar N Empresas, se ejecutan N
  consultas adicionales (una por Empresa) para resolver su suscripcion/plan. El backend debe evitarlo
  resolviendo el dato por lote.
- **Sistema:** en criterios de FRONTEND, el frontend Angular del ambito plataforma (vista de Empresas y panel
  de Plan/Suscripcion). En criterios de BACKEND se nombra explicitamente el componente responsable (por
  ejemplo el `ServicioEmpresas` o la fabrica del `EmpresaDto`).

## Requirements

### Requirement 1: Ver el Plan vigente en el listado de Empresas

**User Story:** Como super_admin, quiero ver el plan vigente de cada empresa en el listado, para conocer de
un vistazo que plan tiene contratado cada tenant sin abrir su ficha.

#### Acceptance Criteria

1. THE Sistema SHALL mostrar una columna "Plan" en el listado de Empresas del super_admin.
2. THE Sistema SHALL alimentar la columna "Plan" a partir del dato de PLAN VIGENTE que el backend agrega al
   `EmpresaDto` del listado, y THE Sistema SHALL NOT realizar peticiones adicionales por Empresa (una por
   fila) para obtener el plan vigente del listado.
3. WHEN una Empresa tiene un plan vigente en el dato enriquecido del `EmpresaDto`, THE Sistema SHALL mostrar
   en la columna "Plan" el NOMBRE del plan vigente provisto por el backend.
4. WHEN el dato enriquecido del `EmpresaDto` indica que la Empresa no tiene plan vigente, THE Sistema SHALL
   mostrar en la columna "Plan" un estado claro con el texto "Sin plan".
5. IF el `EmpresaDto` no incluye nombre de plan vigente resoluble, THEN THE Sistema SHALL mostrar un marcador
   neutro (por ejemplo "Sin plan"), y THE Sistema SHALL NOT mostrar el UUID.
6. THE Sistema SHALL NOT mostrar en la columna "Plan" ningun identificador UUID (`planId`, `tenantId` ni id
   de suscripcion).
7. WHERE el ancho de pantalla es reducido, THE Sistema SHALL aplicar el mismo patron de ocultamiento
   responsivo usado por la columna "Giro" para preservar la legibilidad sin scroll horizontal.

### Requirement 2: Resolver el nombre del plan (patron NO-UUID) segun su origen

**User Story:** Como super_admin, quiero que los planes se muestren siempre por nombre, para no ver
identificadores tecnicos en ningun punto de la interfaz.

#### Acceptance Criteria

1. THE Sistema SHALL tomar el NOMBRE del plan vigente para la COLUMNA "Plan" del listado y para la etiqueta
   del plan vigente en el panel directamente del dato enriquecido del `EmpresaDto` provisto por el backend,
   sin resolverlo en el frontend.
2. THE Sistema SHALL construir un mapa `planId` → nombre del plan a partir del catalogo de planes, cargandolo
   mediante el `PlanesService` existente, para poblar los SELECTORES de plan de las acciones de asignar o
   cambiar plan (y para el panel de la ficha cuando requiera mostrar nombres de planes distintos al vigente).
3. WHEN el Sistema necesita mostrar el nombre de un plan dentro de un SELECTOR a partir de su `planId`, THE
   Sistema SHALL resolverlo con el mapa de planes cargado.
4. IF la carga del catalogo de planes falla, THEN THE Sistema SHALL degradar de forma controlada en los
   SELECTORES sin romper la vista de Empresas; la columna "Plan" del listado seguira mostrando el nombre
   provisto por el backend por no depender de ese catalogo.
5. THE Sistema SHALL cargar el catalogo de planes de forma que cubra todos los planes referenciables en los
   selectores (paginacion del backend: tamano permitido hasta 100 por pagina).

### Requirement 3: Determinar la suscripcion vigente de una Empresa

**User Story:** Como super_admin, quiero que el Sistema elija de forma consistente cual suscripcion mostrar
como "actual", para que el plan y el estado reflejados sean predecibles.

#### Acceptance Criteria

1. WHEN el Sistema obtiene la lista de suscripciones de una Empresa (GET `/suscripciones?tenantId=...`) y
   existe al menos una en estado `activa`, THE Sistema SHALL seleccionar esa suscripcion `activa` como
   vigente.
2. WHEN no existe ninguna suscripcion en estado `activa`, THE Sistema SHALL seleccionar como vigente la
   suscripcion con `vigenciaInicio` mas reciente.
3. IF dos o mas suscripciones comparten la misma `vigenciaInicio` mas reciente, THEN THE Sistema SHALL
   seleccionar la de `createdAt` mas reciente como desempate.
4. WHEN la Empresa no tiene ninguna suscripcion, THE Sistema SHALL representar la ausencia de suscripcion
   vigente como estado "Sin plan".

### Requirement 4: Enriquecer el listado y la consulta de Empresas con el plan vigente (backend)

**User Story:** Como super_admin, quiero que el backend entregue el plan vigente de cada Empresa dentro del
`EmpresaDto`, para que el listado muestre la columna "Plan" sin que el frontend haga N peticiones, con una
regla de vigencia unica y como fuente de verdad.

#### Acceptance Criteria

1. THE EmpresaDto SHALL incluir un dato de PLAN VIGENTE derivado que contenga como minimo el NOMBRE del plan
   vigente y el ESTADO de la suscripcion vigente (`activa`, `suspendida` o `cancelada`).
2. WHERE el frontend requiera enlazar acciones sobre la suscripcion o el plan vigentes, THE EmpresaDto SHALL
   poder incluir los identificadores del plan vigente y de la suscripcion vigente, quedando la forma exacta
   (sub-objeto anidado tipo `planVigente { nombre, estadoSuscripcion, ... }` o campos planos) como decision
   de diseno; el contrato de INFORMACION disponible es: nombre del plan vigente, estado de la suscripcion
   vigente y los identificadores necesarios para invocar acciones.
3. WHEN una Empresa no tiene ninguna suscripcion, THE ServicioEmpresas SHALL poblar el dato de plan vigente
   del `EmpresaDto` de forma que represente inequivocamente la ausencia de plan vigente (sin plan).
4. WHEN el backend determina la suscripcion vigente de una Empresa, THE ServicioEmpresas SHALL aplicar la
   MISMA regla del Requirement 3 (suscripcion `activa`; si no existe ninguna `activa`, la de `vigenciaInicio`
   mas reciente; desempate por `createdAt` mas reciente).
5. THE ServicioEmpresas SHALL ser la FUENTE DE VERDAD de la regla de suscripcion vigente, y el frontend SHALL
   consumir el resultado sin recalcular esa regla para el listado.
6. WHEN el super_admin consulta el listado paginado GET `/empresas`, THE ServicioEmpresas SHALL incluir el
   dato de plan vigente en cada `EmpresaDto` de la pagina.
7. WHEN el super_admin consulta una Empresa por GET `/empresas/{id}`, THE ServicioEmpresas SHALL incluir el
   dato de plan vigente en el `EmpresaDto` de la respuesta, de forma consistente con el listado.
8. THE ServicioEmpresas SHALL resolver el NOMBRE del plan vigente en el backend (patron NO-UUID), de modo que
   el `EmpresaDto` exponga el nombre legible del plan y el usuario nunca vea el UUID.
9. THE EmpresaDto SHALL preservar todos los campos existentes de su contrato actual y SHALL agregar el dato
   de plan vigente unicamente como campos nuevos, sin eliminar ni renombrar campos existentes.
10. THE EmpresaDto SHALL exponer unicamente datos de PLATAFORMA; el plan vigente y el estado de la suscripcion
    se consideran datos de plataforma (monetizacion) y no datos de NEGOCIO del tenant, preservando el
    aislamiento del super_admin (Req 24.3 de la plataforma).
11. WHEN el super_admin lista N Empresas, THE ServicioEmpresas SHALL resolver las suscripciones y planes
    vigentes evitando el problema N+1 (por ejemplo, mediante una consulta por lote), sin ejecutar una
    consulta por cada Empresa de la pagina.
12. THE suite de pruebas de backend SHALL mantenerse en verde tras el enriquecimiento, y THE backend SHALL
    incorporar pruebas que verifiquen el dato de plan vigente en el listado y en la consulta por id.

### Requirement 5: Panel "Plan y Suscripcion" en la ficha de la Empresa

**User Story:** Como super_admin, quiero ver dentro de la ficha de cada empresa el plan vigente y el estado
de su suscripcion en lenguaje claro, para administrar la relacion comercial sin consultar la base de datos.

#### Acceptance Criteria

1. THE Sistema SHALL mostrar, dentro de la ficha de la Empresa, un panel "Plan y Suscripcion".
2. WHEN existe una suscripcion vigente, THE Sistema SHALL mostrar el NOMBRE del plan, la moneda del plan, el
   maximo de usuarios, los modulos habilitados y el total calculado por el backend.
3. WHEN existe una suscripcion vigente, THE Sistema SHALL mostrar el estado de la suscripcion con etiqueta en
   espanol ("Activa", "Suspendida", "Cancelada"), la vigencia con fechas legibles (inicio y fin), la moneda
   de facturacion y los modulos habilitados de la suscripcion.
4. WHEN la `vigenciaFin` de la suscripcion vigente es nula, THE Sistema SHALL mostrar un texto legible que
   indique vigencia sin fecha de fin (por ejemplo "Sin fecha de fin").
5. WHEN la Empresa no tiene suscripcion vigente, THE Sistema SHALL mostrar el estado "Sin plan" y ofrecer la
   accion de asignar un plan.
6. THE Sistema SHALL NOT mostrar en el panel ningun identificador UUID (`planId`, `tenantId`, `id` de
   suscripcion).
7. THE Sistema SHALL mostrar las claves de modulos habilitados por su etiqueta humana cuando el catalogo de
   modulos este disponible, reutilizando el patron de resolucion de nombres de modulo existente.

### Requirement 6: Asignar o cambiar el Plan de una Empresa

**User Story:** Como super_admin, quiero asignar o cambiar el plan de una empresa desde su ficha eligiendo el
plan de una lista, para actualizar su contrato sin exponer identificadores.

#### Acceptance Criteria

1. THE Sistema SHALL ofrecer, en el panel "Plan y Suscripcion", una accion para asignar o cambiar el plan de
   la Empresa, gobernada por el permiso `suscripcion:crear`.
2. THE Sistema SHALL presentar un selector de Planes por NOMBRE (sin UUID) para elegir el plan a asignar.
3. WHEN el super_admin confirma la asignacion, THE Sistema SHALL crear una suscripcion mediante POST
   `/suscripciones` con el `tenantId` de la Empresa y el `planId` seleccionado.
4. WHEN el super_admin proporciona fechas de vigencia (inicio y/o fin), THE Sistema SHALL enviarlas en la
   creacion de la suscripcion usando selectores de calendario.
5. IF el backend responde 404 (empresa o plan inexistente), THEN THE Sistema SHALL mostrar un mensaje claro
   en espanol y THE Sistema SHALL mantener abierto el dialogo para su lectura.
6. IF el backend responde 422 (vigencia invalida), THEN THE Sistema SHALL mostrar el mensaje del backend en
   espanol sin romper la vista.
7. WHEN la creacion de la suscripcion es exitosa, THE Sistema SHALL actualizar el panel y el listado de
   Empresas para reflejar el nuevo plan vigente.
8. THE Sistema SHALL NOT permitir teclear identificadores; el plan se elige de la lista.

### Requirement 7: Cambiar el estado de la Suscripcion (activar / suspender / cancelar)

**User Story:** Como super_admin, quiero activar, suspender o cancelar la suscripcion de una empresa desde su
ficha, para gestionar su ciclo de vida comercial con las reglas del backend.

#### Acceptance Criteria

1. WHERE existe una suscripcion vigente, THE Sistema SHALL ofrecer las acciones de activar, suspender y
   cancelar segun corresponda al estado actual, gobernadas por el permiso `suscripcion:cambiar_estado`.
2. WHEN el super_admin activa una suscripcion, THE Sistema SHALL invocar POST `/suscripciones/{id}/activar` y
   reflejar el nuevo estado en el panel y el listado al obtener exito.
3. WHEN el super_admin suspende una suscripcion, THE Sistema SHALL invocar POST `/suscripciones/{id}/suspender`
   y reflejar el nuevo estado en el panel y el listado al obtener exito.
4. WHEN el super_admin solicita cancelar una suscripcion, THE Sistema SHALL requerir una confirmacion
   explicita que advierta que la cancelacion es irreversible (estado final) antes de invocar POST
   `/suscripciones/{id}/cancelar`.
5. IF el super_admin intenta activar una suscripcion en estado `cancelada`, THEN THE Sistema SHALL mostrar el
   mensaje de error 422 del backend en espanol sin romper la vista.
6. IF el backend responde 404 (suscripcion inexistente) ante un cambio de estado, THEN THE Sistema SHALL
   mostrar un mensaje claro en espanol y refrescar el panel.
7. WHEN un cambio de estado es exitoso, THE Sistema SHALL actualizar la etiqueta de estado mostrada y la
   columna "Plan" del listado si corresponde.

### Requirement 8: Actualizar la vigencia de la Suscripcion

**User Story:** Como super_admin, quiero ajustar la vigencia (inicio y fin) de la suscripcion de una empresa,
para corregir o extender su periodo contratado.

#### Acceptance Criteria

1. WHERE existe una suscripcion vigente, THE Sistema SHALL ofrecer una accion para actualizar su vigencia,
   gobernada por el permiso `suscripcion:actualizar`.
2. THE Sistema SHALL capturar la `vigenciaInicio` y la `vigenciaFin` mediante selectores de calendario
   (datepickers), con etiquetas en espanol.
3. WHEN el super_admin confirma la actualizacion, THE Sistema SHALL invocar PUT `/suscripciones/{id}/vigencia`
   con las fechas capturadas.
4. IF el backend responde 422 (vigencia invalida), THEN THE Sistema SHALL mostrar el mensaje del backend en
   espanol sin cerrar el dialogo.
5. IF el backend responde 404 (suscripcion inexistente), THEN THE Sistema SHALL mostrar un mensaje claro en
   espanol.
6. WHEN la actualizacion de vigencia es exitosa, THE Sistema SHALL reflejar las nuevas fechas legibles en el
   panel.

### Requirement 9: Servicio de frontend para Suscripciones completo

**User Story:** Como desarrollador, quiero que el frontend disponga de todas las operaciones de suscripcion
que el backend expone, para conectar la UI sin dejar acciones sin respaldo.

#### Acceptance Criteria

1. THE Sistema SHALL disponer de una operacion de frontend para listar las suscripciones de una Empresa
   (GET `/suscripciones?tenantId=...`).
2. THE Sistema SHALL disponer de una operacion de frontend para crear una suscripcion (POST `/suscripciones`).
3. THE Sistema SHALL disponer de operaciones de frontend para activar, suspender y cancelar una suscripcion
   (POST `/suscripciones/{id}/activar|suspender|cancelar`).
4. THE Sistema SHALL disponer de una operacion de frontend para actualizar la vigencia de una suscripcion
   (PUT `/suscripciones/{id}/vigencia`).
5. THE Sistema SHALL disponer de una operacion de frontend para consultar una suscripcion por su
   identificador (GET `/suscripciones/{id}`) cuando el flujo lo requiera.
6. THE Sistema SHALL reutilizar el `PlanesService` existente o extenderlo sin duplicar las operaciones ya
   definidas, evitando codigo muerto.

### Requirement 10: RBAC y visibilidad por permiso

**User Story:** Como plataforma, quiero que las acciones de plan y suscripcion solo esten disponibles para el
super_admin, para preservar la seguridad multi-tenant.

#### Acceptance Criteria

1. THE Sistema SHALL mostrar la accion de asignar/cambiar plan solo cuando el usuario tenga el permiso
   `suscripcion:crear`.
2. THE Sistema SHALL mostrar las acciones de activar/suspender/cancelar solo cuando el usuario tenga el
   permiso `suscripcion:cambiar_estado`.
3. THE Sistema SHALL mostrar la accion de actualizar vigencia solo cuando el usuario tenga el permiso
   `suscripcion:actualizar`.
4. THE Sistema SHALL mostrar el panel y la columna "Plan" solo cuando el usuario tenga permiso de lectura de
   suscripciones (`suscripcion:leer` o `suscripcion:listar`).
5. IF el backend responde 403 ante cualquier operacion de plan o suscripcion, THEN THE Sistema SHALL mostrar
   un mensaje claro en espanol indicando falta de permisos, sin romper la vista.

### Requirement 11: Accesibilidad, idioma y sistema de diseno

**User Story:** Como super_admin, quiero que la nueva UI sea accesible y consistente con la plataforma, para
usarla de forma comoda y conforme a los estandares del proyecto.

#### Acceptance Criteria

1. THE Sistema SHALL presentar todos los textos, etiquetas y mensajes en espanol (es-MX).
2. THE Sistema SHALL cumplir WCAG AA en los dialogos y el panel, incluyendo focus trap en los dialogos,
   etiquetas asociadas a los campos, roles apropiados y mensajes de error asociados a su campo.
3. THE Sistema SHALL reutilizar los design tokens, componentes y estilos existentes (patrones de dialogo como
   `CambiarGiroDialog`, tabla de Empresas y componentes compartidos), sin introducir estilos ad-hoc.
4. THE Sistema SHALL ser responsivo, preservando la legibilidad en pantallas estrechas.
5. THE Sistema SHALL NOT exponer ningun UUID en ningun punto de la interfaz.

### Requirement 12: Consistencia, conexion y no regresion

**User Story:** Como super_admin, quiero que los cambios de plan y suscripcion se reflejen en todas las vistas
relacionadas y que no se rompa el comportamiento existente, para confiar en la informacion mostrada.

#### Acceptance Criteria

1. WHEN cambia el plan o el estado o la vigencia de una suscripcion, THE Sistema SHALL reflejar el cambio en
   el panel de la ficha y en la columna "Plan" del listado de Empresas.
2. THE Sistema SHALL preservar el comportamiento verificado existente de la vista de Empresas (alta, edicion,
   activar/suspender empresa, cambiar giro, restablecer contrasena, offboarding, KPIs).
3. THE Sistema SHALL mantener la coherencia con el flujo existente de modulos habilitados (los modulos
   mostrados en el panel corresponden a los de la suscripcion/plan vigentes).
4. THE Sistema SHALL compilar sin errores y mantener en verde la suite de pruebas existente (frontend y
   backend, 1173+ pruebas de backend deben seguir verdes), agregando pruebas para el enriquecimiento del
   `EmpresaDto` en el backend, el servicio de suscripciones y la UI del panel.
5. THE Sistema SHALL NOT dejar codigo muerto ni componentes aislados; toda accion nueva queda conectada a la
   ficha de la Empresa y al listado.

## Suposiciones y riesgos (para revision antes de diseno)

Estas notas marcan supuestos y decisiones. Por decision enterprise, en esta feature SI se toca el backend,
pero de forma ADITIVA (solo agregar campos al `EmpresaDto`), preservando contratos, aislamiento del
super_admin y la suite de pruebas. El resto de supuestos de backend sigue vigente; si alguno requiere mas
cambios de backend, debe elevarse como decision antes de disenar.

1. **RESUELTO — Plan vigente en el listado.** Originalmente `EmpresaDto` no traia el plan vigente, lo que
   obligaba a DERIVARLO en el frontend con N peticiones (una por Empresa) — un riesgo de rendimiento. La
   decision adoptada RESUELVE este riesgo: el backend ENRIQUECE el `EmpresaDto` con el plan vigente (nombre
   del plan + estado de la suscripcion) tanto en GET `/empresas` como en GET `/empresas/{id}`, calculando la
   suscripcion vigente en el backend (fuente de verdad) y evitando el problema N+1 mediante carga por lote
   (ver Requirement 4). El frontend YA NO hace N peticiones para la columna "Plan".

2. **`PUT /suscripciones/{id}/vigencia` NO esta implementado en `PlanesService` (frontend) hoy.** El
   contrato del backend lo incluye, pero el servicio de frontend actual solo cubre crear/activar/suspender/
   cancelar y listar. El Requirement 9.4 obliga a agregar esa operacion. Suposicion: el cuerpo esperado
   contiene `vigenciaInicio` y `vigenciaFin`; confirmar la forma exacta del request en diseno.

3. **`GET /suscripciones/{id}` (consultar por id).** Se asume disponible por el contrato REST; se listara en
   el servicio si algun flujo lo requiere (Requirement 9.5). Confirmar existencia y permiso.

4. **Definicion de "suscripcion vigente".** La regla (activa; si no, la mas reciente por `vigenciaInicio`;
   desempate por `createdAt`) es una decision de producto de esta feature. Ahora VIVE en el backend como
   fuente de verdad para el listado (Requirement 4.4 y 4.5) y se documenta como criterio explicito
   (Requirement 3) para que sea revisable y consistente entre backend y frontend.

5. **Forma exacta del dato enriquecido en `EmpresaDto`.** El CONTRATO de informacion esta fijado (nombre del
   plan vigente, estado de la suscripcion vigente e identificadores necesarios para acciones), pero la forma
   concreta (sub-objeto anidado `planVigente { ... }` vs. campos planos) queda como decision de diseno
   (Requirement 4.2). El frontend NO mostrara los identificadores como UUID, pero puede necesitarlos para
   invocar acciones.

6. **Cancelar es irreversible.** Confirmado por el contrato (cancelada→activar da 422). La UI lo trata como
   accion destructiva con confirmacion (Requirement 7.4).

7. **Codigos de error.** Se asumen los codigos del contrato (404, 409, 422, 403). El mapeo a mensajes usa el
   helper existente `mensajeDeError`; los mensajes 422/404 del backend se propagan tal cual cuando aportan
   contexto.

8. **Permisos.** Se asume que el super_admin ya tiene sembrados `plan:*` y `suscripcion:*` (crear, actualizar,
   leer, listar, cambiar_estado). No se introduce ningun permiso nuevo.

9. **Compatibilidad y aislamiento del backend.** El enriquecimiento del `EmpresaDto` solo AGREGA campos (no
   elimina ni renombra), por lo que no rompe el contrato existente ni la suite (1173+ pruebas de backend
   deben seguir verdes). El plan/suscripcion se tratan como datos de PLATAFORMA (monetizacion), coherentes
   con el aislamiento del super_admin documentado en el `EmpresaDto` (Req 24.3 de la plataforma); no se
   expone ningun dato de NEGOCIO del tenant.
