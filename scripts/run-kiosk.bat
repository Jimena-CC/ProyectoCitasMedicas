@echo off
setlocal enabledelayedexpansion
rem
rem Levanta el kiosko en una sola JVM, con la memoria acotada.
rem
rem   scripts\run-kiosk.bat                  contra la API en localhost:8080
rem   scripts\run-kiosk.bat --demo-stub      con datos simulados, sin API ni base de datos
rem   scripts\run-kiosk.bat --kiosk          a pantalla completa
rem   scripts\run-kiosk.bat --build          fuerza recompilar antes de arrancar
rem
rem La URL de la API se puede cambiar con la variable de entorno API_URL.
rem

cd /d "%~dp0.."

if not defined API_URL set API_URL=http://localhost:8080/api/v1

set FORZAR_COMPILACION=0
set ARGUMENTOS=

:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="--build" (
    set FORZAR_COMPILACION=1
) else if /i "%~1"=="-b" (
    set FORZAR_COMPILACION=1
) else (
    set ARGUMENTOS=!ARGUMENTOS! %1
)
shift
goto parse_args
:args_done

if "%FORZAR_COMPILACION%"=="1" goto compilar
if not exist "desktop\target\cp.txt" goto compilar
if not exist "desktop\target\classes" goto compilar
goto seguir

:compilar
echo Compilando el kiosko...
pushd desktop
call mvnw.cmd -q -DskipTests package
if errorlevel 1 (
    popd
    echo.
    echo ERROR: fallo la compilacion del kiosko. Revisa el log de Maven arriba.
    exit /b 1
)
call mvnw.cmd -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
if errorlevel 1 (
    popd
    echo.
    echo ERROR: fallo al armar el classpath. Revisa el log de Maven arriba.
    exit /b 1
)
popd

:seguir
for /f "usebackq delims=" %%C in ("desktop\target\cp.txt") do set "CLASSPATH_DEPS=%%C"

echo Kiosko conectado a %API_URL%
java -Xmx256m -XX:MaxMetaspaceSize=160m -Dapi.url=%API_URL% -cp "desktop\target\classes;%CLASSPATH_DEPS%" com.medicitas.kiosk.app.KioskLauncher !ARGUMENTOS!