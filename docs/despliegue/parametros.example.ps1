# ============================================================================
#  parametros.example.ps1
#  Plantilla de PARAMETROS de arranque del backend del CRM Dess-TI.
# ----------------------------------------------------------------------------
#  COMO USARLO:
#    1) Copie este archivo como  parametros.ps1  (SIN "example") en la MISMA
#       carpeta. El script instalar-servicio-backend.ps1 lo cargara con dot-source.
#    2) Sustituya TODOS los valores entre [CORCHETES] por sus valores REALES de
#       PRODUCCION. NO reutilice las claves de desarrollo.
#    3) NUNCA suba parametros.ps1 a control de versiones: contiene secretos.
#
#  SEGURIDAD (Req 11):
#    - Estos valores son secretos. Restrinja el acceso al archivo (solo el
#      administrador y la cuenta que ejecuta el servicio).
#    - Genere JWT_SIGNING_KEY y las llaves de cifrado NUEVAS para produccion
#      (ver notas mas abajo). No use jamas las de desarrollo.
# ============================================================================

# ----------------------------------------------------------------------------
# Rutas y ejecutable (ajuste si su instalacion difiere de los valores sugeridos)
# ----------------------------------------------------------------------------
$JavaExe   = "C:\Program Files\Java\jdk-21\bin\java.exe"   # Ruta al java.exe (JDK 21)
$JarPath   = "C:\apps\crm\backend\crm-0.0.1-SNAPSHOT.jar"  # Fat jar del backend
$WorkDir   = "C:\apps\crm\backend"                         # Directorio de trabajo
$LogDir    = "C:\apps\crm\logs"                            # Carpeta de logs del servicio
$ServiceName = "CRM-Backend"                               # Nombre del servicio de Windows

# ----------------------------------------------------------------------------
# Base de datos (PostgreSQL). El backend escucha SOLO en 127.0.0.1 (ver abajo);
# la BD suele estar en el mismo servidor (localhost:5432).
# ----------------------------------------------------------------------------
$DbUrl      = "jdbc:postgresql://127.0.0.1:5432/dessti_plataforma"  # URL JDBC de la BD
$DbUser     = "dessti_app"          # Rol de APLICACION (NOSUPERUSER, sin BYPASSRLS)
$DbPassword = "[CLAVE_APP]"         # Contrasena del rol de aplicacion

# Rol MIGRADOR (owner del esquema, BYPASSRLS) usado por Flyway al arrancar.
$DbMigratorUser     = "dessti_migrator"   # Rol migrador con privilegios DDL
$DbMigratorPassword = "[CLAVE_MIGRATOR]"  # Contrasena del rol migrador

# ----------------------------------------------------------------------------
# JWT (firma de tokens). GENERE UNA CLAVE NUEVA para produccion.
# Sugerencia para generar una clave aleatoria robusta (Base64, 64 bytes):
#   [Convert]::ToBase64String((1..64 | % {Get-Random -Max 256}))
# ----------------------------------------------------------------------------
$JwtSigningKey = "[JWT_SIGNING_KEY_PRODUCCION]"   # Clave secreta de firma del JWT

# ----------------------------------------------------------------------------
# Cifrado de datos sensibles en reposo (AES-256). Material Base64 de 32 bytes.
# GENERE LLAVES NUEVAS para produccion. Sugerencia (Base64, 32 bytes = AES-256):
#   [Convert]::ToBase64String((1..32 | % {Get-Random -Max 256}))
# 'Activa' indica la version en uso (p. ej. "v1"). v2 es opcional (rotacion).
# ----------------------------------------------------------------------------
$EncKeyActive = "v1"                          # Alias de la version activa
$EncKeyV1     = "[LLAVE_CIFRADO_V1_BASE64]"   # Material Base64 (32 bytes)
$EncKeyV2     = ""                            # Opcional: dejar vacio si no hay rotacion

# ----------------------------------------------------------------------------
# Datos del EMISOR (Dess-TI) que se imprimen en el comprobante de renta.
# 'Nombre' tiene sentido por defecto; el resto es opcional.
# ----------------------------------------------------------------------------
$EmisorNombre    = "Dess-TI"
$EmisorRfc       = "[RFC_EMISOR]"          # Opcional
$EmisorDireccion = "[DIRECCION_EMISOR]"    # Opcional
$EmisorEmail     = "[EMAIL_EMISOR]"        # Opcional
$EmisorSitioWeb  = "[SITIO_WEB_EMISOR]"    # Opcional

# ----------------------------------------------------------------------------
# Red del backend: escucha SOLO en la interfaz local (no expuesto a la red).
# NO cambie estos valores salvo que sepa lo que hace: exponer 8080 es un riesgo.
# ----------------------------------------------------------------------------
$ServerAddress = "127.0.0.1"   # Interfaz local unicamente
$ServerPort    = "8080"        # Puerto interno HTTP del backend

# ============================================================================
#  Construccion del arreglo de ARGUMENTOS que se pasa al java -jar.
#  El backend lee secretos/config desde estos argumentos de linea de comando.
#  NO edite esta seccion salvo para agregar/quitar props: use las variables de
#  arriba. Cada elemento es un argumento independiente.
# ============================================================================
$BackendArgs = @(
    # --- Datasource (runtime, rol de aplicacion) ---
    "--spring.datasource.url=$DbUrl",
    "--spring.datasource.username=$DbUser",
    "--spring.datasource.password=$DbPassword",

    # --- Flyway (migraciones al arranque, rol migrador) ---
    "--spring.flyway.url=$DbUrl",
    "--spring.flyway.user=$DbMigratorUser",
    "--spring.flyway.password=$DbMigratorPassword",

    # --- Secretos de la aplicacion ---
    "--crm.secretos.db-user=$DbUser",
    "--crm.secretos.db-password=$DbPassword",
    "--crm.secretos.jwt-signing-key=$JwtSigningKey",

    # --- Cifrado en reposo (AES-256) ---
    "--crm.cifrado.activa=$EncKeyActive",
    "--crm.cifrado.llaves.v1=$EncKeyV1",
    "--crm.cifrado.llaves.v2=$EncKeyV2",

    # --- Emisor del comprobante de renta ---
    "--crm.facturacion.emisor.nombre=$EmisorNombre",
    "--crm.facturacion.emisor.rfc=$EmisorRfc",
    "--crm.facturacion.emisor.direccion=$EmisorDireccion",
    "--crm.facturacion.emisor.email=$EmisorEmail",
    "--crm.facturacion.emisor.sitio-web=$EmisorSitioWeb",

    # --- Proxy inverso (IIS): confiar en X-Forwarded-* ---
    "--server.forward-headers-strategy=framework",

    # --- Red: escuchar SOLO en local ---
    "--server.address=$ServerAddress",
    "--server.port=$ServerPort"
)
