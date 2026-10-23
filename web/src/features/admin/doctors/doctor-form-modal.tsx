import * as React from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { RiStethoscopeLine, RiUserAddLine } from '@remixicon/react';

import { SelectField, TextField } from '@/components/form-field';
import { notify } from '@/components/notify';
import { adminApi } from '@/lib/api/admin';
import type { DoctorDetail, DoctorRequest, SpecialtyDetail } from '@/lib/api/types';

import { FormModal } from '../components/form-modal';
import { clearField, toFormFailure } from '../components/form-errors';
import { SwitchField } from '../components/switch-field';
import { adminKeys } from '../queries';

const CODE_TO_FIELD = { DOCTOR_LICENSE_TAKEN: 'license' };

interface DoctorFormModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Médico a editar; sin valor se registra uno nuevo. */
  doctor: DoctorDetail | null;
  specialties: SpecialtyDetail[];
}

interface FormState {
  license: string;
  specialistRegistry: string;
  firstNames: string;
  lastNames: string;
  specialtyId: string;
  active: boolean;
}

function initialState(doctor: DoctorDetail | null): FormState {
  return {
    license: doctor?.license ?? '',
    specialistRegistry: doctor?.specialistRegistry ?? '',
    firstNames: doctor?.firstNames ?? '',
    lastNames: doctor?.lastNames ?? '',
    specialtyId: doctor ? String(doctor.specialty.id) : '',
    active: doctor?.active ?? true,
  };
}

/** Alta y edición de médicos (CU-022). */
export function DoctorFormModal({ open, onOpenChange, doctor, specialties }: DoctorFormModalProps) {
  const queryClient = useQueryClient();
  const isEdit = doctor !== null;
  const [form, setForm] = React.useState<FormState>(() => initialState(doctor));
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [formError, setFormError] = React.useState<string | null>(null);

  // Especialidades activas, más la actual del médico aunque esté inactiva.
  const specialtyOptions = React.useMemo(
    () =>
      specialties
        .filter((s) => s.active || s.id === doctor?.specialty.id)
        .map((s) => ({
          value: String(s.id),
          label: s.active ? s.name : `${s.name} (inactiva)`,
        })),
    [specialties, doctor],
  );

  const mutation = useMutation({
    mutationFn: (state: FormState) => {
      const body: DoctorRequest = {
        license: state.license.trim(),
        specialistRegistry: state.specialistRegistry.trim() === '' ? null : state.specialistRegistry.trim(),
        firstNames: state.firstNames.trim(),
        lastNames: state.lastNames.trim(),
        specialtyId: Number(state.specialtyId),
        active: isEdit ? state.active : undefined,
      };
      return isEdit ? adminApi.updateDoctor(doctor.id, body) : adminApi.createDoctor(body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminKeys.doctors });
      queryClient.invalidateQueries({ queryKey: adminKeys.users });
    },
  });

  const update = <K extends keyof FormState>(field: K, value: FormState[K]) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => clearField(prev, field));
    setFormError(null);
  };

  const validate = (): boolean => {
    const errors: Record<string, string> = {};
    if (form.license.trim() === '') errors.license = 'Ingresa el número de colegiatura.';
    else if (form.license.trim().length > 10) errors.license = 'La colegiatura admite hasta 10 caracteres.';
    if (form.specialistRegistry.trim().length > 10) errors.specialistRegistry = 'El registro de especialista admite hasta 10 caracteres.';
    if (form.firstNames.trim() === '') errors.firstNames = 'Ingresa los nombres del médico.';
    if (form.lastNames.trim() === '') errors.lastNames = 'Ingresa los apellidos del médico.';
    if (form.specialtyId === '') errors.specialtyId = 'Selecciona la especialidad del médico.';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async () => {
    setFormError(null);
    if (!validate()) return;
    try {
      const saved = await mutation.mutateAsync(form);
      notify.success(isEdit ? `Se guardaron los cambios de ${saved.fullName}.` : `Se registró a ${saved.fullName}.`);
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
      icon={isEdit ? RiStethoscopeLine : RiUserAddLine}
      title={isEdit ? `Editar a ${doctor.fullName}` : 'Nuevo médico'}
      description={isEdit ? 'Actualiza los datos, la especialidad o el estado del médico.' : 'Registra un médico para poder generar su agenda y vincularle un usuario.'}
      errorMessage={formError}
      submitLabel={isEdit ? 'Guardar cambios' : 'Registrar médico'}
      isPending={pending}
      onSubmit={handleSubmit}
    >
      <div className='grid grid-cols-2 gap-4'>
        <TextField
          id='doctor-first-names'
          label='Nombres'
          required
          error={fieldErrors.firstNames}
          inputProps={{
            value: form.firstNames,
            onChange: (e) => update('firstNames', e.target.value),
            placeholder: 'p. ej. Carla',
            maxLength: 100,
            autoFocus: true,
            disabled: pending,
          }}
        />
        <TextField
          id='doctor-last-names'
          label='Apellidos'
          required
          error={fieldErrors.lastNames}
          inputProps={{
            value: form.lastNames,
            onChange: (e) => update('lastNames', e.target.value),
            placeholder: 'p. ej. Benavides Ortiz',
            maxLength: 100,
            disabled: pending,
          }}
        />
        <TextField
          id='doctor-license'
          label='Colegiatura (CMP)'
          required
          error={fieldErrors.license}
          inputProps={{
            value: form.license,
            onChange: (e) => update('license', e.target.value),
            placeholder: 'p. ej. 045871',
            maxLength: 10,
            disabled: pending,
          }}
        />
        <TextField
          id='doctor-registry'
          label='Registro de especialista (RNE)'
          optional
          error={fieldErrors.specialistRegistry}
          inputProps={{
            value: form.specialistRegistry,
            onChange: (e) => update('specialistRegistry', e.target.value),
            placeholder: 'p. ej. 012345',
            maxLength: 10,
            disabled: pending,
          }}
        />
      </div>
      <SelectField
        id='doctor-specialty'
        label='Especialidad'
        required
        value={form.specialtyId}
        onValueChange={(value) => update('specialtyId', value)}
        placeholder='Selecciona la especialidad'
        options={specialtyOptions}
        error={fieldErrors.specialtyId}
        disabled={pending}
      />
      {isEdit && (
        <SwitchField
          id='doctor-active'
          label='Médico activo'
          description='Un médico inactivo no aparece en el kiosko ni se le pueden generar cupos.'
          checked={form.active}
          onCheckedChange={(checked) => update('active', checked)}
          disabled={pending}
        />
      )}
    </FormModal>
  );
}
