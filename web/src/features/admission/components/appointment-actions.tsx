import * as React from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RiCalendarScheduleLine, RiCloseCircleLine, RiErrorWarningFill, RiLoader4Line } from '@remixicon/react';

import { ConfirmModal } from '@/components/confirm-modal';
import { SelectField, TextareaField } from '@/components/form-field';
import { notify } from '@/components/notify';
import * as Alert from '@/components/ui/alert';
import * as Button from '@/components/ui/button';
import * as Modal from '@/components/ui/modal';
import { ApiError, catalogApi, frontDeskApi, type Appointment } from '@/lib/api';
import { formatShortDate, formatTime } from '@/lib/formatters';

import type { AppointmentActionTarget } from '../hooks/use-appointment-actions';
import { admissionKeys, formatAppointmentWhen } from '../lib/patients';
import { AvailabilityPicker, type SelectedSlot } from './availability-picker';

interface AppointmentActionModalsProps {
  patientId: number;
  target: AppointmentActionTarget | null;
  onClose: () => void;
}

/** Monta los modales de reprogramar y anular; cada apertura recibe una key nueva para arrancar limpio. */
export function AppointmentActionModals({ patientId, target, onClose }: AppointmentActionModalsProps) {
  return (
    <>
      <RescheduleModal
        key={`reschedule-${target?.session ?? 0}`}
        patientId={patientId}
        appointment={target?.action === 'reschedule' ? target.appointment : null}
        onClose={onClose}
      />
      <CancelModal
        key={`cancel-${target?.session ?? 0}`}
        patientId={patientId}
        appointment={target?.action === 'cancel' ? target.appointment : null}
        onClose={onClose}
      />
    </>
  );
}

function useInvalidateAppointments(patientId: number) {
  const queryClient = useQueryClient();
  return React.useCallback(
    () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admission', 'appointments', patientId] }),
        queryClient.invalidateQueries({ queryKey: ['admission', 'availability'] }),
      ]),
    [queryClient, patientId],
  );
}

interface ActionModalProps {
  patientId: number;
  appointment: Appointment | null;
  onClose: () => void;
}

/**
 * Reprogramar cita (CU-019): modal grande con la misma grilla de disponibilidad del asistente, limitada a la
 * especialidad de la cita. La API solo devuelve nombres de especialidad y sede, así que se resuelven contra el catálogo.
 */
export function RescheduleModal({ patientId, appointment, onClose }: ActionModalProps) {
  const open = appointment !== null;
  const invalidate = useInvalidateAppointments(patientId);
  const [selection, setSelection] = React.useState<SelectedSlot | null>(null);
  const [chosenLocationId, setChosenLocationId] = React.useState<string | null>(null);
  const [alert, setAlert] = React.useState<string | null>(null);

  const locationsQuery = useQuery({ queryKey: admissionKeys.locations, queryFn: catalogApi.listLocations, staleTime: 10 * 60_000, enabled: open });
  const specialtiesQuery = useQuery({
    queryKey: admissionKeys.specialties(null),
    queryFn: () => catalogApi.listSpecialties(),
    staleTime: 10 * 60_000,
    enabled: open,
  });

  const specialty = specialtiesQuery.data?.find((item) => item.name === appointment?.specialty) ?? null;
  const currentLocation = locationsQuery.data?.find((item) => item.name === appointment?.location) ?? null;
  // Sede elegida por el usuario o, por defecto, la sede actual de la cita.
  const locationId = chosenLocationId ?? (currentLocation ? String(currentLocation.id) : '');

  const mutation = useMutation({
    mutationFn: (newSlotId: number) => frontDeskApi.rescheduleAppointment((appointment as Appointment).id, { newSlotId }),
    onSuccess: async (updated) => {
      await invalidate();
      notify.success(`Cita ${updated.code} reprogramada para el ${formatAppointmentWhen(updated)}.`);
      onClose();
    },
    onError: (error) => {
      if (error instanceof ApiError && (error.status === 409 || error.status === 422 || error.isValidation)) {
        setAlert(error.message);
        setSelection(null);
        void invalidate();
      } else {
        notify.fromError(error);
      }
    },
  });

  const catalogsLoaded = locationsQuery.isSuccess && specialtiesQuery.isSuccess;
  const selectedLocationId = Number(locationId) || null;

  return (
    <Modal.Root open={open} onOpenChange={(next) => !next && !mutation.isPending && onClose()}>
      <Modal.Content className='max-w-[960px]'>
        <Modal.Header
          icon={RiCalendarScheduleLine}
          title={appointment ? `Reprogramar la cita ${appointment.code}` : 'Reprogramar cita'}
          description={
            appointment
              ? `${appointment.specialty} con ${appointment.doctor} · ${formatAppointmentWhen(appointment)} · ${appointment.location}`
              : undefined
          }
        />
        <Modal.Body className='flex flex-col gap-5'>
          {alert && (
            <Alert.Root variant='lighter' status='error' size='small'>
              <Alert.Icon as={RiErrorWarningFill} />
              {alert}
            </Alert.Root>
          )}
          {appointment && catalogsLoaded && !specialty ? (
            <Alert.Root variant='lighter' status='warning' size='small'>
              <Alert.Icon as={RiErrorWarningFill} />
              No pudimos identificar la especialidad de esta cita en el catálogo. Anúlala y agenda una nueva.
            </Alert.Root>
          ) : (
            <>
              <div className='grid grid-cols-3 gap-4'>
                <SelectField
                  id='reschedule-location'
                  label='Sede'
                  placeholder={locationsQuery.isPending ? 'Cargando…' : 'Selecciona la sede'}
                  value={locationId}
                  onValueChange={(value) => {
                    setChosenLocationId(value);
                    setSelection(null);
                  }}
                  options={(locationsQuery.data ?? []).map((location) => ({ value: String(location.id), label: location.name }))}
                  disabled={!locationsQuery.isSuccess || mutation.isPending}
                  hint='Debe ser la misma especialidad; la sede puede cambiar.'
                />
              </div>
              {specialty && selectedLocationId ? (
                <AvailabilityPicker
                  specialtyId={specialty.id}
                  locationId={selectedLocationId}
                  selectedSlotId={selection?.slot.slotId ?? null}
                  onSelect={(next) => {
                    setSelection(next);
                    setAlert(null);
                  }}
                  disabled={mutation.isPending}
                />
              ) : null}
            </>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Modal.Close asChild>
            <Button.Root variant='neutral' mode='stroke' size='small' className='w-full' disabled={mutation.isPending}>
              Cancelar
            </Button.Root>
          </Modal.Close>
          <Button.Root
            variant='primary'
            mode='filled'
            size='small'
            className='w-full'
            disabled={!selection || mutation.isPending}
            onClick={() => selection && mutation.mutate(selection.slot.slotId)}
          >
            {mutation.isPending && <Button.Icon as={RiLoader4Line} className='animate-spin' />}
            {selection ? `Reprogramar para el ${formatShortDate(selection.date)} ${formatTime(selection.slot.startTime)}` : 'Elige un nuevo horario'}
          </Button.Root>
        </Modal.Footer>
      </Modal.Content>
    </Modal.Root>
  );
}

/** Anular cita (CU-020): confirmación en rojo con el motivo obligatorio (≤ 200 caracteres). */
export function CancelModal({ patientId, appointment, onClose }: ActionModalProps) {
  const open = appointment !== null;
  const invalidate = useInvalidateAppointments(patientId);
  const [reason, setReason] = React.useState('');
  const [fieldError, setFieldError] = React.useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => frontDeskApi.cancelAppointment((appointment as Appointment).id, { reason: reason.trim() }),
    onSuccess: async (updated) => {
      await invalidate();
      notify.success(`Cita ${updated.code} anulada.`);
      onClose();
    },
    onError: (error) => {
      if (error instanceof ApiError && error.isValidation) setFieldError(error.fieldMessage('reason') ?? error.message);
      else notify.fromError(error);
    },
  });

  const handleConfirm = () => {
    if (reason.trim() === '') {
      setFieldError('Indica el motivo de la anulación.');
      return;
    }
    mutation.mutate();
  };

  return (
    <ConfirmModal
      open={open}
      onOpenChange={(next) => !next && onClose()}
      status='error'
      icon={RiCloseCircleLine}
      title={appointment ? `¿Anular la cita ${appointment.code}?` : '¿Anular la cita?'}
      description={
        appointment
          ? `${appointment.specialty} con ${appointment.doctor}, ${formatAppointmentWhen(appointment)}. El cupo quedará libre y esta acción no se puede deshacer.`
          : undefined
      }
      confirmLabel='Anular cita'
      cancelLabel='Volver'
      onConfirm={handleConfirm}
      isPending={mutation.isPending}
      confirmDisabled={reason.trim() === ''}
    >
      <TextareaField
        id='cancel-reason'
        label='Motivo de la anulación'
        required
        value={reason}
        maxLength={200}
        error={fieldError}
        textareaProps={{
          placeholder: 'p. ej. El paciente viajó y no podrá asistir',
          rows: 3,
          disabled: mutation.isPending,
          onChange: (e) => {
            setReason(e.target.value);
            if (fieldError) setFieldError(null);
          },
        }}
      />
    </ConfirmModal>
  );
}
