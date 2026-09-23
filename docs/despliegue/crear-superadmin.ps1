#Requires -RunAsAdministrator
<#
================================================================================
  crear-superadmin.ps1
  Crea el usuario super_admin inicial del CRM Dess-TI directamente en la base de
  datos, de forma SEGURA:
    * Pide la contrasena de forma OCULTA (no queda en pantalla ni en el historial).
    * Genera el hash BCrypt con la MISMA libreria del backend (spring-security-crypto),
      extraida del propio jar, de modo que el login la valide correctamente.
    * Inserta el usuario (tenant_id NULL = usuario de PLATAFORMA) y lo vincula al
      rol predefinido 'super_admin' (sembrado por Flyway en V5).
    * Es idempotente: si el identificador ya existe, NO lo duplica (ofrece
      actualizar la contrasena).

  REQUISITOS: Java (JDK) en $JavaExe, el jar del backend, y psql de PostgreSQL.

  USO (PowerShell como Administrador):
    .\crear-superadmin.ps1
  Parametros opcionales:
    -Identificador "admin@dessti.com"   (por defecto admin@dessti.com)
================================================================================
#>

[CmdletBinding()]
param(
    [string]$Identificador = "admin@dessti.com",
    [string]$JavaExe = "C:\Program Files\Java\jdk-21.0.12\bin\java.exe",
    [string]$JShellExe = "C:\Program Files\Java\jdk-21.0.12\bin\jshell.exe",
    [string]$JarPath = "C:\apps\crm\backend\crm-0.0.1-SNAPSHOT.jar",
    [string]$PsqlExe = "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    [string]$DbHost = "127.0.0.1",
    [string]$DbPort = "5432",
    [string]$DbName = "dessti_plataforma",
    [string]$DbUser = "dessti_migrator"
)

$ErrorActionPreference = "Stop"

# ----------------------------------------------------------------------------
# 0) Validaciones
# ----------------------------------------------------------------------------
if (-not (Test-Path $JarPath))   { Write-Error "No se encontro el jar en '$JarPath'."; return }
if (-not (Test-Path $JShellExe)) { Write-Error "No se encontro jshell en '$JShellExe'. Ajuste -JShellExe."; return }
if (-not (Test-Path $PsqlExe))   { Write-Error "No se encontro psql en '$PsqlExe'. Ajuste -PsqlExe."; return }

# ----------------------------------------------------------------------------
# 1) Pedir la contrasena de forma OCULTA (dos veces para confirmar)
# ----------------------------------------------------------------------------
$sec1 = Read-Host -AsSecureString "Contrasena para $Identificador"
$sec2 = Read-Host -AsSecureString "Confirme la contrasena"
$p1 = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec1))
$p2 = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec2))
if ($p1 -ne $p2)      { Write-Error "Las contrasenas no coinciden. Cancelado."; return }
if ($p1.Length -lt 8) { Write-Error "La contrasena debe tener al menos 8 caracteres. Cancelado."; return }

# ----------------------------------------------------------------------------
# 2) Extraer spring-security-crypto del fat jar (contiene BCrypt sin dependencias)
# ----------------------------------------------------------------------------
$work = Join-Path $env:TEMP "crm-superadmin"
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Item -ItemType Directory -Force -Path $work | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
$entry = $zip.Entries | Where-Object { $_.FullName -like "BOOT-INF/lib/spring-security-crypto-*.jar" } | Select-Object -First 1
if (-not $entry) { $zip.Dispose(); Write-Error "No se encontro spring-security-crypto en el jar."; return }
$cryptoJar = Join-Path $work "spring-security-crypto.jar"
[System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $cryptoJar, $true)
$zip.Dispose()

# ----------------------------------------------------------------------------
# 3) Generar el hash BCrypt con jshell (BCrypt.hashpw, strength 10, formato $2a$)
#    La contrasena se pasa por variable de entorno para no escribirla en disco.
# ----------------------------------------------------------------------------
$env:CRM_SUPERADMIN_PWD = $p1
$snippet = @"
import org.springframework.security.crypto.bcrypt.BCrypt;
String pwd = System.getenv("CRM_SUPERADMIN_PWD");
System.out.println("HASH=" + BCrypt.hashpw(pwd, BCrypt.gensalt(10)));
/exit
"@
$snippetFile = Join-Path $work "gen.jsh"
[System.IO.File]::WriteAllText($snippetFile, $snippet, (New-Object System.Text.UTF8Encoding($false)))
$genOut = & $JShellExe --class-path $cryptoJar $snippetFile 2>&1
$env:CRM_SUPERADMIN_PWD = $null   # limpiar de inmediato
$hashLine = ($genOut | Select-String -Pattern "^HASH=").ToString()
if (-not $hashLine) { Write-Error "No se pudo generar el hash. Salida: $genOut"; return }
$hash = $hashLine.Substring(5).Trim()
if ($hash -notmatch '^\$2[aby]\$') { Write-Error "El hash generado no tiene formato BCrypt: $hash"; return }
Write-Host "Hash BCrypt generado correctamente." -ForegroundColor Green

# ----------------------------------------------------------------------------
# 4) Insertar el super_admin en la BD (idempotente) via psql
#    - usuario: tenant_id NULL (usuario de plataforma), activo.
#    - usuario_rol: vinculo al rol predefinido 'super_admin' (tenant_id NULL).
#    Pide la contrasena de la BD de forma oculta.
# ----------------------------------------------------------------------------
$secDb = Read-Host -AsSecureString "Contrasena de la BD para el rol '$DbUser'"
$env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secDb))

# El SQL usa parametros psql (:'ident', :'hash') para evitar inyeccion/escapes.
$sql = @"
\set ON_ERROR_STOP on
DO \$\$
DECLARE
  v_ident text := :'ident';
  v_hash  text := :'hash';
  v_uid   uuid;
  v_rol   uuid;
BEGIN
  SELECT id INTO v_rol FROM rol WHERE nombre = 'super_admin' AND tenant_id IS NULL;
  IF v_rol IS NULL THEN
    RAISE EXCEPTION 'No existe el rol predefinido super_admin (¿Flyway aplico V5?).';
  END IF;

  SELECT id INTO v_uid FROM usuario WHERE identificador_acceso = v_ident;
  IF v_uid IS NULL THEN
    INSERT INTO usuario (tenant_id, identificador_acceso, hash_password, activo, created_by)
    VALUES (NULL, v_ident, v_hash, TRUE, 'despliegue')
    RETURNING id INTO v_uid;
    RAISE NOTICE 'Usuario super_admin creado: %', v_ident;
  ELSE
    UPDATE usuario SET hash_password = v_hash, activo = TRUE, updated_by = 'despliegue', updated_at = now()
    WHERE id = v_uid;
    RAISE NOTICE 'El usuario ya existia; se actualizo su contrasena: %', v_ident;
  END IF;

  INSERT INTO usuario_rol (usuario_id, rol_id)
  VALUES (v_uid, v_rol)
  ON CONFLICT DO NOTHING;
  RAISE NOTICE 'Vinculo usuario<->super_admin asegurado.';
END
\$\$;
"@
$sqlFile = Join-Path $work "seed.sql"
[System.IO.File]::WriteAllText($sqlFile, $sql, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "Ejecutando el alta en la base de datos..." -ForegroundColor Cyan
& $PsqlExe -h $DbHost -p $DbPort -U $DbUser -d $DbName -v ident="$Identificador" -v hash="$hash" -f $sqlFile

$env:PGPASSWORD = $null

# ----------------------------------------------------------------------------
# 5) Limpieza
# ----------------------------------------------------------------------------
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
$p1 = $null; $p2 = $null; $hash = $null

Write-Host ""
Write-Host "Listo. Ya puedes iniciar sesion como '$Identificador' en https://dessti-aplicaciones.ddns.net:8056" -ForegroundColor Green
Write-Host "(Cambia la contrasena desde tu perfil tras el primer acceso.)" -ForegroundColor Yellow