import * as React from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { RiHeartPulseLine, RiTimeLine } from '@remixicon/react';

import { FormField, TextField, TextareaField } from '@/components/form-field';
import { notify } from '@/components/notify';
import * as Checkbox from '@/components/ui/checkbox';
import * as Label from '@/components/ui/label';
import { adminApi } from '@/lib/api/admin';
import type { LocationDetail, SpecialtyDetail, SpecialtyRequest } from '@/lib/api/types';

import { FormModal } from '../components/form-modal';
import { clearField, toFormFailure } from '../components/form-errors';
import { SwitchField } from '../components/switch-field';
import { adminKeys } from '../queries';

const CODE_TO_FIELD = { SPECIALTY_NAME_TAKEN: 'name' };

interface SpecialtyFormModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Especialidad a editar; sin valor se crea una nueva. */
  specialty: SpecialtyDetail | null;
  locations: LocationDetail[];
}

interface FormState {
  name: string;
  description: string;
  appointmentMinutes: string;
  locationIds: number[];
  active: boolean;
}

function initialState(specialty: SpecialtyDetail | null): FormState {
  return {
    name: specialty?.name ?? '',
    description: specialty?.description ?? '',
    appointmentMinutes: specialty ? String(specialty.appointmentMinutes) : '20',
    locationIds: specialty?.locations.map((l) => l.id) ?? [],
    active: specialty?.active ?? true,
  };
}

/** Alta y edición de especialidades con sus sedes (CU-023). */
export function SpecialtyFormModal({ open, onOpenChange, specialty, locations }: SpecialtyFormModalProps) {
  const queryClient = useQueryClient();
  const isEdit = specialty !== null;
  const [form, setForm] = React.useState<FormState>(() => initialState(specialty));
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [formError, setFormError] = React.useState<string | null>(null);

  // Sedes activas, más las que la especialidad ya tiene aunque estén inactivas.
  const locationOptions = React.useMemo(() => locations.filter((l) => l.active || form.locationIds.includes(l.id)), [locations, form.locationIds]);

  const mutation = useMutation({
    mutationFn: (state: FormState) => {
      const body: SpecialtyRequest = {
        name: state.name.trim(),
        description: state.description.trim() === '' ? null : state.description.trim(),
        appointmentMinutes: Number(state.appointmentMinutes),
        locationIds: state.locationIds,
        active: isEdit ? state.active : undefined,
      };
      return isEdit ? adminApi.updateSpecialty(specialty.id, body) : adminApi.createSpecialty(body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminKeys.specialties });
      queryClient.invalidateQueries({ queryKey: adminKeys.doctors });
    },
  });

  const update = <K extends keyof FormState>(field: K, value: FormState[K]) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => clearField(prev, field));
    setFormError(null);
  };

  const toggleLocation = (locationId: number, checked: boolean) => {
    update('locationIds', checked ? [...form.locationIds, locationId] : form.locationIds.filter((id) => id !== locationId));
  };

  const validate = (): boolean => {
    const errors: Record<string, string> = {};
    if (form.name.trim() === '') errors.name = 'Ingresa el nombre de la especialidad.';
    else if (form.name.trim().length > 120) errors.name = 'El nombre admite hasta 120 caracteres.';
    const minutes = Number(form.appointmentMinutes);
    if (form.appointmentMinutes.trim() === '' || !Number.isInteger(minutes)) errors.appointmentMinutes = 'Ingresa la duración de la cita en minutos.';
    else if (minutes < 5) errors.appointmentMinutes = 'La duración mínima de una cita es de 5 minutos.';
    else if (minutes > 240) errors.appointmentMinutes = 'La duración máxima de una cita es de 240 minutos.';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async () => {
    setFormError(null);
    if (!validate()) return;
    try {
      const saved = await mutation.mutateAsync(form);
      notify.success(isEdit ? `Se guardaron los cambios de ${saved.name}.` : `Se creó la especialidad ${saved.name}.`);
      onOpenChange(false);
    } catch (error) {
      const failure = toFormFailure(error, CODE_TO_FIELD);
      setFieldErrors(failure.fields);
      setFormError(failure.message);
    }
  };

  const pending = mutation.isPending;

  return (
    <FormModal
      open={open}
      onOpenChange={onOpenChange}
      icon={RiHeartPulseLine}
      title={isEdit ? `Editar ${specialty.name}` : 'Nueva especialidad'}
      description={
        isEdit ? 'Cambia la duración de la cita, las sedes donde se atiende o su estado.' : 'Define la especialidad, cuánto dura cada cita y en qué sedes se atiende.'
      }
      errorMessage={formError}
      submitLabel={isEdit ? 'Guardar cambios' : 'Crear especialidad'}
      isPending={pending}
      onSubmit={handleSubmit}
    >
      <TextField
        id='specialty-name'
        label='Nombre'
        required
        error={fieldErrors.name}
        inputProps={{
          value: form.name,
          onChange: (e) => update('name', e.target.value),
          placeholder: 'p. ej. Cardiología',
          maxLength: 120,
          autoFocus: true,
          disabled: pending,
        }}
      />
      <TextareaField
        id='specialty-description'
        label='Descripción'
        optional
        value={form.description}
        maxLength={1000}
        error={fieldErrors.description}
        textareaProps={{
          onChange: (e) => update('description', e.target.value),
          placeholder: 'Qué atiende esta especialidad (se muestra en el kiosko).',
          rows: 3,
          disabled: pending,
        }}
      />
      <TextField
        id='specialty-minutes'
        label='Minutos por cita'
        required
        icon={RiTimeLine}
        hint='Entre 5 y 240 minutos. Define la duración de los cupos que se generan.'
        error={fieldErrors.appointmentMinutes}
        inputProps={{
          type: 'number',
          inputMode: 'numeric',
          min: 5,
          max: 240,
          step: 5,
          value: form.appointmentMinutes,
          onChange: (e) => update('appointmentMinutes', e.target.value),
          disabled: pending,
        }}
      />
      <FormField
        label='Sedes donde se atiende'
        hint={locationOptions.length === 0 ? 'No hay sedes activas registradas.' : 'Solo las sedes marcadas ofrecen esta especialidad en el kiosko.'}
        error={fieldErrors.locationIds}
      >
        <div className='flex flex-col gap-3 rounded-10 bg-bg-weak-50 p-3'>
          {locationOptions.map((location) => {
            const id = `specialty-location-${location.id}`;
            return (
              <div key={location.id} className='flex items-center gap-2'>
                <Checkbox.Root
                  id={id}
                  checked={form.locationIds.includes(location.id)}
                  onCheckedChange={(checked) => toggleLocation(location.id, checked === true)}
                  disabled={pending}
                />
                <Label.Root className='text-paragraph-sm' htmlFor={id}>
                  {location.name}
                  <span className='text-text-sub-600'> · {location.district}</span>
                  {!location.active && <span className='text-text-soft-400'> (sede inactiva)</span>}
                </Label.Root>
              </div>
            );
          })}
        </div>
      </FormField>
      {isEdit && (
        <SwitchField
          id='specialty-active'
          label='Especialidad activa'
          description='Una especialidad inactiva deja de ofrecerse en el kiosko.'
          checked={form.active}
          onCheckedChange={(checked) => update('active', checked)}
          disabled={pending}
        />
      )}
    </FormModal>
  );
}
