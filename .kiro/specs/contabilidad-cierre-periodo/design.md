# Diseño — Cierre de periodo / Candado contable

## Visión general

Se agrega un subpaquete `contabilidad.polizas` (mismo submódulo donde vive
`ServicioContabilidad`, para evitar dependencias circulares) que modela el
**periodo contable mensual** y su candado. La pieza central es una validación
insertada en el **único punto de entrada de pólizas** (`registrarPoliza` /
`reversarPoliza`), de modo que el candado aplique por igual a la captura manual y a
los eventos automáticos.

Se respeta la arquitectura hexagonal existente: dominio puro (entidad + máquina de
estados), puerto de consulta, servicio de aplicación, adaptadores REST y de
persistencia, migración Flyway con RLS y permisos idempotentes.

Dirección de dependencias: `polizas` (ServicioContabilidad) → `PeriodoContablePort`
(puerto en `polizas.application`) ← implementado por el servicio de cierre. El
servicio de cierre puede depender del repositorio de reportes (cuadre) sin ciclos.

## Componentes

### 1. Persistencia — Migración V72
`V72__contabilidad_cierre_periodo.sql`:
- Tabla `periodo_contable` (tenant-scoped):
  - `id UUID PK`, `tenant_id UUID NOT NULL FK empresa`
  - `anio SMALLINT NOT NULL` (CHECK 2000..2100)
  - `mes SMALLINT NOT NULL` (CHECK 1..12)
  - `estado VARCHAR(8) NOT NULL` (CHECK IN ('abierto','cerrado'))
  - `fecha_cierre TIMESTAMPTZ`, `cerrado_por VARCHAR(255)`
  - `fecha_reapertura TIMESTAMPTZ`, `reabierto_por VARCHAR(255)`, `motivo_reapertura VARCHAR(500)`
  - `version BIGINT`, marcas de auditoría (`created_at/by`, `updated_at/by`)
  - `UNIQUE (tenant_id, anio, mes)`, índice `(tenant_id, anio)`
  - RLS `tenant_isolation` (ENABLE + FORCE + POLICY), patrón V2/V33.
- Permisos idempotentes: `('periodo_contable','cerrar'|'reabrir'|'leer'|'listar')`
  con `ON CONFLICT DO NOTHING`, enlazados al rol `contabilidad`
  (`a0000000-0000-0000-0000-00000000000b`).

Solo se crean filas cuando un periodo se cierra o reabre; el estado por defecto
(sin fila) es **abierto**.

### 2. Dominio
- `EstadoPeriodo` (enum): `ABIERTO("abierto")`, `CERRADO("cerrado")` con `valorBd()`,
  `desdeValorBd()`, y máquina de estados pura (`MaquinaEstados`): `ABIERTO→CERRADO`,
  `CERRADO→ABIERTO` (ambas transiciones válidas; no hay estado final).
- `EstadoPeriodoConverter` (`AttributeConverter`): persiste `valorBd()`.
- `PeriodoContable` (entidad JPA, extiende `TenantScopedEntity`,
  `@Table("periodo_contable")`):
  - Campos: `id`, `anio`, `mes`, `estado` (con converter), `fechaCierre`,
    `cerradoPor`, `fechaReapertura`, `reabiertoPor`, `motivoReapertura`.
  - Fábrica pura `crearCerrado(anio, mes, actor)`: crea una fila ya en estado
    `cerrado` (para el caso común: un periodo abierto implícito que se cierra por
    primera vez), con `fechaCierre = now`.
  - `cerrar(actor)`: transición `ABIERTO→CERRADO` validada por la máquina; setea
    `fechaCierre`, `cerradoPor`. Lanza `TransicionInvalidaException` (409) si ya
    está cerrado.
  - `reabrir(motivo, actor)`: transición `CERRADO→ABIERTO`; exige motivo no vacío
    (si no, `ReglaNegocioException` 422); setea `fechaReapertura`, `reabiertoPor`,
    `motivoReapertura`. Lanza 409 si no está cerrado.
  - Validación de `anio`/`mes` en la fábrica (rango).

### 3. Puerto de consulta del candado
`polizas.application.PeriodoContablePort`:
- `boolean estaCerrado(int anio, int mes)` — consulta pura para el candado.
Implementado por el servicio de cierre (`ServicioCierrePeriodo`), inyectado en
`ServicioContabilidad`. Así `polizas` no depende de detalles del cierre, solo del
puerto.

### 4. Repositorio de persistencia
`PeriodoContableRepository` (Spring Data JPA):
- `Optional<PeriodoContable> buscarPorAnioMes(int anio, int mes)`
- `List<PeriodoContable> listarPorAnio(int anio)` (ordenado por mes)
Ambos acotados al tenant por el filtro Hibernate + RLS.

### 5. Servicio de aplicación
`ServicioCierrePeriodo` (implements `PeriodoContablePort`):
- `estaCerrado(anio, mes)`: `buscarPorAnioMes(...).map(p -> p.getEstado()==CERRADO).orElse(false)`.
- `cerrarPeriodo(anio, mes)`:
  1. Valida rango.
  2. Valida el **cuadre del periodo**: consulta
     `reportesContablesRepository.agregarSaldosPorCuenta(primerDia, ultimoDia)` (donde
     `ym = YearMonth.of(anio, mes)`), suma cargos y abonos; si difieren, lanza 422
     con la diferencia (mismo criterio que la balanza del Anexo 24).
  3. Carga o crea el periodo: si existe y está `cerrado` → 409; si está `abierto`,
     `cerrar(actor)`; si no existe, `crearCerrado(anio, mes, actor)`.
  4. Persiste y **audita** (`AuditoriaPort`, acción "cerrar", recurso
     "periodo_contable").
- `reabrirPeriodo(anio, mes, motivo)`:
  1. Valida motivo no vacío (422).
  2. Carga el periodo: si no existe o está `abierto` → 409; si `cerrado`,
     `reabrir(motivo, actor)`, persiste y audita (acción "reabrir").
- `consultarAnio(anio)`: devuelve los 12 meses con su estado (los inexistentes como
  `abierto`), fusionando lo persistido con el default.
- `actorActual()` y `auditar(...)` replican el estilo de `ServicioContabilidad`.

### 6. Integración del candado en ServicioContabilidad
- Inyectar `PeriodoContablePort` en `ServicioContabilidad`.
- En `registrarPoliza`: tras construir la póliza y **antes de persistir**, calcular
  `YearMonth` de `poliza.getFecha()` y si `periodoContablePort.estaCerrado(anio, mes)`
  → lanzar `ReglaNegocioException` (422) "El periodo AAAA-MM está cerrado; no admite
  nuevas pólizas."
- En `reversarPoliza`: validar el `YearMonth` de la **fecha del reverso**
  (`fechaReverso`) con el mismo criterio antes de persistir.
- No se toca la firma de `generarPolizaDeEvento` (delega en `registrarPoliza`, por lo
  que hereda el candado automáticamente).

Nota de dependencias para evitar ciclos: `ServicioCierrePeriodo` NO depende de
`ServicioContabilidad`; `ServicioContabilidad` depende del puerto
`PeriodoContablePort`. Spring resuelve la inyección del puerto con la implementación
`ServicioCierrePeriodo`. Ambos viven en el submódulo `contabilidad`.

### 7. REST
`PeriodoContableController` (`@RequestMapping("/contabilidad/periodos")`):
- `GET /contabilidad/periodos?anio=` → 200 con la lista de 12 meses
  (`periodo_contable:leer`).
- `POST /contabilidad/periodos/cerrar` body `{anio, mes}` → 200 con el periodo
  cerrado (`periodo_contable:cerrar`). 422 si no cuadra; 409 si ya cerrado.
- `POST /contabilidad/periodos/reabrir` body `{anio, mes, motivo}` → 200 con el
  periodo reabierto (`periodo_contable:reabrir`). 422 si motivo vacío; 409 si no
  estaba cerrado.
Todos con `@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene(...)`.
DTOs: `PeriodoContableDto` (anio, mes, estado, fechaCierre, cerradoPor,
fechaReapertura, reabiertoPor, motivoReapertura), requests `CerrarPeriodoRequest`,
`ReabrirPeriodoRequest` con Bean Validation.

## Manejo de errores
- 422 (`ReglaNegocioException`): balanza descuadrada al cerrar; motivo de reapertura
  vacío; póliza/reverso en periodo cerrado.
- 409 (`TransicionInvalidaException` / `ConflictoUnicidadException`): cerrar un
  periodo ya cerrado; reabrir uno no cerrado; carrera de UNIQUE (tenant, anio, mes).
- 404: no aplica (el periodo inexistente se trata como abierto).
Se reutilizan los manejadores globales existentes.

## Estrategia de pruebas
- **Dominio (property/unit):** máquina de estados (transiciones válidas/ inválidas),
  fábrica `crearCerrado`, `reabrir` con motivo vacío (422), `valorBd`/`desdeValorBd`.
- **Candado (unit):** con un `PeriodoContablePort` simulado, `registrarPoliza` y
  `reversarPoliza` rechazan cuando el periodo está cerrado y permiten cuando abierto.
- **Backend build:** `mvnw -o -DskipTests clean package` verde con JDK 21.
- **Frontend:** specs Vitest del servicio (cerrar/reabrir/listar, manejo de
  errores) y de la vista (render de rejilla y acciones). `ng build` producción OK.
- **Local:** reiniciar backend (aplica V72), verificar arranque (Hibernate valida el
  mapeo) y probar en pantalla que las vistas de contabilidad no se rompen.

## Frontend
- `features/contabilidad/cierre-periodo/`: componente standalone con selector de año,
  rejilla de 12 meses (chips estado abierto/cerrado), acciones cerrar (confirmación)
  y reabrir (diálogo con motivo). Muestra `fechaCierre`/`cerradoPor` y
  `motivoReapertura` cuando aplican.
- `services/cierre-periodo.service.ts` + `models/cierre-periodo.models.ts`.
- Ruta `/empresa/contabilidad/cierre-periodo`; ítem nuevo en `navigation.ts` sección
  "Contabilidad y finanzas".
- Estilos con las clases existentes del módulo; es-MX; WCAG 2.1 AA.
