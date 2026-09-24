# Diseño — Contabilidad Electrónica SAT (Anexo 24)

## Visión general

Este bloque agrega la **Contabilidad Electrónica** del Anexo 24 al módulo
`contabilidad` existente, de punta a punta (backend hexagonal + frontend Angular
enterprise). Se apoya al máximo en lo que ya existe y solo añade la capa fiscal:

- **Amarre SAT**: una columna nueva `codigo_agrupador_sat` en `cuenta_contable`
  (tabla ya existente, con RLS) y un catálogo maestro de códigos agrupadores del
  SAT (dato de plataforma, SIN RLS, como `permiso`/`plan`).
- **Generadores XML**: tres servicios de dominio puros que producen el XML del
  Catálogo, la Balanza y las Pólizas conforme al esquema 1.3 del SAT, reutilizando
  el catálogo de cuentas y las pólizas ya persistidas y el cálculo de balanza
  existente.
- **REST**: un controlador nuevo `/contabilidad/contabilidad-electronica/**` con
  vista previa (JSON) y descarga (XML) por periodo.
- **Frontend**: una vista nueva "Contabilidad Electrónica (SAT)" y la integración
  del amarre de código agrupador en la gestión del catálogo de cuentas.

No se toca `platform.monetizacion` (facturación de la plataforma, distinta). No se
reimplementa la balanza: se consume `ServicioEstadosFinancieros` /
`BalanzaComprobacionDto` y los repositorios de `contabilidad.reportes`.

## Arquitectura

Nuevo submódulo `com.dessti.crm.contabilidad.electronica` siguiendo el layout
hexagonal ya usado por `contabilidad.polizas` / `contabilidad.reportes`:

```
contabilidad/electronica/
├── domain/
│   ├── CodigoAgrupadorSat.java            (entidad JPA, dato de plataforma sin RLS)
│   ├── NivelCuentaSat.java                (enum: nivel 1/2 derivado del código)
│   ├── NaturalezaSat.java                 (enum D/A para el XML)
│   ├── xml/                               (generadores XML PUROS, sin Spring)
│   │   ├── GeneradorCatalogoXml.java
│   │   ├── GeneradorBalanzaXml.java
│   │   ├── GeneradorPolizasXml.java
│   │   └── XmlUtil.java                    (escape, formato importe 2 decimales)
│   └── modelo/                            (modelos de entrada a los generadores)
│       ├── CuentaCatalogoSat.java
│       ├── RenglonBalanzaSat.java
│       └── PolizaSat.java / TransaccionSat.java
├── application/
│   ├── ServicioContabilidadElectronica.java   (@Service @Transactional)
│   ├── CatalogoAgrupadorSatPort.java + adapter default
│   ├── VistaPreviaCatalogoDto / VistaPreviaBalanzaDto / VistaPreviaPolizasDto
│   ├── ArchivoXmlDto.java                  (nombre + contenido + mimeType)
│   └── CodigoAgrupadorSatDto.java
└── adapter/
    ├── in/rest/
    │   ├── ContabilidadElectronicaController.java
    │   └── CatalogoAgrupadorSatController.java  (GET catálogo oficial de códigos)
    └── out/persistence/
        └── CodigoAgrupadorSatRepository.java
```

El amarre por cuenta se hace **extendiendo la entidad existente**
`CuentaContable` con un campo `codigoAgrupadorSat` y un método
`amarrarCodigoAgrupador(...)`, más un endpoint `PATCH /contabilidad/cuentas/{id}/codigo-agrupador`
en el `CuentaContableController` existente (o un método nuevo en su servicio).

### Multi-tenant y seguridad

- `cuenta_contable` ya es tenant-scoped con RLS; la columna nueva
  `codigo_agrupador_sat` hereda ese aislamiento.
- `codigo_agrupador_sat_catalogo` (catálogo maestro del SAT) es **dato de
  plataforma común a todos los tenants**: SIN RLS, solo lectura para las empresas
  (igual que `permiso`, `plan`), sembrado por la migración.
- Todos los endpoints se protegen con
  `@PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('<recurso>','<op>')")`.
- El RFC del encabezado XML proviene de `Empresa.getRfc()` resuelto por el
  `tenant_id` del TenantContext (nunca de la petición). Se accede vía un puerto
  `DatosFiscalesEmpresaPort` para no acoplar `contabilidad` a `platform.empresas`
  directamente (se implementa con un adapter que consulta `EmpresaRepository`).

## Modelo de datos (migración V71)

`V71__contabilidad_electronica_sat.sql`:

1. **Columna nueva en `cuenta_contable`** (tabla con RLS existente):
   ```sql
   ALTER TABLE cuenta_contable ADD COLUMN codigo_agrupador_sat VARCHAR(10);
   -- índice de apoyo para la exportación por tenant
   CREATE INDEX ix_cuenta_contable_tenant_agrupador
       ON cuenta_contable (tenant_id, codigo_agrupador_sat);
   ```
   Nullable: una cuenta puede no estar amarrada. No lleva FK dura al catálogo
   maestro (el catálogo es dato de plataforma; la validez se valida en la
   aplicación contra el catálogo para permitir evolución del catálogo sin migrar
   datos), pero se documenta la relación lógica.

2. **Catálogo maestro de códigos agrupadores del SAT** (dato de plataforma, SIN
   RLS):
   ```sql
   CREATE TABLE codigo_agrupador_sat_catalogo (
       codigo      VARCHAR(10)  NOT NULL,   -- p. ej. '101.01'
       nombre      VARCHAR(200) NOT NULL,   -- p. ej. 'Efectivo'
       nivel       SMALLINT     NOT NULL,   -- 1 = mayor, 2 = subcuenta
       naturaleza  VARCHAR(1)   NOT NULL,   -- 'D' deudora / 'A' acreedora
       codigo_padre VARCHAR(10),            -- jerarquía (nivel 2 -> su nivel 1)
       CONSTRAINT pk_codigo_agrupador_sat PRIMARY KEY (codigo),
       CONSTRAINT ck_agrup_nivel CHECK (nivel IN (1, 2)),
       CONSTRAINT ck_agrup_natur CHECK (naturaleza IN ('D', 'A'))
   );
   ```
   Se siembra con el subconjunto representativo y de uso común del Apartado B del
   Anexo 24 (100, 101, 102, 105, 107, 108, 110, 113, 115, 118, 120, 201, 205, 206,
   209, 210, 216, 301, 304, 305, 401, 402, 403, 501, 502, 601, 602, 603, 700,
   etc., con sus subcuentas de primer nivel más frecuentes). El catálogo completo
   del SAT tiene cientos de claves; se siembra un conjunto amplio y ampliable, con
   un comentario que explica cómo extenderlo. La generación XML NO depende de que
   el catálogo esté completo: solo valida que el código amarrado exista.

3. **Permisos nuevos** (patrón idempotente, enlazados al rol `contabilidad`
   `a0000000-0000-0000-0000-00000000000b`):
   ```sql
   INSERT INTO permiso (recurso, operacion) VALUES
     ('contabilidad_electronica', 'leer'),
     ('contabilidad_electronica', 'exportar'),
     ('cuenta_contable', 'actualizar')
   ON CONFLICT (recurso, operacion) DO NOTHING;
   -- enlace al rol contabilidad
   ```

## Componentes y contratos

### Dominio: generadores XML puros

Sin dependencias de Spring ni de JPA. Reciben modelos planos y devuelven `String`
(el XML). Deterministas: ordenan cuentas por `codigo`/`codAgrup` y pólizas por
`fecha, id`. Formatean importes con `BigDecimal.setScale(2, HALF_UP)`. Escapan
caracteres XML. Usan `StringBuilder` (o `javax.xml.stream`/StAX) — se elige
**StAX** (`XMLStreamWriter`) para garantizar XML bien formado y escape correcto.

- `GeneradorCatalogoXml.generar(EncabezadoSat, List<CuentaCatalogoSat>) : String`
  → raíz `catalogocuentas:Catalogo` con namespace 1.3, atributos Version, RFC, Mes,
  Anio; hijos `Ctas` (CodAgrup, NumCta, Desc, Nivel, Natur).
- `GeneradorBalanzaXml.generar(EncabezadoSat, TipoEnvio, List<RenglonBalanzaSat>)`
  → raíz `BCE:Balanza` con Version, RFC, Mes, Anio, TipoEnvio; hijos `Ctas`
  (NumCta, SaldoIni, Debe, Haber, SaldoFin).
- `GeneradorPolizasXml.generar(EncabezadoSat, TipoSolicitud, List<PolizaSat>)`
  → raíz `PLZ:Polizas` con Version, RFC, Mes, Anio, TipoSolicitud; hijos `Poliza`
  (NumUnIdenPol, Fecha, Concepto) con `Transaccion` (NumCta, DesCta, Concepto,
  Debe, Haber).

### Aplicación: `ServicioContabilidadElectronica`

`@Service @Transactional(readOnly = true)`. Orquesta:

- `vistaPreviaCatalogo(anio, mes)` → cuenta cuántas cuentas se exportarán y lista
  las cuentas activas SIN código agrupador (advertencia).
- `exportarCatalogo(anio, mes)` → `ArchivoXmlDto` (nombre `<RFC><AAAA><MM>CT.xml`).
- `vistaPreviaBalanza(anio, mes)` → totales y advertencia de descuadre.
- `exportarBalanza(anio, mes)` → `ArchivoXmlDto` (`...BN.xml`).
- `vistaPreviaPolizas(anio, mes)` → conteo de pólizas y transacciones.
- `exportarPolizas(anio, mes)` → `ArchivoXmlDto` (`...PL.xml`).

Dependencias (puertos): `CuentaContableRepository` (existente),
`PolizaContableRepository` + `MovimientoPolizaRepository` (existentes),
`ServicioEstadosFinancieros`/repos de reportes (para la balanza: saldo inicial y
movimientos del periodo), `CatalogoAgrupadorSatPort`, `DatosFiscalesEmpresaPort`,
`AuditoriaPort`.

**Cálculo de la balanza XML (Req 3.4):** `SaldoIni` = suma de (cargos−abonos según
naturaleza) de todas las pólizas ANTERIORES al primer día del periodo; `Debe`/`Haber`
= cargos/abonos del periodo; `SaldoFin` = `SaldoIni + Debe − Haber` (cuentas
deudoras) o `SaldoIni − Debe + Haber` (acreedoras). Se reutilizan las proyecciones
nativas de `contabilidad.reportes` (SaldoCuentaProjection) acotando por fecha.

### Amarre de código agrupador (extiende `contabilidad.polizas`)

- `CuentaContable`: nuevo campo `codigoAgrupadorSat` (String, nullable) + método
  `amarrarCodigoAgrupador(String codigo, String actor)` (valida no vacío; la
  existencia en el catálogo la valida el servicio).
- `ServicioContabilidad` (o servicio de cuentas): método
  `amarrarCodigoAgrupador(UUID cuentaId, String codigo)` que valida contra
  `CatalogoAgrupadorSatPort`, aplica y audita. 422 si el código no existe.
- `CuentaContableController`: `PATCH /contabilidad/cuentas/{id}/codigo-agrupador`
  con `@autorizador.tiene('cuenta_contable','actualizar')`.
- `CuentaContableDto`: incluir `codigoAgrupadorSat`.

### REST

`ContabilidadElectronicaController` (`/contabilidad/contabilidad-electronica`):
- `GET /catalogo/preview?anio=&mes=` → VistaPreviaCatalogoDto (perm leer)
- `GET /catalogo/xml?anio=&mes=` → XML descarga (perm exportar)
- `GET /balanza/preview?anio=&mes=` → VistaPreviaBalanzaDto (perm leer)
- `GET /balanza/xml?anio=&mes=` → XML descarga (perm exportar)
- `GET /polizas/preview?anio=&mes=` → VistaPreviaPolizasDto (perm leer)
- `GET /polizas/xml?anio=&mes=` → XML descarga (perm exportar)

`CatalogoAgrupadorSatController` (`/contabilidad/codigos-agrupadores-sat`):
- `GET ?q=` → lista/busca códigos agrupadores oficiales (perm leer). Cacheable.

Las descargas devuelven `ResponseEntity<byte[]>` con `Content-Disposition:
attachment; filename="..."` y `Content-Type: application/xml`.

## Frontend (Angular enterprise)

Módulo `features/contabilidad` existente. Se añade:

- **Ruta** `contabilidad-electronica` en `contabilidad.routes.ts`, lazy, protegida
  por `guardaPorPermiso('contabilidad_electronica','leer')` (el gating de módulo ya
  lo aplica el padre `contabilidad`).
- **Vista** `contabilidad-electronica/contabilidad-electronica.ts` (+ html + reutiliza
  `contabilidad.scss`): PageHeader "Contabilidad Electrónica (SAT)"; selector de
  periodo (mes/año); tres tarjetas (Catálogo, Balanza, Pólizas) cada una con botón
  "Vista previa" (muestra conteos y advertencias con chips semánticos) y botón
  "Descargar XML". Estados de carga/vacío/error.
- **Amarre de código agrupador** integrado en la vista/diálogo del catálogo de
  cuentas: un `mat-autocomplete` que consulta `/contabilidad/codigos-agrupadores-sat`
  y hace `PATCH` del amarre. (Si hoy no existe una vista de catálogo de cuentas en
  el front, se agrega una mínima "Catálogo de cuentas" con el amarre; se verificará
  en la tarea correspondiente.)
- **Servicio** `contabilidad-electronica.service.ts` con los métodos de preview,
  descarga (responseType blob) y catálogo de agrupadores. La descarga dispara un
  `Blob` y `URL.createObjectURL` para el navegador.
- **Navegación**: nuevo ítem "Contabilidad Electrónica (SAT)" en `navigation.ts`,
  sección "Contabilidad y finanzas", `modulo: 'contabilidad'`, visible por
  `tienePermiso('contabilidad_electronica','leer')`.

## Manejo de errores

- 403 (sin permiso o módulo deshabilitado) — `@PreAuthorize`.
- 422 (`ReglaNegocioException`) — código agrupador inexistente; balanza descuadrada
  al exportar (según decisión: la exportación de balanza que no cuadra advierte
  pero el SAT la rechazaría, así que se bloquea con 422 informando la diferencia).
- 404 (`RecursoNoEncontradoException`) — cuenta inexistente al amarrar.
- El front captura y muestra el mensaje sin romper.

## Decisiones de diseño

1. **StAX para XML**: `XMLStreamWriter` garantiza escape y buen formato; evita
   concatenación manual frágil. Salida UTF-8, dos decimales.
2. **Catálogo agrupador como dato de plataforma sin RLS**: es común a todas las
   empresas (como `permiso`); se siembra en la migración y se puede ampliar. La
   generación no exige catálogo completo, solo valida el código amarrado.
3. **Sin FK dura cuenta→catálogo**: el amarre guarda el código como texto validado
   por la aplicación, permitiendo evolucionar el catálogo sin migraciones de datos
   y sin acoplar la RLS de `cuenta_contable` a una tabla sin RLS.
4. **Reutilización de la balanza existente**: el saldo inicial/movimientos/saldo
   final se derivan de las pólizas ya persistidas; no se duplica el mayor.
5. **Generación, no envío**: se produce el XML válido descargable; el envío al
   Buzón Tributario y el sellado con e.firma quedan fuera (comportamiento estándar).
6. **Nombres de archivo conforme al SAT**: `<RFC><AAAA><MM>{CT|BN|PL}.xml`.
7. **Determinismo**: orden estable por código/fecha para pruebas reproducibles.

## Estrategia de pruebas

- **Dominio puro** (`*PropertyTest` / unit): 
  - `GeneradorCatalogoXml`/`Balanza`/`Polizas` producen XML bien formado, con el
    namespace y atributos correctos, importes a 2 decimales, escape de caracteres,
    y orden determinista.
  - Coherencia de la balanza XML: `SaldoFin = SaldoIni ± Debe/Haber` según
    naturaleza (property test).
- **Integración (Testcontainers)**:
  - Migración V71 aplica y siembra el catálogo agrupador; RLS del amarre por tenant
    (una empresa no ve el amarre de otra).
  - Endpoints preview/descarga responden 200 con XML; 403 sin permiso.
- **Frontend** (`ng test`, jsdom+TestBed): el servicio construye URLs/params y
  maneja blobs; la vista renderiza preview/advertencias y dispara descargas.
- **ArchUnit**: el nuevo submódulo respeta las dependencias hexagonales.

El build de backend (`mvnw -o -DskipTests clean package` con JDK 21) y de frontend
(`ng build --configuration production`) debe quedar verde, más las specs.
