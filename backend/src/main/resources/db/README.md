# Migraciones de base de datos (Flyway)

Este directorio contiene el versionado del esquema de PostgreSQL del CRM de
Anuncios Luminosos, gestionado con **Flyway**.

## Estructura

- `migration/` — migraciones versionadas que Flyway aplica en orden.
  - `V1__baseline_plataforma_seguridad.sql` — **baseline**: esquema base de
    **Plataforma y Seguridad** (empresa/tenant, plan, suscripcion, usuario,
    rol, permiso, rol_permiso, usuario_rol). No incluye tablas de modulos de
    negocio (se agregan en migraciones posteriores).
  - `V2__rls_multi_tenant.sql` — **Row-Level Security (Capa 2, Req 23)**:
    habilita RLS (`ENABLE` + `FORCE ROW LEVEL SECURITY`) y la politica
    `tenant_isolation` en las tablas tenant-scoped (`suscripcion`, `usuario`,
    `rol`). Ver la seccion "Row-Level Security" mas abajo.
- `roles/app_role.sql` — **script de referencia (no ejecutable en automatico)**
  para crear el rol de base de datos de la aplicacion con privilegio minimo.

## Convenciones de esquema (design.md)

- **PK**: `UUID`. Generacion en BD con `gen_random_uuid()` (extension
  `pgcrypto`), permitiendo tambien que la aplicacion aporte su propio UUID.
- **Multi-tenant (Req 23)**: las tablas tenant-scoped incluyen
  `tenant_id UUID NOT NULL` con indice por `tenant_id`. La tabla `empresa` ES
  el tenant, por lo que su `id` cumple el rol de `tenant_id`.
- **Concurrencia optimista (Req 49)**: columna `version BIGINT NOT NULL
  DEFAULT 0` en las entidades de negocio modificables.
- **Auditoria temporal**: `created_at`/`updated_at` como `timestamptz` (UTC) y
  `created_by`/`updated_by` como texto (identificador del actor).
- **Nombres**: `snake_case` para tablas y columnas.

## Decisiones tomadas en la baseline (V1)

1. **Generacion de UUID**: se habilita `pgcrypto` y se usa `gen_random_uuid()`
   como `DEFAULT` de las PK. `pgcrypto` viene con PostgreSQL contrib y no
   requiere componentes externos (a diferencia de `uuid-ossp`).
2. **Unicidad de login**: `usuario.identificador_acceso` es **unico global**,
   no por tenant, porque el login resuelve al usuario antes de conocer el
   tenant (el `tenant_id` se deriva del usuario/JWT, Req 23.4) y el super_admin
   tiene `tenant_id` NULL. El nombre de `rol` es unico **por tenant** para los
   personalizados y global para los predefinidos de sistema.
3. **RLS diferida**: la Row-Level Security **no** se habilita en V1; se activa
   en V2 (tarea 4.2) junto con las politicas `tenant_isolation` y
   `app.current_tenant` por transaccion.

## Row-Level Security (Capa 2, Req 23) — V2

La RLS es la **segunda capa** del aislamiento multi-empresa; complementa (no
sustituye) el filtro de Hibernate de la aplicacion (Capa 1). Aunque una consulta
olvidara el filtro de aplicacion, PostgreSQL igual impide el acceso cruzado.

### Tablas cubiertas

- **Con RLS estricta (tenant-scoped puro)**: `suscripcion` (y todas las tablas de
  negocio de tareas 15+). Solo contienen filas con `tenant_id` no nulo y nunca se
  consultan fuera de un tenant, por lo que conservan la politica estricta de V2.
- **Con RLS CONSCIENTE DE PLATAFORMA (V48)**: `rol` y `usuario`. Ademas de filas
  de empresa (`tenant_id` no nulo) contienen filas de PLATAFORMA compartidas
  (`tenant_id NULL`): los roles predefinidos y el usuario `super_admin`. Ver la
  seccion "RLS consciente de plataforma (V48)".
- **Sin RLS (catalogos de plataforma)**: `plan`, `permiso` — comunes a todas las
  empresas, administrados por `super_admin`.
- **Sin RLS (puentes N:M sin `tenant_id` propio)**: `rol_permiso`,
  `usuario_rol` — su aislamiento se hereda de `rol`/`usuario`.
- **`empresa` (la entidad tenant): SIN RLS, por decision.** `empresa.id` cumple
  el rol de `tenant_id`. El ambito de **plataforma** (`super_admin`) necesita
  listar/crear/suspender empresas **sin** un tenant fijado (Req 24); habilitar
  RLS sobre `empresa` con `id = app.current_tenant` impediria esas operaciones y
  la insercion de la primera empresa. El aislamiento de los **datos de negocio**
  se garantiza porque toda tabla de negocio referencia `empresa` por `tenant_id`
  y esas tablas si estan protegidas por RLS; la confidencialidad de la lista de
  empresas se controla por RBAC (tarea 10, solo `super_admin`).

### Politica `tenant_isolation`

```sql
ALTER TABLE <tabla> ENABLE ROW LEVEL SECURITY;
ALTER TABLE <tabla> FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON <tabla>
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
```

- `USING` filtra lecturas/actualizaciones/borrados; `WITH CHECK` valida las
  escrituras (INSERT/UPDATE), impidiendo insertar filas de otro tenant.
- `FORCE ROW LEVEL SECURITY`: aplica las politicas **tambien al dueno** de la
  tabla (Flyway suele migrar con el rol owner), no solo a roles normales.
- **`current_setting('app.current_tenant', true)`** con el 2.º parametro `true`
  (missing_ok) devuelve `NULL` si la variable **no** esta fijada, en vez de
  fallar.

### Comportamiento fail-safe (variable ausente)

Si `app.current_tenant` **no** esta establecida, la condicion queda
`tenant_id = NULL` → se evalua a NULL (no TRUE) para toda fila, por lo que **no
se ve ni se modifica NINGUNA fila tenant-scoped** (deny-by-default). Una peticion
que olvide fijar el tenant no expone datos, en lugar de exponerlos todos.

### Ambito de plataforma (super_admin, Req 24.3)

Las operaciones de plataforma **no** fijan `app.current_tenant`: sobre tablas
tenant-scoped no ven filas (correcto, el super_admin no opera datos de negocio),
y sobre `plan`/`permiso`/`empresa` (sin RLS) operan con normalidad. Cuando un
caso de uso de plataforma deba tocar una empresa concreta, fijara explicitamente
el tenant objetivo antes de la operacion
(`TenantSessionInitializer.applyTenant(uuid)`).

### Mecanismo de aplicacion (`SET LOCAL` por transaccion)

El backend fija la variable **por transaccion** sobre la **misma conexion**:

- `TenantSessionInitializer` ejecuta el equivalente a
  `SET LOCAL app.current_tenant = :uuid` mediante
  `set_config('app.current_tenant', ?, true)` (parametro vinculado, sin
  interpolacion → sin inyeccion) a traves de `Session.doWork(...)`.
- `TenantRlsAspect` (Spring AOP) envuelve los metodos `@Transactional` de
  `com.dessti.crm..*` y aplica el tenant del `TenantContext` una vez abierta la
  transaccion. Usa `SET LOCAL` (alcance transaccion), de modo que el valor no se
  filtra a otras operaciones que reutilicen la conexion del pool.

### Patron reutilizable para tablas de negocio futuras

Cada migracion que cree una nueva tabla tenant-scoped (tareas 15+) debe repetir
en su propia migracion el bloque `ENABLE`/`FORCE`/`CREATE POLICY tenant_isolation`
mostrado arriba. No hace falta tocar el codigo Java: al extender
`TenantScopedEntity` y ejecutarse dentro de un caso de uso `@Transactional`, el
aspecto fija `app.current_tenant` automaticamente.

## RLS consciente de plataforma (V48) — `rol` y `usuario`

La politica unica `tenant_isolation` de V2 sobre `rol`/`usuario` solo funcionaba
porque las pruebas ejecutaban Flyway y las consultas como SUPERUSUARIO (que se
salta RLS). Con el rol de aplicacion correcto (NOBYPASSRLS) rompia tres flujos
legitimos: (A) el SEED de plataforma (roles predefinidos con `tenant_id NULL`),
(B) el LOGIN (resuelve al usuario por identificador global ANTES de fijar tenant)
y (C) la LECTURA de roles predefinidos al aprovisionar usuarios/construir claims.

`rol` y `usuario` NO son tablas de negocio puras: contienen catalogo COMPARTIDO
de plataforma (`tenant_id NULL`). V48 reemplaza la politica unica por politicas
POR COMANDO (PostgreSQL las combina con OR):

```sql
-- Lectura: plataforma (NULL) OR tenant actual OR ventana de autenticacion
CREATE POLICY rol_lectura ON rol FOR SELECT USING (
    tenant_id IS NULL
    OR current_setting('app.current_tenant', true) IS NULL
    OR current_setting('app.current_tenant', true) = ''
    OR tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Escritura estricta al tenant actual (INSERT/UPDATE/DELETE)
CREATE POLICY rol_insercion    ON rol FOR INSERT WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
CREATE POLICY rol_actualizacion ON rol FOR UPDATE USING (...) WITH CHECK (...);
CREATE POLICY rol_borrado      ON rol FOR DELETE USING (tenant_id = current_setting('app.current_tenant', true)::uuid);
```

`usuario` replica el mismo patron y ademas una politica `usuario_login_mutacion`
(FOR UPDATE) que permite el incremento de intentos fallidos/bloqueo durante el
login (ventana sin tenant), sin cambiar el `tenant_id` de la fila.

**Garantias que se mantienen (Req 23):** una empresa NUNCA ve ni escribe filas de
otra empresa (sus `tenant_id` no nulos difieren). Las filas de plataforma
(`tenant_id NULL`) son de LECTURA compartida y su ESCRITURA esta reservada al ROL
MIGRADOR (BYPASSRLS); el rol de aplicacion no puede crearlas ni modificarlas.
Dentro de la aplicacion SIEMPRE hay tenant fijado, por lo que la Capa 1 (filtro
Hibernate) + estas politicas siguen acotando el listado de usuarios/roles al
tenant. La "ventana de autenticacion" (sin tenant) solo existe en el flujo de
login, que resuelve por identificador UNICO GLOBAL.

## Separacion migrador / runtime (Req 23)

Las migraciones (DDL y semillas de plataforma con `tenant_id NULL`) se ejecutan
con un ROL MIGRADOR dedicado (`dessti_migrator`), owner del esquema y con
`BYPASSRLS` SOLO para migrar. El runtime del backend se conecta con el rol de
aplicacion (`dessti_app`, NOBYPASSRLS). En `application.yml`:

```yaml
spring:
  flyway:
    user: ${DB_MIGRATOR_USER:${DB_USER}}       # migrador (o el datasource por defecto)
    password: ${DB_MIGRATOR_PASSWORD:${DB_PASSWORD}}
  datasource:
    username: ${DB_USER}                        # runtime (dessti_app)
```

Si no se definen `DB_MIGRATOR_USER`/`PASSWORD`, Flyway reutiliza el datasource
(compatibilidad con las pruebas de integracion Testcontainers, que migran y
consultan como el superusuario del contenedor). El aprovisionamiento local de
ambos roles esta en `local-dev/crear-base.sql`.
## Requisito del rol de base de datos (Req 23)

El rol de PostgreSQL usado por la aplicacion **no debe ser superusuario ni
tener `BYPASSRLS`**, para que las politicas RLS (tarea 4.2) siempre apliquen.
Ver `roles/app_role.sql`. Verificacion:

```sql
SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'crm_app';
-- rolsuper y rolbypassrls deben ser FALSE
```

## Configuracion

Flyway esta habilitado en `application.yml` y reutiliza el datasource
(`DB_URL`/`DB_USER`/`DB_PASSWORD`, resueltos desde variables de entorno, Req 11).
`spring.jpa.hibernate.ddl-auto=validate`: el esquema lo gestiona Flyway, no
Hibernate.
