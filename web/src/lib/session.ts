import type { UserInfo } from '@/lib/api/types';

/**
 * Almacén mínimo de la sesión (token JWT + usuario), independiente de React.
 *
 * - Vive en memoria y se copia a sessionStorage para sobrevivir recargas de la pestaña
 *   (se pierde al cerrar la pestaña o el navegador, que es lo deseado en un equipo compartido).
 * - El cliente de la API lo lee para adjuntar el token y lo limpia cuando el servidor
 *   responde 401; el AuthProvider lo observa con useSyncExternalStore.
 */
export interface Session {
  token: string;
  /** Instante ISO-8601 en que vence el token (lo entrega el login). */
  expiresAt: string;
  user: UserInfo;
}

export type LogoutReason = 'manual' | 'expired' | 'idle' | 'inactive';

const STORAGE_KEY = 'medicitas.backoffice.session';

type Listener = () => void;

let current: Session | null = restore();
let lastLogoutReason: LogoutReason | null = null;
const listeners = new Set<Listener>();

function restore(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<Session>;
    if (!parsed.token || !parsed.expiresAt || !parsed.user) return null;
    if (new Date(parsed.expiresAt).getTime() <= Date.now()) {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return parsed as Session;
  } catch {
    return null;
  }
}

function persist(session: Session | null) {
  try {
    if (session) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // sessionStorage puede no estar disponible (modo privado estricto); la sesión sigue en memoria.
  }
}

function emit() {
  listeners.forEach((listener) => listener());
}

export const sessionStore = {
  get(): Session | null {
    return current;
  },
  getToken(): string | null {
    return current?.token ?? null;
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  set(session: Session) {
    current = session;
    lastLogoutReason = null;
    persist(session);
    emit();
  },
  /** Cierra la sesión y recuerda el motivo para que la pantalla de inicio de sesión lo explique. */
  clear(reason: LogoutReason) {
    if (!current && lastLogoutReason === reason) return;
    current = null;
    lastLogoutReason = reason;
    persist(null);
    emit();
  },
  /** Devuelve el motivo del último cierre de sesión y lo olvida (se muestra una sola vez). */
  consumeLogoutReason(): LogoutReason | null {
    const reason = lastLogoutReason;
    lastLogoutReason = null;
    return reason;
  },
};

/** Mensajes en español para cada motivo de cierre de sesión (RNF-16). */
export const LOGOUT_MESSAGES: Record<Exclude<LogoutReason, 'manual'>, string> = {
  expired: 'Tu sesión expiró. Vuelve a iniciar sesión.',
  idle: 'Cerramos tu sesión por inactividad. Vuelve a iniciar sesión.',
  inactive: 'Tu usuario fue desactivado. Comunícate con el administrador del sistema.',
};
