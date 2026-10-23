import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { RiCalendarCheckLine } from '@remixicon/react';

import { CardHeader } from '@/components/card';
import * as Button from '@/components/ui/button';
import * as SegmentedControl from '@/components/ui/segmented-control';
import { frontDeskApi, getErrorMessage, type Patient } from '@/lib/api';

import { useAppointmentActions } from '../hooks/use-appointment-actions';
import { admissionKeys, bookingPath } from '../lib/patients';
import { AppointmentActionModals } from './appointment-actions';
import { AppointmentsTable } from './appointments-table';

interface PatientAppointmentsProps {
  patient: Patient;
  /** Muestra el título de sección dentro de la tarjeta (ficha del paciente). */
  withHeader?: boolean;
}

/**
 * Citas del paciente con el conmutador "Próximas / Todas" (segmented-control-demo) y las acciones de
 * reprogramar y anular. Lo comparten la pestaña "Citas" de la ficha y la vista de Citas.
 */
export function PatientAppointments({ patient, withHeader = false }: PatientAppointmentsProps) {
  // El filtro viaja en la URL (?citas=todas), igual que la búsqueda y la pestaña, para que recargar lo conserve.
  const [params, setParams] = useSearchParams();
  const scope: 'upcoming' | 'all' = params.get('citas') === 'todas' ? 'all' : 'upcoming';
  const upcoming = scope === 'upcoming';
  const setScope = (next: 'upcoming' | 'all') => {
    setParams(
      (current) => {
        const updated = new URLSearchParams(current);
        if (next === 'all') updated.set('citas', 'todas');
        else updated.delete('citas');
        return updated;
      },
      { replace: true },
    );
  };
  const query = useQuery({
    queryKey: admissionKeys.appointments(patient.id, upcoming),
    queryFn: () => frontDeskApi.listPatientAppointments(patient.id, upcoming),
  });
  const actions = useAppointmentActions(patient.id);

  const toggle = (
    <SegmentedControl.Root value={scope} onValueChange={(value) => setScope(value as 'upcoming' | 'all')} className='w-[260px]'>
      <SegmentedControl.List>
        <SegmentedControl.Trigger value='upcoming'>Próximas</SegmentedControl.Trigger>
        <SegmentedControl.Trigger value='all'>Todas</SegmentedControl.Trigger>
      </SegmentedControl.List>
    </SegmentedControl.Root>
  );

  const bookButton = (
    <Button.Root variant='primary' mode='filled' size='small' asChild>
      <Link viewTransition to={bookingPath(patient)}>
        <Button.Icon as={RiCalendarCheckLine} />
        Agendar cita
      </Link>
    </Button.Root>
  );

  return (
    <div className='flex flex-col gap-4'>
      {withHeader ? (
        <CardHeader
          title='Citas del paciente'
          description={upcoming ? 'Citas reservadas que aún no ocurren.' : 'Historial completo, de la más reciente a la más antigua.'}
          actions={toggle}
        />
      ) : (
        <div className='flex items-center justify-between gap-3'>
          {toggle}
          {bookButton}
        </div>
      )}
      <AppointmentsTable
        appointments={query.data}
        isLoading={query.isPending}
        errorMessage={query.isError ? getErrorMessage(query.error) : null}
        onRetry={() => void query.refetch()}
        onReschedule={actions.reschedule}
        onCancel={actions.cancel}
        emptyTitle={upcoming ? 'No tiene citas próximas' : 'No tiene citas registradas'}
        emptyDescription={upcoming ? 'Puedes reservarle una cita nueva o revisar su historial en "Todas".' : 'Cuando reserves una cita aparecerá aquí.'}
        emptyAction={bookButton}
      />
      <AppointmentActionModals {...actions.modalProps} />
    </div>
  );
}
