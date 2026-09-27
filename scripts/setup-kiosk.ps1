<#
.SYNOPSIS
    Configura un kiosko recien instalado: guarda la URL de la API una sola vez
    y registra el arranque automatico al encender el equipo.

.DESCRIPTION
    Correr UNA sola vez por kiosko, como Administrador, despues de instalar
    el .exe de "MediCitas Kiosko".

.EXAMPLE
    .\setup-kiosk.ps1 -ApiUrl "http://192.168.1.50:8080/api/v1"
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$ApiUrl
)
$ErrorActionPreference = "Stop"

# --- 1. Guarda la configuracion, una sola vez ---
$ConfigDir = Join-Path $env:ProgramData "MediCitasKiosk"
New-Item -ItemType Directory -Path $ConfigDir -Force | Out-Null
"api.url=$ApiUrl" | Set-Content -Path (Join-Path $ConfigDir "kiosk.properties") -Encoding UTF8
Write-Host "Configuracion guardada en $ConfigDir\kiosk.properties"

# --- 2. Ubica el ejecutable instalado ---
$ExePath = "C:\Program Files\MediCitas Kiosko\MediCitas Kiosko.exe"
if (-not (Test-Path $ExePath)) {
    Write-Error "No se encontro el ejecutable en '$ExePath'. Instala el .exe primero, o ajusta esta ruta si lo instalaste en otro lugar."
    exit 1
}

# --- 3. Registra el arranque automatico (tarea programada, corre al iniciar sesion) ---
$Action = New-ScheduledTaskAction -Execute $ExePath -Argument "--kiosk"
$Trigger = New-ScheduledTaskTrigger -AtLogOn
$Principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive
$Settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

Register-ScheduledTask -TaskName "MediCitas Kiosko - Autoarranque" `
    -Action $Action -Trigger $Trigger -Principal $Principal -Settings $Settings -Force | Out-Null

Write-Host "Listo. El kiosko arrancara solo, en pantalla completa, cada vez que se inicie sesion en esta PC."
Write-Host "Para probarlo ahora mismo sin reiniciar: Start-ScheduledTask -TaskName 'MediCitas Kiosko - Autoarranque'"