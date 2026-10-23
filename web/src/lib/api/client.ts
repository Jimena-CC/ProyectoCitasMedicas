import { sessionStore } from '@/lib/session';
import type { ErrorResponse, FieldError } from '@/lib/api/types';

/** URL base de la API (sin barra final). Se configura con VITE_API_URL. */
export const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1').replace(/\/+$/, '');

export const NETWORK_ERROR_MESSAGE =
  'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.';
export const UNEXPECTED_ERROR_MESSAGE =
  'Ocurrió un problema al procesar tu solicitud. Inténtalo nuevamente en unos minutos.';

/**
 * Error de la API ya traducido a algo mostrable.
 * `message` siempre está en español y listo para el usuario (RNF-16):
 * los mensajes del servidor se muestran tal cual; los fallos de red usan NETWORK_ERROR_MESSAGE.
 */
export class ApiError extends Error {
  /** Código HTTP; 0 cuando no hubo respuesta (sin conexión). */
  readonly status: number;
  /** Código de negocio del contrato (INVALID_DATA, SLOT_NOT_AVAILABLE, NETWORK_ERROR...). */
  readonly code: string;
  readonly fields: FieldError[];
  readonly timestamp: string | null;

  constructor(status: number, code: string, message: string, fields: FieldError[] = [], timestamp: string | null = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fields = fields;
    this.timestamp = timestamp;
  }

  /** true cuando el servidor devolvió errores por campo (400 INVALID_DATA). */
  get isValidation(): boolean {
    return this.fields.length > 0;
  }

  get isNetwork(): boolean {
    return this.status === 0;
  }

  /** Mensaje del campo indicado, si el servidor lo reportó. */
  fieldMessage(field: string): string | undefined {
    return this.fields.find((f) => f.field === field)?.message;
  }

  /** Mapa campo → mensaje, cómodo para pintar formularios. */
  fieldErrors(): Record<string, string> {
    return Object.fromEntries(this.fields.map((f) => [f.field, f.message]));
  }
}

/** Extrae un mensaje apto para el usuario de cualquier error capturado. */
export function getErrorMessage(error: unknown, fallback = UNEXPECTED_ERROR_MESSAGE): string {
  if (error instanceof ApiError) return error.message;
  return fallback;
}

export type QueryValue = string | number | boolean | null | undefined;
export type QueryParams = Record<string, QueryValue>;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  /** Cuerpo JSON; se serializa automáticamente. */
  body?: unknown;
  /** Parámetros de consulta; se omiten los null/undefined/''. */
  query?: QueryParams;
  /** Cabecera Idempotency-Key (UUID v4) para operaciones que la exigen. */
  idempotencyKey?: string;
  /** Adjuntar el token de la sesión (por defecto sí, si existe). */
  auth?: boolean;
  signal?: AbortSignal;
}

export interface BlobResult {
  blob: Blob;
  /** Nombre sugerido por Content-Disposition, si vino. */
  filename: string | null;
}

/** Genera una clave de idempotencia (UUID v4) para una reserva. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

function buildUrl(path: string, query?: QueryParams): string {
  const url = new URL(path.startsWith('/') ? `${API_URL}${path}` : `${API_URL}/${path}`);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') continue;
      url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

function buildHeaders(options: RequestOptions): Headers {
  const headers = new Headers({ Accept: 'application/json' });
  if (options.body !== undefined) headers.set('Content-Type', 'application/json');
  if (options.idempotencyKey) headers.set('Idempotency-Key', options.idempotencyKey);
  const token = options.auth === false ? null : sessionStore.getToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  return headers;
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const hadToken = options.auth !== false && sessionStore.getToken() !== null;
  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers: buildHeaders(options),
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError(0, 'NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  if (response.ok) return response;

  const apiError = await toApiError(response);

  // Sesión vencida o manipulada: se cierra la sesión y RequireAuth redirige a /login con el aviso.
  if (hadToken && response.status === 401) {
    sessionStore.clear('expired');
  }
  // El administrador desactivó al usuario mientras trabajaba: no tiene sentido seguir.
  if (hadToken && response.status === 403 && apiError.code === 'USER_INACTIVE') {
    sessionStore.clear('inactive');
  }
  throw apiError;
}

async function toApiError(response: Response): Promise<ApiError> {
  let payload: Partial<ErrorResponse> | null = null;
  try {
    const text = await response.text();
    payload = text ? (JSON.parse(text) as Partial<ErrorResponse>) : null;
  } catch {
    payload = null;
  }
  const fields = Array.isArray(payload?.fields) ? payload.fields : [];
  const message =
    typeof payload?.message === 'string' && payload.message.trim() !== ''
      ? payload.message
      : defaultMessageFor(response.status);
  return new ApiError(response.status, payload?.code ?? `HTTP_${response.status}`, message, fields, payload?.timestamp ?? null);
}

function defaultMessageFor(status: number): string {
  switch (status) {
    case 401:
      return 'Debes iniciar sesión para continuar.';
    case 403:
      return 'No tienes permiso para realizar esta acción.';
    case 404:
      return 'El recurso solicitado no existe.';
    default:
      return UNEXPECTED_ERROR_MESSAGE;
  }
}

async function parseJson<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

function filenameFrom(response: Response): string | null {
  const disposition = response.headers.get('Content-Disposition');
  if (!disposition) return null;
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (utf8) return decodeURIComponent(utf8[1]);
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain ? plain[1] : null;
}

/** Petición genérica que devuelve el JSON tipado de la respuesta. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options);
  return parseJson<T>(response);
}

/** Petición que devuelve un archivo (p. ej. CSV) junto con su nombre sugerido. */
export async function requestBlob(path: string, options: RequestOptions = {}): Promise<BlobResult> {
  const response = await send(path, options);
  return { blob: await response.blob(), filename: filenameFrom(response) };
}

/** Atajos por verbo. */
export const http = {
  get: <T>(path: string, query?: QueryParams, options: Omit<RequestOptions, 'method' | 'query' | 'body'> = {}) =>
    request<T>(path, { ...options, method: 'GET', query }),
  post: <T>(path: string, body?: unknown, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    request<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    request<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    request<T>(path, { ...options, method: 'DELETE' }),
  blob: (path: string, query?: QueryParams, options: Omit<RequestOptions, 'method' | 'query' | 'body'> = {}) =>
    requestBlob(path, { ...options, method: 'GET', query }),
};

/** Dispara la descarga de un Blob en el navegador (el token va en cabecera, no se puede abrir la URL directa). */
export function downloadBlob(result: BlobResult, fallbackName: string) {
  const url = URL.createObjectURL(result.blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = result.filename ?? fallbackName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
