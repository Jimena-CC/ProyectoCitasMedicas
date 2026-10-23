import { toast } from '@/components/ui/toast';
import * as AlertToast from '@/components/ui/toast-alert';
import { getErrorMessage } from '@/lib/api/client';

type ToastStatus = 'success' | 'error' | 'warning' | 'information';

/** Posición de la pila de avisos; el <Toaster> de main.tsx usa la misma. */
export const TOAST_POSITION = 'top-center';

/**
 * Los avisos de éxito solo confirman lo que el usuario acaba de hacer: duran menos para no quedarse sobre el
 * título de la página. Los de error y advertencia mantienen la duración por defecto (4 s) para poder leerlos.
 */
const SUCCESS_DURATION_MS = 3000;

function show(status: ToastStatus, message: string) {
  return toast.custom((t) => <AlertToast.Root t={t} status={status} message={message} />, {
    position: TOAST_POSITION,
    ...(status === 'success' && { duration: SUCCESS_DURATION_MS }),
  });
}

/**
 * Avisos flotantes con el estilo AlignUI (ejemplo toast-demo).
 *
 *   notify.success('Cita reservada.');
 *   notify.fromError(error);   // muestra el mensaje en español de un ApiError
 */
export const notify = {
  success: (message: string) => show('success', message),
  error: (message: string) => show('error', message),
  warning: (message: string) => show('warning', message),
  info: (message: string) => show('information', message),
  /** Convierte cualquier error capturado en un aviso de error legible. */
  fromError: (error: unknown, fallback?: string) => show('error', getErrorMessage(error, fallback)),
  dismiss: (id?: string | number) => toast.dismiss(id),
};
