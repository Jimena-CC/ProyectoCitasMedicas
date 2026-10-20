import * as React from 'react';
import { RiTimerLine } from '@remixicon/react';

import * as Button from '@/components/ui/button';
import * as Modal from '@/components/ui/modal';
import { useAuth } from '@/features/auth/use-auth';

const DEFAULT_IDLE_MINUTES = 30;
const WARNING_BEFORE_MS = 60_000;
const ACTIVITY_EVENTS: (keyof WindowEventMap)[] = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'wheel'];

function idleLimitMs(): number {
  const minutes = Number(import.meta.env.VITE_IDLE_MINUTES ?? DEFAULT_IDLE_MINUTES);
  const safeMinutes = Number.isFinite(minutes) && minutes > 0 ? minutes : DEFAULT_IDLE_MINUTES;
  return safeMinutes * 60_000;
}

/**
 * Cierre de sesión por inactividad (RNF-10): a los 29 minutos muestra "¿Sigues ahí?" con cuenta regresiva
 * y a los 30 cierra la sesión. Cualquier interacción reinicia el contador mientras el aviso no está visible.
 * Montar una sola vez dentro del área autenticada (AppShell).
 */
export function IdleLogout() {
  const { logout } = useAuth();
  const limit = React.useMemo(() => idleLimitMs(), []);
  // 0 = "ahora" al montar; se fija en el primer efecto para no llamar Date.now() durante el render.
  const lastActivity = React.useRef(0);
  const [secondsLeft, setSecondsLeft] = React.useState<number | null>(null);
  const warningOpen = secondsLeft !== null;

  // Registra actividad (ignorada mientras el aviso está abierto: solo el botón "Seguir conectado" lo cierra).
  React.useEffect(() => {
    if (warningOpen) return;
    let throttle = 0;
    const onActivity = () => {
      const now = Date.now();
      if (now - throttle < 1000) return;
      throttle = now;
      lastActivity.current = now;
    };
    ACTIVITY_EVENTS.forEach((event) => window.addEventListener(event, onActivity, { passive: true }));
    return () => ACTIVITY_EVENTS.forEach((event) => window.removeEventListener(event, onActivity));
  }, [warningOpen]);

  // Reloj: revisa cada segundo cuánto falta; robusto aunque la pestaña haya estado dormida.
  React.useEffect(() => {
    if (lastActivity.current === 0) lastActivity.current = Date.now();
    const tick = () => {
      const elapsed = Date.now() - lastActivity.current;
      const remaining = limit - elapsed;
      if (remaining <= 0) {
        logout('idle');
        return;
      }
      setSecondsLeft(remaining <= WARNING_BEFORE_MS ? Math.ceil(remaining / 1000) : null);
    };
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [limit, logout]);

  const stayConnected = () => {
    lastActivity.current = Date.now();
    setSecondsLeft(null);
  };

  return (
    <Modal.Root open={warningOpen} onOpenChange={(open) => !open && stayConnected()}>
      <Modal.Content className='max-w-[440px]' showClose={false}>
        <Modal.Body className='flex items-start gap-4'>
          <div className='flex size-10 shrink-0 items-center justify-center rounded-10 bg-warning-lighter'>
            <RiTimerLine className='size-6 text-warning-base' />
          </div>
          <div className='space-y-1'>
            <div className='text-label-md text-text-strong-950'>¿Sigues ahí?</div>
            <div className='text-paragraph-sm text-text-sub-600'>
              No detectamos actividad. Por seguridad cerraremos tu sesión en{' '}
              <span className='text-label-sm text-text-strong-950'>{secondsLeft ?? 0} segundos</span>.
            </div>
          </div>
        </Modal.Body>
        <Modal.Footer>
          <Button.Root variant='neutral' mode='stroke' size='small' className='w-full' onClick={() => logout('manual')}>
            Cerrar sesión
          </Button.Root>
          <Button.Root size='small' className='w-full' onClick={stayConnected}>
            Seguir conectado
          </Button.Root>
        </Modal.Footer>
      </Modal.Content>
    </Modal.Root>
  );
}
