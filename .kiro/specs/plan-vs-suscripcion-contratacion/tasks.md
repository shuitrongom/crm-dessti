# Implementation Plan: Plan vs Suscripción — Contratación excluyente

# Plan de Implementación: Plan vs Suscripción (contratación excluyente)

## Overview

Plan incremental solo-código para introducir la contratación excluyente Plan/Suscripción sobre el módulo `platform.empresas` (Spring Boot + Angular + PostgreSQL). Se construye de abajo hacia arriba: primero base de datos y dominio backend, luego persistencia, DTOs/commands, servicios, configuración, gating y endpoints; después el frontend (modelos, servicios, pantallas y diálogos); y finalmente verificación de build y suite de pruebas. Cada bloque incluye sus pruebas junto a la implementación para detectar errores temprano.

Decisiones confirmadas aplicadas: clasificación de legado como `tipo_instrumento='plan'` (Opción A, sin paquetes semilla); se conserva el nombre interno `Suscripcion` (UI muestra "Plan"/"Suscripción"); corte por vencimiento en tiempo real en el gating (VENCIDA derivada, sin scheduler); umbral de aviso vía propiedad `crm.contratacion.umbral-aviso-dias` (default 14) con `porVencer` calculado en backend; RBAC reutiliza `plan:*` y `suscripcion:*` (sin permisos nuevos); avisos solo en UI.

Las subtareas marcadas con `*` son de prueba/verificación y son opcionales. El resto son de implementación obligatoria. Cada subtarea referencia los requisitos que cubre.

## Tasks

- [x] 1. Migración de base de datos (Flyway V64)
  - [x] 1.1 Crear `V64__contratacion_plan_vs_suscripcion.sql` con estructura y clasificación idempotentes
    - Añadir `plan.duracion_dias` (`INTEGER NOT NULL DEFAULT 730`, backfill implícito, luego `DROP DEFAULT`) y CHECK `ck_plan_duracion_dias (duracion_dias > 365)`.
    - Crear tabla `paquete_suscripcion` (catálogo sin RLS, patrón `plan`/`giro`): columnas `id`, `nombre` (uq), `max_usuarios`, `giro_id` FK, `moneda_codigo` FK, `precios_modulos` jsonb, `modulos_habilitados` jsonb, `duracion_dias`, `admite_prueba`, `duracion_prueba_meses`, auditoría/`version`; CHECKs `ck_paquete_duracion_dias (>0 AND <=365)` y `ck_paquete_prueba`.
    - Añadir a `suscripcion`: `tipo_instrumento VARCHAR(20) NOT NULL DEFAULT 'plan'`, `paquete_suscripcion_id UUID` nullable con FK, `facturacion_activada BOOLEAN NOT NULL DEFAULT FALSE`, `inicio_facturacion DATE`; `ALTER COLUMN plan_id DROP NOT NULL`.
    - Ampliar CHECK de estado: drop `ck_suscripcion_estado` y recrear con `('activa','en_prueba','suspendida','cancelada','vencida')`.
    - Clasificación Opción A (ANTES del CHECK XOR): `UPDATE suscripcion SET tipo_instrumento='plan' WHERE plan_id IS NOT NULL AND paquete_suscripcion_id IS NULL`.
    - Añadir CHECK XOR `ck_suscripcion_instrumento` DESPUÉS de clasificar (o `NOT VALID` + `VALIDATE CONSTRAINT`).
    - Usar `IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS` / `DROP CONSTRAINT IF EXISTS` para idempotencia.
    - _Requisitos: 1.1, 1.2, 2.2, 3.3, 5.1, 12.1, 12.2, 12.3_
  - [x] 1.2 Añadir en la misma migración el saneo del override inconsistente (Demo Factura)
    - `UPDATE` que recorta `modulos_habilitados` de cada Contrato a la intersección con `modulos_habilitados` del `plan` referenciado, solo cuando hay módulos ajenos; idempotente y sin tocar el gating.
    - _Requisitos: 11.3, 12.3_
  - [x]* 1.3 Prueba de integración de arranque de la migración V64
    - Levantar contexto con Testcontainers y `spring.jpa.hibernate.ddl-auto=validate`; verificar que Flyway aplica V64 y el mapeo JPA valida sin errores.
    - _Requisitos: 12.2, 12.4_
  - [x]* 1.4 Prueba de integración del saneo del override
    - Sembrar un Contrato con override superset del plan, ejecutar migración/repetir, verificar que el override queda como intersección y que reejecutar no cambia el resultado (idempotencia).
    - _Requisitos: 11.3_

- [x] 2. Dominio: estados, Plan y nueva entidad de catálogo
  - [x] 2.1 Ampliar `EstadoSuscripcion` con `EN_PRUEBA` y `VENCIDA`
    - Añadir valores con etiquetas `en_prueba`/`vencida`; actualizar `valorBd()`/`desdeValorBd(...)` y `EstadoSuscripcionConverter` para round-trip completo.
    - _Requisitos: 5.1, 12.5_
  - [x] 2.2 Añadir `duracionDias` a `Plan` con validación de dominio
    - Nuevo campo/columna mapeada; fábricas `crear`/`actualizar` validan `duracionDias > 365` lanzando `ReglaNegocioException` (422) con mensaje es-MX.
    - _Requisitos: 2.2, 2.3, 9.1_
  - [x] 2.3 Crear entidad de dominio `PaqueteSuscripcion`
    - Campos espejo de `Plan` + `duracionDias`, `admitePrueba`, `duracionPruebaMeses`; fábricas `crear`/`actualizar` con validaciones: `0 < duracionDias <= 365`; si `admitePrueba`, `duracionPruebaMeses > 0` y prueba en días `<= duracionDias`; lanzar 422 con mensajes es-MX.
    - _Requisitos: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 9.1_
  - [x] 2.4 Ampliar `Suscripcion` (Contrato) con instrumento, facturación y transiciones
    - Añadir enum interno `TipoInstrumento{PLAN,SUSCRIPCION}` + converter (`plan`/`suscripcion`), campos `tipoInstrumento`, `paqueteSuscripcionId`, `facturacionActivada`, `inicioFacturacion`.
    - Nuevas fábricas `crearDePlan(...)`, `crearDeSuscripcion(...)` (ACTIVA) y `crearEnPrueba(...)` (EN_PRUEBA, `vigenciaFin = inicio + duracionPruebaMeses`), todas con invariante XOR.
    - Transición `activarFacturacion(...)` (solo EN_PRUEBA→ACTIVA, marca `facturacionActivada`); método derivado `estaVencida(hoy)` (no persiste); conservar `crearBasica` creando `tipo=plan` para compatibilidad.
    - _Requisitos: 1.1, 1.2, 4.5, 5.2, 8.1, 8.4, 12.1_
  - [x]* 2.5 Property tests jqwik de invariantes y estados de dominio
    - **Property 1: Exclusividad de instrumento (XOR)** — **Validates: Requirements 1.1, 1.2**
    - **Property 2: Duración del Plan siempre mayor a un año** — **Validates: Requirements 2.2, 2.3**
    - **Property 3: Duración del Paquete siempre de un año o menos** — **Validates: Requirements 3.3, 3.4, 9.1**
    - **Property 4: Coherencia de la configuración de prueba** — **Validates: Requirements 3.5, 3.6**
    - **Property 8: Activar facturación solo desde EN_PRUEBA** — **Validates: Requirements 8.1, 8.4**
    - **Property 9: Otorgar prueba fija la vigencia correcta** — **Validates: Requirements 4.5**
    - **Property 10: Estabilidad del enum de estado persistido** — **Validates: Requirements 5.1, 12.5**
    - Etiquetar cada test `// Feature: plan-vs-suscripcion-contratacion, Property N: <texto>`; mínimo 100 iteraciones.
    - _Requisitos: 1.1, 1.2, 2.2, 2.3, 3.3, 3.4, 3.5, 3.6, 4.5, 5.1, 8.1, 8.4, 9.1, 12.5_
  - [x]* 2.6 Unit tests de transiciones de estado
    - EN_PRUEBA→ACTIVA en `activarFacturacion`; rechazo (422) desde estado ≠ EN_PRUEBA; rechazo transición desde CANCELADA; cálculo de `estaVencida(hoy)` en bordes.
    - _Requisitos: 5.2, 5.4, 8.1, 8.4_

- [x] 3. Persistencia y helper compartido de validación por giro
  - [x] 3.1 Crear `PaqueteSuscripcionRepository`
    - `existsByNombre`, `findAll` paginado, `findAllById`, y conteo por referencia para impedir borrado si un Contrato lo usa.
    - _Requisitos: 3.1, 10.3, 12.6_
  - [x] 3.2 Ampliar `SuscripcionRepository` con selección por conjunto de estados
    - Añadir `findByTenantIdAndEstadoInOrderByIdAsc(tenantId, estados)` para gating y enriquecimiento.
    - _Requisitos: 1.3, 6.1_
  - [x] 3.3 Extraer helper `CatalogoModulosGiroValidacion`
    - Extraer de `ServicioPlanes` la validación de módulos por giro/moneda a un helper compartido reutilizable por planes y paquetes; sin duplicar lógica.
    - _Requisitos: 2.5, 3.1, 11.1_
  - [x]* 3.4 Unit tests del helper `CatalogoModulosGiroValidacion`
    - Aceptación de módulos válidos por giro; rechazo (422) de módulos ajenos al giro y de moneda inactiva.
    - _Requisitos: 2.5, 11.1, 11.2_

- [x] 4. DTOs y commands
  - [x] 4.1 Crear DTO y commands de Paquete de Suscripción
    - `PaqueteSuscripcionDto`, `CrearPaqueteSuscripcionCommand`, `ActualizarPaqueteSuscripcionCommand` con `duracionDias`, `admitePrueba`, `duracionPruebaMeses`.
    - _Requisitos: 3.1, 3.2_
  - [x] 4.2 Ampliar `PlanDto` y sus commands con `duracionDias`
    - Añadir `duracionDias` a `PlanDto`, `CrearPlanCommand`, `ActualizarPlanCommand`.
    - _Requisitos: 2.2, 2.3_
  - [x] 4.3 Ampliar `SuscripcionDto` y `EmpresaDto.PlanVigenteDto`
    - `SuscripcionDto`: + `tipoInstrumento`, `paqueteSuscripcionId`.
    - `PlanVigenteDto` (nombre conservado): + `tipoInstrumento`, `nombreInstrumento`, `diasRestantes`, `enPrueba`, `vencida`, `porVencer`, `paqueteSuscripcionId`.
    - _Requisitos: 1.4, 7.1, 7.2, 12.6_

- [x] 5. Configuración del umbral de aviso
  - [x] 5.1 Crear `ContratacionProperties` y registrar `application.yml`
    - `@ConfigurationProperties(prefix="crm.contratacion")` con `umbralAvisoDias` (default 14); bloque `crm.contratacion.umbral-aviso-dias: ${CONTRATACION_UMBRAL_AVISO_DIAS:14}` en `application.yml`.
    - _Requisitos: 7.2, 7.3_

- [x] 6. Servicios de aplicación
  - [x] 6.1 Crear `ServicioPaquetesSuscripcion`
    - CRUD de paquetes; validaciones de duración/prueba/giro/moneda reutilizando `CatalogoModulosGiroValidacion`; impedir borrado si un Contrato lo referencia (422).
    - _Requisitos: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 10.3_
  - [x] 6.2 Ampliar `ServicioPlanes` con duración y helper
    - Validar `duracionDias > 365` en alta/edición; usar `CatalogoModulosGiroValidacion` (sin lógica duplicada).
    - _Requisitos: 2.2, 2.3, 2.5_
  - [x] 6.3 Ampliar `ServicioSuscripciones` con acciones de contrato
    - `activarFacturacion` (EN_PRUEBA→ACTIVA, inicio primer día del mes siguiente si no se indica, ajusta vigenciaFin al paquete); `extenderPrueba` (nueva vigenciaFin acotada a la duración del paquete); `convertirAPlan` (crea Contrato de Plan y cierra el anterior manteniendo exclusividad); validar override ⊆ instrumento; un solo Contrato vigente por Empresa.
    - _Requisitos: 1.3, 8.1, 8.2, 8.3, 8.4, 9.2, 11.1, 11.2_
  - [x] 6.4 Ampliar `ServicioEmpresas.crearEmpresa` con elección excluyente
    - Aceptar `planId` XOR `paqueteSuscripcionId` (422 si ambos o ninguno); `otorgarPrueba` → crear Contrato `EN_PRUEBA` con `vigenciaFin` calculada solo si el paquete `admitePrueba`.
    - _Requisitos: 4.1, 4.2, 4.3, 4.4, 4.5_
  - [x] 6.5 Ampliar enriquecimiento `planVigente` en `ServicioEmpresas`
    - Ampliar `suscripcionVigente` para incluir EN_PRUEBA; resolver `nombreInstrumento` por lote para Plan y Paquete según `tipoInstrumento`; calcular `diasRestantes`, `enPrueba`, `vencida` y `porVencer` (contra `umbralAvisoDias`).
    - _Requisitos: 1.4, 7.1, 7.2, 12.6_
  - [x]* 6.6 Property test de override subconjunto (servicios)
    - **Property 7: El override es siempre subconjunto del instrumento** — **Validates: Requirements 11.1, 11.2**
    - Etiquetar `// Feature: plan-vs-suscripcion-contratacion, Property 7: ...`; mínimo 100 iteraciones.
    - _Requisitos: 11.1, 11.2_
  - [x]* 6.7 Tests de integración de alta excluyente y acciones de contrato
    - Alta con Plan / con Paquete / ambos-422 / ninguno-422 / prueba→EN_PRUEBA con vigenciaFin correcta; activarFacturacion/extenderPrueba/convertirAPlan; un solo Contrato vigente.
    - _Requisitos: 4.1, 4.2, 4.3, 4.4, 4.5, 8.1, 8.3, 9.2, 1.3_

- [x] 7. Gating por vencimiento en `PlanModulosPlanAdapter`
  - [x] 7.1 Ajustar selección y corte por fecha del gating
    - Inyectar `PaqueteSuscripcionRepository` y `Clock`; seleccionar Contrato con estado ∈ {ACTIVA, EN_PRUEBA} vía `findByTenantIdAndEstadoInOrderByIdAsc`; devolver cero módulos si `vigenciaFin != null` y `vigenciaFin < hoy`; resolver módulos de Plan o Paquete según `tipoInstrumento` (override manda si existe); conservar `applyTenant`/RLS.
    - _Requisitos: 5.6, 6.1, 6.2, 6.3, 6.4, 12.6_
  - [x]* 7.2 Property tests jqwik del gating
    - **Property 5: El gating concede acceso solo con estado y vigencia válidos** — **Validates: Requirements 5.6, 6.1, 6.2, 6.3, 6.4**
    - **Property 6: Resolución de módulos según instrumento** — **Validates: Requirements 6.1, 12.6**
    - Etiquetar cada test `// Feature: plan-vs-suscripcion-contratacion, Property N: ...`; mínimo 100 iteraciones.
    - _Requisitos: 5.6, 6.1, 6.2, 6.3, 6.4, 12.6_
  - [x]* 7.3 Tests de integración del gating por vencimiento
    - Concede con EN_PRUEBA vigente; deniega con ACTIVA/EN_PRUEBA vencido (mismo resultado que sin Contrato); deniega SUSPENDIDA/CANCELADA; usa `Clock` fijo.
    - _Requisitos: 6.1, 6.2, 6.3, 6.4_

- [x] 8. Endpoints REST
  - [x] 8.1 Crear `PaqueteSuscripcionController`
    - `/paquetes-suscripcion` CRUD (POST/PUT/GET/GET paginado/DELETE) gated con `suscripcion:*`; 201 en creación; 422 al borrar si un Contrato lo referencia.
    - _Requisitos: 3.1, 10.1, 10.3, 10.4, 10.5_
  - [x] 8.2 Añadir acciones de contrato en `SuscripcionController`
    - `POST /suscripciones/{id}/activar-facturacion` (`suscripcion:cambiar_estado`), `POST /suscripciones/{id}/extender-prueba` (`suscripcion:actualizar`), `POST /suscripciones/{id}/convertir-a-plan` (`suscripcion:crear`).
    - _Requisitos: 8.1, 8.2, 8.3, 8.4, 9.2_
  - [x] 8.3 Ajustar `CrearEmpresaRequest` y `EmpresaController`
    - Evolucionar el request a `planId` XOR `paqueteSuscripcionId` + `otorgarPrueba`; mapear al command excluyente.
    - _Requisitos: 4.1, 4.2, 4.3, 4.4, 4.5_
  - [x]* 8.4 Tests de controladores (`@WebMvcTest`/integración)
    - RBAC por permiso, códigos 201/422/404; alta excluyente vía `EmpresaController`; acciones de contrato en `SuscripcionController`.
    - _Requisitos: 1.4, 4.3, 4.4, 8.4, 10.1, 10.5_

- [x] 9. Actualizar tests backend existentes afectados
  - [x]* 9.1 Actualizar tests existentes por firmas ampliadas
    - `ServicioEmpresasTest`, `ServicioSuscripcionesTest`, `PlanModulosPlanAdapterTest`, `EmpresaControllerTest`: adaptar a nuevos campos/firmas conservando cobertura previa.
    - _Requisitos: 12.1, 12.4_

- [x] 10. Checkpoint backend
  - [x]* 10.1 Verificar build y suite backend (`mvn -o test`) sin regresión (1177+)
    - Ejecutar `mvn -o test`; asegurar que todas las pruebas pasan y no hay regresión. Ante fallos, corregir y volver a ejecutar; preguntar al usuario si surgen dudas.
    - _Requisitos: 12.4_

- [x] 11. Modelos del frontend
  - [x] 11.1 Ampliar `plataforma.models.ts`
    - Añadir `PaqueteSuscripcion`, `TipoInstrumento`; ampliar `EstadoSuscripcion` con `'en_prueba'`/`'vencida'`; extender `PlanVigente` con `tipoInstrumento`, `nombreInstrumento`, `diasRestantes`, `enPrueba`, `vencida`, `porVencer`, `paqueteSuscripcionId`.
    - _Requisitos: 1.4, 3.1, 7.1, 7.2, 12.6_

- [x] 12. Servicios del frontend
  - [x] 12.1 Crear `PaquetesSuscripcionService`
    - CRUD contra `/paquetes-suscripcion` (list paginado, get, create, update, delete).
    - _Requisitos: 3.1, 10.3_
  - [x] 12.2 Ampliar `SuscripcionesService` con acciones de contrato
    - `activarFacturacion`, `extenderPrueba`, `convertirAPlan` contra los endpoints correspondientes.
    - _Requisitos: 8.1, 8.3, 9.2_
  - [x]* 12.3 Specs one-shot de `PaquetesSuscripcionService` y `SuscripcionesService`
    - Verificar URLs, cuerpos y manejo de respuesta con `HttpTestingController`; ejecución one-shot (sin watch).
    - _Requisitos: 3.1, 8.1_

- [x] 13. Pantalla "Planes y suscripciones" y formularios de catálogo
  - [x] 13.1 Reestructurar `planes.ts`/`planes.html` en dos secciones
    - `mat-tab-group` accesible con `aria-label`: "Planes" | "Suscripciones", cada una con rejilla, paginador y botón "Nuevo"; design tokens y WCAG AA.
    - _Requisitos: 10.1, 10.2, 10.3, 10.5, 10.6_
  - [x] 13.2 Ampliar `plan-dialog` con `duracionDias`
    - Campo `duracionDias` con validación cliente `> 365` y mensaje es-MX.
    - _Requisitos: 2.2, 2.3_
  - [x] 13.3 Crear `paquete-suscripcion-dialog`
    - Formulario con módulos+precio (reutiliza `agruparModulosPorGiro`), `maxUsuarios`, moneda, giro, `duracionDias` (≤365), `admitePrueba` que revela `duracionPruebaMeses`; validaciones cliente; WCAG AA + tokens; sin exponer UUID.
    - _Requisitos: 3.1, 3.2, 3.4, 3.5, 3.6, 10.4, 10.5, 10.6_
  - [x]* 13.4 Specs one-shot + axe de `planes` y `paquete-suscripcion-dialog`
    - Dos secciones presentes; validaciones del formulario de paquete; chequeo `axe` de accesibilidad; one-shot aislado.
    - _Requisitos: 10.1, 10.4, 10.6_

- [x] 14. Panel de contrato `plan-suscripcion-dialog`
  - [x] 14.1 Mostrar tipo, estado derivado, días restantes y acciones
    - Mostrar tipo ("Plan"/"Suscripción"), estado con "En prueba"/"Vencida" (derivado comparando `vigenciaFin` con hoy), días restantes, badge "Por vencer" si `porVencer`; acciones "Activar facturación" y "Extender prueba" gated; datepickers ISO; WCAG AA + tokens.
    - _Requisitos: 1.4, 7.1, 7.2, 7.4, 8.1, 8.3_
  - [x]* 14.2 Specs one-shot + axe de `plan-suscripcion-dialog`
    - Etiquetas de estado En prueba/Vencida, días restantes, badge por vencer, visibilidad de acciones gated; chequeo `axe`; one-shot.
    - _Requisitos: 7.1, 7.2, 7.4_

- [x] 15. Alta de empresa `crear-empresa-dialog`
  - [x] 15.1 Selector excluyente Plan | Suscripción + otorgar prueba
    - Radiogroup/toggle excluyente "Plan" | "Suscripción"; checkbox "Otorgar periodo de prueba" visible solo si el paquete `admitePrueba`; validación de elección única; WCAG AA + tokens; sin UUID.
    - _Requisitos: 4.1, 4.2, 4.3, 4.4, 4.5, 10.5, 10.6_
  - [x]* 15.2 Specs one-shot + axe de `crear-empresa-dialog`
    - Elección excluyente (bloqueo de ambos/ninguno), aparición del checkbox de prueba, envío correcto; chequeo `axe`; one-shot.
    - _Requisitos: 4.3, 4.4, 4.5_

- [x] 16. Listado de empresas `empresas.ts`
  - [x] 16.1 Reflejar instrumento vigente en la columna del listado
    - Mostrar `nombreInstrumento` + chip del tipo ("Plan"/"Suscripción") + estado + días restantes/"Por vencer" cuando aplica; sin UUID; WCAG AA + tokens.
    - _Requisitos: 1.4, 7.1, 7.2, 12.6_
  - [x]* 16.2 Specs one-shot + axe de `empresas`
    - Render de `nombreInstrumento`, chip de tipo, estado y aviso "Por vencer"; chequeo `axe`; one-shot.
    - _Requisitos: 1.4, 7.2_

- [x] 17. Actualizar specs frontend existentes afectadas
  - [x]* 17.1 Corregir specs que rompan por nuevos campos
    - Ajustar specs existentes de `planes`, `plan-suscripcion-dialog`, `crear-empresa-dialog`, `empresas` afectadas por los campos añadidos, conservando cobertura.
    - _Requisitos: 12.4_

- [x] 18. Checkpoint frontend
  - [x]* 18.1 Verificar build de producción y specs tocadas (one-shot)
    - Ejecutar `ng build` (configuración production, 0 errores) y las specs tocadas en modo one-shot; corregir fallos. Ante dudas, preguntar al usuario.
    - _Requisitos: 10.6, 12.4_

## Notes

- Las subtareas marcadas con `*` son opcionales (pruebas y verificación) y pueden omitirse para un MVP más rápido; las tareas de nivel superior nunca son opcionales.
- Cada subtarea referencia los requisitos que cubre para trazabilidad.
- Los checkpoints (tareas 10 y 18) validan build y suite de forma incremental.
- Las property tests (jqwik) validan las 10 Correctness Properties del diseño; las pruebas de ejemplo/integración cubren transiciones, gating, alta excluyente, migración y saneo.
- No se incluyen tareas de despliegue ni documentación de usuario.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1", "2.2", "2.3"] },
    { "id": 2, "tasks": ["1.3", "1.4", "2.4", "3.1", "3.3", "5.1"] },
    { "id": 3, "tasks": ["2.5", "2.6", "3.2", "3.4", "4.1", "4.2", "4.3"] },
    { "id": 4, "tasks": ["6.1", "6.2", "6.3", "6.4"] },
    { "id": 5, "tasks": ["6.5", "6.6", "6.7", "7.1"] },
    { "id": 6, "tasks": ["7.2", "7.3", "8.1", "8.2", "8.3"] },
    { "id": 7, "tasks": ["8.4", "9.1"] },
    { "id": 8, "tasks": ["10.1", "11.1"] },
    { "id": 9, "tasks": ["12.1", "12.2", "13.1", "13.2", "13.3", "14.1", "15.1", "16.1"] },
    { "id": 10, "tasks": ["12.3", "13.4", "14.2", "15.2", "16.2", "17.1"] },
    { "id": 11, "tasks": ["18.1"] }
  ]
}
```
