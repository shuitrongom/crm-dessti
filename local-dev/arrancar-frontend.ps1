# ============================================================================
# arrancar-frontend.ps1  -  Sirve el frontend Angular del CRM en desarrollo.
#
# Ejecuta 'ng serve' con el proxy que reenvia /api -> http://localhost:8080
# (backend). Arranca el backend ANTES (arrancar-backend.ps1) en otra ventana.
#
# Requisitos: Node.js 20+ y npm. La primera vez instala dependencias (npm ci).
#
# Uso:
#   .\local-dev\arrancar-frontend.ps1
#
# Al arrancar:  http://localhost:4200
#   Login del sistema; la app enruta por ambito (plataforma / empresa / portal)
#   segun el rol del Usuario autenticado.
# ============================================================================
$ErrorActionPreference = "Stop"
$RAIZ     = Split-Path -Parent $PSScriptRoot
$FRONTEND = Join-Path $RAIZ "frontend"

function Escribir($msg, $color = "Cyan") { Write-Host $msg -ForegroundColor $color }

Push-Location $FRONTEND
try {
    if (-not (Test-Path (Join-Path $FRONTEND "node_modules"))) {
        Escribir "Instalando dependencias del frontend (primera vez)..."
        if (Test-Path (Join-Path $FRONTEND "package-lock.json")) { npm ci } else { npm install }
    }
    Escribir ""
    Escribir "Frontend: http://localhost:4200" "Yellow"
    Escribir "El proxy reenvia /api -> http://localhost:8080 (backend con perfil dev)." "DarkGray"
    Escribir "Deten el frontend con Ctrl+C en esta ventana." "DarkGray"
    Escribir ""
    npm start
} finally {
    Pop-Location
}