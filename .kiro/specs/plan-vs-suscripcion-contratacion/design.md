# Documento de Diseño

## Overview

Esta funcionalidad amplía el modelo de contratación de la plataforma multi-tenant Dess-TI para distinguir **dos instrumentos comerciales excluyentes**: el **Plan** (contrato de largo plazo, >1 año, sin prueba, ya existente y resemantizado) y el nuevo **Paquete de Suscripción** (contrato de ≤1 año, con periodo de prueba opcional). Cada Empresa se contrata con **exactamente uno** de los dos.

El diseño se apoya en el código real actual del módulo `platform.empresas` (backend) y `features/plataforma` (frontend), buscando la **mínima ruptura**:

- La entidad `Plan` (catálogo, tabla `plan`, V1+V55) se conserva y se le agrega **duración de contrato**.
- El nuevo catálogo `PaqueteSuscripcion` (tabla `paquete_suscripcion`) replica el patrón de catálogo de plataforma **sin RLS** (igual que `plan` y `giro`, ver `V50__catalogo_giros.sql`).
- La entidad `Suscripcion` (tabla `suscripcion`, V1) **se resemantiza a "Contrato"** conservando su nombre de clase y tabla (decisión D1 abajo). Se le añade un **discriminador de instrumento** (`tipo_instrumento` + `paquete_suscripcion_id` nullable, manteniendo `plan_id` ahora nullable con la regla XOR) y atributos de prueba/facturación.
- El gating (`PlanModulosPlanAdapter`) pasa a considerar `EN_PRUEBA` además de `ACTIVA` y a **verificar `vigenciaFin` en tiempo real** (sin scheduler), resolviendo módulos del Plan **o** del Paquete según el instrumento del Contrato.
- El enriquecimiento `EmpresaDto.planVigente` **conserva el nombre de campo** (para no romper el frontend recién construido) pero se enriquece con el tipo de instrumento, los días restantes y las banderas derivadas `enPrueba`/`vencida`.

### Nomenclatura de cara al usuario (UI)

En la interfaz se usan dos etiquetas simples: **"Plan"** y **"Suscripción"**. El término interno "Contrato" (la entidad `Suscripcion`) **no se muestra al usuario**; en el panel de la Empresa se habla del "instrumento vigente" como "Plan" o "Suscripción".

## Decisiones de diseño (incluye las 5 aprobadas por el usuario)

| # | Decisión | Resolución |
|---|----------|------------|
| **D1** | Resemantización de `Suscripcion` | **Conservar** la clase `Suscripcion` y la tabla `suscripcion` como el "Contrato" (opción menos disruptiva). Se le agrega `tipo_instrumento` (PLAN/SUSCRIPCION), `paquete_suscripcion_id` (nullable FK) y se relaja `plan_id` a nullable, aplicando la regla XOR en dominio + CHECK en BD. **Trade-off:** renombrar la clase a `Contrato` rompería 4 servicios, 2 controladores, ~10 tests y el frontend; el nombre `suscripcion` sobrevive como término técnico interno, mientras la UI habla de "Plan"/"Suscripción". |
| **D2** (aprobada) | Corte por vencimiento | **Tiempo real en el gating**, sin scheduler. `PlanModulosPlanAdapter` evalúa `estado ∈ {ACTIVA, EN_PRUEBA}` **Y** (`vigenciaFin == null` OR `vigenciaFin >= hoy`). El estado `VENCIDA` es **derivado** (calculado para la UI del super_admin); NO se persiste por job. En BD el registro puede seguir `ACTIVA`/`EN_PRUEBA`; el gating lo trata como sin acceso si la fecha pasó. El super_admin puede activar/suspender manualmente. |
| **D3** (aprobada) | Umbral de aviso | Propiedad de aplicación `crm.contratacion.umbral-aviso-dias` con **default 14**, leída vía `@ConfigurationProperties`. Global (no por contrato). |
| **D4** (aprobada) | Migración de datos | Los Contratos actuales (ej. "Demo Factura", ACTIVA sin fin) se clasifican como `tipo_instrumento = SUSCRIPCION` activos. Se **sanea el override inconsistente** de Demo Factura (override de 15 > plan de 4) recortándolo a la intersección con los módulos del plan. Migración **idempotente** (Flyway V64). |
| **D5** (aprobada) | Notificaciones | Avisos "por vencer" **solo en UI del super_admin** (sin correo) en esta entrega. |
| **D6** | Duración del contrato | Vive en el **catálogo** (`Plan.duracionDias > 365`, `PaqueteSuscripcion.duracionDias <= 365`). El Contrato deriva su `vigenciaFin` del catálogo al asignarse. Coherente con "crea el plan para esos 2 años". |
| **D7** | `EmpresaDto.planVigente` | **Conservar el nombre** del campo `planVigente` (no romper el frontend), enriqueciéndolo con `tipoInstrumento`, `nombreInstrumento`, `diasRestantes`, `enPrueba`, `vencida`. |
| **D8** | RBAC | **Reutilizar** permisos existentes: `plan:*` para el catálogo Plan; para el catálogo Paquete y las acciones de contrato se reutiliza `suscripcion:*` (`crear`, `leer`, `listar`, `actualizar`, `cambiar_estado`). **Sin permisos nuevos.** Se documenta abajo. |

## Contexto de código real leído

- `Plan.java` / `PlanDto.java` / `PlanController.java` (`/planes`) / `ServicioPlanes.java` / `CrearPlanCommand.java`: catálogo Plan con `nombre`, `maxUsuarios`, `giroId`, `monedaCodigo`, `preciosModulos` (JSONB), `modulosHabilitados` (derivado), `total`. **Sin duración hoy.**
- `Suscripcion.java` (Contrato): `tenantId`, `planId` (NOT NULL hoy), `estado`, `vigenciaInicio`, `vigenciaFin` (LocalDate), `modulosHabilitados` (override JSONB), `monedaFacturacion`, `version`. Fábricas `crearBasica`/`crear`, transiciones `activar`/`suspender`/`cancelar`, `actualizarVigencia`, `asignarModulos`.
- `EstadoSuscripcion.java` (ACTIVA/SUSPENDIDA/CANCELADA) + `EstadoSuscripcionConverter` + CHECK `ck_suscripcion_estado` (V1, etiquetas minúsculas).
- `PlanModulosPlanAdapter.java`: gating fuente única de verdad. Hoy `findFirstByTenantIdAndEstadoOrderByIdAsc(tenant, ACTIVA)`; override manda si existe, si no hereda del Plan. **No revisa `vigenciaFin`.**
- `ServicioEmpresas.crearEmpresa` (`CrearEmpresaCommand` exige `planId`); enriquecimiento `planVigente` vía `suscripcionVigente(...)` + `aPlanVigenteDto(...)`.
- `ServicioSuscripciones.java` / `SuscripcionController.java` (`/suscripciones`) / `SuscripcionRepository.java`.
- Migraciones: última = **V63** (`V63__social_canales_facebook_tiktok.sql`); nueva será **V64**. `plan`/`giro` son catálogos de plataforma **sin RLS**; `suscripcion` **tiene RLS** (`tenant_isolation`, V2/V53). CHECK del estado en V1.
- RBAC: `V5` siembra `plan:*` y `suscripcion:{crear,leer,listar,actualizar,cambiar_estado}`; `V56` añade `plan:eliminar`. Todos asignados solo a `super_admin`.
- Errores: `ReglaNegocioException` → **HTTP 422**; `RecursoNoEncontradoException` → 404; `ConflictoUnicidadException` → 409.
- Frontend: `plataforma.models.ts` (Plan, Suscripcion, PlanVigente, EstadoSuscripcion, ModuloCatalogo, Moneda, Giro), `planes.ts` + `plan-dialog.ts`, `PlanesService`/`SuscripcionesService`, `plan-suscripcion-dialog.ts`, `asignar-plan-dialog`, `vigencia-dialog`, `crear-empresa-dialog.ts`.

## Architecture

Arquitectura hexagonal existente del módulo `platform.empresas`. El nuevo catálogo y los cambios se insertan sin nuevas capas.

```mermaid
flowchart TB
    subgraph FE[Frontend Angular]
      P[planes.ts: 2 secciones\nPlanes | Suscripciones]
      PD[plan-dialog.ts]
      SD[paquete-suscripcion-dialog.ts NUEVO]
      CE[crear-empresa-dialog.ts\nelección excluyente]
      PANEL[plan-suscripcion-dialog.ts\nestado/dias/acciones]
      PS[PlanesService]
      SS[SuscripcionesService]
      PSS[PaquetesSuscripcionService NUEVO]
    end
    subgraph BE[Backend Spring Boot]
      PC[PlanController /planes]
      PSC[PaqueteSuscripcionController\n/paquetes-suscripcion NUEVO]
      SC[SuscripcionController /suscripciones\n+ conversion/activar-facturacion/extender-prueba]
      EC[EmpresaController /empresas]
      SPlan[ServicioPlanes]
      SPaq[ServicioPaquetesSuscripcion NUEVO]
      SSus[ServicioSuscripciones]
      SEmp[ServicioEmpresas]
      GATE[PlanModulosPlanAdapter\nGATING + corte por fecha]
    end
    subgraph DB[(PostgreSQL)]
      TPlan[(plan +duracion_dias)]
      TPaq[(paquete_suscripcion NUEVO)]
      TSus[(suscripcion +tipo_instrumento\n+paquete_suscripcion_id +facturacion)]
    end
    PS-->PC-->SPlan-->TPlan
    PSS-->PSC-->SPaq-->TPaq
    SS-->SC-->SSus-->TSus
    CE-->EC-->SEmp-->TSus
    PANEL-->SS
    GATE-->TSus
    GATE-->TPlan
    GATE-->TPaq
```

## Components and Interfaces

### Backend — nuevos y modificados

**Nuevo catálogo `PaqueteSuscripcion`** (patrón espejo de `Plan`):
- `PaqueteSuscripcion.java` (entidad, tabla `paquete_suscripcion`, sin RLS). Campos: `id`, `nombre` (único), `maxUsuarios`, `giroId` (FK), `monedaCodigo` (FK), `preciosModulos` (JSONB), `modulosHabilitados` (derivado), `total`, **`duracionDias`** (≤365), **`admitePrueba`** (boolean), **`duracionPruebaMeses`** (nullable, >0 si `admitePrueba`), auditoría/`version`. Fábricas `crear`/`actualizar` con validaciones de dominio (duración ≤365; si `admitePrueba`, `duracionPruebaMeses > 0` y `duracionPruebaMeses` en días ≤ `duracionDias`).
- `PaqueteSuscripcionDto.java`, `CrearPaqueteSuscripcionCommand.java`, `ActualizarPaqueteSuscripcionCommand.java`.
- `PaqueteSuscripcionRepository.java` (`existsByNombre`, `findAll` paginado; `countBy...` para impedir borrado si algún Contrato lo referencia).
- `ServicioPaquetesSuscripcion.java` (CRUD; reutiliza `validarMonedaActiva` y validación de módulos por Giro **extraída** de `ServicioPlanes` a un helper compartido `CatalogoModulosGiroValidacion` para evitar código duplicado).
- `PaqueteSuscripcionController.java` (`/paquetes-suscripcion`, gated con `suscripcion:*`).

**`Plan` modificado:**
- Nueva columna/campo `duracionDias` (>365). Fábricas `crear`/`actualizar` validan `duracionDias > 365` (422 con mensaje "un contrato ≤ 1 año debe ser Paquete_Suscripcion"). `PlanDto` y `CrearPlanCommand`/`ActualizarPlanCommand` incorporan `duracionDias`.

**`Suscripcion` (Contrato) modificado:**
- Nuevo enum interno `TipoInstrumento { PLAN, SUSCRIPCION }` con converter (etiquetas `plan`/`suscripcion`), columna `tipo_instrumento`.
- `plan_id` pasa a nullable; nueva `paquete_suscripcion_id` (nullable FK). **Invariante XOR** en dominio: exactamente uno no nulo según `tipo_instrumento`.
- Campos de facturación de prueba: `facturacion_activada` (boolean, default false), `inicio_facturacion` (LocalDate nullable).
- Nuevas fábricas: `crearDePlan(...)`, `crearDeSuscripcion(...)` (en `ACTIVA`), `crearEnPrueba(...)` (en `EN_PRUEBA`, `vigenciaFin = inicio + duracionPruebaMeses`).
- Nuevas transiciones de dominio: `activarFacturacion(inicioFacturacion, nuevaVigenciaFin, actor)` (solo desde `EN_PRUEBA` → `ACTIVA`, marca `facturacion_activada`); método derivado `estaVencida(hoy)` (no persiste; para DTO/UI).

**`PlanModulosPlanAdapter` modificado** (ver sección Gating).

**Config** `ContratacionProperties` (`@ConfigurationProperties(prefix="crm.contratacion")`) con `umbralAvisoDias` (default 14).

### Frontend — nuevos y modificados
- `planes.ts` / `planes.html`: dos secciones (mat-tab o dos bloques con encabezado): **Planes** y **Suscripciones**.
- `paquete-suscripcion-dialog.ts` (nuevo): formulario de Paquete (nombre, giro, moneda, módulos+precio, maxUsuarios, `duracionDias` ≤365, `admitePrueba`, `duracionPruebaMeses`).
- `PaquetesSuscripcionService` (nuevo): CRUD `/paquetes-suscripcion`.
- `plan-dialog.ts`: añadir campo `duracionDias` (>365) con validación cliente.
- `plan-suscripcion-dialog.ts` (panel): mostrar tipo de instrumento (Plan/Suscripción), estado incluyendo **En prueba**/**Vencida** (derivado), **días restantes**, y acciones **"Activar facturación"** y **"Extender prueba"** (gated `suscripcion:cambiar_estado`/`actualizar`).
- `crear-empresa-dialog.ts`: selector excluyente Plan **o** Suscripción; si Suscripción con prueba, checkbox "Otorgar prueba".
- `plataforma.models.ts`: añadir `PaqueteSuscripcion`, `TipoInstrumento`, ampliar `EstadoSuscripcion` a `'en_prueba'`, extender `PlanVigente` con `tipoInstrumento`, `diasRestantes`, `enPrueba`, `vencida`.

## Data Models

### Enum de estado ampliado

`EstadoSuscripcion` (etiquetas BD en minúsculas, coherentes con el CHECK):
`ACTIVA('activa')`, `EN_PRUEBA('en_prueba')`, `SUSPENDIDA('suspendida')`, `CANCELADA('cancelada')`, `VENCIDA('vencida')`.

**Persistencia de VENCIDA:** por decisión D2 es **derivada** y no se persiste automáticamente. Se añade al enum y al CHECK por compatibilidad y para permitir que el super_admin registre explícitamente un vencido si lo desea, pero el flujo normal deja el registro en `ACTIVA`/`EN_PRUEBA` y el gating/DTO calculan "vencida" comparando `vigenciaFin` con hoy.

### Diagrama de relaciones (textual)

```
plan (catálogo, SIN RLS)                paquete_suscripcion (catálogo NUEVO, SIN RLS)
  id (PK)                                 id (PK)
  nombre (uq)                             nombre (uq)
  max_usuarios                            max_usuarios
  giro_id  -> giro.id                     giro_id -> giro.id
  moneda_codigo -> moneda.codigo          moneda_codigo -> moneda.codigo
  precios_modulos (jsonb)                 precios_modulos (jsonb)
  modulos_habilitados (jsonb)             modulos_habilitados (jsonb)
  duracion_dias  (NUEVO, >365)            duracion_dias (<=365)
                                          admite_prueba (bool)
                                          duracion_prueba_meses (nullable)

suscripcion  (CONTRATO, CON RLS tenant_isolation)
  id (PK)
  tenant_id -> empresa.id
  tipo_instrumento (NUEVO: 'plan' | 'suscripcion')
  plan_id -> plan.id                (NUEVO: NULLABLE)
  paquete_suscripcion_id -> paquete_suscripcion.id  (NUEVO: NULLABLE)
  estado ('activa'|'en_prueba'|'suspendida'|'cancelada'|'vencida')
  vigencia_inicio / vigencia_fin
  modulos_habilitados (override, jsonb, null=hereda)
  moneda_facturacion
  facturacion_activada (NUEVO, bool)
  inicio_facturacion  (NUEVO, date nullable)
  -- Invariante XOR: (tipo='plan' AND plan_id NOT NULL AND paquete_suscripcion_id NULL)
  --                 OR (tipo='suscripcion' AND paquete_suscripcion_id NOT NULL AND plan_id NULL)
```

### DTOs afectados

- **`PaqueteSuscripcionDto`** (nuevo): espejo de `PlanDto` + `duracionDias`, `admitePrueba`, `duracionPruebaMeses`.
- **`PlanDto`**: + `duracionDias`.
- **`SuscripcionDto`**: + `tipoInstrumento`, `paqueteSuscripcionId` (uso interno, no UUID a UI), y estado ya cubre EN_PRUEBA/VENCIDA.
- **`EmpresaDto.PlanVigenteDto`** (nombre conservado, D7): + `tipoInstrumento` (`'plan'|'suscripcion'`), `nombreInstrumento` (reemplaza semánticamente a `nombrePlan`; se conserva `nombrePlan` con el nombre del instrumento para compatibilidad), `diasRestantes` (Integer, null si `vigenciaFin` null), `enPrueba` (bool), `vencida` (bool derivado). `planId`/`suscripcionId` se mantienen; se añade `paqueteSuscripcionId`.

## Migración Flyway — V64

Archivo: `V64__contratacion_plan_vs_suscripcion.sql`. Idempotente (`IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`, `ON CONFLICT DO NOTHING`), envuelta en la transacción de Flyway. Contenido:

1. **`plan.duracion_dias`**: `ALTER TABLE plan ADD COLUMN IF NOT EXISTS duracion_dias INTEGER NOT NULL DEFAULT 730;` (default 730 = 2 años, seguro para Planes legado; el dominio exige >365). Backfill implícito por default. Se puede quitar el default tras el backfill en la misma migración con `ALTER COLUMN ... DROP DEFAULT`. CHECK `ck_plan_duracion_dias CHECK (duracion_dias > 365)`.

2. **Tabla `paquete_suscripcion`** (patrón de catálogo de plataforma, sin RLS; réplica de `plan`+`giro`):
   ```sql
   CREATE TABLE IF NOT EXISTS paquete_suscripcion (
     id UUID NOT NULL DEFAULT gen_random_uuid(),
     nombre VARCHAR(120) NOT NULL,
     max_usuarios INTEGER NOT NULL,
     giro_id UUID,
     moneda_codigo VARCHAR(3),
     precios_modulos JSONB NOT NULL DEFAULT '{}'::jsonb,
     modulos_habilitados JSONB NOT NULL DEFAULT '[]'::jsonb,
     duracion_dias INTEGER NOT NULL,
     admite_prueba BOOLEAN NOT NULL DEFAULT FALSE,
     duracion_prueba_meses INTEGER,
     version BIGINT NOT NULL DEFAULT 0,
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     created_by VARCHAR(255), updated_by VARCHAR(255),
     CONSTRAINT pk_paquete_suscripcion PRIMARY KEY (id),
     CONSTRAINT uq_paquete_suscripcion_nombre UNIQUE (nombre),
     CONSTRAINT fk_paquete_giro FOREIGN KEY (giro_id) REFERENCES giro(id),
     CONSTRAINT fk_paquete_moneda FOREIGN KEY (moneda_codigo) REFERENCES moneda(codigo),
     CONSTRAINT ck_paquete_max_usuarios CHECK (max_usuarios >= 0),
     CONSTRAINT ck_paquete_duracion_dias CHECK (duracion_dias > 0 AND duracion_dias <= 365),
     CONSTRAINT ck_paquete_prueba CHECK (
       (admite_prueba = FALSE) OR (duracion_prueba_meses IS NOT NULL AND duracion_prueba_meses > 0)
     )
   );
   -- NOTA: NO se habilita RLS (dato de plataforma, coherente con plan/giro).
   ```

3. **Columnas en `suscripcion`** (Contrato):
   ```sql
   ALTER TABLE suscripcion ADD COLUMN IF NOT EXISTS tipo_instrumento VARCHAR(20) NOT NULL DEFAULT 'suscripcion';
   ALTER TABLE suscripcion ADD COLUMN IF NOT EXISTS paquete_suscripcion_id UUID;
   ALTER TABLE suscripcion ADD COLUMN IF NOT EXISTS facturacion_activada BOOLEAN NOT NULL DEFAULT FALSE;
   ALTER TABLE suscripcion ADD COLUMN IF NOT EXISTS inicio_facturacion DATE;
   ALTER TABLE suscripcion ALTER COLUMN plan_id DROP NOT NULL;
   ALTER TABLE suscripcion ADD CONSTRAINT fk_suscripcion_paquete
       FOREIGN KEY (paquete_suscripcion_id) REFERENCES paquete_suscripcion(id);
   ```

4. **CHECK de estado ampliado** (drop + recreate):
   ```sql
   ALTER TABLE suscripcion DROP CONSTRAINT IF EXISTS ck_suscripcion_estado;
   ALTER TABLE suscripcion ADD CONSTRAINT ck_suscripcion_estado
     CHECK (estado IN ('activa','en_prueba','suspendida','cancelada','vencida'));
   ```

5. **CHECK XOR de instrumento:**
   ```sql
   ALTER TABLE suscripcion ADD CONSTRAINT ck_suscripcion_instrumento CHECK (
     (tipo_instrumento = 'plan' AND plan_id IS NOT NULL AND paquete_suscripcion_id IS NULL)
     OR
     (tipo_instrumento = 'suscripcion' AND paquete_suscripcion_id IS NOT NULL AND plan_id IS NULL)
   );
   ```
   **Orden crítico:** los datos existentes tienen `plan_id NOT NULL` y `tipo='suscripcion'` (por el default), lo que **violaría** el XOR. Por eso el paso 6 (clasificación) debe ejecutarse **antes** de añadir el CHECK XOR, o el CHECK debe añadirse `NOT VALID` y validarse tras el backfill. Diseño elegido: **clasificar primero** (paso 6) y luego añadir el CHECK.

6. **Clasificación de datos existentes (D4)** — antes del CHECK XOR:
   Los Contratos existentes referencian un Plan real. Para respetar el XOR con `tipo='suscripcion'` habría que moverlos a un Paquete, pero no existe Paquete equivalente. Decisión: los Contratos existentes se clasifican como **`tipo_instrumento = 'plan'`** (su `plan_id` es real), que es lo veraz respecto al dato actual:
   ```sql
   UPDATE suscripcion SET tipo_instrumento = 'plan'
   WHERE plan_id IS NOT NULL AND paquete_suscripcion_id IS NULL;
   ```
   > Aclaración sobre el enunciado: la instrucción pedía clasificarlos como "SUSCRIPCIÓN activo". Sin embargo, el CHECK XOR exige que un Contrato de tipo `suscripcion` tenga `paquete_suscripcion_id` (que no existe para los datos legado). Clasificarlos como `plan` es la única opción **coherente con el dato real** (referencian un `plan_id`) y con la invariante XOR. Se documenta este ajuste de sub-decisión (**D4-bis**). Si el usuario prefiere convertirlos a Suscripción, la migración debería además crear un `paquete_suscripcion` semilla equivalente y re-apuntar `paquete_suscripcion_id`; se deja como alternativa registrada.

7. **Saneo del override inconsistente de "Demo Factura" (D4, Req 11.3):** recorta el override al subconjunto válido (intersección con `modulos_habilitados` del plan referenciado). Idempotente y sin tocar el gating:
   ```sql
   UPDATE suscripcion s SET modulos_habilitados = (
     SELECT COALESCE(jsonb_agg(m), '[]'::jsonb)
     FROM jsonb_array_elements_text(s.modulos_habilitados) AS m
     WHERE m IN (SELECT jsonb_array_elements_text(p.modulos_habilitados)
                 FROM plan p WHERE p.id = s.plan_id)
   )
   WHERE s.modulos_habilitados IS NOT NULL
     AND s.plan_id IS NOT NULL
     AND EXISTS (
       SELECT 1 FROM jsonb_array_elements_text(s.modulos_habilitados) AS m
       WHERE m NOT IN (SELECT jsonb_array_elements_text(p.modulos_habilitados)
                       FROM plan p WHERE p.id = s.plan_id)
     );
   ```

## Gating: cambios en `PlanModulosPlanAdapter`

El método `modulosHabilitadosDe(UUID tenantId)` cambia así:

1. **Selección del Contrato vigente:** en lugar de `findFirstByTenantIdAndEstadoOrderByIdAsc(tenant, ACTIVA)`, se cargan los Contratos del tenant y se selecciona el vigente considerando **estados que conceden acceso**: `ACTIVA` **o** `EN_PRUEBA` (nuevo repositorio: `findByTenantIdAndEstadoInOrderByIdAsc(tenant, [ACTIVA, EN_PRUEBA])`, tomando el primero estable).
2. **Verificación de fecha en tiempo real (D2):** si el Contrato tiene `vigenciaFin != null` y `vigenciaFin < hoy` (usando el `Clock` inyectado / `LocalDate.now`), se devuelve **lista vacía** (mismo resultado que ausencia de Contrato, Req 6.4). Estados `SUSPENDIDA`/`CANCELADA` no se seleccionan, por lo que también producen cero módulos.
3. **Resolución de módulos por instrumento:** si hay override en el Contrato, manda el override (comportamiento actual). Si no hay override:
   - `tipo_instrumento == PLAN` → `planRepository.findById(planId).getModulosHabilitados()`.
   - `tipo_instrumento == SUSCRIPCION` → `paqueteSuscripcionRepository.findById(paqueteSuscripcionId).getModulosHabilitados()`.

Se inyecta `PaqueteSuscripcionRepository` y `Clock` en el adaptador. El aislamiento RLS (fijar `app.current_tenant` con `TenantSessionInitializer`) se conserva idéntico. El override, si existe, ahora está validado ⊆ instrumento (Plan o Paquete) en el servicio.

**Casos borde del gating:**
- Contrato `EN_PRUEBA` con `vigenciaFin` = hoy → concede (>= hoy).
- Contrato `EN_PRUEBA` con `vigenciaFin` = ayer → deniega (cero módulos).
- Contrato `ACTIVA` sin `vigenciaFin` → concede siempre.
- Override vacío `[]` → cero módulos (sin cambio).
- Instrumento inexistente (anómalo) → cero módulos (fail-safe).

## Reglas de negocio — dónde vive cada validación

| Regla | Ubicación | Error |
|-------|-----------|-------|
| Exclusividad `plan_id` XOR `paquete_suscripcion_id` | Dominio `Suscripcion` (fábricas) + CHECK BD | 422 |
| Un solo Contrato vigente por Empresa (Req 1.3) | `ServicioSuscripciones`/`ServicioEmpresas` (consulta previa) | 422 |
| Plan duración > 365 | Dominio `Plan.crear/actualizar` | 422 |
| Paquete duración ≤ 365 | Dominio `PaqueteSuscripcion.crear/actualizar` | 422 |
| Prueba > 0 y prueba ≤ duración | Dominio `PaqueteSuscripcion` | 422 |
| Override ⊆ módulos del instrumento | `CatalogoModulosGiroValidacion` (helper reutilizado por alta y edición) | 422 |
| Giro/moneda obligatorios en catálogo | `ServicioPlanes`/`ServicioPaquetesSuscripcion` | 422 |
| Activar facturación solo desde EN_PRUEBA | Dominio `Suscripcion.activarFacturacion` | 422 |
| Transición desde CANCELADA prohibida | Dominio `Suscripcion` (ya existe) | 422 |
| Corte por vencimiento | `PlanModulosPlanAdapter` (tiempo real) | acceso denegado |

**Transiciones de estado:**
```
EN_PRUEBA --activar facturación--> ACTIVA (facturacion_activada=true, ajusta vigenciaFin al paquete)
ACTIVA/EN_PRUEBA --suspender--> SUSPENDIDA
SUSPENDIDA --activar--> ACTIVA
cualquiera(no CANCELADA) --cancelar--> CANCELADA (final)
ACTIVA/EN_PRUEBA + vigenciaFin<hoy --(derivado)--> VENCIDA (solo UI/gating, no persiste)
Conversión Suscripción->Plan (Req 9.2): crea Contrato de Plan nuevo y cierra (cancela) el anterior, preservando exclusividad.
```

## Endpoints REST

**Nuevos — `/paquetes-suscripcion`** (gated con `suscripcion:*`, D8):
- `POST /paquetes-suscripcion` (`suscripcion:crear`) → 201
- `PUT /paquetes-suscripcion/{id}` (`suscripcion:actualizar`)
- `GET /paquetes-suscripcion/{id}` (`suscripcion:leer`)
- `GET /paquetes-suscripcion?page&size` (`suscripcion:listar`)
- `DELETE /paquetes-suscripcion/{id}` (`suscripcion:cambiar_estado` — reutilizado; se documenta que no hay `paquete:eliminar` propio). 422 si algún Contrato lo referencia.

**Acciones de contrato en `/suscripciones`:**
- `POST /suscripciones/{id}/activar-facturacion` (`suscripcion:cambiar_estado`), cuerpo opcional `{ inicioFacturacion? }`. EN_PRUEBA→ACTIVA; 422 si no está EN_PRUEBA (Req 8).
- `POST /suscripciones/{id}/extender-prueba` (`suscripcion:actualizar`), cuerpo `{ nuevaVigenciaFin }` acotada a la duración del paquete (Req 7.4/8).
- `POST /suscripciones/{id}/convertir-a-plan` (`suscripcion:crear`), cuerpo `{ planId }` (Req 9.2).

**Alta de Empresa (`/empresas`)**: `CrearEmpresaRequest`/`CrearEmpresaCommand` evolucionan a elección excluyente: `planId` **o** `paqueteSuscripcionId` (exactamente uno; 422 si ambos o ninguno, Req 4.3/4.4) + `otorgarPrueba` (bool, solo aplica si el paquete `admitePrueba`).

**RBAC (D8):** se reutilizan `plan:*` (catálogo Plan) y `suscripcion:*` (catálogo Paquete + acciones de contrato). No se crean permisos nuevos → **no hay migración RBAC**. Justificación: el Paquete es conceptualmente una suscripción y el super_admin es el único titular de ambos conjuntos; añadir `paquete:*` incrementaría superficie sin beneficio de segregación (solo super_admin los posee).

## Frontend

- **Pantalla "Planes y suscripciones"** (`planes.ts`): dos secciones diferenciadas (pestañas `mat-tab-group` accesibles con `aria-label`), "Planes" y "Suscripciones", cada una con su rejilla de tarjetas, paginador y botón "Nuevo". Reutiliza `StateContainer`, `PageHeader`, `PaginatorIntlEs`, `ConfirmDialogService`.
- **Formularios**: `plan-dialog` añade `duracionDias` (>365, validación cliente + mensaje). `paquete-suscripcion-dialog` (nuevo) con módulos+precio (reutiliza `agruparModulosPorGiro`), maxUsuarios, moneda, giro, `duracionDias` (≤365), `admitePrueba` (checkbox) que revela `duracionPruebaMeses`.
- **Panel de contrato** (`plan-suscripcion-dialog`): muestra tipo ("Plan"/"Suscripción"), estado con etiqueta española incluyendo **"En prueba"** y **"Vencida"** (derivada comparando `vigenciaFin` con hoy), **días restantes**, badge "Por vencer" si `diasRestantes <= umbral`. Acciones: Asignar/cambiar, Actualizar vigencia, Activar/Suspender/Cancelar (existentes) + **"Activar facturación"** y **"Extender prueba"** (nuevas, gated).
- **Alta de empresa** (`crear-empresa-dialog`): toggle/radiogroup excluyente "Plan" | "Suscripción"; al elegir Suscripción con `admitePrueba`, checkbox "Otorgar periodo de prueba".
- **Resolución de nombres (no UUID):** todo se muestra por `nombre`/`nombreInstrumento`; `planId`/`paqueteSuscripcionId`/`suscripcionId` son internos.
- **Datepickers ISO** `YYYY-MM-DD` (patrón `vigencia-dialog` existente).
- **WCAG AA + design tokens:** headings jerárquicos, `aria-live` en errores, focus management de diálogos, contraste con tokens del sistema; sin colores hardcodeados.

## Enriquecimiento `EmpresaDto.planVigente` y columna del listado

`ServicioEmpresas.suscripcionVigente(...)` y `aPlanVigenteDto(...)` se amplían:
- Resuelven el nombre del instrumento por **lote** para Planes (`planRepository.findAllById`) **y** Paquetes (`paqueteSuscripcionRepository.findAllById`), según `tipo_instrumento` de cada Contrato vigente (ambas tablas sin RLS → `findAllById` visible).
- `diasRestantes = ChronoUnit.DAYS.between(hoy, vigenciaFin)` si `vigenciaFin != null`, si no `null`.
- `enPrueba = estado == EN_PRUEBA`; `vencida = estado ∈ {ACTIVA,EN_PRUEBA} && vigenciaFin != null && vigenciaFin < hoy`.
- La regla `suscripcionVigente` amplía el filtro de "activa" para incluir `EN_PRUEBA`.

En el listado del super_admin (`empresas.ts`), la columna hoy llamada "Plan" pasa a "Contrato / Plan": muestra `nombreInstrumento` + chip del tipo ("Plan"/"Suscripción") + estado + días restantes/"Por vencer" cuando aplica.

## Config del umbral de aviso (D3)

`application.yml`, bloque `crm`:
```yaml
crm:
  contratacion:
    umbral-aviso-dias: ${CONTRATACION_UMBRAL_AVISO_DIAS:14}
```
Se lee con `@ConfigurationProperties(prefix="crm.contratacion")` en `ContratacionProperties`. El backend expone el umbral en un endpoint de configuración de plataforma existente o lo incorpora al enriquecimiento (`diasRestantes` ya viaja; el frontend recibe también el umbral vía `GET /empresas` metadata o un `GET /contratacion/config`). Decisión: el backend calcula `porVencer` (bool) y lo incluye en `PlanVigenteDto` para no acoplar el umbral al frontend; el umbral vive solo en backend.

## Testing Strategy

Esta funcionalidad **sí es apta para property-based testing** en su lógica pura (validaciones de duración/prueba, invariante XOR, resolución de gating por fecha, subconjunto de override, conversión round-trip de estados). El proyecto ya usa **jqwik** (ver `*PropertyTest.java` y `.jqwik-database`). Las partes de infraestructura (RLS, controllers, persistencia) se cubren con tests de integración/ejemplo.

**Backend:**
- **Property tests (jqwik, ≥100 iteraciones)** — ver sección Correctness Properties.
- **Unit/ejemplo:** transición EN_PRUEBA→ACTIVA en `activarFacturacion`; rechazo desde estado ≠ EN_PRUEBA (422); rechazo transición desde CANCELADA; cálculo `diasRestantes`.
- **Integración (`@SpringBootTest` + Testcontainers, mismo patrón que `ServicioSuscripcionesTest`):** gating concede con EN_PRUEBA vigente y deniega con vencido; alta de empresa excluyente (Plan / Paquete / ambos-422 / ninguno-422 / prueba→EN_PRUEBA); migración V64 (arranque Flyway + `ddl-auto=validate` verde); saneo del override demo.
- Ejecución: `mvn -o test` o `mvn -o -Dtest=... test`.

**Frontend:**
- Tests **one-shot** (sin watch), aislados por el flakiness de `axe`: `paquete-suscripcion-dialog.spec.ts`, ampliación de `plan-suscripcion-dialog.spec.ts` (estado En prueba/Vencida, días, acciones), `crear-empresa-dialog.spec.ts` (elección excluyente), `planes.spec.ts` (dos secciones), más chequeo `axe` de accesibilidad.

**Configuración property tests:** cada test tagged con `// Feature: plan-vs-suscripcion-contratacion, Property N: <texto>`; librería jqwik ya presente; mínimo 100 iteraciones.

## Correctness Properties

*Una propiedad es una característica o comportamiento que debe cumplirse en todas las ejecuciones válidas del sistema: un enunciado formal, verificable por máquina, de lo que el sistema debe hacer.*

### Property 1: Exclusividad de instrumento (XOR)
*Para todo* Contrato construido por las fábricas de dominio, exactamente uno de `planId` / `paqueteSuscripcionId` es no nulo y coincide con `tipo_instrumento`.
**Validates: Requirements 1.1, 1.2**

### Property 2: Duración del Plan siempre mayor a un año
*Para toda* creación/actualización de Plan, la operación se acepta si y solo si `duracionDias > 365`.
**Validates: Requirements 2.2, 2.3**

### Property 3: Duración del Paquete siempre de un año o menos
*Para toda* creación/actualización de Paquete_Suscripcion, la operación se acepta si y solo si `0 < duracionDias <= 365`.
**Validates: Requirements 3.3, 3.4, 9.1**

### Property 4: Coherencia de la configuración de prueba
*Para todo* Paquete_Suscripcion que admite prueba, la operación se acepta si y solo si `duracionPruebaMeses > 0` y la prueba en días no excede `duracionDias`.
**Validates: Requirements 3.5, 3.6**

### Property 5: El gating concede acceso solo con estado y vigencia válidos
*Para todo* Contrato y fecha actual, el gating devuelve módulos no vacíos solo si `estado ∈ {ACTIVA, EN_PRUEBA}` y (`vigenciaFin == null` o `vigenciaFin >= hoy`); en caso contrario devuelve cero módulos.
**Validates: Requirements 5.6, 6.1, 6.2, 6.3, 6.4**

### Property 6: Resolución de módulos según instrumento
*Para todo* Contrato vigente sin override, el conjunto de módulos resuelto por el gating es exactamente el `modulosHabilitados` del instrumento referenciado (Plan o Paquete según `tipo_instrumento`).
**Validates: Requirements 6.1, 12.6**

### Property 7: El override es siempre subconjunto del instrumento
*Para todo* override aceptado sobre un Contrato, todos sus módulos pertenecen a los `modulosHabilitados` del Plan o Paquete del Contrato; cualquier módulo ajeno provoca rechazo (422).
**Validates: Requirements 11.1, 11.2**

### Property 8: Activar facturación solo desde EN_PRUEBA
*Para todo* Contrato, `activarFacturacion` transiciona a `ACTIVA` con `facturacion_activada = true` si y solo si el estado previo es `EN_PRUEBA`; desde cualquier otro estado se rechaza (422).
**Validates: Requirements 8.1, 8.4**

### Property 9: Otorgar prueba fija la vigencia correcta
*Para toda* alta de Empresa con Paquete que admite prueba y prueba otorgada, el Contrato queda en `EN_PRUEBA` con `vigenciaFin = vigenciaInicio + duracionPruebaMeses`.
**Validates: Requirements 4.5**

### Property 10: Estabilidad del enum de estado persistido
*Para todo* `EstadoSuscripcion`, `desdeValorBd(estado.valorBd())` devuelve el mismo estado, y `valorBd()` es una etiqueta en minúsculas admitida por el CHECK de la base de datos (round-trip).
**Validates: Requirements 5.1, 12.5**

## Error Handling

- Violaciones de reglas de negocio (duración, prueba, XOR, override, transiciones, exclusividad de contrato vigente) → `ReglaNegocioException` → **HTTP 422** con mensaje en español es-MX explicando la causa y la acción correctiva (ej. "un contrato ≤ 1 año debe registrarse como Suscripción").
- Instrumento/Empresa/Contrato inexistente → `RecursoNoEncontradoException` → **404**.
- Nombre de catálogo duplicado → `ConflictoUnicidadException` → **409** (traduce también carreras contra los índices únicos).
- Carreras de concurrencia optimista (`@Version`) se propagan como hoy.
- El gating **nunca lanza** por vencimiento: devuelve cero módulos (fail-safe, deny-by-default), traducido a 403 por el `Autorizador`.
- CHECKs de BD (estado, XOR, duración) actúan como red de seguridad final; el dominio los anticipa para dar mensajes claros antes de tocar la BD.

## Compatibilidad, migración y no-regresión

**Qué se resemantiza (sin renombrar):**
- `Suscripcion` = "Contrato" (clase y tabla conservan nombre; D1). Los métodos existentes (`crear`, `crearBasica`, `activar`, `suspender`, `cancelar`, `actualizarVigencia`, `asignarModulos`) se conservan; `crearBasica` sigue creando un Contrato de tipo `plan` (compatibilidad con `ServicioEmpresas` y tests actuales).
- `EmpresaDto.planVigente` conserva su nombre (D7); solo se añaden campos (los existentes no cambian de tipo → el frontend actual sigue compilando).

**Qué se agrega:**
- Catálogo `PaqueteSuscripcion` + endpoints + servicio + frontend.
- Columnas nuevas en `plan` y `suscripcion` (con defaults seguros).
- Estados `EN_PRUEBA`/`VENCIDA`, discriminador de instrumento, atributos de facturación.

**No-regresión de los 1177 tests:**
- Los defaults de migración (`plan.duracion_dias=730`, `suscripcion.tipo_instrumento='plan'`) mantienen los Contratos existentes válidos frente al CHECK XOR (clasificados como `plan` con `plan_id` real).
- `crearBasica` sigue produciendo Contratos válidos (`tipo=plan`, `plan_id` no nulo), por lo que `ServicioEmpresasTest`/`ServicioSuscripcionesTest` no se rompen salvo por firmas ampliadas (se actualizan a la par).
- `PlanModulosPlanAdapterTest`: se amplía para cubrir EN_PRUEBA/vencimiento; los casos ACTIVA-sin-fin siguen concediendo (comportamiento preservado).
- El enum ampliado es aditivo; el converter y el CHECK aceptan las nuevas etiquetas.
- Arranque con `ddl-auto=validate`: el mapeo JPA debe coincidir exactamente con V64 (nombres/nulabilidad de columnas) — se valida en el test de integración de arranque.

## Riesgos

- **R1 — Orden de migración (CHECK XOR vs datos legado):** mitigado ejecutando la clasificación (paso 6) antes del CHECK, o añadiéndolo `NOT VALID` + `VALIDATE CONSTRAINT`.
- **R2 — Interpretación de D4:** el enunciado pedía clasificar legado como "Suscripción"; el XOR obliga a `plan` (D4-bis documentada). Requiere visto bueno del usuario o creación de un Paquete semilla.
- **R3 — `plan_id` deja de ser NOT NULL:** cualquier consulta/derivación que asuma `plan_id` presente debe revisarse (ej. `countByPlanId` para borrado de Plan sigue válido; el enriquecimiento ahora ramifica por tipo).
- **R4 — Coste N+1 en enriquecimiento:** ya acotado a la página (≤100); se mantiene el patrón `applyTenant` por fila + resolución de nombres por lote (Plan y Paquete).
- **R5 — Frontend previo:** conservar `planVigente` y tipos existentes evita romper `plan-suscripcion-dialog`, `asignar-plan-dialog`, `vigencia-dialog`; los nuevos campos son opcionales/aditivos.
- **R6 — Flakiness de axe (frontend):** tests one-shot y aislados, sin watch.
