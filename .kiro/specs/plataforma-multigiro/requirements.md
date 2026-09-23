# Documento de Requisitos — Plataforma Multigiro (Verticales Enchufables)

## Introducción

Hoy la plataforma Dessti (backend Spring Boot 3.3.5 / Java 21, arquitectura hexagonal, monolito modular bajo `com.dessti.crm`; frontend Angular 21) es **multiempresa (multi-tenant)** y funciona: aislamiento por `tenant_id`, RLS en PostgreSQL, filtro global de Hibernate, RBAC con permisos atómicos y *gating por Plan* (Req 25.4 del spec `crm-anuncios-luminosos`). Sin embargo, las reglas de negocio del vertical **"anuncios luminosos"** están **en duro**: el flujo Oportunidad → Cotización → Prueba de Diseño → Orden de Fabricación → Levantamiento → Permiso de Instalación → Orden de Trabajo de Instalación → Mantenimiento vive incrustado en el núcleo (paquetes `comercial`, `operacion`, `mantenimiento`), asumiendo que **toda** Empresa es de anuncios luminosos.

Este spec convierte la plataforma en **multigiro** mediante **verticales enchufables (plugins de giro)**: un **Núcleo Común** transversal a cualquier giro (seguridad, RBAC, auditoría, multi-tenancy, empresas/planes/suscripciones, catálogo comercial base, facturación CFDI, contabilidad, RH-nómina, tesorería, compras, inventario base, notificaciones, reportes-BI base, calidad) más **Módulos-Vertical** independientes por giro, cada uno con sus entidades, flujos, reglas de negocio, permisos y navegación, **aislados y sin interferencia entre giros**. El **Super_Administrador**, al crear una Empresa, **elige un Giro** mediante un selector; la Empresa hereda las reglas de negocio de ese Giro. El diseño debe **escalar a N giros**.

Este entregable persigue dos objetivos indivisibles:

1. **Andamiaje multigiro**: Catálogo de Giros administrado por el Super_Administrador, selección de Giro al alta de Empresa, un **Contrato de Vertical** (puerto de plugin hexagonal-puro) por el que cada Módulo-Vertical se registra, expone lo suyo al Núcleo y consume del Núcleo sin acoplarse a otros verticales, y un **Gating por Giro** que se compone con el Gating por Plan existente.
2. **Refactor de "anuncios luminosos" como el PRIMER vertical enchufable**: extraer del Núcleo el flujo operativo/comercial específico de anuncios (Prueba_Diseno, Orden_Fabricacion, Levantamiento_Sitio, Permiso_Instalacion, Orden_Trabajo_Instalacion, Cuadrilla, y la parte de Mantenimiento/Proyecto-Sitio propia del vertical) a su propio Módulo-Vertical, dejando el Núcleo limpio. Anuncios y un futuro giro "manufactura" quedan como **hermanos** sobre el mismo Núcleo.

Este documento es **complementario** al spec `crm-anuncios-luminosos` y **no lo contradice**: reutiliza sus mecanismos (Empresa=tenant, `PlanModulosPort`, `@autorizador.moduloHabilitado`, RLS, auditoría) y añade el eje "Giro" como **atributo de la Empresa**, nunca como nuevo eje de aislamiento. Toda operación multigiro se verifica con la suite completa (backend `mvn -o clean verify`: unit + IT con Testcontainers, 0 fallos; frontend `ng build` + `ng test`, 0 fallos), sin agujeros negros, sin código duplicado, sin parches ni entregas a medias.

## Glosario

- **Sistema**: La plataforma Dessti en su conjunto (backend, API `/api/v1` y frontend Angular).
- **Nucleo_Comun (Núcleo)**: Conjunto de módulos transversales a **todo** Giro: seguridad, RBAC, auditoría, multi-tenancy, empresas/planes/suscripciones/monetización, catálogo comercial base (Cliente, Contacto, Oportunidad, Cotización, Producto, Lista_Precios), facturación CFDI, contabilidad, RH-nómina, tesorería, compras, inventario base, notificaciones, reportes-BI base y calidad. El Núcleo **no conoce** ningún Giro concreto.
- **Giro**: Vertical de negocio al que pertenece una Empresa (p. ej. `anuncios-luminosos`, `manufactura`). Determina qué Módulos-Vertical, permisos, flujos y navegación específicos hereda la Empresa. Es un **atributo de la Empresa**, administrado por el Super_Administrador en el Catalogo_Giros.
- **Catalogo_Giros**: Catálogo de plataforma, administrado por el Super_Administrador, que enumera los Giros disponibles y su metadato (clave canónica, nombre visible, descripción, estado activo/inactivo, Modulos_Vertical que aporta).
- **Modulo_Vertical (Vertical, Plugin de Giro)**: Componente enchufable que implementa el flujo, entidades, reglas, permisos y navegación específicos de **un** Giro. Se registra ante el Núcleo mediante el Contrato_Vertical, consume servicios del Núcleo por puertos y **no depende** de otros Modulos_Vertical.
- **Contrato_Vertical**: Puerto (interfaz) hexagonal-puro por el que un Modulo_Vertical se registra ante el Núcleo, declara su Giro, los módulos que aporta, sus permisos, su navegación y las capacidades del Núcleo que consume. El Núcleo depende solo del Contrato_Vertical, nunca de una implementación concreta de vertical.
- **Registro_Verticales**: Componente del Núcleo que descubre y registra en el arranque todas las implementaciones del Contrato_Vertical disponibles y las indexa por su Giro.
- **Gating_Por_Plan**: Control existente (Req 25.4) que deniega con 403 el acceso a un módulo no habilitado en el Plan de la Empresa, evaluado por `@autorizador.moduloHabilitado(...)` vía `PlanModulosPort`.
- **Gating_Por_Giro**: Control nuevo que deniega con 403 el acceso a una operación de un Modulo_Vertical cuando el Giro de la Empresa del Usuario no corresponde al Giro que aporta ese Modulo_Vertical.
- **Empresa (Tenant)**: Empresa cliente que renta el Sistema; unidad de aislamiento identificada por su `tenant_id` (su PK). Ahora, además, referencia **exactamente un** Giro.
- **tenant_id**: Identificador único de la Empresa que segmenta y aísla todos los datos de negocio. El Giro **no** sustituye ni complementa al `tenant_id` como eje de aislamiento.
- **Super_Administrador**: Usuario de nivel plataforma (`super_admin`) que administra Empresas, Planes, Suscripciones y ahora el Catalogo_Giros y la asignación de Giro a cada Empresa.
- **Administrador_Empresa**: Usuario (`admin_empresa`) que administra usuarios, roles y configuración dentro de su Empresa; su experiencia queda acotada al Giro de su Empresa.
- **Permiso**: Autorización atómica `recurso:operacion` con denegación por defecto (Req 3 del spec base). Los permisos de un Modulo_Vertical son atómicos igual que los del Núcleo, pero solo aplican a Empresas del Giro correspondiente.
- **Rol_Predefinido**: Rol inmutable sembrado por el Sistema. Los roles que agrupan permisos de un vertical se siembran de forma que solo tengan efecto para Empresas de ese Giro.
- **Rol_Personalizado**: Rol definido por un Administrador_Empresa combinando permisos disponibles **para el Giro de su Empresa**.
- **Vertical_Anuncios**: Primer Modulo_Vertical, resultado de extraer del Núcleo el flujo específico de anuncios luminosos (Prueba_Diseno, Orden_Fabricacion, Levantamiento_Sitio, Permiso_Instalacion, Orden_Trabajo_Instalacion, Cuadrilla y la parte vertical de Mantenimiento/Proyecto-Sitio).
- **Vertical_Manufactura**: Segundo Giro usado como **prueba del modelo** (entidades tipo BOM, Orden_Produccion, planeación) que debe encajar en el Contrato_Vertical sin tocar el Vertical_Anuncios ni el Núcleo.
- **Frontera_Nucleo_Vertical**: Criterio explícito que decide, para cada capacidad, si pertenece al Núcleo (transversal a todo Giro) o a un Modulo_Vertical (específico de un Giro).

## Requisitos

### Requisito 1: Catálogo de Giros administrado por el Super_Administrador

**Historia de Usuario:** Como Super_Administrador, quiero administrar un Catálogo de Giros, para que existan verticales de negocio bien definidos que luego pueda asignar a cada Empresa.

#### Criterios de Aceptación

1. WHERE un Usuario tiene el rol `super_admin`, THE Sistema SHALL permitir crear, consultar, activar y desactivar Giros en el Catalogo_Giros.
2. WHEN el Super_Administrador crea un Giro con datos válidos (clave canónica, nombre visible y descripción), THE Sistema SHALL persistir el Giro con un identificador único y su clave canónica normalizada a minúsculas.
3. IF el Super_Administrador intenta crear un Giro con una clave canónica que ya existe, THEN THE Sistema SHALL rechazar la operación con un código de estado 422 e informar la clave duplicada.
4. THE Sistema SHALL asociar a cada Giro el conjunto de claves de Modulo_Vertical que ese Giro aporta, derivado del Registro_Verticales.
5. WHEN el Super_Administrador desactiva un Giro que está asignado a al menos una Empresa, THE Sistema SHALL rechazar la desactivación con un código de estado 422 e informar el número de Empresas que aún lo usan.
6. WHEN el Super_Administrador consulta el Catalogo_Giros, THE API SHALL devolver los Giros de forma paginada con un tamaño de página predeterminado de 20 registros y un máximo de 100 registros por página, y permitir filtrar por estado activo/inactivo.
7. WHEN el Super_Administrador crea, activa o desactiva un Giro, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el Giro afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 2: Selección de Giro al crear la Empresa

**Historia de Usuario:** Como Super_Administrador, quiero elegir el Giro al dar de alta una Empresa, para que la Empresa herede automáticamente el vertical de negocio correcto.

#### Criterios de Aceptación

1. WHEN el Super_Administrador crea una Empresa, THE Sistema SHALL exigir la selección de exactamente un Giro activo del Catalogo_Giros como dato obligatorio del alta, además de los datos ya requeridos por el Requisito 24.2 del spec base (nombre, identificador fiscal y Plan inicial).
2. IF el Super_Administrador crea una Empresa sin indicar un Giro, o indicando un Giro inexistente o inactivo, THEN THE Sistema SHALL rechazar el alta con un código de estado 422 e informar que el Giro es obligatorio y debe estar activo.
3. WHEN una Empresa se crea con un Giro válido, THE Sistema SHALL persistir la referencia al Giro en la Empresa de forma permanente y asociarla al `tenant_id` generado.
4. THE Sistema SHALL exponer el Giro de una Empresa como dato de solo lectura en las consultas de plataforma del Super_Administrador y en el contexto de sesión de los Usuarios de esa Empresa.
5. WHEN el Super_Administrador crea una Empresa con un Giro, THE Servicio_Auditoria SHALL registrar el Giro seleccionado dentro del Registro_Auditoria del alta de la Empresa.

### Requisito 3: Cambio de Giro de una Empresa existente

**Historia de Usuario:** Como Super_Administrador, quiero que el cambio de Giro de una Empresa sea una operación controlada, para no corromper los datos de negocio ya generados bajo el Giro original.

#### Criterios de Aceptación

1. WHERE una Empresa no tiene datos de negocio específicos del Giro actual, THE Sistema SHALL permitir al Super_Administrador cambiar su Giro por otro Giro activo.
2. IF el Super_Administrador intenta cambiar el Giro de una Empresa que ya tiene datos de negocio específicos de su Giro actual, THEN THE Sistema SHALL rechazar el cambio con un código de estado 422 e informar que existen datos del vertical que impiden el cambio.
3. WHEN el Giro de una Empresa cambia, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, el Giro anterior, el Giro nuevo, la Empresa afectada y la marca temporal en UTC.

### Requisito 4: Contrato de Vertical (puerto de plugin de giro)

**Historia de Usuario:** Como arquitecto de la plataforma, quiero un Contrato de Vertical hexagonal-puro, para que cada vertical se enchufe al Núcleo de forma uniforme, aislada y verificable, y para poder escalar a N giros sin tocar el Núcleo.

#### Criterios de Aceptación

1. THE Nucleo_Comun SHALL definir el Contrato_Vertical como un puerto que cada Modulo_Vertical implementa para declarar su Giro, las claves de módulo que aporta, sus permisos atómicos, su metadato de navegación y las capacidades del Núcleo que consume.
2. THE Nucleo_Comun SHALL depender únicamente del Contrato_Vertical y NO SHALL depender de ninguna implementación concreta de Modulo_Vertical.
3. WHEN el Sistema arranca, THE Registro_Verticales SHALL descubrir todas las implementaciones del Contrato_Vertical disponibles y registrarlas indexadas por su Giro.
4. IF dos Modulo_Vertical distintos declaran la misma clave de Giro, THEN THE Registro_Verticales SHALL rechazar el arranque del Sistema e informar el conflicto de Giro duplicado.
5. THE Modulo_Vertical SHALL consumir las capacidades del Núcleo (Cliente, Cotización, Facturación, Inventario y demás) exclusivamente a través de puertos del Núcleo, y NO SHALL acceder a las clases internas de persistencia del Núcleo ni de otro Modulo_Vertical.
6. THE Modulo_Vertical SHALL NOT declarar dependencias de compilación hacia otro Modulo_Vertical.

### Requisito 5: Frontera entre Núcleo y Vertical

**Historia de Usuario:** Como arquitecto de la plataforma, quiero una frontera explícita entre lo que es Núcleo y lo que es Vertical, para evitar duplicación y ambigüedad al crecer a N giros.

#### Criterios de Aceptación

1. THE Sistema SHALL clasificar como Nucleo_Comun las capacidades transversales a todo Giro: seguridad, RBAC, auditoría, multi-tenancy, empresas/planes/suscripciones/monetización, facturación CFDI, contabilidad, RH-nómina, tesorería, compras, inventario base, notificaciones, reportes-BI base y calidad.
2. THE Sistema SHALL clasificar como Modulo_Vertical los flujos y entidades específicos de un Giro que no aplican a otros Giros.
3. THE Sistema SHALL clasificar el catálogo comercial base (Cliente, Contacto, Oportunidad, Cotización, Producto, Lista_Precios) como Nucleo_Comun compartido por todos los Giros.
4. WHERE una capacidad pertenece al Nucleo_Comun, THE Sistema SHALL exponerla a todos los Modulo_Vertical mediante puertos del Núcleo sin duplicar su lógica en cada vertical.
5. IF una capacidad no aplica a todos los Giros, THEN THE Sistema SHALL ubicarla en el Modulo_Vertical correspondiente y NO SHALL ubicarla en el Nucleo_Comun.

### Requisito 6: Gating por Giro compuesto con Gating por Plan

**Historia de Usuario:** Como Super_Administrador, quiero que cada Empresa solo vea y use los módulos del vertical de su Giro, para que un giro no exponga funciones de otro giro.

#### Criterios de Aceptación

1. WHERE una operación pertenece a un Modulo_Vertical cuyo Giro no coincide con el Giro de la Empresa del Usuario, THE Servicio_Autorizacion SHALL denegar el acceso con un código de estado 403.
2. THE Servicio_Autorizacion SHALL evaluar el Gating_Por_Giro sin sustituir ni debilitar el Gating_Por_Plan (Req 25.4 del spec base): una operación de un Modulo_Vertical requiere que su módulo esté habilitado en el Plan Y que su Giro corresponda a la Empresa.
3. IF la operación pertenece a un Modulo_Vertical cuyo módulo no está habilitado en el Plan de la Empresa, THEN THE Servicio_Autorizacion SHALL denegar el acceso con un código de estado 403 conforme al Gating_Por_Plan, con independencia del Giro.
4. WHERE una operación pertenece al Nucleo_Comun, THE Servicio_Autorizacion SHALL NOT aplicar el Gating_Por_Giro y SHALL aplicar únicamente el RBAC y, cuando corresponda, el Gating_Por_Plan.
5. WHEN el Servicio_Autorizacion deniega una operación por Gating_Por_Giro, THE Servicio_Auditoria SHALL registrar el intento con el actor, el Giro de la Empresa, el Giro requerido por la operación y la marca temporal en UTC.

### Requisito 7: RBAC consciente del Giro

**Historia de Usuario:** Como Administrador_Empresa, quiero que los roles y permisos de mi Empresa correspondan a su Giro, para no ver ni asignar permisos de verticales que no me aplican.

#### Criterios de Aceptación

1. THE Sistema SHALL modelar los permisos de un Modulo_Vertical como permisos atómicos `recurso:operacion`, con el mismo formato que los permisos del Nucleo_Comun.
2. WHERE una Empresa pertenece a un Giro, THE Servicio_Autorizacion SHALL considerar aplicables únicamente los permisos del Nucleo_Comun y los del Modulo_Vertical de ese Giro.
3. WHEN un Administrador_Empresa define un Rol_Personalizado, THE Sistema SHALL ofrecer para su selección únicamente los permisos aplicables al Giro de su Empresa.
4. IF un Rol_Personalizado incluye un permiso de un Modulo_Vertical ajeno al Giro de la Empresa, THEN THE Sistema SHALL rechazar la definición del Rol_Personalizado con un código de estado 422.
5. THE Sistema SHALL mantener inmutables los Rol_Predefinido y sembrar los permisos de cada vertical de forma que solo tengan efecto para Empresas del Giro correspondiente.

### Requisito 8: Coherencia de Multi-tenancy con el Giro

**Historia de Usuario:** Como responsable de seguridad, quiero que el Giro no altere el aislamiento multi-tenant, para preservar las garantías de RLS y `tenant_id` ya verificadas.

#### Criterios de Aceptación

1. THE Sistema SHALL tratar el Giro como un atributo de la Empresa (tenant) y NO SHALL usarlo como eje de aislamiento adicional al `tenant_id`.
2. THE Sistema SHALL seguir derivando el `tenant_id` del contexto de autenticación y NO SHALL aceptar el Giro como parámetro manipulable de la petición, obteniéndolo del contexto de la Empresa del Usuario.
3. THE entidades de negocio de un Modulo_Vertical SHALL respetar las mismas garantías de aislamiento por `tenant_id`, RLS y filtro global de Hibernate que las entidades del Nucleo_Comun.
4. WHERE una tabla del Catalogo_Giros es un dato de plataforma, THE Sistema SHALL tratarla como dato de plataforma sin políticas RLS por `tenant_id`, coherente con el tratamiento de la tabla `empresa`.

### Requisito 9: Navegación de frontend dinámica por Giro

**Historia de Usuario:** Como Usuario de una Empresa, quiero que la aplicación cargue el vertical correcto según el Giro de mi Empresa, para trabajar solo con los menús y las rutas que me aplican.

#### Criterios de Aceptación

1. WHEN un Usuario de Empresa inicia sesión, THE Sistema SHALL exponer el Giro de su Empresa dentro del contexto de sesión del frontend.
2. WHILE un Usuario de Empresa navega en el ámbito `/empresa`, THE frontend SHALL cargar los menús y las rutas del Modulo_Vertical correspondiente al Giro de su Empresa, además de la navegación del Nucleo_Comun común a todo Giro.
3. THE frontend SHALL NOT mostrar menús ni rutas de un Modulo_Vertical ajeno al Giro de la Empresa del Usuario.
4. IF un Usuario navega directamente a una ruta de un Modulo_Vertical ajeno a su Giro, THEN THE frontend SHALL impedir el acceso y redirigir a una vista permitida, coherente con la denegación 403 del backend.
5. THE navegación del ámbito `/plataforma` (Super_Administrador) y `/portal` (Cliente) SHALL permanecer independiente del Giro de las Empresas.

### Requisito 10: Extracción del Vertical de Anuncios Luminosos

**Historia de Usuario:** Como arquitecto de la plataforma, quiero extraer el flujo de anuncios luminosos a su propio Módulo-Vertical, para dejar el Núcleo limpio y demostrar el modelo enchufable con un vertical real.

#### Criterios de Aceptación

1. THE Sistema SHALL ubicar en el Vertical_Anuncios los flujos y entidades específicos de anuncios luminosos: Prueba_Diseno, Orden_Fabricacion, Levantamiento_Sitio, Permiso_Instalacion, Orden_Trabajo_Instalacion, Cuadrilla y la parte de Mantenimiento y Proyecto-Sitio propia del vertical.
2. THE Vertical_Anuncios SHALL implementar el Contrato_Vertical declarando el Giro `anuncios-luminosos`, las claves de módulo que aporta, sus permisos y su navegación.
3. THE Vertical_Anuncios SHALL consumir las capacidades del Nucleo_Comun (Cliente, Cotización, Facturación, Inventario base y demás) exclusivamente por puertos del Núcleo.
4. WHEN se extrae el Vertical_Anuncios, THE Sistema SHALL preservar el comportamiento funcional previo y la totalidad de la suite de pruebas SHALL seguir pasando sin fallos.
5. THE extracción del Vertical_Anuncios SHALL NOT dejar en el Nucleo_Comun código específico de anuncios luminosos ni referencias a sus entidades.

### Requisito 11: Migración y compatibilidad de datos existentes

**Historia de Usuario:** Como responsable de datos, quiero que las Empresas y los datos existentes queden correctamente asignados al Giro de anuncios luminosos, para no romper información ni la suite durante el refactor.

#### Criterios de Aceptación

1. WHEN se aplica la migración multigiro, THE Sistema SHALL sembrar el Giro `anuncios-luminosos` en el Catalogo_Giros como Giro activo.
2. WHEN se aplica la migración multigiro, THE Sistema SHALL asignar el Giro `anuncios-luminosos` a todas las Empresas existentes que no tengan Giro.
3. THE migración de datos SHALL ejecutarse mediante Flyway de forma versionada y no destructiva, preservando los datos de negocio existentes.
4. IF la migración multigiro no puede completarse de forma consistente, THEN THE Sistema SHALL fallar el arranque sin aplicar cambios parciales, dejando la base de datos en un estado coherente.
5. WHEN la migración multigiro concluye, THE totalidad de la suite de pruebas SHALL pasar sin fallos.

### Requisito 12: Prueba del modelo con un segundo Giro (Manufactura)

**Historia de Usuario:** Como arquitecto de la plataforma, quiero demostrar que un segundo Giro encaja en el Contrato de Vertical, para validar que el modelo escala a N giros sin tocar anuncios ni el Núcleo.

#### Criterios de Aceptación

1. THE diseño SHALL demostrar, mediante un Vertical_Manufactura de referencia con entidades propias (por ejemplo BOM, Orden_Produccion y planeación), que un segundo Giro encaja en el Contrato_Vertical.
2. WHERE se incorpora el Vertical_Manufactura, THE Sistema SHALL registrarlo por su Giro `manufactura` sin modificar el Vertical_Anuncios ni el Nucleo_Comun.
3. THE Vertical_Manufactura SHALL consumir las capacidades del Nucleo_Comun exclusivamente por puertos del Núcleo, igual que el Vertical_Anuncios.
4. WHEN coexisten el Vertical_Anuncios y el Vertical_Manufactura, THE Servicio_Autorizacion SHALL aislar sus operaciones por Gating_Por_Giro de modo que una Empresa de un Giro no acceda a las operaciones del otro.

### Requisito 13: Verificación por bloques con la suite completa

**Historia de Usuario:** Como responsable de calidad, quiero que cada bloque de implementación se verifique con la suite completa, para garantizar una entrega enterprise sin regresiones ni entregas a medias.

#### Criterios de Aceptación

1. WHEN se completa un bloque de implementación del backend, THE Sistema SHALL verificarse con `mvn -o clean verify` (pruebas unitarias e IT con Testcontainers) con cero fallos.
2. WHEN se completa un bloque de implementación del frontend, THE Sistema SHALL verificarse con `ng build` y `ng test` con cero fallos.
3. THE implementación multigiro SHALL NOT introducir código duplicado entre el Nucleo_Comun y los Modulo_Vertical para una misma capacidad.
4. IF un bloque de implementación deja código huérfano o no integrado, THEN el bloque SHALL considerarse incompleto y no se avanzará al siguiente bloque.
5. THE documentos de dominio, comentarios de código y mensajes de dominio SHALL redactarse en español cuando sea posible.
