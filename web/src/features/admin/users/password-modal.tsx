import * as React from 'react';
import { useMutation } from '@tanstack/react-query';
import { RiLockPasswordLine } from '@remixicon/react';

import { notify } from '@/components/notify';
import { adminApi } from '@/lib/api/admin';
import type { UserSummary } from '@/lib/api/types';

import { FormModal } from '../components/form-modal';
import { toFormFailure } from '../components/form-errors';
import { PasswordField } from '../components/password-field';

interface PasswordModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  user: UserSummary | null;
}

/** Cambio de contraseña de un usuario por parte del administrador (CU-021). */
export function PasswordModal({ open, onOpenChange, user }: PasswordModalProps) {
  const [password, setPassword] = React.useState('');
  const [confirm, setConfirm] = React.useState('');
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [formError, setFormError] = React.useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (body: { userId: number; password: string }) => adminApi.changeUserPassword(body.userId, { password: body.password }),
  });

  const handleSubmit = async () => {
    if (!user) return;
    const errors: Record<string, string> = {};
    if (password === '') errors.password = 'Ingresa la nueva contraseña.';
    else if (password.length < 8 || password.length > 72) errors.password = 'La contraseña debe tener entre 8 y 72 caracteres.';
    if (confirm !== password) errors.confirm = 'Las contraseñas no coinciden.';
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    try {
      await mutation.mutateAsync({ userId: user.id, password });
      notify.success(`Se cambió la contraseña de ${user.username}.`);
      onOpenChange(false);
    } catch (error) {
      const failure = toFormFailure(error);
      setFieldErrors(failure.fields);
      setFormError(failure.message);
    }
  };

  return (
    <FormModal
      open={open}
      onOpenChange={onOpenChange}
      icon={RiLockPasswordLine}
      title='Cambiar contraseña'
      description={user ? `Define una nueva contraseña para ${user.username}. La anterior dejará de funcionar.` : undefined}
      errorMessage={formError}
      submitLabel='Cambiar contraseña'
      isPending={mutation.isPending}
      onSubmit={handleSubmit}
    >
      <PasswordField
        id='password-new'
        label='Nueva contraseña'
        value={password}
        onChange={(value) => {
          setPassword(value);
          setFieldErrors((prev) => ({ ...prev, password: '' }));
        }}
        error={fieldErrors.password}
        hint='Entre 8 y 72 caracteres.'
        disabled={mutation.isPending}
      />
      <PasswordField
        id='password-confirm'
        label='Repite la contraseña'
        value={confirm}
        onChange={(value) => {
          setConfirm(value);
          setFieldErrors((prev) => ({ ...prev, confirm: '' }));
        }}
        error={fieldErrors.confirm}
        disabled={mutation.isPending}
      />
    </FormModal>
  );
}
