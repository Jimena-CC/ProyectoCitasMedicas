@echo off
setlocal enabledelayedexpansion
rem
rem Levanta la API en una sola JVM, con la memoria acotada.
rem
rem   scripts\run-api.bat            arranca (compila solo si falta el jar)
rem   scripts\run-api.bat --build    fuerza recompilar antes de arrancar
rem

cd /d "%~dp0.."

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

set JAR=
for %%f in (api\target\api-*.jar) do (
    if not defined JAR set JAR=%%f
)

if "%FORZAR_COMPILACION%"=="1" set JAR=

if not defined JAR (
    echo Compilando la API...
    pushd api
    call mvnw.cmd -q -DskipTests package
    if errorlevel 1 (
        popd
        echo.
        echo ERROR: fallo la compilacion de la API. Revisa el log de Maven arriba.
        exit /b 1
    )
    popd
    for %%f in (api\target\api-*.jar) do (
        if not defined JAR set JAR=%%f
    )
)

if not defined JAR (
    echo No se encontro el jar de la API tras compilar. Revisa el log de Maven arriba.
    exit /b 1
)

echo API en http://localhost:8080/api/v1  -  Swagger en http://localhost:8080/swagger-ui.html
java -Xmx256m -XX:MaxMetaspaceSize=160m -jar "%JAR%" !ARGUMENTOS!