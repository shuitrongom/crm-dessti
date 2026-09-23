# Contexto de traspaso — CRM Dess-TI / plataforma-multigiro

> Documento para continuar el trabajo en otra maquina/sesion. Pegar su contenido
> como primer mensaje al retomar.

## Que es
CRM multi-tenant. Backend Spring Boot (Java 21, arquitectura hexagonal) + Frontend
Angular 20 + PostgreSQL. Responder en es-MX.

## Repo Git (fuente de verdad)
- https://github.com/shuitrongom/crm-dessti  (rama: main)
- Ultimo commit relevante: correcciones de bugs del tester.
- El repo se creo desde cero (el proyecto no tenia git). .gitignore excluye target/,
  dist/, node_modules/, .angular/, *.jar y secretos.
- PRIMER PASO en una maquina nueva: clonar y verificar que backend/ y frontend/ esten completos.

## Estado del despliegue (YA EN PRODUCCION, funcionando)
- Server: Windows Server 2022 (VirtualBox, acceso por LogMeIn). hostname WIN-7E6OTPCIO7T.
- Backend como SERVICIO de Windows: **CRM-Backend** (Automatic).
  JAR: C:\apps\crm\backend\crm-0.0.1-SNAPSHOT.jar. Puerto 8080, context path /api/v1.
- Arranque via servicio (instalar-servicio-backend.ps1 / sc.exe); parametros en
  C:\apps\crm\deploy\parametros.ps1 (NO editar el .cmd a mano).
  Scripts: C:\apps\crm\backend\iniciar-backend.cmd, C:\apps\crm\deploy\{parametros.ps1,
  instalar-servicio-backend.ps1, crear-superadmin.ps1}.
- BD del server: PostgreSQL 127.0.0.1:5432, base dessti_plataforma, user dessti_app,
  migrator dessti_migrator. (En desarrollo local era puerto 5433.)
- Frontend servido por IIS (bundle estatico de frontend/dist/frontend/browser/).
  Requiere fallback a index.html para rutas del router (incluida la nueva /cambiar-password).
- Migraciones Flyway aplicadas OK: paso de v66 a v70. Health UP en
  http://127.0.0.1:8080/api/v1/actuator/health.

## Despliegue = parar servicio -> copiar artefactos -> arrancar servicio
```powershell
Stop-Service CRM-Backend
# copiar crm-0.0.1-SNAPSHOT.jar a C:\apps\crm\backend\  y  browser\ a la carpeta de IIS
Start-Service CRM-Backend
# verificar: health + C:\apps\crm\logs\backend-stdout.log
```

## Builds
- Frontend (en frontend/): `npx ng build --configuration production` (EXIT 0; solo
  warnings de presupuesto SCSS). Salida: frontend/dist/frontend/browser/.
- Backend JAR (en backend/): `\='-Xmx1024m'; .\mvnw.cmd -o -DskipTests clean package`
  (el -Xmx evita OOM nativo de la JVM). Salida: backend/target/crm-0.0.1-SNAPSHOT.jar (~64 MB).
- Specs frontend: `npx ng test --watch=false --include="<ruta-al-spec>"` (jsdom+TestBed). NUNCA vitest.
- Tests backend: `.\mvnw.cmd -o test "-Dtest=Clase1,Clase2"` (con MAVEN_OPTS -Xmx1024m).
  En Windows el exit code puede ser 1 por warnings de la JVM en stderr aunque el BUILD sea
  SUCCESS; fijarse en "Tests run: ... Failures:0, Errors:0" y "BUILD SUCCESS".

## Bugs del tester ya corregidos y verificados (esta version)
1. **Editar giro**: PUT /plataforma/giros/{id} + permiso giro:actualizar (V70) + Giro.actualizar
   + GiroDialog modo edicion (clave inmutable) + accion Editar en la lista.
2. **Label Maximo de usuarios**: hint aclaratorio en plan-dialog y paquete-suscripcion-dialog.
3. **Establecer contrasena sin campo**: computed sobre FormControl no reacciona; respaldado con
   signal (reset-password-dialog.ts). Mismo patron que #11.
4. **Login case-insensitive**: identificador a minusculas en creacion (ServicioUsuarios,
   ServicioEmpresas) y login (ServicioAutenticacion) + V68 (lowercase de existentes).
   La contrasena sigue siendo case-sensitive.
5. **Forzar cambio de contrasena temporal**: V69 (debe_cambiar_password), se marca al generar
   temporal/reset, viaja en TokenResponse.debeCambiarPassword, pantalla nueva /cambiar-password;
   Usuario.cambiarPassword limpia el flag.
6. **Pipeline no se ajusta al zoom**: oportunidades.scss columnas minmax(260px,288px) + overflow-x
   scroll + overflow-wrap en tarjetas.
7. **Falta alta de canal de venta**: en Asignar canal del pipeline, si no hay canales se muestra
   aviso con routerLink a /empresa/comercial/canales-venta.
8. **No guarda precio de lista + formato pesos**: el precio SI persistia pero no se veia. Nuevo
   GET /listas-precios/{id}/precios (buscarPreciosDeLista + PrecioListaDto); frontend muestra los
   precios con formato \$ MXN (CurrencyPipe) y recarga tras asignar.
9/10. **No guardaba plan/paquete con Inventario avanzado (bloqueante)**: operacion es giro-scoped
   a anuncios pero inventario-avanzado (Nucleo) lo requiere; CatalogoModulosGiroValidacion
   rechazaba en otros giros. FIX C: CatalogoModulosService expone operacion como Nucleo SOLO en
   el catalogo de monetizacion (set MONETIZACION_NUCLEO), SIN tocar RegistroVerticales.giroDeModulo
   -> el gating RBAC por giro de anuncios queda intacto (Req 16.6).
11. **Cambiar giro deshabilitado**: misma raiz que #3; signal en cambiar-giro-dialog.ts +
    (selectionChange) en el mat-select.
- **Ortografia/acentos**: pase en plataforma y varias vistas. Se ajustaron specs que hardcodeaban
  textos viejos (TITULO_NUCLEO='Nucleo comun' -> 'Nucleo comun' con acentos, etc.).

## Migraciones nuevas de esta version
- V68 login case-insensitive, V69 debe_cambiar_password, V70 permiso giro:actualizar.
- Backend estaba en v66; ahora v70.

## Pendiente / siguiente
- Confirmar que el push a GitHub subio TODO (rama main) y que backend/ y frontend/ estan completos.
- SEGURIDAD: se expuso un token ghp_ en un chat; debe estar REVOCADO. Usar token nuevo o login por navegador.
- Los testers estan probando la version desplegada. Nuevos bugs: mismo flujo -> causa raiz -> fix
  definitivo (no parche) -> build+specs verdes -> generar JAR/dist -> desplegar en servicio CRM-Backend.

## Convenciones
- Soluciones definitivas, no parches; no romper nada; verificar con build y specs.
- Editar HTML de Angular con cuidado en PowerShell (usar Node via .NET WriteAllText para @if/@for
  y comillas). Los .html del front usan LF; los .ts a veces CRLF.