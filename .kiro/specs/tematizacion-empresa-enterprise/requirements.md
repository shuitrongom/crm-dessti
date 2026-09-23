# Requirements Document

## Introduction

Esta funcionalidad combina dos objetivos para el CRM multi-tenant "Dess-TI / plataforma-multigiro" (Spring Boot + Angular 20 + PostgreSQL):

1. **Tematización por empresa (multi-tenant).** El administrador de cada empresa (permiso `branding:actualizar`) elige un único **color primario de marca** desde la vista existente `/empresa/administracion/branding`. La aplicación **deriva automáticamente** una paleta coherente (hover, container, texto-sobre-primario, anillo de foco, superficies de acento) **garantizando contraste WCAG 2.1 AA**, y la aplica en tiempo de ejecución sobre las CSS custom properties del Sistema de Diseño existente (`--ds-color-primary` y derivados). Funciona en modo claro y oscuro, ofrece presets además del selector libre, y persiste el color en el backend por tenant con RLS, RBAC y auditoría.

2. **Rediseño visual enterprise global.** Se eleva la jerarquía y la consistencia visual reutilizando (no reescribiendo) el sistema de tokens existente: `PageHeader` con acento de marca, un patrón reutilizable de chip de estado con color semántico (hoy todos gris neutro), tarjetas más pulidas, y su aplicación en las cuatro vistas de detalle del módulo Operación (Orden de Fabricación, Levantamiento, Permiso de Instalación, Orden de Trabajo de Instalación) y sus listas.

La tematización por color es exclusiva del ámbito de cada empresa. La plataforma (super_admin / Dess-TI) conserva su tema corporativo fijo. Todos los textos son es-MX, se cumple WCAG 2.1 AA, el aislamiento multi-tenant se realiza por RLS y `TenantContext`, la autorización es deny-by-default y no se exponen UUIDs en la interfaz.

## Glossary

- **Sistema**: la aplicación CRM completa (backend Spring Boot + frontend Angular) salvo que se nombre un subsistema específico.
- **Servicio_Tematizacion**: servicio frontend (Angular) que resuelve, deriva y aplica en runtime el color de marca y su paleta sobre las CSS custom properties del documento.
- **Derivador_Paleta**: unidad de lógica pura que, dado un color primario válido, calcula la paleta derivada (hover, container, texto-sobre-primario, anillo de foco, superficies de acento) para los modos claro y oscuro.
- **Servicio_Branding**: servicio de aplicación backend (`ServicioBranding`) que consulta y actualiza el branding de la empresa del contexto autenticado.
- **Controlador_Branding**: adaptador REST backend (`BrandingController`) bajo `/empresa/branding`.
- **Servicio_Carga_Branding**: flujo frontend existente (`BrandingService` + `shell-layout`) que carga perezosamente el branding del tenant al entrar al ámbito empresa vía `GET /empresa/branding`.
- **Color_Primario_Marca**: color de marca elegido por la empresa, expresado como cadena hexadecimal con formato `#RRGGBB`.
- **Paleta_Derivada**: conjunto de tokens de color coherentes calculados a partir del Color_Primario_Marca por el Derivador_Paleta.
- **Tema_Corporativo**: valores por defecto de los tokens de color definidos en `_tokens.scss` (primario `#35507a` y derivados), usados cuando la empresa no ha fijado color o cuando la carga falla.
- **Preset_Color**: opción de color predefinida ofrecida en la interfaz además del selector libre.
- **Token_CSS**: CSS custom property del Sistema de Diseño (p. ej. `--ds-color-primary`, `--ds-color-primary-hover`, `--ds-color-text-on-primary`, `--ds-color-focus-ring`).
- **Contraste_AA**: relación de contraste que cumple WCAG 2.1 AA: ≥ 4.5:1 para texto normal y ≥ 3:1 para texto grande y componentes de interfaz.
- **Ambito_Empresa**: contexto de ejecución del frontend en el que el usuario autenticado opera dentro de una empresa (tenant), por oposición al ámbito de plataforma del super_admin.
- **Tenant**: empresa; en este dominio la Empresa es el tenant y se resuelve con `TenantContext.require()`.
- **Chip_Estado**: componente/patrón de etiqueta compacta que representa un estado con color semántico (éxito, advertencia, error, información o neutro).
- **PageHeader**: componente compartido de encabezado de página (`shared/components/page-header`).
- **Vistas_Operacion**: las cuatro vistas de detalle del módulo Operación: Orden de Fabricación (OF), Levantamiento de Sitio, Permiso de Instalación, y Orden de Trabajo de Instalación (OTI), junto con sus listas.

## Requirements

### Requirement 1: Selección del color de marca por la empresa

**User Story:** Como administrador de empresa con permiso `branding:actualizar`, quiero elegir un color primario de marca desde la vista de branding, para que la interfaz de mi empresa refleje la identidad visual de mi organización.

#### Acceptance Criteria

1. WHERE el usuario autenticado posee el permiso `branding:actualizar`, THE Sistema SHALL mostrar en la vista `/empresa/administracion/branding` un selector de color y un conjunto de Preset_Color.
2. WHEN el usuario selecciona un Preset_Color, THE Sistema SHALL fijar el Color_Primario_Marca al valor hexadecimal del preset seleccionado.
3. WHEN el usuario introduce un color mediante el selector libre, THE Sistema SHALL fijar el Color_Primario_Marca al valor hexadecimal introducido.
4. IF el valor de color introducido no cumple el formato `#RRGGBB`, THEN THE Sistema SHALL rechazar el valor y mostrar un mensaje de validación en es-MX sin modificar el Color_Primario_Marca vigente.
5. WHILE el usuario ajusta el Color_Primario_Marca antes de guardar, THE Servicio_Tematizacion SHALL aplicar una previsualización en vivo de la Paleta_Derivada sobre los Token_CSS del documento.
6. WHEN el usuario confirma el guardado del Color_Primario_Marca, THE Servicio_Branding SHALL persistir el color para el tenant del contexto autenticado.
7. WHEN el usuario solicita limpiar el color de marca, THE Servicio_Branding SHALL eliminar el Color_Primario_Marca del tenant y THE Servicio_Tematizacion SHALL restaurar los Token_CSS al Tema_Corporativo.
8. IF el usuario carece del permiso `branding:actualizar`, THEN THE Sistema SHALL impedir la modificación del Color_Primario_Marca.

### Requirement 2: Derivación automática de la paleta con contraste AA

**User Story:** Como administrador de empresa, quiero que la aplicación derive toda la paleta a partir de un solo color primario, para no tener que elegir colores sueltos y para garantizar que el resultado sea legible y accesible.

#### Acceptance Criteria

1. WHEN se establece un Color_Primario_Marca válido, THE Derivador_Paleta SHALL calcular una Paleta_Derivada que incluya al menos: primario, primario-hover, primario-container, texto-sobre-primario y anillo-de-foco.
2. FOR ALL Color_Primario_Marca válido, THE Derivador_Paleta SHALL seleccionar el valor de texto-sobre-primario cuyo contraste con el primario sea Contraste_AA para texto normal.
3. WHEN el Derivador_Paleta calcula el color primario-hover, THE Derivador_Paleta SHALL producir un valor derivado de forma determinista del Color_Primario_Marca.
4. WHEN el Derivador_Paleta calcula el color primario-container, THE Derivador_Paleta SHALL producir un valor cuyo par de texto asociado cumpla Contraste_AA.
5. FOR ALL Color_Primario_Marca válido y para cada modo (claro y oscuro), THE Derivador_Paleta SHALL producir una Paleta_Derivada cuyos pares texto/superficie de acento cumplan Contraste_AA.
6. WHEN se aplica dos veces consecutivas el mismo Color_Primario_Marca, THE Derivador_Paleta SHALL producir la misma Paleta_Derivada (determinismo).

### Requirement 3: Aplicación en runtime a los tokens CSS

**User Story:** Como usuario de una empresa con color de marca, quiero que el color se aplique inmediatamente sin recargar ni recompilar, para tener una experiencia fluida y coherente.

#### Acceptance Criteria

1. WHEN el Servicio_Tematizacion recibe una Paleta_Derivada, THE Servicio_Tematizacion SHALL escribir cada valor de la Paleta_Derivada en su Token_CSS correspondiente del elemento raíz del documento.
2. WHEN el modo de tema activo es claro, THE Servicio_Tematizacion SHALL aplicar la variante clara de la Paleta_Derivada.
3. WHEN el modo de tema activo es oscuro, THE Servicio_Tematizacion SHALL aplicar la variante oscura de la Paleta_Derivada.
4. WHEN el usuario conmuta entre modo claro y oscuro con un Color_Primario_Marca activo, THE Servicio_Tematizacion SHALL recalcular y aplicar la variante correspondiente sin recargar la aplicación.
5. WHEN el Servicio_Tematizacion limpia el color de marca, THE Servicio_Tematizacion SHALL eliminar las sobrescrituras de Token_CSS de modo que el documento vuelva a resolver los valores del Tema_Corporativo.

### Requirement 4: Carga del color de marca por tenant

**User Story:** Como usuario que entra al ámbito de mi empresa, quiero que el color de marca de mi empresa se cargue automáticamente, para ver la interfaz tematizada desde el inicio de la sesión.

#### Acceptance Criteria

1. WHEN el frontend entra al Ambito_Empresa, THE Servicio_Carga_Branding SHALL solicitar el branding del tenant vía `GET /empresa/branding`.
2. WHEN el branding recibido contiene un Color_Primario_Marca, THE Servicio_Tematizacion SHALL derivar y aplicar la Paleta_Derivada correspondiente.
3. IF el branding recibido no contiene Color_Primario_Marca, THEN THE Servicio_Tematizacion SHALL mantener el Tema_Corporativo.
4. IF la carga del branding falla, THEN THE Servicio_Tematizacion SHALL mantener el Tema_Corporativo y THE Sistema SHALL continuar operando.
5. THE Servicio_Carga_Branding SHALL resolver el Color_Primario_Marca correspondiente al Tenant del contexto autenticado.

### Requirement 5: Alcance de tematización por ámbito

**User Story:** Como responsable de la plataforma, quiero que el color por empresa no afecte el ámbito de plataforma, para preservar la identidad corporativa de Dess-TI.

#### Acceptance Criteria

1. WHILE el usuario opera en el ámbito de plataforma (super_admin), THE Servicio_Tematizacion SHALL aplicar el Tema_Corporativo sin color de empresa.
2. FOR ALL par de tenants distintos, THE Servicio_Carga_Branding SHALL aplicar únicamente el Color_Primario_Marca del Tenant resuelto por `TenantContext`.
3. WHERE el portal del cliente está habilitado, THE Servicio_Tematizacion MAY heredar el Color_Primario_Marca de la empresa a la que pertenece el cliente. (Requisito opcional secundario.)

### Requirement 6: Persistencia backend del color de marca

**User Story:** Como administrador de empresa, quiero que mi color de marca quede guardado de forma segura y auditable, para que persista entre sesiones y quede registrado quién lo cambió.

#### Acceptance Criteria

1. THE Sistema SHALL incorporar mediante la migración Flyway `V67` una columna para el Color_Primario_Marca en la tabla de empresa, con valor por defecto nulo.
2. THE Controlador_Branding SHALL exponer en la respuesta de `GET /empresa/branding` el Color_Primario_Marca vigente del tenant.
3. WHEN el Controlador_Branding recibe una solicitud de actualización con permiso `branding:actualizar`, THE Servicio_Branding SHALL persistir el Color_Primario_Marca para el Tenant resuelto por `TenantContext.require()`.
4. IF el Color_Primario_Marca recibido no cumple el formato `#RRGGBB`, THEN THE Servicio_Branding SHALL rechazar la solicitud con un código de error de validación (HTTP 422).
5. WHEN el Servicio_Branding actualiza el Color_Primario_Marca, THE Servicio_Branding SHALL registrar un evento de auditoría de tenant con actor, acción `actualizar` y recurso `branding` a través del camino central de auditoría.
6. THE Controlador_Branding SHALL derivar el Tenant del contexto autenticado y no aceptar el identificador de tenant en el cuerpo ni en la ruta de la solicitud.
7. THE Sistema SHALL preservar los campos de branding existentes (nombre visible y logotipo) al incorporar el Color_Primario_Marca.

### Requirement 7: Rediseño enterprise global

**User Story:** Como usuario del CRM, quiero una interfaz más pulida y con mejor jerarquía visual, para trabajar de forma más clara y con una percepción de producto profesional.

#### Acceptance Criteria

1. THE Sistema SHALL reutilizar los Token_CSS existentes del Sistema de Diseño sin reescribir el sistema de tokens.
2. WHEN se renderiza un PageHeader, THE PageHeader SHALL mostrar un acento visual basado en el Token_CSS de color primario.
3. THE Sistema SHALL proveer un patrón reutilizable de Chip_Estado que asigne color semántico (éxito, advertencia, error, información o neutro) según el estado representado.
4. WHEN se renderiza un Chip_Estado para un estado con semántica definida, THE Chip_Estado SHALL usar el Token_CSS semántico correspondiente en lugar de un color gris neutro.
5. THE Sistema SHALL aplicar el patrón de Chip_Estado y el estilo de tarjeta refinado en las cuatro Vistas_Operacion y en sus listas.
6. WHEN se refinan los Token_CSS del Tema_Corporativo, THE Sistema SHALL mantener Contraste_AA en todos los pares texto/superficie afectados.

### Requirement 8: Accesibilidad con color dinámico

**User Story:** Como usuario que depende de accesibilidad, quiero que el color dinámico no rompa la legibilidad ni el enfoque, para poder usar el CRM sin barreras.

#### Acceptance Criteria

1. FOR ALL Color_Primario_Marca aplicado, THE Sistema SHALL mantener Contraste_AA entre el texto-sobre-primario y el color primario.
2. WHEN un elemento recibe foco de teclado, THE Sistema SHALL mostrar un anillo de foco visible usando el Token_CSS de anillo-de-foco derivado.
3. WHERE el usuario ha indicado `prefers-reduced-motion`, THE Sistema SHALL suprimir o reducir las animaciones no esenciales al aplicar la tematización.
4. WHEN se aplica una Paleta_Derivada en modo oscuro, THE Sistema SHALL mantener Contraste_AA entre el texto y las superficies de acento en ese modo.

### Requirement 9: No regresión

**User Story:** Como usuario existente del CRM, quiero que la nueva tematización no rompa funcionalidades actuales, para no perder capacidades que ya uso.

#### Acceptance Criteria

1. WHEN una empresa no ha fijado Color_Primario_Marca, THE Sistema SHALL comportarse igual que antes de esta funcionalidad respecto a la apariencia por defecto.
2. THE Sistema SHALL conservar el funcionamiento del nombre visible y del logotipo en la vista de branding y en el shell.
3. WHEN se aplica o se limpia el Color_Primario_Marca, THE Servicio_Tematizacion SHALL dejar los Token_CSS de tipografía, espaciado, radios, sombras y animación sin modificar.
4. THE Sistema SHALL mantener el comportamiento del shell y de la navegación existentes tras aplicar el color de marca.
