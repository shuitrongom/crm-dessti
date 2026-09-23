# Requirements Document

## Introduction

Esta funcionalidad introduce en la plataforma multi-tenant **Dess-TI / plataforma-multigiro** (Spring Boot + Angular + PostgreSQL) una decisión de producto central: cada Empresa cliente se contrata mediante **exactamente un** instrumento comercial vigente que es **O un Plan O un Paquete de Suscripción, nunca ambos** (contratos mutuamente excluyentes).

Se establecen **dos catálogos independientes**, cada uno con sus propios campos (módulos habilitados, precio, límite de usuarios, moneda, giro):

- **Plan**: contrato de largo plazo, con duración **mayor a 1 año**, sin periodo de prueba, firme y facturado.
- **Paquete de Suscripción**: contrato de corto plazo, con duración **menor o igual a 1 año**, que admite un periodo de prueba otorgado por el `super_admin`.

Al dar de alta una Empresa, el `super_admin` elige uno de los dos instrumentos de forma excluyente. El Sistema debe soportar el periodo de prueba, el aviso de "por vencer", la conversión de prueba a suscripción facturada, el corte de acceso al vencer, la regla de que compromisos mayores a un año deben ser Planes, y la administración de ambos catálogos desde la pantalla "Planes y suscripciones".

Este es un cambio de modelo de datos amplio: amplía el enum de estados, agrega un catálogo nuevo, modifica el alta de Empresa, endurece el gating de acceso por vencimiento y requiere una migración de base de datos. Todo el trabajo debe ser enterprise-grade, sin código muerto, cumplir WCAG AA, usar design tokens, no exponer identificadores UUID al usuario final, respetar RBAC por permiso y mantener verde la suite de pruebas del backend.

### Contexto técnico confirmado (código real)

Estos hechos provienen del código actual y sirven de base a los criterios; no son suposiciones:

- **Gating de acceso**: `PlanModulosPlanAdapter` (implementa `PlanModulosPort` y `ModulosHabilitadosPort`) es la fuente única de verdad. Toma el Contrato con estado `ACTIVA` de la Empresa; si hay override de módulos aplica el override, si no hereda del Plan. **Hoy solo considera el estado `ACTIVA` y NO revisa `vigenciaFin`**: un Contrato activo pero vencido seguiría concediendo acceso.
- **Enum `EstadoSuscripcion`** actual: `ACTIVA`, `SUSPENDIDA`, `CANCELADA`. Se persiste como etiqueta en minúsculas (`activa`/`suspendida`/`cancelada`) vía `EstadoSuscripcionConverter`, con un CHECK `ck_suscripcion_estado` definido en la migración V1. La cancelación es final (no admite más transiciones).
- **Entidad `Suscripcion`** (actual): representa el **Contrato** empresa↔plan (referencia `planId`, `tenantId`, `estado`, `vigenciaInicio`, `vigenciaFin`, override `modulosHabilitados`, `monedaFacturacion`). No es un catálogo.
- **Entidad `Plan`** (catálogo): `nombre`, `maxUsuarios`, `giroId`, `monedaCodigo`, `preciosModulos` (JSONB clave→precio), `modulosHabilitados` (derivado de las claves de precios), `total` (suma de precios). No tiene campo de duración hoy.
- **Alta de Empresa**: `CrearEmpresaCommand` exige hoy `planId` obligatorio; `ServicioEmpresas.crearEmpresa` crea una `Suscripcion` básica activa contra ese Plan.
- **Frontend**: pantalla `features/plataforma/planes` (con `planes.ts`, `plan-dialog.ts`, `PlanesService`, `SuscripcionesService`), panel con `PlanSuscripcionDialog`, `AsignarPlanDialog`, `VigenciaDialog`, y `EmpresaDto.planVigente` enriquecido. Hoy la pantalla solo permite crear Planes.

## Glossary

- **Sistema**: la plataforma Dess-TI / plataforma-multigiro en su conjunto (backend Spring Boot, frontend Angular, base de datos PostgreSQL).
- **Super_Admin**: rol de plataforma que administra catálogos, da de alta Empresas, gestiona contratos, otorga pruebas y activa facturación.
- **Empresa**: cliente multi-tenant de la plataforma; cada Empresa corresponde a un tenant.
- **Plan**: entrada del catálogo de contratos de largo plazo (duración mayor a 1 año, sin prueba). Entidad `Plan` existente, resemantizada.
- **Paquete_Suscripcion**: entrada del **nuevo** catálogo de contratos de corto plazo (duración menor o igual a 1 año, con opción de prueba). Es una plantilla comercial, no una asignación a una Empresa. *(Nomenclatura propuesta; ver Decisiones Abiertas.)*
- **Contrato**: instancia que asigna a una Empresa un instrumento comercial vigente, referenciando **O un Plan O un Paquete_Suscripcion**, con estado y vigencia. Corresponde a la entidad `Suscripcion` actual, resemantizada. *(Nomenclatura propuesta; ver Decisiones Abiertas.)*
- **Estado_Contrato**: estado del ciclo de vida de un Contrato. Valores: `ACTIVA`, `EN_PRUEBA`, `SUSPENDIDA`, `CANCELADA`, `VENCIDA`. Amplía el enum `EstadoSuscripcion` actual.
- **Periodo_Prueba**: intervalo de tiempo, expresado en meses, durante el cual una Empresa usa la plataforma bajo un Paquete_Suscripcion sin facturación, otorgado por el Super_Admin.
- **Vigencia_Fin**: fecha de fin de vigencia de un Contrato (`vigenciaFin`); `null` significa sin fecha de fin.
- **Umbral_Aviso**: número de días de anticipación configurable a partir del cual el Sistema advierte que un Contrato está por vencer.
- **Modulos_Habilitados**: conjunto de módulos que un Plan o Paquete_Suscripcion pone a disposición de la Empresa.
- **Override_Modulos**: subconjunto de Modulos_Habilitados fijado a nivel de Contrato para una Empresa concreta.
- **Gating**: mecanismo de autorización por módulo resuelto por `PlanModulosPlanAdapter`.
- **Facturacion_Activada**: condición de un Contrato de Paquete_Suscripcion cuya prueba ha sido convertida a suscripción de pago por el Super_Admin.

## Requirements

### Requisito 1: Exclusividad Plan vs Suscripción

**Historia de Usuario:** Como Super_Admin, quiero que cada Empresa tenga exactamente un Contrato vigente que sea o un Plan o un Paquete de Suscripción, para que nunca coexistan dos instrumentos comerciales contradictorios en la misma Empresa.

#### Criterios de Aceptación

1. THE Sistema SHALL modelar cada Contrato de forma que referencie exactamente uno de los dos instrumentos: un Plan o un Paquete_Suscripcion.
2. IF una operación intenta asignar a una Empresa un Contrato que referencia simultáneamente un Plan y un Paquete_Suscripcion, THEN THE Sistema SHALL rechazar la operación con un error 422.
3. IF una operación intenta crear un segundo Contrato vigente para una Empresa que ya posee un Contrato vigente, THEN THE Sistema SHALL rechazar la operación con un error 422.
4. WHEN el Super_Admin consulta el Contrato de una Empresa, THE Sistema SHALL indicar si el instrumento vigente es un Plan o un Paquete_Suscripcion mediante su nombre, sin exponer identificadores UUID.

### Requisito 2: Catálogo de Planes (contratos mayores a un año)

**Historia de Usuario:** Como Super_Admin, quiero administrar Planes como contratos de largo plazo con duración mayor a un año y sin prueba, para ofrecer compromisos firmes facturados.

#### Criterios de Aceptación

1. THE Plan SHALL exponer los campos nombre, límite de usuarios, moneda, giro, módulos habilitados con su precio por módulo, y total calculado como la suma de los precios de sus módulos.
2. WHEN el Super_Admin crea o edita un Plan con una duración de contrato menor o igual a 365 días, THE Sistema SHALL rechazar la operación con un error 422 e indicar que un contrato de duración menor o igual a un año debe ser un Paquete_Suscripcion.
3. WHEN el Super_Admin crea o edita un Plan con una duración de contrato mayor a 365 días, THE Sistema SHALL aceptar la operación.
4. THE Plan SHALL registrarse sin atributos de periodo de prueba.
5. IF el Super_Admin intenta crear un Plan sin giro o sin moneda, THEN THE Sistema SHALL rechazar la operación con un error 422.

### Requisito 3: Catálogo de Paquetes de Suscripción (contratos de un año o menos)

**Historia de Usuario:** Como Super_Admin, quiero administrar Paquetes de Suscripción como contratos de corto plazo con duración de un año o menos y opción de prueba, para ofrecer contratación flexible y evaluación de la herramienta.

#### Criterios de Aceptación

1. THE Paquete_Suscripcion SHALL exponer sus propios campos nombre, límite de usuarios, moneda, giro, módulos habilitados y precio, de forma independiente del catálogo de Planes.
2. THE Paquete_Suscripcion SHALL exponer los atributos de prueba: si admite prueba y la duración de la prueba expresada en meses.
3. WHEN el Super_Admin crea o edita un Paquete_Suscripcion con una duración de contrato mayor a 365 días, THE Sistema SHALL rechazar la operación con un error 422 e indicar que un contrato de duración mayor a un año debe ser un Plan.
4. WHEN el Super_Admin crea o edita un Paquete_Suscripcion con una duración de contrato menor o igual a 365 días, THE Sistema SHALL aceptar la operación.
5. IF el Super_Admin configura un Paquete_Suscripcion que admite prueba con una duración de prueba menor o igual a cero meses, THEN THE Sistema SHALL rechazar la operación con un error 422.
6. IF el Super_Admin configura un Paquete_Suscripcion que admite prueba con una duración de prueba que excede la duración total del contrato, THEN THE Sistema SHALL rechazar la operación con un error 422.

### Requisito 4: Alta de Empresa con elección excluyente

**Historia de Usuario:** Como Super_Admin, quiero elegir al alta de una Empresa entre un Plan o un Paquete de Suscripción, para asignar el instrumento comercial correcto desde el inicio.

#### Criterios de Aceptación

1. WHEN el Super_Admin da de alta una Empresa indicando un Plan, THE Sistema SHALL crear la Empresa y su Contrato vigente referenciando ese Plan.
2. WHEN el Super_Admin da de alta una Empresa indicando un Paquete_Suscripcion, THE Sistema SHALL crear la Empresa y su Contrato vigente referenciando ese Paquete_Suscripcion.
3. IF el Super_Admin da de alta una Empresa sin indicar ni Plan ni Paquete_Suscripcion, THEN THE Sistema SHALL rechazar la operación con un error 422.
4. IF el Super_Admin da de alta una Empresa indicando a la vez un Plan y un Paquete_Suscripcion, THEN THE Sistema SHALL rechazar la operación con un error 422.
5. WHEN el Super_Admin da de alta una Empresa con un Paquete_Suscripcion que admite prueba y otorga el periodo de prueba, THE Sistema SHALL crear el Contrato en estado `EN_PRUEBA` con Vigencia_Fin igual a la fecha de inicio más la duración de la prueba.

### Requisito 5: Estados y transiciones del Contrato

**Historia de Usuario:** Como Super_Admin, quiero un ciclo de vida claro del Contrato con estados y transiciones definidas, para gobernar el acceso y la facturación de cada Empresa.

#### Criterios de Aceptación

1. THE Estado_Contrato SHALL admitir exactamente los valores `ACTIVA`, `EN_PRUEBA`, `SUSPENDIDA`, `CANCELADA` y `VENCIDA`.
2. WHEN un Contrato se encuentra en estado `EN_PRUEBA` y el Super_Admin activa la facturación, THE Sistema SHALL transicionar el Contrato al estado `ACTIVA`.
3. WHEN un Contrato en estado `ACTIVA` o `EN_PRUEBA` supera su Vigencia_Fin, THE Sistema SHALL considerarlo `VENCIDA` a efectos de acceso.
4. IF una operación intenta transicionar un Contrato en estado `CANCELADA` a cualquier otro estado, THEN THE Sistema SHALL rechazar la operación con un error 422.
5. WHEN el Super_Admin suspende un Contrato en estado `ACTIVA` o `EN_PRUEBA`, THE Sistema SHALL transicionar el Contrato al estado `SUSPENDIDA`.
6. THE Sistema SHALL conceder acceso a los módulos únicamente cuando el Estado_Contrato pertenece al conjunto {`ACTIVA`, `EN_PRUEBA`} y la Vigencia_Fin es nula o mayor o igual a la fecha actual.

### Requisito 6: Corte de acceso por vencimiento

**Historia de Usuario:** Como Super_Admin, quiero que el acceso se corte automáticamente cuando el Contrato vence, para que ninguna Empresa opere con un contrato caducado.

#### Criterios de Aceptación

1. WHEN el Gating resuelve los módulos habilitados de una Empresa, THE Sistema SHALL conceder acceso solo si el Contrato tiene Estado_Contrato en {`ACTIVA`, `EN_PRUEBA`} y Vigencia_Fin nula o mayor o igual a la fecha actual.
2. IF el Contrato de una Empresa tiene Estado_Contrato `ACTIVA` o `EN_PRUEBA` pero su Vigencia_Fin es anterior a la fecha actual, THEN THE Sistema SHALL denegar el acceso a todos los módulos.
3. WHEN el Contrato de una Empresa está en estado `SUSPENDIDA` o `CANCELADA`, THE Sistema SHALL denegar el acceso a todos los módulos.
4. WHEN el Gating deniega el acceso por vencimiento, THE Sistema SHALL producir el mismo resultado que ante la ausencia de Contrato vigente (cero módulos habilitados).

### Requisito 7: Aviso de "por vencer"

**Historia de Usuario:** Como Super_Admin, quiero ver en el panel los días restantes y una advertencia cuando un Contrato está por vencer, para gestionar la facturación o la conversión a tiempo.

#### Criterios de Aceptación

1. WHEN el Super_Admin consulta el listado o el panel de Empresas, THE Sistema SHALL mostrar los días restantes hasta la Vigencia_Fin de cada Contrato con Vigencia_Fin definida.
2. WHILE los días restantes de un Contrato son menores o iguales al Umbral_Aviso, THE Sistema SHALL mostrar una advertencia visual de "por vencer" para ese Contrato.
3. THE Sistema SHALL permitir al Super_Admin configurar el Umbral_Aviso en días.
4. WHERE el Contrato está en estado `EN_PRUEBA`, THE Sistema SHALL indicar en la advertencia que el periodo de prueba está por vencer y que debe gestionarse con el Super_Admin para iniciar la facturación el mes siguiente.
5. THE Sistema SHALL presentar los avisos únicamente en la interfaz de usuario, sin envío de correo electrónico, salvo que exista infraestructura de notificación disponible.

### Requisito 8: Conversión de prueba a suscripción facturada

**Historia de Usuario:** Como Super_Admin, quiero activar la facturación de una prueba para empezar a cobrar el mes siguiente, incluso de forma anticipada si el cliente ya quiere pagar, para convertir la evaluación en un contrato de pago.

#### Criterios de Aceptación

1. WHEN el Super_Admin activa la facturación de un Contrato en estado `EN_PRUEBA`, THE Sistema SHALL transicionar el Contrato al estado `ACTIVA` y marcarlo como Facturacion_Activada.
2. WHEN el Super_Admin activa la facturación sin indicar fecha de inicio de cobro, THE Sistema SHALL fijar el inicio de la facturación en el primer día del mes siguiente a la fecha actual.
3. WHEN el Super_Admin activa la facturación de forma anticipada antes de que termine el Periodo_Prueba, THE Sistema SHALL transicionar el Contrato a `ACTIVA` y ajustar la Vigencia_Fin conforme a la duración del Paquete_Suscripcion.
4. IF el Super_Admin intenta activar la facturación de un Contrato que no está en estado `EN_PRUEBA`, THEN THE Sistema SHALL rechazar la operación con un error 422.

### Requisito 9: Regla de compromiso mayor a un año implica Plan

**Historia de Usuario:** Como Super_Admin, quiero que todo compromiso mayor a un año sea un Plan, para mantener la coherencia entre duración del contrato e instrumento comercial.

#### Criterios de Aceptación

1. IF una operación intenta establecer para un Paquete_Suscripcion o su Contrato una duración total mayor a 365 días, THEN THE Sistema SHALL rechazar la operación con un error 422 e indicar que debe convertirse a Plan.
2. WHEN el Super_Admin solicita convertir un Contrato de Paquete_Suscripcion a un Plan, THE Sistema SHALL crear un Contrato de Plan para la Empresa y cerrar el Contrato de suscripción anterior de forma que se mantenga la exclusividad del Requisito 1.
3. WHILE un Contrato de Paquete_Suscripcion se aproxima a completar un año de vigencia acumulada, THE Sistema SHALL advertir al Super_Admin que debe convertirlo a Plan.

### Requisito 10: Pantalla "Planes y suscripciones"

**Historia de Usuario:** Como Super_Admin, quiero crear, editar y eliminar Planes y Paquetes de Suscripción desde una sola pantalla con dos secciones, para administrar ambos catálogos con una experiencia accesible.

#### Criterios de Aceptación

1. THE pantalla "Planes y suscripciones" SHALL presentar dos secciones diferenciadas: una para Planes y otra para Paquetes de Suscripción.
2. WHEN el Super_Admin crea, edita o elimina un Plan desde la sección de Planes, THE Sistema SHALL persistir la operación y reflejarla en el listado.
3. WHEN el Super_Admin crea, edita o elimina un Paquete_Suscripcion desde la sección de Suscripciones, THE Sistema SHALL persistir la operación y reflejarla en el listado.
4. THE formulario de Paquete_Suscripcion SHALL incluir campos para módulos, precio, límite de usuarios, moneda, giro, duración menor o igual a un año y configuración de prueba.
5. THE pantalla "Planes y suscripciones" SHALL identificar todo elemento por su nombre y no exponer identificadores UUID al usuario.
6. THE pantalla "Planes y suscripciones" SHALL cumplir el nivel WCAG AA y usar los design tokens de la plataforma.

### Requisito 11: Override de módulos como subconjunto del Contrato

**Historia de Usuario:** Como Super_Admin, quiero que el override de módulos de una Empresa sea siempre un subconjunto de los módulos del Plan o Paquete asignado, para evitar habilitar módulos no contratados.

#### Criterios de Aceptación

1. WHEN el Super_Admin fija un Override_Modulos para una Empresa, THE Sistema SHALL aceptar la operación solo si el override es un subconjunto de los Modulos_Habilitados del Plan o Paquete_Suscripcion del Contrato.
2. IF el Override_Modulos contiene algún módulo que no pertenece a los Modulos_Habilitados del instrumento del Contrato, THEN THE Sistema SHALL rechazar la operación con un error 422.
3. THE documento de requisitos SHALL señalar como nota de datos que la Empresa demo con un override de 15 módulos sobre un Plan de 4 módulos es un dato inconsistente que debe sanearse en la migración, sin cambiar el comportamiento del Gating.

### Requisito 12: Compatibilidad, no regresión y migración de datos

**Historia de Usuario:** Como Super_Admin, quiero que la ampliación del modelo no rompa los contratos existentes ni la suite de pruebas, para desplegar el cambio sin regresiones.

#### Criterios de Aceptación

1. THE Sistema SHALL ampliar el enum de estados y el modelo de datos conservando el funcionamiento de los Contratos existentes.
2. WHEN se ejecuta la migración de base de datos, THE Sistema SHALL crear el catálogo de Paquetes de Suscripción, agregar al Contrato los campos de tipo de instrumento, duración y atributos de prueba, y ampliar el CHECK del estado para incluir `en_prueba` y `vencida`.
3. WHEN se ejecuta la migración de base de datos sobre Contratos existentes sin fecha de fin, THE Sistema SHALL clasificarlos conforme a la regla de clasificación por defecto definida en el diseño.
4. THE suite de pruebas del backend SHALL permanecer en verde tras el cambio.
5. THE Sistema SHALL persistir los valores del enum de estado como etiquetas en minúsculas coherentes con el CHECK de la base de datos.
6. WHEN el Sistema enriquece los datos de una Empresa para el frontend, THE Sistema SHALL reflejar en el instrumento vigente tanto Planes como Paquetes de Suscripción, identificados por su nombre.

## Decisiones Abiertas y Riesgos

Estas decisiones deben confirmarse antes de pasar a la fase de diseño:

1. **Nomenclatura (requiere confirmación).** Se propone: catálogos `Plan` y `PaqueteSuscripcion`; instancia de asignación a la Empresa como `Contrato` (la entidad `Suscripcion` actual se resemantiza a "Contrato" y referencia O un `Plan` O un `PaqueteSuscripcion`). La nomenclatura final es una decisión de diseño; se necesita aprobación para no confundir "catálogo de suscripción" (plantilla) con "contrato de la empresa" (instancia).
2. **Mecanismo de corte por vencimiento.** Se recomienda el corte en tiempo real por consulta de fecha en el Gating (`PlanModulosPlanAdapter` evalúa estado y `vigenciaFin` en cada resolución), sin depender de un scheduler. La alternativa es un job que transicione los Contratos a `VENCIDA`. Recomendación: tiempo real por consulta; requiere confirmación.
3. **Migración de datos existentes.** Falta decidir cómo clasificar los Contratos y Planes ya creados bajo el nuevo modelo (por ejemplo, si los Contratos activos sin fecha de fin se consideran suscripción activa sin fin o se convierten en Plan). También el saneo del override inconsistente de la Empresa demo.
4. **Impacto en el alta de Empresa.** `CrearEmpresaCommand` exige hoy `planId`; debe evolucionar para aceptar Plan o Paquete_Suscripcion de forma excluyente. Hay que confirmar la forma del nuevo comando y su compatibilidad con los llamadores actuales.
5. **Enriquecimiento `EmpresaDto.planVigente`.** Debe reflejar ahora Plan o Paquete de Suscripción. Confirmar si se renombra el campo o se conserva el nombre por compatibilidad del frontend.
6. **Umbral de aviso.** El usuario mencionó "una semana antes" para el aviso al cliente y que el Super_Admin pueda gestionar "dos semanas antes o más". Falta confirmar el valor por defecto del Umbral_Aviso y si es configurable por Contrato, por Paquete o global.
7. **Alcance de notificación.** Los avisos son solo en UI salvo que exista infraestructura de notificación (módulo `notificaciones`). Confirmar si se integra correo o queda fuera de alcance en esta entrega.
