@echo off
setlocal enabledelayedexpansion
rem
rem Levanta el Backoffice Web en modo desarrollo (Vite) en http://localhost:5173.
rem
rem   scripts\run-web.bat            instala dependencias si faltan y arranca
rem   scripts\run-web.bat --install  fuerza reinstalar dependencias antes de arrancar
rem
rem Requiere Node 22+ y la API corriendo (scripts\run-api.bat). La URL de la API se
rem configura con VITE_API_URL (por defecto http://localhost:8080/api/v1); ver web\.env.example.
rem

cd /d "%~dp0..\web"

set FORZAR_INSTALACION=0
set ARGUMENTOS=

:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="--install" (
    set FORZAR_INSTALACION=1
) else if /i "%~1"=="-i" (
    set FORZAR_INSTALACION=1
) else (
    set ARGUMENTOS=!ARGUMENTOS! %1
)
shift
goto parse_args
:args_done

where npm >nul 2>nul
if errorlevel 1 (
    echo No se encontro npm. Instala Node 22 o superior desde https://nodejs.org y vuelve a intentarlo.
    exit /b 1
)

if "%FORZAR_INSTALACION%"=="1" goto install
if not exist node_modules goto install
goto run

:install
echo Instalando dependencias del backoffice web...
call npm install
if errorlevel 1 (
    echo.
    echo ERROR: fallo la instalacion de dependencias. Revisa el log de npm arriba.
    exit /b 1
)

:run
if "%VITE_API_URL%"=="" (
    echo Backoffice Web en http://localhost:5173  -  API esperada en http://localhost:8080/api/v1
) else (
    echo Backoffice Web en http://localhost:5173  -  API esperada en %VITE_API_URL%
)
call npm run dev -- !ARGUMENTOS!
