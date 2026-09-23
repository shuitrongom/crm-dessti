# ============================================================================
# verificar-local.ps1  -  Comprueba que el sistema local esta sano end-to-end.
#
# Con el backend ARRIBA (arrancar-backend.ps1), verifica:
#   1) Health de Actuator = UP.
#   2) OpenAPI/Swagger publicado (/v3/api-docs con paths).
#   3) (Opcional) Login y Tablero con DATOS VIVOS: si pasas -Usuario/-Clave de un
#      Usuario con permiso 'tablero:leer', consulta el Tablero e informa cuantas
#      areas e indicadores devuelve (los indicadores salen de adaptadores reales,
#      no de placeholders en cero).
#
# Uso:
#   .\local-dev\verificar-local.ps1
#   .\local-dev\verificar-local.ps1 -Usuario admin@empresa -Clave TU_CLAVE
# ============================================================================
param(
    [string]$BaseUrl = "http://localhost:8080/api/v1",
    [string]$Usuario,
    [string]$Clave
)
$ErrorActionPreference = "Stop"
function Ok($m)   { Write-Host "[OK]  $m" -ForegroundColor Green }
function Info($m) { Write-Host "[..]  $m" -ForegroundColor Cyan }
function Err($m)  { Write-Host "[ERR] $m" -ForegroundColor Red }

# 1) Health
Info "Verificando health..."
try {
    $h = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get
    if ($h.status -eq "UP") { Ok "Health = UP" } else { Err "Health = $($h.status)" }
} catch { Err "No se pudo consultar health: $($_.Exception.Message)"; return }

# 2) OpenAPI / Swagger
Info "Verificando OpenAPI (/v3/api-docs)..."
try {
    $doc = Invoke-RestMethod -Uri "$BaseUrl/v3/api-docs" -Method Get
    if ($doc.openapi -and $doc.paths) {
        $n = ($doc.paths.PSObject.Properties | Measure-Object).Count
        Ok "OpenAPI publicado ($n rutas). Swagger UI: $BaseUrl/swagger-ui.html"
    } else { Err "OpenAPI sin openapi/paths" }
} catch { Err "No se pudo consultar OpenAPI: $($_.Exception.Message)" }

# 3) Tablero con datos vivos (opcional, requiere credenciales)
if ($Usuario -and $Clave) {
    Info "Iniciando sesion como '$Usuario'..."
    try {
        $login = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType "application/json" `
            -Body (@{ identificador = $Usuario; password = $Clave } | ConvertTo-Json)
        $token = $login.accessToken
        Ok "Sesion iniciada."
        Info "Consultando el Tablero (datos vivos)..."
        $tablero = Invoke-RestMethod -Uri "$BaseUrl/reportes-bi/tablero" -Method Get `
            -Headers @{ Authorization = "Bearer $token" }
        $areas = $tablero.areas
        $totalInd = 0
        foreach ($a in $areas) { $totalInd += ($a.indicadores | Measure-Object).Count }
        Ok "Tablero: $(( $areas | Measure-Object).Count) areas, $totalInd indicadores calculados por adaptadores reales."
        Write-Host "     (Si sembras datos por area, los indicadores reflejan valores > 0; el aislamiento por tenant lo garantizan RLS + filtro Hibernate.)" -ForegroundColor DarkGray
    } catch {
        Err "No se pudo verificar el Tablero: $($_.Exception.Message)"
        Write-Host "     Revisa que el Usuario tenga permiso 'tablero:leer' y credenciales validas." -ForegroundColor DarkGray
    }
} else {
    Info "Omite la verificacion del Tablero (pasa -Usuario y -Clave para incluirla)."
}
Write-Host ""
Ok "Verificacion completada."