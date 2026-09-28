# Plan de implementación — Cierre de periodo / Candado contable

- [ ] 1. Migración V72 `V72__contabilidad_cierre_periodo.sql`
  - Tabla `periodo_contable` tenant-scoped (anio, mes, estado, metadatos de
    cierre/reapertura, version, auditoría), UNIQUE (tenant_id, anio, mes), índice
    (tenant_id, anio), CHECKs de rango y estado.
  - RLS `tenant_isolation` (ENABLE + FORCE + POLICY), patrón V2/V33.
  - Permisos `periodo_contable:{cerrar,reabrir,leer,listar}` idempotentes enlazados
    al rol contabilidad `a0000000-0000-0000-0000-00000000000b`.
  - _Requerimientos: 1, 6_

- [ ] 2. Dominio: `EstadoPeriodo` + `EstadoPeriodoConverter` + `PeriodoContable`
  - Enum con máquina de estados pura (abierto↔cerrado), converter JPA, entidad
    tenant-scoped con fábrica `crearCerrado`, `cerrar(actor)`, `reabrir(motivo,actor)`
    y validación de rango/motivo.
  - _Requerimientos: 1, 2, 4_

- [ ] 3. Puerto + repositorio de persistencia
  - `PeriodoContablePort` (estaCerrado) en `polizas.application`.
  - `PeriodoContableRepository` (buscarPorAnioMes, listarPorAnio).
  - _Requerimientos: 3, 5_

- [ ] 4. Servicio `ServicioCierrePeriodo` (implements PeriodoContablePort)
  - cerrarPeriodo (valida cuadre vía ReportesContablesRepository), reabrirPeriodo
    (motivo), consultarAnio (12 meses), estaCerrado; auditoría.
  - DTO `PeriodoContableDto`.
  - _Requerimientos: 2, 4, 5, 6_

- [ ] 5. Integrar candado en `ServicioContabilidad`
  - Inyectar `PeriodoContablePort`; validar periodo cerrado en `registrarPoliza` y
    `reversarPoliza` (422) antes de persistir.
  - _Requerimientos: 3_

- [ ] 6. REST `PeriodoContableController` + requests
  - GET listar por año, POST cerrar, POST reabrir; doble gating de autorización;
    manejo 409/422.
  - _Requerimientos: 2, 4, 5, 6_

- [ ] 7. Pruebas backend + build
  - Tests de dominio (máquina de estados, reabrir motivo vacío) y del candado en el
    servicio (mock del puerto). `mvnw -o -DskipTests clean package` verde (JDK 21) y
    tests unitarios nuevos.
  - _Requerimientos: 2, 3, 4_

- [ ] 8. Frontend: servicio + modelos + vista + navegación + specs Vitest
  - `cierre-periodo/` (rejilla año/meses, cerrar, reabrir con motivo),
    `cierre-periodo.service.ts`, `cierre-periodo.models.ts`, ruta y navegación.
  - Specs Vitest (servicio + vista). `ng build` producción OK.
  - _Requerimientos: 7_

- [ ] 9. Verificación final e integración local
  - Reiniciar backend (aplica V72), verificar health/arranque, probar en pantalla que
    contabilidad no se rompe; build backend y frontend verdes.
  - _Requerimientos: 6, 7_
