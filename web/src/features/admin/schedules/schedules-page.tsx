import * as React from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RiErrorWarningFill, RiLockLine, RiSearchLine } from '@remixicon/react';
import { addDays, differenceInCalendarDays, isValid, parseISO } from 'date-fns';
import { useSearchParams } from 'react-router';

import { Card, CardHeader } from '@/components/card';
import { ConfirmModal } from '@/components/confirm-modal';
import { TextField } from '@/components/form-field';
import { notify } from '@/components/notify';
import { PageHeader } from '@/components/page-header';
import * as Alert from '@/components/ui/alert';
import * as Button from '@/components/ui/button';
import { adminApi } from '@/lib/api/admin';
import { getErrorMessage } from '@/lib/api/client';
import type { SlotDetail, SlotQuery } from '@/lib/api/types';
import { formatLongDate, formatTime, todayIso, toIsoDate } from '@/lib/formatters';

import { DoctorSelect } from '../components/doctor-select';
import { adminKeys, useDoctorsQuery, useLocationsQuery, useSpecialtiesQuery } from '../queries';
import { SlotGenerationForm, type GeneratedRange } from './slot-generation-form';
import { SlotsTable } from './slots-table';

const MAX_RANGE_DAYS = 92;

interface SlotFilter {
  doctorId: string;
  from: string;
  to: string;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function validateFilter(next: SlotFilter): Record<string, string> {
  const errors: Record<string, string> = {};
  if (next.doctorId === '') errors.doctorId = 'Selecciona el médico.';
  if (next.from === '') errors.from = 'Ingresa la fecha inicial.';
  if (next.to === '') errors.to = 'Ingresa la fecha final.';
  else if (next.from && next.to < next.from) errors.to = 'La fecha final debe ser igual o posterior a la fecha inicial.';
  else if (next.from && differenceInCalendarDays(parseISO(next.to), parseISO(next.from)) > MAX_RANGE_DAYS) errors.to = `El rango no puede superar los ${MAX_RANGE_DAYS} días.`;
  return errors;
}

/** Consulta guardada en la URL (?medico=…&desde=…&hasta=…), o null si falta o no es válida. */
function readFilterFromUrl(params: URLSearchParams): SlotFilter | null {
  const doctorId = params.get('medico') ?? '';
  const from = params.get('desde') ?? '';
  const to = params.get('hasta') ?? '';
  if (!/^\d+$/.test(doctorId) || !ISO_DATE.test(from) || !ISO_DATE.test(to)) return null;
  if (!isValid(parseISO(from)) || !isValid(parseISO(to))) return null;
  const filter = { doctorId, from, to };
  return Object.keys(validateFilter(filter)).length === 0 ? filter : null;
}

/** Horarios de atención (CU-024, RF-35): generación de cupos y bloqueo/desbloqueo por médico. */
export function SchedulesPage() {
  const queryClient = useQueryClient();
  const doctorsQuery = useDoctorsQuery();
  const specialtiesQuery = useSpecialtiesQuery();
  const locationsQuery = useLocationsQuery();

  // Filtros del panel "Cupos del médico": se aplican al pulsar "Ver cupos" y se guardan en la URL.
  const [searchParams, setSearchParams] = useSearchParams();
  const [initialFilter] = React.useState(() => readFilterFromUrl(searchParams));
  const [filter, setFilter] = React.useState<SlotFilter>(() => initialFilter ?? { doctorId: '', from: todayIso(), to: toIsoDate(addDays(new Date(), 13)) });
  const [filterErrors, setFilterErrors] = React.useState<Record<string, string>>({});
  const [query, setQuery] = React.useState<SlotQuery | null>(() =>
    initialFilter ? { doctorId: Number(initialFilter.doctorId), from: initialFilter.from, to: initialFilter.to } : null,
  );
  const [blockTarget, setBlockTarget] = React.useState<SlotDetail | null>(null);
  const [blockError, setBlockError] = React.useState<string | null>(null);
  // Guardias síncronas contra el doble clic: isPending recién cambia en el siguiente render.
  const blockInFlight = React.useRef(false);
  const unblockInFlight = React.useRef(new Set<number>());

  const slotsQuery = useQuery({
    queryKey: query ? adminKeys.slots(query.doctorId, query.from, query.to) : [...adminKeys.slotsAll, 'idle'],
    queryFn: () => adminApi.listSlots(query as SlotQuery),
    enabled: query !== null,
  });

  const refreshSlots = () => queryClient.invalidateQueries({ queryKey: adminKeys.slotsAll });

  const blockMutation = useMutation({
    mutationFn: (slot: SlotDetail) => adminApi.blockSlot(slot.id),
    onSuccess: (slot) => {
      refreshSlots();
      notify.success(`Se bloqueó el cupo de las ${formatTime(slot.startTime)} del ${formatLongDate(slot.date)}.`);
      setBlockTarget(null);
    },
    onError: (error) => {
      // El cupo pudo cambiar desde otra sesión (p. ej. se reservó): se muestra el motivo y se refresca la agenda.
      refreshSlots();
      setBlockError(getErrorMessage(error));
    },
    onSettled: () => {
      blockInFlight.current = false;
    },
  });

  const unblockMutation = useMutation({
    mutationFn: (slot: SlotDetail) => adminApi.unblockSlot(slot.id),
    onSuccess: (slot) => {
      refreshSlots();
      notify.success(`Se desbloqueó el cupo de las ${formatTime(slot.startTime)} del ${formatLongDate(slot.date)}.`);
    },
    onError: (error) => {
      // La agenda pudo cambiar desde otra sesión: se muestra el mensaje de la API y se refresca.
      refreshSlots();
      notify.fromError(error);
    },
    // Se libera en el nivel del hook (no en mutate) porque ahí se ejecuta para cada cupo, no solo el último.
    onSettled: (_data, _error, slot) => {
      unblockInFlight.current.delete(slot.id);
    },
  });

  const applyFilter = (next: SlotFilter) => {
    const errors = validateFilter(next);
    setFilterErrors(errors);
    if (Object.keys(errors).length > 0) return;
    const nextQuery: SlotQuery = { doctorId: Number(next.doctorId), from: next.from, to: next.to };
    setSearchParams(
      (prev) => {
        const params = new URLSearchParams(prev);
        params.set('medico', next.doctorId);
        params.set('desde', next.from);
        params.set('hasta', next.to);
        return params;
      },
      { replace: true },
    );
    // Con la misma consulta, "Ver cupos" vuelve a pedir la agenda para mostrar su estado actual.
    if (query && query.doctorId === nextQuery.doctorId && query.from === nextQuery.from && query.to === nextQuery.to) void slotsQuery.refetch();
    else setQuery(nextQuery);
  };

  const unblockSlot = (slot: SlotDetail) => {
    if (unblockInFlight.current.has(slot.id)) return;
    unblockInFlight.current.add(slot.id);
    unblockMutation.mutate(slot);
  };

  const handleGenerated = (range: GeneratedRange) => {
    const next = { doctorId: String(range.doctorId), from: range.from, to: range.to };
    setFilter(next);
    applyFilter(next);
  };

  const pendingSlotId = blockMutation.isPending ? blockMutation.variables?.id : unblockMutation.isPending ? unblockMutation.variables?.id : null;

  return (
    <>
      <PageHeader eyebrow='Administración' title='Horarios' description='Genera los cupos de atención de cada médico y bloquea los que no se deban reservar.' />

      <Card className='flex flex-col gap-5'>
        <CardHeader title='Generar cupos' description='Crea los cupos de un médico en una sede para un rango de fechas. Los horarios que ya existan se conservan.' />
        <SlotGenerationForm
          doctors={doctorsQuery.data ?? []}
          specialties={specialtiesQuery.data ?? []}
          locations={locationsQuery.data ?? []}
          onGenerated={handleGenerated}
        />
      </Card>

      <Card className='flex flex-col gap-5'>
        <CardHeader title='Cupos del médico' description={`Revisa la agenda de un médico en un rango de hasta ${MAX_RANGE_DAYS} días y bloquea o desbloquea cupos.`} />
        <form
          className='grid grid-cols-[360px_170px_170px_auto] items-start gap-3'
          onSubmit={(event) => {
            event.preventDefault();
            applyFilter(filter);
          }}
          noValidate
        >
          <DoctorSelect
            id='list-doctor'
            doctors={doctorsQuery.data ?? []}
            value={filter.doctorId}
            onValueChange={(value) => {
              setFilter((prev) => ({ ...prev, doctorId: value }));
              setFilterErrors((prev) => ({ ...prev, doctorId: '' }));
            }}
            error={filterErrors.doctorId}
            size='small'
          />
          <TextField
            id='list-from'
            label='Desde'
            size='small'
            error={filterErrors.from}
            inputProps={{ type: 'date', value: filter.from, onChange: (e) => setFilter((prev) => ({ ...prev, from: e.target.value })) }}
          />
          <TextField
            id='list-to'
            label='Hasta'
            size='small'
            error={filterErrors.to}
            inputProps={{ type: 'date', value: filter.to, min: filter.from, onChange: (e) => setFilter((prev) => ({ ...prev, to: e.target.value })) }}
          />
          <Button.Root type='submit' variant='neutral' mode='stroke' size='small' className='mt-6' disabled={slotsQuery.isFetching}>
            <Button.Icon as={RiSearchLine} />
            Ver cupos
          </Button.Root>
        </form>

        <SlotsTable
          slots={slotsQuery.data}
          isLoading={query !== null && slotsQuery.isPending}
          errorMessage={slotsQuery.isError ? getErrorMessage(slotsQuery.error) : null}
          onRetry={() => void slotsQuery.refetch()}
          idle={query === null}
          onBlock={(slot) => {
            setBlockError(null);
            setBlockTarget(slot);
          }}
          onUnblock={unblockSlot}
          pendingSlotId={pendingSlotId}
        />
      </Card>

      <ConfirmModal
        open={blockTarget !== null}
        onOpenChange={(open) => !open && setBlockTarget(null)}
        status='warning'
        icon={RiLockLine}
        title='¿Bloquear este cupo?'
        description={
          blockTarget
            ? `El cupo de las ${formatTime(blockTarget.startTime)} del ${formatLongDate(blockTarget.date)} dejará de ofrecerse en el kiosko y en ventanilla. Podrás desbloquearlo después.`
            : undefined
        }
        confirmLabel='Bloquear'
        isPending={blockMutation.isPending}
        onConfirm={() => {
          if (!blockTarget || blockInFlight.current) return;
          blockInFlight.current = true;
          blockMutation.mutate(blockTarget);
        }}
      >
        {blockError && (
          <Alert.Root variant='lighter' status='error' size='small'>
            <Alert.Icon as={RiErrorWarningFill} />
            {blockError}
          </Alert.Root>
        )}
      </ConfirmModal>
    </>
  );
}
