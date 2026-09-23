# ============================================================================
# arrancar-local.ps1  (COMPATIBILIDAD)
# Delega en arrancar-backend.ps1. Mantiene el comportamiento previo: levanta
# PostgreSQL en contenedor. Para usar TU PostgreSQL, ejecuta directamente:
#   .\local-dev\arrancar-backend.ps1 -DbUser <usuario> -DbPassword <clave> -DbNombre crm
# ============================================================================
& (Join-Path $PSScriptRoot "arrancar-backend.ps1") -UsarDocker -DbUser crm_app -DbPassword crm_local_pwd -DbNombre crm