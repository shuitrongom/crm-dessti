# Detiene y elimina el contenedor PostgreSQL local del CRM (si se uso -UsarDocker).
# Si usas TU PostgreSQL, este script no aplica: tu base sigue corriendo.
$DK = "docker"
& $DK stop crm-postgres-local 2>$null
& $DK rm crm-postgres-local 2>$null
Write-Host "Contenedor PostgreSQL local detenido y eliminado (si existia)." -ForegroundColor Green