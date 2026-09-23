# Implementation Plan: Plan y Suscripcion de la Empresa (super_admin)

## Overview

Plan incremental de solo-codigo, en espanol (es-MX), coherente con el diseno aprobado. Primero el
enriquecimiento ADITIVO del backend (`EmpresaDto.planVigente`, regla de vigencia como fuente de verdad,
carga por lote anti-N+1) con sus pruebas junto a cada bloque; luego el frontend (modelo, `SuscripcionesService`,
columna "Plan", panel y dialogos de accion, RBAC, errores en espanol, accesibilidad WCAG AA y design tokens),
tambien con specs junto a cada bloque. Cada tarea construye sobre la anterior y termina cableada a la ficha y al
listado, sin codigo muerto. Se respeta la restriccion de pruebas del proyecto: specs de frontend one-shot (sin
watch) y en aislamiento por el flaky de axe; backend con `mvn -o test` o `-Dtest=` de las clases tocadas.

No se crean migraciones ni permisos nuevos. No se incluyen tareas de despliegue ni documentacion de usuario
(fuera de alcance de este tasks.md de solo-codigo).

## Tasks

- [x] 1. Backend: enriquecer el `EmpresaDto` con el plan vigente (cambio aditivo)
  - [x] 1.1 Crear el record anidado `PlanVigenteDto` dentro de `EmpresaDto`
    - Definir el record `PlanVigenteDto(String nombrePlan, EstadoSuscripcion estado, UUID planId, UUID suscripcionId, LocalDate vigenciaInicio, LocalDate vigenciaFin)` anidado en `EmpresaDto` (misma convencion que `EmpresaDto.DireccionDto`).
    - Importar `EstadoSuscripcion` y tipos requeridos; sin logica adicional.
    - _Requisitos: 4.1, 4.2, 4.8_
  - [x] 1.2 Agregar el componente `planVigente` a `EmpresaDto` conservando la fabrica existente
    - Agregar `PlanVigenteDto planVigente` como ULTIMO componente del record (aditivo; no eliminar ni renombrar campos).
    - Conservar `de(Empresa)` delegando en la nueva sobrecarga con `planVigente = null`.
    - Agregar sobrecarga `de(Empresa, PlanVigenteDto)` que construye el DTO con el plan vigente informado.
    - _Requisitos: 4.2, 4.3, 4.9_
  - [ ]* 1.3 Pruebas unitarias de la fabrica del `EmpresaDto`
    - Verificar que `de(empresa)` produce `planVigente == null` y que `de(empresa, dto)` lo informa.
    - _Requisitos: 4.3, 4.9_

- [x] 2. Backend: consultas por lote para resolver suscripcion vigente y nombre de plan (anti-N+1)
  - [x] 2.1 Agregar consulta por lote de suscripciones en `SuscripcionRepository`
    - Agregar `List<Suscripcion> findByTenantIdInOrderByTenantIdAscCreatedAtDesc(Collection<UUID> tenantIds)`.
    - Verificar riesgo R3 (visibilidad RLS para super_admin sin fijar tenant): si RLS oculta la fila, sustituir por JPQL acotado por `tenantId IN (...)` con el rol de plataforma, siguiendo el patron ya documentado en el repositorio. Reutilizar `PlanRepository.findAllById` para `planId -> nombre`.
    - _Requisitos: 4.8, 4.11_
  - [ ]* 2.2 Prueba de integracion de la visibilidad de la consulta por lote (riesgo R3)
    - Confirmar con datos de varios tenants que `findByTenantIdIn...` (o el fallback JPQL) devuelve las suscripciones esperadas en contexto de plataforma.
    - _Requisitos: 4.11_

- [x] 3. Backend: regla de suscripcion vigente y enriquecimiento en `ServicioEmpresas`
  - [x] 3.1 Implementar la regla unica de "suscripcion vigente" como metodo reutilizable
    - Metodo estatico/privado `vigente(List<Suscripcion>)`: activa; si no, la de `vigenciaInicio` mas reciente; desempate `createdAt` mas reciente.
    - _Requisitos: 3.1, 3.2, 3.3, 3.4, 4.4, 4.5_
  - [x] 3.2 Enriquecer `consultarEmpresa(id)` para armar `planVigente`
    - Resolver la suscripcion vigente de la Empresa y el nombre del plan; construir `PlanVigenteDto` (o `null` si no hay suscripcion) y devolver el DTO enriquecido.
    - _Requisitos: 4.3, 4.7, 4.8, 4.10_
  - [x] 3.3 Agregar `listarEmpresasDto(estado, q, pageable)` con enriquecimiento por lote
    - Envolver el `listarEmpresas` actual; resolver suscripciones (2.1) y planes (`findAllById`) por lote y mapear cada `Empresa` a `EmpresaDto` enriquecido (3 consultas, independiente de N).
    - Revisar usos de `listarEmpresas(...)` (que devuelve `Page<Empresa>`): si nadie mas lo usa, refactorizar para no dejar codigo muerto.
    - _Requisitos: 4.5, 4.6, 4.11, 12.5_
  - [ ]* 3.4 Pruebas unitarias de la regla de vigencia (4 casos del Req 3)
    - activa preferida sobre mas reciente; sin activas -> mas reciente por `vigenciaInicio`; empate -> desempate por `createdAt`; sin suscripciones -> `planVigente == null`.
    - _Requisitos: 3.1, 3.2, 3.3, 3.4_
  - [ ]* 3.5 Pruebas de integracion del enriquecimiento (listado y por id) + anti-N+1
    - `planVigente` con `nombrePlan`/`estado`/ids correctos en listado y por id; Empresa sin suscripcion -> `planVigente == null`; numero de consultas acotado / uso de `findByTenantIdIn` y `findAllById`.
    - _Requisitos: 4.6, 4.7, 4.8, 4.11_

- [x] 4. Backend: exponer el DTO enriquecido desde `EmpresaController`
  - [x] 4.1 GET /empresas y GET /empresas/{id} devuelven el DTO enriquecido
    - Que el listado llame a `listarEmpresasDto(...)` y devuelva `PaginaResponse.de(page)` (dejar de mapear con `EmpresaDto::de` donde aplique); `/empresas/{id}` usa el `consultarEmpresa` enriquecido.
    - _Requisitos: 4.6, 4.7_
  - [ ]* 4.2 Slice del controlador (`EmpresaControllerTest`)
    - El JSON de `/empresas` y `/empresas/{id}` incluye `planVigente`; las rutas que aun usan `EmpresaDto.de(empresa)` (alta/edicion/mi-empresa/giro) siguen serializando `planVigente: null` sin romper aserciones existentes.
    - _Requisitos: 4.7, 4.9, 12.2_

- [ ] 5. Backend: verificacion y no regresion
  - Ejecutar `mvn -o test` (o `-Dtest=` de las clases tocadas) y confirmar build sin errores; la suite (1173+) queda en verde tras el cambio aditivo. Ante dudas, preguntar al usuario.
  - _Requisitos: 4.12, 12.4_

- [x] 6. Frontend: modelo y servicio de suscripciones
  - [x] 6.1 Agregar `PlanVigente` y `planVigente` al modelo
    - Interface `PlanVigente { nombrePlan, estado, planId, suscripcionId, vigenciaInicio, vigenciaFin }` y campo `planVigente: PlanVigente | null` en `Empresa` (`plataforma.models.ts`).
    - _Requisitos: 1.3, 4.1, 4.2_
  - [x] 6.2 Crear `SuscripcionesService` y migrar operaciones desde `PlanesService`
    - Metodos `listarPorEmpresa`, `consultar`, `crear`, `activar`, `suspender`, `cancelar`, `actualizarVigencia` mapeados a los endpoints reales; interfaces `CrearSuscripcionRequest` y `ActualizarVigenciaRequest`.
    - MOVER las operaciones de suscripcion que hoy estan en `PlanesService` al nuevo servicio y actualizar sus consumidores (pantalla de Planes) para no duplicar ni dejar codigo muerto (`PlanesService` queda con planes + catalogos).
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 12.5_
  - [ ]* 6.3 Spec de `SuscripcionesService` (one-shot)
    - Con `HttpTestingController`, cada metodo pega al endpoint/cuerpo correcto.
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.5_

- [x] 7. Frontend: columna "Plan" en la vista `PlataformaEmpresas`
  - [x] 7.1 Agregar la columna "Plan" con el patron de la columna "Giro"
    - `{ clave: 'plan', encabezado: 'Plan', ocultarEnMovil: true }`; helper `nombrePlan(empresa)` -> `empresa.planVigente?.nombrePlan ?? 'Sin plan'`; sin peticiones por fila; nunca UUID.
    - Gating de lectura: mostrar columna solo con `auth.tienePermiso('suscripcion','leer') || auth.tienePermiso('suscripcion','listar')`.
    - _Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 10.4_
  - [ ]* 7.2 Spec de la vista para la columna Plan (one-shot, aislamiento)
    - Muestra `nombrePlan`; "Sin plan" cuando es null; nunca UUID; columna/accion visible solo con permiso de lectura; axe sin violaciones.
    - _Requisitos: 1.3, 1.4, 1.6, 10.4, 11.2_

- [x] 8. Frontend: panel "Plan y Suscripcion" (`PlanSuscripcionDialog`)
  - [x] 8.1 Crear el panel con carga y presentacion del plan/suscripcion vigentes
    - Al abrir carga suscripciones de la Empresa + catalogo de planes (`listarPlanes(0,100)`) + catalogo de modulos.
    - Muestra plan vigente (nombre, moneda, maxUsuarios, modulos por etiqueta humana, total) y suscripcion actual (estado en espanol, vigencia legible con "Sin fecha de fin", moneda de facturacion, modulos); "Sin plan" + accion asignar cuando no hay; nunca UUID; acciones gated por permiso; al exito devuelve indicador de cambio para refrescar el listado.
    - Reutilizar design tokens y componentes existentes; WCAG AA (focus trap, labels, roles, errores por campo).
    - _Requisitos: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 2.2, 2.3, 2.5, 11.1, 11.2, 11.3, 11.5, 12.1_
  - [ ]* 8.2 Spec de `PlanSuscripcionDialog` (one-shot, aislamiento)
    - Renderiza plan vigente y suscripcion actual (estado espanol, fechas legibles, "Sin fecha de fin", modulos por etiqueta); "Sin plan" + accion asignar; acciones gated por permiso; axe.
    - _Requisitos: 5.2, 5.3, 5.4, 5.5, 5.7, 10.1, 10.2, 10.3, 11.2_

- [x] 9. Frontend: dialogos de accion de suscripcion
  - [x] 9.1 Crear `AsignarPlanDialog` (asignar/cambiar plan)
    - `mat-select` de planes por NOMBRE (sin UUID); datepickers ISO opcionales de `vigenciaInicio`/`vigenciaFin`; al confirmar `SuscripcionesService.crear({ tenantId, planId, vigenciaInicio, vigenciaFin })`; errores 404/422 mostrados sin cerrar; patron `CambiarGiroDialog` + `mensajeDeError`; WCAG AA + tokens.
    - _Requisitos: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.8, 10.1, 11.2, 11.3_
  - [x] 9.2 Crear `VigenciaDialog` (actualizar vigencia)
    - Datepickers ISO: inicio obligatorio, fin opcional, etiquetas en espanol; al confirmar PUT vigencia; 404/422 sin cerrar; WCAG AA + tokens.
    - _Requisitos: 8.1, 8.2, 8.3, 8.4, 8.5, 10.3, 11.2, 11.3_
  - [x] 9.3 Acciones activar/suspender/cancelar desde el panel
    - Activar/suspender directas (`activar|suspender`); cancelar con `ConfirmDialogService` destructiva/irreversible antes de `cancelar`; gating por `suscripcion:cambiar_estado`; refresco de panel y columna al exito.
    - _Requisitos: 7.1, 7.2, 7.3, 7.4, 7.7, 10.2, 12.1_
  - [ ]* 9.4 Specs de `AsignarPlanDialog` y `VigenciaDialog` (one-shot, aislamiento)
    - Selector por nombre; datepickers (proveedor ISO); al confirmar llaman al servicio con el cuerpo correcto; 404/422 muestran mensaje y no cierran; cancelar exige confirmacion destructiva; axe.
    - _Requisitos: 6.2, 6.3, 6.4, 6.5, 6.6, 7.4, 8.3, 8.4, 8.5, 11.2_

- [x] 10. Frontend: cableado, errores y refresco en la vista
  - [x] 10.1 Cablear la accion "Plan y suscripcion" en el menu de fila
    - Agregar la accion al menu de fila de `PlataformaEmpresas` (gated por permiso de lectura), abrir `PlanSuscripcionDialog` con la `Empresa`, y refrescar el listado al cerrarse con cambio.
    - _Requisitos: 5.1, 10.4, 12.1, 12.5_
  - [x] 10.2 Manejo de errores 404/409/422/403 en espanol
    - Traducir 404/409/422 con `mensajeDeError`; 403 con mensaje claro de falta de permisos; sin romper la vista.
    - _Requisitos: 6.5, 6.6, 7.5, 7.6, 8.4, 8.5, 10.5, 11.1_

- [ ] 11. Frontend: verificacion y no regresion
  - Ejecutar `ng build development` (0 errores); specs tocadas one-shot y en aislamiento (por el flaky de axe). Confirmar no regresion: specs de empresas (alta/edicion/reset/cambiar giro) y de la pantalla de Planes siguen verdes tras mover las operaciones al nuevo servicio. Ante dudas, preguntar al usuario.
  - _Requisitos: 12.2, 12.3, 12.4, 12.5_

## Notes

- Las subtareas marcadas con `*` (pruebas) son opcionales para un MVP rapido; las tareas de implementacion no lo son.
- Cada tarea referencia los requisitos que cubre para trazabilidad.
- El diseno omite la seccion de Correctness Properties (no aplica PBT: integracion UI + enriquecimiento de proyeccion); por eso se usan pruebas por ejemplos/integracion, no property-based.
- Restriccion de pruebas: frontend one-shot y en aislamiento (flaky de axe); backend con `mvn -o test` o `-Dtest=` de las clases tocadas; no ejecutar la suite completa en watch.
- Tareas 5 y 11 son de verificacion/no-regresion.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "6.1"] },
    { "id": 1, "tasks": ["1.2", "2.1", "6.2"] },
    { "id": 2, "tasks": ["1.3", "2.2", "3.1", "6.3"] },
    { "id": 3, "tasks": ["3.2", "3.3", "7.1"] },
    { "id": 4, "tasks": ["3.4", "3.5", "4.1", "7.2", "8.1"] },
    { "id": 5, "tasks": ["4.2", "8.2", "9.1", "9.2", "9.3"] },
    { "id": 6, "tasks": ["9.4", "10.1", "10.2"] }
  ]
}
```
