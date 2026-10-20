import * as React from 'react';
import { useSearchParams } from 'react-router';
import { RiErrorWarningFill, RiLoader4Line, RiPrinterLine, RiRefreshLine } from '@remixicon/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { Card, CardHeader } from '@/components/card';
import { notify } from '@/components/notify';
import { PageHeader } from '@/components/page-header';
import * as Alert from '@/components/ui/alert';
import * as Button from '@/components/ui/button';
import * as SegmentedControl from '@/components/ui/segmented-control';
import { useCurrentUser } from '@/features/auth/use-auth';
import { ApiError, doctorApi, getErrorMessage } from '@/lib/api';
import type { AgendaItem, AppointmentStatus, AttendanceRequest, IsoDate } from '@/lib/api';
import { formatLongDate, todayIso } from '@/lib/formatters';

import { canRegisterAttendance, findNextPending, resolveAgendaDate } from '@/features/doctor/agenda-date';
import { AgendaSummary } from '@/features/doctor/components/agenda-summary';
import { AgendaTable } from '@/features/doctor/components/agenda-table';
import { AttendanceModal, NOTE_MAX_LENGTH, type AttendanceTarget } from '@/features/doctor/components/attendance-modal';
import { DateNavigator } from '@/features/doctor/components/date-navigator';
import '@/features/doctor/agenda-print.css';

type AgendaFilter = 'all' | Extract<AppointmentStatus, 'BOOKED' | 'ATTENDED' | 'NO_SHOW'>;

const FILTERS: { value: AgendaFilter; label: string; emptyTitle: string }[] = [
  { value: 'all', label: 'Todas', emptyTitle: 'No tienes citas para este día' },
  { value: 'BOOKED', label: 'Pendientes', emptyTitle: 'No hay citas pendientes este día' },
  { value: 'ATTENDED', label: 'Atendidas', emptyTitle: 'Aún no hay citas atendidas este día' },
  { value: 'NO_SHOW', label: 'No asistieron', emptyTitle: 'No hay inasistencias este día' },
];

const AGENDA_QUERY_KEY = ['agenda'] as const;

/**
 * Agenda del día del médico (CU-014) con registro de asistencia (CU-015).
 * La fecha viaja en `?fecha=yyyy-MM-dd`; sin parámetro se muestra hoy.
 */
export function AgendaPage() {
  const user = useCurrentUser();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const date = resolveAgendaDate(searchParams.get('fecha'));
  const today = todayIso();

  const [filter, setFilter] = React.useState<AgendaFilter>('all');
  const [target, setTarget] = React.useState<AttendanceTarget | null>(null);
  const [note, setNote] = React.useState('');
  const [noteError, setNoteError] = React.useState<string | null>(null);

  const agendaQuery = useQuery({
    queryKey: [...AGENDA_QUERY_KEY, date],
    queryFn: () => doctorApi.getAgenda(date),
  });

  const attendance = useMutation({
    mutationFn: ({ appointmentId, body }: { appointmentId: number; body: AttendanceRequest }) =>
      doctorApi.registerAttendance(appointmentId, body),
  });

  const changeDate = (next: IsoDate) => {
    setSearchParams(next === today ? {} : { fecha: next });
  };

  const agenda = agendaQuery.data;
  const error = agendaQuery.error;
  const notLinked = error instanceof ApiError && error.code === 'DOCTOR_NOT_LINKED';
  const errorMessage = error && !notLinked ? getErrorMessage(error) : null;

  const items = agenda?.items;
  const visibleItems = React.useMemo(() => {
    if (!items) return undefined;
    return filter === 'all' ? items : items.filter((item) => item.status === filter);
  }, [items, filter]);
  const highlightedId = React.useMemo(() => (items ? findNextPending(items, date) : null), [items, date]);
  const canRegister = canRegisterAttendance(date);
  const activeFilter = FILTERS.find((option) => option.value === filter) ?? FILTERS[0];

  const openAttendance = (item: AgendaItem, status: AttendanceTarget['status']) => {
    setNote('');
    setNoteError(null);
    setTarget({ item, status });
  };

  const closeAttendance = () => {
    setTarget(null);
    setNoteError(null);
  };

  const confirmAttendance = async () => {
    if (!target) return;
    const trimmed = note.trim();
    if (trimmed.length > NOTE_MAX_LENGTH) {
      setNoteError(`La nota no puede superar los ${NOTE_MAX_LENGTH} caracteres.`);
      return;
    }
    try {
      const updated = await attendance.mutateAsync({
        appointmentId: target.item.appointmentId,
        body: { status: target.status, note: trimmed === '' ? null : trimmed },
      });
      const name = updated.patient.fullName;
      notify.success(updated.status === 'ATTENDED' ? `Registraste la atención de ${name}.` : `Registraste que ${name} no asistió.`);
      closeAttendance();
      await queryClient.invalidateQueries({ queryKey: AGENDA_QUERY_KEY });
    } catch (caught) {
      if (caught instanceof ApiError && caught.isValidation) {
        setNoteError(caught.fieldMessage('note') ?? caught.message);
        return;
      }
      notify.fromError(caught);
      // La cita cambió desde otro lugar (anulada, ya registrada, no encontrada): se cierra y se refresca la agenda.
      if (caught instanceof ApiError && (caught.status === 404 || caught.status === 422 || caught.status === 403)) {
        closeAttendance();
        void queryClient.invalidateQueries({ queryKey: AGENDA_QUERY_KEY });
      }
    }
  };

  const doctorLine = agenda ? `${agenda.doctor.fullName} · ${agenda.doctor.specialty}` : user.fullName;
  const cardDescription = (() => {
    if (!agenda) return 'Citas ordenadas por hora de inicio.';
    const { total } = agenda.summary;
    if (total === 0) return 'No hay citas registradas para esta fecha.';
    const count = `${total} ${total === 1 ? 'cita ordenada' : 'citas ordenadas'} por hora`;
    return canRegister ? count : `${count}. Podrás registrar la asistencia el día de la atención.`;
  })();

  return (
    <>
      <PageHeader
        eyebrow={formatLongDate(date)}
        title='Agenda del día'
        description={doctorLine}
        actions={
          <div className='flex items-center gap-2 print:hidden'>
            <DateNavigator date={date} onChange={changeDate} />
            <Button.Root
              variant='neutral'
              mode='stroke'
              size='xsmall'
              onClick={() => void agendaQuery.refetch()}
              disabled={agendaQuery.isFetching}
              aria-label='Actualizar la agenda'
            >
              <Button.Icon as={agendaQuery.isFetching ? RiLoader4Line : RiRefreshLine} className={agendaQuery.isFetching ? 'animate-spin' : undefined} />
              Actualizar
            </Button.Root>
            <Button.Root
              variant='neutral'
              mode='stroke'
              size='xsmall'
              onClick={() => window.print()}
              disabled={!agenda || agenda.items.length === 0}
              aria-label='Imprimir la agenda del día'
            >
              <Button.Icon as={RiPrinterLine} />
              Imprimir agenda
            </Button.Root>
          </div>
        }
      />

      {notLinked ? (
        <Alert.Root variant='lighter' status='warning' size='small'>
          <Alert.Icon as={RiErrorWarningFill} />
          {error.message}
        </Alert.Root>
      ) : (
        <>
          <AgendaSummary summary={agenda?.summary} isLoading={agendaQuery.isPending} />

          <Card flush className='agenda-print-card'>
            <CardHeader
              className='px-5 pt-5'
              title='Citas del día'
              description={cardDescription}
              actions={
                <SegmentedControl.Root value={filter} onValueChange={(value) => setFilter(value as AgendaFilter)} className='w-[420px] print:hidden'>
                  <SegmentedControl.List>
                    {FILTERS.map((option) => (
                      <SegmentedControl.Trigger key={option.value} value={option.value}>
                        {option.label}
                      </SegmentedControl.Trigger>
                    ))}
                  </SegmentedControl.List>
                </SegmentedControl.Root>
              }
            />
            <div className='px-4 pb-4 pt-4'>
              <AgendaTable
                items={visibleItems}
                isLoading={agendaQuery.isPending}
                errorMessage={errorMessage}
                onRetry={() => void agendaQuery.refetch()}
                highlightedId={highlightedId}
                canRegister={canRegister}
                onRegister={openAttendance}
                emptyTitle={items && items.length === 0 ? FILTERS[0].emptyTitle : activeFilter.emptyTitle}
                emptyDescription={
                  items && items.length === 0
                    ? 'Cuando tengas citas reservadas para esta fecha aparecerán aquí ordenadas por hora.'
                    : 'Cambia el filtro para ver el resto de citas del día.'
                }
              />
            </div>
          </Card>
        </>
      )}

      <AttendanceModal
        target={target}
        onOpenChange={(open) => {
          if (!open) closeAttendance();
        }}
        note={note}
        onNoteChange={(value) => {
          setNote(value);
          if (noteError) setNoteError(null);
        }}
        noteError={noteError}
        isPending={attendance.isPending}
        onConfirm={confirmAttendance}
      />
    </>
  );
}
