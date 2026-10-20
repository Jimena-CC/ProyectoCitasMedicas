#!/usr/bin/env bash
#
# Levanta el Backoffice Web en modo desarrollo (Vite) en http://localhost:5173.
#
#   ./scripts/run-web.sh            instala dependencias si faltan y arranca
#   ./scripts/run-web.sh --install  fuerza reinstalar dependencias antes de arrancar
#
# Requiere Node 22+ y la API corriendo (./scripts/run-api.sh). La URL de la API se
# configura con VITE_API_URL (por defecto http://localhost:8080/api/v1); ver web/.env.example.
#
set -euo pipefail
cd "$(dirname "$0")/../web"

FORZAR_INSTALACION=false
ARGUMENTOS=()
for arg in "$@"; do
  case "$arg" in
    --install|-i) FORZAR_INSTALACION=true ;;
    *) ARGUMENTOS+=("$arg") ;;
  esac
done

if ! command -v npm >/dev/null 2>&1; then
  echo "No se encontró npm. Instala Node 22 o superior desde https://nodejs.org y vuelve a intentarlo." >&2
  exit 1
fi

if [ "$FORZAR_INSTALACION" = true ] || [ ! -d node_modules ]; then
  echo "Instalando dependencias del backoffice web…"
  npm install
fi

echo "Backoffice Web en http://localhost:5173  ·  API esperada en ${VITE_API_URL:-http://localhost:8080/api/v1}"
exec npm run dev -- ${ARGUMENTOS[@]+"${ARGUMENTOS[@]}"}
