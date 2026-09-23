# Documento de Requisitos

_(Operación e Inventario como bloque dependiente — rediseño enterprise de Inventario Avanzado, dependencia de módulos y término del módulo Operación)_

## Introducción

En la plataforma Dess-TI / plataforma-multigiro (CRM enterprise multi-tenant: Spring Boot + Angular 20 + PostgreSQL) existen dos módulos que en realidad forman **un solo bloque de negocio**:

- **`operacion`** contiene el **inventario base**: los **Materiales** (catálogo de artículos) y sus movimientos base. Sin Materiales no hay nada que inventariar.
- **`inventario-avanzado`** se construye **encima de los Materiales**: almacenes multi-sitio, existencias por almacén, movimientos (entradas/salidas/transferencias), Kardex, lotes con caducidad y configuración de inventario por material (costeo, reorden, control de lote).

Hoy la plataforma permite que un plan o una suscripción tengan `inventario-avanzado` **sin** `operacion`. Ese estado deja el módulo avanzado **inutilizable**: no hay Materiales que operar, y el endpoint de creación de Materiales (`POST /api/v1/materiales`) exige el módulo `operacion` (`@autorizador.moduloHabilitado('operacion')`), por lo que responde **403** al intentar crearlos. Esta es la causa raíz de síntomas como "crear lote no crea nada" y de las pantallas vacías o pobres del inventario. El caso real es la empresa **"tester"** (id `cf1acdb6-75e6-40f4-a28d-d364142fbfbe`, plan "Plan Demo Factura", giro `anuncios-luminosos`), cuyos módulos hoy son `["comercial","inventario-avanzado","estrategia","redes-sociales"]` — con `inventario-avanzado` pero **sin** `operacion`.

El usuario ya tomó la decisión de negocio (**"Opción A"**): `inventario-avanzado` es un **bloque dependiente** de `operacion`. Cada módulo conserva su propia vista y función, pero `inventario-avanzado` no puede quedar activo sin `operacion`.

Esta especificación cubre **tres bloques de trabajo**, en este **orden de prioridad** que el usuario definió:

1. **Rediseño enterprise de Inventario Avanzado (frontend)** — terminar primero la interfaz del módulo avanzado: descriptiva, informativa, con navegación clara (no amontonada), a la altura de los mejores inventarios.
2. **Dependencia de módulos (backend + frontend + UX de super_admin)** — activar `inventario-avanzado` activa automáticamente `operacion`; avisar al super_admin; y dejar consistentes los planes/suscripciones existentes que ya tienen el estado roto.
3. **Terminar el módulo Operación (Materiales / inventario base)** — dejar el inventario base con el mismo estándar enterprise y coherente con Inventario Avanzado.

Se preservan las reglas de la plataforma: multi-tenant con aislamiento por Empresa, RBAC por permiso atómico y por módulo contratado (deny-by-default), **UI en español (es-MX)**, responsiva, **WCAG AA**, **solo design tokens** (sin colores/espaciados hardcodeados) y **sin exponer UUIDs** al usuario (ya existe `NombresInventarioService` que resuelve id→nombre).

### Contexto técnico ya resuelto (no rehacer; solo referencia)

- **Bug HTTP 500 al crear almacén / lote / configuración / movimientos.** Su causa era que las columnas JSONB de auditoría `valor_anterior` / `valor_nuevo` recibían texto plano no-JSON (PostgreSQL error `22P02`). **Ya está corregido de raíz** en `backend/.../operacion/inventario/avanzado/application/ServicioInventarioAvanzado.java` (el helper `auditar` coacciona a JSON válido con el método `aJson`, siguiendo el precedente de `ServicioBrandingTest`). Verificado contra la BD real (crear/actualizar almacén devuelven 201/200 y persisten JSON válido). **No es una tarea pendiente**; se menciona solo como contexto de por qué antes fallaba.
- **Acceso del admin al módulo Inventario.** Ya se resolvió con `guardaModuloAlguno('operacion','inventario-avanzado')` en la ruta padre. Una vez implementada la dependencia (Bloque 2), esa lógica **podría** simplificarse; se captura como consideración sin romper lo existente.

## Glosario

- **Bloque Operación-Inventario:** conjunto de negocio formado por los módulos `operacion` (inventario base / Materiales) e `inventario-avanzado`, donde el segundo depende del primero.
- **Módulo `operacion`:** módulo que habilita el inventario base y el catálogo de **Materiales**.
- **Módulo `inventario-avanzado`:** módulo que habilita almacenes, existencias, movimientos, Kardex, lotes y configuración por material, operando sobre los Materiales de `operacion`.
- **Dependencia de módulo:** relación declarada según la cual activar un módulo (dependiente) obliga a activar otro módulo (requerido). Aquí: `inventario-avanzado` **requiere** `operacion`.
- **Material:** artículo de inventario del módulo `operacion`; el inventario avanzado opera SOBRE Materiales existentes.
- **Almacén:** sucursal o bodega donde se guardan existencias (módulo `inventario-avanzado`).
- **Existencia por Almacén:** saldo vivo (cantidad + costo promedio) de un Material en un Almacén.
- **Movimiento:** entrada, salida, ajuste o transferencia que altera existencias y costo.
- **Kardex:** historial cronológico append-only de movimientos de un Material en un Almacén, con saldo tras cada movimiento.
- **Lote:** agrupación de un Material con código y caducidad opcional (trazabilidad).
- **Configuración de inventario por Material:** método de costeo, stock máximo, control de lote y parámetros de punto de reorden.
- **`plan.modulos_habilitados`:** columna `jsonb` del Plan que lista las claves de módulo del plan.
- **`suscripcion.modulos_habilitados`:** columna `jsonb` de la Suscripción que lista las claves de módulo efectivas de la empresa.
- **Claim de módulos (JWT):** conjunto de módulos efectivos que viaja en el token y gobierna el gating en frontend y backend.
- **super_admin:** rol con ámbito de plataforma que configura planes y suscripciones.
- **admin de empresa:** rol con ámbito de una Empresa que opera los módulos contratados.
- **Design token:** variable del sistema de diseño (color, espaciado, tipografía, radios, sombras) que la UI DEBE usar en lugar de valores hardcodeados.
- **`NombresInventarioService`:** servicio de frontend existente que resuelve identificadores técnicos (UUID) a nombres legibles.
- **WCAG AA:** nivel de conformidad de accesibilidad exigido por la plataforma.
- **Componentes compartidos de estado:** componentes reutilizables de carga, vacío y error del sistema de diseño.

## Requisitos

---

## BLOQUE 1 — Rediseño enterprise de Inventario Avanzado (frontend, prioridad 1)

### Requisito 1: Navegación clara y no amontonada del Inventario Avanzado

**Historia de usuario:** Como responsable de almacén, quiero navegar el Inventario Avanzado entre sus secciones de forma clara y sin elementos encimados, para operar el inventario de manera profesional y sin confusión.

#### Criterios de aceptación

1. THE componente `inventario-avanzado` SHALL organizar sus contenidos en las secciones Resumen, Existencias, Movimientos, Kardex, Lotes, Configuración y Almacenes mediante un patrón de navegación único y consistente (por ejemplo pestañas o acordeón; la elección concreta se define en diseño).
2. WHEN el usuario cambia de sección, THE componente `inventario-avanzado` SHALL mostrar únicamente el contenido de la sección seleccionada, sin superponer visualmente el contenido de otras secciones.
3. THE componente `inventario-avanzado` SHALL mostrar cada sección disponible con una etiqueta descriptiva en es-MX que identifique su propósito.
4. WHERE la ventana tiene ancho reducido (viewport móvil), THE componente `inventario-avanzado` SHALL mantener la navegación entre secciones usable, con controles alcanzables y sin recorte de contenido.
5. THE componente `inventario-avanzado` SHALL exponer los controles de navegación de secciones como elementos operables por teclado y con nombre accesible conforme a WCAG AA.

### Requisito 2: Tab de Movimientos legible (corregir el estado "encimado")

**Historia de usuario:** Como operador de almacén, quiero registrar entradas, salidas y transferencias con formularios legibles y botones visibles, para mover inventario sin que la pantalla se vea encimada ni con botones cortados.

#### Criterios de aceptación

1. THE sección Movimientos SHALL presentar las acciones de entrada, salida y transferencia sin apilar formularios superpuestos, de modo que cada formulario y sus campos sean visibles de forma independiente.
2. THE sección Movimientos SHALL mostrar cada botón de acción completo y no recortado, con su etiqueta legible en es-MX.
3. THE sección Movimientos SHALL espaciar y agrupar sus formularios usando exclusivamente design tokens de espaciado, sin valores hardcodeados.
4. WHEN el usuario abre la sección Movimientos, THE sección Movimientos SHALL indicar claramente qué tipo de movimiento está capturando en cada momento (entrada, salida o transferencia).
5. WHERE el viewport es reducido, THE sección Movimientos SHALL reorganizar los formularios de forma que ningún botón ni campo quede recortado.

### Requisito 3: Diseño descriptivo e informativo del Inventario Avanzado (estándar enterprise)

**Historia de usuario:** Como responsable de almacén, quiero un inventario con información rica y descriptiva (indicadores, contexto y estados claros), para entender el estado del inventario de un vistazo como en los mejores sistemas.

#### Criterios de aceptación

1. THE sección Resumen SHALL mostrar indicadores útiles del inventario (por ejemplo número de almacenes, materiales con stock bajo, materiales por reabastecer y valor total del inventario) cuando los datos estén disponibles.
2. WHERE un indicador no tiene datos disponibles, THE sección Resumen SHALL mostrar un estado vacío descriptivo en lugar de un valor en blanco o cero engañoso.
3. THE componente `inventario-avanzado` SHALL usar exclusivamente design tokens para color, espaciado, tipografía, radios y sombras, sin valores hardcodeados.
4. THE componente `inventario-avanzado` SHALL usar los componentes compartidos de estado para carga, vacío y error en cada sección.
5. THE componente `inventario-avanzado` SHALL presentar todo su texto en es-MX.
6. THE componente `inventario-avanzado` SHALL cumplir WCAG AA en contraste, foco visible, orden de tabulación y nombres accesibles.

### Requisito 4: Cero UUIDs visibles en Inventario Avanzado

**Historia de usuario:** Como usuario del inventario, quiero ver y elegir almacenes, materiales y lotes por su nombre o código, para no tener que leer ni teclear identificadores técnicos.

#### Criterios de aceptación

1. THE componente `inventario-avanzado` SHALL mostrar Almacenes, Materiales y Lotes por su nombre o código legible, resolviendo los identificadores mediante `NombresInventarioService`.
2. THE componente `inventario-avanzado` SHALL permitir seleccionar Almacén, Material y Lote mediante selectores por nombre, sin pedir que el usuario teclee un UUID.
3. THE sección Existencias SHALL mostrar el nombre del Almacén y del Material en cada fila, sin exponer sus identificadores.
4. THE sección Kardex SHALL permitir elegir Almacén y Material por selector, sin exigir teclear identificadores.
5. IF un identificador no puede resolverse a nombre, THEN THE componente `inventario-avanzado` SHALL mostrar un texto de respaldo legible en es-MX en lugar del UUID crudo.

### Requisito 5: Secciones de Lotes, Configuración y Almacenes con diseño enterprise

**Historia de usuario:** Como responsable de almacén, quiero que las secciones de Lotes, Configuración y Almacenes tengan un diseño consistente, descriptivo y usable, para gestionarlas sin fricción.

#### Criterios de aceptación

1. THE sección Lotes SHALL listar los lotes de un Material elegido por selector, mostrando código y caducidad cuando exista, e indicar de forma visible los lotes próximos a caducar o caducados.
2. THE sección Configuración SHALL presentar la configuración por Material (método de costeo con etiquetas en es-MX, stock máximo, control de lote y parámetros de punto de reorden) con validación de rangos no negativos antes de enviar.
3. THE sección Almacenes SHALL listar y permitir gestionar los almacenes mostrando su nombre y datos descriptivos, sin exponer UUIDs.
4. THE Lotes, Configuración y Almacenes SHALL compartir el mismo lenguaje visual (tokens, tipografía, componentes de estado) que el resto del Inventario Avanzado.
5. IF una operación de crear o actualizar en Lotes, Configuración o Almacenes es rechazada por el backend, THEN THE sección correspondiente SHALL mostrar un mensaje de error claro en es-MX conservando los datos capturados por el usuario.

---

## BLOQUE 2 — Dependencia de módulos `inventario-avanzado` → `operacion` (prioridad 2)

### Requisito 6: Declaración de la dependencia de módulos

**Historia de usuario:** Como plataforma, quiero declarar que `inventario-avanzado` requiere `operacion`, para que la relación de dependencia sea una regla explícita y única del sistema.

#### Criterios de aceptación

1. THE plataforma SHALL declarar que el módulo `inventario-avanzado` requiere el módulo `operacion` como dependencia.
2. THE plataforma SHALL exponer la dependencia de forma consultable para el frontend, de modo que la UX de configuración pueda informarla sin datos hardcodeados en la vista.
3. WHERE existan otras dependencias entre módulos en el futuro, THE mecanismo de dependencia SHALL admitir declararlas sin cambiar la regla ya definida para `inventario-avanzado`.

### Requisito 7: Activar `inventario-avanzado` activa `operacion` en Plan

**Historia de usuario:** Como super_admin, quiero que al habilitar `inventario-avanzado` en un plan se habilite automáticamente `operacion`, para que el plan nunca quede en un estado dependiente incompleto.

#### Criterios de aceptación

1. WHEN un plan se crea o actualiza con `inventario-avanzado` en `plan.modulos_habilitados` y sin `operacion`, THE ServicioPlanes SHALL agregar `operacion` a `plan.modulos_habilitados` antes de persistir.
2. WHEN un plan se persiste con `inventario-avanzado`, THE ServicioPlanes SHALL garantizar que `operacion` esté presente en `plan.modulos_habilitados`.
3. THE ServicioPlanes SHALL agregar `operacion` sin duplicar la clave si ya está presente y sin alterar el resto de los módulos del plan.
4. IF un plan tiene `operacion` pero no `inventario-avanzado`, THEN THE ServicioPlanes SHALL conservar el plan sin agregar `inventario-avanzado` (la dependencia es unidireccional).

### Requisito 8: Activar `inventario-avanzado` activa `operacion` en Suscripción

**Historia de usuario:** Como super_admin, quiero que al habilitar `inventario-avanzado` en una suscripción se habilite automáticamente `operacion`, para que los módulos efectivos de la empresa sean siempre consistentes.

#### Criterios de aceptación

1. WHEN una suscripción se crea o actualiza con `inventario-avanzado` en `suscripcion.modulos_habilitados` y sin `operacion`, THE ServicioSuscripciones SHALL agregar `operacion` a `suscripcion.modulos_habilitados` antes de persistir.
2. WHEN se calcula el claim de módulos del JWT para una empresa cuya suscripción incluye `inventario-avanzado`, THE plataforma SHALL incluir `operacion` en el conjunto de módulos efectivos.
3. THE ServicioSuscripciones SHALL agregar `operacion` sin duplicar la clave y sin alterar el resto de los módulos de la suscripción.
4. WHILE una suscripción incluye `inventario-avanzado`, THE plataforma SHALL mantener `operacion` presente en los módulos efectivos de la empresa.

### Requisito 9: Aviso al super_admin sobre la dependencia

**Historia de usuario:** Como super_admin, quiero que al configurar un plan o suscripción se me informe que `inventario-avanzado` es un bloque con dependencia y que se activará también `operacion`, para tomar la decisión con información completa.

#### Criterios de aceptación

1. WHEN el super_admin selecciona `inventario-avanzado` al configurar un plan o una suscripción, THE UI de configuración SHALL mostrar un aviso en es-MX indicando que `inventario-avanzado` depende de `operacion` y que `operacion` se activará también.
2. THE UI de configuración SHALL reflejar `operacion` como módulo activado cuando `inventario-avanzado` esté seleccionado.
3. IF el super_admin intenta deseleccionar `operacion` mientras `inventario-avanzado` sigue seleccionado, THEN THE UI de configuración SHALL impedir dejar `inventario-avanzado` sin `operacion` y explicar el motivo en es-MX.
4. THE aviso de dependencia SHALL derivarse de la dependencia declarada (Requisito 6), sin listas de módulos hardcodeadas en la vista.

### Requisito 10: Backfill de planes y suscripciones existentes

**Historia de usuario:** Como plataforma, quiero corregir los planes y suscripciones que hoy tienen `inventario-avanzado` sin `operacion`, para que el estado roto existente (como el de la empresa "tester") quede consistente.

#### Criterios de aceptación

1. THE plataforma SHALL agregar `operacion` a todo plan cuyo `plan.modulos_habilitados` contenga `inventario-avanzado` y no contenga `operacion`.
2. THE plataforma SHALL agregar `operacion` a toda suscripción cuyo `suscripcion.modulos_habilitados` contenga `inventario-avanzado` y no contenga `operacion`.
3. WHEN el backfill se aplica sobre la suscripción de la empresa "tester" (id `cf1acdb6-75e6-40f4-a28d-d364142fbfbe`), THE resultado SHALL incluir `operacion` junto con sus módulos actuales `comercial`, `inventario-avanzado`, `estrategia` y `redes-sociales`.
4. THE backfill SHALL ser idempotente: aplicarlo más de una vez SHALL producir el mismo resultado sin duplicar la clave `operacion`.
5. THE backfill SHALL preservar todos los demás módulos y datos de cada plan y suscripción sin modificarlos.

### Requisito 11: Habilitación efectiva de creación de Materiales para el tester

**Historia de usuario:** Como admin de la empresa "tester", quiero poder crear Materiales una vez aplicada la dependencia, para que el Inventario Avanzado deje de estar inutilizable.

#### Criterios de aceptación

1. WHILE la empresa tiene `inventario-avanzado` activo y, por dependencia, `operacion` activo, THE endpoint `POST /api/v1/materiales` SHALL autorizar la creación de Materiales para un usuario con el permiso `material:crear`.
2. WHEN el admin de la empresa "tester" crea un Material tras aplicarse la dependencia, THE endpoint `POST /api/v1/materiales` SHALL responder con éxito en lugar de 403 por módulo no habilitado.
3. IF un usuario carece del permiso `material:crear`, THEN THE endpoint `POST /api/v1/materiales` SHALL denegar la operación con el código de error de autorización correspondiente, con independencia de la dependencia de módulos.

---

## BLOQUE 3 — Terminar el módulo Operación (Materiales / inventario base, prioridad 3)

### Requisito 12: Vista de Materiales con estándar enterprise

**Historia de usuario:** Como responsable de operación, quiero una vista de Materiales descriptiva, informativa y completa, para gestionar el catálogo base del inventario con el mismo nivel que Inventario Avanzado.

#### Criterios de aceptación

1. THE componente `materiales` SHALL listar los Materiales mostrando datos descriptivos legibles (por ejemplo nombre, código, unidad y estado) sin exponer UUIDs.
2. THE componente `materiales` SHALL permitir crear y editar un Material mediante `MaterialesService` sobre los endpoints `/materiales` existentes, respetando los permisos `material:crear` y `material:listar`.
3. THE componente `materiales` SHALL mostrar los movimientos base de un Material consultando `/materiales/{id}/movimientos`, presentando los datos por nombre y no por identificador.
4. THE componente `materiales` SHALL usar los componentes compartidos de estado para carga, vacío y error.
5. IF una operación sobre Materiales es rechazada por el backend, THEN THE componente `materiales` SHALL mostrar un mensaje de error claro en es-MX conservando los datos capturados.

### Requisito 13: Coherencia visual y de navegación entre Operación e Inventario Avanzado

**Historia de usuario:** Como usuario del bloque Operación-Inventario, quiero que Materiales e Inventario Avanzado compartan lenguaje visual y navegación, para percibir un bloque único y coherente.

#### Criterios de aceptación

1. THE componente `materiales` SHALL usar el mismo lenguaje visual (design tokens, tipografía y componentes de estado) que el componente `inventario-avanzado`.
2. THE componente `materiales` SHALL presentar todo su texto en es-MX y cumplir WCAG AA en contraste, foco visible, orden de tabulación y nombres accesibles.
3. THE bloque Operación-Inventario SHALL ofrecer una navegación clara entre Materiales (inventario base) y las secciones del Inventario Avanzado, sin exponer UUIDs.
4. THE componente `materiales` SHALL usar exclusivamente design tokens para color, espaciado, tipografía, radios y sombras, sin valores hardcodeados.

### Requisito 14: Materiales como base única del Inventario Avanzado

**Historia de usuario:** Como usuario enterprise, quiero que el Inventario Avanzado opere sobre los Materiales del módulo Operación y no sobre un catálogo paralelo, para que el inventario sea trazable de punta a punta.

#### Criterios de aceptación

1. THE Inventario Avanzado SHALL usar los Materiales del módulo `operacion` como catálogo único de materiales en sus selectores.
2. WHEN no existe ningún Material dado de alta, THE secciones del Inventario Avanzado que dependen de Materiales SHALL mostrar un estado vacío descriptivo en es-MX que oriente a crear Materiales primero.
3. THE bloque Operación-Inventario SHALL evitar duplicar catálogos de materiales entre `operacion` e `inventario-avanzado`.

---

## Requisito 15: Calidad y no regresión

**Historia de usuario:** Como responsable técnico, quiero que los cambios compilen, no rompan lo existente y respeten las reglas de la plataforma, para entregar de raíz y sin parches.

#### Criterios de aceptación

1. THE cambios SHALL compilar en backend y frontend sin errores.
2. THE suite de pruebas existente SHALL permanecer en verde, agregando pruebas para la dependencia de módulos, el backfill y las nuevas vistas.
3. THE UI SHALL respetar design tokens, ser responsiva, cumplir WCAG AA, estar en es-MX y no exponer UUIDs.
4. THE cambios SHALL preservar el comportamiento y los contratos ya verificados del backend (incluido el fix de auditoría JSONB ya aplicado).
5. WHILE se implementa la dependencia de módulos, THE guardas de acceso existentes al Inventario SHALL seguir funcionando sin romper el acceso actual del admin.
