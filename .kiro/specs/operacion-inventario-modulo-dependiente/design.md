# Documento de Diseño

_(Operación e Inventario como bloque dependiente — rediseño enterprise de Inventario Avanzado, dependencia de módulos `inventario-avanzado` → `operacion`, y término del módulo Operación/Materiales)_

## Overview

Este diseño resuelve, de raíz y sin parches, tres bloques de trabajo sobre el bloque de negocio **Operación-Inventario** de la plataforma Dess-TI (Spring Boot + Angular 20 + PostgreSQL, multi-tenant con RLS y RBAC deny-by-default):

1. **Frontend Inventario Avanzado (prioridad 1):** el componente `inventario-avanzado` ya existe y está funcionalmente completo (7 secciones cableadas al backend con resolución de nombres sin UUIDs). El problema real es de **layout y jerarquía de navegación**: el tab de Movimientos apila **tres formularios** (Entrada, Salida, Transferencia) en un grid `repeat(auto-fit, minmax(300px, 1fr))` que, al no caber en una sola fila, se ven "encimados" y con botones cortados. El rediseño reorganiza la navegación y convierte Movimientos en **un solo formulario con selector de tipo de movimiento**, sin tocar la lógica de signals ni los servicios.
2. **Dependencia de módulos (prioridad 2):** hoy un Plan/Paquete/Suscripción puede tener `inventario-avanzado` sin `operacion`, dejando el módulo inutilizable (el `POST /api/v1/materiales` exige `operacion` y responde 403). Se declara la dependencia como **regla única y consultable**, se normaliza en la persistencia de Planes, Paquetes y Suscripciones, se avisa y bloquea la deselección en la UI del super_admin, y se hace **backfill idempotente** de planes y suscripciones existentes.
3. **Frontend Operación/Materiales (prioridad 3):** el componente `materiales` ya existe; se eleva al mismo estándar visual y de navegación que Inventario Avanzado, reforzando que Materiales es el catálogo único base del inventario.

> **Alcance real verificado en el código.** El backend de Inventario Avanzado expone 13 endpoints ya probados; el `InventarioAvanzadoService` y `MaterialesService` (`operacion/services/inventario.service.ts`) están completos; `NombresInventarioService` resuelve id→nombre sin exponer UUIDs. La lista autoritativa de módulos de un Plan/Paquete **se deriva de las claves de `precios_modulos`** (`Plan.aplicarPrecios` / `PaqueteSuscripcion.aplicarPrecios`), y el claim de módulos del JWT sale de `PlanModulosPlanAdapter.modulosHabilitadosDe(...)` (override de Suscripción → si no, catálogo del instrumento). El fix de auditoría JSONB (`ServicioInventarioAvanzado.auditar`/`aJson`) ya está aplicado y **no se toca**. La última migración Flyway es **V64**; la nueva de backfill será **V65**.

### Mapa Requisito → Diseño

| Requisito | Dónde se satisface en este diseño |
|---|---|
| **1** Navegación clara y no amontonada | BLOQUE 1 › Decisión de navegación (tabs + sub-navegación segmentada); wireframes por sección |
| **2** Movimientos legible (fin del "encimado") | BLOQUE 1 › Rediseño de Movimientos: un formulario + selector de tipo; SCSS con tokens |
| **3** Diseño descriptivo/informativo (KPIs, estados) | BLOQUE 1 › Resumen dashboard; componentes de estado; tokens; WCAG AA |
| **4** Cero UUIDs visibles | BLOQUE 1 › Estrategia sin-UUIDs con `NombresInventarioService` (ya implementada) |
| **5** Lotes, Configuración, Almacenes enterprise | BLOQUE 1 › wireframes de esas secciones; estados de caducidad |
| **6** Declaración de la dependencia | BLOQUE 2 › `CatalogoDependenciasModulos` + endpoint consultable |
| **7** Activar avanzado activa operación en Plan | BLOQUE 2 › normalización en `Plan.aplicarPrecios` (o servicio) |
| **8** Idem en Suscripción + claim JWT | BLOQUE 2 › normalización en `PaqueteSuscripcion`/override + efecto en el adaptador de claim |
| **9** Aviso + bloqueo de deselección (super_admin) | BLOQUE 2 › UX en `plan-dialog`, `paquete-suscripcion-dialog`, `plan-suscripcion-dialog` |
| **10** Backfill de planes y suscripciones | BLOQUE 2 › migración Flyway **V65** idempotente (planes + suscripciones/paquetes) |
| **11** Habilitación efectiva de Materiales (tester) | BLOQUE 2 › consecuencia del claim; validación con permiso `material:crear` |
| **12** Materiales estándar enterprise | BLOQUE 3 › rediseño de la vista `materiales` |
| **13** Coherencia visual y navegación | BLOQUE 3 › lenguaje visual compartido; navegación base↔avanzado |
| **14** Materiales como base única | BLOQUE 3 › catálogo único vía `MaterialesService`; estados vacíos que orientan a crear Materiales |
| **15** Calidad y no regresión | Estrategia de pruebas + Riesgos/No-regresión |

---

## Architecture

### El bloque Operación-Inventario: dos vistas, un bloque dependiente

```
Módulo 'operacion'                          Módulo 'inventario-avanzado' (depende de 'operacion')
────────────────────                        ───────────────────────────────────────────────────
Vista Materiales (inventario base)          Vista Inventario Avanzado (opera SOBRE Materiales)
  - Catálogo único de Materiales      ──►     - Almacenes, Existencias, Movimientos,
  - Alta/edición/baja lógica                    Kardex, Lotes, Configuración por Material
  - Movimiento base (entrada/salida/ajuste)   - Selectores de Material = MISMO catálogo (MaterialesService)
  POST /materiales (exige 'operacion')        endpoints /inventario-avanzado/** (exigen 'inventario-avanzado')

Relación:  inventario-avanzado  REQUIERE  operacion   (dependencia unidireccional, declarada una sola vez)
```

Ambas vistas cuelgan de la rama `/empresa/operacion` (`empresa.routes.ts`), cuya compuerta gruesa hoy es `guardaModuloAlguno('operacion','inventario-avanzado')`. Cada hoja reaplica su gating fino (`operacion.routes.ts`): Materiales exige `guardaModulo('operacion')` + `material:listar`; Inventario Avanzado exige `guardaModulo('inventario-avanzado')` + `almacen:listar`. El backend reimpone todo con `@PreAuthorize`.

### Flujo de la dependencia (fuente única de verdad)

```
Declaración (Req 6)
   CatalogoDependenciasModulos:  'inventario-avanzado' → ['operacion']
        │
        ├─► Backend persistencia (Req 7, 8): al guardar Plan/Paquete/Suscripción se NORMALIZA
        │      añadiendo 'operacion' si está 'inventario-avanzado' y falta 'operacion'.
        │         └─► modulos_habilitados incluye 'operacion'
        │               └─► PlanModulosPlanAdapter.modulosHabilitadosDe(tenant)  ← claim JWT (Req 8.2, 11)
        │                     └─► POST /materiales autoriza por módulo 'operacion' (Req 11)
        │
        ├─► Endpoint consultable (Req 6.2): GET /plataforma/dependencias-modulos
        │      └─► Frontend super_admin: aviso + bloqueo de deselección SIN hardcode (Req 9)
        │
        └─► Backfill (Req 10): migración V65 corrige filas históricas (planes + paquetes + suscripciones override)
```

**Decisión clave (D1):** la dependencia se aplica **en el momento de persistir** (dominio/servicio), no solo en la vista. Así el claim del JWT — que deriva de `modulos_habilitados` — queda consistente sin lógica adicional en el adaptador de claim, y el gating del backend (`POST /materiales`) autoriza al tester en cuanto su suscripción incluye `operacion`. La UI del super_admin refuerza la regla, pero **no es la fuente de verdad**.

---

## Components and Interfaces

## BLOQUE 1 — Rediseño enterprise de Inventario Avanzado (frontend)

### Investigación de UX y decisión de navegación (Req 1)

Resumen de la investigación (parafraseado; ver fuentes citadas al pie):

- **Tabs vs sidebar.** Los patrones de navegación SaaS recomiendan **tabs/segmented control cuando hay pocas áreas primarias (aprox. 3–6)** y sidebar cuando la jerarquía es profunda o hay muchas secciones ([saasui.design — navegación](https://www.saasui.design/blog/saas-navigation-ux-patterns)). Un buen conjunto de tabs deja ver todas las secciones a la vez, indica en cuál estás y cambia sin saltos de layout ([saasui.design — tabs/segmented](https://www.saasui.design/blog/saas-tabs-segmented-control-ux-patterns)). Los tabs reducen la sobrecarga mostrando una sección a la vez sin perder el resto de opciones a un clic o tecla ([uxpatterns.dev](https://uxpatterns.dev/patterns/navigation/tabs)).
- **Dashboard de inventario.** Un buen tablero es una **vista operativa única** de qué tienes, dónde está, qué se mueve, qué está detenido y qué requiere acción ahora ([sotion.so](https://sotion.so/blog/inventory-management-dashboard)); conviene alinear los KPIs a rotación, merma y velocidad de reabasto ([zigpoll — estrategia](https://www.zigpoll.com/content/inventory-management-optimization-strategy-guide-director)).
- **Formularios de movimiento.** Los sistemas enterprise (NetSuite, sku.io, SAP EWM, Accounting Seed) modelan cada cambio de stock como un **movimiento único con un campo "tipo"** (entrada/salida/transferencia/ajuste) escrito a un ledger inmutable ([sku.io — movement types](https://www.sku.io/docs/guides/inventory/movement-types-and-fields-reference/)). Amontonar formularios correlaciona con abandono en WMS de PyMEs ([zigpoll — formularios logísticos](https://www.zigpoll.com/content/15-ways-improve-form-completion-improvement-logistics)).

_Contenido reformulado para cumplir las restricciones de licencia; sin cita literal extensa._

**Decisión (D2) — Navegación.** Se conserva el patrón **tabs** (`mat-tab-group`, ya en uso y accesible) como navegación principal de las 7 secciones, porque:
- son 7 áreas hermanas de una sola entidad de negocio (Inventario Avanzado), no una jerarquía profunda;
- el componente ya está construido con tabs y con gating por permiso por tab (no reintroducimos riesgo);
- los tabs cumplen WCAG AA con teclado y ARIA de forma nativa en Angular Material.

Se **descartan** sidebar (sobra para 7 áreas de una entidad y competiría con el sidebar global de la app) y acordeón (peor para tablas densas y para comparar secciones). La mejora de fondo no es cambiar de patrón, sino **rediseñar el interior de cada tab** — especialmente Movimientos — y dar a **Resumen** un layout de **dashboard con KPIs**.

**Decisión (D3) — Resumen como dashboard.** El tab Resumen se estructura como tablero operativo: fila de KPIs (stat-cards) + panel de alertas accionables. Es la "vista operativa única" de la investigación, dentro del primer tab.

### Rediseño del tab Movimientos — fin del "encimado" (Req 2)

**Causa raíz verificada.** `inventario-avanzado.html` renderiza tres `mat-card` (Entrada, Salida, Transferencia), cada una con su `<form>`, dentro de `.inventario-avanzado__movimientos { display:grid; grid-template-columns: repeat(auto-fit, minmax(300px,1fr)); }`. En anchos intermedios el grid no acomoda 3 columnas legibles y las tarjetas quedan estrechas: los formularios se "encaman" y los botones (`operacion-form__acciones`, alineados a la derecha) se ven cortados.

**Decisión (D4) — Un solo formulario con selector de tipo.** Se sustituyen los 3 formularios por **un formulario de movimiento único** con un **selector de tipo** (segmented/`mat-button-toggle-group` o `mat-select`) con las opciones **Entrada / Salida / Transferencia**. Los campos visibles se adaptan al tipo elegido (renderizado condicional con `@if`/`@switch` sobre un signal `tipoMovimiento`):

```
┌──────────────────────────────────────────────────────────────┐
│ Registrar movimiento                                           │
│ Tipo de movimiento:  [ Entrada ] [ Salida ] [ Transferencia ]  │  ← button-toggle (indica el tipo actual, Req 2.4)
├──────────────────────────────────────────────────────────────┤
│  (Entrada)         Almacén ▾   Material ▾   Cantidad   Costo   │
│                    Código de lote (opcional)                   │
│  (Salida)          Almacén ▾   Material ▾   Cantidad           │
│                    Código de lote (opcional)                   │
│  (Transferencia)   Origen ▾   Destino ▾   Material ▾  Cantidad │
├──────────────────────────────────────────────────────────────┤
│                                        [ Registrar movimiento ]│  ← botón único, completo (Req 2.2)
└──────────────────────────────────────────────────────────────┘
```

- **Lógica preservada:** se conservan los tres `FormGroup` existentes (`formEntrada`, `formSalida`, `formTransferencia`) y sus métodos (`registrarEntrada/Salida/Transferencia`) — el selector solo decide **cuál form/campos se muestran y qué método se invoca al enviar** mediante un despachador `registrarMovimiento()` que hace `switch (tipoMovimiento())`. No cambia ningún contrato de servicio ni la validación existente (incluida "origen ≠ destino" y el manejo del 422 por existencias insuficientes).
- **Layout (D5):** un solo `mat-card` con `.ds-dialog-form` en una **columna fluida** (los campos apilan verticalmente en móvil y usan un grid de 2 columnas con `minmax` amplio en escritorio), separación con `--ds-space-*`, y el botón de acción en `operacion-form__acciones` visible completo. Se elimina el grid de 3 columnas de `__movimientos`.
- **Accesibilidad (Req 2, 3.6):** el grupo de tipo se expone como `role="group"` con `aria-label="Tipo de movimiento"`; el toggle activo tiene `aria-pressed`; el foco y el orden de tab siguen el flujo tipo → campos → botón.

### Wireframes textuales por sección (Req 1, 3, 4, 5)

**Resumen (dashboard, Req 3.1, 3.2):**
```
[ Almacenes: N ]  [ Materiales con stock bajo: N ]  [ Por reabastecer: N ]  [ Valor del inventario (cargado): $ ]
Alertas de stock bajo (lista accionable):  Material · existencias/mínimo (unidad)  → [Ver Kardex] [Registrar entrada]
```
- Los KPIs "Almacenes", "Stock bajo" y "Valor" ya existen; se **añade "Por reabastecer"** (materiales cuya existencia ≤ punto de reorden cuando la config está disponible). Cuando un indicador no tiene datos, muestra un **estado vacío descriptivo** vía `app-state-container` / texto de ayuda, nunca un cero engañoso (Req 3.2).
- El "Valor del inventario (cargado)" mantiene su nota de que se calcula sobre lo paginado (honesto, no engañoso).

**Existencias (Req 4.3):** tabla con **nombre** de Almacén y Material (resueltos por `NombresInventarioService`), cantidad, costo promedio, resalte de stock bajo con icono (no solo color, WCAG), filtros por Almacén/Material (`entity-select`, sin UUID) y acción "Ver Kardex" que salta al tab Kardex ya filtrado.

**Movimientos:** ver rediseño arriba (D4/D5).

**Kardex (Req 4.4):** selectores Almacén + Material por nombre; tabla cronológica (fecha, tipo con etiqueta es-MX, cantidad, costo unitario/total, saldo cantidad/costo). Estado vacío que invita a elegir Almacén y Material.

**Lotes (Req 5.1):** selector de Material → alta de lote (código, caducidad opcional con datepicker) + tabla de lotes con **estado de caducidad** visible: `Vigente` / `Próximo a caducar` (≤ 30 días) / `Caducado` / `Sin caducidad`, diferenciados por texto + color-token (no solo color). La lógica `estadoLote`/`etiquetaEstadoLote` ya existe y se conserva.

**Configuración (Req 5.2):** selector de Material → método de costeo (etiquetas es-MX: "Promedio ponderado" / "PEPS"), stock máximo, control por lote (toggle), consumo promedio, tiempo de entrega, stock de seguridad; validaciones `min(0)` antes de enviar; muestra el punto de reorden calculado que devuelve el `PUT`.

**Almacenes (Req 5.3):** listado con nombre/tipo/estado y alta/edición en tarjeta; sin UUIDs.

### Estrategia sin-UUIDs (Req 4) — ya implementada, se preserva
`NombresInventarioService.cargar()` precarga Almacenes y Materiales (≤200) y arma mapas id→entidad. Todos los selectores usan `app-entity-select` sobre listas en memoria y todas las celdas muestran nombre/código. Un id no resuelto cae al marcador neutro `(sin nombre)`, **nunca** al UUID. No se modifica esta estrategia; solo se garantiza que se sigue usando en las secciones rediseñadas.

### Design tokens concretos y componentes compartidos (Req 3.3, 3.4)
Se usan exclusivamente los tokens de `src/styles/_tokens.scss`:
- **Espaciado:** `--ds-space-1..8` (grid/gaps del formulario de movimiento y de las secciones).
- **Color:** `--ds-color-surface`, `--ds-color-border`, `--ds-color-text`, `--ds-color-text-muted`, semánticos `--ds-color-error`, `--ds-color-warning`, `--ds-color-success`, `--ds-color-info`.
- **Radios/sombras/tipografía:** `--ds-radius-md/lg`, `--ds-shadow-sm/md`, `--ds-font-size-sm/lg`.
- **Componentes compartidos:** `PageHeader`, `StateContainer` (carga/vacío/error), `StatCard`, `EntitySelect`, `DataTable` + `CeldaTablaDirective` — todos ya importados por el componente.
El SCSS del componente ya está tokenizado; el único cambio es eliminar `&__movimientos` (grid de 3 columnas) y añadir estilos del formulario único de movimiento con tokens (sin literales de color/espaciado). Los fallbacks tipo `var(--x, #hex)` presentes hoy se conservan tal cual (no se introducen nuevos hardcodes).

### Accesibilidad WCAG AA y responsividad (Req 1.4, 1.5, 3.6, 2.5)
- Tabs operables por teclado con nombre accesible (nativo de Material).
- Selector de tipo de movimiento como `role="group"` etiquetado; foco visible con `--ds-color-focus-ring`.
- Indicadores de estado (stock bajo, caducidad) diferenciados por icono/texto además de color.
- Responsivo: el formulario de movimiento pasa a una columna en `bp.until(md)`; ningún botón/campo se recorta.

---

## BLOQUE 2 — Dependencia de módulos `inventario-avanzado` → `operacion`

### Req 6 — Declaración de la dependencia (mecanismo consultable)

**Componente nuevo:** `CatalogoDependenciasModulos` (backend, paquete `platform.modulos`), fuente única de verdad de las dependencias entre módulos.

```java
// Declaración única y extensible (Req 6.1, 6.3).
public final class CatalogoDependenciasModulos {
    // clave dependiente -> conjunto de claves requeridas
    private static final Map<String, Set<String>> DEPENDENCIAS =
        Map.of("inventario-avanzado", Set.of("operacion"));

    /** Requeridos directos de una clave (normalizada), o vacío. */
    public static Set<String> requeridosDe(String modulo) { ... }

    /**
     * Cierre de la dependencia: dado un conjunto de módulos, devuelve el mismo
     * conjunto MÁS todos los requeridos (transitivo, idempotente, sin duplicar).
     * Normaliza claves (recorte + minúsculas). Preserva el orden de inserción.
     */
    public static Set<String> normalizar(Collection<String> modulos) { ... }
}
```

- **Diseño para extensibilidad (Req 6.3):** añadir futuras dependencias es agregar entradas al mapa; `normalizar` ya calcula el cierre transitivo, sin cambiar la regla existente.
- **Por qué un catálogo estático y no una tabla:** las claves de módulo son un catálogo de plataforma versionado en código (igual que `CatalogoModulosService.ETIQUETAS` y `RolModuloCatalogo`). Se mantiene el mismo patrón para coherencia y testabilidad; si en el futuro se requiere edición en runtime, la interfaz `requeridosDe/normalizar` permite respaldarla por BD sin cambiar los llamadores.

**Endpoint consultable (Req 6.2):** `GET /api/v1/plataforma/dependencias-modulos` → devuelve `{ "inventario-avanzado": ["operacion"] }` (mapa clave→requeridos). Se sirve junto al catálogo de módulos existente (mismo controlador de plataforma que ya expone `/plataforma/modulos`). El frontend lo consume para derivar el aviso y el bloqueo **sin listas hardcodeadas** en la vista.

### Req 7 — Plan: activar `inventario-avanzado` activa `operacion`

**Restricción de dominio verificada:** en `Plan`, `modulos_habilitados` **se deriva de las claves de `precios_modulos`** (`aplicarPrecios`). Por eso la normalización debe **inyectar la clave `operacion` en el mapa de precios** para que quede reflejada en `modulos_habilitados` y en el total.

**Decisión (D6) — Dónde normalizar en Plan.** Se normaliza en `Plan.aplicarPrecios(...)` (dominio), justo tras construir el mapa normalizado de precios:
```
si claves(preciosNormalizados) contiene 'inventario-avanzado' y NO contiene 'operacion':
    preciosNormalizados.put('operacion', BigDecimal 0.00 con escala monetaria)  // dependencia sin costo adicional
modulos_habilitados = new ArrayList(claves(preciosNormalizados))
```
- **Precio de la dependencia:** `operacion` se agrega con **precio 0.00** cuando se añade por dependencia (no altera el total salvo por 0). Si el super_admin ya capturó un precio para `operacion`, se respeta el capturado (solo se agrega cuando falta la clave). Esto satisface Req 7.1–7.3 sin duplicar la clave ni alterar el resto de módulos.
- **Unidireccional (Req 7.4):** solo `inventario-avanzado` arrastra `operacion`; tener `operacion` sin `inventario-avanzado` no agrega nada.
- **Aplica a crear y actualizar** porque ambos pasan por `aplicarPrecios`.

Alternativa considerada y descartada: normalizar solo en `ServicioPlanes` (aplicación). Se descarta porque dejaría el invariante fuera del dominio y no cubriría otros llamadores del dominio; `aplicarPrecios` es el punto único correcto.

### Req 8 — Suscripción/Paquete: idem + claim JWT

El claim del JWT se resuelve en `PlanModulosPlanAdapter.modulosHabilitadosDe(tenant)`:
- **override presente** → usa `Suscripcion.getModulosHabilitados()`;
- **sin override** → hereda del instrumento (`Plan` o `PaqueteSuscripcion`).

Para que el claim incluya `operacion` en **los tres caminos**, la normalización se aplica en cada fuente al persistir:
- **Paquete de Suscripción:** misma normalización que Plan en `PaqueteSuscripcion.aplicarPrecios(...)` (D6), inyectando `operacion` (precio 0.00) cuando falta y hay `inventario-avanzado`.
- **Override de Empresa (Suscripción):** en `Suscripcion.asignarModulos(...)` (o en `ServicioSuscripciones.actualizarModulosEmpresa` antes de asignar) se aplica `CatalogoDependenciasModulos.normalizar(modulos)` cuando `modulos != null`, de modo que un override que incluya `inventario-avanzado` incluya también `operacion`. La validación de subconjunto (`exigirSubconjuntoDelInstrumento`) se ejecuta **después de normalizar el instrumento** (que ya incluye `operacion`), evitando que la dependencia agregada sea rechazada por "no pertenece al instrumento".

**Decisión (D7) — El claim NO se normaliza en el adaptador.** Como Plan, Paquete y override ya quedan normalizados al persistir, `modulosHabilitadosDe` no necesita lógica de dependencia. Se evita duplicar la regla en el punto de lectura (una sola fuente de verdad, coherente con el resto del adaptador). Consecuencia directa: Req 8.2 y 8.4 se cumplen porque la fuente ya contiene `operacion`; y Req 11 (tester) se cumple en cuanto su suscripción/override incluye `operacion`, pues `POST /materiales` (`@autorizador.moduloHabilitado('operacion')`) verá el módulo en el claim.

> **Nota (D7-b, robustez):** como red de seguridad ante datos legados que no pasen por backfill, se documenta la opción de que `modulosHabilitadosDe` aplique `CatalogoDependenciasModulos.normalizar(...)` sobre la lista efectiva antes de devolverla. Se deja como **decisión de implementación conservadora**: si se activa, es idempotente y no rompe nada; si no, el backfill (Req 10) + la normalización al persistir bastan. Recomendación: activarla, porque es barata, idempotente y blinda el claim aunque una fila escape al backfill.

### Req 9 — UX del super_admin: aviso + bloqueo de deselección

**Componentes concretos afectados (frontend, plataforma):**
- `planes/plan-dialog.ts` (+ `.html`) — alta/edición de Plan.
- `planes/paquete-suscripcion-dialog.ts` (+ `.html`) — alta/edición de Paquete de Suscripción.
- `empresas/plan-suscripcion-dialog` / `empresas/crear-empresa-dialog.ts` — selección del subconjunto (override) de módulos por Empresa.

**Datos de dependencia (sin hardcode, Req 9.4):** los diálogos ya cargan el catálogo de módulos vía `PlanesService.listarModulos()`. Se añade `PlanesService.listarDependenciasModulos()` → `GET /plataforma/dependencias-modulos`, y un signal `dependencias` en cada diálogo. La lógica de aviso/bloqueo deriva de ese mapa.

**Comportamiento (Req 9.1–9.3):**
- Al **marcar** `inventario-avanzado`: se marca automáticamente `operacion` (y se habilita su precio en Plan/Paquete, con 0.00 por defecto) y se muestra un **aviso** en es-MX: _"Inventario avanzado depende de Operación y producción. Se activará también Operación."_ El aviso se calcula desde `dependencias['inventario-avanzado']`, no desde una lista fija.
- Al intentar **desmarcar** `operacion` mientras `inventario-avanzado` sigue marcado: **se impide** (la casilla `operacion` queda deshabilitada/bloqueada mientras exista un dependiente seleccionado) y se muestra el motivo en es-MX: _"No puedes desactivar Operación mientras Inventario avanzado esté activo, porque depende de él."_ (Req 9.3, decisión del usuario: **bloquear**, no solo avisar.)
- La UI refleja `operacion` como activado cuando `inventario-avanzado` está seleccionado (Req 9.2).

**Implementación en `plan-dialog`/`paquete-suscripcion-dialog`:** se añade un `computed requeridosBloqueados()` = unión de `dependencias[clave]` para toda `clave` en `seleccion()`; el método `alternar(clave, seleccionado)` rechaza el desmarcado si `requeridosBloqueados().has(clave)`; y `alternar('inventario-avanzado', true)` agrega también `operacion` a `seleccion()` con precio 0.00 si no tenía. En `plan-suscripcion-dialog` (override por Empresa, sin precios) la misma lógica aplica sobre el set de módulos elegidos.

### Req 10 — Backfill idempotente (planes + paquetes + suscripciones)

**Migración nueva:** `V65__dependencia_operacion_inventario_avanzado.sql` (la última existente es V64).

**Estrategia SQL (idempotente, JSONB):** para cada tabla con `modulos_habilitados jsonb` (array) que contenga `inventario-avanzado` y **no** contenga `operacion`, se **agrega** `operacion` al array. Para Plan/Paquete, además se agrega la clave `operacion` a `precios_modulos` con `0.00` si falta (para mantener el invariante "claves de precios = modulos_habilitados").

```sql
-- Planes: array modulos_habilitados + objeto precios_modulos
UPDATE plan
SET modulos_habilitados = modulos_habilitados || '["operacion"]'::jsonb,
    precios_modulos = jsonb_set(precios_modulos, '{operacion}', '0.00'::jsonb, true)
WHERE modulos_habilitados @> '["inventario-avanzado"]'::jsonb
  AND NOT (modulos_habilitados @> '["operacion"]'::jsonb);

-- Paquetes de suscripción: mismo patrón
UPDATE paquete_suscripcion
SET modulos_habilitados = modulos_habilitados || '["operacion"]'::jsonb,
    precios_modulos = jsonb_set(precios_modulos, '{operacion}', '0.00'::jsonb, true)
WHERE modulos_habilitados @> '["inventario-avanzado"]'::jsonb
  AND NOT (modulos_habilitados @> '["operacion"]'::jsonb);

-- Suscripciones con override (modulos_habilitados NO nulo): sin precios
UPDATE suscripcion
SET modulos_habilitados = modulos_habilitados || '["operacion"]'::jsonb
WHERE modulos_habilitados IS NOT NULL
  AND modulos_habilitados @> '["inventario-avanzado"]'::jsonb
  AND NOT (modulos_habilitados @> '["operacion"]'::jsonb);
```

- **Idempotencia (Req 10.4):** el `WHERE ... AND NOT (@> '["operacion"]')` garantiza que reaplicar la migración no duplica la clave (la segunda pasada no encuentra filas). Como es una migración Flyway versionada, además solo corre una vez; el predicado la hace segura si se reejecuta en otro entorno con datos ya corregidos.
- **Preservación (Req 10.5):** `|| '["operacion"]'` **agrega** sin tocar el resto del array; `jsonb_set(..., true)` solo crea la clave si falta. No se altera ningún otro módulo ni dato.
- **Caso tester (Req 10.3):** la suscripción de `cf1acdb6-75e6-40f4-a28d-d364142fbfbe` (override `["comercial","inventario-avanzado","estrategia","redes-sociales"]`) cae en el `UPDATE suscripcion` y queda `[... ,"operacion"]` junto a sus módulos actuales.
- **Nombre de columnas real:** las tres tablas tienen `modulos_habilitados jsonb`; Plan/Paquete además `precios_modulos jsonb`. La suscripción usa override nullable (`NULL` = hereda, no se toca).
- **Verificación de exactitud del nombre de columna `suscripcion.modulos_habilitados`** se confirma en tarea de implementación leyendo V21/V64 antes de escribir el SQL final (se referencia aquí como estrategia; el `.sql` real se valida contra el esquema).

### Req 11 — Habilitación efectiva de Materiales (tester)

Consecuencia directa de Req 8/10: cuando el claim incluye `operacion`, `POST /api/v1/materiales` (`@autorizador.moduloHabilitado('operacion') and @autorizador.tiene('material','crear')`) autoriza a un usuario con permiso `material:crear` (Req 11.1, 11.2). La ausencia del permiso `material:crear` sigue denegando con el error de autorización correspondiente, con independencia de la dependencia (Req 11.3). **No se modifica el endpoint ni su gating.**

### Simplificación de `guardaModuloAlguno` (consideración, sin romper)

Una vez que `inventario-avanzado` implica `operacion` en todo instrumento, la compuerta gruesa `guardaModuloAlguno('operacion','inventario-avanzado')` de `empresa.routes.ts` es equivalente a `guardaModulo('operacion')` para toda empresa con inventario avanzado. **Se documenta como posible simplificación futura pero NO se aplica en este diseño**, para no romper el acceso actual del admin (Req 15.5) ni depender de que el backfill ya haya corrido en todos los entornos. Se deja `guardaModuloAlguno` como está.

---

## BLOQUE 3 — Terminar el módulo Operación (Materiales / inventario base)

### Req 12, 13, 14 — Vista Materiales enterprise, coherente y catálogo único

El componente `materiales` ya lista (DataTable), crea, registra movimiento base (entrada/salida/ajuste) y da de baja lógica, con gating por permiso y `StateContainer`. El rediseño lo alinea al estándar de Inventario Avanzado:

- **Lenguaje visual compartido (Req 13.1, 13.4):** mismos tokens (`_tokens.scss`), tipografía y componentes de estado que Inventario Avanzado; se revisa el `.scss` para eliminar cualquier literal y usar solo `--ds-*` (se verifica en implementación).
- **Datos descriptivos sin UUIDs (Req 12.1):** columnas nombre, código/unidad, existencias, stock mínimo, estado; sin identificadores técnicos.
- **Alta/edición vía `MaterialesService` (Req 12.2):** sobre `/materiales`, respetando `material:crear`/`material:listar`.
- **Movimientos base por nombre (Req 12.3):** el panel de movimiento del Material muestra el nombre del Material (no id). _(Nota de integración ya documentada en el servicio: el inventario base no expone un LISTADO de movimientos; el historial cronológico es el Kardex del inventario avanzado. No se simula un listado inexistente.)_
- **Estados de carga/vacío/error (Req 12.4, 12.5):** `StateContainer`; errores en es-MX conservando lo capturado.
- **Navegación clara base↔avanzado (Req 13.3):** ambas vistas viven bajo `/empresa/operacion`; el `PageHeader` de cada una y el menú de la rama las presentan como parte del mismo bloque. Materiales enlaza conceptualmente a Inventario Avanzado (base → avanzado).
- **Catálogo único (Req 14.1, 14.3):** los selectores de Material del Inventario Avanzado usan `MaterialesService`/`NombresInventarioService` (mismo catálogo del Núcleo), sin catálogo paralelo.
- **Estado vacío que orienta (Req 14.2):** cuando **no hay Materiales**, las secciones del Inventario Avanzado que dependen de Materiales muestran un estado vacío en es-MX del tipo _"Aún no hay materiales. Crea Materiales en Operación para empezar a operar el inventario."_ (mensaje del `StateContainer`), guiando al usuario al inventario base.

---

## Data Models

**Sin tablas nuevas.** Se reutilizan:
- `plan.modulos_habilitados jsonb`, `plan.precios_modulos jsonb` (V1/V55).
- `paquete_suscripcion.modulos_habilitados jsonb`, `paquete_suscripcion.precios_modulos jsonb` (V64).
- `suscripcion.modulos_habilitados jsonb` (override nullable, V21/V64).
- Inventario: `almacen`, `existencia_almacen`, `movimiento_almacen`, `capa_costo`, `lote`, `config_inventario_material` (V26); `material` (V18).

**Modelos nuevos (no persistentes):**
- Backend: `CatalogoDependenciasModulos` (estático), DTO de respuesta del endpoint de dependencias (`Map<String, List<String>>`).
- Frontend: tipo `DependenciasModulos = Record<string, string[]>` en `plataforma.models.ts`; sin cambios en los modelos de inventario (ya completos).

**Frontend inventario avanzado:** se agrega un signal `tipoMovimiento: 'entrada'|'salida'|'transferencia'` en el componente; no cambian los modelos de request/response del servicio.

---

## Correctness Properties

_Una propiedad es una característica o comportamiento que debe cumplirse en todas las ejecuciones válidas del sistema — una afirmación formal de lo que el sistema debe hacer. Las propiedades son el puente entre la especificación legible por humanos y garantías de corrección verificables por máquina._

> **Aplicabilidad de PBT.** El bloque de la **dependencia de módulos** es lógica pura sobre conjuntos/JSONB con propiedades universales claras (idempotencia, cierre, preservación) y es **ideal para property-based testing**. El backfill es una transformación pura sobre datos, verificable con una propiedad de idempotencia. El bloque de **frontend/UI** (layout de Movimientos, wireframes, tokens, tabs) **no** es adecuado para PBT (rendering/UX): se cubre con specs de componente y verificación de build (ver Testing Strategy). Por eso las propiedades siguientes se concentran en el Bloque 2.

### Property 1: La normalización agrega `operacion` cuando hay `inventario-avanzado`

*Para todo* conjunto de módulos que contenga `inventario-avanzado`, el resultado de `CatalogoDependenciasModulos.normalizar(...)` contiene también `operacion`.

**Validates: Requirements 6.1, 7.1, 7.2, 8.1**

### Property 2: La normalización es idempotente

*Para todo* conjunto de módulos, aplicar `normalizar` una vez produce el mismo resultado que aplicarlo dos o más veces (`normalizar(x) == normalizar(normalizar(x))`), sin duplicar `operacion`.

**Validates: Requirements 7.3, 8.3, 10.4**

### Property 3: La dependencia es unidireccional y preserva el resto

*Para todo* conjunto de módulos que contenga `operacion` pero no `inventario-avanzado`, `normalizar` no agrega `inventario-avanzado`; y para todo conjunto, todos los módulos originales distintos de la dependencia se conservan sin alteración (Req 7.4, 10.5).

**Validates: Requirements 7.4, 10.5**

### Property 4: Persistir un Plan/Paquete con `inventario-avanzado` habilita `operacion`

*Para todo* Plan (o Paquete) construido/actualizado con `inventario-avanzado` entre sus módulos, `getModulosHabilitados()` incluye `operacion` y las claves de `precios_modulos` coinciden exactamente con `modulos_habilitados` (invariante de coherencia de claves).

**Validates: Requirements 7.1, 7.2, 8.1**

### Property 5: El claim efectivo incluye `operacion` mientras haya `inventario-avanzado`

*Para toda* suscripción vigente cuya lista efectiva de módulos (override o herencia del instrumento) incluya `inventario-avanzado`, `modulosHabilitadosDe(tenant)` incluye `operacion`.

**Validates: Requirements 8.2, 8.4, 11.1, 11.2**

### Property 6: El backfill es idempotente y preserva los demás módulos

*Para todo* estado inicial de `modulos_habilitados` (array JSONB), aplicar el backfill una o más veces produce el mismo resultado: si contenía `inventario-avanzado` sin `operacion`, el resultado contiene `operacion` exactamente una vez; en cualquier caso, todos los demás módulos se conservan sin cambios ni duplicados.

**Validates: Requirements 10.1, 10.2, 10.4, 10.5**

---

## Error Handling

- **Backend, normalización:** trabaja sobre claves normalizadas (recorte + minúsculas); claves nulas/vacías se ignoran (ya es el comportamiento de `aplicarPrecios`). No lanza errores nuevos.
- **Override de Empresa que incluye `inventario-avanzado`:** tras normalizar (agrega `operacion`), la validación de subconjunto se hace contra el instrumento **ya normalizado**; si el instrumento no incluye `inventario-avanzado`, el 422 existente sigue aplicando (no se puede elegir un módulo que el instrumento no ofrece).
- **Endpoint de dependencias:** solo lectura; si el mapa está vacío, devuelve `{}` (el frontend no muestra avisos). Falla suave: si la carga del mapa falla en el diálogo, el aviso/bloqueo no se activa pero el guardado sigue disponible (la normalización del backend es la red de seguridad real).
- **Migración V65:** idempotente por predicado; no borra ni reescribe otros campos. Si una fila ya está correcta, no se toca.
- **Frontend Movimientos:** se conserva el manejo del 422 (existencias insuficientes) y la validación "origen ≠ destino"; los errores se muestran en es-MX conservando lo capturado (Req 5.5, 2).
- **Frontend estados vacíos:** `StateContainer` muestra vacío/error/carga; un id no resuelto cae a `(sin nombre)`.

---

## Testing Strategy

### Enfoque dual
- **Property-based tests (Bloque 2, lógica pura):** las 6 propiedades de arriba, con librería PBT del ecosistema Java (**jqwik**, ya presente en el backend — hay `.jqwik-database` en `backend/`). Mínimo **100 iteraciones** por propiedad; cada test etiquetado `Feature: operacion-inventario-modulo-dependiente, Property N: ...`.
- **Unit tests (ejemplos y bordes):**
  - Backend: `CatalogoDependenciasModulos` (casos base, transitividad futura, claves con espacios/mayúsculas); `Plan`/`PaqueteSuscripcion` con y sin `inventario-avanzado`, con `operacion` preexistente (no duplica, respeta precio capturado); `ServicioSuscripciones.actualizarModulosEmpresa` con override que incluye `inventario-avanzado` (agrega `operacion`, pasa validación de subconjunto); `PlanModulosPlanAdapter` (claim incluye `operacion`).
  - Migración: prueba de idempotencia del backfill — se aplica sobre un dataset con planes/paquetes/suscripciones mixtos (con/sin la clave), se verifica el resultado y se **reaplica la sentencia** para confirmar que no cambia (Property 6). Se incluye el caso tester como ejemplo puntual.
- **Frontend (specs de componente, one-shot):**
  - `inventario-avanzado.spec.ts`: el tab Movimientos muestra **un** formulario con selector de tipo; cambiar el tipo cambia los campos; enviar invoca el método correcto (`registrarEntrada/Salida/Transferencia`); ningún UUID en el DOM; axe sin violaciones en el tab. Se ejecuta en **aislamiento** (`--include` del spec, sin watch) para evitar flakiness de axe en la suite completa.
  - `plan-dialog.spec.ts` / `paquete-suscripcion-dialog.spec.ts`: marcar `inventario-avanzado` marca `operacion` y muestra el aviso; intentar desmarcar `operacion` con `inventario-avanzado` activo lo impide y muestra el motivo; el aviso deriva del mapa de dependencias mockeado (no hardcode).
  - `materiales.spec.ts`: estados de carga/vacío/error; sin UUIDs; axe.

### Verificación
- **Backend:** `mvn -o test` de las clases tocadas (dependencia, servicios, migración) y luego build. La suite existente debe seguir verde (Req 15.2).
- **Frontend:** `ng build` de producción (0 errores) + los specs tocados en aislamiento (one-shot).
- **No se levantan servidores ni watchers** como parte del diseño/pruebas automatizadas; comandos de larga duración se dejan al usuario.
- Limpieza de cualquier archivo temporal de verificación.

---

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Cambiar Movimientos rompe la lógica existente | Se conservan los 3 `FormGroup` y métodos; solo cambia qué se muestra y el despacho por tipo. Spec de componente lo cubre. |
| Normalizar en el dominio altera el total del Plan | `operacion` se agrega con **0.00**; solo cuando falta la clave. Property 4 verifica coherencia claves/precios. |
| Override de Empresa rechazado por validación de subconjunto | Se normaliza el instrumento antes de validar; el override que incluye `inventario-avanzado` ya encuentra `operacion` permitido. |
| Datos legados que escapen al backfill | Backfill idempotente + (opción D7-b) normalización defensiva en el claim; ambas idempotentes. |
| Flakiness de axe en la suite completa de frontend | Ejecutar specs tocados en aislamiento (one-shot). |
| Nombre exacto de columnas JSONB en el SQL | Se valida contra V21/V64 antes de escribir el `.sql` final. |

## Consideraciones de no-regresión (Req 15)

- **Guardas de acceso existentes:** `guardaModulo`, `guardaModuloAlguno`, `guardaGiro`, `guardaPorPermiso` no se modifican; el acceso actual del admin al Inventario se preserva (Req 15.5). La posible simplificación de `guardaModuloAlguno` queda **documentada, no aplicada**.
- **Contratos backend verificados:** los 13 endpoints de Inventario Avanzado y `POST /materiales` no cambian de contrato; solo se agrega el endpoint de solo lectura de dependencias.
- **Fix de auditoría JSONB:** `ServicioInventarioAvanzado.auditar`/`aJson` **no se toca** (Req 15.4).
- **Design tokens / es-MX / responsividad / WCAG AA / sin UUIDs:** invariantes de UI preservados en las tres vistas (Req 15.3).

---

### Fuentes (investigación de UX, contenido parafraseado por cumplimiento de licencia)

- Patrones de navegación SaaS (sidebar vs top bar/tabs): https://www.saasui.design/blog/saas-navigation-ux-patterns
- Tabs y segmented control: https://www.saasui.design/blog/saas-tabs-segmented-control-ux-patterns y https://uxpatterns.dev/patterns/navigation/tabs
- Dashboards de inventario: https://sotion.so/blog/inventory-management-dashboard y https://www.zigpoll.com/content/inventory-management-optimization-strategy-guide-director
- Movimientos como ledger con tipo único: https://www.sku.io/docs/guides/inventory/movement-types-and-fields-reference/
- Formularios logísticos y abandono: https://www.zigpoll.com/content/15-ways-improve-form-completion-improvement-logistics
