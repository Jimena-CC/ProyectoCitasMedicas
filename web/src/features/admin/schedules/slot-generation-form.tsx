import * as React from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { RiCalendarCheckLine, RiCheckboxCircleFill, RiDoorOpenLine, RiErrorWarningFill, RiLoader4Line, RiTimeLine } from '@remixicon/react';
import { addDays } from 'date-fns';

import { FormField, SelectField, TextField } from '@/components/form-field';
import * as Alert from '@/components/ui/alert';
import * as Button from '@/components/ui/button';
import * as Checkbox from '@/components/ui/checkbox';
import * as Label from '@/components/ui/label';
import { adminApi } from '@/lib/api/admin';
import type { DoctorDetail, LocationDetail, SlotGenerationRequest, SpecialtyDetail, Weekday } from '@/lib/api/types';
import { todayIso, toIsoDate } from '@/lib/formatters';

import { DoctorSelect } from '../components/doctor-select';
import { clearField, toFormFailure } from '../components/form-errors';
import { adminKeys } from '../queries';

const WEEKDAYS: { value: Weekday; label: string }[] = [
  { value: 'MONDAY', label: 'Lunes' },
  { value: 'TUESDAY', label: 'Martes' },
  { value: 'WEDNESDAY', label: 'Miércoles' },
  { value: 'THURSDAY', label: 'Jueves' },
  { value: 'FRIDAY', label: 'Viernes' },
  { value: 'SATURDAY', label: 'Sábado' },
  { value: 'SUNDAY', label: 'Domingo' },
];
const DEFAULT_WEEKDAYS: Weekday[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

export interface GeneratedRange {
  doctorId: number;
  from: string;
  to: string;
}

interface SlotGenerationFormProps {
  doctors: DoctorDetail[];
  specialties: SpecialtyDetail[];
  locations: LocationDetail[];
  /** Se invoca tras generar cupos para que la página muestre la agenda resultante. */
  onGenerated?: (range: GeneratedRange) => void;
}

interface FormState {
  doctorId: string;
  locationId: string;
  room: string;
  from: string;
  to: string;
  startTime: string;
  endTime: string;
  durationMinutes: string;
  weekdays: Weekday[];
}

function initialState(): FormState {
  return {
    doctorId: '',
    locationId: '',
    room: '',
    from: todayIso(),
    to: toIsoDate(addDays(new Date(), 6)),
    startTime: '08:00',
    endTime: '13:00',
    durationMinutes: '',
    weekdays: DEFAULT_WEEKDAYS,
  };
}

/** Panel "Generar cupos" (CU-024): crea los cupos de un médico en una sede para un rango de fechas. */
export function SlotGenerationForm({ doctors, specialties, locations, onGenerated }: SlotGenerationFormProps) {
  const queryClient = useQueryClient();
  const [form, setForm] = React.useState<FormState>(initialState);
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [formError, setFormError] = React.useState<string | null>(null);
  const [result, setResult] = React.useState<{ created: number; skipped: number } | null>(null);

  const doctor = React.useMemo(() => doctors.find((d) => String(d.id) === form.doctorId) ?? null, [doctors, form.doctorId]);
  const specialty = React.useMemo(() => (doctor ? (specialties.find((s) => s.id === doctor.specialty.id) ?? null) : null), [doctor, specialties]);

  // Solo las sedes activas que ofrecen la especialidad del médico.
  const locationOptions = React.useMemo(() => {
    if (!specialty) return [];
    const offered = new Set(specialty.locations.map((l) => l.id));
    return locations.filter((l) => l.active && offered.has(l.id)).map((l) => ({ value: String(l.id), label: `${l.name} · ${l.district}` }));
  }, [specialty, locations]);

  const mutation = useMutation({
    mutationFn: (body: SlotGenerationRequest) => adminApi.generateSlots(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.slotsAll }),
  });

  const update = <K extends keyof FormState>(field: K, value: FormState[K]) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => clearField(prev, field));
    setFormError(null);
  };

  const toggleWeekday = (weekday: Weekday, checked: boolean) => {
    update('weekdays', checked ? WEEKDAYS.map((w) => w.value).filter((w) => w === weekday || form.weekdays.includes(w)) : form.weekdays.filter((w) => w !== weekday));
  };

  const validate = (): boolean => {
    const errors: Record<string, string> = {};
    if (form.doctorId === '') errors.doctorId = 'Selecciona el médico.';
    if (form.locationId === '') errors.locationId = 'Selecciona la sede.';
    if (form.from === '') errors.from = 'Ingresa la fecha inicial.';
    else if (form.from < todayIso()) errors.from = 'La fecha inicial no puede ser anterior a hoy.';
    if (form.to === '') errors.to = 'Ingresa la fecha final.';
    else if (form.from && form.to < form.from) errors.to = 'La fecha final debe ser igual o posterior a la fecha inicial.';
    if (form.startTime === '') errors.startTime = 'Ingresa la hora de inicio.';
    if (form.endTime === '') errors.endTime = 'Ingresa la hora de fin.';
    else if (form.startTime && form.endTime <= form.startTime) errors.endTime = 'La hora de fin debe ser posterior a la hora de inicio.';
    if (form.durationMinutes.trim() !== '') {
      const minutes = Number(form.durationMinutes);
      if (!Number.isInteger(minutes) || minutes < 5 || minutes > 240) errors.durationMinutes = 'La duración debe estar entre 5 y 240 minutos.';
    }
    if (form.weekdays.length === 0) errors.weekdays = 'Marca al menos un día de la semana.';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError(null);
    setResult(null);
    if (!validate()) return;
    try {
      const outcome = await mutation.mutateAsync({
        doctorId: Number(form.doctorId),
        locationId: Number(form.locationId),
        room: form.room.trim() === '' ? null : form.room.trim(),
        from: form.from,
        to: form.to,
        startTime: form.startTime,
        endTime: form.endTime,
        weekdays: form.weekdays,
        durationMinutes: form.durationMinutes.trim() === '' ? null : Number(form.durationMinutes),
      });
      setResult({ created: outcome.created, skipped: outcome.skipped });
      onGenerated?.({ doctorId: Number(form.doctorId), from: form.from, to: form.to });
    } catch (error) {
      const failure = toFormFailure(error);
      setFieldErrors(failure.fields);
      setFormError(failure.message);
    }
  };

  const pending = mutation.isPending;
  const noLocations = specialty !== null && locationOptions.length === 0;

  return (
    <form className='flex flex-col gap-4' onSubmit={handleSubmit} noValidate>
      {result && (
        <Alert.Root variant='lighter' status='success' size='small'>
          <Alert.Icon as={RiCheckboxCircleFill} />
          {result.created === 1 ? 'Se generó 1 cupo' : `Se generaron ${result.created} cupos`}
          {result.skipped > 0 && ` (${result.skipped === 1 ? '1 ya existía' : `${result.skipped} ya existían`})`}.
        </Alert.Root>
      )}
      {formError && (
        <Alert.Root variant='lighter' status='error' size='small'>
          <Alert.Icon as={RiErrorWarningFill} />
          {formError}
        </Alert.Root>
      )}

      <div className='grid grid-cols-3 gap-4'>
        <DoctorSelect
          id='gen-doctor'
          required
          doctors={doctors}
          value={form.doctorId}
          onValueChange={(value) => {
            update('doctorId', value);
            update('locationId', '');
            update('durationMinutes', '');
          }}
          error={fieldErrors.doctorId}
          disabled={pending}
        />
        <SelectField
          id='gen-location'
          label='Sede'
          required
          value={form.locationId}
          onValueChange={(value) => update('locationId', value)}
          placeholder={doctor ? (noLocations ? 'Ninguna sede atiende esta especialidad' : 'Selecciona la sede') : 'Primero elige el médico'}
          options={locationOptions}
          hint={noLocations ? `Ninguna sede ofrece ${specialty?.name}. Agrega la sede en Especialidades antes de generar la agenda.` : undefined}
          error={fieldErrors.locationId}
          disabled={pending || !doctor || noLocations}
        />
        <TextField
          id='gen-room'
          label='Consultorio'
          optional
          icon={RiDoorOpenLine}
          error={fieldErrors.room}
          inputProps={{ value: form.room, onChange: (e) => update('room', e.target.value), placeholder: 'p. ej. 204', maxLength: 20, disabled: pending }}
        />
      </div>

      <div className='grid grid-cols-5 gap-4'>
        <TextField
          id='gen-from'
          label='Desde'
          required
          error={fieldErrors.from}
          inputProps={{ type: 'date', value: form.from, min: todayIso(), onChange: (e) => update('from', e.target.value), disabled: pending }}
        />
        <TextField
          id='gen-to'
          label='Hasta'
          required
          error={fieldErrors.to}
          inputProps={{ type: 'date', value: form.to, min: form.from || todayIso(), onChange: (e) => update('to', e.target.value), disabled: pending }}
        />
        <TextField
          id='gen-start'
          label='Hora de inicio'
          required
          error={fieldErrors.startTime}
          inputProps={{ type: 'time', value: form.startTime, step: 300, onChange: (e) => update('startTime', e.target.value), disabled: pending }}
        />
        <TextField
          id='gen-end'
          label='Hora de fin'
          required
          error={fieldErrors.endTime}
          inputProps={{ type: 'time', value: form.endTime, step: 300, onChange: (e) => update('endTime', e.target.value), disabled: pending }}
        />
        <TextField
          id='gen-duration'
          label='Minutos por cupo'
          optional
          icon={RiTimeLine}
          hint={specialty ? `Vacío: ${specialty.appointmentMinutes} min, los de ${specialty.name}.` : 'Vacío: los minutos de la especialidad.'}
          error={fieldErrors.durationMinutes}
          inputProps={{
            type: 'number',
            inputMode: 'numeric',
            min: 5,
            max: 240,
            step: 5,
            value: form.durationMinutes,
            placeholder: specialty ? String(specialty.appointmentMinutes) : '',
            onChange: (e) => update('durationMinutes', e.target.value),
            disabled: pending,
          }}
        />
      </div>

      <div className='flex items-start justify-between gap-4'>
        <FormField label='Días de la semana' required error={fieldErrors.weekdays} hint='Por defecto, de lunes a sábado.' className='flex-1'>
          <div className='flex min-h-10 flex-wrap items-center gap-x-5 gap-y-2 rounded-10 bg-bg-weak-50 px-3 py-2'>
            {WEEKDAYS.map((weekday) => {
              const id = `gen-weekday-${weekday.value}`;
              return (
                <div key={weekday.value} className='flex items-center gap-2'>
                  <Checkbox.Root
                    id={id}
                    checked={form.weekdays.includes(weekday.value)}
                    onCheckedChange={(checked) => toggleWeekday(weekday.value, checked === true)}
                    disabled={pending}
                  />
                  <Label.Root className='text-paragraph-sm' htmlFor={id}>
                    {weekday.label}
                  </Label.Root>
                </div>
              );
            })}
          </div>
        </FormField>
        <Button.Root type='submit' variant='primary' mode='filled' size='medium' className='mt-6 shrink-0' disabled={pending}>
          <Button.Icon as={pending ? RiLoader4Line : RiCalendarCheckLine} className={pending ? 'animate-spin' : undefined} />
          {pending ? 'Generando cupos…' : 'Generar cupos'}
        </Button.Root>
      </div>
    </form>
  );
}
