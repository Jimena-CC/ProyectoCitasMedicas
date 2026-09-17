#!/usr/bin/env bash
#
# Levanta la API en una sola JVM, con la memoria acotada.
#
# Usar `mvn spring-boot:run` deja dos JVM encendidas a la vez (Maven y la
# aplicación) y ronda los 520 MB. Ejecutando el jar directamente baja a ~370 MB.
#
#   ./scripts/run-api.sh            arranca (compila solo si falta el jar)
#   ./scripts/run-api.sh --build    fuerza recompilar antes de arrancar
#
set -euo pipefail
cd "$(dirname "$0")/.."

FORZAR_COMPILACION=false
ARGUMENTOS=()
for arg in "$@"; do
  case "$arg" in
    --build|-b) FORZAR_COMPILACION=true ;;
    *) ARGUMENTOS+=("$arg") ;;
  esac
done

JAR=$(ls api/target/api-*.jar 2>/dev/null | head -1 || true)
if [ "$FORZAR_COMPILACION" = true ] || [ -z "$JAR" ]; then
  echo "Compilando la API…"
  (cd api && ./mvnw -q -DskipTests package)
  JAR=$(ls api/target/api-*.jar | head -1)
fi

echo "API en http://localhost:8080/api/v1  ·  Swagger en http://localhost:8080/swagger-ui.html"
exec java -Xmx256m -XX:MaxMetaspaceSize=160m -jar "$JAR" ${ARGUMENTOS[@]+"${ARGUMENTOS[@]}"}
