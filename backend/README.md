# CRM de Anuncios Luminosos — Backend

Andamiaje del backend (Tarea 1). Spring Boot 3.3.5 sobre Java 21 LTS, empaquetado
como JAR ejecutable con Tomcat embebido. Arquitectura hexagonal pragmática
(Puertos y Adaptadores) organizada como monolito modular. Ver
`.kiro/specs/crm-anuncios-luminosos/design.md`.

## Requisitos

- Java 21 LTS (`JAVA_HOME` apuntando al JDK 21).
- Se incluye el Maven Wrapper (`mvnw` / `mvnw.cmd`); no requiere Maven instalado.

## Compilar y empaquetar

```powershell
# Build completo (compila y ejecuta pruebas)
.\mvnw.cmd package

# Solo empaquetar sin pruebas
.\mvnw.cmd -DskipTests package
```

El JAR ejecutable se genera en `target/crm-0.0.1-SNAPSHOT.jar`.

## Ejecutar

Los secretos NO están embebidos (Req 11): se leen de variables de entorno.

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/crm"
$env:DB_USER="<usuario>"
$env:DB_PASSWORD="<password>"
java -jar target/crm-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

## Decisiones de diseño

- **Ruta base de API `/api/v1` (Req 12.4):** se usa `server.servlet.context-path: /api/v1`,
  de modo que toda la API (incluidos Swagger y Actuator) queda bajo ese prefijo.
  El Proxy_Inverso (IIS) enruta `/api` hacia el backend.
- **OpenAPI/Swagger (Req 13):** springdoc habilitado.
  - API docs: `/api/v1/v3/api-docs`
  - Swagger UI: `/api/v1/swagger-ui.html`
- **Actuator (base para Req 51):** health expuesto en `/api/v1/actuator/health`.
- **Grupo/artefacto/paquete:** `com.empresa` / `crm` / `com.empresa.crm`.
- **Base de datos:** PostgreSQL con Flyway; `spring.jpa.hibernate.ddl-auto=validate`.

## Estructura de paquetes (hexagonal)

```
com.empresa.crm
├── CrmApplication            # clase principal @SpringBootApplication
├── platform                  # núcleo transversal
│   ├── config  tenant  security  audit  web
├── comercial                 # patrón de referencia por módulo:
│   ├── domain
│   ├── application
│   └── adapter.in.rest / adapter.out.persistence
└── (módulos de negocio, placeholders de andamiaje)
    operacion  compras  facturacion  contabilidad  rhnomina
    tesoreria  activosfijos  mantenimiento  portalcliente
    estrategia  presupuestos  social  notificaciones  reportesbi  plataforma
```

> Nota: esta fase es solo andamiaje. Entidades de negocio, multi-tenant y
> seguridad real se implementan en tareas posteriores (4+). La carga completa
> del contexto de Spring con base de datos se prueba con Testcontainers en las
> tareas de integración (p. ej. 2.2).
