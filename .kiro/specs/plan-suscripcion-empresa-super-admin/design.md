# Design Document

_(Documento de diseno — Plan y Suscripcion de la Empresa visibles y editables por el super_admin)_

## Overview

Feature cross-stack acotada, apoyada en contrato REST y servicios que YA existen. Cierra el hueco
descrito en los requisitos: hoy el super_admin no VE el plan vigente de una Empresa en el listado ni
puede ADMINISTRAR su suscripcion desde la vista de Empresas. La solucion combina:

- **BACKEND (cambio ADITIVO):** enriquecer el `EmpresaDto` con el "plan vigente" (nombre del plan +
  estado de la suscripcion vigente + identificadores para acciones), calculado en el backend como
  fuente de verdad y sin problema N+1. Se toca solo la proyeccion a DTO y se agregan consultas por
  lote a los repositorios. No hay migracion nueva, ni permiso nuevo, ni cambio de contrato de los
  campos existentes.
- **FRONTEND:** columna "Plan" en el listado (patron identico a la columna "Giro" ya implementada),
  panel "Plan y Suscripcion" en un dialogo de la ficha, y las acciones de suscripcion (asignar/
  cambiar plan, activar/suspender/cancelar, actualizar vigencia) conectadas al `SuscripcionController`
  existente, con RBAC por permiso y errores traducidos a espanol.

### Como encaja en la arquitectura existente

Toda la feature vive en el ambito de **plataforma / super_admin**, coherente con las features
analogas ya entregadas:

- El backend reutiliza el modulo `com.dessti.crm.platform.empresas`: `ServicioEmpresas` (fuente de
  verdad de plataforma sobre Empresas), `ServicioSuscripciones` (ciclo de vida de suscripciones),
  `SuscripcionRepository`/`PlanRepository`, y el `EmpresaController`/`SuscripcionController`. El plan
  y la suscripcion son datos de PLATAFORMA (monetizacion), por lo que enriquecer el `EmpresaDto` es
  coherente con el aislamiento del super_admin ya documentado en ese DTO (Req 24.3): no se expone
  ningun dato de NEGOCIO del tenant.
- El frontend reutiliza `features/plataforma/empresas` (la vista `PlataformaEmpresas`), el
  `PlanesService` (que ya cubre planes + parte de suscripciones), el patron `nombreGiro`/mapa
  id→nombre, el dialogo `CambiarGiroDialog` como plantilla, el helper `mensajeDeError`, el
  `AuthService.tienePermiso(recurso, operacion)` para RBAC en UI, y el datepicker ISO compartido
  (`provideFechaIsoDatepicker` + `FechaIsoDateAdapter`).

Este diseno replica las decisiones de la feature analoga `giro-empresa-super-admin`: resolver
id→nombre en el punto correcto (el nombre del plan vigente lo resuelve el BACKEND; el mapa planId→
nombre del frontend queda solo para SELECTORES), reutilizar permisos ya sembrados, no crear
migraciones innecesarias, y delegar todas las reglas en los servicios.

## Architecture

```
BACKEND (context-path /api/v1)
  GET /empresas            [empresa:listar]  -> Page<EmpresaDto> enriquecido (planVigente por fila)
  GET /empresas/{id}       [empresa:leer]    -> EmpresaDto enriquecido (planVigente)
      ServicioEmpresas.listarEmpresas(...) / consultarEmpresa(id)
        - carga la pagina/entidad de Empresas (ya existe)
        - RESUELVE por LOTE la suscripcion vigente y el nombre del plan (anti-N+1)
        - ensambla EmpresaDto con el sub-objeto planVigente

  POST /suscripciones                         [suscripcion:crear]           (YA existe)
  POST /suscripciones/{id}/activar|suspender|cancelar [suscripcion:cambiar_estado] (YA existe)
  PUT  /suscripciones/{id}/vigencia           [suscripcion:actualizar]      (YA existe)
  GET  /suscripciones/{id}                    [suscripcion:leer]            (YA existe)
  GET  /suscripciones?tenantId=...            [suscripcion:listar]          (YA existe)

FRONTEND (features/plataforma/empresas)
  Modelo Empresa           -> agrega `planVigente: PlanVigente | null`
  SuscripcionesService     -> nuevo servicio con TODAS las operaciones de suscripcion
                              (o extension de PlanesService; ver decision D2)
  Vista PlataformaEmpresas -> columna "Plan" (patron de la columna "Giro"; "Sin plan"; responsiva)
                              + accion "Plan y suscripcion" (abre el panel)
  PlanSuscripcionDialog    -> panel "Plan y Suscripcion": muestra plan vigente + suscripcion actual
                              y concentra las acciones (asignar/cambiar plan, activar/suspender/
                              cancelar, actualizar vigencia), gated por permiso
  Dialogos de accion       -> AsignarPlanDialog (selector de planes + vigencia con datepickers),
                              VigenciaDialog (datepickers); cancelar usa confirmacion destructiva
```

### Flujo de datos del enriquecimiento (anti-N+1)

```
listarEmpresas(estado, q, pageable):
  Page<Empresa> pagina = empresaRepository...(...)            // 1 query (ya existe)
  List<UUID> tenantIds = pagina.map(Empresa::getId)
  List<Suscripcion> subs = suscripcionRepository.findByTenantIdIn(tenantIds)   // 1 query por lote
  Map<UUID, Suscripcion> vigentePorTenant = reducir(subs, REGLA_VIGENTE)       // en memoria
  Set<UUID> planIds = vigentePorTenant.values().map(planId)
  Map<UUID, String> nombrePorPlan = planRepository.findAllById(planIds)        // 1 query por lote
                                       .toMap(Plan::getId, Plan::getNombre)
  return pagina.map(e -> EmpresaDto.de(e, planVigenteDe(e.getId(),
                                       vigentePorTenant, nombrePorPlan)))
```

Total: 3 consultas para N empresas (1 pagina + 1 suscripciones + 1 planes), independiente de N.

## Components and Interfaces

### Backend

#### 1. `PlanVigenteDto` (nuevo record anidado en `EmpresaDto`)

Sub-objeto que representa el plan/suscripcion vigente de la Empresa. Se anida en `EmpresaDto` (misma
convencion que `EmpresaDto.DireccionDto`).

```java
public record PlanVigenteDto(
        String nombrePlan,              // nombre legible del plan vigente (patron NO-UUID)
        EstadoSuscripcion estado,       // estado de la suscripcion vigente (activa/suspendida/cancelada)
        UUID planId,                    // id del plan vigente (para preseleccion en selectores; NO se muestra)
        UUID suscripcionId,             // id de la suscripcion vigente (para acciones; NO se muestra)
        LocalDate vigenciaInicio,       // inicio de vigencia de la suscripcion vigente
        LocalDate vigenciaFin) {        // fin de vigencia (null = sin fin)
}
```

Regla de poblado: cuando la Empresa NO tiene suscripcion, `EmpresaDto.planVigente` es `null` (ausencia
inequivoca de plan vigente; Req 4.3). Cuando existe, todos los campos vienen informados salvo
`vigenciaFin` que puede ser `null`.

Decision (ver D1): sub-objeto anidado en lugar de campos planos.

#### 2. `EmpresaDto` (enriquecido, ADITIVO)

- Se agrega un unico componente nuevo al record: `PlanVigenteDto planVigente` (puede ser `null`).
- La fabrica actual `EmpresaDto.de(Empresa)` se **conserva** para no romper llamadas existentes; se
  delega en la nueva sobrecarga pasando `planVigente = null`:

```java
public static EmpresaDto de(Empresa empresa) {
    return de(empresa, null);                       // preserva el contrato de las llamadas existentes
}

public static EmpresaDto de(Empresa empresa, PlanVigenteDto planVigente) {
    return new EmpresaDto(/* ...campos actuales..., */ planVigente);
}
```

Compatibilidad: solo se AGREGA un campo al final del record; ningun campo existente se elimina ni
renombra. Los consumidores actuales que no leen `planVigente` no se ven afectados (Jackson serializa
el campo nuevo, que sera `null` en las rutas que aun usan `de(empresa)`, por ejemplo `crearEmpresa`,
`actualizarEmpresa`, `mi-empresa`, `cambiarGiro`). En GET `/empresas` y GET `/empresas/{id}` vendra
informado.

#### 3. `SuscripcionRepository` (consultas por LOTE nuevas)

Se agregan dos derived queries para resolver el enriquecimiento sin N+1:

```java
// Todas las suscripciones de un conjunto de tenants, orden estable para desempate determinista.
List<Suscripcion> findByTenantIdInOrderByTenantIdAscCreatedAtDesc(Collection<UUID> tenantIds);
```

`PlanRepository` ya extiende `JpaRepository`, por lo que `findAllById(Collection<UUID>)` esta
disponible sin cambios para resolver `planId → nombre` por lote.

Nota RLS: la tabla `suscripcion` es tenant-scoped por RLS (V2/V53). Las operaciones puntuales del
`ServicioSuscripciones` fijan `app.current_tenant` por tenant. Para el listado de N empresas eso no
escala. `ServicioEmpresas.listarEmpresas` y `consultarEmpresa` son operaciones de PLATAFORMA del
super_admin; la carga por lote debe ejecutarse en el mismo contexto de plataforma que ya usa
`empresaRepository.findAll` (la tabla `empresa` no lleva RLS). **Riesgo R3:** confirmar en
implementacion que la consulta por lote sobre `suscripcion` es visible para el super_admin sin fijar
un tenant unico; si RLS la ocultara, la alternativa es una consulta JPQL explicita que acote por
`tenantId IN (...)` ejecutada con el rol de plataforma (mismo enfoque que ya documenta
`SuscripcionRepository`: "acotan explicitamente por tenant_id ... sin depender del filtro de tenant
de Hibernate"). Se valida con un test de integracion del enriquecimiento.

#### 4. `ServicioEmpresas` (owner del enriquecimiento)

Cambio de responsabilidad: hoy el `EmpresaController` proyecta `Page<Empresa>` a DTO con
`EmpresaDto::de`. Para centralizar la regla de vigencia y la carga por lote (fuente de verdad,
Req 4.5), el servicio pasa a devolver el DTO **ya enriquecido**:

- `consultarEmpresa(UUID)` : ya devuelve `EmpresaDto`; ahora arma `planVigente` para esa Empresa
  (resuelve su suscripcion vigente y el nombre del plan).
- Nuevo `Page<EmpresaDto> listarEmpresasDto(EstadoEmpresa, String, Pageable)` que envuelve el
  `listarEmpresas` actual y enriquece la pagina por lote. El controlador pasa a llamar a este metodo
  y devuelve `PaginaResponse.de(page)` (sin el mapeador `EmpresaDto::de`).
  - Se conserva `listarEmpresas(...)` (que devuelve `Page<Empresa>`) si algun otro consumidor lo usa;
    si no lo usa nadie mas, se refactoriza para evitar codigo muerto (Req 12.5). Se verifica con
    busqueda de usos antes de decidir.

Regla de suscripcion vigente (unica, Req 3 y 4.4), aplicada tanto en el listado como en la consulta:

```java
static Optional<Suscripcion> vigente(List<Suscripcion> deLaEmpresa) {
    return deLaEmpresa.stream()
        .filter(s -> s.getEstado() == EstadoSuscripcion.ACTIVA)   // 1) activa
        .findFirst()
        .or(() -> deLaEmpresa.stream()                            // 2) si no, mas reciente por
            .max(Comparator.comparing(Suscripcion::getVigenciaInicio)  //    vigenciaInicio
                .thenComparing(Suscripcion::getCreatedAt)));           // 3) desempate: createdAt
}
```

(La eleccion de "activa" asume a lo sumo una activa por Empresa, como ya documenta
`SuscripcionRepository.findFirstByTenantIdAndEstadoOrderByIdAsc`; ante mas de una activa se toma la
primera de forma estable.)

Resolucion del nombre del plan (patron NO-UUID, Req 4.8): a partir del `planId` de la suscripcion
vigente contra el `Map<UUID,String>` cargado por lote (`planRepository.findAllById`).

No se toca el ciclo de vida de suscripciones (crear/activar/suspender/cancelar/actualizarVigencia):
esas operaciones ya viven en `ServicioSuscripciones` y sus endpoints ya existen.

#### 5. Endpoints de suscripcion (SIN cambios)

`SuscripcionController` ya expone POST `/suscripciones`, POST `/{id}/activar|suspender|cancelar`,
PUT `/{id}/vigencia`, GET `/{id}`, GET `?tenantId=`. El request de PUT vigencia ya existe
(`ActualizarVigenciaRequest { @NotNull LocalDate vigenciaInicio; LocalDate vigenciaFin }`). No se
agrega ni modifica ningun endpoint de backend salvo el enriquecimiento del `EmpresaDto`.

### Frontend

#### 1. Modelo (`plataforma.models.ts`)

```typescript
/** Plan/suscripcion vigente de una Empresa (dato enriquecido del EmpresaDto). */
export interface PlanVigente {
  nombrePlan: string;
  estado: EstadoSuscripcion;      // 'activa' | 'suspendida' | 'cancelada'
  planId: string;                 // uso interno (preseleccion); NUNCA se muestra
  suscripcionId: string;          // uso interno (acciones); NUNCA se muestra
  vigenciaInicio: string;         // ISO YYYY-MM-DD
  vigenciaFin: string | null;
}

export interface Empresa {
  // ...campos actuales...
  /** Plan vigente resuelto por el backend; null cuando la Empresa no tiene suscripcion. */
  planVigente: PlanVigente | null;
}
```

#### 2. Servicio de suscripciones (`SuscripcionesService`, nuevo)

Decision D2: crear un `SuscripcionesService` dedicado y **mover** ahi las operaciones de suscripcion
que hoy conviven en `PlanesService` (listar/crear/activar/suspender/cancelar), agregando las que
faltan (`actualizarVigencia`, `consultar`). `PlanesService` queda enfocado en Planes + catalogos
(modulos/monedas). Se actualizan los consumidores actuales de esas operaciones (la pantalla de
Planes) para que apunten al nuevo servicio, evitando duplicacion y codigo muerto (Req 9.6, 12.5).

```typescript
/** Cuerpo de POST /suscripciones. */
export interface CrearSuscripcionRequest {
  tenantId: string; planId: string;
  vigenciaInicio?: string | null; vigenciaFin?: string | null;   // ISO YYYY-MM-DD
}
/** Cuerpo de PUT /suscripciones/{id}/vigencia. */
export interface ActualizarVigenciaRequest {
  vigenciaInicio: string;              // obligatorio (backend @NotNull)
  vigenciaFin?: string | null;         // opcional (null = sin fin)
}

@Injectable({ providedIn: 'root' })
export class SuscripcionesService {
  listarPorEmpresa(tenantId: string): Observable<Suscripcion[]>;        // GET /suscripciones?tenantId
  consultar(id: string): Observable<Suscripcion>;                       // GET /suscripciones/{id}
  crear(request: CrearSuscripcionRequest): Observable<Suscripcion>;     // POST /suscripciones
  activar(id: string): Observable<Suscripcion>;                         // POST /{id}/activar
  suspender(id: string): Observable<Suscripcion>;                       // POST /{id}/suspender
  cancelar(id: string): Observable<Suscripcion>;                        // POST /{id}/cancelar
  actualizarVigencia(id: string, r: ActualizarVigenciaRequest): Observable<Suscripcion>; // PUT /{id}/vigencia
}
```

#### 3. Columna "Plan" en el listado (`PlataformaEmpresas`)

- Se agrega `{ clave: 'plan', encabezado: 'Plan', ocultarEnMovil: true }` al arreglo `columnas`,
  con el MISMO patron responsivo de la columna "Giro" (Req 1.7).
- Helper `nombrePlan(empresa)`: devuelve `empresa.planVigente?.nombrePlan ?? 'Sin plan'` (Req 1.3,
  1.4, 1.5). El nombre viene del backend; NO se resuelve en el frontend y NO se hace peticion por fila
  (Req 1.2). Nunca se muestra UUID (Req 1.6).
- Gating de lectura (Req 10.4): la columna y la accion del panel se muestran solo si
  `auth.tienePermiso('suscripcion','leer') || auth.tienePermiso('suscripcion','listar')`.

#### 4. Panel "Plan y Suscripcion" (`PlanSuscripcionDialog`, nuevo)

Como la vista de Empresas es un listado (no hay ruta de ficha/detalle), el panel se abre como dialogo
desde una accion del menu de la fila ("Plan y suscripcion"), igual que "Cambiar giro"/"Editar". El
dialogo recibe la `Empresa` y:

- Al abrir, carga las suscripciones de la Empresa (`SuscripcionesService.listarPorEmpresa`) para
  disponer de la suscripcion vigente completa (con `modulosHabilitados`, `monedaFacturacion`) y del
  `id` para las acciones; carga tambien el catalogo de planes (`PlanesService.listarPlanes(0,100)`)
  para el selector, y el catalogo de modulos (`PlanesService.listarModulos`) para etiquetar modulos.
- Muestra (Req 5.2, 5.3, 5.4):
  - **Plan vigente:** nombre del plan, moneda del plan, maximo de usuarios, modulos habilitados
    (por etiqueta humana), total (calculado por backend). El plan completo se obtiene del catalogo por
    `planId` de `planVigente` (Req 2.2, 2.3).
  - **Suscripcion actual:** estado en espanol ("Activa"/"Suspendida"/"Cancelada"), vigencia con fechas
    legibles (inicio y fin; "Sin fecha de fin" cuando `vigenciaFin` es null, Req 5.4), moneda de
    facturacion, modulos habilitados de la suscripcion (etiqueta humana).
  - Cuando no hay suscripcion vigente: estado "Sin plan" + accion "Asignar plan" (Req 5.5).
- Resolucion de nombres de modulo (Req 5.7): `Map<clave, nombreVisible>` construido desde
  `PlanesService.listarModulos()`, identico al patron de `planes.ts`
  (`new Map(modulos.map(m => [m.clave, m.nombreVisible]))`).
- NO muestra ningun UUID (Req 5.6).
- Concentra las acciones segun permiso (Req 5, 7, 8). En exito de cualquier accion, el dialogo cierra
  devolviendo un indicador de cambio para que la vista refresque el listado (Req 12.1).

#### 5. Dialogos de accion

- **AsignarPlanDialog** (asignar/cambiar plan, Req 6): `mat-select` de planes por NOMBRE (sin UUID,
  Req 6.2, 6.8); datepickers opcionales de `vigenciaInicio`/`vigenciaFin` (Req 6.4) usando el
  datepicker ISO compartido. Al confirmar: `SuscripcionesService.crear({ tenantId, planId,
  vigenciaInicio, vigenciaFin })` (Req 6.3). Errores 404/422 se muestran dentro del dialogo sin
  cerrarlo (Req 6.5, 6.6). Reutiliza el patron de `CambiarGiroDialog` (estructura, `mensajeDeError`,
  senal `guardando`/`error`, focus).
- **VigenciaDialog** (actualizar vigencia, Req 8): datepickers de inicio (obligatorio) y fin
  (opcional) con etiquetas en espanol (Req 8.2). Al confirmar: `actualizarVigencia(id, { vigenciaInicio,
  vigenciaFin })` (Req 8.3). 404/422 dentro del dialogo (Req 8.4, 8.5).
- **Activar/Suspender:** invocan `SuscripcionesService.activar|suspender(suscripcionId)` (Req 7.2,
  7.3). No requieren dialogo propio (accion directa desde el panel).
- **Cancelar (destructiva/irreversible, Req 7.4):** usa el `ConfirmDialogService` existente con
  `destructiva: true` y un mensaje que advierte que la cancelacion es irreversible (estado final),
  como ya hace "Suspender empresa". Solo tras confirmar invoca `cancelar(suscripcionId)`.

#### 6. Datepickers (reutilizacion)

Se usa `provideFechaIsoDatepicker()` + `FechaIsoDateAdapter` (locale es-MX, modelo = cadena ISO
`YYYY-MM-DD`), el mismo que el resto de la app. Esto hace que los controles reactivos emitan
directamente `YYYY-MM-DD`, exactamente lo que espera el backend (`LocalDate`), sin desfase de zona
horaria. Los dialogos con fechas importan `MatDatepickerModule` y agregan los proveedores en su
`TestBed` (patron ya usado en `objetivo-dialog`, `inventario-avanzado`).

## Data Models

### Contrato JSON de `EmpresaDto` enriquecido (GET /empresas/{id})

```jsonc
{
  "id": "…", "nombre": "Acme S.A.", "rfc": "…", "giroId": "…", "estado": "activa",
  "brandingNombreVisible": null, "brandingLogo": null, "nombreComercial": null,
  "emailContacto": null, "telefono": null, "sitioWeb": null,
  "direccion": { "calle": null, "ciudad": null, "estado": null, "cp": null, "pais": null },
  "notas": null, "fechaCancelacion": null, "finPeriodoGracia": null,
  "createdAt": "…", "updatedAt": "…",
  "planVigente": {                       // NUEVO; null cuando la Empresa no tiene suscripcion
    "nombrePlan": "Plan Profesional",
    "estado": "activa",
    "planId": "…",                       // uso interno del frontend; nunca se muestra
    "suscripcionId": "…",                // uso interno del frontend; nunca se muestra
    "vigenciaInicio": "2025-01-01",
    "vigenciaFin": null
  }
}
```

En GET `/empresas` (paginado), cada elemento de `content` incluye el mismo campo `planVigente`.

### Requests de suscripcion (contratos ya existentes en backend)

```jsonc
// POST /suscripciones
{ "tenantId": "…", "planId": "…", "vigenciaInicio": "2025-01-01", "vigenciaFin": null }

// PUT /suscripciones/{id}/vigencia   (vigenciaInicio obligatorio; vigenciaFin opcional/null = sin fin)
{ "vigenciaInicio": "2025-01-01", "vigenciaFin": "2025-12-31" }

// POST /suscripciones/{id}/activar | /suspender | /cancelar   -> cuerpo vacio {}
```

Las respuestas de estas operaciones son `SuscripcionDto` (id, tenantId, planId, estado,
vigenciaInicio/Fin, modulosHabilitados, monedaFacturacion, version, timestamps), con `estado`
serializado en minusculas.

## Error Handling

| Origen | Codigo | Manejo en UI |
|---|---|---|
| Empresa o Plan inexistente al crear suscripcion | 404 | Mensaje del backend en espanol; dialogo permanece abierto (Req 6.5) |
| Vigencia invalida (crear/actualizar) | 422 | Mensaje del backend en espanol; dialogo no se cierra (Req 6.6, 8.4) |
| Activar una suscripcion cancelada | 422 | Mensaje del backend en espanol; sin romper la vista (Req 7.5) |
| Suscripcion inexistente (cambio de estado / vigencia) | 404 | Mensaje claro en espanol; refresca panel (Req 7.6, 8.5) |
| Falta de permisos en cualquier operacion | 403 | Mensaje claro de falta de permisos en espanol (Req 10.5) |
| Conflicto (bloqueo optimista u otros) | 409 | Mensaje del backend en espanol |
| Nombre de plan no resoluble en selector / catalogo caido | — | Degradado controlado; la columna del listado sigue usando el nombre del backend (Req 2.4) |

Todos los mensajes se derivan con el helper existente `mensajeDeError` (que propaga el mensaje del
backend cuando aporta contexto), igual que en `CambiarGiroDialog` y el resto de la plataforma. El
patron es: senal `error = signal<string|null>(null)` mostrada dentro del dialogo; en acciones sin
dialogo (activar/suspender) se usa `NotificacionesService`.

## Testing Strategy

Esta feature es integracion UI + enriquecimiento de proyeccion; **no** aporta algoritmos puros con
propiedades universales de alto valor mas alla de la regla de "suscripcion vigente", que se prueba de
forma directa y exhaustiva con ejemplos y casos borde. Por eso se omite la seccion de Correctness
Properties (PBT) y se aplica una estrategia de pruebas por ejemplos/integracion, coherente con la
feature analoga `giro-empresa-super-admin`.

### Backend (Java, JUnit + MockMvc)

- **Regla de suscripcion vigente** (`ServicioEmpresas`, unit): cubrir los cuatro casos del Req 3 con
  ejemplos y bordes:
  - hay una `activa` → se elige la activa aunque exista otra con `vigenciaInicio` mas reciente;
  - sin activas → la de `vigenciaInicio` mas reciente;
  - empate en `vigenciaInicio` → desempate por `createdAt` mas reciente;
  - sin suscripciones → `planVigente == null`.
- **Enriquecimiento del listado y consulta** (integracion / slice de servicio con repos): GET
  `/empresas` y GET `/empresas/{id}` devuelven `planVigente` con `nombrePlan` y `estado` correctos, e
  ids poblados; una Empresa sin suscripcion devuelve `planVigente == null`.
- **Anti-N+1** (integracion): al listar N empresas se ejecuta un numero de consultas ACOTADO
  (independiente de N) para resolver suscripciones y planes (p. ej. contando consultas o verificando
  que se usan `findByTenantIdIn`/`findAllById`). Valida Req 4.11 y confirma la visibilidad RLS
  (riesgo R3).
- **Slice del controlador** (`EmpresaControllerTest`): el JSON de `/empresas` y `/empresas/{id}`
  incluye el campo `planVigente` (servicio mockeado); las rutas que usan `EmpresaDto.de(empresa)`
  (alta/edicion/mi-empresa/giro) siguen serializando `planVigente: null` sin romper aserciones
  existentes.
- **No regresion:** la suite de backend (1173+) debe seguir en verde tras el cambio ADITIVO.

### Frontend (Angular, Jasmine/Karma one-shot + axe)

Respetando la restriccion del proyecto: pruebas one-shot (sin watch) y ejecutadas en aislamiento por
componente (para evitar flaky de axe al correr toda la suite en paralelo).

- **`SuscripcionesService` spec:** cada metodo hace la peticion correcta al endpoint correcto con el
  cuerpo esperado (`crear`, `actualizarVigencia`, `activar`, `suspender`, `cancelar`,
  `listarPorEmpresa`, `consultar`), con `HttpTestingController`.
- **Vista `PlataformaEmpresas` spec:** la columna "Plan" muestra el `nombrePlan` del `planVigente`;
  "Sin plan" cuando es null; nunca se muestra UUID; la accion del panel/columna aparece solo con
  permiso de lectura de suscripciones; axe sin violaciones.
- **`PlanSuscripcionDialog` spec:** renderiza plan vigente y suscripcion actual (estado en espanol,
  fechas legibles, "Sin fecha de fin", modulos por etiqueta humana); estado "Sin plan" + accion
  asignar cuando no hay suscripcion; acciones gated por permiso; axe.
- **`AsignarPlanDialog` / `VigenciaDialog` spec:** selector de planes por nombre; datepickers
  (proveedor ISO); al confirmar llaman al servicio con el cuerpo correcto; 404/422 muestran el mensaje
  y no cierran; cancelar exige confirmacion destructiva; axe.
- **No regresion:** specs existentes de empresas (alta/edicion/reset/cambiar giro) y de la pantalla de
  Planes siguen verdes tras mover las operaciones de suscripcion al nuevo servicio.

### Verificacion

- Backend: `mvn -o test` (o `-Dtest=` de las clases tocadas) + build.
- Frontend: `ng build` (0 errores) + specs tocadas en aislamiento one-shot.

## Risks and Decisions

- **D1 — Forma del dato enriquecido: sub-objeto anidado `planVigente { … }`.** Elegido frente a
  campos planos por: (a) claridad y cohesion (nombre + estado + ids + vigencia son un solo concepto);
  (b) permite representar "sin plan" con un unico `null` en vez de varios campos planos nulos
  coordinados; (c) deja disponibles `planId`/`suscripcionId` para las acciones del frontend sin
  mostrarlos como UUID; (d) sigue la convencion ya usada en `EmpresaDto.DireccionDto`. Cumple Req 4.2.
- **D2 — Servicio de frontend: nuevo `SuscripcionesService` (con migracion de las operaciones de
  suscripcion que hoy estan en `PlanesService`).** Elegido para separar responsabilidades
  (Planes vs Suscripciones) y ubicar `actualizarVigencia`/`consultar` sin inflar `PlanesService`. La
  alternativa (extender `PlanesService`) cumpliria Req 9.6 pero mezcla dos agregados. Se evita
  duplicacion actualizando los consumidores existentes; sin codigo muerto (Req 9.6, 12.5).
- **D3 — El enriquecimiento se centraliza en `ServicioEmpresas` (fuente de verdad, Req 4.5)** y el
  controlador deja de mapear con `EmpresaDto::de`. La fabrica `EmpresaDto.de(Empresa)` se conserva por
  compatibilidad de las otras rutas (cambio ADITIVO, Req 4.9).
- **D4 — PUT `/suscripciones/{id}/vigencia`: request confirmado** como
  `{ vigenciaInicio (obligatorio), vigenciaFin (opcional/null = sin fin) }`, verificado en
  `ActualizarVigenciaRequest`/`ServicioSuscripciones.actualizarVigencia`. Resuelve el supuesto 2 de
  los requisitos.
- **R3 — Visibilidad RLS de la carga por lote de suscripciones.** La tabla `suscripcion` tiene RLS; el
  super_admin opera sin tenant en contexto de plataforma. Se debe verificar en implementacion (con un
  test de integracion) que `findByTenantIdIn` es visible; si no, usar JPQL que acote por
  `tenantId IN (...)` con el rol de plataforma (patron ya documentado en el repositorio). Mitigado por
  el test anti-N+1/enriquecimiento.
- **R4 — Volumen del catalogo de planes en selectores.** Se carga con `size=100` (maximo permitido,
  Req 2.5). Si a futuro hay >100 planes, el selector requerira busqueda server-side; fuera de alcance
  ahora.
- **No hay migracion nueva ni permiso nuevo.** Se reutilizan los permisos ya sembrados
  (`suscripcion:crear`, `suscripcion:cambiar_estado`, `suscripcion:actualizar`, `suscripcion:leer`,
  `suscripcion:listar`), coherente con la feature analoga de Giro.
