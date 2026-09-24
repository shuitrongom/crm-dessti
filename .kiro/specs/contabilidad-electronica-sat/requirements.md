# Requisitos — Contabilidad Electrónica SAT (Anexo 24)

## Introducción

El bloque de **Contabilidad y Finanzas** del CRM Dess-TI ya es maduro: catálogo de
cuentas, pólizas de partida doble (balanceadas, inmutables, con reverso), Cuentas
por Cobrar, Cuentas por Pagar, estados financieros (balance general, estado de
resultados, balanza de comprobación) y tesorería. Sin embargo, para alcanzar el
nivel **enterprise/premium** que exige el mercado mexicano, falta la pieza fiscal
más importante y de mayor valor: la **Contabilidad Electrónica** obligatoria ante
el SAT conforme al **Anexo 24 de la Resolución Miscelánea Fiscal**.

Este entregable agrega, de punta a punta (backend Spring Boot hexagonal + frontend
Angular enterprise), la capacidad de que cada Empresa (tenant) genere y descargue
los archivos XML oficiales de Contabilidad Electrónica:

1. **Catálogo de Cuentas** (XML) — con el amarre de cada Cuenta_Contable al
   **código agrupador del SAT** (Apartado B del Anexo 24).
2. **Balanza de Comprobación** (XML mensual) — saldo inicial, cargos, abonos y
   saldo final por cuenta agrupada, derivados de las Pólizas_Contables.
3. **Pólizas del Periodo** (XML) — detalle de los asientos del periodo con sus
   movimientos, para entrega a requerimiento/devoluciones.

Se reutiliza TODO lo existente (catálogo de cuentas, pólizas, balanza de
comprobación ya calculada) y se añade únicamente lo fiscal: el **código agrupador**
en las cuentas y los **generadores XML** conforme al esquema oficial 1.3.

El sistema es multi-tenant (aislamiento por `tenant_id` con RLS de PostgreSQL y
`TenantContext`), la autorización es **deny-by-default** con permisos atómicos
(RBAC del rol `contabilidad`), el módulo está sujeto al **gating por Plan**
(`contabilidad`), todos los textos son **es-MX**, se cumple **WCAG 2.1 AA** y no se
exponen UUIDs en la interfaz.

> **Alcance explícito:** el sistema GENERA los XML válidos conforme al esquema del
> SAT y permite descargarlos. NO realiza el envío automático al Buzón Tributario
> ni el sellado con la e.firma (esos pasos los ejecuta el contribuyente/contador
> en el portal del SAT con el archivo generado), lo cual es el comportamiento
> estándar de los sistemas contables comerciales.

## Glosario

- **Anexo_24**: anexo de la RMF que define el formato XML de la Contabilidad
  Electrónica (catálogo de cuentas, balanza de comprobación y pólizas).
- **Codigo_Agrupador_SAT**: clave del catálogo del SAT (Apartado B del Anexo 24) a
  la que se "amarra" cada Cuenta_Contable de nivel mayor o subcuenta de primer
  nivel (p. ej. `101.01` = "Efectivo").
- **Cuenta_Contable**: cuenta del catálogo contable de la Empresa (entidad
  existente), con `codigo`, `nombre`, `tipo` y `naturaleza`.
- **Poliza_Contable**: asiento contable balanceado existente (cargos = abonos),
  con sus Movimiento_Poliza (renglones cargo/abono sobre una Cuenta_Contable).
- **Balanza_Comprobacion**: reporte por cuenta con saldo inicial, movimientos y
  saldo final de un periodo, ya calculado por el módulo de reportes.
- **Catalogo_XML**: archivo XML del catálogo de cuentas conforme al esquema
  `CatalogoCuentas` 1.3 del SAT.
- **Balanza_XML**: archivo XML de la balanza de comprobación conforme al esquema
  `BalanzaComprobacion` 1.3 del SAT.
- **Polizas_XML**: archivo XML de las pólizas del periodo conforme al esquema
  `PolizasPeriodo` 1.3 del SAT.
- **Periodo_Contable**: mes y año fiscal al que corresponde una balanza o un
  conjunto de pólizas (formato `AAAA-MM`).
- **Datos_Fiscales_Empresa**: RFC de la Empresa emisora, requerido en el encabezado
  de todos los XML (proviene de los datos de la Empresa/tenant, entidad existente).
- **TenantContext**: contexto que resuelve el `tenant_id` de la Empresa autenticada
  desde el JWT (nunca desde la petición).

## Requisitos

### Requisito 1 — Amarre de Cuenta_Contable al Código Agrupador del SAT

**Historia:** Como contador de una Empresa, quiero asociar cada cuenta de mi
catálogo con un código agrupador del SAT, para que la Contabilidad Electrónica
pueda interpretarse por la autoridad.

#### Criterios de aceptación

1. THE sistema SHALL permitir asignar a cada Cuenta_Contable un
   Codigo_Agrupador_SAT opcional (una cuenta sin agrupador se considera "sin
   amarrar").
2. THE sistema SHALL exponer el catálogo oficial de códigos agrupadores del SAT
   (Apartado B del Anexo 24) como catálogo de solo lectura consultable para elegir
   el código a amarrar.
3. WHEN se asigna un Codigo_Agrupador_SAT a una Cuenta_Contable, THE sistema SHALL
   validar que el código exista en el catálogo oficial del SAT; si no existe, SHALL
   rechazar con 422 informando el código inválido.
4. THE sistema SHALL persistir el Codigo_Agrupador_SAT en la Cuenta_Contable
   respetando el aislamiento multi-tenant (RLS) y la concurrencia optimista.
5. WHEN se consulta o lista una Cuenta_Contable, THE sistema SHALL incluir su
   Codigo_Agrupador_SAT (o vacío si no está amarrada).
6. THE sistema SHALL registrar en auditoría la asignación/cambio del
   Codigo_Agrupador_SAT de una Cuenta_Contable.
7. THE operación de amarre SHALL exigir el permiso `cuenta_contable:actualizar` y
   el módulo `contabilidad` habilitado en el Plan; en su ausencia SHALL responder
   403.

### Requisito 2 — Exportación del Catálogo de Cuentas (XML Anexo 24)

**Historia:** Como contador, quiero descargar el XML del catálogo de cuentas
conforme al Anexo 24, para enviarlo al SAT.

#### Criterios de aceptación

1. THE sistema SHALL generar un Catalogo_XML válido conforme al esquema
   `www.sat.gob.mx/esquemas/ContabilidadE/1_3/CatalogoCuentas` (versión 1.3),
   incluyendo el namespace y `schemaLocation` correctos.
2. THE Catalogo_XML SHALL incluir en su encabezado: Version="1.3", el RFC de la
   Empresa (Datos_Fiscales_Empresa) y el Mes/Año de inicio de vigencia solicitados.
3. FOR ALL Cuenta_Contable activa con Codigo_Agrupador_SAT amarrado, THE
   Catalogo_XML SHALL emitir un elemento `Ctas` con: CodAgrup (código agrupador),
   NumCta (código de la cuenta), Desc (nombre), Nivel y Natur (naturaleza D/A).
4. WHERE una Cuenta_Contable activa NO tiene Codigo_Agrupador_SAT amarrado, THE
   sistema SHALL advertir (en la respuesta de la vista previa) las cuentas sin
   amarrar, ya que el SAT exige el amarre; la generación SHALL poder continuar solo
   con las cuentas amarradas o bloquearse según decisión de diseño documentada.
5. THE sistema SHALL entregar el Catalogo_XML como descarga con `Content-Type`
   `application/xml` y un nombre de archivo conforme a la convención del SAT
   (`<RFC><AAAA><MM>CT.xml`).
6. THE exportación SHALL exigir el permiso `contabilidad_electronica:exportar` y el
   módulo `contabilidad` habilitado; en su ausencia SHALL responder 403.
7. THE sistema SHALL registrar en auditoría cada generación del Catalogo_XML.

### Requisito 3 — Exportación de la Balanza de Comprobación (XML Anexo 24)

**Historia:** Como contador, quiero descargar el XML de la balanza de comprobación
mensual conforme al Anexo 24, para cumplir el envío mensual al SAT.

#### Criterios de aceptación

1. THE sistema SHALL generar una Balanza_XML válida conforme al esquema
   `www.sat.gob.mx/esquemas/ContabilidadE/1_3/BalanzaComprobacion` (versión 1.3).
2. THE Balanza_XML SHALL incluir en su encabezado: Version="1.3", RFC de la
   Empresa, Mes y Anio del Periodo_Contable, y TipoEnvio (N=Normal).
3. FOR ALL Cuenta_Contable con movimientos o saldo en el periodo, THE Balanza_XML
   SHALL emitir un elemento `Ctas` con: NumCta (código de la cuenta), SaldoIni,
   Debe (cargos del periodo), Haber (abonos del periodo) y SaldoFin, con dos
   decimales.
4. THE saldo inicial, cargos, abonos y saldo final SHALL derivarse EXACTAMENTE de
   las Pólizas_Contables del periodo y el saldo acumulado previo (reutilizando el
   cálculo de la Balanza_Comprobacion existente), garantizando la coherencia
   contable (`SaldoFin = SaldoIni ± Debe ∓ Haber` según naturaleza).
5. THE sistema SHALL entregar la Balanza_XML como descarga `application/xml` con
   nombre `<RFC><AAAA><MM>BN.xml` (BN = balanza normal).
6. THE exportación SHALL exigir el permiso `contabilidad_electronica:exportar` y el
   módulo `contabilidad` habilitado; en su ausencia SHALL responder 403.
7. THE sistema SHALL registrar en auditoría cada generación de la Balanza_XML.

### Requisito 4 — Exportación de las Pólizas del Periodo (XML Anexo 24)

**Historia:** Como contador, quiero descargar el XML de las pólizas de un periodo
conforme al Anexo 24, para entregarlo cuando el SAT lo requiera (devoluciones,
compensaciones o auditorías).

#### Criterios de aceptación

1. THE sistema SHALL generar un Polizas_XML válido conforme al esquema
   `www.sat.gob.mx/esquemas/ContabilidadE/1_3/PolizasPeriodo` (versión 1.3).
2. THE Polizas_XML SHALL incluir en su encabezado: Version="1.3", RFC de la
   Empresa, Mes y Anio del Periodo_Contable, y TipoSolicitud (AF=Acto de
   Fiscalización por defecto, configurable).
3. FOR ALL Poliza_Contable del periodo, THE Polizas_XML SHALL emitir un elemento
   `Poliza` con: NumUnIdenPol (identificador único de la póliza), Fecha, Concepto y
   sus renglones `Transaccion` con NumCta (cuenta), DesCta (nombre), Concepto,
   Debe (cargo) y Haber (abono).
4. THE sistema SHALL entregar el Polizas_XML como descarga `application/xml` con
   nombre `<RFC><AAAA><MM>PL.xml`.
5. THE exportación SHALL exigir el permiso `contabilidad_electronica:exportar` y el
   módulo `contabilidad` habilitado; en su ausencia SHALL responder 403.
6. THE sistema SHALL registrar en auditoría cada generación del Polizas_XML.

### Requisito 5 — Vista previa y validación antes de exportar

**Historia:** Como contador, quiero ver un resumen y las advertencias antes de
descargar cada XML, para corregir problemas (cuentas sin amarrar, balanza
descuadrada) antes de enviar al SAT.

#### Criterios de aceptación

1. THE sistema SHALL ofrecer, para cada uno de los tres XML, una vista previa
   (JSON) con: el conteo de elementos que se incluirán y las advertencias
   detectadas.
2. FOR el catálogo, THE vista previa SHALL listar las cuentas activas SIN
   Codigo_Agrupador_SAT (advertencia de amarre pendiente).
3. FOR la balanza, THE vista previa SHALL advertir si la balanza del periodo no
   cuadra (total de cargos ≠ total de abonos) informando la diferencia.
4. THE vista previa SHALL exigir el permiso `contabilidad_electronica:leer` y el
   módulo `contabilidad` habilitado.

### Requisito 6 — Frontend enterprise de Contabilidad Electrónica

**Historia:** Como contador, quiero una pantalla clara dentro del módulo de
Contabilidad para amarrar cuentas al SAT y descargar los tres XML por periodo.

#### Criterios de aceptación

1. THE frontend SHALL agregar una vista "Contabilidad Electrónica (SAT)" bajo el
   módulo Contabilidad (`/empresa/contabilidad/contabilidad-electronica`), visible
   solo si el módulo `contabilidad` está habilitado y el usuario tiene el permiso
   `contabilidad_electronica:leer`.
2. THE vista SHALL permitir seleccionar el Periodo_Contable (mes/año) y ofrecer tres
   acciones de descarga: Catálogo, Balanza y Pólizas, cada una con su vista previa y
   advertencias antes de descargar.
3. THE frontend SHALL integrar, dentro de la gestión del catálogo de cuentas, la
   capacidad de amarrar/editar el Codigo_Agrupador_SAT de cada cuenta con un
   selector que consulte el catálogo oficial (autocompletar por código/nombre).
4. THE frontend SHALL usar el sistema de diseño enterprise existente (PageHeader con
   acento de marca, chips de estado semánticos, tarjetas), textos es-MX, cumplir
   WCAG 2.1 AA y no exponer UUIDs.
5. WHEN una descarga o vista previa falla (403/422/500), THE frontend SHALL mostrar
   el mensaje de error de forma clara sin romper la aplicación.
6. THE ítem de navegación SHALL colocarse en la sección "Contabilidad y finanzas"
   del menú, con su gating por módulo y permiso.

### Requisito 7 — Seguridad, multi-tenant y calidad

**Historia:** Como responsable de la plataforma, quiero que el bloque respete el
aislamiento multi-tenant, el RBAC y los estándares de calidad del proyecto.

#### Criterios de aceptación

1. FOR ALL operación del bloque, THE sistema SHALL resolver el `tenant_id` desde el
   TenantContext (JWT) y NUNCA desde la petición (Req 23.4), aplicando RLS.
2. THE sistema SHALL sembrar los permisos nuevos (`contabilidad_electronica:leer`,
   `contabilidad_electronica:exportar`, `cuenta_contable:actualizar`) con el patrón
   idempotente (`ON CONFLICT DO NOTHING`) y enlazarlos al rol `contabilidad`.
3. THE nueva tabla/columna de catálogo agrupador SAT (dato de plataforma, común a
   todos los tenants) SHALL vivir SIN RLS (como `plan`/`permiso`), mientras que el
   amarre por cuenta vive en la tabla `cuenta_contable` (con RLS).
4. THE código nuevo SHALL respetar la arquitectura hexagonal y las reglas ArchUnit
   existentes.
5. THE bloque SHALL incluir pruebas: dominio puro (generación XML determinista y
   coherencia de la balanza), integración con Testcontainers (RLS del amarre) y del
   frontend (servicio HTTP y vista). El build de backend y frontend SHALL quedar
   verde.
6. THE generación de los XML SHALL ser determinista (orden estable de cuentas y
   pólizas) y usar codificación UTF-8 con dos decimales en importes.
