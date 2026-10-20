import * as React from 'react';

import type { Appointment } from '@/lib/api';

export type AppointmentAction = 'reschedule' | 'cancel';

export interface AppointmentActionTarget {
  action: AppointmentAction;
  appointment: Appointment;
  /** Cambia en cada apertura para que el modal arranque con el estado limpio (se usa como key). */
  session: number;
  /**
   * false mientras el modal se cierra: se conserva la cita y la sesión para que la key no cambie
   * y el modal pueda mostrar su animación de salida.
   */
  open: boolean;
}

/**
 * Estado compartido de los modales de reprogramar y anular. Lo usan la ficha del paciente y la vista de Citas:
 *   const actions = useAppointmentActions(patient.id);
 *   <AppointmentsTable onReschedule={actions.reschedule} onCancel={actions.cancel} />
 *   <AppointmentActionModals {...actions.modalProps} />
 */
export function useAppointmentActions(patientId: number) {
  const [target, setTarget] = React.useState<AppointmentActionTarget | null>(null);
  const sessionRef = React.useRef(0);

  const openAction = React.useCallback((action: AppointmentAction, appointment: Appointment) => {
    sessionRef.current += 1;
    setTarget({ action, appointment, session: sessionRef.current, open: true });
  }, []);

  const reschedule = React.useCallback((appointment: Appointment) => openAction('reschedule', appointment), [openAction]);
  const cancel = React.useCallback((appointment: Appointment) => openAction('cancel', appointment), [openAction]);
  const onClose = React.useCallback(() => setTarget((current) => (current ? { ...current, open: false } : current)), []);

  return { reschedule, cancel, modalProps: { patientId, target, onClose } };
}
