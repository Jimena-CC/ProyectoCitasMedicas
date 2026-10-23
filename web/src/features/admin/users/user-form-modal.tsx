import * as React from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { RiUserAddLine, RiUserSettingsLine, RiUserLine } from '@remixicon/react';

import { SelectField, TextField } from '@/components/form-field';
import { notify } from '@/components/notify';
import { adminApi } from '@/lib/api/admin';
import type { DoctorDetail, UserRole, UserSummary } from '@/lib/api/types';
import { ROLE_LABELS } from '@/lib/formatters';

import { FormModal } from '../components/form-modal';
import { clearField, toFormFailure } from '../components/form-errors';
import { PasswordField } from '../components/password-field';
import { SwitchField } from '../components/switch-field';
import { adminKeys } from '../queries';

const ROLE_OPTIONS = (Object.keys(ROLE_LABELS) as UserRole[]).map((role) => ({
  value: role,
  label: ROLE_LABELS[role],
}));
const USERNAME_PATTERN = /^[A-Za-z0-9._-]+$/;
const PASSWORD_HINT = 'Entre 8 y 72 caracteres. Usa letras, números y algún símbolo.';

/** Códigos 409 de la API que se muestran bajo el campo correspondiente. */
const CODE_TO_FIELD = {
  USERNAME_TAKEN: 'username',
  DOCTOR_ALREADY_LINKED: 'doctorId',
};

interface UserFormModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Usuario a editar; sin valor se crea uno nuevo. */
  user: UserSummary | null;
  users: UserSummary[];
  doctors: DoctorDetail[];
}

interface FormState {
  username: string;
  password: string;
  role: UserRole | '';
  doctorId: string;
  active: boolean;
}

function initialState(user: UserSummary | null): FormState {
  return {
    username: user?.username ?? '',
    password: '',
    role: user?.role ?? '',
    doctorId: user?.doctorId ? String(user.doctorId) : '',
    active: user?.active ?? true,
  };
}

/**
 * Alta y edición de usuarios (CU-021). Al crear se piden usuario y contraseña;
 * al editar solo rol, médico vinculado y estado (la contraseña se cambia aparte).
 */
export function UserFormModal({ open, onOpenChange, user, users, doctors }: UserFormModalProps) {
  const queryClient = useQueryClient();
  const isEdit = user !== null;
  const [form, setForm] = React.useState<FormState>(() => initialState(user));
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [formError, setFormError] = React.useState<string | null>(null);

  // Médicos activos sin usuario asignado (más el ya vinculado, al editar).
  const doctorOptions = React.useMemo(() => {
    const linked = new Set(users.filter((u) => u.id !== user?.id).map((u) => u.doctorId));
    return doctors
      .filter((d) => (d.active && !linked.has(d.id)) || d.id === user?.doctorId)
      .map((d) => ({
        value: String(d.id),
        label: `${d.fullName} · ${d.specialty.name}`,
      }));
  }, [doctors, users, user]);

  const mutation = useMutation({
    mutationFn: async (state: FormState) => {
      const role = state.role as UserRole;
      const doctorId = role === 'DOCTOR' && state.doctorId ? Number(state.doctorId) : null;
      if (isEdit)
        return adminApi.updateUser(user.id, {
          role,
          doctorId,
          active: state.active,
        });
      return adminApi.createUser({
        username: state.username.trim(),
        password: state.password,
        role,
        doctorId,
      });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.users }),
  });

  const update = <K extends keyof FormState>(field: K, value: FormState[K]) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => clearField(prev, field));
    setFormError(null);
  };

  const validate = (): boolean => {
    const errors: Record<string, string> = {};
    if (!isEdit) {
      const username = form.username.trim();
      if (username === '') errors.username = 'Ingresa el nombre de usuario.';
      else if (username.length < 3 || username.length > 50) errors.username = 'El nombre de usuario debe tener entre 3 y 50 caracteres.';
      else if (!USERNAME_PATTERN.test(username)) errors.username = 'El nombre de usuario solo admite letras, números, punto, guion y guion bajo.';
      if (form.password === '') errors.password = 'Ingresa la contraseña.';
      else if (form.password.length < 8 || form.password.length > 72) errors.password = 'La contraseña debe tener entre 8 y 72 caracteres.';
    }
    if (form.role === '') errors.role = 'Selecciona el perfil del usuario.';
    if (form.role === 'DOCTOR' && form.doctorId === '') errors.doctorId = 'Selecciona el médico vinculado al usuario.';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async () => {
    setFormError(null);
    if (!validate()) return;
    try {
      const saved = await mutation.mutateAsync(form);
      notify.success(isEdit ? `Se guardaron los cambios de ${saved.username}.` : `Se creó el usuario ${saved.username}.`);
      onOpenChange(false);
    } catch (error) {
      const failure = toFormFailure(error, CODE_TO_FIELD);
      setFieldErrors(failure.fields);
      setFormError(failure.message);
    }
  };

  const noDoctorsAvailable = form.role === 'DOCTOR' && doctorOptions.length === 0;

  return (
    <FormModal
      open={open}
      onOpenChange={onOpenChange}
      icon={isEdit ? RiUserSettingsLine : RiUserAddLine}
      title={isEdit ? `Editar usuario ${user.username}` : 'Nuevo usuario'}
      description={isEdit ? 'Cambia el perfil, el médico vinculado o el estado del usuario.' : 'Crea un acceso al backoffice para una persona del personal.'}
      errorMessage={formError}
      submitLabel={isEdit ? 'Guardar cambios' : 'Crear usuario'}
      isPending={mutation.isPending}
      onSubmit={handleSubmit}
    >
      {!isEdit && (
        <>
          <TextField
            id='user-username'
            label='Nombre de usuario'
            required
            icon={RiUserLine}
            hint='Entre 3 y 50 caracteres: letras, números, punto, guion o guion bajo.'
            error={fieldErrors.username}
            inputProps={{
              value: form.username,
              onChange: (e) => update('username', e.target.value),
              placeholder: 'p. ej. mlopez',
              autoComplete: 'off',
              autoFocus: true,
              maxLength: 50,
              disabled: mutation.isPending,
            }}
          />
          <PasswordField
            id='user-password'
            label='Contraseña'
            value={form.password}
            onChange={(value) => update('password', value)}
            error={fieldErrors.password}
            hint={PASSWORD_HINT}
            disabled={mutation.isPending}
          />
        </>
      )}

      <SelectField
        id='user-role'
        label='Perfil'
        required
        value={form.role}
        onValueChange={(value) => {
          update('role', value as UserRole);
          if (value !== 'DOCTOR') update('doctorId', '');
        }}
        placeholder='Selecciona el perfil'
        options={ROLE_OPTIONS}
        error={fieldErrors.role}
        disabled={mutation.isPending}
      />

      {form.role === 'DOCTOR' && (
        <SelectField
          id='user-doctor'
          label='Médico vinculado'
          required
          value={form.doctorId}
          onValueChange={(value) => update('doctorId', value)}
          placeholder={noDoctorsAvailable ? 'No hay médicos activos sin usuario' : 'Selecciona el médico'}
          options={doctorOptions}
          hint={
            noDoctorsAvailable
              ? 'Registra o activa un médico en la sección Médicos antes de crear su usuario.'
              : 'Solo se listan médicos activos que aún no tienen usuario.'
          }
          error={fieldErrors.doctorId}
          disabled={mutation.isPending || noDoctorsAvailable}
        />
      )}

      {isEdit && (
        <SwitchField
          id='user-active'
          label='Usuario activo'
          description='Un usuario inactivo no puede iniciar sesión.'
          checked={form.active}
          onCheckedChange={(checked) => update('active', checked)}
          disabled={mutation.isPending}
        />
      )}
    </FormModal>
  );
}
