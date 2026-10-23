/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** URL base de la API, p. ej. http://localhost:8080/api/v1 */
  readonly VITE_API_URL?: string;
  /** Minutos de inactividad antes de cerrar la sesión (por defecto 30). */
  readonly VITE_IDLE_MINUTES?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
