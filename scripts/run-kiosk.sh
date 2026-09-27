#!/usr/bin/env bash
#
# Levanta el kiosko en una sola JVM, con la memoria acotada.
#
# Usar `mvn javafx:run` deja dos JVM encendidas a la vez (Maven y la
# aplicación). Ejecutando las clases directamente se ahorra una de ellas.
#
#   ./scripts/run-kiosk.sh                  contra la API en localhost:8080
#   ./scripts/run-kiosk.sh --demo-stub      con datos simulados, sin API ni base de datos
#   ./scripts/run-kiosk.sh --kiosk          a pantalla completa
#   ./scripts/run-kiosk.sh --build          fuerza recompilar antes de arrancar
#
# La URL de la API se puede cambiar con la variable API_URL.
#
set -euo pipefail
cd "$(dirname "$0")/.."

API_URL="${API_URL:-http://localhost:8080/api/v1}"

FORZAR_COMPILACION=false
ARGUMENTOS=()
for arg in "$@"; do
  case "$arg" in
    --build|-b) FORZAR_COMPILACION=true ;;
    *) ARGUMENTOS+=("$arg") ;;
  esac
done

if [ "$FORZAR_COMPILACION" = true ] || [ ! -f desktop/target/cp.txt ] || [ ! -d desktop/target/classes ]; then
  echo "Compilando el kiosko…"
  (cd desktop && ./mvnw -q -DskipTests package \
                && ./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt)
fi

echo "Kiosko conectado a $API_URL"
exec java -Xmx256m -XX:MaxMetaspaceSize=160m \
  -Dapi.url="$API_URL" \
  -cp "desktop/target/classes:$(cat desktop/target/cp.txt)" \
  com.medicitas.kiosk.app.KioskLauncher ${ARGUMENTOS[@]+"${ARGUMENTOS[@]}"}
