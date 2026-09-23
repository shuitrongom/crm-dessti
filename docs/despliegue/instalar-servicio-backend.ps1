#Requires -RunAsAdministrator
<#
================================================================================
  instalar-servicio-backend.ps1
  Instala el backend Spring Boot del CRM Dess-TI como SERVICIO DE WINDOWS, para
  que arranque solo al iniciar el servidor y se reinicie ante fallos.
--------------------------------------------------------------------------------
  DOS METODOS SOPORTADOS:
    * nssm (recomendado, mas simple): "Non-Sucking Service Manager". Es una
      utilidad externa (un unico nssm.exe). Descarguela de https://nssm.cc/download
      y coloque nssm.exe en el PATH o indique su ruta con -NssmPath.
    * sc.exe (nativo de Windows, sin dependencias): se usa como respaldo cuando
      no hay nssm. Genera un pequeno .cmd envoltorio que lanza java -jar y crea
      el servicio con sc.exe. Menos robusto para reinicios/logs que nssm.

  USO:
    1) Copie parametros.example.ps1 como parametros.ps1 y rellene sus valores.
    2) Abra PowerShell COMO ADMINISTRADOR en esta carpeta.
    3) Con nssm (recomendado):
         .\instalar-servicio-backend.ps1 -Metodo nssm -NssmPath C:\tools\nssm.exe
       O, si nssm.exe ya esta en el PATH:
         .\instalar-servicio-backend.ps1 -Metodo nssm
       Con sc.exe (respaldo):
         .\instalar-servicio-backend.ps1 -Metodo sc

  SEGURIDAD: este script NO contiene secretos. Todos los valores sensibles se
  leen desde parametros.ps1 (que NO debe versionarse).
================================================================================
#>

[CmdletBinding()]
param(
    # Metodo de instalacion del servicio: "nssm" (recomendado) o "sc" (respaldo).
    [ValidateSet("nssm", "sc")]
    [string]$Metodo = "nssm",

    # Ruta a nssm.exe (solo para -Metodo nssm). Si esta en el PATH, deje "nssm".
    [string]$NssmPath = "nssm",

    # Ruta al archivo de parametros. Por defecto parametros.ps1 en esta carpeta.
    [string]$ParametrosPath = (Join-Path $PSScriptRoot "parametros.ps1")
)

$ErrorActionPreference = "Stop"

# ----------------------------------------------------------------------------
# 1) Cargar parametros (dot-source). Define $JavaExe, $JarPath, $WorkDir,
#    $LogDir, $ServiceName y $BackendArgs (arreglo de argumentos).
# ----------------------------------------------------------------------------
if (-not (Test-Path $ParametrosPath)) {
    Write-Error "No se encontro '$ParametrosPath'. Copie parametros.example.ps1 como parametros.ps1 y complete sus valores."
    return
}
Write-Host "Cargando parametros desde: $ParametrosPath" -ForegroundColor Cyan
. $ParametrosPath

# ----------------------------------------------------------------------------
# 2) Validaciones basicas.
# ----------------------------------------------------------------------------
if (-not (Test-Path $JavaExe)) { Write-Error "No se encontro java.exe en '$JavaExe'. Instale el JDK 21 o corrija la ruta." ; return }
if (-not (Test-Path $JarPath)) { Write-Error "No se encontro el jar en '$JarPath'. Copie el artefacto del backend o corrija la ruta." ; return }
if (-not (Test-Path $WorkDir))  { New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null }
if (-not (Test-Path $LogDir))   { New-Item -ItemType Directory -Force -Path $LogDir  | Out-Null }

# Si el servicio ya existe, se detiene y elimina para reinstalarlo limpio.
$existente = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($existente) {
    Write-Host "El servicio '$ServiceName' ya existe. Se detiene y elimina para reinstalarlo." -ForegroundColor Yellow
    if ($existente.Status -ne "Stopped") { Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue }
    if ($Metodo -eq "nssm") {
        & $NssmPath remove $ServiceName confirm | Out-Null
    } else {
        sc.exe delete $ServiceName | Out-Null
    }
    Start-Sleep -Seconds 2
}

# ============================================================================
#  METODO 1 — nssm (recomendado)
# ============================================================================
if ($Metodo -eq "nssm") {
    Write-Host "Instalando el servicio '$ServiceName' con nssm..." -ForegroundColor Cyan

    # nssm arranca java.exe con los argumentos: -jar <jar> <BackendArgs...>
    $appParams = @("-jar", $JarPath) + $BackendArgs
    $appParamsStr = ($appParams -join " ")

    & $NssmPath install $ServiceName $JavaExe
    & $NssmPath set $ServiceName AppParameters $appParamsStr
    & $NssmPath set $ServiceName AppDirectory $WorkDir
    & $NssmPath set $ServiceName DisplayName "CRM Dess-TI - Backend (Spring Boot)"
    & $NssmPath set $ServiceName Description "Backend del CRM Dess-TI. Escucha en 127.0.0.1:8080 tras el proxy inverso de IIS."
    & $NssmPath set $ServiceName Start SERVICE_AUTO_START

    # Redireccion de logs (stdout/stderr) a la carpeta de logs.
    & $NssmPath set $ServiceName AppStdout (Join-Path $LogDir "backend-stdout.log")
    & $NssmPath set $ServiceName AppStderr (Join-Path $LogDir "backend-stderr.log")
    # Rotacion de logs por nssm.
    & $NssmPath set $ServiceName AppRotateFiles 1
    & $NssmPath set $ServiceName AppRotateBytes 10485760

    # Reinicio automatico ante caida.
    & $NssmPath set $ServiceName AppExit Default Restart

    Write-Host "Iniciando el servicio..." -ForegroundColor Cyan
    Start-Service -Name $ServiceName
    Start-Sleep -Seconds 3
    Get-Service -Name $ServiceName | Format-Table -AutoSize

    Write-Host "Listo. Verifique el arranque en: $LogDir\backend-stdout.log" -ForegroundColor Green
    Write-Host "El backend debe responder en http://127.0.0.1:$ServerPort/api/v1/actuator/health" -ForegroundColor Green
    return
}

# ============================================================================
#  METODO 2 — sc.exe (respaldo nativo, sin nssm)
# ----------------------------------------------------------------------------
#  sc.exe no puede lanzar directamente java.exe con argumentos largos de forma
#  robusta, por lo que se genera un .cmd envoltorio en el WorkDir. El servicio
#  ejecuta ese .cmd. NOTA: java.exe no es un "servicio nativo" (no responde a
#  los controles SCM), por lo que este metodo es funcional pero menos limpio
#  que nssm para detener/reiniciar. Se recomienda nssm para produccion.
# ============================================================================
Write-Host "Instalando el servicio '$ServiceName' con sc.exe (metodo de respaldo)..." -ForegroundColor Cyan

# Construir el .cmd envoltorio. Se escapan comillas para el .cmd.
$wrapperPath = Join-Path $WorkDir "iniciar-backend.cmd"
$argsLine = ($BackendArgs | ForEach-Object { '"' + $_ + '"' }) -join " "
$wrapperContent = @"
@echo off
REM Envoltorio generado por instalar-servicio-backend.ps1 (metodo sc.exe).
REM Lanza el backend del CRM Dess-TI. Editar via parametros.ps1, no aqui.
cd /d "$WorkDir"
"$JavaExe" -jar "$JarPath" $argsLine >> "$LogDir\backend-stdout.log" 2>> "$LogDir\backend-stderr.log"
"@
Set-Content -Path $wrapperPath -Value $wrapperContent -Encoding ASCII
Write-Host "Envoltorio creado: $wrapperPath" -ForegroundColor DarkGray

# Crear el servicio apuntando al .cmd. binPath usa cmd.exe /c para ejecutar el envoltorio.
$binPath = 'cmd.exe /c "' + $wrapperPath + '"'
sc.exe create $ServiceName binPath= $binPath start= auto DisplayName= "CRM Dess-TI - Backend (Spring Boot)" | Out-Null
sc.exe description $ServiceName "Backend del CRM Dess-TI. Escucha en 127.0.0.1:8080 tras el proxy inverso de IIS." | Out-Null
# Politica de recuperacion: reiniciar ante fallo.
sc.exe failure $ServiceName reset= 86400 actions= restart/5000/restart/5000/restart/5000 | Out-Null

Write-Host "Iniciando el servicio..." -ForegroundColor Cyan
Start-Service -Name $ServiceName
Start-Sleep -Seconds 3
Get-Service -Name $ServiceName | Format-Table -AutoSize

Write-Host "Listo (metodo sc.exe). Verifique el arranque en: $LogDir\backend-stdout.log" -ForegroundColor Green
Write-Host "El backend debe responder en http://127.0.0.1:$ServerPort/api/v1/actuator/health" -ForegroundColor Green
