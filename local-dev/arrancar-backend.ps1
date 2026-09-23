# ============================================================================
# arrancar-backend.ps1  -  Arranca el backend del CRM (perfil dev) contra tu
# PostgreSQL local y publica la API + Swagger.
#
# Pensado para usar TU PostgreSQL ya instalado (con tu user/pass). Si prefieres
# levantar PostgreSQL en contenedor, usa el parametro -UsarDocker.
#
# Requisitos:
#   - JDK 21 (ruta configurable con -JavaHome).
#   - PostgreSQL 16 accesible (el tuyo, o contenedor con -UsarDocker).
#   - Una base de datos vacia para el CRM (Flyway crea el esquema V1..V48).
#
# Ejemplos:
#   # Con tu PostgreSQL (ajusta usuario/clave/base):
#   .\local-dev\arrancar-backend.ps1 -DbUser postgres -DbPassword TU_CLAVE -DbNombre crm
#
#   # Levantando PostgreSQL 16 en contenedor automaticamente:
#   .\local-dev\arrancar-backend.ps1 -UsarDocker
#
# Al arrancar:
#   Swagger UI: http://localhost:8080/api/v1/swagger-ui.html
#   Health:     http://localhost:8080/api/v1/actuator/health
#
# NOTA: las llaves JWT/cifrado de este script son SOLO para desarrollo local.
#       Los secretos reales se resuelven fuera del codigo (Req 11).
# ============================================================================
param(
    [string]$DbHost     = "localhost",
    [int]   $DbPort     = 5433,
    [string]$DbNombre   = "dessti_plataforma",
    [string]$DbUser     = "dessti_app",
    [string]$DbPassword = "Pa55worD",
    [string]$DbMigradorUser     = "dessti_migrator",
    [string]$DbMigradorPassword = "Pa55worD",
    [switch]$UsarDocker,
    [string]$JavaHome   = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot",
    [string]$MvnCmd     = "C:\apache-maven-3.9.10\bin\mvn.cmd"
)

$ErrorActionPreference = "Stop"
$RAIZ    = Split-Path -Parent $PSScriptRoot
$BACKEND = Join-Path $RAIZ "backend"
$CONTENEDOR = "crm-postgres-local"
$DK = "docker"

function Escribir($msg, $color = "Cyan") { Write-Host $msg -ForegroundColor $color }

# ---------------------------------------------------------------------------
# 1) Base de datos: tu PostgreSQL (por defecto) o contenedor (-UsarDocker)
# ---------------------------------------------------------------------------
if ($UsarDocker) {
    Escribir "Levantando PostgreSQL 16 en contenedor '$CONTENEDOR'..."
    $existe = & $DK ps -a --filter "name=$CONTENEDOR" --format "{{.Names}}"
    if ($existe -eq $CONTENEDOR) {
        & $DK start $CONTENEDOR | Out-Null
    } else {
        & $DK run -d --name $CONTENEDOR `
            -e POSTGRES_USER=$DbUser -e POSTGRES_PASSWORD=$DbPassword -e POSTGRES_DB=$DbNombre `
            -p "$($DbPort):5432" postgres:16 | Out-Null
    }
    Escribir "Esperando a PostgreSQL..."
    for ($i = 0; $i -lt 30; $i++) {
        & $DK exec $CONTENEDOR pg_isready -U $DbUser -d $DbNombre 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 2
    }
    Escribir "PostgreSQL (contenedor) listo." "Green"
} else {
    Escribir "Usando TU PostgreSQL en $DbHost:$DbPort/base '$DbNombre' (usuario '$DbUser')." "Green"
    Escribir "La base '$DbNombre' debe existir y estar vacia la primera vez (Flyway crea el esquema)." "DarkYellow"
}

# ---------------------------------------------------------------------------
# 2) Variables de entorno (Req 11: secretos fuera del codigo, aqui solo dev)
# ---------------------------------------------------------------------------
$env:JAVA_HOME          = $JavaHome
$env:DB_URL             = "jdbc:postgresql://$DbHost:$DbPort/$DbNombre"
$env:DB_USER            = $DbUser
$env:DB_PASSWORD        = $DbPassword
# Rol MIGRADOR (Flyway) separado del rol de runtime (Req 23): las migraciones y
# semillas de plataforma corren con dessti_migrator (BYPASSRLS); el runtime usa
# dessti_app (NOBYPASSRLS) para que la RLS multi-tenant siempre aplique.
$env:DB_MIGRATOR_USER     = $DbMigradorUser
$env:DB_MIGRATOR_PASSWORD = $DbMigradorPassword
$env:JWT_SIGNING_KEY    = "clave-de-firma-jwt-solo-para-desarrollo-local-0123456789"
$env:CRM_ENC_KEY_ACTIVE = "v1"
$env:CRM_ENC_KEY_V1     = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="

Escribir ""
Escribir "API base : http://localhost:8080/api/v1" "Yellow"
Escribir "Swagger  : http://localhost:8080/api/v1/swagger-ui.html" "Yellow"
Escribir "Health   : http://localhost:8080/api/v1/actuator/health" "Yellow"
Escribir "Flyway aplicara las migraciones V1..V48 al arrancar." "DarkGray"
Escribir "Deten el backend con Ctrl+C en esta ventana." "DarkGray"
Escribir ""

# ---------------------------------------------------------------------------
# 3) Arranque del backend con perfil dev
# ---------------------------------------------------------------------------
Push-Location $BACKEND
try {
    & $MvnCmd -o spring-boot:run "-Dspring-boot.run.profiles=dev"
} finally {
    Pop-Location
}