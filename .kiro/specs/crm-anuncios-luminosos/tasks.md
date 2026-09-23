# Implementation Plan: CRM de Anuncios Luminosos

## Overview

Este plan convierte el diseño (arquitectura hexagonal pragmática con Spring Boot 3.x / Java 21, frontend Angular + Angular Material, PostgreSQL con Row-Level Security y multi-tenancy) en una serie de pasos de codificación incrementales. Cada tarea construye sobre las anteriores y termina integrándose en el sistema, sin dejar código huérfano.

El trabajo se organiza en bloques: (1) andamiaje del proyecto, (2) núcleo transversal (multi-tenant, errores, paginación, auditoría, secretos, cifrado), (3) seguridad (autenticación, autorización, sesiones), (4) plataforma/tenants, (5) módulos de negocio (cada uno con dominio, máquinas de estado, casos de uso, adaptadores y pruebas), (6) frontend, y (7) cierre transversal (concurrencia, rendimiento, respaldo, OpenAPI, integración con Testcontainers).

Convenciones:
- Backend en `com.empresa.crm.*` con paquetes `domain`, `application`, `adapter.in.rest`, `adapter.out.persistence` por módulo.
- Pruebas de propiedad con **jqwik** (mínimo 100 iteraciones) etiquetadas `// Feature: crm-anuncios-luminosos, Property N: {texto}`.
- Dinero con `BigDecimal`/`NUMERIC(18,2)`, redondeo half-up.
- Las sub-tareas marcadas con `*` son pruebas opcionales (pueden omitirse para un MVP más rápido); las tareas de implementación de núcleo nunca son opcionales.

## Tasks

- [x] 1. Andamiaje del proyecto backend (Spring Boot 3.x / Java 21, hexagonal)
  - Inicializar proyecto Gradle (o Maven) con Spring Boot 3.x sobre Java 21 LTS, empaquetado como JAR ejecutable con Tomcat embebido
  - Crear la estructura de paquetes hexagonal `com.empresa.crm` (`platform.config`, `platform.tenant`, `platform.security`, `platform.audit`, `platform.web`, y módulos de negocio con `domain/application/adapter.in.rest/adapter.out.persistence`)
  - Añadir dependencias base: Spring Web, Spring Data JPA, Spring Security, Bean Validation, springdoc-openapi, MapStruct, Flyway, driver PostgreSQL, jqwik, JUnit 5, Testcontainers
  - Configurar perfiles y `application.yml` base (sin secretos embebidos)
  - _Requisitos: 12, 13_

- [x] 2. Esqueleto de base de datos PostgreSQL y migraciones
  - [x] 2.1 Configurar Flyway y migración inicial de esquema base
    - Definir migración `V1` con tablas de plataforma/seguridad base y convenciones (`tenant_id UUID`, `version BIGINT`, `created_at/updated_at/created_by/updated_by` en UTC)
    - Configurar el usuario de BD de la aplicación sin privilegios de superusuario ni `BYPASSRLS`
    - _Requisitos: 23, 49_
  - [x]* 2.2 Prueba de integración de arranque de migraciones con Testcontainers
    - Verificar que Flyway aplica las migraciones sobre un contenedor PostgreSQL limpio
    - _Requisitos: 23_

- [x] 3. Andamiaje del frontend Angular + Sistema de Diseño
  - Inicializar proyecto Angular (última versión estable) con Angular Material
  - Crear el Sistema de Diseño con tokens (color, tipografía, espaciados, radios, sombras) como CSS custom properties + tema de Angular Material, con modo claro/oscuro
  - Configurar cliente HTTP hacia `/api/v1`, enrutador base y estructura de módulos/feature folders
  - _Requisitos: 52, 53, 57_

- [x] 4. Contexto de tenant y multi-tenancy (capa de aplicación + RLS)
  - [x] 4.1 Implementar TenantContext y filtro de resolución de tenant
    - Crear `TenantContext` (ThreadLocal/RequestScope) y filtro de servlet que deriva `tenant_id` del contexto autenticado, nunca de parámetros de la petición
    - Implementar filtro global de Hibernate (`@FilterDef`/`@Filter`) e interceptor de persistencia que asigna `tenant_id` en escrituras
    - _Requisitos: 23_
  - [x] 4.2 Activar Row-Level Security en PostgreSQL como segunda capa
    - Añadir migración que habilita RLS y políticas `tenant_isolation` por tabla de negocio y fija `app.current_tenant` por transacción (`SET LOCAL`)
    - _Requisitos: 23_
  - [x] 4.3 Definir unicidad de negocio por tenant y comportamiento de acceso cruzado
    - Restricciones `UNIQUE (tenant_id, rfc)` para activos; devolver 404 ante acceso a recurso de otro tenant y registrar el intento en auditoría
    - _Requisitos: 23_
  - [x]* 4.4 Prueba de propiedad: aislamiento multi-empresa
    - **Property 1: Aislamiento multi-empresa (tenant isolation)**
    - **Validates: Requisitos 23.2, 23.3, 23.4, 45.4**
  - [x]* 4.5 Prueba de propiedad: unicidad de identificadores por tenant
    - **Property 22: Unicidad de identificadores de negocio por tenant**
    - **Validates: Requisitos 5.3, 23.6, 29.3**
  - [x]* 4.6 Prueba de integración RLS con Testcontainers
    - Verificar que las políticas RLS impiden acceso cruzado aun omitiendo el filtro de aplicación
    - _Requisitos: 23_

- [x] 5. Manejo de errores, paginación y validación transversal
  - [x] 5.1 Implementar manejo global de errores (Problem Details RFC 7807)
    - `@RestControllerAdvice` que traduce excepciones de dominio/infraestructura a 400/401/403/404/409/429 sin fugas internas
    - _Requisitos: 8, 56_
  - [x] 5.2 Implementar utilidades de paginación y validación de entrada
    - Soporte `page`/`size` (por defecto 20, máximo 100) con metadatos `totalElements`/`totalPages`; rechazo de `size > 100` con 400 donde aplique; Bean Validation en DTOs y consultas parametrizadas
    - _Requisitos: 8, 12_
  - [x]* 5.3 Prueba de propiedad: validación de datos de entrada
    - **Property 23: Validación de datos de entrada**
    - **Validates: Requisitos 5.1, 5.2, 8.1, 8.2, 29.1, 29.2, 40.1, 40.2, 44.1, 44.2**
  - [x]* 5.4 Prueba de propiedad: acotación y metadatos de paginación
    - **Property 24: Acotación y metadatos de paginación**
    - **Validates: Requisitos 5.7, 6.8, 7.7, 7.8, 12.1, 14.7, 24.5**

- [x] 6. Gestión de secretos y cifrado de datos sensibles en reposo
  - [x] 6.1 Implementar carga de secretos externa al código
    - Resolver secretos (credenciales BD, clave de firma JWT) desde variables de entorno/almacén externo; abortar arranque si falta un secreto, registrando su nombre y nunca su valor; evitar registrar secretos en logs
    - _Requisitos: 11_
  - [x] 6.2 Implementar cifrado en reposo y gestión de Llave_Cifrado
    - Convertidor de atributos JPA para campos altamente sensibles; metadatos `Llave_Cifrado` (alias/estado, sin el valor); soporte de rotación conservando el descifrado de datos previos; bloqueo controlado si la llave no está disponible; credenciales de integraciones cifradas
    - _Requisitos: 67_
  - [x]* 6.3 Prueba de integración de cifrado en reposo y rotación de llave
    - Verificar datos cifrados en almacenamiento, fallo controlado sin exponer la llave y descifrado tras rotación
    - _Requisitos: 67, 11_

- [x] 7. Servicio de auditoría inmutable encadenado por hash
  - [x] 7.1 Implementar Registro_Auditoria y AuditoriaPort
    - Entidad con `actor`, `accion`, `recurso`, `tenant_id` (o marca plataforma), `valor_anterior`/`valor_nuevo` (sin secretos), `trace_id`, timestamp UTC, `hash_actual`/`hash_previo`; sin update/delete
    - Consulta paginada y filtrable (actor, tipo de recurso, rango de fechas) y exportación; retención configurable; auditoría de lectura/exportación de datos sensibles
    - _Requisitos: 10_
  - [x] 7.2 Implementar alertas de auditoría y verificación de cadena
    - `Alerta_Auditoria` configurable ante patrones sensibles con Notificacion; operación de verificación de integridad de la cadena de hash con reporte de ruptura; propagación de `trace_id`
    - _Requisitos: 10_
  - [x]* 7.3 Prueba de propiedad: integridad de la cadena de auditoría
    - **Property 20: Integridad de la cadena de auditoría**
    - **Validates: Requisitos 10.4, 10.7**

- [x] 8. Checkpoint - Núcleo transversal
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Autenticación (JWT + refresco + bloqueo + rate limiting)
  - [x] 9.1 Implementar Servicio_Autenticacion y emisión de tokens
    - Endpoints `login`/`refresh`/`logout`; hashing BCrypt/Argon2; Token_Acceso (≤ 15 min) y Token_Refresco (≤ 7 días) con claims `sub`/`tenant_id`/`roles`/`permisos`/`exp`; errores genéricos; 401 en token expirado/refresco inválido
    - _Requisitos: 1, 11_
  - [x] 9.2 Implementar bloqueo por intentos fallidos y rate limiting
    - Bloqueo de cuenta 15 min tras 5 fallos consecutivos en ventana de 15 min, reinicio de contador al expirar; rate limiting 100 req/min/IP → 429; auditoría de cada intento y de rechazos por bloqueo
    - _Requisitos: 2_
  - [x]* 9.3 Prueba de propiedad: vigencia acotada de los tokens
    - **Property 26: Vigencia acotada de los tokens**
    - **Validates: Requisitos 1.4, 1.7**
  - [x]* 9.4 Prueba de propiedad: bloqueo por intentos fallidos
    - **Property 27: Bloqueo por intentos fallidos**
    - **Validates: Requisitos 2.1, 2.2**

- [x] 10. Autorización RBAC y modelo de roles
  - [x] 10.1 Implementar RBAC deny-by-default con permisos atómicos
    - Permisos `(recurso, operacion)`; `@PreAuthorize` evaluando dentro del tenant; 403 sin permiso o sin rol válido; gating por Plan (403 si módulo no habilitado)
    - _Requisitos: 3, 25_
  - [x] 10.2 Implementar roles predefinidos y roles personalizados por empresa
    - Roles de plataforma (`super_admin`) y de empresa según Req 27; `Rol_Personalizado` combinando permisos existentes (sin permisos de plataforma); roles predefinidos inmutables; auditoría
    - _Requisitos: 3, 27, 28_
  - [x]* 10.3 Prueba de propiedad: autorización RBAC con denegación por defecto
    - **Property 25: Autorización RBAC con denegación por defecto**
    - **Validates: Requisitos 3.1, 3.2, 3.5, 3.6, 25.4**

- [x] 11. Gestión de usuarios y revocación de sesiones
  - [x] 11.1 Implementar gestión de Usuarios, Roles y Permisos
    - Crear/desactivar usuarios, asignar roles, aplicar permisos en la siguiente evaluación; conflicto de identificador duplicado (409); auditoría de operaciones de acceso
    - _Requisitos: 4_
  - [x] 11.2 Implementar gestión y revocación de sesiones
    - Almacén de Token_Refresco con denylist por `jti`; revocación en logout, por administrador, por desactivación y por cambio de contraseña; consulta paginada de sesiones activas; auditoría
    - _Requisitos: 68_
  - [x]* 11.3 Prueba de propiedad: revocación de Token_Refresco
    - **Property 41: Revocación de Token_Refresco**
    - **Validates: Requisitos 68.1, 68.2, 68.3, 68.4**

- [x] 12. Protección de comunicación y cabeceras de seguridad
  - Configurar cabeceras `Content-Security-Policy`, `Strict-Transport-Security`, `X-Frame-Options` en Spring Security; protección CSRF para operaciones de cambio de estado desde navegador; documentar terminación TLS y redirección HTTP→HTTPS en IIS (proxy inverso)
  - _Requisitos: 9_

- [x] 13. Checkpoint - Seguridad
  - Ensure all tests pass, ask the user if questions arise.

- [x] 14. Plataforma: empresas, planes, suscripciones y offboarding
  - [x] 14.1 Implementar administración de Empresas (tenants) y super_admin
    - Crear/activar/suspender/consultar Empresas con `tenant_id` único; crear `admin_empresa` inicial; suspensión impide login; super_admin sin acceso a datos de negocio; auditoría de plataforma
    - _Requisitos: 24_
  - [x] 14.2 Implementar Planes y Suscripciones
    - Definir Plan (máx. usuarios, módulos habilitados); Suscripcion con estado/vigencia; límite de usuarios; gating de módulos por Plan; auditoría
    - _Requisitos: 25_
  - [x] 14.3 Implementar personalización por empresa (branding)
    - Nombre visible y logotipo por Empresa aplicados en la interfaz; auditoría
    - _Requisitos: 26_
  - [x] 14.4 Implementar offboarding y portabilidad del tenant
    - Exportación de datos por `tenant_id` en formato estructurado; Periodo_Gracia configurable; eliminación/anonimización acotada por tenant apoyada en RLS; preservación de comprobantes fiscales bajo retención; auditoría
    - _Requisitos: 69_
  - [x]* 14.5 Prueba de propiedad: límite de usuarios del Plan
    - **Property 28: Límite de usuarios del Plan**
    - **Validates: Requisitos 25.3**
  - [x]* 14.6 Prueba de propiedad: aislamiento del offboarding del tenant
    - **Property 42: Aislamiento del offboarding del tenant**
    - **Validates: Requisitos 69.3, 69.4, 69.5**

- [x] 15. Módulo comercial-crm: clientes y contactos
  - [x] 15.1 Implementar dominio y persistencia de Cliente y Contacto
    - Validación de datos obligatorios y RFC; borrado lógico (`activo`); unicidad de RFC por tenant en activos; asociación de Contacto solo a Cliente activo; auditoría de cambios
    - _Requisitos: 5_
  - [x] 15.2 Implementar API REST de Clientes/Contactos con listado, filtro y paginación
    - Listado paginado (20/100), filtro por nombre o RFC sin distinguir mayúsculas; DTOs distintos de entidades
    - _Requisitos: 5, 12_
  - [x]* 15.3 Pruebas unitarias de validación y conflicto de RFC duplicado
    - Casos límite de datos obligatorios y RFC inválido/duplicado
    - _Requisitos: 5_

- [x] 16. Módulo comercial-crm: catálogo de productos y listas de precios
  - [x] 16.1 Implementar Producto, Lista_Precios y Precio_Producto
    - Alta con datos obligatorios; borrado lógico; información comercial de apoyo; precios con vigencia/prioridad en rango 0.01..999,999,999.99; selección de lista por prioridad/segmento; sugerencia de precio en Partida_Cotizacion; auditoría
    - _Requisitos: 59_
  - [x]* 16.2 Prueba de propiedad: rango de precios de Producto
    - **Property 30: Rango de precios de Producto**
    - **Validates: Requisitos 59.3, 59.10**
  - [x]* 16.3 Prueba unitaria de selección de Lista_Precios por prioridad/segmento
    - Ejemplos con varias listas vigentes aplicables a un Cliente
    - _Requisitos: 59_

- [x] 17. Módulo comercial-crm: oportunidades, cotizaciones y canal de venta
  - [x] 17.1 Implementar Oportunidad y máquina de estados del pipeline
    - Alta con datos obligatorios y etapa inicial "nuevo"; asignación de responsable; transiciones definidas y estados finales; conversión de "ganado" a Cotizacion; auditoría de cambios de etapa
    - _Requisitos: 14_
  - [x] 17.2 Implementar Cotizacion, partidas y cálculo de totales
    - Estado inicial "borrador"; 1..500 partidas; validación de cantidad/precio; subtotal y total con redondeo half-up; máquina de estados (borrador→enviada→{aprobada|rechazada}); listado con filtro por cliente/estado; auditoría
    - _Requisitos: 6, 12_
  - [x] 17.3 Implementar clasificación por canal de venta
    - `Canal_Venta` y FK opcional en Oportunidad/Cotizacion; segmentación en reportes; auditoría de asignación/modificación
    - _Requisitos: 63_
  - [x]* 17.4 Prueba de propiedad: totales monetarios de documentos con partidas
    - **Property 2: Totales monetarios de documentos con partidas**
    - **Validates: Requisitos 6.3, 6.5, 31.3, 31.5**
  - [x]* 17.5 Prueba de propiedad: rechazo de partidas fuera de rango
    - **Property 3: Rechazo de partidas fuera de rango**
    - **Validates: Requisitos 6.4, 31.4**

- [x] 18. Módulo comercial-crm: pruebas de diseño (aprobación de arte)
  - [x] 18.1 Implementar Prueba_Diseno versionada y aprobación/rechazo
    - Generación con versión 1 y estado "pendiente"; aprobación/rechazo con actor y UTC; rechazo genera nueva versión +1; historial inmutable; listado paginado; auditoría
    - _Requisitos: 15_
  - [x]* 18.2 Prueba de propiedad: versionado monótono de Pruebas de Diseño
    - **Property 8: Versionado monótono de Pruebas de Diseño**
    - **Validates: Requisitos 15.3, 15.4**

- [x] 19. Módulo operacion-produccion: órdenes de fabricación
  - [x] 19.1 Implementar Orden_Fabricacion y precondiciones de generación
    - Generación solo desde Cotizacion "aprobada" sin OF previa y con Prueba_Diseno aprobada; estado inicial "pendiente"; máquina de estados con finales; listado paginado con filtro por estado/cliente; auditoría
    - _Requisitos: 7, 15_
  - [x]* 19.2 Prueba de propiedad: precondiciones para generar Orden de Fabricación
    - **Property 7: Precondiciones para generar Orden de Fabricación**
    - **Validates: Requisitos 7.1, 7.2, 7.3, 15.5**

- [x] 20. Módulo operacion-produccion: inventario de materiales (base)
  - [x] 20.1 Implementar Material y Movimiento_Inventario
    - Alta con existencias iniciales 0; movimientos entrada/salida/ajuste; rechazo de salida que deje existencias < 0; consumo por Orden_Fabricacion; notificación de stock bajo; listado con filtro; auditoría
    - _Requisitos: 18_
  - [x]* 20.2 Prueba de propiedad: no negatividad de existencias de inventario
    - **Property 9: No negatividad de existencias de inventario**
    - **Validates: Requisitos 18.2, 18.3, 18.4**

- [x] 21. Módulo operacion-produccion: levantamiento en sitio y permisos
  - [x] 21.1 Implementar Levantamiento_Sitio
    - Alta con datos obligatorios y estado "en_proceso"; vínculos a Sitio/Cotizacion/OF; fotografías; marca "completado" con actor/UTC; guarda de programación que exige levantamiento completado; listado paginado; auditoría
    - _Requisitos: 16_
  - [x] 21.2 Implementar Permiso_Instalacion y zonificación
    - Alta con estado "solicitado"; máquina de estados (solicitado→{aprobado|rechazado}); guarda de programación que exige permiso aprobado; notificación de vencimiento próximo (30 días); listado con filtros; auditoría
    - _Requisitos: 17_

- [x] 22. Módulo operacion-produccion: instalación y proyectos multi-sitio
  - [x] 22.1 Implementar Orden_Trabajo_Instalacion y Lista_Pendientes
    - Creación desde OF "terminada" con cuadrilla/fecha y estado "programada"; guardas de levantamiento/permiso; registro de avance/evidencia; máquina de estados con finales; guarda de cierre sin pendientes; listado con filtros; auditoría
    - _Requisitos: 19_
  - [x] 22.2 Implementar Proyecto y Sitio con avance consolidado
    - Creación de Proyecto/Sitio; avance por fase de cada Sitio; estado consolidado derivado; listado con filtro por cliente; auditoría
    - _Requisitos: 21_
  - [x]* 22.3 Prueba de propiedad: guarda de cierre de Orden de Trabajo de Instalación
    - **Property 6: Guarda de cierre de Orden de Trabajo de Instalación**
    - **Validates: Requisitos 19.6**

- [x] 23. Módulo operacion-produccion: inventario avanzado
  - [x] 23.1 Implementar Almacenes, existencias por almacén y Kardex
    - `Almacen`, `Existencia` por almacén (inventario perpetuo), Kardex de solo lectura con cantidad/costo unitario/costo total/saldo; stock mínimo/máximo y punto de reorden con notificaciones; listado con filtros; auditoría
    - _Requisitos: 60, 18_
  - [x] 23.2 Implementar costeo (promedio/PEPS), lotes y transferencias
    - Motor de costeo promedio ponderado y PEPS; control de lotes con trazabilidad; transferencia entre almacenes (salida+entrada conservando costo)
    - _Requisitos: 60_
  - [x]* 23.3 Prueba de propiedad: no negatividad y perpetuidad por almacén
    - **Property 31: No negatividad y perpetuidad de existencias por Almacén**
    - **Validates: Requisitos 60.3, 60.10, 60.12**
  - [x]* 23.4 Prueba de propiedad: transferencia entre almacenes conserva cantidad y costo
    - **Property 32: Transferencia entre Almacenes conserva cantidad y costo**
    - **Validates: Requisitos 60.13**
  - [x]* 23.5 Prueba de propiedad: recosteo correcto según método (promedio/PEPS)
    - **Property 33: Recosteo correcto según método (promedio/PEPS)**
    - **Validates: Requisitos 60.11**
  - [x]* 23.6 Prueba de propiedad: Kardex refleja el saldo acumulado con signo
    - **Property 34: Kardex refleja el saldo acumulado con signo**
    - **Validates: Requisitos 60.3, 60.12**

- [x] 24. Checkpoint - Comercial y producción
  - Ensure all tests pass, ask the user if questions arise.

- [x] 25. Módulo mantenimiento: contratos y tickets de servicio
  - [x] 25.1 Implementar Contrato_Mantenimiento y Ticket_Servicio con SLA
    - Alta de contrato con SLA (respuesta/resolución); tickets manuales o por preventivo con estado "abierto"; asignación a técnico/cuadrilla; máquina de estados; cálculo de cumplimiento de SLA al resolver; listado con filtros; auditoría
    - _Requisitos: 20_

- [x] 26. Módulo compras: proveedores, requisiciones y órdenes de compra
  - [x] 26.1 Implementar Proveedor
    - Alta con datos obligatorios; unicidad de RFC por tenant en activos; borrado lógico; listado con filtro por nombre/RFC; auditoría
    - _Requisitos: 29_
  - [x] 26.2 Implementar Requisicion_Compra
    - Alta con partidas y estado "borrador"; máquina de estados con finales; generación de Orden_Compra desde requisición "aprobada"; listado con filtro; auditoría
    - _Requisitos: 30_
  - [x] 26.3 Implementar Orden_Compra con partidas y totales
    - Alta con proveedor y 1..500 partidas; validación de cantidad/precio; subtotal/total half-up; máquina de estados con finales; listado con filtros; auditoría
    - _Requisitos: 31_

- [x] 27. Módulo compras: recepción de mercancía y conciliación de tres vías
  - [x] 27.1 Implementar Recepcion_Mercancia integrada con inventario
    - Recepción contra OC "abierta"/"recibida_parcial"; rechazo de exceso sobre lo ordenado; generación de Movimiento_Inventario "entrada"; derivación de estado de OC (parcial/total); listado con filtro; auditoría
    - _Requisitos: 32, 18_
  - [x] 27.2 Implementar Factura_Proveedor y Conciliacion_Tres_Vias
    - Registro con estado "registrada"; conciliación de cantidad/precio dentro de tolerancia; marca "discrepancia" o "conciliada"; máquina de estados; guarda de autorización de pago; listado con filtros; auditoría
    - _Requisitos: 33_
  - [x]* 27.3 Prueba de propiedad: recepción de mercancía acotada por lo ordenado
    - **Property 10: Recepción de mercancía acotada por lo ordenado**
    - **Validates: Requisitos 32.3**
  - [x]* 27.4 Prueba de propiedad: estado de OC derivado de las recepciones
    - **Property 11: Estado de Orden de Compra derivado de las recepciones**
    - **Validates: Requisitos 32.5, 32.6**
  - [x]* 27.5 Prueba de propiedad: conciliación de tres vías nunca autoriza fuera de tolerancia
    - **Property 12: Conciliación de tres vías nunca autoriza fuera de tolerancia**
    - **Validates: Requisitos 33.3, 33.4, 33.5**

- [x] 28. Módulo facturacion-cfdi: emisión, timbrado y cancelación (PAC)
  - [x] 28.1 Definir PacPort y adaptador stub del PAC
    - Puerto `PacPort` (timbrar/cancelar) en el dominio; adaptador HTTP desacoplado + stub para pruebas; credenciales por gestión de secretos (Req 11)
    - _Requisitos: 35, 11_
  - [x] 28.2 Implementar Factura (CFDI) y cálculo fiscal
    - Emisión desde Cotizacion "aprobada" u OF con datos fiscales del receptor y estado "borrador"; IVA 16% y retenciones; total half-up; validación de datos fiscales; listado con filtros; auditoría
    - _Requisitos: 34_
  - [x] 28.3 Implementar timbrado/cancelación y máquina de estados CFDI
    - Timbrado con Folio_Fiscal y estado "timbrada"; rechazo conserva "borrador"; inmutabilidad de datos timbrados; cancelación con motivo SAT y estado "cancelacion_en_proceso"→"cancelada"; histórico inmutable; auditoría
    - _Requisitos: 35_
  - [x] 28.4 Implementar Nota_Credito
    - Emisión referenciando Factura timbrada; CFDI egreso timbrado; disminución de CxC; rechazo por exceso; histórico inmutable; auditoría
    - _Requisitos: 37_
  - [x]* 28.5 Prueba de propiedad: cálculo fiscal de la Factura (CFDI)
    - **Property 4: Cálculo fiscal de la Factura (CFDI)**
    - **Validates: Requisitos 34.2**
  - [x]* 28.6 Prueba de propiedad: nota de crédito acotada por el saldo de la factura
    - **Property 14: Nota de crédito acotada por el saldo de la factura**
    - **Validates: Requisitos 37.2**
  - [x]* 28.7 Prueba de propiedad: inmutabilidad de CFDI y recibos timbrados
    - **Property 21: Inmutabilidad de CFDI y recibos timbrados**
    - **Validates: Requisitos 35.3, 35.6, 37.3, 41.7**
  - [x]* 28.8 Prueba de integración del adaptador PAC (stub)
    - Timbrado exitoso, rechazo y cancelación con mapeo de estados
    - _Requisitos: 35_

- [x] 29. Módulo contabilidad-finanzas: CxC, pagos y complementos
  - [x] 29.1 Implementar Cuenta_Por_Cobrar y Pago_Cliente
    - CxC al timbrar factura; aplicación de pagos con disminución de saldo; rechazo por exceso; Complemento_Pago para parcialidades/diferidos (timbrado vía PAC); antigüedad de saldos (aging); listado con filtros; auditoría
    - _Requisitos: 36_
  - [x]* 29.2 Prueba de propiedad: aplicación de pago de cliente acotada por el saldo
    - **Property 13: Aplicación de pago de cliente acotada por el saldo**
    - **Validates: Requisitos 36.2, 36.3**

- [x] 30. Módulo contabilidad-finanzas: pólizas, cuentas y CxP
  - [x] 30.1 Implementar catálogo de cuentas y pólizas balanceadas
    - Catálogo de Cuenta_Contable por tenant; generación automática de Poliza_Contable ante eventos relevantes; validación de balance (cargos = abonos); inmutabilidad con reverso; listado con filtros; auditoría
    - _Requisitos: 38_
  - [x] 30.2 Implementar Cuentas por Pagar y Programacion_Pago
    - CxP al conciliar factura de proveedor; programación de pagos; aplicación con disminución de saldo y marca "pagada"; rechazo por exceso; aging por proveedor; listado con filtros; auditoría
    - _Requisitos: 42_
  - [x]* 30.3 Prueba de propiedad: póliza contable balanceada
    - **Property 16: Póliza contable balanceada**
    - **Validates: Requisitos 38.2, 38.3, 38.4**
  - [x]* 30.4 Prueba de propiedad: cuenta por pagar acotada por el saldo
    - **Property 15: Cuenta por pagar acotada por el saldo**
    - **Validates: Requisitos 42.3, 42.4**

- [x] 31. Módulo tesorería: cuentas bancarias y conciliación
  - [x] 31.1 Definir ImportacionBancariaPort y adaptador
    - Puerto de importación de estados de cuenta (archivo/API) con adaptador desacoplado
    - _Requisitos: 43_
  - [x] 31.2 Implementar Cuenta_Bancaria, estados de cuenta y conciliación bancaria
    - Alta de cuenta; importación de Estado_Cuenta_Bancario con movimientos; emparejamiento automático con póliza/pago dentro de tolerancia; excepciones; diferencia cero para conciliación completa; listado con filtros; auditoría
    - _Requisitos: 43_
  - [x]* 31.3 Prueba de propiedad: conciliación bancaria completa solo con diferencia cero
    - **Property 18: Conciliación bancaria completa solo con diferencia cero**
    - **Validates: Requisitos 43.3, 43.4, 43.5**

- [x] 32. Módulo activos-fijos: activos y depreciación
  - [x] 32.1 Implementar Activo_Fijo y Depreciacion
    - Alta con datos obligatorios; validación; cálculo/registro de depreciación con póliza; baja/venta conservando histórico; listado con filtro por estado; auditoría
    - _Requisitos: 44_

- [x] 33. Módulo contabilidad-finanzas: reportes y estados financieros
  - [x] 33.1 Implementar reportes financieros/fiscales
    - Estado de cuenta por cliente, ingresos por periodo, IVA trasladado/retenido, aging, libro de pólizas; agregaciones de solo lectura; filtros por fecha/cliente y exportación; 403 sin permiso; auditoría de consulta/exportación
    - _Requisitos: 39_
  - [x] 33.2 Implementar estados financieros
    - Balance general, estado de resultados y balanza de comprobación derivados de pólizas; ecuación contable; filtro por periodo y exportación; 403 sin permiso; auditoría
    - _Requisitos: 47_
  - [x]* 33.3 Prueba de propiedad: ecuación contable del balance general
    - **Property 17: Ecuación contable del balance general**
    - **Validates: Requisitos 47.3**

- [x] 34. Módulo rh-nomina: empleados, contratos e incidencias
  - [x] 34.1 Implementar Empleado, Contrato_Laboral e Incidencia
    - Alta con datos obligatorios y validación de RFC/CURP/NSS; incidencias por periodo; baja conservando histórico; listado con filtro por nombre/estado; auditoría
    - _Requisitos: 40_

- [x] 35. Módulo rh-nomina: cálculo y timbrado de nómina
  - [x] 35.1 Implementar cálculo de Nomina y máquina de estados
    - Cálculo de percepciones/deducciones (ISR/IMSS/Infonavit) y subsidio; neto no negativo; rechazo por datos fiscales faltantes; máquina de estados; generación y timbrado de Recibo_Nomina vía PAC; histórico inmutable; auditoría
    - _Requisitos: 41_
  - [x]* 35.2 Prueba de propiedad: identidad aritmética de la nómina
    - **Property 19: Identidad aritmética de la nómina**
    - **Validates: Requisitos 41.1, 41.2**
  - [x]* 35.3 Pruebas unitarias de tablas fiscales (ISR/IMSS/Infonavit)
    - Ejemplos con importes verificados a mano
    - _Requisitos: 41_

- [x] 36. Módulo rh-nomina: organización de personal
  - [x] 36.1 Implementar Puesto, organigrama, asignaciones y evaluaciones
    - Puestos con jerarquía validada como grafo acíclico; asignación de empleados; Evaluacion_Desempeno con escala e historial; organigrama derivado; listado con filtros; auditoría
    - _Requisitos: 61, 40_
  - [x]* 36.2 Prueba de propiedad: organigrama sin ciclos
    - **Property 35: Organigrama sin ciclos**
    - **Validates: Requisitos 61.7**

- [x] 37. Módulo estrategia: misión/visión/valores y objetivos
  - [x] 37.1 Implementar Esencia_Empresa y Objetivo_Estrategico con resultados clave
    - Registro de misión/visión/valores; objetivos con avance inicial 0; validación de campos; historial de avance; resultados clave ponderados; avance 0..100 y estado derivado (en_riesgo/en_curso/cumplido); listado con filtros; auditoría
    - _Requisitos: 58_
  - [x]* 37.2 Prueba de propiedad: avance de objetivo acotado y ponderado
    - **Property 29: Avance de Objetivo Estratégico acotado y ponderado**
    - **Validates: Requisitos 58.8, 58.9**
  - [x]* 37.3 Prueba unitaria de estado derivado del objetivo
    - Ejemplos de en_riesgo/en_curso/cumplido según avance y periodo
    - _Requisitos: 58_

- [x] 38. Módulo presupuestos: presupuestos y control de costos
  - [x] 38.1 Implementar Presupuesto y cálculo de variación
    - Presupuesto por área/periodo; variación (real − presupuestado) en importe y porcentaje con indicador favorable/desfavorable; real derivado de operaciones; destacar desviaciones por umbral; listado con filtros; auditoría
    - _Requisitos: 62_
  - [x]* 38.2 Prueba de propiedad: variación de presupuesto en importe y porcentaje
    - **Property 36: Variación de presupuesto en importe y porcentaje**
    - **Validates: Requisitos 62.6, 62.7**

- [x] 39. Checkpoint - Finanzas, RH y estrategia
  - Ensure all tests pass, ask the user if questions arise.

- [x] 40. Módulo redes-sociales: mensajería omnicanal y bandeja unificada
  - [x] 40.1 Definir MensajeriaSocialPort y adaptador Meta stub
    - Puerto `MensajeriaSocialPort` (enviar/plantilla/publicar/estado/métricas); adaptadores por canal (WhatsApp Cloud, Graph, Marketing) + stub; credenciales vía secretos (Req 11); política de reintentos
    - _Requisitos: 64, 11_
  - [x] 40.2 Implementar Cuenta_Canal_Social y controlador de webhooks entrantes
    - Configuración de cuenta por canal (`credenciales_ref`); controlador REST dedicado sobre HTTPS/TLS con validación de firma; persistencia de Mensaje_Social entrante; asociación/creación de Cliente/Contacto/Oportunidad (captura de leads)
    - _Requisitos: 64, 9_
  - [x] 40.3 Implementar Bandeja_Unificada, envío, ventana de servicio y opt-in
    - Conversacion/Mensaje_Social; hilo único por Cliente/Contacto con filtros; guarda de Ventana_Servicio (24h) y Plantilla_Mensaje; mensajes interactivos; opt-in/opt-out; handover; reintentos; auditoría
    - _Requisitos: 64_
  - [x]* 40.4 Prueba de propiedad: guarda de Ventana_Servicio para envío
    - **Property 37: Guarda de Ventana_Servicio para envío de mensajes**
    - **Validates: Requisitos 64.6, 64.7**
  - [x]* 40.5 Prueba de propiedad: guarda de Opt_In para mensajería de marketing
    - **Property 38: Guarda de Opt_In para mensajería de marketing**
    - **Validates: Requisitos 64.8, 64.9, 46.7**
  - [x]* 40.6 Prueba de integración del adaptador Meta y webhook (stub)
    - Envío con reintentos, mapeo de estado_entrega, y recepción/validación de firma del webhook
    - _Requisitos: 64, 9_

- [x] 41. Módulo redes-sociales: publicación y campañas
  - [x] 41.1 Implementar Publicacion_Social y Campaña_Publicitaria
    - Publicación con máquina de estados (borrador→programada→{publicada|fallida}); publicación programada vía adaptador con reintentos; campañas con presupuesto/periodo válidos y estado externo de solo lectura; listado con filtros; auditoría
    - _Requisitos: 65_
  - [x]* 41.2 Prueba de propiedad: validación de presupuesto y periodo de la campaña
    - **Property 39: Validación de presupuesto y periodo de la Campaña_Publicitaria**
    - **Validates: Requisitos 65.7, 65.8**

- [x] 42. Módulo redes-sociales: analítica social
  - [x] 42.1 Implementar métricas sociales por canal y periodo
    - Métricas de solo lectura (alcance, interacciones, mensajes, tiempo de respuesta, conversiones/leads); segmentación por canal de venta; consolidación en Tablero/BI; filtros y exportación; 403 sin permiso; aislamiento por tenant; auditoría
    - _Requisitos: 66_
  - [x]* 42.2 Prueba de propiedad: métricas sociales como agregación de solo lectura por tenant
    - **Property 40: Métricas sociales como agregación de solo lectura y por tenant**
    - **Validates: Requisitos 66.1, 66.6**

- [x] 43. Módulo notificaciones
  - [x] 43.1 Definir NotificacionPort e implementar notificaciones
    - Adaptadores de correo/WhatsApp desacoplados con reintentos; generación ante eventos relevantes; reutilización de la integración social respetando Ventana_Servicio/Plantilla/Opt_In; contenido mínimo sin datos sensibles; auditoría de envío
    - _Requisitos: 46_

- [x] 44. Módulo reportes-bi: tablero e inteligencia de negocio
  - [x] 44.1 Implementar Tablero de indicadores por área
    - Indicadores de todas las áreas (comercial, producción, instalación, mantenimiento, inventario, compras, finanzas, RH/nómina, tesorería, CxP, estrategia, inventario avanzado, presupuesto, redes sociales); agregaciones de solo lectura; filtros por fecha/cliente y exportación; 403 sin permiso; auditoría
    - _Requisitos: 22_
  - [x] 44.2 Implementar Inteligencia de Negocio consolidada
    - Análisis consolidado de todas las áreas con tendencias/comparativos; tableros personalizados; filtros por fecha/área/dimensión y exportación; aislamiento por tenant; 403 sin permiso; auditoría
    - _Requisitos: 48_

- [x] 45. Módulo portal-cliente
  - [x] 45.1 Implementar Portal_Cliente restringido
    - Acceso `cliente_portal` a su propia información (cotizaciones, pruebas de diseño, avance de proyectos/sitios, tickets, facturas); aprobación/rechazo de sus pruebas de diseño; impedir acceso a otros clientes/operaciones internas; aislamiento por tenant; listados paginados; auditoría
    - _Requisitos: 45_

- [x] 46. Máquinas de estado consolidadas y pruebas transversales de dominio
  - [x] 46.1 Consolidar la implementación de máquinas de estado del dominio
    - Función pura `(estadoActual, evento) → estadoSiguiente` reutilizable por todos los módulos con estados finales que rechazan transiciones posteriores
    - _Requisitos: 6, 7, 14, 17, 19, 20, 30, 31, 33, 35, 41, 65_
  - [x]* 46.2 Prueba de propiedad: transiciones de estado válidas (todas las máquinas)
    - **Property 5: Transiciones de estado válidas (máquinas de estado)**
    - **Validates: Requisitos 6.6, 6.7, 7.5, 7.6, 14.3, 14.4, 17.2, 17.3, 19.5, 20.4, 20.5, 30.3, 30.4, 31.6, 31.7, 33.6, 35.7, 41.5, 41.6, 65.3, 65.4**

- [x] 47. Checkpoint - Redes sociales, reportes y portal
  - Ensure all tests pass, ask the user if questions arise.

- [x] 48. Concurrencia optimista y OpenAPI
  - [x] 48.1 Implementar control de concurrencia optimista
    - `@Version` en entidades de negocio modificables; conflicto de versión → 409 sin sobrescribir
    - _Requisitos: 49_
  - [x] 48.2 Publicar especificación OpenAPI y Swagger UI
    - springdoc-openapi con esquemas de DTO; interfaz navegable
    - _Requisitos: 13_

- [x] 49. Rendimiento, disponibilidad y respaldo
  - [x] 49.1 Implementar health check y procesamiento asíncrono
    - `/actuator/health`; ejecutor dedicado para operaciones intensivas (nómina, estados financieros, BI, timbrado masivo); caché de catálogos de cambio lento
    - _Requisitos: 51, 12_
  - [x] 49.2 Implementar respaldo y recuperación cifrados
    - Respaldos periódicos cifrados con RPO/RTO configurables; restauración auditada; acceso restringido
    - _Requisitos: 50_
  - [x]* 49.3 Pruebas de arranque/smoke y de rendimiento
    - Aborto de arranque ante secreto/llave faltante; publicación de OpenAPI; carga con concurrencia esperada (p95 lectura ≤ 2 s / escritura ≤ 4 s)
    - _Requisitos: 11, 51, 67_

- [x] 50. Frontend: shell de navegación y áreas de acceso
  - [x] 50.1 Implementar autenticación y enrutamiento por ámbito
    - Login, refresco de sesión y guardas de ruta; enrutamiento según rol (plataforma, empresa, portal cliente); navegación compuesta dinámicamente por permisos (deny-by-default)
    - _Requisitos: 1, 3, 68_
  - [x] 50.2 Implementar página principal por empresa
    - Misión/visión/valores, resumen de objetivos con avance y estado derivado, tablero de indicadores por área, branding de la empresa
    - _Requisitos: 22, 26, 58_
  - [x] 50.3 Implementar área de administración de plataforma (super_admin)
    - Gestión de empresas, planes/suscripciones y offboarding
    - _Requisitos: 24, 25, 69_
  - [x] 50.4 Implementar área de administración de empresa (admin_empresa)
    - Usuarios/roles/permisos y roles personalizados, revocación de sesiones, branding, configuración de módulos/integraciones, planeación y presupuestos
    - _Requisitos: 4, 26, 27, 28, 68_

- [x] 51. Frontend: vistas de módulos de negocio
  - [x] 51.1 Implementar vistas comerciales y de producción
    - Clientes/contactos, oportunidades/pipeline, cotizaciones con partidas y precio sugerido, pruebas de diseño, órdenes de fabricación, levantamiento/permisos, instalación, proyectos/sitios, inventario y Kardex
    - _Requisitos: 5, 6, 14, 15, 7, 16, 17, 19, 21, 18, 60, 59, 63_
  - [x] 51.2 Implementar vistas de finanzas, RH y estrategia
    - Compras/3 vías, facturación CFDI, CxC/pagos, contabilidad/estados financieros, tesorería, activos fijos, RH/nómina, organización de personal, estrategia y presupuestos, mantenimiento
    - _Requisitos: 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 42, 43, 44, 47, 40, 41, 61, 58, 62, 20_
  - [x] 51.3 Implementar vistas de redes sociales, reportes y portal del cliente
    - Bandeja unificada con indicador de ventana de servicio y compositor, publicación/campañas, analítica social, tablero/BI, portal del cliente
    - _Requisitos: 64, 65, 66, 22, 48, 45_

- [x] 52. Frontend: experiencia, accesibilidad y retroalimentación
  - [x] 52.1 Implementar responsive, modales, animaciones y estados
    - Responsive mobile-first con breakpoints; modales de confirmación para acciones sensibles; animaciones/microinteracciones con reducción de movimiento; estados de carga/vacío/error; notificaciones toast; mensajes de negocio sin detalles técnicos
    - _Requisitos: 52, 54, 55, 56_
  - [x]* 52.2 Pruebas de componente, accesibilidad (axe-core) y e2e
    - Componentes del Sistema de Diseño, estados vacíos/error, modales; axe-core sobre vistas clave (WCAG 2.1 AA); e2e de flujos críticos (login, cotización, aprobación de diseño, orden de fabricación, timbrado) y responsive
    - _Requisitos: 52, 53, 54, 55, 56, 57_

- [x] 53. Checkpoint final - Integración completa
  - Ensure all tests pass, ask the user if questions arise.

- [x] 54. Módulo calidad: cumplimiento y calidad ISO 9001:2026
  - [x] 54.1 Implementar Queja_Cliente y su vínculo a Acción Correctiva
    - `Queja_Cliente` (origen incluido canal social del Req 64, descripción, cliente asociado, marca temporal UTC); vínculo opcional (no obligatorio) como entrada a `Accion_Correctiva`; creación desde una Conversacion social reutilizando el vínculo cliente/contacto; listado con filtros; auditoría
    - _Requisitos: 70.1, 70.8, 70.9_
  - [x] 54.2 Implementar No_Conformidad y Acción Correctiva con verificación de eficacia
    - `No_Conformidad` (origen, descripción, proceso afectado, marca temporal UTC); `Accion_Correctiva` (responsable, causa raíz, acciones, evidencia de cierre); máquina de estados abierta→en_analisis→en_ejecucion→verificacion→cerrada con estado final; guarda de cierre que exige eficacia verificada; listado con filtros; auditoría
    - _Requisitos: 70.2, 70.9_
  - [x] 54.3 Implementar registros separados de Riesgo y Oportunidad_Calidad
    - `Riesgo` (probabilidad, impacto, nivel derivado, acciones) y `Oportunidad_Calidad` (beneficio esperado, acciones) como agregados distintos con acciones propias, reflejando la separación de las cláusulas 6.1.2 y 6.1.3; listados con filtros; auditoría
    - _Requisitos: 70.3, 70.9_
  - [x] 54.4 Implementar Cambio_SGC (gestión del cambio)
    - `Cambio_SGC` que exige propósito, consecuencias potenciales, recursos necesarios y responsable antes de aprobar; máquina de estados propuesto→aprobado→implementado (rechazado como final alterno); auditoría de la aprobación con actor y marca temporal UTC
    - _Requisitos: 70.4, 70.9_
  - [x] 54.5 Implementar Contexto_Organizacion (contexto y cambio climático)
    - `Contexto_Organizacion` (cuestión interna/externa, indicador de pertinencia del cambio climático con justificación, parte interesada y expectativa); conserva la justificación aun cuando la conclusión sea "no pertinente"; auditoría
    - _Requisitos: 70.5, 70.9_
  - [x] 54.6 Indicadores de cultura de calidad y percepción del cliente en el Tablero
    - Agregaciones de solo lectura: No_Conformidad abiertas/cerradas, tiempo medio de cierre de Accion_Correctiva, tasa de reincidencia y quejas atendidas en tiempo; integración de métricas sociales (Req 66) como fuente de percepción del cliente; sin modificar datos de origen
    - _Requisitos: 70.6, 70.7, 70.8_
  - [x] 54.7 Vista de trazabilidad de cláusulas ISO 9001:2026
    - Vista de solo lectura que mapea cada cláusula soportada al recurso/evidencia del Sistema que la habilita (base de la tabla de trazabilidad del diseño); reutiliza el Servicio_Auditoria como evidencia documentada (cláusula 7.5)
    - _Requisitos: 70.6, 70.10_
  - [x]* 54.8 Prueba de propiedad: guarda de cierre de Acción Correctiva (eficacia verificada)
    - **Property 43: Guarda de cierre de Acción Correctiva (eficacia verificada)**
    - **Validates: Requisitos 70.2**

- [x] 55. Checkpoint - Cumplimiento y calidad ISO 9001:2026
  - Ensure all tests pass, ask the user if questions arise.

- [x] 56. Reportes-BI: adaptadores reales de indicadores por área (datos vivos del Tablero)
  - [x] 56.1 Implementar adaptadores concretos de IndicadorAreaPort para todas las áreas
    - Reemplazar los adaptadores por defecto en cero (`IndicadorXxxVacio`) por adaptadores concretos de SOLO LECTURA, uno por área, que deriven cada indicador del Tablero (Req 22.1) de su propio módulo, respetando el aislamiento por tenant (Req 23) y sin modificar los datos de origen (Req 22.2): comercial (pipeline de oportunidades, cotizaciones por estado), producción (órdenes de fabricación por estado), instalación (cumplimiento de fechas programadas), mantenimiento (cumplimiento de SLA), inventario (materiales bajo stock mínimo), inventario avanzado (existencias por almacén y valuación), compras (órdenes de compra por estado y facturas de proveedor con discrepancia), finanzas/facturación (facturación del periodo, CxC vencidas, IVA del periodo), RH/nómina (costo de nómina del periodo), tesorería (saldos bancarios y partidas en conciliación pendientes), CxP (cuentas por pagar vencidas), estrategia (avance de objetivos), presupuesto (variación presupuestal) y redes sociales (mensajes por canal, tiempo de respuesta y leads). Cada adaptador vive en su propio módulo (o en un submódulo de integración de reportes-bi) exponiendo consultas de agregación read-only; al registrarse como bean, desplaza automáticamente al adaptador por defecto (`@ConditionalOnMissingBean`). El Tablero (Req 22) y la Inteligencia de Negocio (Req 48) quedan con datos vivos, sin placeholders en cero.
    - _Requisitos: 22, 48, 66, 23_
  - [x]* 56.2 Pruebas de integración de los indicadores del Tablero
    - Con datos sembrados por área en un tenant, el Tablero devuelve los indicadores calculados esperados (no cero) y respeta el aislamiento por tenant (otro tenant no ve los datos); consolidado de BI con comparativos por periodo
    - _Requisitos: 22, 48, 23_

- [x] 57. Checkpoint definitivo - Sistema completo end-to-end
  - Verificar suite completa en verde, migraciones V1..N aplicando limpio, arranque del backend con perfil dev, Swagger publicado, y ausencia de adaptadores/placeholders en cero en el Tablero (datos vivos). Entregar guía de ejecución local (scripts de arranque). Ask the user if questions arise.
## Notes

- Las sub-tareas marcadas con `*` son opcionales (pruebas de propiedad, integración, unitarias y de frontend) y pueden omitirse para un MVP más rápido; sin embargo, para el núcleo (multi-tenant, seguridad, comercial, producción, facturación CFDI y contabilidad) se recomienda implementarlas.
- Cada tarea referencia los requisitos que cubre para trazabilidad; entre todas cubren los 70 requisitos del documento de requisitos (incluido el Requisito 70 de cumplimiento y calidad ISO 9001:2026).
- Las 43 Correctness Properties del diseño están cubiertas por una prueba de propiedad cada una, agrupadas junto al módulo/dominio correspondiente, con jqwik (mínimo 100 iteraciones) y etiqueta de trazabilidad.
- Los checkpoints permiten validación incremental por bloques.
- Las integraciones externas (PAC, Meta, correo, banca) se implementan tras puertos/adaptadores desacoplados con stubs para pruebas, preservando la portabilidad del núcleo.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1", "3"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["2.2", "4.1", "5.1", "6.1"] },
    { "id": 3, "tasks": ["4.2", "5.2", "6.2", "7.1"] },
    { "id": 4, "tasks": ["4.3", "5.3", "5.4", "6.3", "7.2", "9.1"] },
    { "id": 5, "tasks": ["4.4", "4.5", "4.6", "7.3", "9.2", "10.1", "12"] },
    { "id": 6, "tasks": ["9.3", "9.4", "10.2", "11.1"] },
    { "id": 7, "tasks": ["10.3", "11.2", "14.1"] },
    { "id": 8, "tasks": ["11.3", "14.2", "14.3", "14.4"] },
    { "id": 9, "tasks": ["14.5", "14.6", "15.1"] },
    { "id": 10, "tasks": ["15.2", "15.3", "16.1", "17.1"] },
    { "id": 11, "tasks": ["16.2", "16.3", "17.2", "17.3", "18.1"] },
    { "id": 12, "tasks": ["17.4", "17.5", "18.2", "19.1", "20.1"] },
    { "id": 13, "tasks": ["19.2", "20.2", "21.1", "21.2", "23.1"] },
    { "id": 14, "tasks": ["22.1", "22.2", "23.2"] },
    { "id": 15, "tasks": ["22.3", "23.3", "23.4", "23.5", "23.6", "25.1", "26.1"] },
    { "id": 16, "tasks": ["26.2", "28.1"] },
    { "id": 17, "tasks": ["26.3", "28.2"] },
    { "id": 18, "tasks": ["27.1", "28.3"] },
    { "id": 19, "tasks": ["27.2", "28.4"] },
    { "id": 20, "tasks": ["27.3", "27.4", "27.5", "28.5", "28.6", "28.7", "28.8", "29.1"] },
    { "id": 21, "tasks": ["29.2", "30.1"] },
    { "id": 22, "tasks": ["30.2", "31.1", "32.1", "34.1"] },
    { "id": 23, "tasks": ["30.3", "30.4", "31.2", "35.1", "36.1", "37.1", "38.1"] },
    { "id": 24, "tasks": ["31.3", "33.1", "33.2", "35.2", "35.3", "36.2", "37.2", "37.3", "38.2"] },
    { "id": 25, "tasks": ["33.3", "40.1", "43.1"] },
    { "id": 26, "tasks": ["40.2", "41.1", "43.1"] },
    { "id": 27, "tasks": ["40.3", "41.2", "42.1"] },
    { "id": 28, "tasks": ["40.4", "40.5", "40.6", "42.2", "44.1", "45.1", "46.1"] },
    { "id": 29, "tasks": ["44.2", "46.2", "48.1", "48.2"] },
    { "id": 30, "tasks": ["49.1", "49.2", "50.1"] },
    { "id": 31, "tasks": ["49.3", "50.2", "50.3", "50.4"] },
    { "id": 32, "tasks": ["51.1", "51.2", "51.3"] },
    { "id": 33, "tasks": ["52.1"] },
    { "id": 34, "tasks": ["52.2"] },
    { "id": 35, "tasks": ["54.1", "54.2", "54.3", "54.4", "54.5"] },
    { "id": 36, "tasks": ["54.6", "54.7"] },
    { "id": 37, "tasks": ["54.8"] }
  ]
}
```
