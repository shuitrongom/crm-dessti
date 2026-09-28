# Requerimientos — Cierre de periodo / Candado contable

## Introducción

Bloque enterprise que agrega el **cierre de periodo contable mensual** (candado
contable) al módulo `contabilidad` ya existente. Su propósito es proteger la
integridad fiscal y contable: una vez que un periodo mensual se declara cerrado
(por ejemplo, tras enviar la Contabilidad Electrónica del Anexo 24 al SAT),
**ninguna póliza nueva ni reverso puede afectar ese periodo**, evitando que se
altere información ya declarada. La reapertura queda disponible pero **auditada y
con motivo obligatorio**, para casos de revisión o corrección autorizada.

El bloque es multi-tenant (cada Empresa gestiona sus propios periodos), respeta la
Row-Level Security (Capa 2) y el doble gating de autorización
(`@autorizador.moduloHabilitado('contabilidad')` + permiso RBAC), y no modifica el
comportamiento de las pólizas ya registradas (inmutabilidad contable preservada).

Alcance: **periodo mensual** (año + mes). El cierre de ejercicio anual completo
(póliza de cierre, saldado de cuentas de resultados) queda fuera de este bloque y
sería un bloque posterior.

## Requerimientos

### Requerimiento 1 — Modelo de periodo contable mensual
**Historia:** Como responsable de contabilidad, quiero que el sistema modele cada
mes como un periodo contable con un estado (abierto/cerrado), para poder controlar
qué meses admiten movimientos.

#### Criterios de aceptación
1. CUANDO no existe registro explícito de un periodo (año, mes) para el tenant,
   ENTONCES el sistema DEBE tratarlo como **abierto** por defecto (no se exige
   crear filas por adelantado).
2. El periodo se identifica por `(tenant_id, anio, mes)` de forma **única**; `mes`
   DEBE estar entre 1 y 12 y `anio` en un rango válido (p. ej. 2000–2100).
3. Un periodo SOLO puede estar en estado `abierto` o `cerrado`.
4. El registro de periodo DEBE persistir quién y cuándo lo cerró/reabrió y, en la
   reapertura, el **motivo** (auditoría de transiciones, Req 10).

### Requerimiento 2 — Cierre de periodo
**Historia:** Como responsable de contabilidad, quiero cerrar un periodo mensual,
para congelar la información contable de ese mes.

#### Criterios de aceptación
1. CUANDO se solicita cerrar un periodo (año, mes) que está abierto (o inexistente),
   ENTONCES el sistema DEBE validar primero que **la balanza del periodo cuadre**
   (suma de cargos = suma de abonos del mes); si no cuadra, DEBE rechazar con 422
   informando la diferencia, sin cerrar.
2. CUANDO la balanza cuadra, ENTONCES el sistema DEBE marcar el periodo como
   `cerrado`, registrar `fecha_cierre` y el actor, y auditar el cierre.
3. CUANDO se intenta cerrar un periodo que ya está `cerrado`, ENTONCES el sistema
   DEBE rechazar con 409 (conflicto de estado) sin efectos.
4. El cierre DEBE requerir el permiso `periodo_contable:cerrar` y el módulo
   `contabilidad` habilitado.

### Requerimiento 3 — Candado: bloqueo de pólizas y reversos en periodo cerrado
**Historia:** Como sistema, debo impedir que se registren o reversen pólizas con
fecha en un periodo cerrado, para preservar la información declarada.

#### Criterios de aceptación
1. CUANDO se registra una Póliza_Contable (por captura manual REST o por evento
   automático vía `PolizaContablePort.generarPolizaDeEvento`) cuya `fecha` cae en un
   periodo `cerrado`, ENTONCES el sistema DEBE rechazar con 422 e informar que el
   periodo está cerrado, sin persistir nada.
2. CUANDO se reversa una póliza y la **fecha del reverso** cae en un periodo
   `cerrado`, ENTONCES el sistema DEBE rechazar con 422 sin persistir el reverso.
   La corrección debe hacerse en un periodo abierto.
3. La validación DEBE ser el **único punto** de entrada de pólizas
   (`ServicioContabilidad.registrarPoliza`), de modo que cubra todas las fuentes
   actuales y futuras (facturas, pagos, nómina, depreciación).
4. La validación NO DEBE alterar el comportamiento cuando el periodo está abierto o
   no existe (compatibilidad total con lo existente).

### Requerimiento 4 — Reapertura auditada de periodo
**Historia:** Como responsable de contabilidad autorizado, quiero reabrir un periodo
cerrado indicando un motivo, para corregir información bajo control y trazabilidad.

#### Criterios de aceptación
1. CUANDO se solicita reabrir un periodo `cerrado` con un **motivo no vacío**,
   ENTONCES el sistema DEBE marcarlo como `abierto`, registrar `fecha_reapertura`,
   el actor y el motivo, y auditar la reapertura.
2. CUANDO se intenta reabrir un periodo que está `abierto` (o inexistente), ENTONCES
   el sistema DEBE rechazar con 409 sin efectos.
3. CUANDO el motivo viene vacío o ausente, ENTONCES el sistema DEBE rechazar con 422.
4. La reapertura DEBE requerir el permiso `periodo_contable:reabrir` y el módulo
   `contabilidad` habilitado.

### Requerimiento 5 — Consulta y listado de periodos
**Historia:** Como usuario de contabilidad, quiero ver el estado de los periodos de
un año, para saber qué meses están abiertos o cerrados.

#### Criterios de aceptación
1. CUANDO se consulta el listado de periodos de un año dado, ENTONCES el sistema
   DEBE devolver los 12 meses con su estado (los inexistentes como `abierto`), con
   los metadatos de cierre/reapertura cuando existan.
2. El listado/consulta DEBE requerir el permiso `periodo_contable:leer` y el módulo
   `contabilidad` habilitado.
3. La consulta DEBE estar acotada al tenant (RLS + filtro Hibernate).

### Requerimiento 6 — Seguridad, multi-tenant y auditoría (transversal)
#### Criterios de aceptación
1. La tabla `periodo_contable` DEBE ser tenant-scoped con RLS `tenant_isolation`
   (patrón V2/V33) y el `tenant_id` asignado desde el contexto autenticado, nunca
   desde la petición (Req 23.4).
2. Todos los endpoints DEBEN aplicar el doble gating
   `@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene(...)`.
3. Cierre y reapertura DEBEN auditarse con `AuditoriaPort` (actor, acción, recurso,
   detalle), sin exponer datos sensibles.
4. El bloque NO DEBE romper migraciones, entidades ni pruebas existentes; el arranque
   con `ddl-auto=validate` DEBE seguir validando el esquema.

### Requerimiento 7 — Frontend (vista del cliente)
**Historia:** Como usuario de contabilidad, quiero una vista clara para cerrar y
reabrir periodos, porque es la cara visible del control contable.

#### Criterios de aceptación
1. DEBE existir una vista "Cierre de periodo contable" en `features/contabilidad`,
   con selector de año y una rejilla de los 12 meses mostrando su estado con chips
   accesibles (ok/cerrado).
2. DEBE permitir cerrar un mes (con confirmación y mostrando el resultado de la
   validación de cuadre) y reabrir un mes cerrado (pidiendo el motivo).
3. DEBE ser es-MX, cumplir WCAG 2.1 AA (roles, foco, contraste, textos alternativos)
   y usar los servicios HTTP del frontend, manejando errores 409/422 con mensajes
   claros.
4. NO DEBE degradar ni romper las vistas de contabilidad existentes.
